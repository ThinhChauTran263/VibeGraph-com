# BÁO CÁO RÀ SOÁT TOÀN DIỆN VIBEGRAPH (VÒNG 2) — 12/08/2026

> **Phiên bản: v2.4 — đối chiếu lại bản Qwen 76 finding, Claude và bằng chứng runtime**
>
> **Ngày:** 12/08/2026 — **Người rà soát:** hệ thống agent tự động (backend / frontend / database / security / DevOps) — **Trạng thái:** đã cập nhật sau phản biện vòng 3 và đối chiếu Qwen/Claude mới (xem §9–§10)

---

## 1. Tóm tắt điều hành

- **Phạm vi:** Backend Java/Spring Boot (~530 file, `src/main`), Frontend Vue 3 + TypeScript (`vibegraph-web`, 67 SFC + 64 spec), tầng dữ liệu (Neo4j 5, PostgreSQL 16/Flyway, Supabase), hạ tầng (Dockerfile, `docker-compose.yml`, CI GitHub Actions), và đối chiếu với báo cáo audit vòng 1 tại `update/AUDIT-REPORT.md`.
- **Phương pháp:** đọc trực tiếp source code + cấu hình, đối chiếu từng finding của audit vòng 1 trên code hiện hành, kiểm chứng bằng grep/test có sẵn. Báo cáo áp dụng bắt buộc bước xác minh reachability (truy caller đến HTTP/UI/MCP) theo bài học vòng 3 (§9), rồi đối chiếu với các báo cáo độc lập trong `update/docs/` (§10).
- **Kết quả tổng quan (phân loại v2.4):** **2 vấn đề Critical, 11 vấn đề High, 31 vấn đề Medium, 10 nhóm Low** (**54 finding** sau khử trùng lặp).
  - **Critical (2):** C1 — registry project in-memory, restart méo trạng thái hiển thị; C2 — secret thật còn trong `.env`, file backup và Git object/stash.
  - **High (11):** H1 — upsert Neo4j không nguyên tử; H2 — analyze đồng bộ; H3 — parse tuần tự; H4 — backup/restore; H5 — container hardening; H6 — mount `.env`; H7 — cookie Secure; H8 — npm dependency vulnerabilities; H9 — rate-limit sau BCrypt; H10 — `readRange` nạp toàn file; H11 — private-key redaction chỉ che header.
- **Medium mới trong v2.4:** M23 — scheduler mặc định một thread; M24 — purge trash không phân trang/transaction dài; M25 — `IllegalStateException` map toàn cục thành 409; M26 — 3 test source rỗng; M27 — watcher đọc full graph hai lần; M28 — actuator metrics cho mọi user đăng nhập; M29 — multipart ceiling 2 GiB; M30 — lỗi UX tại `UsersTableView`; M31 — `dev-up.ps1` không khởi động Postgres. M30 là mức Medium theo bằng chứng runtime UX, không phải lỗi phân quyền hay mất dữ liệu.
  - **Low mới trong v2.4:** L8 — dead DTO + dead JPA mapping + test scaffold; L9 — 1.319 dòng dead frontend + client/config/timer hygiene; L10 — reproducibility và thư mục task cần quyết định ownership.
- Không phát hiện Critical/High mới trong logic Vue; H8 vẫn là High supply-chain của dependency frontend. Nền tảng bảo mật auth/JWT và kiến trúc multi-user nhìn chung vững.
- Từ bản v2.4: bằng chứng runtime của Qwen xác nhận M11/H7/M30/H10/M1, không tái hiện CORS wildcard và chưa tái hiện chi phí BCrypt với prefix ngẫu nhiên; severity vẫn dựa trên tác động và reachability, không dựa vào số lượng báo cáo đồng thuận.

---

## 2. Điểm mạnh cần giữ nguyên (không sửa nhầm)

### Backend
- JWT HS512 fail-fast (ép secret ≥64 bytes), kiểm tra chặt `alg` header chống alg-confusion.
- Refresh rotation có hash SHA-256, family revocation, replay detection với grace window cho 2 tab.
- Chống user enumeration bằng dummy BCrypt hash.
- Ownership guard chống IDOR ở mọi endpoint project-scoped (kể cả STOMP SUBSCRIBE).
- ArchiveExtractor chặn zip-slip/symlink, giới hạn kích thước file nén và byte giải nén của entry `.java`; entry không phải `.java` còn là điểm hardening CPU phòng thủ chiều sâu, không phải Critical (§10).
- CORS allow-list, không wildcard.
- Thread pool bounded + AbortPolicy.
- Module hóa rõ (mỗi module có `MODULE-GUIDE.md` + `package-info.java`), ArchUnit enforce ranh giới.
- 174 file Java trong `src/test`, trong đó 155 file chứa `@Test`/`@ArchTest`; JaCoCo gate 70% và có Testcontainers. Ba file rỗng/disabled scaffold được tách thành M26/L8 thay vì dùng tổng file để suy ra coverage.
- `GlobalExceptionHandler` map nhiều domain exception sang `ApiResponse` chuẩn; riêng nhánh `IllegalStateException` quá rộng là M25.

### Frontend
- JWT trong HttpOnly cookie (không dùng localStorage cho token).
- TypeScript strict + `noUncheckedIndexedAccess`, 0 `any` trong toàn `src`.
- CSP chặt trong `nginx.conf.template` (`script-src 'self'`).
- Kiến trúc phân lớp rõ (`lib/api.ts` typed DTO, http interceptor 401 + refresh).
- Graph render tối ưu tốt: WebGL Sigma, FA2/Noverlap trong Web Worker, position cache, debounce, viewport culling, lazy import sockjs/highlight.js.

### Hạ tầng
- Neo4j migration idempotent + V2 backfill `:Symbol` giải quyết index.
- Supabase telemetry batch insert idempotent + drain khi shutdown.
- CI có JaCoCo gate, integration test, `npm audit --audit-level=high`.
- Supabase runtime role least-privilege đã verify.

---

## 3. Đối chiếu với thư mục `update/` (audit vòng 1)

Bảng trạng thái (đã kiểm chứng trên code ngày 12/08/2026):

| Mã | Đề xuất vòng 1 | Trạng thái | Bằng chứng |
|----|----------------|------------|------------|
| D1 | Escape Lucene cho search | ❌ CHƯA áp dụng (liên quan code chết L7) | grep `escapeLucene`/`QueryParser.escape` = 0 match; query nguyên văn tại `Neo4jGraphRepository.java:345`. Lưu ý vòng 3: chuỗi `searchNodes` hiện không có caller nào (§9/P1) |
| D2 | `executeWrite` + `deleteProject` khi FAILED | ❌ Áp dụng MỘT PHẦN / chưa đầy đủ | `upsertNodes`/`upsertEdges` vẫn `session.run` autocommit dòng 166–231; đường sync archive/tarball ĐÃ gọi cleanup → `deleteProject` (`ArchiveImportServiceImpl.java:120,263`; `TarballImportServiceImpl.java:151,243`), nhưng đường async FAILED vẫn giữ graph dở (H1) |
| D4 | Tách query nodes/edges + bật cap | ⚠️ Áp dụng MỘT PHẦN | Query vẫn 1 câu OPTIONAL MATCH dòng 265–268, nhưng đã có guardrail `vibegraph.graph.node-limit` — mặc định yaml vẫn 0 |
| C4 | Chunk batch 5k–10k | ❌ CHƯA áp dụng | 1 UNWIND toàn bộ per label |
| C2 | Trả bytes từ extractor, bỏ walk thừa | ❌ CHƯA áp dụng | `measureExtractedSize` vẫn tồn tại ở `ArchiveImportServiceImpl.java:298`, `TarballImportServiceImpl.java:277` |
| B1/B5 | Dùng `FA2_ITERATIONS` / outlier clamp | ❌ CHƯA áp dụng | 2 hằng số chỉ có trong `runtimeConfig.ts`, không nơi nào dùng |
| D3 | Prod yêu cầu `allowedRoot` | ✅ ĐÃ cứng cố đúng hướng | `application.yaml` hardcode `allow-unconfined-*=false`, chỉ profile dev bật qua env |

**Kết luận:** báo cáo `update/` vẫn còn nguyên giá trị, phần lớn đề xuất backend/data chưa được hiện thực.

---

## 4. Danh sách phát hiện chi tiết

### 🔴 CRITICAL

#### C1 — Registry project in-memory: sau restart, project ANALYZING/FAILED hiển thị "đã phân tích xong 100%" vĩnh viễn *(H3 cũ, nâng từ High lên Critical theo xác minh vòng 3)*
- **Vấn đề:** Trạng thái project không bền vững; sau restart hiển thị sai trạng thái trực tiếp cho người dùng.
- **Vị trí:** `ProjectServiceImpl.java:34` (`ConcurrentHashMap`), `projectFromMetadata` dòng 230–231.
- **Bằng chứng (cơ chế kiểm chứng khớp 100%):** registry là `ConcurrentHashMap` in-memory (`ProjectServiceImpl.java:34`); `projectFromMetadata` hard-code status `ANALYZED`/progress `100` (dòng 230–231); grep `@Scheduled` ra 7 job, không job nào sweep trạng thái project `ANALYZING` treo.
- **Tác động:** đây là finding duy nhất gây sai lệch trực tiếp thứ người dùng nhìn thấy: project đang `ANALYZING` hoặc `FAILED`, sau restart, hiển thị "đã phân tích xong 100%" vĩnh viễn; người dùng mở graph rỗng/dở mà hệ thống báo thành công.
- **Ghi chú trung thực:** mức Critical là quyết định phân loại của vòng 3 dựa trên tác động người dùng trực tiếp; đây là sai trạng thái hiển thị + mất dấu lỗi, không phải mất dữ liệu (graph vẫn re-analyze được).
- **Giải pháp:** persist trạng thái sang Postgres (dùng `ProjectRuntimeStatusRepository` đã tồn tại làm nguồn chính, hoặc thêm cột status/progress/last_error); thêm scheduled sweep "ANALYZING quá N phút ⇒ FAILED".

#### C2 — Secret thật còn trong `.env`, file backup và Git object/stash *(action vận hành cũ, đưa lại vào bảng Critical)*
- **Vấn đề:** Repository state hiện hành vẫn chứa nhiều credential không rỗng trong `.env`; một bản backup môi trường còn ở root, và parent untracked của `stash@{0}` chứa thêm một bản backup trong Git object database. Đây không chỉ là cấu hình máy cá nhân: stash/object có thể đi theo mirror/bundle/backup repository và file backup dễ bị sao chép ngoài ý muốn.
- **Vị trí:** `.env:16,27,33,41,54,56,76,86`; `.env.codex-backup-before-9e1dfed-20260725-140618:16,27,30,40,42,60,70`; `stash@{0}` parent untracked `388632b...` chứa `.env.codex-backup-before-905919f-20260725-140030`; `update/docs/Qwen/AUDIT-REPORT.md:27–29` còn sao chép một số giá trị credential vào artifact audit. Báo cáo Codex chỉ nêu tên/vị trí, không sao chép giá trị.
- **Bằng chứng an toàn:** kiểm tra tên biến có assignment không rỗng xác nhận `JWT_SECRET`, OAuth client secrets, Gemini key(s), Neo4j/Postgres/Supabase passwords; `git rev-list --parents -n 1 'stash@{0}'` cho thấy stash có parent thứ ba, và `git ls-tree` trên parent đó thấy file backup. `.gitignore:117–120` đã ngăn track mới nhưng không xóa object/file đã tồn tại.
- **Tác động:** nếu các giá trị đã được dùng ở production, attacker có thể giả token, dùng OAuth/API key hoặc truy cập hạ tầng; không thể coi "chưa commit trên branch" là an toàn sau khi secret đã xuất hiện trong backup/stash/chat.
- **Giải pháp:** rotate/revoke toàn bộ credential trước; scrub/xóa an toàn bản audit Qwen đang chứa giá trị sau khi giữ một bản đã redact nếu cần thảo luận; sau khi xác nhận credential cũ vô hiệu và có backup an toàn, kiểm tra từng `stash@{0..3}`, drop đúng stash chứa secret, expire reflog + garbage collect theo quy trình; xóa/di chuyển file backup root sang vault. Không chạy lệnh Git destructive hàng loạt khi chưa review từng stash.
- **Snippet quy trình đề xuất:**
  ```powershell
  # Chỉ chạy sau khi rotate/revoke và xác nhận đúng stash mục tiêu.
  git stash drop 'stash@{0}'
  git reflog expire --expire=now --all
  git gc --prune=now
  ```

---

### 🟠 HIGH

