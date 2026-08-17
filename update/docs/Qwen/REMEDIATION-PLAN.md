# LỘ TRÌNH TRIỂN KHAI SỬA CHỮA & NÂNG CẤP — VibeGraph

- **Ngày tạo:** 12/08/2026 · **Nguồn:** `AUDIT-REPORT.md` (76 phát hiện) · Snippet chi tiết: `FIX-DETAILS-BACKEND.md`, `FIX-DETAILS-FRONTEND-DEVOPS.md`
- **Trạng thái bằng chứng:** RT = đã xác nhận runtime · SA = phân tích tĩnh · CC = xác nhận chéo (quy ước đầy đủ trong `README.md`)

## 1. Tổng quan

| Mức độ | Số lượng | Phân bổ theo đợt |
|---|---|---|
| 🔴 Nghiêm trọng | 2 | Đợt 0 (S1, S2) |
| 🟠 Cao | 17 | Đợt 1: H1–H5, H13–H17 (10) · Đợt 2: H6–H9 (4) · Đợt 3: H10–H12 (3) |
| 🟡 Trung bình | 31 | Đợt 1: S-M2, B-M12, B-M14 (3) · Đợt 2: B-M1–B-M11, B-M13 (12) · Đợt 3: F-M1–F-M7 (7) · Backlog: S-M1, S-M3–S-M5, D-M1–D-M5 (9) |
| ⚪ Thấp | 26 | Backlog (toàn bộ) |
| **Tổng** | **76** | |

**Nguyên tắc ưu tiên:**
1. Secret đã lộ đi trước mọi thứ (S1/S2) — rotate trước, dọn git object sau; mọi sửa khác đều vô nghĩa nếu secret còn lộ.
2. Bảo mật vận hành container (H1–H5) trước logic — vì chúng là bề mặt tấn công trực diện khi chạy Docker.
3. Phát hiện đã xác nhận runtime (RT) được ưu tiên nghiệm thu lại đúng test đó sau khi sửa; phát hiện từng chưa tái hiện đã được đo lại và xác nhận (H13 — V2.2/V2.3, 12/08/2026) nên xử lý dứt điểm ngay trong Đợt 1.
4. Sửa theo cụm nguyên nhân (vd H17 + B-M14 cùng chuỗi scheduler/purge; H6 + H7 cùng `ProjectServiceImpl`) để tránh sửa 2 lần cùng file.
5. Mỗi đợt kết thúc bằng chạy lại bộ kiểm thử tương ứng (`RUNTIME-VERIFICATION-PROMPT.md`) + `mvnw verify` / `npm run build` trước khi coi là xong.

---

## 2. Đợt 0 — Ngay lập tức (P0)

**Mục tiêu:** triệt tiêu secret đã lộ; không có ngoại lệ.

| Mã | Mức | File chính | Trạng thái bằng chứng | Tiêu chí nghiệm thu | Rủi ro khi sửa |
|---|---|---|---|---|---|
| S1 | 🔴 | `.env` (dòng 31–38, 41, 53–56, 76, 86) | SA | Toàn bộ secret production (Supabase password, JWT_SECRET, OAuth client secret, 8 Gemini key) đã rotate; `.env` trong repo chỉ còn giá trị dev hoặc tham chiếu secret store `${VAR:?...}` | Quên rotate 1 secret → vẫn lộ; phải có checklist từng secret và xác nhận phía nhà cung cấp (Supabase/Google/GitHub/Gemini) |
| S2 | 🔴 | git stash object `388632b` (`stash@{0}`) + bản working tree `.env.codex_backup-before-9e1dfed-20260725-140618` ở root | SA | `git show 388632b:...` không còn trả nội dung `.env`; `git stash list` không còn stash chứa secret; file backup trong working tree đã xóa | `gc --prune=now` không thu hồi object nếu còn ref khác trỏ tới — phải kiểm tra stash@{1..3} và reflog trước; KHÔNG hoàn tác được sau prune |

**Lệnh dọn git object (bản ĐÃ SỬA — theo AUDIT-REPORT S2):**
```powershell
git stash drop stash@{0}
# kiểm tra cả stash@{1..3} — repo hiện có 4 stash
git reflog expire --expire=now --all
git gc --prune=now
```
KHÔNG dùng `git filter-repo` — sai công cụ: `.env` chưa từng commit lên branch nào, object chỉ sống qua stash nên drop stash + expire reflog + gc là đủ. Xóa thêm bản sao `.env.codex_backup-*` trong working tree (lưu ý bản trong stash object tên `.env.codex-backup-before-905919f-...140030` KHÁC với bản working tree `.env.codex_backup-before-9e1dfed-...`).

