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

### 2.1 Các "góc khuất" bổ sung (thảo luận 2026-07, chưa nằm trong V1-V9 vì không phải access-control thuần)

| # | Lỗ hổng | Hệ quả |
|---|---|---|
| V10 | **MCP server dùng chung cửa với REST** (`/mcp`, Streamable HTTP) — không auth, không tách theo user | AI agent (Claude/Cursor/Kiro…) của bất kỳ ai trỏ vào `http://server:8080/mcp` là gọi được cả 15 tool trên **mọi project** — kể cả tool đọc source (`get_source_file`, `search_source`). Không thể giới hạn "agent của user A chỉ thấy project của A" nếu chưa gắn ownership check vào tầng MCP tool giống REST. |
| V11 | **Data egress qua Gemini API** (`LlmUseCaseRefiner`/`GeminiFailoverChatClient`) khi bật `vibegraph.usecase.llm.enabled=true` | Tên use-case suy luận (id/name/domain — **không phải source code**) được gửi ra ngoài tới Google GenAI để làm gọn nhãn. Rủi ro thấp (đã tự giới hạn fact-grounded input, không gửi code), nhưng vẫn là **dữ liệu rời khỏi hạ tầng** cần khai báo rõ trong chính sách riêng tư nếu deploy cho khách hàng thật (domain/tên nghiệp vụ có thể gợi ý ngành/khách hàng). API key Gemini cũng là secret cần bảo vệ (đọc từ `GEMINI_API_KEYS`/`.env`, không hardcode). |
| V12 | **Secret nằm ngay trong code user được import** | VibeGraph parse & hiển thị lại nội dung file `.java` (Code Viewer, MCP `get_source_file`). Nếu user vô tình để `application.yaml`/hardcoded API key/DB password trong repo họ import, VibeGraph sẽ **hiển thị nguyên văn** secret đó cho bất kỳ ai xem được project (kể cả redaction hiện tại chỉ nhắm token nội bộ của VibeGraph, không quét secret của user). Chưa có bước quét/redact secret dạng phổ biến (AWS key, JWT, connection string) trong source hiển thị lại. |
| V13 | **Một Neo4j dùng chung cho toàn hệ thống** (không phải theo Postgres ownership ở V2, mà ở tầng hạ tầng DB) | Một sự cố Cypher sai phạm vi (thiếu `WHERE projectId = ...`) hoặc một lỗi driver/migration ảnh hưởng **toàn bộ user cùng lúc** — không có cách ly instance-per-tenant hay backup/restore theo từng user riêng. Quy mô lớn hơn IDOR đơn lẻ: đây là rủi ro "một lỗi nhỏ, hỏng cho tất cả". |
| V14 | **Giai đoạn parse chưa có giới hạn CPU/thời gian per-request** ngoài guardrail số node/edge | Một project cấu trúc "quái" (file cực dài, class lồng sâu, generic phức tạp) có thể khiến JavaParser + SymbolSolver chạy rất lâu trên 1 luồng — với executor phân tích dùng chung (core 2 / max 4), vài request như vậy đủ **làm nghẽn hàng đợi phân tích của mọi user khác** (DoS bằng input, không cần cố ý ác ý). |
| V15 | **Xoá project chưa chắc "xoá sạch"** | `deleteProject` xoá node Neo4j (`DETACH DELETE`), nhưng: (a) file gốc trên đĩa (nếu đã lưu khi import archive/local) có bị xoá kèm không cần xác minh lại theo từng luồng import; (b) không có cơ chế xoá cứng sau X ngày hay xác nhận 2 lớp cho dữ liệu nhạy cảm; (c) chưa có "xoá tài khoản" kéo theo xoá toàn bộ project của user đó (quan trọng khi có luật riêng tư dữ liệu). |
| V16 | **Vận hành chưa có giám sát bảo mật** | Chưa có access log tập trung theo user/project, chưa có alert khi có pattern bất thường (một IP quét nhiều `projectId`, một tài khoản gọi API dồn dập), chưa có kế hoạch backup/khôi phục Neo4j định kỳ. Đây là lớp "phát hiện sớm" bổ sung cho các lớp chặn (AuthN/AuthZ) ở trên — cần thiết trước khi vận hành thật, không chỉ dừng ở ngăn chặn. |

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

### 3.2b Fix cho các góc khuất V10-V16

**MCP theo user (V10)**
- Mỗi user có **API key/token MCP riêng** (khác token trình duyệt là được — MCP client thường không login bằng cookie).
- Tool nào nhận `projectId` phải chạy **cùng dòng check ownership** như REST (mục 3.1.B) — không tạo "cửa sau" bỏ qua AuthZ chỉ vì đi qua MCP thay vì HTTP JSON thường.
- Nếu chưa kịp làm ownership cho MCP: tách hẳn 1 MCP server/process theo project hoặc theo user (tốn tài nguyên hơn nhưng đơn giản, an toàn tạm thời).

**Data egress qua LLM ngoài (V11)**
- Ghi rõ trong tài liệu/chính sách: khi bật `usecase.llm.enabled`, tên use-case suy luận (không phải code) được gửi tới Gemini API.
- Cho **admin/user tự tắt** tính năng này per-project nếu dữ liệu nhạy cảm (đã có flag `enabled`, chỉ cần expose ra UI/config theo project).
- Xoay & giới hạn quyền API key Gemini tối thiểu (đã có failover theo key nhưng vẫn nên rotate định kỳ).

