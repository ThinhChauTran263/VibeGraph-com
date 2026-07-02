# Bảo mật & Multi-user — Hiện trạng và Hướng phát triển

**Trạng thái:** Ghi nhận sau thảo luận review (2026-07). Đây là tài liệu định hướng, KHÔNG phải mô tả tính năng đã hoàn thành.
**Phạm vi:** Áp dụng khi chuyển VibeGraph từ chế độ **local/self-host một người dùng** sang **deploy đa người dùng**.

> **Verified 2026-07-02:** Phần "1. Hiện trạng (as-built)" đã được đối chiếu với code — `glob **/{Security*,*Security*}.java` trong `src/main/java` trả 0 file, xác nhận **chưa có SecurityConfig/SecurityFilterChain**. Các mục V1-V9 vẫn open. Roadmap chưa apply.

> Tóm tắt 1 dòng: VibeGraph hiện chạy đúng cho **local/dev một người dùng**. Để deploy đa người dùng an toàn cần thêm **xác thực + phân quyền theo chủ sở hữu + sandbox theo user + quota**, cùng các bước hardening tiêu chuẩn (TLS, bảo vệ khoá, auth cho WebSocket, rate-limit, CORS/CSRF/XSS, giới hạn CPU).

---

## 1. Hiện trạng (as-built)

- **Chưa có auth/authorization**: mọi REST controller và WebSocket đều mở; không phân biệt người dùng.
- **Phân tách dữ liệu chỉ bằng `projectId`** trong Neo4j; `projectId` là chuỗi 8 ký tự cắt từ UUID (có thể dò/đoán/lộ qua URL/log).
- **Import Local = local với MÁY CHỦ**, không phải máy người dùng:
  - `GET /api/projects/browse` chạy `listRoots()` trên host backend → liệt kê **ổ đĩa của server** (chạy local thì trùng máy dev nên trông như "ổ của user").
  - `validateRootPath` resolve path **trên host backend**; `vibegraph.projects.allowed-root` mặc định **rỗng → không giới hạn** (đọc được mọi thư mục tồn tại trên host).
- **Realtime** chỉ chạy khi mã nguồn **sống và bị sửa ngay trên host backend** (watcher `WatchService`). Deploy mà upload từ trình duyệt → snapshot tĩnh, không realtime.
- **Đọc source** (`/source`, `CodeViewerModal`, MCP source tools) có redaction + giới hạn cửa sổ dòng, nhưng phạm vi phục vụ vẫn theo path server, chưa cô lập theo user.
- **Điểm cộng an toàn sẵn có:** VibeGraph chỉ **parse (đọc AST)**, KHÔNG biên dịch/chạy code của user → nội dung code **không phải vector RCE**. Rủi ro chính là access-control / path-traversal / DoS / quyền riêng tư dữ liệu.
- **Guard đã có ở luồng Archive/GitHub:** kiểm đuôi file, cap dung lượng (100MB), chống archive-bomb (`copyCapped`), chống path traversal (`resolveSafely`), reject symlink, workspace giới hạn.

---

## 2. Các lỗ hổng đã xác định (khi deploy nguyên trạng)

| # | Lỗ hổng | Hệ quả |
|---|---|---|
| V1 | Không xác thực trên mọi endpoint + WebSocket | Bất kỳ ai gọi API cũng import/browse/analyze/delete/đọc graph. |
| V2 | Không kiểm quyền sở hữu (IDOR) | User A biết/đoán `projectId` của B → đọc graph/source/impact của B. |
| V3 | `/browse` liệt kê ổ đĩa server; `allowed-root` rỗng | Lộ cấu trúc/nội dung filesystem máy chủ. |
| V4 | Không cô lập theo user trên đĩa | Source của các user nằm chung, không có "phòng riêng". |
| V5 | Không quota / không giới hạn CPU per-user | 1 user đẩy repo khổng lồ / spam analyze → đầy đĩa hoặc nghẽn (DoS); executor phân tích dùng chung (core 2 / max 4 / queue 50). |
| V6 | WebSocket topic `/topic/projects/{id}/updates` không auth | A subscribe được stream cập nhật của B. |
| V7 | `deleteFile` chạy 2 câu Cypher auto-commit riêng; analyze không lock | Race khi sửa/xoá đồng thời → diff/dữ liệu sai. |
| V8 | INCREMENTAL không cap payload | 1 file đổi tạo diff lớn → không giới hạn trên socket. |
| V9 | Chưa siết TLS/headers/CORS/CSRF/XSS, chưa rate-limit, chưa bảo vệ khoá | Sniff token, brute-force, XSS khi render tên node/source. |

---

## 3. Hướng phát triển (future work để fix)

### 3.1 Lớp bắt buộc cho multi-user

**A. Xác thực (AuthN)** — fix V1
- Login bằng OAuth **hoặc** email/mật khẩu + JWT/session (OAuth chỉ là một lựa chọn, không bắt buộc).
- Lưu người dùng ở **Postgres** (thêm datastore quan hệ cho user/ownership/API key).

