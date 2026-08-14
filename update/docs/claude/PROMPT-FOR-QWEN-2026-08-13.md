# PROMPT CHO QWEN — chốt Đợt 2/3 (13/08/2026)

Gửi nguyên văn phần dưới cho Qwen.

---

## Bối cảnh

Reviewer đã rà soát code Đợt 2+3 và báo cáo phiên H16+Backlog. Kết quả: **build và test đều xanh**, phần lớn fix đúng. Ghi nhận riêng hai việc làm tốt:

- **S-M5**: bạn dừng lại thay vì làm theo tài liệu khi tài liệu trái dữ liệu thật. Đã kiểm chứng bạn đúng — `V3__plans_and_credits.sql:48` plan MAX = `2147483648` byte = đúng 2 GiB = đúng trần `2048MB`.
- **H16**: chẩn đoán nguyên nhân gốc (`override "undici"` khoá **dưới** bản vá làm `npm audit fix` lặp vô tận) là công việc debug thật, không phải chạy lệnh cho có.

Dưới đây là **quyết định cho 4 câu hỏi bạn nêu**, **việc của bạn**, và **việc reviewer đã/đang làm** (đừng chạm vào).

---

## PHẦN A — Quyết định cho 4 câu hỏi của bạn

### A1. S-M5 — GIỮ `2048MB`. Nhưng "hiện trạng đã đạt" là **chưa đúng**.

Bảng plan thật (`V3__plans_and_credits.sql:45–49`):

| Plan | storage_limit_bytes | |
|---|---|---|
| FREE | 104857600 | 100 MiB |
| PRO | 524288000 | 500 MiB |
| PRO_PLUS | 1073741824 | 1 GiB |
| MAX | 2147483648 | **2 GiB** |
| ENTERPRISE | 0 | contact sales |

Không siết trần. Nhưng lỗ hổng thật nằm chỗ khác — đã kiểm chứng:

```bash
grep -rn 'getContentLength' src/main/java --include=*.java    # 0 hit — KHÔNG có pre-check nào
```

`accountSettingsService.assertQuotaNotExceeded(userId, file.getSize())` tại `ArchiveImportServiceImpl.java:181` nhận `MultipartFile` — tại thời điểm đó **Spring đã spool xong toàn bộ body xuống đĩa**. Nên **một user FREE (quota 100 MiB) vẫn khiến server ghi 2 GiB xuống đĩa rồi mới bị từ chối**: khuếch đại ghi 20×, lặp lại được, ở gói thấp nhất.

**Việc phải làm (Đợt 2, không phải backlog):** thêm pre-check `Content-Length` **trước khi multipart parse**, đối chiếu hạn mức hiệu lực của caller.

```java
// Filter/interceptor đặt TRƯỚC multipart resolver, chỉ áp cho route import archive.
long declared = request.getContentLengthLong();
long allowed  = accountSettingsService.effectiveLimitBytes(userId);   // 0 = ENTERPRISE → dùng trần host
long ceiling  = allowed > 0 ? Math.min(allowed, hostCeilingBytes) : hostCeilingBytes;
if (declared > 0 && declared > ceiling) {
    // 413 trước khi ghi 1 byte nào xuống đĩa
    throw new PayloadTooLargeException(declared, ceiling);
}
```

**Tiêu chí nghiệm thu (bắt buộc đo, không suy luận):**
1. User FREE POST archive 500 MiB → nhận **413** và `du -sh` thư mục spool multipart **không tăng**.
2. User MAX POST archive 1,5 GiB → vẫn **đi qua** (không regression gói cao).
3. `Content-Length` thiếu/không hợp lệ → rơi về trần host hiện tại, không fail-open.

### A2. Đợt 0 (xoay secret + dọn git object) — **chủ repo tự làm, hôm nay, trước mọi thứ khác.**
Cần quyền tài khoản Supabase/Google/GitHub/Gemini nên không giao cho agent. Đây là mục duy nhất trong 76 mục đang chảy máu thật. Lệnh dọn git object đã chốt ở `REMEDIATION-PLAN.md` Đợt 0 — dùng đúng bản đó (`stash drop` → `reflog expire` → `gc --prune`, **không** `filter-repo`).

### A3. D-M5 (`task/` vs `task-final/`) — **hoãn về backlog. Không tiêu thời gian Đợt 2/3.**
Đã kiểm: 2/3 file **khác nhau**, chỉ `export_to_csv.py` giống hệt. Là tài liệu, rủi ro runtime = 0. Giữ cả hai thư mục, **không merge mù, không `git rm` cả thư mục**. Ghi chú lại đúng như bạn đã viết ở D-M5 — không cần làm gì thêm.

