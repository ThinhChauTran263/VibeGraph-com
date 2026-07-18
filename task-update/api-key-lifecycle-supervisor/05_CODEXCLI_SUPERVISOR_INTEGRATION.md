# Prompt Cho CodexCli

Ban la supervisor/integrator cho vong API key lifecycle.

Nhiem vu:
- Doc tat ca handoff trong `task-update/api-key-lifecycle-supervisor`.
- Kiem tra code khong con admin-create API key:
  - Khong `AdminApiKeyCreateRequest`.
  - Khong `createForUser`.
  - Khong frontend `createApiKeyForUser`.
  - Khong `POST /api/admin/api-keys`.
- Dam bao API key lifecycle moi dong nhat giua backend/frontend/CLI/MCP.
- Chay gate tong hop neu co the.
- Khong commit/push/merge neu user chua noi.

Gate:
- `.\mvnw.cmd "-Dtest=*ApiKey*,*Feature*,*Session*,*Account*,*Mcp*,*LocalPatch*" test`
- `.\mvnw.cmd clean test`
- `npm --prefix vibegraph-web run type-check`
- `npm --prefix vibegraph-web run test:unit -- --run`
- `npm --prefix vibegraph-web run build`
- `git diff --check`
- `npx gitnexus detect-changes --repo VibeGraph-com` hoac lenh GitNexus tuong duong.

Handoff:
- Tao `task-update/api-key-lifecycle-supervisor/CodexCli_SUPERVISOR_HANDOFF.md`
- Ghi merge readiness: READY hoac REQUEST CHANGES.

