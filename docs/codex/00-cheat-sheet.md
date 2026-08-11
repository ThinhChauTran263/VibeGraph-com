# Codex Cheat Sheet

Mở file này khi bạn quên lệnh. Trong Codex CLI, gõ `/` để mở command popup và lọc tên lệnh.

## 12 lệnh slash cần nhớ

| Lệnh | Dùng để làm gì | Khi dùng |
|---|---|---|
| `/status` | Xem model, approval, sandbox, cwd và token | Mở đầu hoặc khi nghi ngờ cấu hình |
| `/debug-config` | Xem thứ tự các lớp config và policy | Khi project/global config có vẻ ghi đè nhau |
| `/model` | Đổi model và reasoning effort | Trước task khó hoặc task cần nhanh |
| `/plan` | Bật plan mode và yêu cầu kế hoạch | Feature lớn, refactor, migration |
| `/agent` | Xem/chuyển sang subagent | Khi đang chạy multi-agent |
| `/diff` | Xem diff kể cả file untracked | Trước test và trước commit |
| `/review` | Yêu cầu review working tree | Sau khi code xong |
| `/compact` | Tóm tắt chat để giải phóng context | Chat dài, output nhiều |
| `/new` | Tạo chat mới trong cùng CLI | Đổi outcome, giữ terminal |
| `/resume` | Mở lại chat đã lưu | Tiếp tục session cũ |
| `/fork` | Nhánh chat mới giữ transcript hiện tại | Thử hướng khác, không phá nhánh gốc |
| `/mention` | Đính kèm file/folder vào prompt | Muốn chỉ rõ evidence |

Các lệnh hữu ích khác: `/permissions`, `/mcp`, `/skills`, `/goal`, `/ps`, `/stop`, `/rename`, `/quit`.

## Shell diagnostics (PowerShell)

```powershell
# Kiểm tra Codex, config, auth, MCP và runtime
codex --strict-config doctor --summary

# Xem toàn bộ feature flag (multi_agent, plugins, hooks...)
codex features list

# Xem MCP server đang bật
codex mcp list

# Xem phiên bản
codex --version

# Mở session gần nhất
codex resume --last

# Fork session gần nhất
codex fork --last
```

`doctor --summary` xác nhận config có load được; nó không chứng minh một subagent cụ thể đã chạy với effort nào. Muốn kiểm tra effort, xem [03-multi-agent.md](03-multi-agent.md).

## One-off override, không sửa file config

CLI flag có precedence cao nhất. Dùng khi chỉ muốn thử một lượt:

```powershell
# Chạy một lượt với read-only và effort nhẹ hơn
codex -s read-only -c 'model_reasoning_effort="high"' "Review read-only phần auth và chỉ báo cáo findings."

# Tạm giới hạn fan-out cho một task
codex -c 'agents.max_concurrent_threads_per_session=2' "Dùng tối đa 2 subagent để phân tích bug này."
```

Đừng dùng one-off override để che một config sai lâu dài; sửa đúng lớp config nếu giá trị cần tồn tại cho mọi session.

## Non-interactive / CI

```powershell
# Chạy một prompt và trả output cuối cùng
codex exec "Chạy test liên quan, phân tích failure và báo cáo nguyên nhân. Không sửa file."

# JSONL để pipeline đọc từng event
codex exec --json "Review working tree, không sửa file." | Tee-Object codex-events.jsonl

# Review thay đổi chưa commit
codex review --uncommitted "Ưu tiên bug, security và missing tests; cite file/line."

# Lưu message cuối vào file
codex exec -o codex-last-message.md "Tóm tắt trạng thái repo và các bước tiếp theo."
```

## Chọn permission đúng mức

| Tình huống | Gợi ý |
|---|---|
| Đọc code, review, docs | `read-only` |
| Sửa code trong repo | `workspace-write` |
| Cần quyền ngoài workspace | Dừng, nêu chính xác lý do và xin approval |
| Production/destructive command | Không tự suy đoán; xác nhận scope trước |

Không dùng `--dangerously-bypass-approvals-and-sandbox` cho workflow thường ngày.

## Prompt ngắn nhưng đủ chuẩn

```text
Mục tiêu: <một câu>
Phạm vi: <module/file>
Ràng buộc: <không commit / read-only / giữ API>
Kiểm chứng: <test/build/reproducer>
Đầu ra: findings hoặc file đã đổi + lệnh và kết quả kiểm chứng.
```

