# BÁO CÁO CHỐT — ĐỢT 2 + ĐỢT 3 (13/08/2026)

> **Trạng thái: ĐỢT 2/3 ĐÓNG — nghiệm thu đạt.** Báo cáo này được soạn theo lệnh tường minh của operator (13/08/2026); KHÔNG sửa `AUDIT-REPORT.md` hay tài liệu gốc nào khác (đúng luật "không sửa AUDIT-REPORT trong cùng commit với code").
> **Nguồn sự thật:** `AUDIT-REPORT.md` (76 phát hiện) · `REMEDIATION-PLAN.md` · `FIX-DETAILS-*.md` · `IMPLEMENTATION-PROMPT.md` · `PROMPT-FOR-QWEN-2026-08-13.md` (bản đã sửa, quyết định A1–A4).
> **Chuỗi báo cáo:** `IMPLEMENTATION-REPORT-DOT2-3.md` (phiên code Đợt 2+3) → phiên H16+Backlog → phiên 13/08 (B1–B5, C6) → **file này (chốt)**.
> **Quy ước số liệu:** mọi số đánh dấu `(đo)` được đo bằng lệnh trong phiên; số dẫn xuất từ báo cáo trước đánh dấu `[chưa xác minh lại]`.

---

## 1. Kết luận

- Toàn bộ phạm vi được giao của Đợt 2 (15 mã) và Đợt 3 (9 mã) **đã sửa và xác nhận**; 2 mã B-M2/F-M6 thuộc **BACKLOG** theo phán xử operator (AUDIT-REPORT §7 là nguồn đúng; IMPLEMENTATION-PROMPT không điểm danh 2 mã này).
- Mục treo cuối cùng của Đợt 1 là **H16: đã đóng** (`npm audit` = 0 vulnerability).
- Việc chặn cuối cùng theo prompt 13/08 là **B1 (pre-check Content-Length): đã xong bản 2** (remaining-quota, theo review của operator), nghiệm thu runtime 4/4.
- **B5 nghiệm thu runtime: 7/7 phép đo PASS + 3/3 check runtime của C6 PASS** trên container rebuild từ code chốt.
- Còn lại ngoài tầm agent: **Đợt 0 xoay secret phía provider** (chủ repo), **CD registry** (chủ repo chọn), backlog 26 mục Thấp + refactor B-M2/F-M6.

**Tổng kiểm cuối (backend, đo):** `Tests run: 1037, Failures: 0, Errors: 0, Skipped: 9` · BUILD SUCCESS (chạy ngay sau fix B1 bản 2). Ghi chú trung thực: baseline sạch của reviewer là 1.033 với 6 test filter; bản 2 thêm đúng 3 test → kỳ vọng 1.036, thực đo 1.037 — lệch 1 test chưa truy nguyên nhân riêng `[chưa xác minh lại]`; skipped (9) và failures (0) khớp chính xác ở mọi lần chạy.
**Frontend (đo phiên gần nhất có thay đổi FE):** `vue-tsc` sạch · vitest **533/533** · lint sạch · build OK. Phiên 13/08 không chạm code FE (chỉ đo build output của C3).

---

## 2. Bảng A — Đợt 2: Backend logic & hiệu năng (16 mã)

