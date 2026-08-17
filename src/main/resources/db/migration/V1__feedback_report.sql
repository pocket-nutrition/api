-- Public user feedback on ingredient data quality.
-- Side-channel inbox: written only by pocket-nutrition-api, read only by pocket-nutrition-community.
-- NOT part of the knowledge pipeline (never mirrored to Redis / YAML files in a private repository / raw source tables).
CREATE TABLE IF NOT EXISTS feedback_report (
    id              BIGSERIAL   PRIMARY KEY,
    reported_name   TEXT        NOT NULL,
    ingredient_id   TEXT,
    resolved        BOOLEAN     NOT NULL DEFAULT FALSE,
    issue_type      TEXT        NOT NULL,
    comment         TEXT        NOT NULL,
    cooking_method  TEXT,
    measured_state  TEXT,
    source          TEXT,
    source_surface  TEXT        NOT NULL DEFAULT 'sandbox',
    ip_hash         TEXT,
    user_agent      TEXT,
    status          TEXT        NOT NULL DEFAULT 'new',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_feedback_ingredient ON feedback_report (ingredient_id);
CREATE INDEX IF NOT EXISTS idx_feedback_created_at ON feedback_report (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_feedback_status     ON feedback_report (status);
