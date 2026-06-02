package com.kodtodya.insurance.poc.processor;

import com.kodtodya.insurance.poc.config.PolicyProcessingConfig;
import com.kodtodya.insurance.poc.model.InsurancePolicy;
import com.kodtodya.insurance.poc.model.ProcessingBatch;
import com.kodtodya.insurance.poc.service.PolicyRowMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Paginated DB fetch processor.
 *
 * Strategy: Keyset / offset pagination
 *   - Reads PAGE_SIZE rows at a time using OFFSET+LIMIT.
 *   - Each page is sent as a List<InsurancePolicy> on the exchange body.
 *   - The route's loop terminates when a page has 0 rows.
 *
 * Best practices applied:
 *   - PreparedStatement reuse inside each invocation
 *   - Forward-only, read-only ResultSet cursor (minimal memory)
 *   - Stream / close resources immediately after each page
 */
@ApplicationScoped
public class PolicyPageFetchProcessor implements Processor {

    private static final Logger LOG = Logger.getLogger(PolicyPageFetchProcessor.class);

    // Exchange property keys
    public static final String PROP_PAGE_NUM   = "pageNumber";
    public static final String PROP_BATCH      = "processingBatch";
    public static final String PROP_TOTAL_READ = "totalRead";

    private static final String QUERY =
        "SELECT id, policy_number, policy_type, status, " +
        "       holder_first_name, holder_last_name, holder_email, holder_phone, " +
        "       holder_date_of_birth, holder_gender, " +
        "       nominee_name, nominee_relationship, " +
        "       insured_amount, premium_amount, premium_frequency, " +
        "       start_date, end_date, renewal_date, " +
        "       risk_category, agent_code, branch_code, underwriter_code, " +
        "       created_at, updated_at, processed, processed_at, processing_batch_id, remarks " +
        "FROM insurance_policy " +
        "WHERE processed = FALSE " +
        "ORDER BY id " +
        "LIMIT ? OFFSET ?";

    @Inject
    DataSource dataSource;

    @Inject
    PolicyProcessingConfig config;

    @Inject
    PolicyRowMapper rowMapper;

    @Override
    public void process(Exchange exchange) throws Exception {
        int pageNumber = exchange.getProperty(PROP_PAGE_NUM, 0, Integer.class);
        long offset    = (long) pageNumber * config.pageSize();
        ProcessingBatch batch = exchange.getProperty(PROP_BATCH, ProcessingBatch.class);

        LOG.debugf("[%s] Fetching page %d (offset=%d, limit=%d)",
                   batch != null ? batch.getBatchId() : "N/A", pageNumber, offset, config.pageSize());

        List<InsurancePolicy> page = fetchPage(offset, config.pageSize());

        long totalRead = exchange.getProperty(PROP_TOTAL_READ, 0L, Long.class) + page.size();
        exchange.setProperty(PROP_TOTAL_READ, totalRead);
        exchange.setProperty(PROP_PAGE_NUM, pageNumber + 1);

        exchange.getMessage().setBody(page);

        if (batch != null) {
            batch.setTotalRecords(totalRead);
        }

        LOG.debugf("Page %d fetched %d records (cumulative: %d)", pageNumber, page.size(), totalRead);
    }

    private List<InsurancePolicy> fetchPage(long offset, int limit) throws SQLException {
        List<InsurancePolicy> results = new ArrayList<>(limit);

        try (Connection conn = dataSource.getConnection()) {
            conn.setReadOnly(true);
            try (PreparedStatement ps = conn.prepareStatement(
                    QUERY,
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY)) {

                ps.setFetchSize(Math.min(limit, 500)); // hint driver to stream
                ps.setInt(1, limit);
                ps.setLong(2, offset);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapRow(rs));
                    }
                }
            }
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private InsurancePolicy mapRow(ResultSet rs) throws SQLException {
        // Build a lightweight map to reuse the PolicyRowMapper
        java.util.HashMap<String, Object> row = new java.util.HashMap<>();
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            row.put(md.getColumnName(i).toUpperCase(), rs.getObject(i));
        }
        return rowMapper.map(row);
    }
}
