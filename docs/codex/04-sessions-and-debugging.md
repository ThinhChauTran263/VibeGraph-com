# Sessions, Context và Debugging

Session là nơi giữ transcript và context hội thoại. Workspace là nơi giữ file. Hai thứ này độc lập: chat mới không biết chat cũ, nhưng vẫn nhìn thấy file mà chat cũ đã sửa.

## Lệnh session

| Lệnh | Kết quả |
|---|---|
| `/new` | Chat mới trong cùng CLI, không giữ transcript cũ trong context mới |
| `/clear` | Chat mới và dọn phần hiển thị terminal |
| `/resume` | Mở lại transcript của session đã lưu |
| `/fork` | Tạo chat ID mới, copy transcript hiện tại |
| `/compact` | Tóm tắt context hiện tại để tiết kiệm token |
| `/side` hoặc `/btw` | Hỏi tạm một việc mà không làm bẩn transcript chính |
| `/rename` | Đặt tên dễ tìm cho session |
| `/archive` | Ẩn session khỏi danh sách active, không xóa transcript |
| `/delete` | Xóa session; chỉ dùng khi thực sự muốn mất transcript |

Ngoài CLI:

```powershell
codex resume
codex resume --last
codex resume --all
codex fork
codex fork --last
```

## Session A có thấy B/C không?

| Dữ liệu | Session A mới |
|---|---|
| Tin nhắn/lập luận trong B/C | Không tự thấy |
| File B/C đã sửa trong cùng repo | Có |
| Git diff và untracked files | Có |
| Service/process B/C đang chạy | Có thể thấy qua OS, nhưng không tự biết mục đích |
| Summary subagent của session khác | Không tự thấy |

Muốn chuyển task sạch giữa session, tạo handoff:

```text
docs/<task>-handoff.md
```

Handoff chỉ cần decision, file/symbol, verification, blocker và next step. Không copy toàn bộ transcript.

## Khi nào compact, new, resume hay fork?

- Chat vẫn cùng outcome nhưng dài: `/compact`.
- Outcome mới, không cần lịch sử: `/new`.
- Tiếp tục đúng task cũ: `/resume`.
- Thử phương án khác nhưng muốn giữ bối cảnh: `/fork`.
- Config/role vừa thay đổi: thoát Codex và chạy lại là cách chắc chắn nhất để reload.

## Debugging ladder

### 1. Xem session thật

```text
/status
```

Xác nhận model, effort, cwd, sandbox, approval và context capacity.

### 2. Xem lớp config

```text
/debug-config
```

Precedence cao xuống thấp:

```text
CLI flags/-c
-> project .codex/config.toml gần cwd nhất
-> profile
-> user ~/.codex/config.toml
-> system config
-> built-in defaults
```

### 3. Chẩn đoán installation/runtime

```powershell
codex --strict-config doctor --summary
codex features list
codex mcp list
```

Một số cảnh báo thường gặp:

- `rollouts/threads`: session history cũ, file rollout lỗi hoặc scan chưa đủ; không đồng nghĩa config fail.
- `app-server not running`: bình thường trong ephemeral mode.
- `terminal height`: cảnh báo hiển thị, không phải lỗi reasoning.
- `0 fail`: các check bắt buộc đã qua; vẫn đọc warning theo ngữ cảnh.

### 4. Thu nhỏ failure

Không retry cùng một lệnh nhiều lần. Ghi:

```text
Goal:
Last successful step:
Exact error:
Last failed command/tool:
Environment assumptions:
Smallest discriminating check:
```

Ví dụ `ECONNREFUSED`: kiểm tra process, port và health endpoint trước khi sửa code.

## Giữ context sạch

- Một chat cho một outcome.
- Log dài nên lưu file và trích 20-50 dòng liên quan.
- Sau một decision lớn, yêu cầu Codex ghi lại decision/assumption.
- Trước `/compact`, yêu cầu một status recap nếu task có nhiều nhánh.
- Dùng `/ps` để xem background terminals; dùng `/stop` khi chắc chắn muốn dừng chúng.

