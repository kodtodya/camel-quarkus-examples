package com.kodtodya.insurance.poc.route;

import com.kodtodya.insurance.poc.config.CamelThreadPoolConfig;
import com.kodtodya.insurance.poc.config.PolicyProcessingConfig;
import com.kodtodya.insurance.poc.metrics.ProcessingMetrics;
import com.kodtodya.insurance.poc.model.ProcessingBatch;
import com.kodtodya.insurance.poc.processor.PolicyBatchProcessor;
import com.kodtodya.insurance.poc.processor.PolicyPageFetchProcessor;
import com.kodtodya.insurance.poc.service.BatchStateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Main Apache Camel route for bulk insurance policy processing.
 *
 * Architecture:
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  REST trigger  →  direct:startBulkProcessing                       │
 * │     ↓                                                               │
 * │  Initialize batch state                                             │
 * │     ↓                                                               │
 * │  LOOP (until page is empty)                                         │
 * │    │  PolicyPageFetchProcessor  (paginated SQL, stream cursor)      │
 * │    │       ↓                                                        │
 * │    │  seda:processPage  (async handoff, configurable queue)         │
 * │    └──────────────────────────────────────────────────────┐         │
 * │                                                           ↓         │
 * │                            PolicyBatchProcessor           │         │
 * │                         (parallel sub-batches,            │         │
 * │                          virtual threads,                 │         │
 * │                          bulk DB update)                  │         │
 * │                                                           │         │
 * │  Complete batch  ←────────────────────────────────────────┘         │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * Best practices applied:
 *  ✔ Stream caching (spool to disk for large payloads)
 *  ✔ SEDA queue for async decoupling (producer vs consumer throttling)
 *  ✔ Configurable concurrency on SEDA consumers
 *  ✔ Pagination (LIMIT/OFFSET) — avoids loading 200K records at once
 *  ✔ Custom thread pool profiles
 *  ✔ Dead letter channel for fault tolerance
 *  ✔ Micrometer metrics per stage
 *  ✔ idempotent processing (WHERE processed = FALSE)
 */
@ApplicationScoped
public class InsurancePolicyRoute extends RouteBuilder {

    private static final Logger LOG = Logger.getLogger(InsurancePolicyRoute.class);

    @Inject
    PolicyPageFetchProcessor pageProcessor;

    @Inject
    PolicyBatchProcessor batchProcessor;

    @Inject
    BatchStateService batchService;

    @Inject
    ProcessingMetrics metrics;

    @Inject
    PolicyProcessingConfig config;

    @Inject
    CamelThreadPoolConfig threadPoolConfig;

    @Override
    public void configure() throws Exception {

        // Register thread pool profiles once context is ready
        threadPoolConfig.registerThreadPoolProfiles();

        // ============================================================
        // Global error handling with Dead Letter Channel
        // ============================================================
        errorHandler(deadLetterChannel("direct:deadLetter")
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .backOffMultiplier(2)
            .useExponentialBackOff()
            .logRetryAttempted(true)
            .logExhausted(true)
            .logStackTrace(true)
        );

        // ============================================================
        // Dead Letter Route — logs failed exchanges for audit
        // ============================================================
        from("direct:deadLetter")
            .routeId("deadLetterRoute")
            .log(LoggingLevel.ERROR,
                 "Dead letter — exchange failed: ${exchangeId}, cause: ${exception.message}")
            .to("log:DEAD_LETTER?level=ERROR&showAll=true");

        // ============================================================
        // ROUTE 1: Bulk Processing Orchestrator
        // Triggered via REST (direct:startBulkProcessing)
        // ============================================================
        from("direct:startBulkProcessing")
            .routeId("bulkProcessingOrchestrator")
            .streamCaching()
            .log(LoggingLevel.INFO, "Starting bulk insurance policy processing. BatchId: ${exchangeProperty.processingBatch?.batchId}")

            // Initialise counters
            .setProperty(PolicyPageFetchProcessor.PROP_PAGE_NUM,   constant(0))
            .setProperty(PolicyPageFetchProcessor.PROP_TOTAL_READ, constant(0L))

            // ---- Pagination Loop ----
            .loopDoWhile(exchange -> {
                // Continue while the last fetched page was non-empty
                List<?> body = exchange.getMessage().getBody(List.class);
                // On first iteration body is null — start the loop
                return body == null || !body.isEmpty();
            })
                // Fetch next page from DB (paginated SQL, stream cursor)
                .process(pageProcessor)
                .id("fetchPage")

                // Skip processing if page is empty (loop will exit after this)
                .choice()
                    .when(body().isNull())
                        .log(LoggingLevel.DEBUG, "Page fetch returned null — ending loop")
                    .when(simple("${body.size()} == 0"))
                        .log(LoggingLevel.INFO,
                             "No more records to process. Total read: ${exchangeProperty.totalRead}")
                    .otherwise()
                        // Async hand-off to SEDA queue (decouples DB fetch from processing)
                        .to("seda:processPage?waitForTaskToComplete=Never&timeout=0")
                        .log(LoggingLevel.DEBUG,
                             "Page ${exchangeProperty.pageNumber} dispatched to SEDA queue")
                .end()
            .end() // end loop

            // Signal completion
            .process(exchange -> {
                ProcessingBatch batch = exchange.getProperty(
                        PolicyPageFetchProcessor.PROP_BATCH, ProcessingBatch.class);
                if (batch != null) {
                    batchService.completeBatch(batch.getBatchId());
                    metrics.markBatchCompleted();
                    exchange.getMessage().setBody(batch);
                }
            })
            .log(LoggingLevel.INFO, "Bulk processing pagination complete. Awaiting SEDA consumers to finish.");

        // ============================================================
        // ROUTE 2: SEDA Consumer — Parallel Page Processor
        // Each message is a List<InsurancePolicy> (one page)
        // concurrentConsumers = config-driven multi-threading
        // ============================================================
        from("seda:processPage"
                + "?concurrentConsumers=" + config.sedaConcurrency()
                + "&size=" + config.sedaQueueSize()
                + "&blockWhenFull=true"
                + "&pollTimeout=5000")
            .routeId("sedaPageConsumer")
            .streamCaching()
            .log(LoggingLevel.DEBUG, "SEDA consumer processing page of ${body.size()} records")

            // Apply business processing + bulk DB update
            .process(batchProcessor)
            .id("processPage")

            .log(LoggingLevel.DEBUG, "SEDA page processing complete");

        // ============================================================
        // ROUTE 3: Status / Health check route (internal)
        // ============================================================
        from("direct:processingStatus")
            .routeId("processingStatusRoute")
            .process(exchange -> {
                boolean active = batchService.isProcessingActive();
                exchange.getMessage().setHeader("isActive", active);
                exchange.getMessage().setBody(batchService.getAllBatches());
            });
    }
}
