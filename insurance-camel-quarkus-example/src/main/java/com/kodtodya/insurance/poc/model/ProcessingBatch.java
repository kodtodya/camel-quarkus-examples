package com.kodtodya.insurance.poc.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks the state of a bulk processing batch.
 */
public class ProcessingBatch {

    private String batchId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private AtomicLong totalRecords   = new AtomicLong(0);
    private AtomicLong processedRecords = new AtomicLong(0);
    private AtomicLong failedRecords  = new AtomicLong(0);
    private String status;
    private String triggeredBy;
    private long   durationMillis;

    public ProcessingBatch() {}

    public ProcessingBatch(String batchId, String triggeredBy) {
        this.batchId     = batchId;
        this.triggeredBy = triggeredBy;
        this.startTime   = LocalDateTime.now();
        this.status      = "RUNNING";
    }

    public void complete() {
        this.endTime       = LocalDateTime.now();
        this.status        = "COMPLETED";
        this.durationMillis = java.time.Duration.between(startTime, endTime).toMillis();
    }

    public void fail() {
        this.endTime       = LocalDateTime.now();
        this.status        = "FAILED";
        this.durationMillis = java.time.Duration.between(startTime, endTime).toMillis();
    }

    // -----------------------------------------------
    // Getters & Setters
    // -----------------------------------------------
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public long getTotalRecords() { return totalRecords.get(); }
    public void setTotalRecords(long total) { this.totalRecords.set(total); }
    public void incrementTotal() { this.totalRecords.incrementAndGet(); }

    public long getProcessedRecords() { return processedRecords.get(); }
    public void incrementProcessed() { this.processedRecords.incrementAndGet(); }
    public void addProcessed(long count) { this.processedRecords.addAndGet(count); }

    public long getFailedRecords() { return failedRecords.get(); }
    public void incrementFailed() { this.failedRecords.incrementAndGet(); }
    public void addFailed(long count) { this.failedRecords.addAndGet(count); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }

    public long getDurationMillis() { return durationMillis; }

    public double getThroughputPerSecond() {
        if (durationMillis <= 0) return 0.0;
        return (processedRecords.get() * 1000.0) / durationMillis;
    }
}
