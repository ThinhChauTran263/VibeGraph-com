# Prompt Cho gemini

Ban la final strict reviewer. Hay review cuc ky khat khe API key lifecycle moi.

Doc:
- `task-update/api-key-lifecycle-supervisor/README.md`
- Tat ca handoff trong folder nay.
- Diff thuc te cua repo.

Review criteria:
- Admin khong con tao API key dum user o backend, frontend, tests, docs contract moi.
- User key lifecycle dung:
  - create project-bound.
  - disable.
  - delete neu khong admin locked.
  - one key per project until deleted.
  - admin-locked key chan delete va chan replacement.
- Secret raw chi hien mot lan khi create, khong luu list/store persistent.
- CLI/MCP khong bypass project binding, disabled, deleted, admin lock.
- Authorization ownership day du.
- Race condition: tao hai key dong thoi cho cung project khong tao duplicate. Can DB constraint hoac transaction/lock ro.
- API errors ro rang va FE fail closed.
- Khong JWT localStorage, khong browser alert/confirm, khong mock business logic production.

Hay tra verdict:
- PASS neu merge duoc.
- REQUEST CHANGES neu con bug/risk.

Output:
- Ghi vao `task-update/api-key-lifecycle-supervisor/GEMINI_FINAL_REVIEW.md`
- Findings theo severity voi file/line.

