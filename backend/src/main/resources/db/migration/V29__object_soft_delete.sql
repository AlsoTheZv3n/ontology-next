-- V29: Soft-delete + merge tracking for ontology_objects.
--
-- Prod-12's confirmMerge hard-deleted the loser row and relied on ON DELETE
-- CASCADE to sweep up links, comments, lineage, etc. That made a wrong merge
-- irreversible: you can't unmerge if the loser row is gone, and external
-- references (cached URLs, audit logs) dangle forever.
--
-- This migration adds:
--   * deleted_at   — soft-delete tombstone
--   * merged_into  — redirect pointer so "where did this object go?" still
--                    resolves after a merge
--   * merged_at    — timeline
--   * merged_by    — who confirmed the merge (for audit)
--
-- Reads that want active objects MUST filter deleted_at IS NULL.
-- Reads that want to follow a merge chain use merged_into (see
-- OntologyObjectService.findEffective).

ALTER TABLE ontology_objects
    ADD COLUMN IF NOT EXISTS deleted_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS merged_into UUID REFERENCES ontology_objects(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS merged_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS merged_by   TEXT;

-- Partial index for the common "list active objects of type X" path.
CREATE INDEX IF NOT EXISTS idx_ontology_objects_active
    ON ontology_objects(tenant_id, object_type_id)
    WHERE deleted_at IS NULL;

-- Look up a loser by its merged_into pointer efficiently (for redirects).
CREATE INDEX IF NOT EXISTS idx_ontology_objects_merged_into
    ON ontology_objects(merged_into)
    WHERE merged_into IS NOT NULL;
