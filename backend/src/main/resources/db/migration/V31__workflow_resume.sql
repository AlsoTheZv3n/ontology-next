-- V31: Resume-later support for workflow runs.
--
-- Prod-08 WaitStep blocked the executing thread for up to 10s — fine for
-- short pauses but unusable for waits in minutes/hours. gap-13 moves long
-- waits to a resume-later pattern: the run is marked PAUSED with a
-- resume_at timestamp, and a scheduler picks it up when due.

ALTER TABLE workflow_runs
    ADD COLUMN IF NOT EXISTS resume_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS current_step INT NOT NULL DEFAULT 0;

-- Partial index for the resume scheduler — most rows are not paused.
CREATE INDEX IF NOT EXISTS idx_workflow_runs_resume_due
    ON workflow_runs(resume_at)
    WHERE status = 'PAUSED';

-- Allow PAUSED in the existing status check. Postgres won't let us alter
-- a CHECK in place, but the original V20 didn't pin a CHECK on status —
-- it used VARCHAR(50) — so this column-level permissiveness is fine.
