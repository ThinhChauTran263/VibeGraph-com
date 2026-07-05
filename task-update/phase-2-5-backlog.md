# Phase 2–5 — Backlog (làm sau Phase 1)

Các pha này mở rộng từ nền auth của Phase 1. Chỉ bắt đầu khi Phase 1 đã chạy + test ổn. Mỗi mục ánh xạ tới lỗ hổng V-number trong `security-multiuser-roadmap.md`.

---

## Phase 2 — Sandbox theo user + Quota (V3, V4, V5)

- ⬜ **2.1** Bố cục lưu trên server: `<storage-root>/<userId>/<projectId>/...`; mọi ghi/đọc khóa trong subtree của user.
- ⬜ **2.2** Chống path traversal: chặn `..`, path tuyệt đối, reject symlink (tái dùng guard của `ArchiveExtractor`).
- ⬜ **2.3** **Bỏ/thay** picker duyệt ổ đĩa server (`/browse` liệt kê `listRoots()` của host) — user chỉ thấy không gian của mình. Với deploy: dùng folder-picker trình duyệt (upload) thay vì browse ổ server.
- ⬜ **2.4** Quota: thuộc tính `User.quotaBytes` + `usedBytes`; kiểm trước khi ghi (`used + incoming <= quota`), vượt → 413.
- ⬜ **2.5** Cap số file/project; chỉ nhận `.java`.
- ⬜ **2.6** Giới hạn analyze đồng thời per-user + timeout per-file khi parse (fix V14 luôn — huỷ task nếu 1 file quá X giây).

## Phase 3 — Auth cho WebSocket + MCP theo user (V6, V10)

- ⬜ **3.1** WebSocket: xác thực JWT khi CONNECT/SUBSCRIBE; chặn subscribe topic `/topic/projects/{id}/updates` của project không thuộc user.
- ⬜ **3.2** FE: gắn token vào STOMP `connectHeaders`.
- ⬜ **3.3** MCP: mỗi user có API key/token MCP riêng (bảng/`:User` thuộc tính, lưu **hash**).
- ⬜ **3.4** Mỗi tool `mcp/tool/*Tool.java` nhận `projectId` → chạy **cùng ownership check** như REST (dùng lại `ProjectOwnershipGuard`).
- ⬜ **3.5** Cấu hình MCP client production: `url` + header `Authorization: Bearer <apiKey>` (ghi mẫu vào `review/mcp-server.html`).

## Phase 4 — CLI `vibegraph` (đưa code lên server không cần zip)

- ⬜ **4.1** Package Node `vibegraph-cli` (`package.json` có trường `bin: { vibegraph: ... }`).
- ⬜ **4.2** `vibegraph login` — mở browser hoặc `--api-key`, lưu vào `~/.vibegraph/config`.
- ⬜ **4.3** `vibegraph push` — đọc `.vibegraphignore`, upload delta file `.java` qua HTTPS (xác thực bằng API key).
- ⬜ **4.4** `vibegraph analyze` — trigger `POST /{id}/analyze`.
- ⬜ **4.5** Publish npm: `npm login` → `npm publish` → user cài `npm install -g vibegraph-cli`. (Kiểm tên trống bằng `npm view vibegraph-cli`.)
- ⬜ **4.6** Backend: endpoint nhận upload delta có xác thực + ownership + quota.

## Phase 5 — Hardening & Vận hành (V7, V8, V9, V11, V12, V13, V15, V16)

- ⬜ **5.1** (V9) TLS/HTTPS + security headers (CSP, HSTS, X-Frame-Options, X-Content-Type-Options).
- ⬜ **5.2** (V9) Rate-limit login + thử API key; API key lưu hash, có hạn/rotate/revoke.
- ⬜ **5.3** (V9) XSS: escape khi render tên node/đường dẫn/source ra UI. CSRF nếu dùng cookie session (không cần nếu thuần JWT header).
- ⬜ **5.4** (V7) Gói `deleteFile` + reparse trong 1 giao dịch/khóa per-project (tránh race).
- ⬜ **5.5** (V8) Cap payload INCREMENTAL trên WebSocket.
- ⬜ **5.6** (V11) LLM egress: cờ bật/tắt `usecase.llm` per-project + ghi rõ chính sách "gửi tên use-case tới Gemini".
- ⬜ **5.7** (V12) Quét & mask secret phổ biến (AWS key, JWT, connection string, PRIVATE KEY) trong source hiển thị lại (Code Viewer + MCP source tools).
- ⬜ **5.8** (V13) Test/lint bắt buộc mọi Cypher mới có `WHERE ... projectId = $projectId`; backup + kiểm thử restore Neo4j.
- ⬜ **5.9** (V15) Hàm `purgeProject()` duy nhất xoá cả Neo4j + file đĩa + cache; luồng "xoá tài khoản" gọi purge cho mọi project của user.
- ⬜ **5.10** (V16) Access log theo user/project + alert cơ bản (N×403 từ 1 IP, N request/T giây) + cron backup Neo4j.

---

## Ưu tiên gợi ý

1. **Phase 1** (bắt buộc, hội đồng yêu cầu).
2. **Phase 3.1–3.2** (WS auth) — vì bật security ở Phase 1 dễ làm gãy realtime, nên xử lý sớm.
3. **Phase 5.7** (quét secret) — rủi ro lộ dữ liệu user, giá trị cao, làm độc lập được.
4. Phase 2 / 4 / phần còn lại — theo nhu cầu thực tế khi thật sự multi-user.