#### H1 — Upsert Neo4j không nguyên tử, luồng async FAILED để lại graph dở dang *(C3 cũ, hạ từ Critical xuống High; viết lại giải pháp)*
- **Vấn đề gốc (vẫn đúng):** ghi graph bằng autocommit theo nhóm label, luồng async FAILED giữ lại graph dở.
- **Vị trí:** `Neo4jGraphRepository.java` — `upsertNodes` (dòng 166–184) / `upsertEdges` (dòng 213–231); các import service.
- **Bằng chứng:** `upsertNodes`/`upsertEdges` dùng `session.run` autocommit theo nhóm label (`Neo4jGraphRepository.java:166–184, 213–231`); đường async FAILED giữ graph dở (`ArchiveImportServiceImpl.java:243`, `TarballImportServiceImpl.java:220`, `LocalImportServiceImpl.java:169–172` — có comment "keep the FAILED project").
- **Sai sót của bản v2 đã sửa:**
  - (a) Câu "3 import service không gọi `deleteProject`" là **SAI MỘT PHẦN** — đường sync archive/tarball ĐÃ gọi cleanup → `deleteProject` (`ArchiveImportServiceImpl.java:120,263`; `TarballImportServiceImpl.java:151,243`); chỉ đường async FAILED là giữ graph dở.
  - (b) Đề xuất delete-on-failure **chỉ áp dụng cho project MỚI TẠO trong phiên import hiện tại** (3 luồng import luôn tạo project mới — an toàn); KHÔNG được áp máy móc cho endpoint re-analyze vì re-analyze là MERGE in-place trên graph cũ đang tốt (`AnalyzeServiceImpl.java:104–112` không xóa trước) — gọi `deleteProject` khi re-analyze FAILED sẽ xóa mất dữ liệu tốt.
  - (c) **Trade-off cần nêu rõ:** chunk 5–10k item/UNWIND giảm heap pressure nhưng tăng số transaction (hiện đã là N tx, chunk thành N×M), không làm pipeline nguyên tử; nguyên tử toàn project 200k node không thực tế trên Neo4j.
- **Giải pháp:** hướng đúng là chấp nhận non-atomic + dọn dẹp tất định khi FAILED (delete-on-failure cho project mới tạo trong phiên import) + sweep định kỳ; chunk 5–10k item/UNWIND trong `session.executeWrite` để giảm heap pressure; KHÔNG dùng `deleteProject` cho re-analyze FAILED.

#### H2 — POST /api/projects/{id}/analyze chạy parse đồng bộ trên request thread *(H2 cũ, sửa bằng chứng về guard)*
- **Vấn đề:** Endpoint re-analyze thủ công vẫn blocking, trong khi các luồng import đã async.
- **Vị trí:** `ProjectController.java` dòng 106–120.
- **Bằng chứng:** gọi `analyzeService.analyzeProject` trực tiếp; trong khi 3 luồng import đã đẩy analyze sang `analysisExecutor` (202 + progress WS) → với repo lớn request treo hàng phút, chiếm thread servlet, dễ timeout ở proxy (parse chiếm ~65% thời gian). Xác minh vòng 3: endpoint analyze (`ProjectController.java:106–120`) KHÔNG hề có `ConcurrentImportGuard` — guard chỉ dùng trong 3 import service; và guard là bộ đếm per-user (`ConcurrentImportGuard.java:15–34`), không chặn 2 tiến trình cùng re-analyze một project.
- **Giải pháp:** chuyển sang cùng pattern async (`analysisExecutor.execute` + trả 202 + `broadcastStatus`); bổ sung cơ chế chặn re-analyze song song per-project (guard hiện tại là per-user, không đủ).

#### H3 — Parse CPG tuần tự là bottleneck lớn nhất của import *(H4 cũ; finding C1 vòng 1)*
- **Vấn đề:** Parse đơn luồng, không tận dụng đa nhân.
- **Vị trí:** `ParserServiceImpl.java` (505 dòng).
- **Bằng chứng:** duyệt `for` tuần tự với 1 JavaParser + CombinedTypeSolver dùng chung.
- **Giải pháp:** parse song song trên bounded pool, mỗi thread một JavaParser (type-solver read-only dùng chung); điều kiện tiên quyết: xác minh thread-safety của `ProjectSymbolRegistry` (đang dùng dạng Scope dòng 108, nhiều khả năng ThreadLocal) bằng test trước.

#### H4 — Không có chiến lược backup/restore cho cả 3 kho dữ liệu *(H5 cũ)*
- **Vấn đề:** Mất volume = mất users/ownership/API-key vĩnh viễn (graph phân tích lại được nhưng control plane thì không).
- **Vị trí:** named volumes `postgres-data`, `neo4j-data`, `upload-workspaces`; `DEVOPS-GUIDE.md`, `deployment-plan.md`.
- **Bằng chứng:** không có mục `pg_dump` / `neo4j-admin dump` / restore trong tài liệu vận hành.
- **Giải pháp:** bổ sung chương backup vào DEVOPS-GUIDE (lịch `pg_dump`, `neo4j-admin database dump`, snapshot volume uploads); diễn tập restore 1 lần trước production.

#### H5 — Container backend chạy root, build bỏ qua test, không cấu hình heap *(H6 cũ)*
- **Vấn đề:** Image backend thiếu hardening cơ bản.
- **Vị trí:** `Dockerfile`.
- **Bằng chứng:** không có USER non-root, build `mvn -DskipTests`, ENTRYPOINT `java -jar` không cờ JVM; pipeline phân tích cho phép tới 200k node / 600k edge trong RAM nhưng container backend không có `mem_limit` như Neo4j.
- **Giải pháp:** `addgroup`/`adduser` + `USER app`; `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`; `mem_limit` cho backend; chạy test ở CI thay vì skip trong image.

#### H6 — Toàn bộ `.env` mount read-only vào container backend *(H7 cũ)*
- **Vấn đề:** Đưa toàn bộ secret vào filesystem container vượt nhu cầu; tạo 2 nguồn config song song dễ lệch.
- **Vị trí:** `docker-compose.yml:128` (`./.env:/app/.env:ro`).
- **Bằng chứng:** mount `.env` đầy đủ vào container, song song với khối `environment:` của compose.
- **Giải pháp:** bỏ mount, chỉ truyền biến cần thiết qua `environment:`; nếu giữ thì tách `.env.docker` tối thiểu.

#### H7 — Cookie session mặc định không Secure trong triển khai Docker *(H8 cũ)*
- **Vấn đề:** Hướng fail-open: triển khai Docker mặc định gửi cookie plaintext trừ khi đặt TLS + đổi env.
- **Vị trí:** `application.yaml` (secure-cookies mặc định true) vs `docker-compose.yml:102` (`AUTH_COOKIE_SECURE: ${AUTH_COOKIE_SECURE:-false}`), `.env.example:75` cũng false.
- **Bằng chứng:** compose override biến mặc định an toàn thành false.
- **Giải pháp:** đổi default compose sang true; chỉ false có chủ đích cho LAN dev; tài liệu hóa yêu cầu TLS trước khi expose.

#### H8 — npm audit: 8 lỗ hổng dependency (1 critical, 6 high, 1 moderate)
- **Vấn đề:** dependency tree hiện tại chứa `websocket-driver@0.7.4` (critical resource-limit bypass), cùng các bản vulnerable của `axios`, `brace-expansion`, `nanoid`, `postcss`, `shell-quote`, `undici`; advisory moderate còn lại thuộc `jsdom`.
- **Vị trí:** `vibegraph-web/package.json:21,65`; khóa cụ thể trong `vibegraph-web/package-lock.json` tại các package tương ứng.
- **Bằng chứng:** `npm audit --audit-level=moderate` trả exit code 1 và liệt kê 8 advisories; lint/type-check/test/build vẫn pass nên đây là supply-chain risk độc lập với compile correctness.
- **Giải pháp:** nâng từng dependency theo changelog, cập nhật lockfile bằng `npm install`/`npm audit fix` có review, chạy lại `npm audit`, unit test và production build; ưu tiên `websocket-driver` và `axios`.

#### H9 — Rate-limit chạy sau bước xác thực API key bằng BCrypt
- **Vấn đề:** Request sai có prefix API key hợp lệ có thể tiêu tốn tối đa 5 phép BCrypt trước khi rate limiter tổng quát được thực thi, tạo bề mặt DoS CPU trên endpoint MCP/patch.
- **Vị trí:** `SecurityConfig.java:179–184`; `ApiKeyAuthFilter.java:79–94`, đặc biệt `passwordEncoder.matches` dòng 93; reachability tại `ApiKeyAuthFilter.java:116–120`.
- **Bằng chứng:** `apiKeyAuthFilter` được đặt sau `UsernamePasswordAuthenticationFilter`, còn `rateLimitFilter` chỉ đặt trước `AuthorizationFilter`; do đó API-key authentication hoàn thành trước rate-limit. Filter này áp dụng cho `/mcp/**`, `/api/projects/{id}/patch` và `/api/projects/current/patch`. Runtime test của Qwen chưa tái hiện chênh lệch với fake prefix ngẫu nhiên, nhưng không phủ trường hợp attacker biết prefix 12 ký tự đang tồn tại; cơ chế static vẫn reachable.
- **Giải pháp:** không di chuyển nguyên limiter hiện tại lên trước auth vì `RateLimitFilter` dùng identity/API-key ref sau xác thực. Thêm limiter sớm theo IP cho bề mặt API-key trước BCrypt, rồi giữ limiter identity-aware hiện tại sau auth; hoặc thêm failure limiter riêng trong `ApiKeyAuthFilter`.
- **Snippet đề xuất:**
  ```java
  .addFilterBefore(preAuthApiKeyRateLimitFilter, ApiKeyAuthFilter.class)
  .addFilterAfter(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
  .addFilterBefore(rateLimitFilter, AuthorizationFilter.class);
  ```

#### H10 — `readRange()` nạp toàn bộ file vào RAM trước khi áp giới hạn response
- **Vấn đề:** Giới hạn 300 dòng/64 KiB chỉ áp sau khi `Files.readAllLines` đã materialize toàn file; file văn bản lớn có đuôi được phép gây heap spike, GC pressure hoặc OOM.
- **Vị trí:** `SourceFileServiceImpl.java:88–139`, trọng tâm dòng 110 và helper `readAllLines` dòng 280–283; REST caller `SourceController.java:45–53` và nhiều MCP caller qua `SourceFileService`.
- **Bằng chứng:** nhánh search kiểm tra `Files.size()` trước ở dòng 196, nhưng `readRange` không có guard tương đương. Runtime verification với file 200 MiB ghi nhận memory backend tăng khoảng 211,6 MiB dù response chỉ trả 300 dòng.
- **Giải pháp:** kiểm tra kích thước file trước khi đọc và stream bằng `BufferedReader`, bỏ qua đến `startLine`, dừng ngay khi đạt `MAX_LINES` hoặc `MAX_BYTES`; không dùng `readAllLines` trên request path.
- **Snippet đề xuất:**
  ```java
  if (Files.size(candidate) > MAX_FILE_BYTES_TO_READ) {
      return notServed(relativePath, "File is too large to serve safely.");
  }
  try (BufferedReader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
      return readBoundedRange(reader, startLine, endLine, MAX_LINES, MAX_BYTES);
  }
  ```

#### H11 — Private-key redaction chỉ che dòng BEGIN, thân base64 vẫn được trả về
- **Vấn đề:** Redaction chạy từng dòng và pattern chỉ nhận header `BEGIN ... PRIVATE KEY`; các dòng key material phía sau vẫn nguyên văn trong REST/MCP source response.
- **Vị trí:** `SourceFileServiceImpl.java:68`, `SourceFileServiceImpl.java:128–138`, `SourceFileServiceImpl.java:305–319`.
- **Bằng chứng:** khi gặp header, `redact` thay đúng một dòng bằng `[REDACTED]`; hàm không giữ state đến `END ... PRIVATE KEY`. Blocked filename chỉ phủ một số tên cố định, nên khóa nằm trong `.pem`, `.key`, `.txt` hoặc source fixture vẫn có thể lọt nếu extension được phép.
- **Giải pháp:** ưu tiên từ chối phục vụ toàn file khi phát hiện private-key block; nếu cần giữ nội dung khác, dùng state machine che toàn bộ `BEGIN` → `END` và dùng chung cho read/search/snippet.
- **Snippet đề xuất:**
  ```java
  boolean insidePrivateKey = false;
  if (PRIVATE_KEY_BEGIN.matcher(line).find()) insidePrivateKey = true;
  if (insidePrivateKey) output.append(REDACTED).append('\n');
  if (PRIVATE_KEY_END.matcher(line).find()) insidePrivateKey = false;
  ```

---

### 🟡 MEDIUM

