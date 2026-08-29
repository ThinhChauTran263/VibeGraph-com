# BÁO CÁO PHIÊN TIẾP NỐI — B-M2 cụm rẻ · T2 TLS · T6 · update/graph/ (13/08/2026)

- **Phạm vi phiên:** 4 việc operator giao (a) sửa câu chữ Đ7-4, (b) test cụm rẻ B-M2 + đo lại BRANCH, (c) nghiệm thu TLS cục bộ cho T2, (d) giải thích `update/graph/` — cộng quyết định T6 được ủy quyền.
- **Kế thừa:** `EXECUTION-REPORT-DOT4-7-2026-08-13.md` (Đợt 4–7). File này chỉ ghi phần làm thêm sau báo cáo đó.
- **Quy ước:** mọi số là output thật của lệnh đã chạy trong phiên, dán nguyên văn hoặc ghi rõ lệnh. Không có số ước lượng.

---

## 1. Kết quả từng việc

### (d) `update/graph/` — đã xác định nguồn gốc

**Không phải rác, không thuộc EXEC nào — là handoff của agent khác.** `update/graph/README.md` ghi nguyên văn: *"Investigator: Claude Opus 5 (session ended at quota limit — this folder is the handoff)"*, timestamp 8 file từ 20:51 đến 20:57 ngày 13/08. Nội dung: nghiên cứu có bằng chứng về lỗi "dính node" khi zoom (3 tầng nguyên nhân: `graphology-noverlap` nhận pixel nhưng tính theo graph units; `ZOOM_SIZE_POWER = 0.75` làm overlap tệ nhất khi zoom out; không có zoom clamp) + `06-IMPORT-PERFORMANCE.md` về tốc độ import, kèm `05-IMPLEMENTATION-PLAN.md`.
**Lệnh kiểm:** `Get-ChildItem update\graph -Recurse` → 8 file `.md`, đọc `README.md`.
**Khuyến nghị:** commit — tài liệu công việc hợp lệ; không xóa.

### (a) Câu chữ Đ7-4 — đã sửa trong EXECUTION-REPORT

