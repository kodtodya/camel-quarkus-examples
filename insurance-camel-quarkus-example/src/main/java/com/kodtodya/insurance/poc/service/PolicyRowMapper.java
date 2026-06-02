package com.kodtodya.insurance.poc.service;

import com.kodtodya.insurance.poc.model.InsurancePolicy;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Converts a raw SQL result row (Map<String,Object>) into an InsurancePolicy domain object.
 * Used within Camel processors after SQL component queries.
 */
@ApplicationScoped
public class PolicyRowMapper {

    private static final Logger LOG = Logger.getLogger(PolicyRowMapper.class);

    public InsurancePolicy map(Map<String, Object> row) {
        InsurancePolicy policy = new InsurancePolicy();

        try {
            policy.setId(toLong(row.get("ID")));
            policy.setPolicyNumber(toString(row.get("POLICY_NUMBER")));
            policy.setPolicyType(toString(row.get("POLICY_TYPE")));
            policy.setStatus(toString(row.get("STATUS")));

            policy.setHolderFirstName(toString(row.get("HOLDER_FIRST_NAME")));
            policy.setHolderLastName(toString(row.get("HOLDER_LAST_NAME")));
            policy.setHolderEmail(toString(row.get("HOLDER_EMAIL")));
            policy.setHolderPhone(toString(row.get("HOLDER_PHONE")));
            policy.setHolderDateOfBirth(toLocalDate(row.get("HOLDER_DATE_OF_BIRTH")));
            policy.setHolderGender(toString(row.get("HOLDER_GENDER")));

            policy.setNomineeName(toString(row.get("NOMINEE_NAME")));
            policy.setNomineeRelationship(toString(row.get("NOMINEE_RELATIONSHIP")));

            policy.setInsuredAmount(toBigDecimal(row.get("INSURED_AMOUNT")));
            policy.setPremiumAmount(toBigDecimal(row.get("PREMIUM_AMOUNT")));
            policy.setPremiumFrequency(toString(row.get("PREMIUM_FREQUENCY")));

            policy.setStartDate(toLocalDate(row.get("START_DATE")));
            policy.setEndDate(toLocalDate(row.get("END_DATE")));
            policy.setRenewalDate(toLocalDate(row.get("RENEWAL_DATE")));

            policy.setRiskCategory(toString(row.get("RISK_CATEGORY")));
            policy.setAgentCode(toString(row.get("AGENT_CODE")));
            policy.setBranchCode(toString(row.get("BRANCH_CODE")));
            policy.setUnderwriterCode(toString(row.get("UNDERWRITER_CODE")));

            policy.setCreatedAt(toLocalDateTime(row.get("CREATED_AT")));
            policy.setUpdatedAt(toLocalDateTime(row.get("UPDATED_AT")));
            policy.setProcessed(toBoolean(row.get("PROCESSED")));
            policy.setProcessedAt(toLocalDateTime(row.get("PROCESSED_AT")));
            policy.setProcessingBatchId(toString(row.get("PROCESSING_BATCH_ID")));
            policy.setRemarks(toString(row.get("REMARKS")));

        } catch (Exception e) {
            LOG.warnf("Error mapping row to InsurancePolicy: %s", e.getMessage());
        }

        return policy;
    }

    // -----------------------------------------------
    // Type-safe conversion helpers
    // -----------------------------------------------
    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        return Long.parseLong(val.toString());
    }

    private String toString(Object val) {
        return val == null ? null : val.toString();
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(val.toString());
    }

    private LocalDate toLocalDate(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDate ld) return ld;
        if (val instanceof Date d) return d.toLocalDate();
        if (val instanceof Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        return null;
    }

    private LocalDateTime toLocalDateTime(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDateTime ldt) return ldt;
        if (val instanceof Timestamp ts) return ts.toLocalDateTime();
        return null;
    }

    private Boolean toBoolean(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        String s = val.toString().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }
}