### A4. D-M4 — **tách đôi.**
- **Làm ngay:** pin SHA cho GitHub Actions (cơ học, rủi ro thấp).
- **Hoãn:** job CD — cần chủ repo chọn registry (GHCR / Docker Hub / chưa cần). Đừng để nó chặn Đợt 2/3.

---

## PHẦN B — Việc của bạn trong phiên tới

### B1. 🔴 Pre-check `Content-Length` theo plan (A1)
Theo spec + 3 tiêu chí nghiệm thu ở trên.

### B2. Sửa số liệu tự báo trong tài liệu

| Cần sửa | Hiện ghi | Đúng là | Cách kiểm |
|---|---|---|---|
| **RÚT LẠI** dòng "đo lại: UserDetailDrawer 2.821 dòng, LandingView 2.681" | 2.821 / 2.681 | **3.201 / 2.958** | `wc -l` + `git diff --numstat` 2 file này **rỗng** — chúng KHÔNG bị sửa trong Đợt 2/3 nên số dòng không thể đổi. Hai con số 2.821/2.681 trùng khớp số đã bị chứng minh sai của `claudepostman` |
| npm audit trước khi vá | "từ 7: 1 critical + 5 high + 1 moderate" | **8** (1 critical + 6 high + 1 moderate) | Chính `AUDIT-REPORT.md` H16 của bạn ghi 8 |
| Test backend | 1.024 test, 9 skipped | **không kết luận được — xem ghi chú** | Reviewer từng nói con số của bạn sai (1.065). **Điều đó không đáng tin**: 1.065 là kết quả gộp file XML surefire **cũ còn sót** từ các lần chạy `-Dtest=...` riêng lẻ. Sau khi xoá `target/classes`, `target/test-classes`, `target/surefire-reports` rồi chạy lại đầy đủ, con số là **1.033 test · 9 skipped · 0 error · 0 failure** (đã gồm 3 test mới của reviewer). **Không có bằng chứng nào cho thấy 1.024 của bạn sai.** Việc cần làm: khi báo số test, xoá `target/surefire-reports` trước rồi dán nguyên dòng summary của Maven — đừng gộp XML tồn đọng (reviewer đã mắc đúng lỗi này) |

**Quan trọng:** `AUDIT-REPORT.md` F-M6 hiện đang ghi **ĐÚNG** (3.202 / 2.959 — chính bạn đo). Đừng "cập nhật cho mới" bằng 2.821/2.681 — làm vậy là ghi số sai lên số đúng, và dán nhãn "đo lại".

### B3. Chuyển warning chunk >500 kB ra khỏi F-M6
Trước đây bạn ghi *"warning chunk echarts > 500 kB đã biết, thuộc F-M6 backlog"* — **chẩn đoán sai địa chỉ**. F-M6 là "SFC quá lớn" (file nguồn); warning đó do luật `manualChunks` đẩy ECharts thành chunk mà entry import tĩnh.

Reviewer **đã sửa** `vite.config.ts` (xem C3). Sau khi sửa, warning đã **đổi địa chỉ**: giờ là `DashboardView-*.js` **582 kB** — và *bây giờ* nó mới thực sự thuộc nhóm "tách file". Cập nhật tài liệu theo trạng thái mới, đừng để nó nằm dưới nhãn cũ.

### B4. Pin SHA cho GitHub Actions (D-M4 phần 1)
`.github/workflows/backend.yml:39,42,56` và `frontend.yml:33,36`.

### B5. Nghiệm thu runtime — **chạy SAU khi B1 và C4 xong**
Đo bây giờ là đo một build sắp đổi.

| Test | Việc |
|---|---|
| **T1 — tiêu chí MỚI** | Tiêu chí cũ ("landing không còn `sigma.js`/`graphology.js`") đã *pass* trong khi landing vẫn tải **671 kB ECharts**. Tiêu chí mới, cả hai phải đạt: `grep -c vendor-charts dist/index.html` = **0**, và tổng gzip các asset được `modulepreload` trong `index.html` **≤ 120 kB** |
| T4 | Chạy lại: Offline + Search trong `UsersTableView` phải hiện lỗi rõ ràng |
| T7 | Chạy lại sau khi đặt cap dương (B-M10) — phải có cảnh báo truncation |
| **S-M3 e2e** | USER thường → **403** tại `/actuator/metrics`; `/actuator/health` → **200** |
| **Chốt tên metric alert** | `curl -s localhost:8080/actuator/prometheus \| grep -E 'security_events\|queue_fresh'` — lấy **tên thật** từ output rồi mới chốt `expr`. Đoán tên là alert im lặng, mà im lặng đúng là thứ H17 đang chống |
| **Đo leak-vs-spike (H14)** | `docker stats` 60s sau request file lớn: heap tụt về mức nền (spike) hay không tụt (leak). Nếu leak → nâng mức độ phát hiện |
| **Đo lại H13 kèm biến thể XFF** | Theo tiêu chí đã chốt: key sai trùng prefix **kèm `X-Forwarded-For` xoay vòng mỗi request** vẫn phải chạm 429 |

