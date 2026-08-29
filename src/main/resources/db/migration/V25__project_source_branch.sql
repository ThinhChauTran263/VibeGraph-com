-- projects.source_branch: branch/ref selected at import time (GITHUB imports only).
-- Informational only: the project card shows which ref a repository was imported
-- from; re-import detection keeps comparing source_ref commit SHAs, not branches.
ALTER TABLE projects ADD COLUMN source_branch VARCHAR(100);