#### M1 — getFullGraph 1 query nhân bản node theo cạnh, cap mặc định 0
- **Vấn đề:** Query trả O(số cạnh) dòng gom hết vào RAM; guardrail chưa hiệu lực mặc định.
- **Vị trí:** `Neo4jGraphRepository.getFullGraph` (dòng 261–319).
- **Bằng chứng:** dùng `MATCH (n:Symbol) OPTIONAL MATCH (n)-[r]->(m:Symbol)`; guardrail `vibegraph.graph.node-limit` mặc định yaml = 0, docker-compose KHÔNG truyền `VIBEGRAPH_GRAPH_NODE_LIMIT` qua `environment:`, và file `.env` hiện tại cũng KHÔNG đặt biến này → triển khai mặc định không có cap nào hiệu lực (xem thêm L2). `CachingGraphRepository` (cache 5 phút, max 32 snapshot) giảm tải Neo4j nhưng payload HTTP vẫn phình; endpoint graph còn chạy `architectureProjector` + `graphResponseFilter` trên toàn payload mỗi request.
- **Giải pháp:** tách 2 query nodes/edges; đặt cap dương trong deploy profile; đưa biến guardrail vào `environment:` của compose; cân nhắc streaming/chunked response.

#### M2 — Project listing load toàn bộ tenant rồi filter bằng Java
- **Vấn đề:** Aggregate stats cho mọi project và filter ownership trong Java thay vì trong query.
- **Vị trí:** `projectMetadataCypher` (dòng 95–105), `ProjectController.list` (dòng 88–98).
- **Bằng chứng:** query tính stats aggregate cho MỌI project; controller load toàn bộ project mọi user rồi filter bằng `ownedIds` trong Java.
- **Giải pháp:** push ownership filter xuống query (`WHERE p.id IN $ids` hoặc join Postgres `owner_id`); cache/đếm stats định kỳ.

#### M3 — Config chết
- **Vấn đề:** Config khai báo nhưng không code nào đọc — đánh lừa người vận hành.
- **Vị trí:** `vibegraph.parser.use-cache`/`cache-dir` trong `application.yaml`/`application-prod.yaml`; `PROJECTS_AUTO_REFRESH_INTERVAL_MS`, `FA2_ITERATIONS`, `FA2_OUTLIER_CLAMP_PERCENTILE`, `FA2_ITERATIONS_LARGE` tại `runtimeConfig.ts:100,212,242,258` (frontend).
- **Bằng chứng:** grep toàn `src/main` không có code nào đọc các key này. Đây chính là cache parse được kỳ vọng cho bottleneck import.
- **Giải pháp:** hiện thực hóa hoặc gỡ config để khỏi đánh lừa người vận hành.

#### M4 — ApiKeyAuthFilter: BCrypt theo candidate prefix + DB write mỗi request MCP *(viết lại bằng chứng)*
- **Vấn đề:** Mỗi tool call MCP tốn chi phí match BCrypt + 1 DB write.
- **Vị trí:** `ApiKeyAuthFilter.java` dòng 29, 90–94, 149–150.
- **Bằng chứng:** BCrypt chạy qua stream lazy `.filter(...).findFirst()` (`ApiKeyAuthFilter.java:90–94`) — thường chỉ 1 lần match với key hợp lệ, tối đa 5 lần khi cả 5 candidate cùng prefix đều trượt (`MAX_PREFIX_CANDIDATES=5`, dòng 29). Phần DB write `apiKeyRepository.save(key)` cập nhật `lastUsedAt` MỖI request (dòng 149–150) là vấn đề riêng và được giữ ở Medium; chi phí pre-auth DoS đã tách thành H9.
- **Giải pháp:** throttle `lastUsedAt` (≥1 phút/lần); cân nhắc so sánh HMAC/SHA-256 nhanh thay BCrypt cho API key.

#### M5 — Stateful per-instance cản trở scale ngang
- **Vấn đề:** Nhiều thành phần giữ trạng thái cục bộ → không chạy đa replica được.
- **Vị trí:** STOMP `RealtimeAccountAccessInterceptor`, SimpleBroker, rate-limit/`ConcurrentImportGuard` (Caffeine), file watcher, local import.
- **Bằng chứng:** 5 map sessionId trong RAM; SimpleBroker in-process (scale ngang WS bắt buộc sticky session hoặc broker ngoài RabbitMQ/Redis relay); rate-limit/ConcurrentImportGuard Caffeine per-instance (N replica = N× giới hạn); file watcher gắn FS cục bộ (prod đã tắt mặc định — hợp lý); local import gắn `./projects` host.
- **Giải pháp:** ghi rõ "single-replica only" trong DEPLOYMENT.md tới khi có shared storage + rate limit tập trung; coi đây trigger trong capacity policy.

#### M6 — Drift `.env` ↔ `.env.example`
- **Vấn đề:** Người vận hành không biết giá trị nào hiệu lực.
- **Vị trí:** `.env` và `.env.example` đối chiếu `application.yaml`.
- **Bằng chứng:** `.env` chứa key chết (`APP_BASE_URL`, `FRONTEND_BASE_URL`), thiếu hàng loạt key mới (rate-limit, graph guardrails, analysis executor, admin bootstrap, `OAUTH_REDIRECT_BASE_URL`); mặc định lệch: `VIBEGRAPH_PARSER_DEEP_CPG` example=false vs `application.yaml:296` default=true; `VIBEGRAPH_GRAPH_NODE_LIMIT` example=2500 vs yaml=0 — và vì `.env` hiện tại không đặt biến này, cap 2500 của example chưa bao giờ hiệu lực (xem L2).
- **Giải pháp:** sinh lại `.env` từ example; thêm script/test kiểm tra mỗi key example phải có trong yaml và ngược lại; đồng nhất mặc định.

#### M7 — Hai nguồn Postgres schema + Flyway ignore-missing
- **Vấn đề:** Drift schema không được phát hiện tự động; migration bị xóa nhầm không cảnh báo.
- **Vị trí:** `database/schema/V1__init_auth_schema.sql` vs `src/main/resources/db/migration/`; config `flyway.ignore-migration-patterns: "*:missing"`.
- **Bằng chứng:** bản copy đã lệch bản Flyway (README quy ước giữ giống nhau nhưng không tự động kiểm tra); ignore-pattern đặt do V16 bị xoá sau khi apply — migration bị xoá nhầm về sau không bị phát hiện.
- **Giải pháp:** bỏ bản copy `database/schema/` để README trỏ `db/migration/` (một nguồn sự thật); thu hẹp ignore-pattern hoặc ghi chú trong CI.

#### M8 — CI không build image Docker, không validate compose
- **Vấn đề:** Lỗi compose/Dockerfile chỉ lộ khi deploy.
- **Vị trí:** `.github/workflows/backend.yml`, `frontend.yml`.
- **Bằng chứng:** backend.yml chạy `mvnw verify`, frontend.yml chạy type-check/test/lint/build/audit — tốt, nhưng không job nào chạy `docker compose config` / `docker build`.
- **Giải pháp:** thêm job nhẹ `docker compose config -q` + `docker build .` khi đổi Dockerfile/compose.

#### M9 — Port DB expose thẳng ra host, APOC unrestricted
- **Vấn đề:** Rủi ro nếu host expose ra mạng.
- **Vị trí:** `docker-compose.yml:24–30` (publish 7474/7687 Neo4j, 5432 Postgres); `NEO4J_dbms_security_procedures_unrestricted: apoc.*`.
- **Bằng chứng:** ổn cho dev cục bộ, rủi ro nếu host expose ra mạng.
- **Giải pháp:** bind `127.0.0.1:` hoặc bỏ publish; whitelist APOC theo thủ tục thực dùng.

#### M10 — .dockerignore thiếu `.env` và thư mục artifact
- **Vấn đề:** Cả backend root context và frontend context đều thiếu exclude quan trọng; frontend còn `COPY . .`, nên local `node_modules`, `dist`, log/screenshot bị gửi vào Docker daemon và làm hỏng cache/build time.
- **Vị trí:** root `.dockerignore`; `vibegraph-web/Dockerfile:13`; file `vibegraph-web/.dockerignore` không tồn tại.
- **Bằng chứng:** root `.dockerignore` có tồn tại nhưng chưa loại `.env`, `.vibegraph/`, `qa-artifacts/`, `logs/`, `projects/`; frontend context hiện có `node_modules`, `dist`, logs/screenshots và không có ignore riêng. Qwen xếp High là quá mức: đây là Medium build-context/security hygiene, trừ khi Dockerfile thật sự copy secret vào final layer.
- **Giải pháp:** bổ sung exclude root và tạo `vibegraph-web/.dockerignore` tối thiểu cho node artifacts/editor/log/test evidence.
- **Snippet đề xuất:**
  ```dockerignore
  node_modules
  dist
  *.log
  .vite
  coverage
  screenshots
  ```

#### M11 — Frontend: 5 view gốc import tĩnh — graph stack lọt bundle ban đầu
- **Vấn đề:** Khách landing page tải luôn Sigma + Graphology + FA2 dù chưa login.
- **Vị trí:** `router/index.ts` dòng 2–6; `vite.config.ts` (không manualChunks).
- **Bằng chứng:** import tĩnh `GraphView`/`HomeView`/`LandingView`/`LoginView`/`RegisterView`; entry chunk `dist/assets/index-*.js` = 556KB (chưa gzip); admin views đã lazy đúng.
- **Giải pháp:** dynamic import 5 view gốc; manualChunks tách sigma/graphology và vue-i18n; kiểm chứng bằng `vite build` + rollup-plugin-visualizer.

#### M12 — Frontend: 31/67 file không dùng i18n, toàn bộ vùng graph hardcode tiếng Anh
- **Vấn đề:** Người dùng chọn tiếng Việt vẫn thấy UI graph tiếng Anh.
- **Vị trí:** `GraphCanvas`, `SearchBar`, `DiagramPanel`, `NodeDetailPanel`, `ImpactAnalysisPanel`, `FilterPanel`, `ExplorerPanel`, `CodeViewerModal`, `GraphView`…
- **Bằng chứng:** 31/67 file không dùng i18n trong khi app hỗ trợ en-US/vi-VN (có `localeParity.spec.ts`).
- **Giải pháp:** i18n hóa vùng graph trước (mặt tiền sản phẩm); UI nguyên tử (Button/Spinner) có thể bỏ qua.

#### M13 — Frontend: layout graph không tất định + tốn CPU cố định 8 giây
- **Vấn đề:** Khớp audit vòng 1 B1/B2 — cùng project ra hình khác nhau giữa các máy; mỗi rebuild chạy worker đủ 8s kể cả khi đã hội tụ.
- **Vị trí:** `useSigma.ts` dòng 487–494; `GraphCanvas.vue` dòng 679, 683.
- **Bằng chứng:** dừng theo `setTimeout` thay vì số iterations; dùng `Math.random()` cho node mới.
- **Giải pháp:** dừng theo số iterations; seed vị trí node mới bằng hash id (đã có sẵn `seededUnit` trong `graphAdapter.ts`).

#### M14 — Frontend: ảnh tài nguyên ~1,4 MB chưa tối ưu *(sửa số liệu)*
- **Vấn đề:** Asset nguyên bản copy thẳng vào dist.
- **Vị trí:** `vibegraph-logo.png` (635.570 B — dùng làm cả favicon lẫn logo), `LogoClaudeAI.jpg` (130KB), 6 logo khác 25–111KB.
- **Bằng chứng:** tổng ~1,4 MB ảnh chưa tối ưu trong bundle (kiểm chứng: Get-ChildItem đo 1.440.014 bytes / 12 file; riêng `vibegraph-logo.png` 635.570 B).
- **Giải pháp:** WebP/AVIF + resize (favicon 64–128px), giảm >1MB.

#### M15 — Frontend: god store `admin.ts` + `lib/api.ts` monolith
- **Vấn đề:** State admin tập trung quá lớn, api client đang phình.
- **Vị trí (đo lại v2.4):** store `admin.ts` 782 dòng (~25 mảng state: users, plans, pricing, reports, feature flags, announcements, security events, IP blocks, audit logs + polling/SSE); `lib/api.ts` 989 dòng.
- **Bằng chứng:** kích thước và số trách nhiệm trong mỗi file.
- **Giải pháp:** tách store theo domain (adminUsers/adminSecurity/adminBilling/adminContent) mỗi store sở hữu polling/SSE riêng; tách `api.ts` theo module (api/auth, api/graph, api/admin) giữ chung http instance — làm sớm trước khi phình thêm.