---

## PHẦN C — Reviewer đã làm rồi. **ĐỪNG SỬA LẠI, ĐỪNG SỬA NGƯỢC.**

Đã chạy và verify sau **build sạch** (xoá `target/classes`, `target/test-classes`, `target/surefire-reports`): backend **1.033 test · 9 skipped · 0 error · 0 failure**; frontend `vue-tsc` sạch, **533/533 test**, build OK.

| # | File | Thay đổi | Nghiệm thu đã đạt |
|---|---|---|---|
| C1 | `pom.xml` | Thêm `io.micrometer:micrometer-registry-prometheus` scope `runtime` | `target/app.jar` giờ chứa `micrometer-registry-prometheus-1.16.5.jar` + 6 `prometheus-metrics-*`. Trước đó `/actuator/prometheus` **không tồn tại** → alert H17/B-L8 không có gì để scrape |
| C2 | `application-prod.yaml` | `management.metrics.export.prometheus.*` → `management.prometheus.metrics.export.enabled` | Khoá cũ là cú pháp Boot 2; project ở Boot **4.0.6** nên nó bị bỏ qua âm thầm — đọc như "Prometheus đã bật" trong khi endpoint chưa bao giờ được bật |
| C3 | `vibegraph-web/vite.config.ts` | Bỏ echarts khỏi `manualChunks`; siết `sigma`/`graphology` thành `/node_modules\/(sigma\|graphology)/` | `vendor-charts` **biến mất khỏi `index.html` (0)** và khỏi entry chunk (0). ECharts về `DashboardView-*.js` (lazy admin). Landing: **~289 kB → ~98 kB gzip** |
| C4 | `Dockerfile:11` | `eclipse-temurin:21-jre-alpine` → `21.0.5_11-jre-alpine` | D-M2 bạn báo ✅ hoàn tất nhưng **image runtime của backend** — cái thật sự chạy production — vẫn là tag nổi. Giờ mới thực sự xong |
| C5 | `SourceFileServiceImpl.java` | Thêm `redactPreservingLineCount()`; `redactPrivateKeyBlocks` dùng nó | H15 gộp block N dòng thành **1 dòng** `[REDACTED]`, còn vòng lặp tăng `lastIncludedLine` theo **dòng output** (`:149` init `start-1`, `:162` `++`) → `endLine` trả về **thiếu đúng số dòng key chiếm**. MCP client phân trang theo giá trị đó. Giữ nguyên 3 ca biên của bạn (`PRIVATE_KEY_BLOCK` / `OPEN_ENDED` / `ORPHAN_END`) |

### C6 — Rate-limit 2 tầng: **ĐÃ XONG**, đừng sửa ngược

**File: `RateLimitFilter.java`, `SecurityConfig.java`, `RateLimitFilterTest.java`.**

Đã hoàn tất và verify: **1.033 test · 9 skipped · 0 error · 0 failure** (build sạch).

Lý do phải sửa (regression do H13 tạo ra, đã kiểm chứng):

```java
// RateLimitFilter.java:182-189 — cả hai nguồn danh tính đều do filter chạy SAU nó nạp
Authentication authentication = SecurityContextHolder.getContext().getAuthentication();  // JwtAuthFilter:106 set
String apiKeyRef = request.getAttribute(ApiKeyAuthFilter.API_KEY_REF_ATTRIBUTE) ...      // ApiKeyAuthFilter:147 set
```

`SecurityConfig:196` đặt rate-limit **trước** `jwtAuthFilter:197` và `apiKeyAuthFilter:198`. `StatelessSessionCookieFilter` (chạy trước, `:183`) **không** nạp SecurityContext — đã kiểm, nó chỉ `filterChain.doFilter` đi qua. Hệ quả:

- `requestsPerMinutePerUser` — **không còn hiệu lực** (`userId` luôn null)
- `requestsPerMinutePerApiKey` — **không còn hiệu lực** (`apiKeyRef` luôn null)
- `eventService.record(null, null, ip, ...)` (`:133`) → telemetry mất **toàn bộ** quy chiếu user/API-key; admin security monitor hết xem được theo user

Nghĩa là H13 đã đóng lỗ DoS-CPU nhưng **mở giới hạn tần suất per-user và per-API-key**. Với API key dùng cho MCP/CLI, đó là mất hạn mức của kênh gọi nhiều nhất.