Dòng cũ *"việc sửa không thuộc phiên này (EXEC chỉ giao bước đo)"* dễ đọc thành "đóng vĩnh viễn" như Đ7-1. Đã thay bằng: **"Trạng thái quyết định: ĐO XONG, CHƯA AI QUYẾT có làm tiếp hay không"** — nêu rõ Đ7-1 có quyết định DỪNG (QĐ #3) còn Đ7-4 chỉ mới hoàn tất bước đo, Đ7-4b→4e chưa từng bị từ chối; kèm khuyến nghị "nên làm" (k đo thật 3.4–3.5) và rủi ro số 1 (mất node cô lập, tiêu chí Đ7-4d viết sẵn).

### (b) B-M2 cụm rẻ — 31 test mới, BRANCH đo thật 2 lần

File mới: `src/test/java/com/vibegraph/diagram/service/impl/UseCaseInferenceEngineHelperTest.java` (31 test hành vi: input→output theo đúng javadoc engine; reflection chỉ dùng để chạm private record `Endpoint`/`DomainGuess` và các hàm thuần private — mọi assertion đều là hành vi, không đốt coverage suông).

| Chỉ số | Trước phiên | Sau phiên | Lệnh đo |
|---|---|---|---|
| BRANCH (cột $6/$7) | 69.7% (544/780, missed 236) | **75.9% (592/780, missed 188)** | `mvnw -DskipITs test` rồi awk `jacoco.csv`, xóa surefire-reports trước mỗi lần |
| LINE (cột $8/$9) | 86.6% (589/680) | **90.6% (616/680)** | như trên |
| Suite backend | 1031/0/0/1 | **1062/0/0/1** | summary Maven nguyên văn |
| Dòng `nc` trong class | 77 | **52** — cụm string helpers 1270–1396 sạch hoàn toàn | jacoco HTML |

**Trung thực về quá trình:** 2 test đầu viết sai do kỳ vọng của tôi sai hành vi thật — `"ies"` (length 3) rơi vào nhánh s-thường cho `"ie"`; `"Quizzes"` bỏ `"es"` cho `"Quizz"` (không gấp đôi phụ âm). Đã sửa test ghim đúng hành vi thật, ghi chú *"pins the real behaviour, not ideal English"*. Không sửa production code.

**Gate chưa đạt: cần thêm 32 branch (missed 188 → ≤156).** Phần thiếu nằm trọn ở cụm đắt: 373–486 (`mergeDuplicateNamedUseCases`/`disambiguateScopedDuplicates` — cần fixture graph tạo 2 use case trùng tên cùng scope), 260/264 (`useCaseServices`), và vài dòng rải rác 565–989. Chưa tách class — gate là điều kiện cứng của EXEC-2 §1.1.

### (c) T2 — PASS, kèm phát hiện thật

Dựng nginx TLS throwaway: cert self-signed (`openssl req -x509` trong container alpine), conf `.vibegraph/tls/nginx-t2.conf`, port 8443, network `vibegraph_default`, proxy → `vibegraph-backend:8080`. Đo 3 bước:

1. **Lần 1 — KHÔNG set `X-Forwarded-Proto`: cookie KHÔNG có cờ Secure dù request qua HTTPS thật.** Nguyên nhân đo được: backend leg plaintext nên `request.isSecure()=false`; `printenv AUTH_COOKIE_SECURE` trong container = **`false`** (`.env` đang override default `:-true` của compose). Đây chính là khoảng trống T2 chưa ai verify trước đây.
2. **Lần 2 — proxy set `X-Forwarded-Proto: https`: cả `vg_session` và `vg_refresh` CÓ cờ Secure.** Nhánh này là code có sẵn ở 3 nơi (`AuthCookieService:78–84`, `StatelessSessionCookieFilter:57–63`, `HttpCookieOAuth2AuthorizationRequestRepository:102–108`) — **không sửa dòng code production nào trong phiên này**.
3. **Roundtrip:** gửi lại cookie Secure qua HTTPS gọi `GET /api/projects` → **HTTP 200** (cookie hoạt động thật, không chỉ cờ đúng).

Đã dọn container proxy sau drill (`docker rm -f vg-t2-tls-proxy`), stack còn đúng 3 container healthy.

### T6 — Quyết định: GIỮ file 200MB (được operator ủy quyền quyết)

Cân nhắc hai phía như operator yêu cầu:
- **Rủi ro vận hành nếu GIỮ = 0** (operator đã tự verify: `readAllLines` chỉ khi có request đọc; `getFileSlice` chỉ khi file bị sửa; `grep '@Scheduled'` trong watcher rỗng — không job nền quét workspace).
- **Cái mất nếu XÓA:** file là **bằng chứng duy nhất** tái hiện được pha đo trước sửa (+211,6 MiB heap/request — nền tảng của phát hiện H14 và bản vá `Files.size()`). Nếu sau này cần audit lại hành vi OOM-kill đã ghi nhận, không còn gì để đo lại. Xóa là bất thuận nghịch, không khẩn.
→ **GIỮ** cho tới khi có lý do đủ lớn để đánh đổi bằng chứng (ví dụ đã hoàn tất chương đánh giá/không còn tranh chấp tiềm tàng).

---

## 2. Ý tưởng & định hướng đề xuất

### 2.1. B-M2 — cách đạt 32 branch còn lại mà không rơi vào bẫy "coverage xanh, bảo vệ rỗng"

- Cụm 373–486 chỉ chạm được qua `infer()` với **fixture graph dựng tay**: 2 endpoint beautify về cùng tên cùng scope (kích hoạt merge), 1 cặp admin/non-admin cùng tên khác scope (kích hoạt disambiguate "(All)"/"(Own)"), và INJECTS edge để phủ nhánh `useCaseServices` 260/264. Đây là 3 fixture nhỏ, không phải hạ tầng lớn — ước lượng 1 phiên làm được, nhưng **mỗi fixture phải assert đầu ra quan hệ/actor, không chỉ assert "chạy không lỗi"**.
- **Khi tách class (chỉ sau khi gate đạt):** chuyển các helper thuần (`singularize`, `pluralizeWord`, `pascal`, `uniqueId`, `stripTechWords`, `stripLeadingRoleWords`, `roleToActorName`) thành **package-private trong class mới** (vd `UseCaseNameNormalizer`). Lúc đó 31 test hiện tại bỏ reflection, gọi trực tiếp — test nhanh hơn, stack trace rõ hơn, và class mới tự nó đã là bước tách đầu tiên có test bảo vệ sẵn.
- Hai nhánh gần như chết đã phát hiện khi viết test: `singularizeWords` line 1292 (`parts.length == 0` không bao giờ xảy ra vì `"".split()` trả `[""]`) và `roleToActorName` line 1256 (`split("\\s+")` không sinh phần tử blank). Khi tách, xóa được thì xóa — nhưng chỉ sau khi gate đạt và có test phủ quanh.

### 2.2. T2 — biến drill thành tài sản tái chạy được, và 2 việc củng cố

- **Commit drill config** vào `scripts/drills/` (nginx-t2.conf + hướng dẫn 5 dòng). Lần này drill nằm ở `.vibegraph/tls/` (gitignore) — lần sau ai muốn verify lại sau một thay đổi cookie/TLS phải dựng lại từ đầu. Commit conf biến T2 thành phép đo 2 phút bất cứ lúc nào.
- **Thêm unit test cho nhánh secure của `AuthCookieService`** — hiện `AuthCookieServiceTest` có **0** test phủ `isSecure()/X-Forwarded-Proto` (grep trong phiên: 0 hit). Ba cấu hình cần pin: (1) config true → Secure bất kể request; (2) config false + `X-Forwarded-Proto: https` → Secure; (3) config false + HTTP thường → không Secure. Đây chính là 3 trạng thái drill vừa đo, đưa vào test để khỏi cần drill mỗi lần.
- **Định hướng dài hơn (chưa làm, cần quyết định):** nhánh `X-Forwarded-Proto` hiện được tin **không qua trust-proxy gate** (3 nơi đọc trực tiếp header). Không phải lỗ hổng (hậu quả tối đa: cookie Secure trên HTTP = phiên tự vô hiệu, không lộ), nhưng cùng họ với S-M2 — nếu sau này bật trust proxy thật, nên gate header này cùng chỗ với `ClientAddressResolver` để nhất quán "chỉ tin proxy thật".

### 2.3. `update/graph/` — giao điểm với việc đang có

- `06-IMPORT-PERFORMANCE.md` của handoff đó **liên quan trực tiếp Đ7-1** (parse chiếm bao nhiêu thời gian import) — phiên này ta đo được ~98% cửa sổ parse+upsert; nên đối chiếu số của họ với số ta đo trước khi ai đó làm Đ7-1d, tránh hai nguồn số lệch.
- Ground rule số 1 của họ — *"Do not trust the comments in runtimeConfig.ts"* — đáng lưu ý cho F-M6/F-L3: nếu chạm `runtimeConfig.ts` ở phiên sau, kiểm bằng code chứ không bằng comment.
- Định hướng: **không gộp** handoff đó vào bộ tài liệu Qwen (nguồn khác, quy ước khác); chỉ thêm 1 dòng tham chiếu trong báo cáo phiên kế tiếp nếu công việc đụng nhau.

### 2.4. Thứ tự đề xuất cho phiên kế tiếp

1. **32 branch fixture của B-M2** (đã định nghĩa sẵn ở 2.1) → đo lại BRANCH → chỉ tách khi missed ≤ 156.
2. **Tách `DashboardView.vue`** (F-M6, đã đủ gate 71.52% ≥ 70) — nhớ nghiệm thu 6-F3/6-F4: `lines.pct` tổng ≥ baseline, tổng byte dist chênh ≤ 3% so mốc 1.569.955 B, chunk DashboardView giảm từ mốc 582.351 B.
3. **Commit drill T2 vào `scripts/drills/`** (việc 5 phút).
4. Việc operator còn giữ: xoay secret (chảy máu thật), registry CD, volume drill `vibegraph-restore-neo4j-drill`, quyết định Đ7-4b.

---

## 3. Trạng thái cuối phiên (đo thật)

| Chỉ số | Giá trị |
|---|---|
| Backend suite | `Tests run: 1062, Failures: 0, Errors: 0, Skipped: 1` · BUILD SUCCESS |
| `UseCaseInferenceEngine` | BRANCH 75.9% (missed 188) · LINE 90.6% — **gate ≤156 chưa đạt, chưa tách** |
| T2 e2e | PASS qua HTTPS thật — Secure flag trên cả 2 cookie + roundtrip 200 (nhánh `X-Forwarded-Proto`) |
| T6 | GIỮ file 200MB — quyết định bằng chứng, ghi trong báo cáo này |
| Stack docker | 3 container healthy (backend/postgres/neo4j); proxy TLS drill đã dọn |
| Code production thay đổi trong phiên | **0 dòng** — chỉ thêm 1 file test |

---

*Báo cáo soạn 13/08/2026 theo lệnh operator ("viết báo cáo đi, có ý tưởng/định hướng thì nêu luôn"). Các phiên sau mở file mới, không sửa file này.*