#### M16 — Frontend: SFC/composable quá lớn
- **Vấn đề:** File vượt xa ngưỡng bảo trì.
- **Vị trí (đo lại v2.4):** `UserDetailDrawer.vue` 3201 dòng, `LandingView.vue` 2958, `DashboardView.vue` 1524, `GraphCanvas.vue` 1468, `UsersTableView.vue` 1114, `SecurityView.vue` 1046, `useSigma.ts` 1037, `DiagramPanel.vue` 968. `GraphCanvas` chứa sidebar resize/focus/realtime patch/filter; `useSigma` chứa post-layout passes có thể tách.
- **Bằng chứng:** kích thước đo trực tiếp.
- **Giải pháp:** tách composable/SFC con theo trách nhiệm.

#### M17 — Project ID chỉ có 32 bit ngẫu nhiên và ghi đè khi trùng
- **Vấn đề:** Ba đường tạo project cắt UUID còn 8 hex ký tự; `ConcurrentHashMap.put` không kiểm tra collision. Xác suất va chạm tăng theo birthday bound (xấp xỉ 50% quanh 77 nghìn ID), nên không phù hợp định danh dài hạn/multi-tenant.
- **Vị trí:** `ProjectServiceImpl.java:61–72`, `ProjectServiceImpl.java:89–103` — các dòng 62, 91, 101 tạo ID; dòng 72/93/103 ghi vào map.
- **Bằng chứng:** schema Postgres đã cho phép `VARCHAR(64)`, vì vậy giới hạn 8 ký tự là do code chứ không phải ràng buộc dữ liệu. Collision có thể ghi đè registry trước khi ownership/persistence phát hiện.
- **Giải pháp:** dùng full UUID/UUID không dấu gạch hoặc ULID; giữ unique/primary key ở persistent store và xử lý duplicate như lỗi retry, không dựa vào map.
- **Snippet đề xuất:**
  ```java
  String id = UUID.randomUUID().toString();
  if (projects.putIfAbsent(id, project) != null) {
      throw new IllegalStateException("Generated duplicate project id");
  }
  ```

#### M18 — N+1 query khi liệt kê người dùng trong admin
- **Vấn đề:** Mỗi user trong một page phát sinh thêm 2 query (settings + tổng storage), ngoài query page ban đầu; page 20 user thành ít nhất 41 query.
- **Vị trí:** `AdminService.java:225–229` map từng user; `AdminService.java:518–530` gọi `settingsRepository.findById` và `projectUsageRepository.sumStorageBytesByOwnerId`.
- **Bằng chứng:** `Page.map(this::toAdminUserResponse)` gọi mapper cho từng phần tử; mapper truy repository riêng lẻ, không có batch/prefetch.
- **Giải pháp:** lấy page user trước, batch `findAllById(userIds)` cho settings và một aggregate query `GROUP BY owner_id`; map từ hai `Map<UUID,...>` trong memory.
- **Snippet đề xuất:**
  ```java
  List<UUID> ids = users.getContent().stream().map(User::getId).toList();
  Map<UUID, UserAccountSettings> settings = indexByUserId(settingsRepository.findAllById(ids));
  Map<UUID, Long> usage = projectUsageRepository.sumStorageBytesByOwnerIds(ids).stream()
      .collect(toMap(StorageByOwner::ownerId, StorageByOwner::bytes));
  ```

#### M19 — Polling GitHub import không hủy khi component unmount hoặc reset
- **Vấn đề:** WebSocket có teardown trong `finally`, nhưng vòng polling vô hạn có watchdog không nhận cancellation signal; rời view/reset vẫn tiếp tục timer và request cho tới terminal/timeout, rồi có thể cập nhật state của scope cũ.
- **Vị trí:** `vibegraph-web/src/composables/useGitHubImport.ts:68–136`, `useGitHubImport.ts:210–250`.
- **Bằng chứng:** `delay()` chỉ dùng `setTimeout`; `waitForGitHubAnalysis()` không nhận `AbortSignal`; file không import/gọi `onScopeDispose`. `reset()` chỉ đổi state, không hủy tác vụ đang chạy.
- **Giải pháp:** tạo `AbortController` cho mỗi import, hủy controller cũ khi bắt đầu/reset/unmount; truyền signal vào delay và `projectApi.get` nếu HTTP client hỗ trợ.
- **Snippet đề xuất:**
  ```ts
  let activeImport: AbortController | null = null
  onScopeDispose(() => activeImport?.abort())

  activeImport?.abort()
  activeImport = new AbortController()
  await waitForGitHubAnalysis(project, setProgress, activeImport.signal)
  ```

#### M20 — X-Forwarded-For chọn IP public trái nhất khi bật trust proxy
- **Vấn đề:** Khi `trustProxy=true` và remote thuộc trusted proxy, resolver lấy public token trái nhất do client cung cấp; proxy thường append địa chỉ thật ở bên phải, nên attacker có thể chèn token trái để xoay key rate-limit/IP-block.
- **Vị trí:** `ClientAddressResolver.java:19–35`, trọng tâm `.findFirst()` dòng 34.
- **Bằng chứng:** YAML mặc định `trustProxy=false` là fail-closed, nhưng `.env` hiện hành đặt `VIBEGRAPH_TRUST_PROXY=true` và trusted range gồm Docker bridge/localhost. Vì `.env` là trạng thái deployment cục bộ, finding vẫn Medium có điều kiện theo môi trường chứ không High vô điều kiện; khi dùng cấu hình hiện tại, điều kiện đã được kích hoạt.
- **Giải pháp:** parse chuỗi từ phải sang trái, bỏ qua đúng các hop proxy tin cậy rồi chọn hop không tin cậy đầu tiên; thu hẹp `trusted-proxies` về IP/CIDR của proxy thực.
- **Snippet đề xuất:**
  ```java
  List<String> chain = canonicalForwardedChain(forwarded);
  for (int i = chain.size() - 1; i >= 0; i--) {
      if (!isTrustedProxy(chain.get(i))) return chain.get(i);
  }
  return remote;
  ```

#### M21 — IP-block truy vấn Postgres trên mọi request
- **Vấn đề:** `IpBlockFilter` nằm sớm trong chain và luôn gọi repository, kể cả static/auth request không bị block; tải HTTP tăng trực tiếp thành tải DB và cạnh tranh Hikari pool.
- **Vị trí:** `IpBlockFilter.java:30–39`; `IpBlockService.java:32–35`.
- **Bằng chứng:** `findActive` là transaction read-only gọi `repository.findActive` mỗi lần, không có `@Cacheable`, local cache hay snapshot.
- **Giải pháp:** cache negative/positive lookup TTL ngắn hoặc nạp snapshot active blocks; invalidate ngay sau create/update/remove để không kéo dài block/unblock stale.
- **Snippet đề xuất:**
  ```java
  private final Cache<String, Optional<IpBlockView>> activeBlocks = Caffeine.newBuilder()
      .maximumSize(10_000).expireAfterWrite(Duration.ofSeconds(15)).build();
  return activeBlocks.get(canonicalIp, repository::findActiveView);
  ```

#### M22 — Cache response LLM không giới hạn kích thước hoặc TTL
- **Vấn đề:** Mỗi tập use case khác nhau thêm một raw LLM response vào `ConcurrentHashMap` vĩnh viễn; service singleton có thể tăng heap không chặn theo số project/graph variation.
- **Vị trí:** `LlmUseCaseRefiner.java:67–105`, trọng tâm khai báo dòng 71 và `put` dòng 105.
- **Bằng chứng:** cache key là SHA-256 nên key không trùng theo input; không có eviction, TTL hoặc thống kê trọng lượng. Giá trị là raw model output có kích thước biến thiên.
- **Giải pháp:** dùng Caffeine đã có trong dự án với `maximumSize`/`maximumWeight` và expiry; không cache response quá lớn hoặc không parse được.
- **Snippet đề xuất:**
  ```java
  private final Cache<String, String> responseCache = Caffeine.newBuilder()
      .maximumSize(1_000)
      .expireAfterAccess(Duration.ofHours(6))
      .build();
  ```

#### M23 — Bảy job `@Scheduled` dùng scheduler mặc định một thread
- **Vấn đề:** `RequestEventService.flush()` mỗi 2 giây, online sampling và năm job maintenance 02:00–03:30 chia sẻ scheduler mặc định của Spring Boot; một job blocking làm các job sau bị head-of-line blocking.
- **Vị trí:** `RequestEventService.java:146`; `OnlineUserHistoryService.java:21`; `FeedbackReportService.java:171`; `AuditService.java:140`; `SupabaseRetentionService.java:29`; `RefreshSessionService.java:193`; `ProjectTrashService.java:108`.
- **Bằng chứng:** source có đúng 7 annotation job (không tính comment/guide), không có `spring.task.scheduling.*`, `TaskScheduler`, `ThreadPoolTaskScheduler` hoặc `SchedulingConfigurer`. Telemetry queue có capacity 10.000 (`application.yaml:125`), shed-oldest ở `RequestEventService.java:351–372`; vì vậy scheduler stall làm giảm nhịp drain và tăng xác suất drop, kể cả security event.
- **Giới hạn kết luận:** chưa có benchmark chứng minh purge thường kéo dài 200 giây hoặc tạo "cửa sổ mù mỗi đêm"; mức Medium phản ánh cơ chế blocking có thật. Tăng pool chỉ giảm head-of-line blocking, không biến telemetry thành audit-grade/durable.
- **Giải pháp:** cấu hình scheduler pool riêng (ví dụ 4 thread), tách telemetry flush khỏi maintenance executor, đặt timeout/metrics cho từng job; xử lý M24 song song.
- **Snippet đề xuất:**
  ```yaml
  spring:
    task:
      scheduling:
        pool:
          size: ${SCHEDULER_POOL_SIZE:4}
  ```

#### M24 — Purge trash nạp toàn bộ danh sách và giữ transaction dài
- **Vấn đề:** Sweep lấy mọi project hết hạn vào `List`, rồi trong một method `@Transactional` tuần tự purge Neo4j, Postgres và filesystem; runtime/memory tăng không chặn theo backlog, transaction control-plane có thể sống suốt toàn sweep.
- **Vị trí:** `ProjectTrashService.java:108–123`; `ProjectOwnershipRepository.java:55`; `ProjectDeletionOrchestrator.java:179–200`.
- **Bằng chứng:** repository trả `List<ProjectOwnership> findByDeletedAtLessThan(Instant cutoff)` không `Pageable`/`LIMIT`; mỗi project có thể đi qua `Files.walk()` và các thao tác cross-store. M23 vẫn tồn tại ngay cả khi tăng scheduler pool, và M24 vẫn tồn tại ngay cả khi tách scheduler.
- **Giải pháp:** keyset/page theo `deletedAt,projectId`, giới hạn batch; transaction chỉ bao quanh một project hoặc một batch control-plane ngắn, không ôm toàn sweep.
- **Snippet đề xuất:**
  ```java
  Slice<ProjectOwnership> batch = ownershipRepository
      .findByDeletedAtLessThanOrderByDeletedAtAscProjectIdAsc(cutoff, PageRequest.of(0, 100));
  batch.forEach(item -> purgeOneInNewTransaction(item.getProjectId()));
  ```

#### M25 — Handler `IllegalStateException` quá rộng: lỗi hạ tầng thành 409 và trả raw message
- **Vấn đề:** Mọi `IllegalStateException` reachable qua HTTP bị coi là precondition 409, không log, và trả nguyên `ex.getMessage()`; lỗi hạ tầng/data invariant bị che thành lỗi client, làm mất observability và có thể lộ tên biến/cấu hình nội bộ.
- **Vị trí:** `GlobalExceptionHandler.java:236–245`; catch-all an toàn/log tại `GlobalExceptionHandler.java:316–323`; throw site ví dụ `AnalyzeServiceImpl.java:72–76`, `AtomicPatchApplier.java:46`, `CliRepositoryService.java:73`, `CreditBalanceService.java:144`.
- **Bằng chứng:** nhánh 409 đứng trước catch-all, không phân biệt precondition domain với SHA-256 unavailable, workspace I/O, atomic patch failure hoặc multiple active balance. `AnalyzeServiceImpl` còn đưa số node/edge và tên hai env var vào message.
- **Giải pháp:** tạo exception domain typed cho precondition người dùng có thể sửa; chỉ handler đó trả 409/message an toàn. Để lỗi hạ tầng rơi vào 500 catch-all đã log; không chỉ thêm log rồi giữ nguyên status.
- **Snippet đề xuất:**
  ```java
  @ExceptionHandler(ProjectPreconditionException.class)
  ResponseEntity<ApiResponse<Void>> handlePrecondition(ProjectPreconditionException ex) {
      return conflict("PRECONDITION_FAILED", ex.safeMessage());
  }
  // IllegalStateException không có handler riêng: rơi vào handleGeneric() -> 500 + log.
  ```

