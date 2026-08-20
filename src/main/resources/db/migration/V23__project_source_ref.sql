-- projects.source_ref: commit SHA of the imported source (GITHUB imports only).
-- Powers re-import detection: importing the same repository again compares the current
-- HEAD SHA against this value — equal blocks as "up to date", different refreshes the
-- existing project in place instead of creating a duplicate row.
ALTER TABLE projects ADD COLUMN source_ref VARCHAR(64);
