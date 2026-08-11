# Multi-Agent Playbook

Multi-agent giúp giảm context noise và chạy các lane độc lập song song. Nó không tự làm từng agent thông minh hơn, và nhiều agent hơn không đồng nghĩa kết quả tốt hơn.

## Cấu hình hiện tại

```toml
[features]
multi_agent = true

[agents]
enabled = true
max_concurrent_threads_per_session = 8
max_depth = 2
default_subagent_reasoning_effort = "max"
```

Ý nghĩa:

- Tối đa 8 spawned-agent thread đang mở, không tính main agent.
- `depth = 2` cho phép `main -> subagent -> sub-subagent`.
- Đây là trần, không phải target. Task nhỏ có thể dùng 0 agent.
- Giới hạn áp dụng cho toàn cây, không phải mỗi parent được thêm 8 agent.

## Role đang có

| Scope | Role | Effort hiện tại | Chức năng |
|---|---|---|---|
| Global | `explorer` | `high` | Map code và evidence read-only |
| Global | `reviewer` | `high` | Correctness, regression, tests |
| Global | `docs_researcher` | `high` | Xác minh API/tài liệu chính thức |
| VibeGraph | `java_reviewer` | `max` kế thừa default | Spring/Java/PostgreSQL/Neo4j review |
| VibeGraph | `typescript_reviewer` | `max` kế thừa default | Vue/TS/Pinia/Vite review |
| VibeGraph | `security_reviewer` | `max` kế thừa default | Auth/session/input/secrets review |
| VibeGraph | `architect` | `max` kế thừa default | Boundary, failure modes, scaling |

Các role project hiện có `sandbox_mode = "read-only"`. Chúng review và trả evidence; main agent hoặc worker riêng mới nên sửa code.

## Effort precedence

Codex giải từng setting theo thứ tự:

```text
agent TOML -> explicit spawn value -> [agents] default -> parent value
```

Vì `explorer`, `reviewer`, `docs_researcher` pin `high` trong agent TOML, chúng vẫn chạy `high` dù default global là `max`.

## Ba cách giao việc

### Cho Codex tự quyết định

```text
Tự quyết định có cần subagent không. Nếu cần, chỉ chia các lane độc lập,
dùng tối đa 8 agent/depth 2, chờ kết quả rồi tổng hợp evidence.
```

### Ép đúng role

```text
Dùng java_reviewer và security_reviewer song song để review thay đổi auth.
Cả hai read-only. Java reviewer tập trung transaction/query/tests;
security reviewer tập trung attack path và session lifecycle.
```

### Fan-out toàn diện

```text
Spawn 4 role: java_reviewer, typescript_reviewer, security_reviewer, architect.
Mỗi role chỉ sở hữu lane của mình. Chờ tất cả, loại findings trùng,
rồi tổng hợp theo severity với file/line và test gaps.
```

Trong CLI dùng `/agent` hoặc `/subagents` để xem thread con và chuyển sang agent đang chạy.

## Khi nào không nên dùng

- Một lỗi nhỏ trong một method.
- Các agent phải sửa cùng file hoặc cùng symbol.
- Chưa hiểu problem nhưng đã chia implementation thành nhiều lane.
- Task có một dependency tuần tự chặt; agent sau phải chờ output agent trước.
- Token/rate limit đang căng.

## Session và context

- Subagent nhận task/context mà main gửi cho nó và trả summary về main.
- Một top-level session mới không tự đọc transcript của session khác.
- Các session trong cùng workspace vẫn nhìn thấy cùng file và working tree.
- Nếu muốn truyền quyết định bền vững, tạo handoff Markdown thay vì kỳ vọng session tự nhớ.

## Xác minh model, effort và depth thật

`doctor` chỉ xác nhận config load. Sau khi spawn agent, chạy PowerShell sau để đọc rollout mới nhất:

```powershell
$subagentFiles = Get-ChildItem "$env:USERPROFILE\.codex\sessions" `
  -Recurse -Filter "rollout-*.jsonl" |
  Sort-Object LastWriteTime -Descending

$result = foreach ($subagentFile in $subagentFiles) {
  $metaMatch = Select-String -LiteralPath $subagentFile.FullName `
    -Pattern '"type"\s*:\s*"session_meta"' | Select-Object -First 1

  if (-not $metaMatch) { continue }

  $meta = ($metaMatch.Line | ConvertFrom-Json).payload
  if ($meta.thread_source -ne "subagent") { continue }

  $turnMatch = Select-String -LiteralPath $subagentFile.FullName `
    -Pattern '"type"\s*:\s*"turn_context"' | Select-Object -First 1
  $turn = ($turnMatch.Line | ConvertFrom-Json).payload

  [pscustomobject]@{
    Role     = $meta.agent_role
    Depth    = $meta.source.subagent.thread_spawn.depth
    Provider = $meta.model_provider
    Model    = $turn.model
    Effort   = $turn.effort
    Version  = $turn.multi_agent_version
    File     = $subagentFile.FullName
  }
  break
}

$result | Format-List
```

Probe thực tế trên máy này đã xác nhận:

```text
java_reviewer  depth 1  cx/gpt-5.6-sol  max
architect      depth 1  cx/gpt-5.6-sol  max
explorer       depth 2  cx/gpt-5.6-sol  high
```

