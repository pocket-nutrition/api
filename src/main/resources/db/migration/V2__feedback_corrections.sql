-- Feedback v2: structured corrections.
-- Additive migration — V1 (feedback_report) is already applied in production.
-- A report is now "≥1 structured correction OR a free-text comment", so both
-- comment and the legacy single issue_type become nullable.
ALTER TABLE feedback_report ADD COLUMN IF NOT EXISTS corrections TEXT;
ALTER TABLE feedback_report ALTER COLUMN comment DROP NOT NULL;
ALTER TABLE feedback_report ALTER COLUMN issue_type DROP NOT NULL;
