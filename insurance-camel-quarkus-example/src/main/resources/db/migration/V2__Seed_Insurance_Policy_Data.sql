-- ============================================================
-- V2__Seed_Insurance_Policy_Data.sql
-- Generates 200,000 insurance policy records using H2's
-- system range table for fast bulk insert
-- ============================================================

-- H2-compatible bulk insert using system_range()
-- We insert in 10 batches of 20,000 to avoid memory pressure

INSERT INTO insurance_policy (
    policy_number,
    policy_type,
    status,
    holder_first_name,
    holder_last_name,
    holder_email,
    holder_phone,
    holder_date_of_birth,
    holder_gender,
    nominee_name,
    nominee_relationship,
    insured_amount,
    premium_amount,
    premium_frequency,
    start_date,
    end_date,
    renewal_date,
    risk_category,
    agent_code,
    branch_code,
    underwriter_code,
    created_at,
    updated_at,
    processed,
    remarks
)
SELECT
    CONCAT('POL-', LPAD(CAST(X AS VARCHAR), 8, '0')),

    CASE MOD(X, 6)
        WHEN 0 THEN 'LIFE'
        WHEN 1 THEN 'HEALTH'
        WHEN 2 THEN 'MOTOR'
        WHEN 3 THEN 'HOME'
        WHEN 4 THEN 'TRAVEL'
        ELSE 'TERM'
    END,

    CASE MOD(X, 8)
        WHEN 0 THEN 'ACTIVE'
        WHEN 1 THEN 'ACTIVE'
        WHEN 2 THEN 'ACTIVE'
        WHEN 3 THEN 'ACTIVE'
        WHEN 4 THEN 'PENDING'
        WHEN 5 THEN 'EXPIRED'
        WHEN 6 THEN 'CANCELLED'
        ELSE 'LAPSED'
    END,

    CASE MOD(X, 20)
        WHEN 0  THEN 'Aarav'
        WHEN 1  THEN 'Vivaan'
        WHEN 2  THEN 'Aditya'
        WHEN 3  THEN 'Vihaan'
        WHEN 4  THEN 'Arjun'
        WHEN 5  THEN 'Priya'
        WHEN 6  THEN 'Ananya'
        WHEN 7  THEN 'Divya'
        WHEN 8  THEN 'Meera'
        WHEN 9  THEN 'Pooja'
        WHEN 10 THEN 'Rohan'
        WHEN 11 THEN 'Kiran'
        WHEN 12 THEN 'Neha'
        WHEN 13 THEN 'Ravi'
        WHEN 14 THEN 'Sunita'
        WHEN 15 THEN 'Amit'
        WHEN 16 THEN 'Shreya'
        WHEN 17 THEN 'Rahul'
        WHEN 18 THEN 'Kavya'
        ELSE 'Deepak'
    END,

    CASE MOD(X, 15)
        WHEN 0  THEN 'Sharma'
        WHEN 1  THEN 'Verma'
        WHEN 2  THEN 'Gupta'
        WHEN 3  THEN 'Singh'
        WHEN 4  THEN 'Kumar'
        WHEN 5  THEN 'Mehta'
        WHEN 6  THEN 'Shah'
        WHEN 7  THEN 'Patel'
        WHEN 8  THEN 'Reddy'
        WHEN 9  THEN 'Nair'
        WHEN 10 THEN 'Iyer'
        WHEN 11 THEN 'Agarwal'
        WHEN 12 THEN 'Joshi'
        WHEN 13 THEN 'Pandey'
        ELSE 'Rao'
    END,

    CONCAT(
        LOWER(CASE MOD(X, 20)
            WHEN 0  THEN 'aarav'    WHEN 1  THEN 'vivaan'  WHEN 2  THEN 'aditya'
            WHEN 3  THEN 'vihaan'   WHEN 4  THEN 'arjun'   WHEN 5  THEN 'priya'
            WHEN 6  THEN 'ananya'   WHEN 7  THEN 'divya'   WHEN 8  THEN 'meera'
            WHEN 9  THEN 'pooja'    WHEN 10 THEN 'rohan'   WHEN 11 THEN 'kiran'
            WHEN 12 THEN 'neha'     WHEN 13 THEN 'ravi'    WHEN 14 THEN 'sunita'
            WHEN 15 THEN 'amit'     WHEN 16 THEN 'shreya'  WHEN 17 THEN 'rahul'
            WHEN 18 THEN 'kavya'    ELSE 'deepak'
        END),
        '.',
        LOWER(CASE MOD(X, 15)
            WHEN 0  THEN 'sharma'   WHEN 1  THEN 'verma'   WHEN 2  THEN 'gupta'
            WHEN 3  THEN 'singh'    WHEN 4  THEN 'kumar'   WHEN 5  THEN 'mehta'
            WHEN 6  THEN 'shah'     WHEN 7  THEN 'patel'   WHEN 8  THEN 'reddy'
            WHEN 9  THEN 'nair'     WHEN 10 THEN 'iyer'    WHEN 11 THEN 'agarwal'
            WHEN 12 THEN 'joshi'    WHEN 13 THEN 'pandey'  ELSE 'rao'
        END),
        X,
        '@insureemail.com'
    ),

    CONCAT('+91-9', LPAD(CAST(MOD(X * 7919, 1000000000) AS VARCHAR), 9, '0')),

    DATEADD('YEAR', -(25 + MOD(X, 40)), CURRENT_DATE),

    CASE MOD(X, 3) WHEN 0 THEN 'MALE' WHEN 1 THEN 'FEMALE' ELSE 'OTHER' END,

    CASE MOD(X, 10)
        WHEN 0 THEN 'Raj Kumar'    WHEN 1 THEN 'Sunita Devi'  WHEN 2 THEN 'Manoj Singh'
        WHEN 3 THEN 'Kavitha Rao'  WHEN 4 THEN 'Suresh Patel' WHEN 5 THEN 'Anita Sharma'
        WHEN 6 THEN 'Vikram Nair'  WHEN 7 THEN 'Reena Gupta'  WHEN 8 THEN 'Arun Mehta'
        ELSE 'Shalini Verma'
    END,

    CASE MOD(X, 5)
        WHEN 0 THEN 'SPOUSE'   WHEN 1 THEN 'CHILD'   WHEN 2 THEN 'PARENT'
        WHEN 3 THEN 'SIBLING'  ELSE 'OTHER'
    END,

    CAST((100000 + MOD(X * 1234567, 9900000)) AS DECIMAL(15,2)),

    CAST((500 + MOD(X * 9871, 49500)) AS DECIMAL(10,2)),

    CASE MOD(X, 4)
        WHEN 0 THEN 'MONTHLY' WHEN 1 THEN 'QUARTERLY'
        WHEN 2 THEN 'HALF_YEARLY' ELSE 'ANNUAL'
    END,

    DATEADD('DAY', -MOD(X, 1825), CURRENT_DATE),

    DATEADD('YEAR', (5 + MOD(X, 25)), DATEADD('DAY', -MOD(X, 1825), CURRENT_DATE)),

    DATEADD('YEAR', 1, DATEADD('DAY', -MOD(X, 365), CURRENT_DATE)),

    CASE MOD(X, 4)
        WHEN 0 THEN 'LOW' WHEN 1 THEN 'MEDIUM'
        WHEN 2 THEN 'HIGH' ELSE 'VERY_HIGH'
    END,

    CONCAT('AGT-', LPAD(CAST(MOD(X, 500) AS VARCHAR), 4, '0')),

    CONCAT('BR-', LPAD(CAST(MOD(X, 100) AS VARCHAR), 3, '0')),

    CONCAT('UW-', LPAD(CAST(MOD(X, 50) AS VARCHAR), 3, '0')),

    DATEADD('DAY', -MOD(X, 365), CURRENT_TIMESTAMP),

    DATEADD('DAY', -MOD(X, 30), CURRENT_TIMESTAMP),

    FALSE,

    CASE MOD(X, 5)
        WHEN 0 THEN 'Standard policy'
        WHEN 1 THEN 'High value client'
        WHEN 2 THEN 'Renewal pending'
        WHEN 3 THEN 'Risk assessed'
        ELSE 'Normal processing'
    END

FROM SYSTEM_RANGE(1, 200000);
