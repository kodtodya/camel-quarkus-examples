package com.kodtodya.insurance.poc.processor;

import com.kodtodya.insurance.poc.config.PolicyProcessingConfig;
import com.kodtodya.insurance.poc.metrics.ProcessingMetrics;
import com.kodtodya.insurance.poc.model.InsurancePolicy;
import com.kodtodya.insurance.poc.model.ProcessingBatch;
import com.kodtodya.insurance.poc.service.PolicyProcessingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Processes a page (List<InsurancePolicy>) using a parallel fork-join approach.
 *
 * Best practices applied:
 *   ✔ Sub-batch splitting for finer parallelism
 *   ✔ CompletableFuture parallel processing
 *   ✔ Bulk DB update after page completes (minimises round-trips)
 *   ✔ Progress logging at configurable intervals
 *   ✔ Micrometer metrics updated per record
 */
@ApplicationScoped
public class PolicyBatchProcessor implements Processor {

    private static final Logger LOG = Logger.getLogger(PolicyBatchProcessor.class);
    private static final int   SUB_BATCH_SIZE = 100; // sub-divide page for parallel tasks

    private static final String UPDATE_SQL =
        "UPDATE insurance_policy SET processed=TRUE, processed_at=?, processing_batch_id=?, remarks=? WHERE id=?";

    private final AtomicLong globalProcessedCounter = new AtomicLong(0);

    @Inject
    PolicyProcessingService processingService;

    @Inject
    PolicyProcessingConfig config;

    @Inject
    ProcessingMetrics metrics;

    @Inject
    DataSource dataSource;

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) throws Exception {
        List<InsurancePolicy> page = exchange.getMessage().getBody(List.class);
        if (page == null || page.isEmpty()) {
            LOG.debug("Empty page received — skipping");
            return;
        }

        ProcessingBatch batch = exchange.getProperty(
                PolicyPageFetchProcessor.PROP_BATCH, ProcessingBatch.class);

        long start = System.currentTimeMillis();

        // Split page into sub-batches and process in parallel
        List<List<InsurancePolicy>> subBatches = splitIntoSubBatches(page, SUB_BATCH_SIZE);

        // Use virtual threads (Java 21) for max throughput
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<CompletableFuture<List<InsurancePolicy>>> futures = new ArrayList<>();

        for (List<InsurancePolicy> subBatch : subBatches) {
            CompletableFuture<List<InsurancePolicy>> future = CompletableFuture.supplyAsync(() -> {
                processingService.processBatch(subBatch, batch);
                return subBatch;
            }, executor);
            futures.add(future);
        }

        // Wait for all sub-batches and collect results
        List<InsurancePolicy> processedPolicies = new ArrayList<>(page.size());
        for (CompletableFuture<List<InsurancePolicy>> future : futures) {
            processedPolicies.addAll(future.get(120, TimeUnit.SECONDS));
        }

        executor.shutdown();

        // Bulk DB update
        bulkUpdateProcessed(processedPolicies);

        // Update metrics
        long processed  = processedPolicies.stream().filter(p -> Boolean.TRUE.equals(p.getProcessed())).count();
        long failed     = processedPolicies.size() - processed;
        long elapsed    = System.currentTimeMillis() - start;

        metrics.recordPageProcessed(processed, failed, elapsed);

        long cumulative = globalProcessedCounter.addAndGet(processed);

        // Progress log
        if (cumulative % config.batchLogInterval() < page.size()) {
            LOG.infof("[%s] Progress: %,d records processed (page time=%dms, throughput=%.0f/s)",
                      batch != null ? batch.getBatchId() : "N/A",
                      cumulative,
                      elapsed,
                      processed * 1000.0 / Math.max(elapsed, 1));
        }

        exchange.getMessage().setBody(processedPolicies);
    }

    // -----------------------------------------------
    // Bulk update helper
    // -----------------------------------------------
    private void bulkUpdateProcessed(List<InsurancePolicy> policies) {
        if (policies.isEmpty()) return;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
                int count = 0;
                for (InsurancePolicy p : policies) {
                    if (Boolean.TRUE.equals(p.getProcessed()) && p.getId() != null) {
                        ps.setTimestamp(1, p.getProcessedAt() != null
                                ? Timestamp.valueOf(p.getProcessedAt())
                                : Timestamp.valueOf(LocalDateTime.now()));
                        ps.setString(2, p.getProcessingBatchId());
                        ps.setString(3, p.getRemarks());
                        ps.setLong(4, p.getId());
                        ps.addBatch();
                        count++;
                        if (count % 500 == 0) {
                            ps.executeBatch();
                        }
                    }
                }
                ps.executeBatch();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                LOG.errorf(e, "Bulk update failed, rolling back");
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to get DB connection for bulk update");
        }
    }

    // -----------------------------------------------
    // Partition list into sub-lists
    // -----------------------------------------------
    private <T> List<List<T>> splitIntoSubBatches(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
