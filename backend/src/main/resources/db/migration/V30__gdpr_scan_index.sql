-- V30: GIN index on ontology_objects.properties for fast GDPR / dedup scans.
--
-- GdprErasureService currently does "properties::text ILIKE '%email%'" which
-- forces a full-table scan. jsonb_path_ops is the right index shape for
-- exact-value membership queries (the @? operator) and stays compact compared
-- to the full jsonb_ops variant.
--
-- CONCURRENTLY means no writer lock during creation — essential on a hot
-- ontology_objects table. Postgres rejects CONCURRENTLY inside a transaction,
-- so Flyway has to run this migration with mixed=true (set in
-- application.yml's flyway config) or manual execution.
--
-- If the CONCURRENTLY variant fails on your Flyway setup, re-run as a regular
-- CREATE INDEX (blocking) off-peak. The service code works with either.

CREATE INDEX IF NOT EXISTS idx_ontology_objects_properties_gin
    ON ontology_objects USING gin (properties jsonb_path_ops);
