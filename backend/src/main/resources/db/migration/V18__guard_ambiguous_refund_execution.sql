ALTER TABLE refunds ADD COLUMN execution_claimed BOOLEAN NOT NULL DEFAULT FALSE;
-- Historic unfinished outcomes require provider reconciliation before another transfer.
-- Do not synthesize active markers: multiple historic failures may share the same subject.
UPDATE refunds SET execution_claimed=TRUE WHERE status IN ('PROCESSING','FAILED');