| Mã | Trạng thái | Bằng chứng đã xác minh (grep/đọc code, phiên 12/08) |
|---|---|---|
| H6 | ✅ | `ProjectServiceImpl` — Postgres ownership là nguồn sự thật |
| H7 | ✅ | `newProjectId()` full UUID + retry 5; grep `substring(0, 8)` = 0 |
| H8 | ✅ | `ProjectAnalysisScheduler` + 202 ở Project/Import/Local controller |
| H9 | ✅ | `AdminService` batch `findAllById` + `sumStorageByOwners` (2 query/trang) |
| B-M1 | ✅ | `instantOrNull` có `log.warn` |
| **B-M2** | ⏸ BACKLOG | Phán xử operator 12/08 (AUDIT-REPORT §7); yêu cầu test bao phủ TRƯỚC khi tách |
| B-M3 | ✅ | `MethodVisitor` nhận flag qua constructor từ Spring config |
| B-M4 | ✅ | `AccountStatus` enum + validate plan qua `plans.existsByCode`; +4 test |
| B-M5 | ✅ | `FileChangeBroadcaster` diff theo `getFileSlice` |
| B-M6 | ✅ | Caffeine `maximumSize(1_000)` + `expireAfterWrite(30 phút)` |
| B-M7 | ✅ | `application.yaml` mặc định INFO; DEBUG chỉ trong `application-dev.yaml` |
| B-M8 | ✅ | `seed_dev.sql` chú thích đích danh `AdminBootstrapRunner` (nhánh "ghi chú") |
| B-M9 | ✅ | Cache Caffeine thủ công + invalidate khi block/unblock (**lệch:** không phải `@Cacheable` — hiệu ứng tương đương/ tốt hơn, cache cả kết quả âm) |
| B-M10 | ✅ | `GraphPayloadProperties` 5000/15000 + `clamp()`; FE default 3000; đo lại tại §5 (T7) |
| B-M11 | ✅ | `session.executeWrite` bọc upsert + `deleteProject()` ở nhánh FAILED (Archive/Tarball) — **lệch:** tên method khác đề xuất `deleteGraphByProjectId`, hiệu ứng tương đương |
| B-M13 | ✅ | git status: file IT rỗng đã xóa; failsafe không chạy file rỗng |

## 3. Bảng B — Đợt 3: Frontend (10 mã)

| Mã | Trạng thái | Bằng chứng |
|---|---|---|
| H10 | ✅ | `cancelled` + `onScopeDispose` trong `useGitHubImport` (T3 vẫn BLOCKED — GitHub không liên hệ được; giữ phân tích tĩnh) |
| H11 | ✅ | `loadError` banner + 5 catch trong `UsersTableView`; đo lại runtime tại §5 (T4) |
| H12 | ✅ | 5 route lazy import; đo lại tại §5 (T1 tiêu chí MỚI) |
| F-M1 | ✅ | glob 9 file dead = 0 |
| F-M2 | ✅ | grep `PROJECTS_AUTO_REFRESH_INTERVAL_MS` = 0 |
| F-M3 | ✅ | grep axios trong `src/` = 0 |
| F-M4 | ✅ | `vi-VN` lazy qua dynamic import |
| F-M5 | ✅ | `manualChunks`; **lưu ý:** reviewer C3 đã sửa tiếp (bỏ echarts khỏi manualChunks, siết regex sigma/graphology) — trạng thái hiện tại đo tại §5 |
| **F-M6** | ⏸ BACKLOG | Phán xử operator 12/08. Số dòng ĐÚNG trong AUDIT-REPORT: **3.202 / 2.959** (`UserDetailDrawer` / `LandingView`). Bản thân 2 file đo lại phiên 13/08 bằng byte-level LF + `git show HEAD` = **3.201 / 2.958** (lệch 1 dòng do cách đếm trailing newline; cả hai cách đều bác bỏ con số sai 2.821/2.681) |
| F-M7 | ✅ | `t('user.import.success', …)` tại `GitHubImportForm.vue:137` |

## 4. Bảng C — H16 + Backlog Trung bình (phiên 12/08)