**Kiểm tra sau sửa:** `git show 388632b:...` báo lỗi object không tồn tại; `git grep JWT_SECRET $(git rev-list --all)` rỗng; khởi động lại stack với secret mới, login thành công.
**Người thực hiện đề xuất:** người sở hữu repo (secret rotation cần quyền tài khoản Supabase/Google/GitHub/Gemini) + Tina (Bảo mật) giám sát checklist.

---

## 3. Đợt 1 — Tuần 1 (bảo mật + vận hành)

**Trạng thái: ✅ HOÀN THÀNH + NGHIỆM THU ĐẠT (12/08/2026)** — 12/13 mục đã sửa và xác nhận runtime; riêng H16 chưa có dữ liệu nghiệm thu trong đợt này (xem ghi chú dưới bảng). Số liệu đầy đủ: AUDIT-REPORT §10.

**Mục tiêu:** đóng bề mặt tấn công container/cấu hình + các điểm nóng bảo mật backend.

| Mã | Mức | File chính | Trạng thái bằng chứng | Tiêu chí nghiệm thu | Rủi ro khi sửa |
|---|---|---|---|---|---|
| H1 ✅ | 🟠 | `Dockerfile` dòng 10–14 | SA | Image backend chạy user non-root (`docker exec ... whoami` ≠ root) | Volume `./projects` phải chown/quyền ghi cho user app, nếu không import project fail |
| H2 ✅ | 🟠 | `docker-compose.yml` dòng 128 | SA | Bỏ mount `./.env:/app/.env:ro`, container vẫn khởi động bình thường (nhờ `spring.config.import` là `optional:`) | Nếu còn biến env nào đó chỉ có trong `.env` mà thiếu trong khối `environment:` (63–126) → app thiếu cấu hình; rà soát đủ biến trước khi bỏ |
| H3 ✅ | 🟠 | `docker-compose.yml` dòng 6–7, 24–30; `.env` dòng 16, 27 | SA | Port 5432/7474/7687 bind `127.0.0.1`; password DB mạnh; dòng `NEO4J_dbms_security_procedures_unrestricted: apoc.*` đã xóa | Đổi password DB phải đồng bộ mọi nơi tham chiếu; bỏ APOC unrestricted có thể làm hỏng query dùng apoc chưa khai báo — cần chạy lại các flow phân tích graph |
| H4 ✅ | 🟠 | `docker-compose.yml` dòng 102 + `.env` dòng 44 | **RT (T2)** | `AUTH_COOKIE_SECURE` mặc định `true`; chạy lại test T2: cookie có cờ Secure khi qua HTTPS | Secure=true làm cookie không gửi qua HTTP thuần → mất phiên nếu môi trường chưa có HTTPS; chuẩn bị reverse proxy TLS trước |
| H5 ✅ | 🟠 | tạo mới `vibegraph-web/.dockerignore` | SA | Build context frontend không còn `node_modules/`, `dist/` (soi `docker build` output); build vẫn thành công | Pattern quá tay loại file cần thiết (vd `.env*` đang được COPY) — kiểm tra từng dòng Dockerfile |
| S-M2 ✅ | 🟡 | `.env` dòng 104–105 + `ClientAddressResolver.java` dòng 28–35 | SA | Resolver lấy token phải nhất ngoài trusted range; thu hẹp trusted proxies; `VIBEGRAPH_TRUST_PROXY=false` nếu không đứng sau reverse proxy thật | Đổi cách phân giải IP làm lệch rate-limit/quota đang tính theo IP cũ — theo dõi 429 false-positive sau khi bật. **H13 phụ thuộc mục này — phải sửa TRƯỚC H13** |
| H13 ✅ | 🟠 | `SecurityConfig.java` dòng 179–184 + `ApiKeyAuthFilter.java` dòng 86–94 | **RT (V2.2/V2.3)** | Rate-limit chạy trước jwt/apiKey filter; đo lại sau sửa: key sai trùng prefix bị 429 trước khi tốn ~50ms bcrypt; **biến thể bắt buộc:** đo lại V2.2 nhưng kèm `X-Forwarded-For` xoay vòng mỗi request — vẫn phải chạm 429 | Đổi thứ tự filter ảnh hưởng luồng xác thực mọi request — phải regression toàn bộ auth flow |
| H14 ✅ | 🟠 | `SourceFileServiceImpl.java` dòng 110, 122–136, 196 | **RT (T6)** | `readRange` chốt `Files.size()` trước `readAllLines`; chạy lại T6 với file 200MiB: không còn +200MiB heap | Endpoint trả "file too large" cho file hợp lệ rất lớn — cần message rõ ràng |
| H15 ✅ | 🟠 | `SourceFileServiceImpl.java` dòng 68, 305–320 | SA | Redact theo block `-----BEGIN ... -----END`; body base64 không còn lọt qua endpoint đọc source | Regex block tham lam có thể che nhầm nội dung hợp lệ sau key — dùng non-greedy + test với file nhiều key |
| H16 | 🟠 | `vibegraph-web/package-lock.json` | SA | `npm audit` còn 0 critical/high (ưu tiên vá `websocket-driver`, `undici`, `axios`) | Nâng version có thể breaking change — chạy đủ type-check → test → lint → build |
| H17 ✅ | 🟠 | 7 job `@Scheduled` (liệt kê tại AUDIT-REPORT H17) | SA | `spring.task.scheduling.pool.size: 4` hiệu lực; counter `security_events.dropped.total` không tăng do nghẽn scheduler; gauge `request_events.queue.fresh.size` ổn định khi giả lập purge lớn; **alert rule đã cài** (xem ghi chú alert bên dưới) | Tăng pool tốn thêm thread — mức 4 là đủ cho 7 job vì đa số chạy ngắn/lệch giờ |
| B-M12 ✅ | 🟡 | `GlobalExceptionHandler.java` dòng 236–246 | SA | Nhánh `IllegalStateException` có `log.warn` + trả message chung an toàn, không lộ `ex.getMessage()` raw | Đổi message có thể làm frontend đang parse message cũ bị lệch — kiểm tra nơi hiển thị lỗi 409 |
| B-M14 ✅ | 🟡 | `ProjectTrashService.java` dòng 113 | SA | `findByDeletedAtLessThan` chạy theo batch + `Pageable`; purge trash lớn không chiếm thread scheduler quá lâu (cùng cụm nguyên nhân H17) | Xóa theo batch thay đổi tốc độ purge — kiểm tra không bỏ sót bản ghi giữa các batch |