#### M26 — Ba Java test-source được track nhưng rỗng, hai file khớp Failsafe include
- **Vấn đề:** Suite có 174 file Java nhưng chỉ 155 file chứa `@Test`/`@ArchTest`; ba file 0 byte tạo tín hiệu coverage sai, đặc biệt hai `*IT.java` được Failsafe include mà không chạy kiểm thử nào.
- **Vị trí:** `src/test/java/com/vibegraph/VibeGraphIT.java`; `src/test/java/com/vibegraph/graph/importer/github/GitHubImportIT.java`; `src/test/java/com/vibegraph/integration/FinalIntegrationTest.java`; `pom.xml:329–348`.
- **Bằng chứng:** cả ba file có size 0 và được `git ls-files` track; Failsafe include `**/*IT.java` khớp hai file đầu. GitHub import vẫn có unit/parser/preflight tests, nên finding đúng là thiếu end-to-end integration coverage, không phải "không có test GitHub".
- **Giải pháp:** viết IT thật cho application smoke + GitHub import state machine, hoặc xóa placeholder và ghi test gap rõ trong backlog; thêm CI check chỉ cấm file test rỗng/placeholder, không cấm helper/fixture thiếu `@Test`.
- **Snippet đề xuất:**
  ```powershell
  $emptyTests = Get-ChildItem src/test/java -Recurse -Filter *.java |
    Where-Object Length -eq 0
  if ($emptyTests) { throw "Empty Java test sources: $($emptyTests.FullName -join ', ')" }
  ```

#### M27 — File watcher tải và diff toàn graph hai lần cho mỗi lần save
- **Vấn đề:** Một thay đổi file đơn lẻ gọi `getFullGraph(projectId)` trước và sau mutation, rồi dựng set/diff toàn bộ node/edge; autosave làm chi phí O(graph) lặp lại trên hot path realtime.
- **Vị trí:** `FileChangeBroadcaster.java:85–176`, trọng tâm dòng 99 và 110.
- **Bằng chứng:** code chỉ re-parse một file nhưng snapshot toàn project hai lần; repository hiện chỉ có `deleteFile` và `getFullGraph`, chưa có API đọc slice theo file. Finding reachable qua file watcher và có unit/integration coverage cho broadcaster.
- **Giải pháp:** trước khi xóa, query node/edge IDs thuộc file; parser đã trả slice mới nên có thể phát `GraphChangeSet` trực tiếp và tính removal từ slice cũ. Nếu cần consistency, thêm repository method `getFileSlice(projectId,filePath)` thay vì full snapshot.
- **Snippet đề xuất:**
  ```java
  GraphFileSlice before = graphRepository.getFileSlice(projectId, storedPath);
  graphRepository.replaceFile(projectId, storedPath, parsed);
  GraphRemoval removed = diffIds(before, parsed);
  graphUpdateController.broadcastIncremental(projectId, toUpserts(parsed), null, removed);
  ```

#### M28 — Actuator metrics/prometheus cho mọi user đã đăng nhập
- **Vấn đề:** Production expose `health,info,metrics,prometheus`; Security chỉ permit public health và để các endpoint actuator còn lại rơi vào `anyRequest().authenticated()`, nên role USER thường đọc được topology/metric nội bộ.
- **Vị trí:** `application-prod.yaml:107–118`; `SecurityConfig.java:159–171`.
- **Bằng chứng:** chỉ `/api/admin/**` có `hasRole("ADMIN")`; không có rule `/actuator/**` riêng. Đây là authorization gap, không phải public exposure vì anonymous vẫn bị chặn.
- **Giải pháp:** health public tối thiểu; metrics/prometheus admin-only hoặc tốt hơn bind management port riêng, không publish ra user network.
- **Snippet đề xuất:**
  ```java
  auth.requestMatchers("/actuator/health").permitAll();
  auth.requestMatchers("/actuator/**").hasRole("ADMIN");
  ```

#### M29 — Multipart parser cho phép request tới 2 GiB trước tầng business quota
- **Vấn đề:** Host-level ceiling 2048/2050 MB quá cao; servlet multipart có thể nhận/spool body trước khi controller kiểm account quota, nên một request hợp lệ về content-type vẫn gây áp lực disk/temp/I/O rất lớn. Không có bằng chứng nó luôn buffer 2 GiB trong RAM.
- **Vị trí:** `application.yaml:30–35`; `application-prod.yaml:28–31`; quota check tại `ArchiveImportServiceImpl.java:174–188`.
- **Bằng chứng:** service kiểm `file.getSize()`/quota trước `Files.copy`, do đó claim "chỉ kiểm quota sau upload" là sai ở controller layer; nhưng container đã parse multipart để tạo `MultipartFile`, và enterprise quota có thể cấu hình cao/unbounded.
- **Giải pháp:** đặt hard cap deploy-level sát kích thước gói lớn nhất, thêm reverse-proxy `client_max_body_size`, temp-volume quota và 413 test. Account quota vẫn giữ riêng.
- **Snippet đề xuất:**
  ```yaml
  spring.servlet.multipart:
    max-file-size: ${VIBEGRAPH_IMPORT_ARCHIVE_HARD_MAX_SIZE:512MB}
    max-request-size: ${VIBEGRAPH_IMPORT_ARCHIVE_HARD_MAX_REQUEST_SIZE:513MB}
  ```

#### M30 — `UsersTableView` để unhandled rejection ở load/filter/refresh/pagination
- **Vấn đề:** Năm call `fetchUsers`/`fetchPlans` không có error boundary; network/backend failure giữ dữ liệu cũ nhưng không báo lỗi và tạo `Uncaught (in promise)`.
- **Vị trí:** `UsersTableView.vue:70–79`, `UsersTableView.vue:104–112`, `UsersTableView.vue:316–347`.
- **Bằng chứng:** block/unblock/create đã có `try/catch` nên không được nói toàn trang thiếu xử lý; runtime Qwen T4 bật Network Offline rồi Search xác nhận UI không báo lỗi và console có rejection.
- **Giải pháp:** gom mọi load/pagination qua một `loadUsers` wrapper có loading/error/finally; event handler template gọi wrapper, không gọi Promise trực tiếp.
- **Snippet đề xuất:**
  ```ts
  async function loadUsers(page = currentPage.value): Promise<void> {
    loadError.value = ''
    try { await adminStore.fetchUsers(filtersFor(page)) }
    catch (error: unknown) { loadError.value = toUserMessage(error) }
  }
  ```

#### M31 — `dev-up.ps1` khởi động Neo4j nhưng bỏ Postgres bắt buộc
- **Vấn đề:** Script quảng bá luồng chạy đầy đủ nhưng chỉ `docker compose up -d neo4j`; backend mặc định cần PostgreSQL cho datasource/Flyway/JPA validate nên checkout mới dễ fail trước khi mở API.
- **Vị trí:** `scripts/dev-up.ps1:32–58`; `application.yaml:9–28`.
- **Bằng chứng:** datasource mặc định `localhost:5432`, Flyway enabled, `ddl-auto: validate`; script không start/wait Postgres.
- **Giải pháp:** start `postgres neo4j`, đợi cả `pg_isready` và Neo4j health trước khi launch backend; hoặc đổi script gọi full `docker compose up` theo tài liệu.
- **Snippet đề xuất:**
  ```powershell
  docker compose up -d postgres neo4j | Out-Null
  docker compose exec -T postgres pg_isready -U $env:POSTGRES_USER -d $env:POSTGRES_DB
  ```

---

### 🟢 LOW

#### L1 — Frontend: v-html 3 điểm thiếu lớp phòng thủ
- **Vấn đề:** Đã escape đúng nhưng thiếu sanitize belt-and-suspenders; nếu `esc()` bị bỏ sót là XSS.
- **Vị trí:** `CodeViewerModal.vue:242` (hljs); `DiagramPanel.vue:500,583` (SVG).
- **Bằng chứng:** nguồn dữ liệu là tên class/method từ project người dùng import.
- **Giải pháp:** thêm DOMPurify sanitize trước `v-html` + test.

#### L2 — Frontend: polling chạy cả khi tab ẩn + Safe Mode render cap tắt — KHÔNG cap nào hiệu lực ở triển khai mặc định *(lật ngược đánh giá rủi ro)*
- **Vấn đề:** Lớp phòng thủ client đang vô hiệu; đồng thời không có cap nào hiệu lực ở cả server lẫn client với triển khai mặc định.
- **Vị trí:** `UserLayout.vue:85` (setInterval 10s), `LandingView.vue:370`; `GRAPH_SAFE_NODE_LIMIT=0` tại `runtimeConfig.ts:60`; `application.yaml:272`.
- **Bằng chứng (sự thật đã kiểm chứng, thay cho nhận định "backend đã cap 2500 node nên rủi ro thấp" của v2):** polling chạy khi tab ẩn (`DashboardView` đã làm đúng với `visibilityState`); `application.yaml:272` mặc định `node-limit = 0`; `application-prod.yaml` không override; docker-compose KHÔNG truyền `VIBEGRAPH_GRAPH_NODE_LIMIT` qua `environment:`; file `.env` hiện tại cũng KHÔNG đặt biến này (chỉ có `VITE_GRAPH_SAFE_NODE_LIMIT=0` là biến frontend). Cap 2500 chỉ tồn tại trong `.env.example` chưa ai copy → với triển khai mặc định KHÔNG có cap nào hiệu lực, cả server lẫn client.
- **Giải pháp:** bật cap dương cả backend (đưa vào `environment:` của compose) lẫn client (~3000); visibility check thống nhất; dài hạn thay polling bằng realtime/SSE. Ghi chú: vòng 3 đã cân nhắc nâng mục này từ Low.

#### L3 — Backend: class quá lớn + duplication giữa 3 import service + field injection
- **Vấn đề:** Điểm trừ bảo trì.
- **Vị trí (đo lại v2.4):** `UseCaseInferenceEngine.java` 1398 dòng, `MethodVisitor.java` 860, `Neo4jGraphRepository.java` 604, `AdminService.java` 591; 3 import service (`measureExtractedSize`, `deleteRecursively`, `analyzeInBackground`, `cleanup` gần như copy); `ProjectServiceImpl` field injection `@Autowired(required=false)` cho 3 collaborator nullable.
- **Giải pháp:** extract `ImportWorkspaceSupport`; constructor injection.

#### L4 — CSRF bằng custom header `X-VibeGraph-Client` thay vì token chuẩn
- **Vấn đề:** Phòng tuyến "độc quyền custom".
- **Bằng chứng:** hoạt động được nhờ SameSite=Lax + CORS allow-list + JSON POST bắt buộc preflight.
- **Giải pháp:** giữ nguyên, ghi chú; cân nhắc Spring CSRF token nếu sau này nới SameSite; xác nhận backend không cho phép text/plain fallback.

#### L5 — Unconfined browse/import bật mặc định ở profile dev
- **Vấn đề:** Copy nhầm `.env`/profile lên server là thành lỗ đọc filesystem.
- **Vị trí:** `application-dev.yaml` dòng 14–15 (`allow-unconfined-*: true`); `.env` dev chạy với `VIBEGRAPH_PROJECTS_ALLOWED_ROOT` trống.
- **Bằng chứng:** import/browse bất kỳ thư mục nào trên host khi chạy dev; code `LocalProjectPathValidator`/browse fail-closed khi không phải dev — tốt.
- **Giải pháp:** thêm test hard-fail nếu `allow-unconfined-*=true` ở prod; cảnh báo trong `.env.example`.

#### L6 — Cấu hình nhỏ
- **Vấn đề:** Một số tinh chỉnh vận hành còn thiếu.
- **Bằng chứng:** Hikari pool size Postgres không tường minh (default 10 trong khi mọi request authenticated tốn 1 query `findAuthSnapshot`; API-key flow thêm 2–3 query); DB lookup IP-block đã tách thành M21. `JwtAuthFilter.ACTIVE_USERS` là static map nhưng `OnlineUserHistoryService` kích hoạt cleanup mỗi 30 giây, nên chỉ giữ hygiene Low; thư mục `.vibegraph/uploads` tồn đọng 457 file; `seed_dev.sql` dùng placeholder BCrypt — an toàn, giữ nguyên.
- **Giải pháp:** set `maximum-pool-size` tường minh; thay static map bằng Caffeine `expireAfterWrite`; thêm job dọn workspace FAILED định kỳ.

