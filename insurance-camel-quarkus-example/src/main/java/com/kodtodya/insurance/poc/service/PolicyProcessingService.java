package com.kodtodya.insurance.poc.service;

import com.kodtodya.insurance.poc.model.InsurancePolicy;
import com.kodtodya.insurance.poc.model.ProcessingBatch;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Core business logic for processing insurance policies.
 * Called by Camel processors to apply business rules,
 * risk assessment, and enrichment.
 */
@ApplicationScoped
public class PolicyProcessingService {

    private static final Logger LOG = Logger.getLogger(PolicyProcessingService.class);

    private static final BigDecimal HIGH_VALUE_THRESHOLD      = new BigDecimal("5000000");
    private static final BigDecimal VERY_HIGH_VALUE_THRESHOLD = new BigDecimal("10000000");

    /**
     * Process a single insurance policy.
     * Applies enrichment, validation, and business rules.
     */
    public InsurancePolicy processPolicy(InsurancePolicy policy, ProcessingBatch batch) {
        try {
            validatePolicy(policy);
            enrichPolicy(policy, batch);
            applyBusinessRules(policy);
            policy.setProcessed(Boolean.TRUE);
            policy.setProcessedAt(java.time.LocalDateTime.now());
            batch.incrementProcessed();
        } catch (Exception e) {
            LOG.warnf("Failed to process policy [%s]: %s", policy.getPolicyNumber(), e.getMessage());
            policy.setRemarks("PROCESSING_FAILED: " + e.getMessage());
            batch.incrementFailed();
        }
        return policy;
    }

    /**
     * Process a list (page) of policies in bulk.
     * Designed for use within a Camel Splitter or multi-threaded processor.
     */
    public void processBatch(List<InsurancePolicy> policies, ProcessingBatch batch) {
        int pageSize = policies.size();
        LOG.debugf("Processing page of %d policies for batch [%s]", pageSize, batch.getBatchId());
        for (InsurancePolicy policy : policies) {
            processPolicy(policy, batch);
        }
    }

    // -----------------------------------------------
    // Validation
    // -----------------------------------------------
    private void validatePolicy(InsurancePolicy policy) {
        if (policy.getPolicyNumber() == null || policy.getPolicyNumber().isBlank()) {
            throw new IllegalArgumentException("Policy number is blank");
        }
        if (policy.getInsuredAmount() == null || policy.getInsuredAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid insured amount");
        }
        if (policy.getEndDate() != null && policy.getStartDate() != null
                && policy.getEndDate().isBefore(policy.getStartDate())) {
            throw new IllegalArgumentException("End date before start date");
        }
    }

    // -----------------------------------------------
    // Enrichment
    // -----------------------------------------------
    private void enrichPolicy(InsurancePolicy policy, ProcessingBatch batch) {
        // Tag with batch
        policy.setProcessingBatchId(batch.getBatchId());

        // Derive / override risk category based on insured amount
        if (policy.getInsuredAmount() != null) {
            if (policy.getInsuredAmount().compareTo(VERY_HIGH_VALUE_THRESHOLD) >= 0) {
                policy.setRiskCategory("VERY_HIGH");
            } else if (policy.getInsuredAmount().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
                policy.setRiskCategory("HIGH");
            }
        }
    }

    // -----------------------------------------------
    // Business Rules
    // -----------------------------------------------
    private void applyBusinessRules(InsurancePolicy policy) {
        // Rule 1: Policies expiring within 30 days → flag for renewal
        if (policy.getEndDate() != null) {
            LocalDate thirtyDaysFromNow = LocalDate.now().plusDays(30);
            if (!policy.getEndDate().isAfter(thirtyDaysFromNow) && "ACTIVE".equals(policy.getStatus())) {
                policy.setRemarks("RENEWAL_ALERT: Policy expires within 30 days");
            }
        }

        // Rule 2: Lapsed policies older than 1 year → flag for write-off review
        if ("LAPSED".equals(policy.getStatus()) && policy.getEndDate() != null) {
            if (policy.getEndDate().isBefore(LocalDate.now().minusYears(1))) {
                policy.setRemarks("WRITE_OFF_REVIEW: Lapsed over 1 year");
            }
        }

        // Rule 3: High-risk policies without underwriter → alert
        if ("VERY_HIGH".equals(policy.getRiskCategory()) &&
                (policy.getUnderwriterCode() == null || policy.getUnderwriterCode().isBlank())) {
            policy.setRemarks("UNDERWRITER_REQUIRED: Very high risk policy without underwriter");
        }
    }
}