**Nghiệm thu Đợt 1 (12/08/2026):** tổng kiểm `mvnw -DskipITs test` — 1.008 test, 0 failure, BUILD SUCCESS. Các mục ✅ đã đạt tiêu chí nghiệm thu kèm xác nhận runtime (số liệu chi tiết tại AUDIT-REPORT §10, gồm các lệch có lý do so với FIX-DETAILS: entrypoint chown H1, anchor filter H13, mở rộng redact H15, batch-0 stop B-M14, message tiếng Anh B-M12, Postgres host port 5433). **H16 KHÔNG đánh dấu ✅** — đợt nghiệm thu này không có dữ liệu `npm audit` cho H16; giữ nguyên trạng thái chờ thực hiện. Mục mở chuyển sang Backlog/Đợt 0: kiểm chứng T2 end-to-end chờ TLS termination; tàn dư test T6 (2 tài khoản disposable + project `be9ab43e`, `d0b1f52d`) chờ operator duyệt dọn; Đợt 0 (S1/S2) chưa làm.

**Ghi chú H13:** T5 (12/08/2026) từng KHÔNG tái hiện do key giả có prefix không khớp bản ghi nào → tra prefix trả danh sách rỗng, `passwordEncoder.matches()` không được gọi. **Đo lại V2 (12/08/2026) đã tái hiện — CONFIRMED:** median 30 request/nhóm 4,25ms (không key) / 4,64ms (prefix ngẫu nhiên) / **54,84ms (trùng prefix)** = +~50,20ms BCrypt trước 401; V2.3 xác nhận API-key filter chạy trước rate-limit. Không cần đo lại trước khi sửa — xử lý dứt điểm trong Đợt 1 cùng cụm bảo mật (key test `runtime-h13-20260812` đã xóa, `deleted_at IS NOT NULL`).