#### L7 — Code chết: chuỗi `searchNodes` + fulltext index `node_search` *(gộp C2 + H1 cũ, hạ từ Critical/High xuống Low sau xác minh reachability)*
- **Vấn đề:** Chuỗi `searchNodes` tồn tại đầy đủ nhưng không có bất kỳ caller nào trong sản phẩm; fulltext index đi kèm phủ thiếu label.
- **Bằng chứng reachability:** chuỗi `searchNodes` tồn tại (`GraphRepository.java:58` → `Neo4jGraphRepository.java:342–359` → `CachingGraphRepository.java:145` → `GraphService.java:26` → `GraphServiceImpl.java:52`) nhưng KHÔNG có controller nào gọi (`GraphController` chỉ dùng `getFullGraph`/`getImpactAnalysis`/`getNodeDetail`), KHÔNG có MCP tool nào gọi (`McpServerConfig` đăng ký 18 tool, `SearchSourceTool` không liên quan), UI chỉ filter client-side (`SearchBar.vue:21–32` dùng `props.nodes.filter` + `includes`; `lib/api.ts` không có endpoint graph-search).
- **Bằng chứng label mismatch (có thật):** `GraphVocabulary.java:21–25` khai báo `Record`/`DBModel`/`Constructor`/`APIEndpoint`; `V1__init_schema.cypher:108–110` chỉ phủ `Class|Interface|Enum|Method|Field|Annotation`; không có migration V3.
- **Kết luận:** `searchNodes` + fulltext index là code chết — khi nối vào sản phẩm phải kèm escape Lucene + migration V3 recreate index phủ đủ label; nếu không dùng thì xóa (kể cả test `GraphServiceTest.searchNodesDelegates`).
- **Lưu ý đính chính:** phát biểu "ParseException → HTTP 500 mỗi lần gõ search" của H1 cũ được gỡ bỏ hoàn toàn — nó mô tả triệu chứng không thể xảy ra vì không có endpoint nào gọi `searchNodes`.

#### L8 — Dead DTO/JPA mapping và test scaffold cũ
- **Vấn đề:** Sáu DTO chỉ còn declaration/guide reference (98 dòng tổng), một JPA entity không có Java consumer, và một file test scaffold 8/8 disabled làm nhiễu codebase/test metrics.
- **Vị trí:** `PaginationRequest.java` (17 dòng), `AnalyzeRequest.java` (15), `ClassContextRequest.java` (15), `LayerPatternRequest.java` (15), `ParseFileRequest.java` (16), `ParseResultResponse.java` (20); `UserNotification.java`; `TarballImportServiceTest.java:26–95`.
- **Bằng chứng:** sáu DTO không có type reference trong main/test ngoài chính file và một vài `MODULE-GUIDE.md`; `UserNotification` chỉ xuất hiện trong chính entity. Tuy nhiên bảng `user_notifications` **đang được dùng** qua `JdbcNotificationRepository.java:24–111`, controller/UI notifications và `SupabaseDisabledModeIT.java:46,86,161,227,267`, nên chỉ mapping JPA là dead — không được drop table/migration. `TarballImportServiceTest` có 8 `@Test` và 8 `@Disabled`; `TarballImportServiceImplTest` có 5 test hoạt động.
- **Giải pháp:** xóa sáu DTO sau impact check và cập nhật guide; xóa riêng entity JPA nếu JDBC là kiến trúc chính; xóa/viết lại scaffold Tarball. Không xóa bảng notification.
- **Snippet đề xuất:**
  ```java
  // Keep NotificationRepository/JDBC path; remove only the unused ORM mapping.
  // Delete UserNotification.java after confirming no reflection/serialization contract.
  ```

#### L9 — Frontend dead code và lifecycle/bundle hygiene
- **Vấn đề:** Có 1.319 dòng component/composable không reachable từ live app; Axios client thứ hai chỉ phục vụ `authApi.me()`; hai locale eager (~140 KB raw), một config refresh không dùng, và LandingView giữ timer/listener tới khi callback/event xảy ra.
- **Vị trí:** `HeaderBar.vue` 93, `MainLayout.vue` 23, `SidePanel.vue` 19, `StatusBar.vue` 23, `GraphControls.vue` 20, `CodeInspector.vue` 21, `AddProjectLocal.vue` 459, `DirectoryBrowserModal.vue` 447, `useLocalImport.ts` 214; `lib/http.ts:11–58`, `lib/api.ts:7,621–624`; `language/index.ts:2–28`; `runtimeConfig.ts:100–104`; `LandingView.vue:349–358,490–503`.
- **Bằng chứng:** sáu component nhỏ chỉ còn self/comment refs; `DirectoryBrowserModal`/`useLocalImport` chỉ được dùng bởi dead `AddProjectLocal`; `lib/http.ts` chỉ được import ở `lib/api.ts` cho một endpoint; locale JSON 65.415 B + 75.314 B được import eager; `PROJECTS_AUTO_REFRESH_INTERVAL_MS` không có consumer; bốn listener dùng `{once:true}` nên leak bounded, nhưng callback vẫn giữ component scope tới event/page lifetime, còn typing timeout không có handle để clear.
- **Giải pháp:** xóa dead island theo một patch có test/import check; chuyển `me()` sang fetch wrapper rồi gỡ Axios nếu không còn consumer; lazy-load locale phụ; xóa/triển khai config; giữ timer handles và remove đúng bốn listener khi unmount.
- **Snippet đề xuất:**
  ```ts
  let typingTimer: ReturnType<typeof setTimeout> | null = null
  onBeforeUnmount(() => {
    if (typingTimer) clearTimeout(typingTimer)
    for (const event of ['scroll', 'mousemove', 'mousedown', 'keydown']) {
      window.removeEventListener(event, stopAutoTour)
    }
  })
  ```

#### L10 — Reproducibility và repository hygiene cần quyết định ownership
- **Vấn đề:** Docker base images dùng floating tags và GitHub Actions chỉ pin major; `task/` và `task-final/` cùng track 8 tên file nhưng không phải bản sao hoàn toàn, nên không thể xóa một thư mục máy móc.
- **Vị trí:** `Dockerfile:3,10`; `vibegraph-web/Dockerfile:3,16`; `docker-compose.yml:3,21`; `.github/workflows/backend.yml`, `.github/workflows/frontend.yml`; `task/`, `task-final/`.
- **Bằng chứng:** image/action pinning là supply-chain/reproducibility hardening, chưa phải exploit. So hash: chỉ `export_to_csv.py` và `ed_calculation.csv` byte-identical trong lần đo hiện tại; các tài liệu/backlog còn lại khác nhau (Qwen/Claude từng nói 3 file giống là số liệu stale). "Thiếu CD" là capability gap, không tự động là bug.
- **Giải pháp:** pin version/digest theo lịch nâng cấp có review; xác định `task-final` là output hay nhánh tài liệu độc lập, rồi merge từng file bằng diff. Không `git rm` cả thư mục dựa trên tên giống nhau.
- **Snippet đề xuất:**
  ```yaml
  # Example only: Renovate/Dependabot should update the exact digest deliberately.
  uses: actions/checkout@<reviewed-commit-sha>
  ```

---

## 5. Đánh giá theo 4 khía cạnh

| Khía cạnh | Đánh giá |
|-----------|----------|
| **Hiệu năng** | Indexing tốt từ V2 `:Symbol`; Neo4j bound memory. Điểm nghẽn còn lại: `getFullGraph` (M1), batch UNWIND không chunk (H1), parse tuần tự (H3), analyze đồng bộ (H2), `readRange` materialize file (H10), BCrypt trước rate-limit (H9), N+1 admin (M18), IP-block query hot path (M21), polling GitHub không hủy (M19), scheduler/purge (M23–M24), full-graph diff watcher (M27), entry chunk frontend 556KB (M11). |
| **Bảo mật** | Nền tảng tốt (fail-closed `/ws` + `/mcp`, JWT fail-fast, admin bootstrap opt-in, API key chỉ lưu hash, path traversal xử lý). Lỗ hở cần vá: secret exposure trong repo state (C2), private-key block redaction (H11), rate-limit trước BCrypt (H9), cookie Secure (H7), mount `.env` (H6), container root (H5), DB port expose (M9), XFF ở deployment hiện đang bật trust proxy (M20), actuator USER access (M28), multipart host cap (M29). |
| **Khả năng mở rộng** | Control-plane/data-plane tách bạch hợp lý; Supabase offload telemetry có capacity policy + cutover gate. Rào chắn: single-instance (M5), chưa backup (H4), project ID 32-bit (M17), cache LLM không giới hạn (M22), scheduler/purge tuần tự (M23–M24), Neo4j Community không multi-database/RBAC (tenant isolation dựa hoàn toàn projectId ở app — cần giữ kỷ luật review). |
| **Clean code** | Module hóa và coverage gate tốt nhưng số file test không đồng nghĩa coverage thật. Điểm trừ: class/SFC lớn (L3, M16), config chết (M3), code chết searchNodes (L7), DTO/JPA/test scaffold chết (L8), frontend dead island/dual HTTP client (L9), drift config (M6), i18n phủ chưa đều (M12), exception typing quá rộng (M25). |

---

## 6. Bảng tổng hợp theo ưu tiên (54 finding)

