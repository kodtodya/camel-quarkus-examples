package com.kodtodya.insurance.poc.metrics;

import io.micrometer.core.instrument.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom Micrometer metrics for the insurance policy processing pipeline.
 *
 * Metrics exposed:
 *   insurance_policies_processed_total       - Counter: total records processed
 *   insurance_policies_failed_total          - Counter: total records failed
 *   insurance_batch_active                   - Gauge: 1 if batch is running, 0 otherwise
 *   insurance_page_processing_duration       - Timer: time per page
 *   insurance_throughput_records_per_second  - Gauge: current throughput
 */
@ApplicationScoped
public class ProcessingMetrics {

    private static final Logger LOG = Logger.getLogger(ProcessingMetrics.class);

    private final Counter processedCounter;
    private final Counter failedCounter;
    private final Timer   pageTimer;

    private final AtomicLong batchActiveGauge    = new AtomicLong(0);
    private final AtomicLong throughputGauge      = new AtomicLong(0);
    private final AtomicLong lastProcessedSnapshot = new AtomicLong(0);

    @Inject
    public ProcessingMetrics(MeterRegistry registry) {
        processedCounter = Counter.builder("insurance.policies.processed.total")
                .description("Total number of insurance policies successfully processed")
                .tag("component", "camel-pipeline")
                .register(registry);

        failedCounter = Counter.builder("insurance.policies.failed.total")
                .description("Total number of insurance policies that failed processing")
                .tag("component", "camel-pipeline")
                .register(registry);

        pageTimer = Timer.builder("insurance.page.processing.duration")
                .description("Time taken to process a single page of policies")
                .tag("component", "camel-pipeline")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(registry);

        Gauge.builder("insurance.batch.active", batchActiveGauge, AtomicLong::get)
                .description("1 if a processing batch is currently active, 0 otherwise")
                .tag("component", "camel-pipeline")
                .register(registry);

        Gauge.builder("insurance.throughput.records.per.second", throughputGauge, AtomicLong::get)
                .description("Approximate records processed per second over last page")
                .tag("component", "camel-pipeline")
                .register(registry);
    }

    public void recordPageProcessed(long processed, long failed, long elapsedMs) {
        processedCounter.increment(processed);
        failedCounter.increment(failed);
        pageTimer.record(elapsedMs, TimeUnit.MILLISECONDS);

        long tps = elapsedMs > 0 ? (processed * 1000) / elapsedMs : 0;
        throughputGauge.set(tps);
    }

    public void markBatchStarted() {
        batchActiveGauge.set(1);
        LOG.debug("Metrics: batch started");
    }

    public void markBatchCompleted() {
        batchActiveGauge.set(0);
        LOG.debug("Metrics: batch completed");
    }

    public double getTotalProcessed() {
        return processedCounter.count();
    }

    public double getTotalFailed() {
        return failedCounter.count();
    }
}