**Phụ thuộc bắt buộc S-M2 → H13 (reviewer 12/08/2026, đã kiểm cơ chế):** sửa H13 một mình VÔ HIỆU — với API key sai, principal là anonymous → `RateLimitFilter.java:88` chỉ consume bucket IP (`"ip:"+ip`); IP do `ClientAddressResolver` phân giải (S-M2: trust proxy true + `.findFirst()` token trái nhất) → attacker xoay `X-Forwarded-For` mỗi request = khóa bucket rate-limit mới mỗi lần → bcrypt vẫn bị gọi dù filter đã đổi thứ tự. Vì vậy S-M2 xếp TRƯỚC H13 trong Đợt 1; nghiệm thu H13 phải kèm biến thể XFF xoay vòng (vẫn phải chạm 429).
**Ghi chú alert production cho H17/B-L8 (BẮT BUỘC khi ship):** tác hại của chuỗi H17 là **im lặng** (drop security event không log, không lỗi hiển thị) → khi ship phải kèm: (1) alert khi counter `security_events.dropped.total` tăng (rate > 0 trong cửa sổ giám sát); (2) cảnh báo khi gauge `request_events.queue.fresh.size` vượt ngưỡng (vd > 80% sức chứa 10.000). Không có alert = coi như chưa sửa xong phần vận hành.
**Kiểm tra sau sửa:** chạy lại test T2, T6 trong `RUNTIME-VERIFICATION-PROMPT.md` + phép đo V2.2 (key sai trùng prefix phải bị rate-limit chặn trước khi tốn bcrypt, kể cả biến thể xoay vòng `X-Forwarded-For`); `docker compose up -d` toàn bộ service healthy; regression login/API key/rate-limit.
**Người thực hiện đề xuất:** Eric (DevOps) cho H1–H5; Tina (Bảo mật) cho H13–H16, S-M2; Alex (Backend) cho H17, B-M12, B-M14.

---

## 4. Đợt 2 — Tuần 2 (backend logic + hiệu năng)

**Mục tiêu:** sửa các điểm nóng logic backend và hiệu năng truy vấn.