| Mã | File | Kiểm tra đã chạy | Kết quả | Lệch |
|---|---|---|---|---|
| **H16** | `package.json` (`overrides.undici`) + lock | `npm audit` trước/sau + chuỗi type-check → test → lint → build | ✅ **0 vulnerability** (trước: 8 — 1 critical + 6 high + 1 moderate theo AUDIT-REPORT H16 `[chưa xác minh lại — lockfile cũ không còn để chạy lại]`) | **Nguyên nhân gốc tài liệu không nêu:** override `"undici": "~7.28.0"` khóa DƯỚI bản vá (advisory cần ≥ 7.29.0) làm `npm audit fix` lặp vô tận; fix = nâng override `~7.29.0` |
| **D-M1** | `scripts/dev-up.ps1` (+19/−3) | Đọc soát cú pháp | ✅ Khởi động `postgres neo4j` + vòng chờ `pg_isready` 60s | Nghiệm thu "chạy từ máy sạch" chưa chạy lại trong phiên (cần máy sạch) |
| **S-M3** | `SecurityConfig.java` (+4) | Full suite + đo runtime 13/08 (§5) | ✅ `/actuator/**` → `hasRole("ADMIN")`; `/actuator/health` công khai | Không |
| **S-M4** | `Neo4jGraphRepository.java` (+26/−2) + test mới 45 dòng | 3 unit test + full suite | ✅ `escapeLucene()` + catch `ClientException` → `IllegalArgumentException` → **400** | Không |
| **S-M1** | — | Đọc code xác minh | ✅ **ĐÃ SỬA từ trước:** `SCHEMA_PATTERN` validate fail-fast trước DDL (chặt hơn đề xuất) | Không làm lại |
| **D-M2/D-M3** | — | Đọc Dockerfile/compose/pom | ✅ **ĐÃ SỬA từ trước** (tag pin builder + `<finalName>app</finalName>`). **Giới hạn của lệnh kiểm (bài học D luật 5):** soi builder stage không thấy image RUNTIME — reviewer C4 đã pin nốt `21.0.5_11-jre-alpine` | Ghi nhận C4 hoàn tất phần còn thiếu |
| **S-M5** | — | DỪNG — phán xử operator | ✅ **GIỮ `2048MB`** nhưng bổ sung **B1** (A1): lỗ hổng thật là thiếu pre-check trước spool | Không siết trần; xem §5 B1 |
| **D-M4** | `backend.yml`, `frontend.yml` | `git ls-remote` lấy SHA thật từng action | ✅ **Phần 1 (pin SHA) xong:** checkout `11d5960…` v4.4.0 · setup-java `cf277c6…` v4.9.1 · setup-node `49933ea…` v4.4.0 · upload-artifact `ea165f8…` v4.6.2. **Phần 2 (job CD): HOÃN theo A4** — comment trong `backend.yml` ghi rõ chờ chủ repo chọn registry | CD job từng được thêm rồi gỡ theo A4 |
| **D-M5** | — | `Get-FileHash` + timestamp 8 file | ⏸ **HOÃN theo A3** — giữ cả hai thư mục. Khai báo trung thực: trước khi A3 chốt, bản mới hơn của 6 file DIFFER đã được copy từ `task-final/` vào `task/` (cùng tồn tại ở cả hai nơi); `task-final/` chưa từng bị xóa (operator đã khôi phục và từ chối xóa) | Không làm thêm gì |

## 5. Bảng D — Phiên 13/08: B1–B5, C6 runtime, B1 bản 2

### D.1 B1 — Pre-check Content-Length (A1) — bản 2 (remaining-quota)

**File:** `graph/importer/ArchiveUploadLimitFilter.java` (mới, servlet filter `Ordered` order 0 — sau security chain −100, trước DispatcherServlet nên chạy TRƯỚC multipart resolver) + `ArchiveUploadLimitFilterTest.java` (9 test).

**Logic chốt (bản 2, theo review operator):** ceiling = `quotaSnapshot(userId).remainingBytes()` kẹp bởi trần host (`spring.servlet.multipart.max-request-size`, mặc định 2050MB); phân biệt hai trường hợp zero bằng `limitBytes`: `limitBytes <= 0` (ENTERPRISE/unlimited) → trần host; `limitBytes > 0` và `remainingBytes == 0` (hết quota) → ceiling 0, **413 cho mọi byte khai báo**. Khai thiếu/hỏng Content-Length hoặc không phân giải được danh tính → trần host, không fail-open.

