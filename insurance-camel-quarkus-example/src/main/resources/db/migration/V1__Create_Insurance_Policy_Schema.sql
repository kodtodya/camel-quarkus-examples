-- ============================================================
-- V1__Create_Insurance_Policy_Schema.sql
-- Creates the insurance_policy table and indexes
-- ============================================================

CREATE TABLE IF NOT EXISTS insurance_policy (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    policy_number           VARCHAR(30)     NOT NULL,
    policy_type             VARCHAR(30)     NOT NULL,
    status                  VARCHAR(20)     NOT NULL,
    holder_first_name       VARCHAR(100)    NOT NULL,
    holder_last_name        VARCHAR(100)    NOT NULL,
    holder_email            VARCHAR(255)    NOT NULL,
    holder_phone            VARCHAR(20),
    holder_date_of_birth    DATE            NOT NULL,
    holder_gender           VARCHAR(10),
    nominee_name            VARCHAR(200),
    nominee_relationship    VARCHAR(50),
    insured_amount          DECIMAL(15,2)   NOT NULL,
    premium_amount          DECIMAL(10,2)   NOT NULL,
    premium_frequency       VARCHAR(20)     NOT NULL,
    start_date              DATE            NOT NULL,
    end_date                DATE            NOT NULL,
    renewal_date            DATE,
    risk_category           VARCHAR(20)     NOT NULL,
    agent_code              VARCHAR(20),
    branch_code             VARCHAR(20),
    underwriter_code        VARCHAR(20),
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed               BOOLEAN         NOT NULL DEFAULT FALSE,
    processed_at            TIMESTAMP,
    processing_batch_id     VARCHAR(50),
    remarks                 VARCHAR(500),
    CONSTRAINT pk_insurance_policy PRIMARY KEY (id)
);

-- Unique constraint
ALTER TABLE insurance_policy
    ADD CONSTRAINT uq_policy_number UNIQUE (policy_number);

-- Indexes for query performance
CREATE INDEX IF NOT EXISTS idx_policy_status         ON insurance_policy(status);
CREATE INDEX IF NOT EXISTS idx_policy_type           ON insurance_policy(policy_type);
CREATE INDEX IF NOT EXISTS idx_policy_processed      ON insurance_policy(processed);
CREATE INDEX IF NOT EXISTS idx_policy_risk_category  ON insurance_policy(risk_category);
CREATE INDEX IF NOT EXISTS idx_policy_start_date     ON insurance_policy(start_date);
CREATE INDEX IF NOT EXISTS idx_policy_renewal_date   ON insurance_policy(renewal_date);
CREATE INDEX IF NOT EXISTS idx_policy_batch_id       ON insurance_policy(processing_batch_id);
CREATE INDEX IF NOT EXISTS idx_policy_created_at     ON insurance_policy(created_at);

-- Composite index for pagination queries (status + id for keyset pagination)
CREATE INDEX IF NOT EXISTS idx_policy_status_id      ON insurance_policy(status, id);
CREATE INDEX IF NOT EXISTS idx_policy_processed_id   ON insurance_policy(processed, id);

-- Processing stats table
CREATE TABLE IF NOT EXISTS processing_batch (
    batch_id            VARCHAR(50)     NOT NULL,
    start_time          TIMESTAMP       NOT NULL,
    end_time            TIMESTAMP,
    total_records       BIGINT          DEFAULT 0,
    processed_records   BIGINT          DEFAULT 0,
    failed_records      BIGINT          DEFAULT 0,
    status              VARCHAR(20)     NOT NULL DEFAULT 'RUNNING',
    triggered_by        VARCHAR(100),
    CONSTRAINT pk_processing_batch PRIMARY KEY (batch_id)
);

CREATE INDEX IF NOT EXISTS idx_batch_status     ON processing_batch(status);
CREATE INDEX IF NOT EXISTS idx_batch_start_time ON processing_batch(start_time);