| Mã | Mức | File chính | Trạng thái bằng chứng | Tiêu chí nghiệm thu | Rủi ro khi sửa |
|---|---|---|---|---|---|
| H6 | 🟠 | `ProjectServiceImpl.java` dòng 34 | SA | Registry status/progress/name đọc từ bảng `projects` Postgres (đã có sẵn cột), map chỉ là cache | Chuyển nguồn sự thật sang DB thay đổi ngữ nghĩa khôi phục sau restart — cần test restart toàn diện |
| H7 | 🟠 | `ProjectServiceImpl.java` dòng 62, 91, 101 | SA | Cả 3 điểm tạo project dùng full UUID + chống trùng; không còn `substring(0, 8)` | ID dài hơn ảnh hưởng mọi nơi lưu/hiển thị project id (URL, log, frontend) — rà soát toàn bộ |
| H8 | 🟠 | `ProjectController.java` dòng 106–119 | SA | `POST /{id}/analyze` trả 202 ngay, phân tích chạy nền theo pattern `PatchAnalysisScheduler` (dòng 53–81); progress qua WebSocket | Frontend đang chờ response đồng bộ phải chuyển sang theo dõi WebSocket — phối hợp Sam |
| H9 | 🟠 | `AdminService.java` dòng 518–530 | SA | Trang 20 user tốn ≤ 2 query bổ sung (batch `findAllById` + `sumStorageByOwners`); hết N+1 | Query `GROUP BY` mới cần kiểm tra index `owner_id` (đã có theo mục SẠCH/TỐT) |
| B-M1 | 🟡 | `Neo4jGraphRepository.java` dòng 119–132 | SA | `instantOrNull` có `log.warn` trước khi return null | Thấp — chỉ thêm log |
| B-M2 | 🟡 | `UseCaseInferenceEngine.java` dòng 1–1398 | SA | Tách Strategy/heuristic rules + `StringNormalizer` util; file ≤ ~400 dòng | Refactor lớn trên 1.398 dòng — bắt buộc có test bao phủ trước khi tách |
| B-M3 | 🟡 | `MethodVisitor.java` dòng 68 | SA | `Boolean.getBoolean(...)` thay bằng inject `@ConfigurationProperties`/constructor param; application.yaml hiệu lực | Mặc định hành vi đổi nếu cấu hình cũ đang dựa system property — giữ default cũ |
| B-M4 | 🟡 | `AdminService.java` dòng 478–499 | SA | Danh sách status/plan validate qua enum hoặc bảng `plans`, hết hardcode `List.of(...)` | Nếu DB có giá trị ngoài enum → validate fail; cần migration/kiểm tra dữ liệu hiện có |
| B-M5 | 🟡 | `FileChangeBroadcaster.java` dòng 103–113 | SA | Diff theo file (`findNodesByFile`) thay vì 2 lần `getFullGraph` | Cần đảm bảo diff trước/sau vẫn chính xác khi node thuộc nhiều file |
| B-M6 | 🟡 | `LlmUseCaseRefiner.java` dòng 71 | SA | `responseCache` chuyển Caffeine `maximumSize + expireAfterWrite` | Cache hết hạn làm tăng call LLM — chọn TTL hợp lý |
| B-M7 | 🟡 | `application.yaml` ~dòng 316 | SA | Mặc định INFO; DEBUG chỉ ở profile `dev` | Dev mất log DEBUG mặc định — ghi chú cách bật profile |
| B-M8 | 🟡 | `database/seed_dev.sql` dòng 15–22 | SA | Hash BCrypt placeholder thay bằng hash thật + lệnh sinh (hoặc ghi chú dựa `AdminBootstrapRunner`) | Hash sinh sai → dev không login được như cũ; kiểm tra `AdminBootstrapRunner` còn hoạt động |
| B-M9 | 🟡 | `IpBlockService.java` dòng 32–35 | SA | `findActive` có `@Cacheable` TTL ngắn (30–60s) keyed theo IP; hết round-trip DB mỗi request | Unblock IP trễ tối đa TTL — chấp nhận được, ghi chú vận hành |
| B-M10 | 🟡 | `.env` dòng 116 + `VIBEGRAPH_GRAPH_NODE_LIMIT` chưa đặt | **RT (T7)** | Cap node mặc định hiệu lực cả backend (property) lẫn frontend (safe node limit > 0); chạy lại T7 có cảnh báo cap | Cap quá thấp làm graph lớn bị cắt — chọn giá trị hợp lý + thông báo truncation |
| B-M11 | 🟡 | Neo4j upsert batch (autocommit `session.run`) | SA (V1.1 đã kiểm chứng tĩnh; runtime V1.2 BLOCKED) | Bọc các lệnh upsert trong 1 transaction + dọn/đánh dấu graph của project FAILED | Khi sửa cần test với project FAILED tạo chủ động trong môi trường riêng (không kill backend dùng chung); transaction lớn ảnh hưởng RAM Neo4j |
| B-M13 | 🟡 | 3 file test 0 byte (gồm `VibeGraphIT.java`, `GitHubImportIT.java`) | SA | File 0 byte bị xóa hoặc có test thật; `pom.xml:346–348` failsafe không còn chạy file rỗng | Xóa file track bởi git — cần commit dọn riêng, kiểm tra failsafe không fail vì thiếu class |

**Kiểm tra sau sửa:** `./mvnw verify` (JaCoCo gate) xanh; test tích hợp admin dashboard (phân trang/lọc/BAN) theo kịch bản T4 mở rộng; đo thời gian response endpoint admin trước/sau batch query.
**Người thực hiện đề xuất:** Alex (Backend/Database) toàn bộ đợt; Sam phối hợp phần frontend chờ H8 chuyển sang WebSocket.

---

## 5. Đợt 3 — Tuần 2–3 (frontend)

**Mục tiêu:** sửa UX/lỗi runtime frontend đã xác nhận + giảm bundle.