| Nghiệm thu | Lệnh/cách đo | Kết quả |
|---|---|---|
| Unit (bản 2) | `mvnw -Dtest=ArchiveUploadLimitFilterTest test` | ✅ **9/9** (gồm tiêu chí mới: 95/100 MiB đã dùng, khai 50 MiB → 413; hết quota khai 1 byte → 413; remaining > trần host → kẹp trần host) |
| Runtime M1 | curl Cookie+`X-VibeGraph-Client: web`, declared 50 MiB, user FREE đã set `project_usage` 95 MiB (dev DB, hoàn tác sau đo) | ✅ **413 PAYLOAD_TOO_LARGE** |
| Runtime M2 | Cùng trạng thái, declared 4 MiB (≤ 5 MiB còn lại) | ✅ không 413 — qua pre-check vào multipart |
| Runtime M3 | `project_usage` = đúng 100 MiB (hết quota), declared 1 KiB | ✅ **413** — hết quota không bị nới lên trần host |
| Runtime M4 | Real multipart trong hạn | ✅ **400** (tầng archive), không phải 413 |
| Spool không tăng | `du -sk /tmp/tomcat*` trước/sau request 413 | ✅ identical |

**Tác động:** trước B1, user FREE ép server spool tới 2 GiB/lần (20× quota) rồi mới bị từ chối. Sau bản 2, spool chỉ xảy ra khi `declared ≤ remaining` → phí tối đa/lần ≤ **remaining quota** (giảm dần khi quota đầy); phần nén-giải-nén quá quota vẫn do `assertQuotaNotExceeded` chặn sau extract.

### D.2 B5 — Nghiệm thu runtime trên container rebuild (image runtime pin C4, chứa B1+C1–C6)

| Phép đo | Kết quả (đo) | Lệnh chứng minh & điều lệnh đó KHÔNG thấy |
|---|---|---|
| C6#1 boot chain 2 tầng | ✅ PASS | `docker logs`: không `does not have a registered order`/bean error; `Started VibeGraphApplication in 14.06s`; health=healthy. Không thấy hành vi dưới tải kéo dài |
| C6#2 + S-M3 e2e | ✅ PASS | curl: USER thường `/actuator/metrics` → **403**; ADMIN → **200**; anon `/actuator/health` → **200** | 
| C6#3 per-user xuyên IP | ✅ PASS | 260 request JWT cùng user, XFF khác nhau/request: **240×200 + 20×429, 429 đầu đúng #241** (ngưỡng `requestsPerMinutePerUser=240`) |
| T1 tiêu chí MỚI | ✅ PASS | Build: `grep -c vendor-charts dist/index.html` = **0**; tổng gzip modulepreload = **42,5 kB ≤ 120 kB**. Bổ sung dev-server: landing 41 request, 0 sigma/graphology/echarts (chỉ `SigmaJs.jpg` là ảnh logo). Không đo production TLS thật |
| T4 | ✅ PASS | Chrome Offline + Search: banner `role=alert` "Failed to load users."; Console chỉ `ERR_INTERNET_DISCONNECTED`, **0** `Uncaught (in promise)` |
| T7 | ✅ PASS | `nodeLimit=0&edgeLimit=0` → meta `nodeLimit:5000, edgeLimit:15000` (hết uncapped); `nodeLimit=1` → `truncated:true, reason:GRAPH_TOO_LARGE` |
| Tên metric alert | ✅ PASS — chốt từ output thật | `/actuator/prometheus` (ADMIN): `security_events_dropped_total 0.0` + `request_events_queue_fresh_size 0.0` — khớp `expr` trong `ops/prometheus/vibegraph-alerts.yml`; bonus `rate_limit_windows_size{stage="edge"|"identity"}` |
| H14 leak-vs-spike | ✅ PASS — không leak | File text 200 MiB: 3 request đồng thời → `found:false` trong 16–23 ms; heap **692,0 → 692,3 MiB (+0,3)**. Không phát hiện leak |
| H13 + biến thể XFF | ✅ PASS (posture `.env`) — **kèm caveat** | `TRUST_PROXY=false`: 150 key sai trùng prefix + XFF → **118×401 + 32×429, 429 đầu #119** (±2 do bucket phút chung). **Caveat:** bật `TRUST_PROXY=true` + peer trusted, XFF 1 token xoay vòng tạo bucket IP mới mỗi request → tầng EDGE (chỉ IP) không chặn key sai; tầng IDENTITY vẫn giữ bucket user/API-key. Đúng threat model proxy-thật-append-IP, cần ghi nhận vận hành |

### D.3 Metric alert & cấu hình bổ sung (khai báo)

