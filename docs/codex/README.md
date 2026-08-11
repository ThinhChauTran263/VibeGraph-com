# Codex Senior Playbook cho VibeGraph

Bộ tài liệu này dành cho lúc bạn muốn dùng Codex như một senior engineer: giao việc rõ, kiểm soát phạm vi, chia việc đúng lúc, kiểm chứng bằng bằng chứng và giữ session gọn.

Tài liệu được viết cho Codex CLI `v0.145.0` trên Windows PowerShell. Lệnh hoặc màn hình có thể thay đổi ở các phiên bản sau; khi nghi ngờ, chạy `codex --help` và `/` trong composer để xem danh sách hiện tại.

## Đọc theo thứ tự

1. [Cheat sheet một trang](00-cheat-sheet.md) - các lệnh cần nhớ hằng ngày.
2. [Senior workflow](01-senior-workflow.md) - cách biến yêu cầu mơ hồ thành một lượt làm việc có kiểm soát.
3. [Prompt library](02-prompt-library.md) - prompt copy-paste cho bug, feature, review và handoff.
4. [Multi-agent playbook](03-multi-agent.md) - role, effort, depth, fan-out và cách xác minh rollout.
5. [Sessions và debugging](04-sessions-and-debugging.md) - `/new`, `/resume`, `/fork`, `/compact`, `/status`, `doctor`.
6. [VibeGraph playbook](05-vibegraph-playbook.md) - lệnh backend, frontend, Docker, test và GitNexus của repo này.

## Bộ nhớ tối thiểu

Nếu chỉ nhớ một chuỗi, hãy dùng:

```text
/status -> /plan -> làm việc -> /diff -> test -> /review -> git diff
```

Nếu task có thay đổi code trong VibeGraph, luôn nói rõ:

```text
Không commit. Chỉ sửa trong phạm vi đã nêu. Trước khi sửa symbol,
hãy chạy GitNexus impact analysis; sau đó chạy test liên quan và báo cáo
file, dòng, bằng chứng kiểm chứng và rủi ro còn lại.
```

## Baseline hiện tại của máy này

Global config `C:\Users\User\.codex\config.toml` và project config `.codex/config.toml` đều bật multi-agent và đặt giới hạn `8 / depth 2`. Global config còn đặt effort mặc định của subagent là `max`:

```toml
[features]
multi_agent = true

[agents]
enabled = true
max_concurrent_threads_per_session = 8
max_depth = 2
default_subagent_reasoning_effort = "max"
```

Các role VibeGraph nằm trong `.codex/agents/` và đều là reviewer read-only. Xem chi tiết trong [03-multi-agent.md](03-multi-agent.md).

## Senior loop

Một lượt làm việc tốt thường có sáu nhịp:

1. **Định nghĩa đầu ra:** muốn sửa, điều tra, review hay chỉ báo cáo?
2. **Khoanh phạm vi:** file/module nào nằm trong scope, file nào không được đụng?
3. **Tìm đường đi thật:** đọc code, chạy query/impact, không đoán từ tên file.
4. **Thay đổi nhỏ:** mỗi patch có một lý do và một cách kiểm chứng.
5. **Kiểm chứng độc lập:** test/build/lint hoặc reproducer, không chỉ nhìn diff.
6. **Bàn giao:** nêu file đã đổi, lệnh đã chạy, kết quả và rủi ro còn lại.

## Nguồn chính thức

- [Codex manual](https://developers.openai.com/codex/codex-manual.md)
- [Slash commands](https://learn.chatgpt.com/docs/developer-commands.md?surface=cli)
- [Configuration reference](https://learn.chatgpt.com/docs/config-file/config-reference.md)
- [Subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents.md)