| Mã | Mức | File chính | Trạng thái bằng chứng | Tiêu chí nghiệm thu | Rủi ro khi sửa |
|---|---|---|---|---|---|
| H10 | 🟠 | `useGitHubImport.ts` ~124–160 + `GitHubImportForm.vue` + `ImportProjectPanel.vue` dòng 171 | SA (T3 BLOCKED) | Cancellation token + `onScopeDispose`; polling dừng khi unmount/chuyển tab | Chờ GitHub liên hệ được để chạy lại T3 xác nhận thực tế; logic cancel sai làm import dở dang treo |
| H11 | 🟠 | `UsersTableView.vue` dòng 70–72, 74–79, 104–110, 316–321, 340–345 | **RT (T4)** | 5 điểm gọi API có try/catch + thông báo lỗi i18n; chạy lại T4 (Offline + Search) hiển thị lỗi rõ ràng | Thấp — thêm xử lý lỗi, không đổi luồng chính |
| H12 | 🟠 | `vibegraph-web/src/router/index.ts` dòng 2–6 | **RT (T1)** | 5 view chuyển lazy import; chạy lại T1: landing/login không còn tải `sigma.js`/`graphology.js` | Route lazy tăng nhẹ độ trễ lần đầu vào view — acceptable, preload nếu cần |
| F-M1 | 🟡 | 9 file dead code (~1.319 dòng): `HeaderBar.vue`, `MainLayout.vue`, `SidePanel.vue`, `StatusBar.vue`, `GraphControls.vue`, `CodeInspector.vue`, `AddProjectLocal.vue`, `DirectoryBrowserModal.vue`, `useLocalImport.ts` | SA | 9 file + test tương ứng đã xóa; build xanh; grep không còn import | Nhầm file còn dùng — chỉ xóa file đã grep xác nhận 0 import (AddProjectLocal kéo theo 2 file phụ thuộc) |
| F-M2 | 🟡 | `src/lib/runtimeConfig.ts` dòng 100–104 | SA | Xóa `PROJECTS_AUTO_REFRESH_INTERVAL_MS` hoặc triển khai tính năng | Thấp |
| F-M3 | 🟡 | `src/lib/http.ts` + `package.json` (axios ^1.16.1) + `src/lib/api.ts` dòng 7, 622 | SA | `authApi.me()` chuyển fetch wrapper; gỡ axios + `lib/http.ts`; bundle bớt ~14KB gzip | Logic refresh 401 của fetch wrapper phải tương đương axios interceptor — test me() + hết hạn token |
| F-M4 | 🟡 | `src/language/index.ts` dòng 2–3 | SA | Locale mặc định eager, locale còn lại lazy qua dynamic import trong `setLocale()` | Chuyển ngôn ngữ lần đầu có độ trễ nhỏ — preload khi hover nếu cần |
| F-M5 | 🟡 | `vite.config.ts` | SA | `manualChunks` tách vendor (sigma, echarts, graphology); báo cáo build cho thấy chunk riêng | Chia chunk sai tạo circular warning — kiểm tra `npm run build` |
| F-M6 | 🟡 | 10 file > 400 dòng (`UserDetailDrawer.vue` 3.202 dòng …) | SA | Tách theo gợi ý (UserDetailDrawer → sub-panel quota/API keys/sessions; LandingView → sections; api.ts theo domain) | Refactor lớn — tách từng file một, mỗi file kèm test component |
| F-M7 | 🟡 | `GitHubImportForm.vue` dòng 137–138 | SA | Text cứng tiếng Anh chuyển `t('user.import.success', {...})` | Thấp — thêm key i18n cả 2 locale |

**Kiểm tra sau sửa:** chạy lại T1, T4 (và T3 nếu GitHub liên hệ được) trong `RUNTIME-VERIFICATION-PROMPT.md`; CI frontend đủ chuỗi type-check → test → lint → build → audit xanh; so sánh kích thước bundle trước/sau.
**Người thực hiện đề xuất:** Sam (Frontend) toàn bộ đợt.

---

## 6. Backlog — mức Thấp + trung bình chưa xếp đợt

**Mục tiêu:** dọn dẹp, chuẩn hóa, giảm nợ kỹ thuật — làm xen kẽ khi rỗi, không chặn đợt nào.

### 6.1. Toàn bộ phát hiện mức Thấp (26)