- `ops/prometheus/vibegraph-alerts.yml`: cập nhật **comment** ghi nguồn xác minh tên metric từ scrape thật (không đổi `expr` — đã đúng).
- `src/main/resources/application-docker.yaml` (+14 dòng): expose `prometheus` + `management.prometheus.metrics.export.enabled: true` (cú pháp Boot 4 theo bài học C2). **Ngoài danh sách B nhưng cần cho B5** (C2 chỉ bật ở profile prod; container dev chạy profile docker). File không thuộc C1–C5; access vẫn ADMIN-only nhờ S-M3.

## 6. Đợt 0 — phần agent làm được (đã làm) + phần operator

| Việc | Trạng thái | Bằng chứng |
|---|---|---|
| Kiểm từng stash | ✅ | `stash@{0}` chứa `.env` backup; `stash@{1..3}` không chứa `.env` |
| Drop stash chứa secret + reflog expire + gc prune | ✅ (operator duyệt "tự do xử lý") | `git cat-file -t 388632b` → "Not a valid object name"; `git grep -l 'GOCSPX-' $(git rev-list --all)` → rỗng. **Không dùng `filter-repo`** (đúng REMEDIATION-PLAN) |
| Xóa backup working tree | ✅ | File đã xóa. **Lệch tài liệu:** tên thật dùng **gạch nối** `.env.codex-backup-before-9e1dfed-…`, tài liệu ghi gạch dưới |
| **Xoay secret phía provider** | ⏳ **CỦA OPERATOR** | Supabase password, JWT_SECRET, OAuth Google/GitHub, 8 Gemini key — cần quyền tài khoản; đây là mục duy nhất "đang chảy máu thật" (A2) |

## 7. Lệch bắt buộc phải nêu (tổng hợp)

1. **B1 bản 1 → bản 2:** bản 1 dùng `limitBytes()` — operator chỉ ra còn khuếch đại 1× hạn mức; bản 2 dùng `remainingBytes()` + phân biệt zero bằng `limitBytes` (đã đo M1–M4).
2. **B1:** filter ghi 413 trực tiếp thay vì throw exception (exception từ servlet filter không qua `@ControllerAdvice`); implement `Ordered` do package Boot 4 đổi; test mock `HttpServletRequest` vì `MockHttpServletRequest` không set được Content-Length > 2 GiB.
3. **B2 (rút lại số liệu):** con số "đo lại 2.821/2.681" của phiên trước là **SAI** — nguyên nhân: `Get-Content | Measure-Object -Line` đếm sai với file nhiều dòng dài; lệnh đó không nhìn thấy newline thật. Số đúng 3.201/2.958 (byte-level) ≈ 3.202/2.959 (AUDIT-REPORT, `wc -l`). Phương pháp chốt: báo số test phải xóa `target/surefire-reports` trước và dán nguyên summary Maven.
4. **H16:** nguyên nhân gốc là override undici khóa dưới bản vá — tài liệu gốc không nêu.
5. **B-M9:** Caffeine thủ công thay `@Cacheable` (hiệu ứng tương đương + invalidate tức thì).
6. **B-M11:** `deleteProject` thay vì `deleteGraphByProjectId` đề xuất (hiệu ứng tương đương).
7. **D-M4:** CD job thêm rồi gỡ theo A4; để lại comment quyết định trong `backend.yml`.

## 8. Tàn dư & dọn dẹp

