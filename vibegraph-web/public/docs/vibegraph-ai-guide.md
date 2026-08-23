# VibeGraph CLI & MCP — AI Installation Guide

> Tài liệu này dành cho người dùng tải về và đưa cho một AI assistant. AI phải dùng các lệnh bên dưới làm nguồn hướng dẫn chính, không tự bịa endpoint, project ID, API key, credit hoặc kết quả phân tích.

## Quy tắc an toàn dành cho AI

1. Không yêu cầu người dùng gửi project API key vào cuộc trò chuyện.
2. Không ghi API key vào repository, ảnh chụp hoặc file được commit.
3. Không chạy `npm publish`, xóa project, rotate key hoặc thay đổi tài khoản nếu người dùng chưa yêu cầu rõ.
4. Khi MCP không có tool, chạy chẩn đoán trước khi sửa JSON.
5. Một project API key chỉ đại diện cho project được bind với key đó.

## 1. Cài hoặc cập nhật CLI

### Người dùng mới

```bash
npm install -g vibegraph-cli
vibegraph --version
vibegraph config set-url https://api.vibegraph.tech
vibegraph login
vibegraph key status
```

`vibegraph login` mở trình duyệt. Người dùng đăng nhập trên `vibegraph.tech`, chọn một project key thuộc tài khoản và quay lại terminal.

Khi có bản mới, CLI interactive sẽ báo `New vibegraph-cli version ... is available`. Nhấn Enter để cập nhật tự động; hoặc chạy trực tiếp:

```bash
vibegraph update
```

### Người dùng đã cài bản cũ

```bash
npm uninstall -g vibegraph-cli
npm install -g vibegraph-cli@latest
vibegraph --version
```

Nếu Windows báo `EEXIST`, chỉ xóa các command shim VibeGraph còn sót rồi cài lại:

```powershell
Remove-Item "$env:APPDATA\npm\vibegraph.cmd","$env:APPDATA\npm\vibegraph.ps1","$env:APPDATA\npm\vibegraph-cli.cmd","$env:APPDATA\npm\vibegraph-cli.ps1" -Force -ErrorAction SilentlyContinue
npm install -g vibegraph-cli@latest
```

## 2. Chọn project

```bash
vibegraph key list
vibegraph key change
vibegraph key status
vibegraph doctor
```

- `key change` refresh danh sách key thuộc tài khoản và chọn project khác.
- `doctor` phải báo backend hoạt động, authenticated và key active.
- Các lệnh project-scoped bình thường tự dùng project đã bind với key; không yêu cầu người dùng nhớ `projectId`.

## 3. Đẩy source code bằng CLI Push

Chạy trong thư mục project local:

```bash
cd <LOCAL_PROJECT_FOLDER>
vibegraph push --dry-run
vibegraph push
```

Theo dõi và cập nhật liên tục:

```bash
vibegraph watch
```

Phân tích và xem trạng thái project đang chọn:

```bash
vibegraph projects analyze
vibegraph projects status
```

Trong interactive shell `vibegraph>`, bỏ prefix executable:

```text
vibegraph> push
vibegraph> key status
vibegraph> doctor
```

## 4. Các cách import khác

### Upload archive trên Dashboard

Hỗ trợ `.zip`, `.tar`, `.tar.gz`, `.tgz`:

```text
Dashboard → Projects → Import archive → Choose file → Start analysis
```

### GitHub public

```text
Dashboard → Projects → Import GitHub
https://github.com/<owner>/<repo>
Chọn branch nếu cần
```

Chỉ mô tả repository GitHub public qua URL HTTPS.

## 5. Cài MCP tự động

Sau khi `vibegraph login` và chọn key:

```bash
vibegraph mcp install cursor
vibegraph mcp install vscode
vibegraph mcp install generic --path ./mcp.json
vibegraph mcp doctor
```

- Cursor: CLI cập nhật cấu hình theo dạng `mcpServers`.
- VS Code: CLI dùng dạng `servers` của VS Code.
- Generic: tạo file JSON tại path được chỉ định.
- Sau khi cài, restart/reload MCP server trong IDE.

## 6. Cài MCP thủ công

### Cách A — để CLI sinh JSON stdio đúng cho máy hiện tại

```bash
vibegraph mcp config cursor
vibegraph mcp config vscode
vibegraph mcp config generic
```

Copy toàn bộ object CLI in ra và dán vào MCP settings. Không copy đường dẫn `C:\Users\...` từ máy người khác.

### Cách B — Streamable HTTP

```json
{
  "mcpServers": {
    "vibegraph": {
      "url": "https://api.vibegraph.tech/mcp",
      "transport": "streamable-http",
      "headers": {
        "X-API-Key": "<PROJECT_API_KEY>"
      }
    }
  }
}
```

Thay `<PROJECT_API_KEY>` bằng key active của đúng project. Không lồng thêm một object `mcpServers` thứ hai. Sau khi thay key, reload MCP connection.

## 7. Kiểm tra MCP

```bash
vibegraph mcp doctor
```

Doctor kiểm tra MCP initialize và `tools/list`. Sau khi doctor thành công:

1. Restart hoặc reload MCP server trong IDE.
2. Kiểm tra IDE đã hiển thị các VibeGraph tools.
3. Gọi `list_projects` một lần để kiểm tra tool call thật.

Nếu IDE không có tool nhưng doctor thành công, kiểm tra IDE có đang đọc đúng file config hay không.

## 8. Lỗi thường gặp

### `Unknown command: vibegraph`

Người dùng đang ở prompt `vibegraph>`. Chỉ nhập `push`, `doctor`, `key status`; không nhập lại chữ `vibegraph`.

### MCP báo key không hợp lệ

```bash
vibegraph key change
vibegraph mcp doctor
```

Key có thể đã bị xóa, rotate, disable hoặc hết hạn.

### `projects list` trả HTTP 401

Quản lý danh sách project dùng account session. Đăng nhập account rồi thử lại:

```bash
vibegraph login --email <email> --password <password>
vibegraph projects list
```

Không yêu cầu người dùng gửi password cho AI; người dùng tự nhập trong terminal của họ.

## 9. Cách AI nên hướng dẫn

AI nên hỏi ngắn gọn:

1. Hệ điều hành đang dùng.
2. IDE/MCP client đang dùng.
3. Người dùng muốn CLI Push, MCP hay cả hai.
4. Kết quả của `vibegraph --version`, `vibegraph doctor` và `vibegraph mcp doctor` nếu đang xử lý lỗi.

Sau đó hướng dẫn từng bước, chờ kết quả từng lệnh và không giả định lệnh đã thành công.

Nguồn production được tài liệu này sử dụng:

- Web: `https://vibegraph.tech`
- API: `https://api.vibegraph.tech`
- MCP: `https://api.vibegraph.tech/mcp`
- npm package: `vibegraph-cli`