| # | Mã | Vấn đề (tóm tắt) | Khía cạnh | Mức độ | Hành động đề xuất |
|---|----|------------------|-----------|--------|-------------------|
| 1 | C2 | Secret thật trong `.env`/backup/Git object | Bảo mật / Quản trị repo | 🔴 Critical | Rotate/revoke trước; sau đó dọn đúng stash/object; chuyển secret manager |
| 2 | C1 *(H3 cũ)* | Registry project in-memory, restart hiển thị sai "đã phân tích xong 100%" vĩnh viễn | Độ tin cậy | 🔴 Critical | Persist trạng thái sang Postgres; sweep ANALYZING quá hạn |
| 3 | H1 *(C3 cũ)* | Upsert Neo4j không nguyên tử, async FAILED để lại graph dở | Hiệu năng / Độ tin cậy | 🟠 High | Chấp nhận non-atomic + delete-on-failure cho project mới tạo + sweep định kỳ; chunk UNWIND; KHÔNG áp cho re-analyze |
| 4 | H2 | Endpoint analyze chạy đồng bộ, không có guard per-project | Hiệu năng | 🟠 High | Chuyển async 202 + progress WS; chặn re-analyze song song per-project |
| 5 | H3 *(H4 cũ)* | Parse CPG tuần tự — bottleneck import | Hiệu năng | 🟠 High | Parse song song bounded pool (xác minh thread-safety Registry trước) |
| 6 | H4 *(H5 cũ)* | Không có chiến lược backup/restore 3 kho dữ liệu | Độ tin cậy | 🟠 High | Chương backup trong DEVOPS-GUIDE; diễn tập restore |
| 7 | H5 *(H6 cũ)* | Container root, skipTests, không cấu hình heap | Bảo mật / Hạ tầng | 🟠 High | USER non-root; MaxRAMPercentage; mem_limit |
| 8 | H6 *(H7 cũ)* | Toàn bộ `.env` mount vào container backend | Bảo mật | 🟠 High | Bỏ mount; truyền env tối thiểu qua environment: |
| 9 | H7 *(H8 cũ)* | Cookie Secure mặc định false ở Docker | Bảo mật | 🟠 High | Đổi default compose sang true; TLS trước khi expose |
| 10 | H8 | npm audit: 8 lỗ hổng dependency | Supply chain | 🟠 High | Nâng dependency/lockfile theo từng nhóm, audit lại |
| 11 | H9 | Rate-limit chạy sau BCrypt trên API-key path | Bảo mật / Hiệu năng | 🟠 High | Limiter sớm theo IP trước BCrypt + giữ limiter identity-aware sau auth |
| 12 | H10 | `readRange` nạp toàn file trước khi cap | Độ tin cậy / Hiệu năng | 🟠 High | Files.size guard + stream bounded range |
| 13 | H11 | Redaction private key chỉ che header | Bảo mật | 🟠 High | Từ chối file khóa hoặc redact stateful BEGIN→END |
| 14 | M1 | getFullGraph nhân bản theo cạnh, cap mặc định 0 | Hiệu năng | 🟡 Medium | Tách query nodes/edges; cap dương; đưa biến vào compose |
| 15 | M2 | Project listing load toàn tenant, filter Java | Hiệu năng | 🟡 Medium | Push ownership filter xuống query; cache stats |
| 16 | M3 | Config chết (use-cache, FA2_ITERATIONS…) | Clean code | 🟡 Medium | Hiện thực hóa hoặc gỡ config |
| 17 | M4 | BCrypt candidate prefix + DB write mỗi request MCP | Hiệu năng | 🟡 Medium | Throttle lastUsedAt; cân nhắc HMAC/SHA-256 cho API key |
| 18 | M5 | Stateful per-instance cản trở scale ngang | Khả năng mở rộng | 🟡 Medium | Ghi "single-replica only" trong DEPLOYMENT.md |
| 19 | M6 | Drift `.env` ↔ `.env.example` ↔ yaml | Clean code / Vận hành | 🟡 Medium | Sinh lại .env; script/test kiểm tra parity |
| 20 | M7 | Hai nguồn Postgres schema + Flyway ignore-missing | Độ tin cậy | 🟡 Medium | Một nguồn sự thật db/migration; thu hẹp ignore-pattern |
| 21 | M8 | CI không validate Docker/compose | Hạ tầng | 🟡 Medium | Job `docker compose config -q` + `docker build` |
| 22 | M9 | Port DB expose host, APOC unrestricted | Bảo mật | 🟡 Medium | Bind 127.0.0.1 hoặc bỏ publish; whitelist APOC |
| 23 | M10 | .dockerignore thiếu `.env` và artifact | Bảo mật / Hạ tầng | 🟡 Medium | Bổ sung root + `vibegraph-web/.dockerignore` |
| 24 | M11 | 5 view gốc import tĩnh, entry chunk 556KB | Hiệu năng (FE) | 🟡 Medium | Dynamic import + manualChunks; đo lại bằng visualizer |
| 25 | M12 | 31/67 file không i18n, vùng graph hardcode EN | Clean code (FE) | 🟡 Medium | i18n hóa vùng graph trước |
| 26 | M13 | Layout graph không tất định + 8s CPU cố định | Hiệu năng (FE) | 🟡 Medium | Dừng theo iterations; seed bằng hash id |
| 27 | M14 | Ảnh tài nguyên ~1,4 MB chưa tối ưu | Hiệu năng (FE) | 🟡 Medium | WebP/AVIF + resize, giảm >1MB |
| 28 | M15 | God store admin.ts 782 dòng + api.ts 989 dòng | Clean code (FE) | 🟡 Medium | Tách store/api theo domain |
| 29 | M16 | SFC/composable quá lớn (GraphCanvas 1468 dòng…) | Clean code (FE) | 🟡 Medium | Tách composable/SFC con theo trách nhiệm |
| 30 | M17 | Project ID 32-bit, không xử lý collision | Độ tin cậy | 🟡 Medium | Full UUID/ULID + unique persistent key |
| 31 | M18 | Admin user listing N+1 (2 query/user) | Hiệu năng / DB | 🟡 Medium | Batch settings + aggregate storage theo page |
| 32 | M19 | GitHub polling không hủy khi unmount/reset | Hiệu năng (FE) / Độ tin cậy | 🟡 Medium | AbortController + onScopeDispose |
| 33 | M20 | XFF chọn IP trái nhất khi trust proxy | Bảo mật | 🟡 Medium *(có điều kiện)* | Right-most untrusted + thu hẹp trusted CIDR |
| 34 | M21 | IP-block query DB trên mọi request | Hiệu năng / DB | 🟡 Medium | Cache TTL/snapshot + invalidate admin mutation |
| 35 | M22 | LLM response cache không giới hạn | Hiệu năng / Độ tin cậy | 🟡 Medium | Caffeine maximumSize/weight + expiry |
| 36 | M23 | Scheduler mặc định một thread, maintenance có thể chặn telemetry flush | Hiệu năng / Độ tin cậy | 🟡 Medium | Pool scheduler riêng + timeout/metrics; không coi telemetry là durable |
| 37 | M24 | Trash purge không phân trang và giữ transaction dài | DB / Hiệu năng | 🟡 Medium | Keyset/page + batch transaction per project/batch |
| 38 | M25 | Global IllegalStateException handler trả 409/raw message | Correctness / Bảo mật | 🟡 Medium | Typed domain precondition exception; lỗi hạ tầng thành 500 generic + log |
| 39 | M26 | 3 test source rỗng, 2 file khớp Failsafe IT include | Testing | 🟡 Medium | Viết IT thật hoặc xóa placeholder; CI check file rỗng |
| 40 | M27 | File watcher snapshot/diff full graph hai lần mỗi save | Hiệu năng | 🟡 Medium | API file-slice/replace và diff cục bộ |
| 41 | M28 | Actuator metrics/prometheus USER thường đọc được | Bảo mật | 🟡 Medium | Admin-only hoặc management port/network riêng |
| 42 | M29 | Multipart ceiling 2 GiB/2.05 GiB gây host resource pressure | Bảo mật / Hạ tầng | 🟡 Medium | Proxy/body-size cap + temp-volume quota + cap deploy thấp hơn |
| 43 | M30 | UsersTableView thiếu error boundary ở load/filter/pagination | Correctness (FE) | 🟡 Medium | Wrapper loadUsers với catch/finally và UI error |
| 44 | M31 | dev-up.ps1 không start Postgres dù backend bắt buộc | DevEx / Hạ tầng | 🟡 Medium | Start/wait postgres và Neo4j trước backend |
| 45 | L1 | v-html 3 điểm thiếu sanitize phòng thủ | Bảo mật (FE) | 🟢 Low | DOMPurify trước v-html + test |
| 46 | L2 | Polling khi tab ẩn + không cap nào hiệu lực mặc định | Hiệu năng (FE) / Vận hành | 🟢 Low *(đã cân nhắc nâng)* | Bật cap dương backend/client; visibility check |
| 47 | L3 | Class lớn backend + duplication 3 import service + field injection | Clean code | 🟢 Low | Extract ImportWorkspaceSupport; constructor injection |
| 48 | L4 | CSRF bằng custom header thay token chuẩn | Bảo mật | 🟢 Low | Giữ nguyên, ghi chú; cân nhắc Spring CSRF token |
| 49 | L5 | Unconfined browse/import bật mặc định dev | Bảo mật | 🟢 Low | Test hard-fail nếu bật ở prod; cảnh báo .env.example |
| 50 | L6 | Hikari pool không tường minh, static map, 457 file tồn đọng | Vận hành | 🟢 Low | Set pool size; Caffeine expireAfterWrite; job dọn workspace |
| 51 | L7 *(C2+H1 cũ)* | Code chết: `searchNodes` + fulltext index thiếu label | Clean code / Chức năng | 🟢 Low | Nối endpoint (kèm escape Lucene + migration V3) hoặc xóa cả test |
| 52 | L8 | 6 DTO chết + JPA mapping UserNotification + disabled Tarball scaffold | Clean code / Testing | 🟢 Low | Xóa mapping/DTO/scaffold sau impact check; giữ JDBC table |
| 53 | L9 | 1.319 dòng dead frontend + Axios/locale/config/timer hygiene | Clean code / Hiệu năng FE | 🟢 Low | Xóa dead island; hợp nhất HTTP; lazy locale; cleanup lifecycle |
| 54 | L10 | Floating image/action tags và task/task-final ownership drift | DevOps / Hygiene | 🟢 Low | Pin digest; quyết định ownership rồi merge bằng diff |

**Lưu ý:** mã C/H/M/L là khóa ổn định để đối chiếu giữa các vòng. Tổng số **54** gồm C1–C2, H1–H11, M1–M31 và L1–L10.

---

## 7. Lộ trình khuyến nghị *(thay thế theo reviewer, đã kiểm chứng)*

1. **C2 — rotate/revoke secrets** — P0; chỉ dọn stash/object/history sau khi credential cũ đã vô hiệu và có bản sao an toàn.
2. **C1** (H3 cũ): persist trạng thái project + sweep "ANALYZING quá hạn ⇒ FAILED".
3. **H7/H6/H5:** bật cookie Secure, bỏ mount `.env`, chạy container non-root và đặt heap/resource limit.
4. **H9:** thêm limiter pre-auth theo IP cho API-key path, giữ limiter hiện tại sau auth để bảo toàn quota theo identity.
5. **H10/H11:** stream source bounded trước khi materialize; từ chối hoặc redact toàn khối private key; thêm regression test REST + MCP.
6. **H2/H1:** analyze async + guard per-project; cleanup tất định cho import project mới khi FAILED.
7. **H8:** nâng dependency theo từng nhóm/changelog, audit/test/build lại.
8. **M1/M9/M10:** bật graph cap dương, bind port DB cục bộ, bổ sung `.dockerignore`.
9. **Sprint kế tiếp:** M17–M22 — full UUID, batch admin queries, polling cancellation, XFF right-most-untrusted, IP-block cache, bounded LLM cache.
10. **Vòng kế tiếp:** M23–M31 — scheduler/purge batching, typed exception mapping, real integration tests, file-slice broadcaster, actuator authorization, multipart host cap, admin error UX và dev-up Postgres.
11. **H3/H4 + hygiene:** parse song song sau test thread-safety, backup/restore rehearsal; sau đó xử lý Low L8–L10 và quyết định nối hoặc xóa code chết L7.

Mỗi patch phải chạy `gitnexus_impact` trên symbol tương ứng trước khi sửa; các thay đổi auth filter, source reader và project identity cần integration/regression test riêng.

---

## 8. Rủi ro, giả định và lưu ý quy trình

- Đánh giá bundle frontend dựa trên `dist/` hiện có (minified chưa gzip) — nên đo lại bằng rollup-plugin-visualizer sau khi áp dụng M11.
- Song song hóa parser (H3) cần xác minh thread-safety `ProjectSymbolRegistry` bằng test trước khi triển khai.
- Runtime evidence Qwen được dùng như dữ liệu bổ sung, không thay thế benchmark có kiểm soát; T1/T2/T4/T6/T7 xác nhận triệu chứng, T3 bị chặn, T5/T8 không tái hiện.
- Phiên này không expose GitNexus MCP; vì chỉ sửa tài liệu, không có symbol code nào bị chỉnh và không cần impact analysis. Mọi remediation source trong tương lai vẫn phải chạy `gitnexus_impact` trước.
- Theo AGENTS.md: trước khi sửa các symbol nhiều caller (`parseProject`, `upsertNodes`/`upsertEdges`, `getFullGraph`, `searchNodes`, `analyze`) cần chạy `gitnexus_impact` đánh giá blast radius.
- Báo cáo này là vòng 2, bản v2.4, kế thừa và kiểm chứng lại audit vòng 1 tại `update/AUDIT-REPORT.md`, rồi đối chiếu các báo cáo độc lập trong `update/docs/`; mọi finding chính thức đều kèm vị trí file/dòng và reachability để bên thứ ba tự xác minh.

---

## 9. Phản biện và xác minh vòng 3

Báo cáo v2 bị reviewer phản biện theo 6 nhóm (P1–P6). Một researcher độc lập đã kiểm chứng từng luận điểm **TRỰC TIẾP TRÊN CODE**. Kết quả: hầu hết phản biện là ĐÚNG. Bảng dưới ghi lịch sử thay đổi ở bản v2.3; bản v2.4 tiếp tục sửa riêng P3 sau khi kiểm tra được parent untracked của stash:

| Nhóm | Nội dung phản biện (vấn đề bị chất vấn) | Verdict | Hệ quả hiện hành |
|------|------------------------------------------|---------|------------------------|
| P1 | C2 + H1: search có thực sự đang chạy/ảnh hưởng người dùng? | **ĐÚNG** | Chuỗi `searchNodes` không có caller (controller/MCP/UI) → gộp thành code chết L7; gỡ phát biểu "ParseException → HTTP 500" |
| P2 | C3: "3 import service không gọi deleteProject"? | **ĐÚNG MỘT PHẦN** | Đường sync archive/tarball ĐÃ gọi cleanup → `deleteProject`; hạ C3 xuống H1, viết lại giải pháp (không áp delete cho re-analyze; trade-off chunk tx) |
| P3 | C1 cũ: gitignore và bản chất secrets? | **ĐÚNG một phần ở v2.3, sửa lại v2.4** | Gitignore đã hoàn thành nhưng secret vẫn ở `.env`, backup và Git object/stash; đưa lại thành Critical C2 |
| P4 | L2: "backend đã cap 2500 node"? | **ĐÚNG** | Không cap nào hiệu lực ở triển khai mặc định (yaml=0, prod không override, compose/`.env` không đặt biến) → lật ngược đánh giá L2 |
| P5 | H2: cơ chế guard của endpoint analyze? | **ĐÚNG (cơ chế)** | Endpoint analyze không có `ConcurrentImportGuard`; guard là bộ đếm per-user (`ConcurrentImportGuard.java:15–34`) → yêu cầu chặn per-project |
| P6 | H3: cả hai luận điểm (hard-code ANALYZED/100 + không có sweep)? | **ĐÚNG cả hai** | Nâng H3 cũ lên Critical (C1 mới) — sai lệch hiển thị trực tiếp cho người dùng sau restart |

**Bài học phương pháp:** audit vòng 2 không phân biệt code tồn tại với code đang chạy (thiếu bước truy caller đến HTTP/UI/MCP) — mọi phát hiện severity từ Medium trở lên cần xác minh reachability trước khi xếp hạng. Với state Git/secrets, `git ls-files` và `git stash show` không đủ: phải kiểm cả parents/object tree mà không in nội dung nhạy cảm.

---

