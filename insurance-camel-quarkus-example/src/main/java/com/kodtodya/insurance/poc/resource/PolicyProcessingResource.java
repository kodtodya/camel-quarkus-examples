package com.kodtodya.insurance.poc.resource;

import com.kodtodya.insurance.poc.metrics.ProcessingMetrics;
import com.kodtodya.insurance.poc.model.ProcessingBatch;
import com.kodtodya.insurance.poc.processor.PolicyPageFetchProcessor;
import com.kodtodya.insurance.poc.service.BatchStateService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * REST API for triggering and monitoring the insurance policy bulk processing pipeline.
 *
 * Base path: /api/v1/policies
 */
@Path("/api/v1/policies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Insurance Policy Processing", description = "Endpoints to trigger and monitor bulk policy processing")
public class PolicyProcessingResource {

    private static final Logger LOG = Logger.getLogger(PolicyProcessingResource.class);

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    BatchStateService batchService;

    @Inject
    ProcessingMetrics metrics;

    // ============================================================
    // POST /api/v1/policies/process/start
    // Trigger a new bulk processing batch
    // ============================================================
    @POST
    @Path("/process/start")
    @Operation(
        summary = "Start bulk policy processing",
        description = "Initiates the Camel pipeline to paginate and process all unprocessed insurance policies. " +
                      "Returns 202 Accepted immediately; processing runs asynchronously. " +
                      "Only one batch can run at a time."
    )
    @APIResponse(responseCode = "202", description = "Batch accepted and started")
    @APIResponse(responseCode = "409", description = "A batch is already running")
    public Response startProcessing(
            @QueryParam("triggeredBy") @DefaultValue("REST_API") String triggeredBy) {

        if (batchService.isProcessingActive()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of(
                        "error", "A processing batch is already running",
                        "timestamp", LocalDateTime.now().toString()
                    ))
                    .build();
        }

        ProcessingBatch batch = batchService.startBatch(triggeredBy);
        if (batch == null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Failed to create batch — another batch may have started concurrently"))
                    .build();
        }

        metrics.markBatchStarted();

        // Fire and forget — Camel route runs asynchronously via SEDA
        producerTemplate.asyncSend("direct:startBulkProcessing", exchange -> {
            exchange.setProperty(PolicyPageFetchProcessor.PROP_BATCH, batch);
            exchange.getMessage().setBody(null);
        });

        LOG.infof("Batch [%s] triggered via REST by [%s]", batch.getBatchId(), triggeredBy);

        return Response.accepted()
                .location(URI.create("/api/v1/policies/process/status/" + batch.getBatchId()))
                .entity(Map.of(
                    "batchId",     batch.getBatchId(),
                    "status",      batch.getStatus(),
                    "startTime",   batch.getStartTime().toString(),
                    "triggeredBy", batch.getTriggeredBy(),
                    "message",     "Processing started. Poll /api/v1/policies/process/status/" + batch.getBatchId()
                ))
                .build();
    }

    // ============================================================
    // GET /api/v1/policies/process/status/{batchId}
    // Get status of a specific batch
    // ============================================================
    @GET
    @Path("/process/status/{batchId}")
    @Operation(
        summary = "Get batch status",
        description = "Returns the current status, record counts, and throughput for a specific batch"
    )
    @APIResponse(responseCode = "200", description = "Batch found")
    @APIResponse(responseCode = "404", description = "Batch not found")
    public Response getBatchStatus(@PathParam("batchId") String batchId) {
        return batchService.getBatch(batchId)
                .map(batch -> Response.ok(buildBatchResponse(batch)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Batch not found: " + batchId))
                        .build());
    }

    // ============================================================
    // GET /api/v1/policies/process/status
    // List all batches (in-memory)
    // ============================================================
    @GET
    @Path("/process/status")
    @Operation(summary = "List all batches", description = "Returns all processing batches (in-memory, current session only)")
    public Response getAllBatches() {
        Collection<ProcessingBatch> batches = batchService.getAllBatches();
        return Response.ok(Map.of(
            "total",     batches.size(),
            "batches",   batches.stream().map(this::buildBatchResponse).toList(),
            "timestamp", LocalDateTime.now().toString()
        )).build();
    }

    // ============================================================
    // GET /api/v1/policies/process/active
    // Is a batch currently running?
    // ============================================================
    @GET
    @Path("/process/active")
    @Operation(summary = "Check if processing is active")
    public Response isActive() {
        boolean active = batchService.isProcessingActive();
        return Response.ok(Map.of(
            "active",    active,
            "timestamp", LocalDateTime.now().toString()
        )).build();
    }

    // ============================================================
    // GET /api/v1/policies/metrics/summary
    // Custom metrics summary
    // ============================================================
    @GET
    @Path("/metrics/summary")
    @Operation(summary = "Processing metrics summary", description = "Returns Micrometer-backed processing statistics")
    public Response getMetricsSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalProcessed",       metrics.getTotalProcessed());
        summary.put("totalFailed",          metrics.getTotalFailed());
        summary.put("batchCurrentlyActive", batchService.isProcessingActive());
        summary.put("timestamp",            LocalDateTime.now().toString());
        summary.put("prometheusEndpoint",   "/q/metrics");
        summary.put("healthEndpoint",       "/q/health");
        return Response.ok(summary).build();
    }

    // ============================================================
    // GET /api/v1/policies/info
    // Application info endpoint
    // ============================================================
    @GET
    @Path("/info")
    @Operation(summary = "Application info")
    public Response info() {
        return Response.ok(Map.of(
            "application",    "Insurance Policy Camel POC",
            "version",        "1.0.0",
            "framework",      "Quarkus 3.17.7 + Apache Camel 4.x",
            "database",       "H2 (TCP Server Mode)",
            "h2Console",      "http://localhost:8080/h2-console",
            "h2StandaloneConsole", "http://localhost:8082",
            "swagger",        "http://localhost:8080/swagger-ui",
            "prometheus",     "http://localhost:8080/q/metrics",
            "health",         "http://localhost:8080/q/health",
            "timestamp",      LocalDateTime.now().toString()
        )).build();
    }

    // ============================================================
    // Helper
    // ============================================================
    private Map<String, Object> buildBatchResponse(ProcessingBatch batch) {
        Map<String, Object> map = new HashMap<>();
        map.put("batchId",           batch.getBatchId());
        map.put("status",            batch.getStatus());
        map.put("triggeredBy",       batch.getTriggeredBy());
        map.put("startTime",         batch.getStartTime() != null ? batch.getStartTime().toString() : null);
        map.put("endTime",           batch.getEndTime() != null ? batch.getEndTime().toString() : null);
        map.put("totalRecords",      batch.getTotalRecords());
        map.put("processedRecords",  batch.getProcessedRecords());
        map.put("failedRecords",     batch.getFailedRecords());
        map.put("durationMillis",    batch.getDurationMillis());
        map.put("throughputPerSec",  String.format("%.1f", batch.getThroughputPerSecond()));
        return map;
    }
}