**Secret trong code user (V12)**
- Thêm bước quét pattern secret phổ biến (AWS `AKIA...`, JWT, connection string có password, `-----BEGIN PRIVATE KEY-----`) trên nội dung trả về ở Code Viewer/`get_source_file`/`search_source`, mask trước khi trả — tái dùng tầng redaction hiện có, mở rộng danh sách pattern.
- Cảnh báo trong UI: "VibeGraph hiển thị lại nguyên văn source đã import — không import repo chứa secret thật chưa được `.gitignore`".

**Neo4j dùng chung toàn hệ thống (V13)**
- Bắt buộc test tự động (integration test) cho **mọi Cypher mới** phải có `WHERE ... projectId = $projectId` — thêm rule lint/checklist code review, không chỉ trông chờ convention.
- Có kế hoạch backup định kỳ + kiểm thử restore Neo4j (không chỉ backup, phải test restore được).
- Khi có traffic thật lớn: xem xét tách theo `database` riêng của Neo4j Enterprise (multi-database) hoặc namespace theo nhóm khách hàng, không chỉ theo `projectId` string trong 1 DB.

**Giới hạn CPU/thời gian parse (V14)**
- Thêm **timeout** cho từng file/parse task (không chỉ cap tổng node/edge) — huỷ task nếu 1 file mất quá X giây.
- Giới hạn analyze đồng thời per-user (đã nêu ở 3.1.D) áp dụng luôn ở đây — hai vấn đề dùng chung 1 cơ chế.

**Xoá sạch khi xoá project/account (V15)**
- Checklist xoá: Neo4j (`DETACH DELETE`) + file trên đĩa (nếu import archive/local) + cache liên quan (nếu có) — viết 1 hàm `purgeProject()` duy nhất gọi đủ 3 bước, tránh mỗi luồng import tự xoá nửa vời.
- Khi có tài khoản: thêm luồng "xoá tài khoản" gọi `purgeProject()` cho toàn bộ project của user đó.
- Cân nhắc "soft delete + xoá cứng sau X ngày" cho dữ liệu nhạy cảm thay vì xoá ngay tức khắc không hồi phục.

**Giám sát vận hành (V16)**
- Access log tối thiểu: ai (userId) gọi gì (`method + path + projectId`) lúc nào — đủ để điều tra sau sự cố, không cần APM đầy đủ ngay từ đầu.
- Alert cơ bản: N lần 403 liên tiếp từ 1 IP/token, N request phân tích trong T giây từ 1 user.
- Backup Neo4j định kỳ (cron `neo4j-admin database dump` hoặc tương đương) lưu ngoài host chạy chính.

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
| MCP theo user (V10) | ❌ (mở, không phân biệt user) | ✅ token riêng + ownership check trong tool |
| Khai báo data egress LLM (V11) | ⚠️ có tính năng, chưa có khai báo/tuỳ chọn tắt lộ ra UI | ✅ cờ bật/tắt per-project + ghi rõ chính sách |
| Quét secret trong source hiển thị lại (V12) | ❌ chỉ redact token nội bộ | ✅ mở rộng pattern secret phổ biến |
| Test/lint bắt buộc lọc `projectId` trong Cypher (V13) | ❌ dựa convention | ✅ test tự động + backup/restore |
| Timeout per-file khi parse (V14) | ❌ chỉ cap tổng node/edge | ✅ timeout + giới hạn song song per-user |
| `purgeProject()` xoá sạch 1 hàm (V15) | ❌ mỗi luồng xoá riêng lẻ | ✅ 1 hàm chuẩn hoá + xoá theo tài khoản |
| Access log + alert + backup định kỳ (V16) | ❌ | ✅ |

---

## 5. Thông điệp trình bày hội đồng

- **Không giấu, không bỏ Import Local** — dùng nó để **demo realtime** (giá trị khác biệt so với Archive/GitHub).
- Định vị: **Archive + GitHub = onboarding cho bản deploy**; **Local = chế độ dev/same-host + realtime**, mở rộng đa người dùng ở hướng phát triển.
- Khi bị hỏi "deploy có chạy cho user từ xa không?": trả lời trung thực — "'local' là local với máy chủ, không phải máy người dùng; bản deploy dùng Archive/GitHub; Local realtime cần thêm push có xác thực + sandbox (đã nằm trong hướng phát triển)."
- Nhấn mạnh: các lỗ đã được **nhận diện có chủ đích** và có **kế hoạch fix rõ ràng** (tài liệu này) — đúng tinh thần chương Đánh giá (nêu giới hạn kèm hướng cải tiến).
- Nếu bị hỏi sâu hơn "còn góc nào chưa nói": có thể chủ động nêu **V10-V16** (MCP dùng chung cửa, data egress qua Gemini khi bật LLM refiner, secret trong code user hiển thị lại, DB dùng chung toàn hệ thống, DoS bằng input khi parse, xoá chưa triệt để, chưa có giám sát vận hành) — cho thấy đã rà soát toàn diện, không chỉ dừng ở access-control cơ bản.