| Nhóm | Mã | Nội dung tóm tắt (theo AUDIT-REPORT) |
|---|---|---|
| Backend (11) | B-L1…B-L11 | Trùng factory parser; prune cache tuyến tính; overload JwtService chết; AnnotationVisitor @Deprecated; CORS đăng ký 2 nơi; ERD.md lỗi thời; field injection; shed-oldest telemetry (chuỗi H17); 6 DTO chết; entity `UserNotification` chết — GIỮ bảng `user_notifications` (đang dùng qua JDBC); TarballImportServiceTest 8/8 @Disabled → XÓA (implementation + test suite thật đã tồn tại, không bật lại) |
| Frontend (4) | F-L1…F-L4 | setTimeout đệ quy không hủy; listener window không remove; SearchBar O(n) mỗi keystroke; SVG path hardcode |
| Bảo mật (5) | S-L1…S-L5 | v-html tồn dư thấp; CSRF custom header; rate-limit per-instance; ACTIVE_USERS map phình nhẹ; GitHub parser `.`/`..` + redirect |
| DevOps (6) | D-L1…D-L6 | container_name cố định; credential dev mặc định; ~105MB rác root; .gitignore chặn nhầm quick-start; logs/ chồng chéo; VITE env bake lúc build |

### 6.2. Dọn rác root (D-L3) — lệnh ĐÍCH DANH

Xóa ĐÍCH DANH nhóm log/dump ở root đã liệt kê trong AUDIT-REPORT (đo lại 12/08/2026: 28 file rác, lớn nhất `backend_run.log` 71M): `backend.out.log` (14 MB), `graph_check.json` (2,9 MB), `replay_pid*.log`, `scratch.diff`, `bash.exe.stackdump`… (tổng ~105 MB, không bị git track).

⚠️ **KHÔNG dùng `git clean -fdX`** — lệnh này xóa ~985MB gồm cả `target/`, `node_modules/`, `.gitnexus/`, `.vibegraph/` (đều trong .gitignore) chứ không chỉ 105MB rác root; chỉ dùng khi chấp nhận mất cache/build.

### 6.3. Phát hiện mức Trung bình chưa xếp đợt (9)

| Mã | Nội dung | Gợi ý thời điểm |
|---|---|---|
| S-M1 | `SupabaseDatabaseConfig.java:181` DDL ghép chuỗi schema → validate regex `^[a-z0-9_]+$` | Cùng đợt bảo mật kế tiếp |
| S-M3 | Actuator `info,metrics,prometheus` USER thường đọc được → `hasRole('ADMIN')` trừ health | Có thể kéo lên Đợt 1 nếu thấy cần |
| S-M4 | Fulltext Lucene nhận input thô → escape + map 400 | Trước khi nối lại dead path |
| S-M5 | Trần multipart 2048MB → hạ sát quota gói lớn nhất (vd 512MB) | Cùng đợt cấu hình |
| D-M1 | `scripts/dev-up.ps1:32–44` thiếu Postgres → thêm `docker compose up -d postgres` + chờ `pg_isready` | Làm sớm — dev đang chạy script này sẽ fail backend |
| D-M2 | Image tag không pin minor/patch → pin tag/digest | Cùng đợt Dockerfile |
| D-M3 | `Dockerfile:12` wildcard jar → tên tường minh + `<finalName>app</finalName>` | Làm chung H1 (đã kèm trong snippet H1) |
| D-M4 | Actions chỉ pin major → pin SHA + bổ sung workflow CD | Cùng đợt CI/CD |
| D-M5 | `task/` vs `task-final/` cùng track 8 tên file nhưng **không phải bản sao hoàn toàn** → xác định ownership của `task-final` rồi merge từng file bằng diff; KHÔNG `git rm` cả thư mục máy móc | Dọn dẹp định kỳ |

**Kiểm tra sau sửa:** từng mục tự nghiệm thu theo cột "Đề xuất sửa" trong AUDIT-REPORT; `./mvnw verify` + `npm run build` xanh sau mỗi nhóm.
**Người thực hiện đề xuất:** phân theo lĩnh vực — Alex (B-L), Sam (F-L), Tina (S-L, S-M), Eric (D-L, D-M); D-M1 nên ưu tiên sớm cho Eric.

---

## 7. Định nghĩa xong (Definition of Done) mỗi đợt

1. Mọi phát hiện trong đợt đạt tiêu chí nghiệm thu ở bảng tương ứng.
2. Test/lệnh kiểm tra của đợt chạy xanh (bộ test runtime `RUNTIME-VERIFICATION-PROMPT.md` cho các mã có RT).
3. Không phát sinh regression: `./mvnw verify` (backend) và type-check → test → lint → build → audit (frontend) xanh.
4. Kết quả nghiệm thu runtime mới (nếu có) được lưu vào `runtime-evidence/` và tham chiếu về AUDIT-REPORT.
