# Claude Short Prompt

Copy only this short prompt into Claude. Do not paste the long task files.

```text
Bạn là Claude, phụ trách Backend + CLI + MCP cho VibeGraph Phase 6.

Workspace:
D:\Users\User\IdeaProjects\VibeGraph

Đừng đọc toàn bộ repo ngay. Trước tiên đọc 2 file này:
task-update/phase-6-user-admin-hardening/README.md
task-update/phase-6-user-admin-hardening/CLAUDE_BACKEND_CLI_MCP.md

Sau đó chỉ bắt đầu Slice 1:
- blocked account realtime/session-state
- login/account blocked safe reason
- product endpoints reject blocked accounts
- tests cho blocked account behavior

Không làm các slice khác cho tới khi tôi bảo tiếp tục.
Không sửa frontend.
Không commit, không push, không merge.
Không hardcode secrets.

Khi xong Slice 1, ghi handoff ngắn vào:
task-update/phase-6-user-admin-hardening/CLAUDE_HANDOFF_SLICE1.md

Handoff gồm:
- files changed
- API contract
- tests run/results
- blockers/questions
```