**Đính chính của reviewer:** trước đó reviewer nói *"`RateLimitFilterTest` có 0 tham chiếu tới perUser/apiKeyRef/SecurityContextHolder"* — **sai**, do pattern grep hụt. Test `consume_apiKeyLimit_enforcedIndependently` **có** phủ bucket API key. Khi tách 2 tầng, đúng 3 test đó fail, và đó là cách regression lộ ra. Điều vẫn đúng: **không có test nào phủ bucket per-user**, và không test nào assert `eventService.record` nhận `userId` khác null — nên nửa telemetry của regression thì thật sự không ai bắt được.

**Đã sửa như sau:**

- `RateLimitFilter.Stage` enum: `EDGE` giữ nguyên vị trí trước auth, chỉ bucket IP (đúng mục tiêu H13); `IDENTITY` là instance thứ hai lo bucket user + key.
- `SecurityConfig`: `identityRateLimitFilter` được `addFilterBefore(..., AuthorizationFilter.class)` — tức sau `jwtAuthFilter`/`apiKeyAuthFilter`. **Chọn anchor `AuthorizationFilter` chứ không phải `ApiKeyAuthFilter`** vì Spring Security 7 chỉ nhận filter của chính nó làm anchor (đúng ràng buộc bạn đã phát hiện khi làm H13), và `AuthorizationFilter` là đúng vị trí limiter từng đứng nên chắc chắn assemble được.
- Telemetry: `safeRecord` giờ phân giải principal **tại thời điểm ghi** (trong `finally`, sau khi chain nội đã chạy) thay vì lúc vào filter → `userId`/`apiKeyRef` không còn null. Có cờ `RECORDED_ATTRIBUTE` chống ghi trùng khi tầng IDENTITY đã ghi `RATE_LIMIT`.
- Metric: counter (`rate_limit.requests.rejected`, …) **giữ nguyên tên không tag** để dashboard/alert hiện có không vỡ; chỉ gauge `rate_limit.windows.size` được tag `stage` vì mỗi tầng có cache riêng.
- 3 test mới: `identityStage_perUserLimit_enforcedAcrossIps` (bucket user, các IP khác nhau), `edgeStage_recordsResolvedPrincipal_notNull` (`verify(eventService).record(eq(userId), eq("key-9"), …)`), `edgeStage_authenticatedUserOverLimit_isNotRejectedThere` (chốt rằng tầng EDGE **không** giữ bucket identity, để lần sau ai dời về đó là test kêu).

**Chưa verify được, cần stack sống của bạn (thêm vào B5):** không có test surefire nào dựng web security filter chain (các `@SpringBootTest` phủ chain đều là `*IT.java`, bị surefire loại trừ). Nên **thứ tự filter mới chỉ được chứng minh ở mức unit + compile, chưa chứng minh khi app boot thật**. Khi bật stack, kiểm 3 điều:
1. App boot không lỗi `does not have a registered order`.
2. `GET /actuator/health` → 200; USER thường `GET /actuator/metrics` → 403.
3. Vượt `requests-per-minute-per-user` bằng **JWT của một user qua nhiều IP khác nhau** → phải nhận **429** (trước khi sửa: không bao giờ 429).

---

## PHẦN D — Luật cứng cho phiên tới

1. **Không chạm** `RateLimitFilter.java`, `SecurityConfig.java` (C6), và 5 file ở C1–C5.
2. **Mọi số liệu tự báo phải đo lại bằng lệnh trước khi ghi vào tài liệu.** Con số dẫn xuất hoặc nhớ lại từ báo cáo khác thì đánh dấu `[chưa xác minh lại]`. Sự cố 2.821/2.681 là ví dụ: nó được trình bày là "đo lại" trong khi hai file không hề bị sửa.
3. **Không commit.** Working tree hiện có ~92 file thay đổi chờ chủ repo xem.
4. Điểm nào tài liệu trái dữ liệu thật thì **dừng và báo**, như bạn đã làm với S-M5. Đó là hành xử đúng, giữ nguyên.
5. Khi tuyên bố một hạng mục "đã xong/đã sạch", nêu rõ **lệnh nào** chứng minh và **lệnh đó không nhìn thấy được cái gì**. (D-M2 báo ✅ trong khi image runtime chưa pin là ví dụ: lệnh kiểm chỉ soi builder stage.)

## Trạng thái Đợt 2/3

Còn **1 việc chặn**: B1 (Content-Length pre-check) — của bạn. C6 (rate-limit 2 tầng) đã xong. Sau B1, B5 là bước nghiệm thu cuối để chốt Đợt 2/3 — nhớ gồm 3 kiểm tra runtime của C6.