**B. Phân quyền theo chủ sở hữu (AuthZ / chống IDOR)** — fix V2
- Bảng `project(ownerId)` trong Postgres.
- **Mọi API có `projectId`** (graph, neighbors, impact, source, analyze, delete, diagrams) kiểm `project.ownerId == currentUser` trước khi truy vấn Neo4j/đọc file; sai → **403**.
- Lưu ý: Postgres chỉ *lưu* quyền sở hữu; việc chặn thực sự là **dòng check ở đầu mỗi endpoint** (không phải DB tự làm).

**C. Sandbox theo user** — fix V3, V4
- Bố cục lưu trên server: `<storage-root>/<userId>/<projectId>/...`.
- Mọi thao tác khóa trong subtree của user; chặn `..`, path tuyệt đối, **reject symlink** (tái dùng guard của `ArchiveExtractor`).
- **Bỏ/thay** picker duyệt ổ đĩa server; user chỉ thấy không gian của mình.

**D. Quota & giới hạn tài nguyên** — fix V5
- Postgres lưu `user.quotaBytes` + `user.usedBytes`; kiểm trước khi ghi (`used + incoming <= quota`), vượt → **413**.
- Cap số file/project; chỉ nhận `.java`.
- **Giới hạn analyze đồng thời per-user** + timeout (quota đĩa không chặn CPU); executor hiện dùng chung.

### 3.2 Cách đưa code lên server (thay "local path" khi deploy)

- **Folder picker của trình duyệt** (`<input webkitdirectory>` / File System Access API): user chọn thư mục thật của họ, upload từng file qua HTTPS — "push không zip", dùng token phiên (không cần API key).
- **CLI push** (xác thực bằng **API key**): `vibegraph push` đọc folder, upload delta (chỉ file thay đổi) — hợp workflow "mỗi lần có code mới gõ 1 lệnh".
- Sau khi push: **watcher** bắt (sửa nhỏ) hoặc gọi **`POST /{id}/analyze`** (đẩy batch → index lại sạch). **Không** chạy song song watcher + analyze cùng project (tránh race V7).
- Kênh: **HTTPS + token/git-over-HTTPS**, KHÔNG cấp SSH shell cho web user.

### 3.3 Hardening tiêu chuẩn — fix V6, V7, V8, V9
- **TLS/HTTPS bắt buộc** + security headers (CSP, HSTS, X-Frame-Options, X-Content-Type-Options).
- **API key**: đặt ở header (không URL/log), **lưu hash**, có hạn/rotate/revoke; **rate-limit** login + thử key.
- **Auth cho WebSocket**: xác thực khi subscribe + kiểm quyền theo project (chặn subscribe topic của user khác).
- **CORS** siết theo origin production; **CSRF** nếu dùng cookie session; **XSS** khi render tên node/đường dẫn/source ra UI.
- **Tính đúng đắn đồng thời**: gói `deleteFile` + reparse trong một giao dịch/khoá per-project; cân nhắc cap payload INCREMENTAL.
- **Dữ liệu at-rest**: xoá project purge cả Neo4j + file; backup; credential DB để trong secret (không hardcode); log không chứa secret.

---

## 4. Phân định phạm vi

| Hạng mục | MVP hiện tại | Future work (multi-user deploy) |
|---|---|---|
| Import GitHub / Archive | ✅ luồng onboarding chính | giữ nguyên, thêm quota/ownership |
| Import Local + realtime | ✅ dev/same-host (demo realtime) | thay bằng push có xác thực + sandbox |
| Auth (login) | ❌ | ✅ OAuth hoặc JWT |
| Ownership check (chống IDOR) | ❌ | ✅ bắt buộc |
| Sandbox theo user + quota | ❌ | ✅ |
| Postgres (user/ownership/key) | ❌ (chỉ Neo4j) | ✅ thêm |
| Hardening (TLS/WS-auth/rate-limit/CORS/CSRF/XSS) | ❌ | ✅ |

---

## 5. Thông điệp trình bày hội đồng

- **Không giấu, không bỏ Import Local** — dùng nó để **demo realtime** (giá trị khác biệt so với Archive/GitHub).
- Định vị: **Archive + GitHub = onboarding cho bản deploy**; **Local = chế độ dev/same-host + realtime**, mở rộng đa người dùng ở hướng phát triển.
- Khi bị hỏi "deploy có chạy cho user từ xa không?": trả lời trung thực — "'local' là local với máy chủ, không phải máy người dùng; bản deploy dùng Archive/GitHub; Local realtime cần thêm push có xác thực + sandbox (đã nằm trong hướng phát triển)."
- Nhấn mạnh: các lỗ đã được **nhận diện có chủ đích** và có **kế hoạch fix rõ ràng** (tài liệu này) — đúng tinh thần chương Đánh giá (nêu giới hạn kèm hướng cải tiến).