## 10. Kết luận đối chiếu đa báo cáo *(bản v2.4)*

### 10.1. Tóm tắt tài liệu nguồn

`update/security-perf-audit.md` và các báo cáo trong `update/docs/` là các luồng audit độc lập được dùng để phản biện chéo. Các finding chỉ được nhập vào bảng chính thức khi đã đọc source hiện hành, xác minh caller/reachability và loại bỏ trùng lặp. Tổng hợp cuối cùng là 54 finding (C1–C2, H1–H11, M1–M31, L1–L10). Qwen bản mới có 76 mục trước khi khử trùng lặp; Claude audit/cross-audit bổ sung các claim scheduler, purge, exception handler, test hygiene và các phản biện phủ định.

### 10.2. Bảng verdict và mapping cuối cùng

Ghi chú phương pháp: mọi điểm mới dưới đây đã được kiểm chứng bằng đọc/grep source ngày 12/08/2026 và đã có mã chính thức ở §4/§6. “Đã kiểm chứng” không đồng nghĩa “đã khai thác runtime”; các mục chưa có runtime reproduction được ghi rõ là bằng chứng cơ chế tĩnh.

| # | Phát hiện nguồn | Vị trí nêu trong nguồn | Verdict v2.4 | Mã chính thức | Trạng thái kiểm chứng | Kết luận |
|---|-----------------|------------------------|-----------|-------------------|------------------------|--------------------|
| F1 | Rate-limit chạy SAU xác thực bcrypt → DoS CPU | `SecurityConfig.securityFilterChain`, `ApiKeyAuthFilter.findMatch` | **Đã nhập, High** | **H9** | ✅ Cơ chế reachable qua `/mcp/**` và patch; runtime fake-key timing không tái hiện với prefix ngẫu nhiên | Tách pre-auth IP limiter khỏi identity-aware limiter; không đổi thứ tự toàn chain một cách mù quáng |
| F2 | `readRange` nạp cả file vào RAM trước khi chốt size → OOM | `SourceFileServiceImpl.readRange` | **Đã nhập, High** | **H10** | ✅ Source + MCP/REST caller; runtime 200 MiB xác nhận memory spike | Stream bounded read và size guard trước materialization |
| F3 | Chỉ redact dòng header private key, thân khóa lộ nguyên văn | `SourceFileServiceImpl.redact` | **Đã nhập, High** | **H11** | ✅ Source xác nhận per-line redaction; chưa cần giả định mọi project chứa key | Stateful block redaction hoặc deny file chứa key |
| F4 | XFF lấy token trái nhất → giả mạo IP, né rate-limit/IP-block | `ClientAddressResolver.resolve` | **Đã nhập, Medium có điều kiện** | **M20** | ✅ Chỉ xảy ra khi `trustProxy=true` và remote thuộc trusted proxy; `.env` hiện đang bật | Chọn right-most untrusted hop; thu hẹp trusted CIDR; YAML default false giữ nguyên |
| F5 | `ACTIVE_USERS` static map dọn lười → rò rỉ bộ nhớ | `JwtAuthFilter` | **Đã giữ ở Low hygiene** | **L6** | ✅ Cơ chế map tồn tại, nhưng `OnlineUserHistoryService` gọi cleanup mỗi 30 giây | Không nâng severity; dùng Caffeine vẫn là cải thiện tùy chọn |
| F6 | Ghi DB `lastUsedAt` mỗi request dùng API key | `ApiKeyAuthFilter.authenticate` | **Đã giữ ở Medium** | **M4** | ✅ Trùng khớp dòng 149–150; write amplification độc lập với H9 | Throttle timestamp hoặc tách telemetry/touch update |
| F7 | Telemetry “đồng bộ” | `RequestEventService` | **Bác bỏ / rút lại** | — | ✅ `record()` normalize + offer bounded queue; batch/drain hiện hành | Không đưa vào finding |
| F8 | `IpBlockService.findActive` query DB mỗi request | `IpBlockFilter` / `IpBlockService` | **Đã nhập, Medium** | **M21** | ✅ Filter gọi sớm cho mọi request, không cache | TTL cache/snapshot + invalidation |
| F9 | Zip/tar-bomb qua entry non-`.java` | `ArchiveExtractor.materializeIfJava` | **Đã hạ thành hardening** | — | ✅ Entry non-`.java` bỏ qua trước đọc; archive compressed-size cap vẫn tồn tại | Giữ cảnh báo defense-in-depth; không gọi là Critical/archive-bomb blocker |
| F10 | `Redirect.NORMAL` + owner/repo cho phép `.`/`..` | GitHub import client/parser | **Low hardening, chưa nhập** | — | ✅ Host cố định `api.github.com`; segment regex cho phép dot segments | Reject `.`/`..` để giảm ambiguity; không ảnh hưởng priority backlog |
| F11 | Queue telemetry đầy có thể drop security event | `RequestEventService.offer` | **Low observability, chưa nhập** | — | ✅ Best-effort shedding có chủ đích, metric `securityDropped` tăng | Cân nhắc kênh security event bền vững nếu yêu cầu forensic cao |
| F12 | Secret trong `.env` + backup + parent untracked của stash | `.env`, backup root, `stash@{0}` parent 3 | **Đã nhập, Critical** | **C2** | ✅ Chỉ kiểm tên biến/assignment, stash parents và tree path; không in giá trị | Rotate/revoke trước, rồi dọn đúng object/file; gitignore không đủ |
| F13 | 7 scheduled job dùng scheduler mặc định một thread | 7 `@Scheduled`, không scheduler config/bean | **Đã nhập, Medium** | **M23** | ✅ Cơ chế tĩnh; chưa có benchmark thời gian purge | Tách scheduler/telemetry; không tuyên bố "mù 200 giây" khi chưa đo |
| F14 | Purge trash không phân trang | `ProjectTrashService`, `ProjectOwnershipRepository` | **Đã nhập, Medium** | **M24** | ✅ List unbounded + transaction/sweep cross-store | Batch/keyset và transaction ngắn |
| F15 | Global `IllegalStateException` → 409/raw message | `GlobalExceptionHandler` + throw sites | **Đã nhập, Medium** | **M25** | ✅ Nhiều lỗi hạ tầng/data invariant reachable | Custom domain exception; generic 500/log cho lỗi hạ tầng |
| F16 | 3 tracked empty tests | 2 `*IT.java` + `FinalIntegrationTest.java` | **Đã nhập, Medium** | **M26** | ✅ 0 byte; Failsafe include khớp 2 file | Viết IT thật hoặc xóa placeholder; không gọi mọi helper thiếu @Test là rác |
| F17 | File watcher đọc full graph trước/sau mỗi save | `FileChangeBroadcaster.java:99,110` | **Đã nhập, Medium** | **M27** | ✅ Reachable hot path | Repository file-slice + diff cục bộ |
| F18 | Actuator metrics/prometheus cho role USER | prod management exposure + `anyRequest().authenticated()` | **Đã nhập, Medium** | **M28** | ✅ Anonymous bị chặn, USER thường được phép | Admin-only/management network riêng |
| F19 | Multipart 2 GiB | Spring multipart config + archive quota flow | **Đã nhập, Medium** | **M29** | ✅ Host cap cao; quota được kiểm trước `Files.copy` nhưng sau multipart parse | Proxy/body cap + temp quota; không gọi là 2 GiB RAM buffer chắc chắn |
| F20 | UsersTableView unhandled rejection | load/filter/refresh/pagination | **Đã nhập, Medium** | **M30** | ✅ Runtime T4 + source; action mutation đã có catch | Gom load wrapper và UI error |
| F21 | dev-up bỏ Postgres | `scripts/dev-up.ps1`, datasource/Flyway config | **Đã nhập, Medium** | **M31** | ✅ Script chỉ start Neo4j | Start/wait cả Postgres |
| F22 | DTO/frontend/JPA/test dead code và hygiene | các file tại L8–L10 | **Đã nhập, Low theo nhóm** | **L8–L10** | ✅ Reference/hash/count đo lại | Xóa/gộp có impact check; không drop notification table hoặc task folder máy móc |

**Đối chiếu mục "Đã kiểm chứng an toàn" của nguồn:** các kết luận về JWT HS512/alg-confusion, ownership/IDOR, path traversal, zip-slip/symlink, CORS và CSRF phù hợp với code hiện hành. Các claim sau **không được nhập như vulnerability đã xác nhận**:

- **Supabase schema injection:** false positive; `SupabaseDatabaseConfig.java:149–157` validate schema bằng `SCHEMA_PATTERN` trước khi DDL được ghép ở dòng 181.
- **Quota TOCTOU:** chưa phải vulnerability; `ProjectUsageService` khóa `UserAccountSettings` và `ProjectUsage` bằng `PESSIMISTIC_WRITE`, rồi tính lại aggregate trong transaction (`ProjectUsageService.java:45–57, 117–147`).
- **`ACTIVE_USERS` leak vô hạn:** severity sai; cleanup chạy qua `OnlineUserHistoryService.java:21–24` mỗi 30 giây. Giữ L6/hygiene.
- **Lucene search gây 500 runtime:** dead path; không có controller/MCP/UI caller. Giữ L7.
- **Telemetry synchronous:** finding đã rút lại; queue/batch là thiết kế hiện hành.
- **`UserNotification` đồng nghĩa bảng notification chết:** sai; chỉ entity JPA không dùng. `JdbcNotificationRepository`, account notification API/UI và integration test đang SELECT/INSERT/DELETE bảng thật. Giữ L8, không drop table/migration.
- **Multipart "buộc buffer 2 GiB RAM":** diễn giải quá mạnh; servlet container thường spool theo cấu hình. Finding đúng là host-level ceiling quá cao (M29).
- **`task/` và `task-final/` hoàn toàn trùng:** sai; cùng 8 tên file nhưng phần lớn nội dung khác. Cần ownership decision/diff, không xóa nguyên thư mục.
- **`MethodVisitor` đọc sai Spring YAML:** sai bối cảnh; key không tồn tại trong YAML. Đây là JVM-only toggle chưa tài liệu hóa, mức hygiene thấp, đã gộp vào L10 thay vì Medium.
- **`instantOrNull()` nuốt parse exception là data corruption Medium:** chưa đủ bằng chứng; đây có thể là compatibility fallback. Thiếu log/metric là Low observability, chưa tách thành finding riêng.
- **DEBUG default là production leak:** profile mặc định có DEBUG nhưng `application-prod.yaml` override INFO; giữ ở hygiene/dev-noise, không xếp security Medium.
- **Seed BCrypt placeholder là bug:** `database/seed_dev.sql` có comment rõ đây là template; không nhập.
- **Project ID 32-bit là High:** collision mechanism đúng, nhưng birthday bound khoảng 77 nghìn ID mới đạt xác suất ~50%; giữ M17 Medium vì tần suất có điều kiện dù hậu quả ghi đè cần sửa.
- **Frontend `.dockerignore` là High:** build context phình/cache kém là thật, nhưng chưa chứng minh secret vào final image; gộp M10 Medium.
- **Scheduler purge chắc chắn làm mù telemetry mỗi đêm:** chưa có duration/load benchmark; giữ M23 Medium và ghi rõ giả định.
- **CORS wildcard:** runtime Qwen T8 không tái hiện; origin lạ không nhận `Access-Control-Allow-Origin`. Kết luận allow-list hiện hành vẫn giữ.
- **Rate-limit/BCrypt không tồn tại vì T5 nhanh:** runtime dùng fake prefix không khớp record nên không chạy `passwordEncoder.matches`; chưa bác bỏ cơ chế H9, nhưng cũng không chứng minh exploit timing. Giữ H9 dựa trên reachable prefix-collision path và yêu cầu benchmark đúng mẫu.
- **`getNeighborhood`/`ImpactController` scaffold:** stale documentation; symbol/endpoint không còn trong source hiện tại, không xếp finding runtime.
- **UsersTableView thiếu try/catch High:** lỗi UX/rejection là thật và runtime xác nhận, nhưng block/unblock/create đã xử lý; giữ M30 Medium, không High.
- **Archive bomb:** entry `.java` được cap; entry khác là defense-in-depth CPU concern, không phải Critical.

**Kết luận:** Qwen/Claude mới làm báo cáo Codex thay đổi đáng kể nhưng không theo tỷ lệ 1:1 với 76 finding nguồn. Bản v2.4 nhập C2, M23–M31 và L8–L10; mở rộng M10, sửa H8/test wording/số dòng; giữ các claim scheduler/multipart/UsersTable ở Medium thay vì High; bác bỏ Supabase injection, notification-table-dead, CORS wildcard và các thao tác xóa/gộp quá tay. §6 là danh sách ưu tiên có hiệu lực.

---

*Hết báo cáo.*
