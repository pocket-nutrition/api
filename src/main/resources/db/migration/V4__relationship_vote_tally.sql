-- Public confidence votes on curated cooking relationships (upvote/downvote).
-- Side-channel tally, like feedback_report and mapping_vote_tally: written only by
-- pocket-nutrition-api, not part of the knowledge pipeline (never mirrored to Redis / knowledge YAML files).
-- Deliberately keyed on the curated relationship (ingredient_id, cooking_method) rather than
-- on a v2_training_pairs row — that table is rebuilt by ingest on every run and has no stable
-- identity to vote on.
CREATE TABLE IF NOT EXISTS relationship_vote_tally (
    id              BIGSERIAL   PRIMARY KEY,
    ingredient_id   TEXT        NOT NULL,
    cooking_method  TEXT        NOT NULL,
    upvotes         INTEGER     NOT NULL DEFAULT 0,
    downvotes       INTEGER     NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_relationship_vote_tally_key
    ON relationship_vote_tally (ingredient_id, cooking_method);
