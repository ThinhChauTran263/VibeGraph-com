# BẢNG SO SÁNH TRƯỚC / SAU NÂNG CẤP — toàn diện (14/08/2026)

**Phạm vi:** toàn bộ chuỗi nâng cấp theo `AUDIT-REPORT.md` (76 phát hiện) + EXEC Đợt 4–7 + phiên 14/08.
**Nguyên tắc:** mọi ô "trước/sau" đều là số đã đo, kèm nguồn bằng chứng (file log/báo cáo/lệnh). Ô nào không có số trước thì ghi rõ "không đo trước" — KHÔNG suy đoán.

---

## 1. Hiệu năng

| Hạng mục | TRƯỚC | SAU | Bằng chứng |
|---|---|---|---|
| `/api/projects/{id}/graph?mode=deep` cold (project 16.010 node) | **5.072s** | **1.334s** (≈3.8×) | `d74-api-before/after.ps1`, 5 lần đo mỗi bên, report DOT-4ITEMS §6 |
| Cùng endpoint, project thứ hai | 1.734s | 0.778s (≈2.2×) | như trên |
| Cùng endpoint, median cached | 0.101s / 0.071s | 0.102s / 0.077s (≈ nhau — cache 5 phút) | như trên |
| Số dòng Neo4j truyền (query cũ OPTIONAL MATCH vs 2 query mới) | 42.057 dòng | 47.505 dòng (**+13%**) | cypher-shell đo thật; ghi thẳng: tách query giảm **độ trễ** chứ không giảm dòng |
| Chunk `DashboardView-*.js` | **582.351 B** | **21.10 kB** (−96%) | `npm run build` 14/08; echarts sang chunk async riêng 561.43 kB |
| Tổng `dist/assets/*.js` | 1.569.955 B | 1.570.653 B (**+0.027%**, trong hạn mức ≤3%) | build trước/sau các lần tách FE |
| Import archive (parse+upsert) | — | **không đổi** (đo 98.2–98.7% cửa sổ; DỪNG song song hoá theo QĐ#3) | EXECUTION-REPORT §5, bảng Đ7-1 |

## 2. Bảo mật

| Hạng mục | TRƯỚC | SAU | Bằng chứng |
|---|---|---|---|
| `npm audit` frontend | **8 advisory** (1 critical `websocket-driver`, 6 high, 1 moderate) | **0 vulnerabilities** (đo lại 14/08: "found 0 vulnerabilities") | AUDIT-REPORT L265-270; FINAL-REPORT-DOT2-3 L13; `npm audit` chạy lại 14/08 |
| Đọc file workspace (`readRange`) | `readAllLines` → +211.6 MiB heap/request (đo thật, OOM-kill) | `Files.size()` pre-check + `ArchiveUploadLimitFilter` dùng **remainingBytes** (2-zero phân biệt ENTERPRISE vs hết quota) | đo pha trước sửa trong EXEC; code + test `ArchiveUploadLimitFilterTest` |
| Actuator | hở | ADMIN-only (S-M3) | EXECUTION-REPORT; nghiệm thu profile |
| Tìm kiếm (Lucene injection) | chưa escape | escape (S-M4) | EXECUTION-REPORT |
| Cookie `Secure` sau TLS edge | **chưa ai verify** (`.env` AUTH_COOKIE_SECURE=false, backend leg plaintext) | drill T2 PASS: có `X-Forwarded-Proto: https` → cả 2 cookie Secure + roundtrip 200 | SESSION-REPORT-BM2-T2 §1c; drill tái chạy được tại `scripts/drills/` |
| CORS | nhiều nguồn | gom 1 nguồn + guard startup chặn `"*"` (B-L5), test T8 lặp lại | EXECUTION-REPORT Lô B |
| Rate limit / JWT / payload cap (B-M10) | theo phát hiện audit | đã vá các đợt trước, giữ nguyên nghiệm thu | IMPLEMENTATION-REPORT-DOT2-3 / EXECUTION-REPORT |
| **Secret production trong `.env`** | **đã lộ** (7 secret) | **CHƯA XOAY** 🔴 — việc operator, không phải code | AUDIT-REPORT S1; ghi thẳng, không tô hồng |

## 3. Chất lượng / kiểm thử

| Hạng mục | TRƯỚC | SAU | Bằng chứng |
|---|---|---|---|
| Backend unit suite | 1.008 (chốt Đợt 1) → 1.031 (chốt Đợt 4–7) | **1.067 / 0 fail / 0 error / 1 skip** | summary Maven nguyên văn, surefire-reports xóa trước mỗi lần đo |
| Backend IT (Neo4j thật, Testcontainers) | — | **19/19** (`Neo4jGraphRepositoryIT`, chạy trên code 2-query mới) | failsafe log 14/08 |
| Frontend suite | 538 test / 65 file (đầu 14/08) | **570 test / 67 file** | vitest log |
| BRANCH `UseCaseInferenceEngine` | 69.7% (missed 236) | gate **80.5%** (missed 152) → cluster sau tách **80.8% (629/778)** | awk jacoco.csv cột $6/$7, 3 lần đo |
| LINE `UseCaseInferenceEngine` | 86.6% | 96.8% | như trên |
| Coverage `UserDetailDrawer.vue` | 65.00% | **94.49%** (223/236) | coverage-summary, full suite |
| Coverage `DashboardView.vue` | 71.52% | 72.65% + `dashboard-transforms` 100% + `dashboard-echarts` (async) | coverage-summary |
| Coverage `UserApiKeyList.vue` (mới) | — | **100% (25/25)** | coverage-summary |
| Accuracy eval harness (P/R/F1) | 1.00 / 1.00 / 1.00 (tp 3/7/8) | **IDENTICAL** sau tách (Compare-Object 18 dòng report) | `bm2-accuracy-before/after.log` |

## 4. Cấu trúc code (độ phức tạp)

| Hạng mục | TRƯỚC | SAU | Bằng chứng |
|---|---|---|---|
| `UseCaseInferenceEngine.java` | 1.398 dòng | **387 dòng** + 6 collaborator (130–340 dòng mỗi file) | `Measure-Object` 14/08 |
| `DashboardView.vue` | 1.525 dòng | 1.423 dòng + 2 module tách | như trên |
| `UserDetailDrawer.vue` | 3.201 dòng | **2.922 dòng** + `UserApiKeyList.vue` 286 dòng | như trên |
| Code chết (B-L3/4/9/10/11) | còn | đã xóa, verify `find` = 0 | EXECUTION-REPORT §3-4 |

## 5. Vận hành / độ tin cậy

| Hạng mục | TRƯỚC | SAU | Bằng chứng |
|---|---|---|---|
| Backup control+data plane | chưa diễn tập | **31.3s**, output nguyên văn | EXECUTION-REPORT §6 |
| Restore drill volume mới | chưa | **9.3s PASS**; Neo4j `count(n)` = **56.724 = 56.724** | như trên |
| Scale backend=2 | không rõ giới hạn | **fail đúng thiết kế** (exit 1, lỗi nguyên văn) + DEPLOYMENT.md "Single-replica only" | EXECUTION-REPORT §7 |
| Postgres image | floating tag | pin `postgres:16.11-alpine` cả 2 compose | grep compose |
| ERD vs migration | lệch tên bảng | sửa theo migration thật (`refresh_sessions`, `audit_logs`) | EXECUTION-REPORT lệch #7 |
| UI verify trình duyệt (14/08) | chưa từng | landing/admin/dashboard(21 users, 4 chart)/graph 2.495 node/drawer — render đúng, console sạch (1 deprecation notice của sockjs-client dev-dep, 0 lỗi app); BEFORE/AFTER tách drawer **giống hệt** | Chrome DevTools MCP, snapshot + screenshot |

## 6. Những thứ KHÔNG đổi / DỪNG có quyết định (nói thẳng)

| Hạng mục | Trạng thái | Lý do |
|---|---|---|
| Song song hoá parse (Đ7-1d–1f) | DỪNG | QĐ#3: chỉ đo; cửa sổ parse+upsert 98% nhưng chưa tách được parse thuần khỏi upsert — lợi ích chưa chứng minh, rủi ro pipeline cao |
| Xoay 7 secret | **CHƯA** 🔴 | ngoài tầm code; việc operator |
| Commit + `gitnexus analyze` | CHƯA | luật cứng: reviewer/operator commit |
| Volume `vibegraph-restore-neo4j-drill`, registry CD | CHƯA | việc operator |

---

**Đọc bảng thế nào:** cột hiệu năng có 1 ô "đi lùi" cố ý giữ nguyên (+13% dòng truyền) vì đó là sự thật đo được của đánh đổi Đ7-4 — đổi dòng lấy độ trễ (cold −3.8×). Mọi ô còn lại đi đúng hướng hoặc giữ nguyên có chủ đích. Không có ô nào điền bằng suy đoán.

*Bảng soạn 14/08/2026 từ các báo cáo đo cùng chuỗi phiên; các phiên sau mở file mới.*
