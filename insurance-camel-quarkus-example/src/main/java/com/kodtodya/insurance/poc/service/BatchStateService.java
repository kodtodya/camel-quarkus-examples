package com.kodtodya.insurance.poc.service;

import com.kodtodya.insurance.poc.model.ProcessingBatch;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the lifecycle of processing batches.
 * Maintains in-memory state and persists to DB for audit.
 */
@ApplicationScoped
public class BatchStateService {

    private static final Logger LOG = Logger.getLogger(BatchStateService.class);

    private final Map<String, ProcessingBatch> activeBatches = new ConcurrentHashMap<>();
    private final AtomicBoolean processingActive = new AtomicBoolean(false);

    @Inject
    DataSource dataSource;

    /**
     * Creates a new batch and marks processing as active.
     * Returns null if a batch is already running.
     */
    public ProcessingBatch startBatch(String triggeredBy) {
        if (!processingActive.compareAndSet(false, true)) {
            LOG.warn("A processing batch is already running. Rejecting new request.");
            return null;
        }
        String batchId = "BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ProcessingBatch batch = new ProcessingBatch(batchId, triggeredBy);
        activeBatches.put(batchId, batch);
        persistBatch(batch);
        LOG.infof("Started batch [%s] triggered by [%s]", batchId, triggeredBy);
        return batch;
    }

    public void completeBatch(String batchId) {
        ProcessingBatch batch = activeBatches.get(batchId);
        if (batch != null) {
            batch.complete();
            updateBatch(batch);
            processingActive.set(false);
            LOG.infof("Completed batch [%s]: processed=%d, failed=%d, duration=%dms, throughput=%.1f/s",
                      batchId, batch.getProcessedRecords(), batch.getFailedRecords(),
                      batch.getDurationMillis(), batch.getThroughputPerSecond());
        }
    }

    public void failBatch(String batchId, String reason) {
        ProcessingBatch batch = activeBatches.get(batchId);
        if (batch != null) {
            batch.fail();
            updateBatch(batch);
            processingActive.set(false);
            LOG.errorf("Failed batch [%s]: reason=%s", batchId, reason);
        }
    }

    public Optional<ProcessingBatch> getBatch(String batchId) {
        return Optional.ofNullable(activeBatches.get(batchId));
    }

    public Collection<ProcessingBatch> getAllBatches() {
        return activeBatches.values();
    }

    public boolean isProcessingActive() {
        return processingActive.get();
    }

    // -----------------------------------------------
    // DB persistence
    // -----------------------------------------------
    private void persistBatch(ProcessingBatch batch) {
        String sql = "INSERT INTO processing_batch (batch_id, start_time, status, triggered_by, total_records, processed_records, failed_records) " +
                     "VALUES (?, ?, ?, ?, 0, 0, 0)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, batch.getBatchId());
            ps.setTimestamp(2, Timestamp.valueOf(batch.getStartTime()));
            ps.setString(3, batch.getStatus());
            ps.setString(4, batch.getTriggeredBy());
            ps.executeUpdate();
        } catch (Exception e) {
            LOG.warnf("Could not persist batch record: %s", e.getMessage());
        }
    }

    private void updateBatch(ProcessingBatch batch) {
        String sql = "UPDATE processing_batch SET end_time=?, status=?, total_records=?, processed_records=?, failed_records=? WHERE batch_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, batch.getEndTime() != null ? Timestamp.valueOf(batch.getEndTime()) : null);
            ps.setString(2, batch.getStatus());
            ps.setLong(3, batch.getTotalRecords());
            ps.setLong(4, batch.getProcessedRecords());
            ps.setLong(5, batch.getFailedRecords());
            ps.setString(6, batch.getBatchId());
            ps.executeUpdate();
        } catch (Exception e) {
            LOG.warnf("Could not update batch record: %s", e.getMessage());
        }
    }
}
