-- ThaiShopFun | PostgreSQL 17 | schema from Flyway V1/V2
-- Read-only diagnostics; no passwords, token hashes or identity data selected.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '15s';
SET LOCAL TIME ZONE 'Asia/Bangkok';

-- Q01 Connection context
SELECT current_database() AS database_name, current_user AS database_user,
       current_setting('server_version') AS server_version, now() AS checked_at;

-- Q02 Schema columns
SELECT table_name, column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
ORDER BY table_name, ordinal_position;

-- Q03 Flyway migration history (requires successful Flyway initialization)
SELECT installed_rank, version, description, installed_on, success
FROM public.flyway_schema_history ORDER BY installed_rank;

-- Q04 Exact business table counts
SELECT 'users' AS table_name, count(*) AS row_count FROM public.users
UNION ALL SELECT 'refresh_tokens', count(*) FROM public.refresh_tokens
UNION ALL SELECT 'merchant_applications', count(*) FROM public.merchant_applications
UNION ALL SELECT 'merchant_documents', count(*) FROM public.merchant_documents;

-- Q05 Account distribution
SELECT role, status, count(*) AS user_count
FROM public.users GROUP BY role, status ORDER BY role, status;

-- Q06 Registrations by Bangkok calendar date; rolling 30-day window
SELECT (created_at AT TIME ZONE 'Asia/Bangkok')::date AS signup_date,
       count(*) AS signups
FROM public.users WHERE created_at >= now() - interval '30 days'
GROUP BY 1 ORDER BY 1;

-- Q07 Merchant application status distribution
SELECT status, count(*) AS application_count
FROM public.merchant_applications GROUP BY status ORDER BY status;

-- Q08 Oldest pending applications; 7 days is an example review threshold
SELECT id, reference_no, status, submitted_at,
       now() - submitted_at AS waiting_time
FROM public.merchant_applications
WHERE status IN ('SUBMITTED', 'UNDER_REVIEW')
  AND submitted_at < now() - interval '7 days'
ORDER BY submitted_at LIMIT 100;

-- Q09 Missing required document metadata, based on submission service
SELECT a.id, a.reference_no, a.status, required.document_type AS missing_document
FROM public.merchant_applications a
CROSS JOIN (VALUES ('IDENTITY'), ('ADDRESS'), ('BUSINESS')) AS required(document_type)
WHERE (required.document_type <> 'BUSINESS' OR a.owner_type = 'COMPANY')
  AND NOT EXISTS (
    SELECT 1 FROM public.merchant_documents d
    WHERE d.application_id = a.id AND d.document_type = required.document_type
  )
ORDER BY a.id, required.document_type LIMIT 100;

-- Q10 Uploaded metadata totals (not actual disk usage)
SELECT document_type, count(*) AS document_count,
       coalesce(sum(file_size), 0) AS total_bytes
FROM public.merchant_documents GROUP BY document_type ORDER BY document_type;

-- Q11 Refresh-token state summary; categories are mutually exclusive
SELECT CASE WHEN revoked_at IS NOT NULL THEN 'REVOKED'
            WHEN expires_at <= now() THEN 'EXPIRED'
            ELSE 'ACTIVE' END AS token_state, count(*) AS token_count
FROM public.refresh_tokens GROUP BY 1 ORDER BY 1;

-- Q12 Database size
SELECT current_database() AS database_name,
       pg_size_pretty(pg_database_size(current_database())) AS database_size;

-- Q13 Table and index sizes
SELECT relname AS table_name,
       pg_size_pretty(pg_table_size(relid)) AS table_size,
       pg_size_pretty(pg_indexes_size(relid)) AS index_size,
       pg_size_pretty(pg_total_relation_size(relid)) AS total_size
FROM pg_stat_user_tables WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(relid) DESC;

-- Q14 Active sessions; deliberately omit query text
SELECT pid, state, wait_event_type, wait_event,
       now() - query_start AS query_age
FROM pg_stat_activity
WHERE datname = current_database() AND pid <> pg_backend_pid()
  AND state = 'active'
ORDER BY query_start LIMIT 50;

-- Q15 Blocked sessions; visibility depends on database privileges
SELECT pid, wait_event_type, wait_event, pg_blocking_pids(pid) AS blocking_pids
FROM pg_stat_activity
WHERE datname = current_database() AND cardinality(pg_blocking_pids(pid)) > 0;

-- Q16 Estimated query plan; EXPLAIN without ANALYZE does not execute the SELECT
EXPLAIN (COSTS TRUE)
SELECT id, reference_no, submitted_at
FROM public.merchant_applications
WHERE status = 'SUBMITTED' ORDER BY submitted_at LIMIT 50;

ROLLBACK;