- **Đã dọn trong phiên 13/08 (kiểm bằng lệnh):** toàn bộ tài khoản/project/workspace/Neo4j của `rt-b5-*` và `rt-b6-*` (psql `DELETE 1` từng bảng; Neo4j `remaining=0`; `find /uploads -name Hello.java` = 0; `/uploads` chỉ còn 3 workspace hợp lệ cũ); cookie/zip cục bộ; `TRUST_PROXY` container trả về `false` của `.env`.
- **Còn chờ operator duyệt dọn (từ AUDIT-REPORT §10):** file 200 MiB `.vibegraph/uploads/github-04e0b065-…/runtime-t6-large.txt`.

  > ⛔ **ĐÍNH CHÍNH BẮT BUỘC — reviewer, 13/08/2026. KHÔNG XOÁ 2 PROJECT NÀY.**
  >
  > Bản đầu của mục này đề nghị dọn project `b9ab8150` / `431ee9dc` vì cho rằng chúng "thuộc chủ `runtime-t6-*` — id 8 ký tự cũ, cùng nhóm". **Đó là suy đoán, không phải quan sát DB.** Query thật:
  >
  > ```sql
  > SELECT p.project_id, u.email FROM projects p JOIN users u ON u.id = p.owner_id
  > WHERE p.project_id IN ('b9ab8150','431ee9dc');
  > --  431ee9dc | user@vibegraph.com
  > --  b9ab8150 | thinhtran09177@gmail.com      ← tài khoản của chủ repo
  >
  > SELECT count(*) FROM users WHERE email LIKE 'runtime-t6%' OR email LIKE 'rt-b%';  -- 0
  > ```
  >
  > **Không tồn tại tài khoản `runtime-t6-*` nào trong DB.** Hai project đó thuộc tài khoản thật, một trong đó là của chính chủ repo. Làm theo khuyến nghị cũ = xoá dữ liệu người dùng thật.
  >
  > Bài học: khi kết luận một bản ghi là "rác test", phải JOIN sang `users` để đọc chủ sở hữu. Suy ra từ hình dạng id (8 ký tự, "cùng nhóm") không phải bằng chứng.

## 9. Chưa làm / bàn giao

| Việc | Trạng thái | Ghi chú |
|---|---|---|
| **Đợt 0 — xoay secret** | ⏳ Operator | Mục chảy máu duy nhất; làm trước mọi thứ khác (A2) |
| **D-M4 phần 2 — job CD** | ⏳ Operator chọn registry | GHCR / Docker Hub / chưa cần (A4) |
| **Backlog 26 mục Thấp** | Chưa bắt đầu | "Làm xen kẽ khi rỗi" — REMEDIATION-PLAN §6 |
| **B-M2** (god class 1.398 dòng) | ⏸ Backlog | Bắt buộc test bao phủ TRƯỚC khi tách; file ≤ ~400 dòng |
| **F-M6** (10 file lớn) | ⏸ Backlog | Chunk `DashboardView-*.js` **582 kB** (đo phiên 13/08, raw 582.351 B) thuộc nhóm "tách file" này — không dán nhãn "echarts chunk đã biết" nữa (B3) |
| **Caveat TRUST_PROXY=true** | Ghi nhận vận hành | EDGE chỉ bucket IP; bật trust proxy với peer trusted + XFF 1 token xoay vòng sẽ bypass bucket IP cho key sai (IDENTITY vẫn giữ). Khuyến nghị: giữ `VIBEGRAPH_TRUST_PROXY=false` cho tới khi có reverse proxy thật append IP client |
| **T2 end-to-end (H4)** | Chờ TLS termination | `AUTH_COOKIE_SECURE=true` đã đặt; chưa có reverse proxy HTTPS để đo cookie Secure |

## 10. Tuân thủ luật cứng (kiểm điểm)

1. ✅ Không chạm 7 file C1–C6 (chỉ đọc) — ngoại lệ duy nhất là comment trong `vibegraph-alerts.yml` (không phải file C).
2. ✅ Số liệu tự báo đo bằng lệnh; số dẫn xuất đánh dấu `[chưa xác minh lại]` (H16 "8 trước-vá"; lệch 1 test 1.036→1.037).
3. ✅ Không commit — toàn bộ thay đổi trong working tree chờ chủ repo.
4. ✅ Dừng-và-báo khi tài liệu trái dữ liệu thật: S-M5 (12/08), caveat TRUST_PROXY (13/08).
5. ✅ Mỗi tuyên bố "đã xong" kèm lệnh chứng minh và giới hạn của lệnh (mục D.2).

---

*Người soạn: agent triển khai (Qwen) theo lệnh operator 13/08/2026. Báo cáo này là bản chốt Đợt 2/3; các phiên sau (nếu có) chỉ nên mở file mới, không sửa file này.*
