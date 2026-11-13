-- Public confidence votes on ingredient↔source mapping associations (upvote/downvote).
-- Side-channel tally, like feedback_report: written only by pocket-nutrition-api, not part of
-- the knowledge pipeline (never mirrored to Redis / knowledge YAML files / raw source tables).
CREATE TABLE IF NOT EXISTS mapping_vote_tally (
    id              BIGSERIAL   PRIMARY KEY,
    source          TEXT        NOT NULL,
    source_id       TEXT        NOT NULL,
    cooking_method  TEXT        NOT NULL,
    upvotes         INTEGER     NOT NULL DEFAULT 0,
    downvotes       INTEGER     NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_mapping_vote_tally_key
    ON mapping_vote_tally (source, source_id, cooking_method);
