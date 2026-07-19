# Prompt Cho Droid

Ban la owner reverify CLI/MCP voi project-bound API key lifecycle moi.

Doc:
- `task-update/api-key-lifecycle-supervisor/README.md`
- Backend handoff cua CladueCli neu co.
- Frontend handoff cua opencode neu co.

Khong commit/push/merge. Khong revert thay doi cua agent khac.

Scope:
- API-key auth filter/context.
- MCP callback/tool handling.
- CLI/local patch/API-key project binding enforcement.
- Tests lien quan MCP/CLI/API key context.

Can xac minh:
- API key disabled by user hoac admin deu khong dung duoc voi CLI/MCP.
- API key deleted khong dung duoc.
- API key bound project A khong duoc dung cho project B.
- Admin-locked key khong bi user workaround bang cach tao key moi cho cung project.
- Khong co endpoint admin-create key nao duoc CLI/MCP phu thuoc.

Gate:
- Backend focused tests cho `*ApiKey*,*Mcp*,*LocalPatch*,*Cli*` neu ton tai.
- `git diff --check`

Handoff:
- Tao `task-update/api-key-lifecycle-supervisor/Droid_CLI_MCP_REVERIFY_HANDOFF.md`
- Ghi ro pass/fail va bat ky backend gap nao phai tra lai CladueCli.

