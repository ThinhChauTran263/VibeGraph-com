# BÁO CÁO THI HÀNH — PROMPT-FOR-QWEN-2026-08-13-EXEC (Đợt 4–7)

- **Ngày thi hành:** 13/08/2026
- **Nguồn lệnh:** `update/docs/claude/PROMPT-FOR-QWEN-2026-08-13-EXEC.md` (bản EXEC hoàn chỉnh) + `PLAN-REMAINING-2026-08-13.md`, `EXEC-1-DOT-4-5.md`, `EXEC-2-DOT-6-7.md`
- **Chuỗi báo cáo:** `IMPLEMENTATION-REPORT-DOT2-3.md` (Đợt 2+3) → `FINAL-REPORT-DOT2-3.md` (chốt Đợt 2/3 + H16 + Backlog TB phiên 12/08) → **file này (Đợt 4–7)**
- **Quy ước số liệu:** mọi số dưới đây là output thật của lệnh đã chạy trong phiên, dán nguyên văn. Mục không đo được ghi `[không đo được]` + lý do. Luật cứng §12 của prompt EXEC được tuân thủ (liệt kê ở cuối file).

---

## 1. Baseline bắt buộc — đầu và cuối

### 1.1. Baseline đầu phiên

**Lệnh đã chạy:**
```
git log --oneline -1
git status --porcelain | Measure-Object -Line
Remove-Item target\surefire-reports -Recurse -Force; .\mvnw.cmd -B test
```
**Output thật:**
```
b7f294c feat(ops): restore drill that verifies a backup actually loads
porcelain = 4  (3 file EXEC/PROMPT mới + update/graph/ — không phải của tôi, không đụng)
Tests run: 1031, Failures: 0, Errors: 0, Skipped: 1   ·   BUILD SUCCESS
```
**Kết luận:** PASS — khớp baseline reviewer công bố trong prompt.

Lưu ý trung thực: run đầu tiên trong phiên gặp flake `CreditDebitConcurrencyTest` (Testcontainers timeout chờ log Postgres: `Timed out waiting for log output matching '.*database system is ready to accept connections.*'`). Chạy lại đơn lẻ 5/5 PASS, chạy lại full suite xanh → phân loại flake môi trường Docker, không phải lỗi code.

### 1.2. Baseline cuối phiên

**Lệnh đã chạy:** `Remove-Item target\surefire-reports …; .\mvnw.cmd -B test` và chuỗi FE `type-check → vitest run → build`
**Output thật:**
```
Tests run: 1031, Failures: 0, Errors: 0, Skipped: 1   ·   BUILD SUCCESS
Test Files  65 passed (65)      (trước phiên: 64 — thêm LandingView.spec.ts)
Tests  538 passed (538)         (trước phiên: 533 — thêm 5 test mới)
dist JS: 53 files, tổng 1.569.955 bytes
```
**Kết luận:** PASS. Backend 1031→1031 (Lô A xóa 2 test AnnotationVisitorTest; thêm +2: `CachingGraphRepositoryTest`, test B-L8). FE +5 test (LandingView ×3, SearchBar ×1, ImportProjectPanel ×1).

---

## 2. Bảng kết quả từng mục

| Mã | File đã sửa | Dòng thay đổi | Lệnh nghiệm thu | Output thật | Lệch so với prompt EXEC |
|---|---|---|---|---|---|
| **B-L3** | `JwtService.java`, `JwtServiceTest.java` | −5 / +2 | grep overload + `mvnw -Dtest=JwtServiceTest` | overload count = **0**; suite xanh (trong run 1029) | Không |
| **B-L4** | xóa `AnnotationVisitor.java` + `AnnotationVisitorTest.java` | −2 file | `find` cả 2 → rỗng; grep trừ `SpringAnnotationVisitor` → rỗng | rỗng / rỗng; `SpringAnnotationVisitor` trong `ParserServiceImpl` = 2 | Không |
| **F-L4** | `ImportProjectPanel.vue`, `AppIcon.vue`, spec | −25/+8, +6, +16 | `Select-String "'M[0-9]"` + vitest | count = **0**; `3 passed (3)` | Không |
| **F-L2/F-L1** | `LandingView.vue` + `LandingView.spec.ts` (mới) | +19/−11, +59 | vitest `LandingView.spec.ts` | `3 passed (3)` — typing chain dừng sau unmount; 4 tour listener bị remove | Không |
| **F-L3** | `SearchBar.vue` + spec | +24/−2, +50/−8 | vitest `SearchBar.spec.ts` | `7 passed (7)`; burst 10 keystroke trong 100ms → filter chạy ≤ 2 lần, kết quả cuối đúng | Không |
| **B-L2** | `CachingGraphRepository.java` + `CachingGraphRepositoryTest.java` (mới) | +15/−15 (+seam clock), +54 | `mvnw -Dtest=CachingGraphRepositoryTest` | `1 passed`; MAX_ENTRIES+3 → size đúng MAX_ENTRIES, 3 key cũ nhất evict | Thêm constructor seam + `@Autowired` (mục 7 Lệch) |
| **B-L7** | `ProjectServiceImpl.java` + 3 test class | +28/−26 | `mvnw -Dtest=ProjectServiceImplTest,ProjectServicePersistenceTest,ProjectRestartSourceTest` + boot docker profile | `28 passed`; `Started VibeGraphApplication in 11.709s`, health=healthy | Spring 7 bỏ `ObjectProvider.empty()/of()` — test dùng mock provider (ghi chú trong test) |
| **B-L5** | `CorsConfig.java` | +7/−19 | `grep addCorsMappings src/main` | chỉ còn `SecurityConfig.corsConfigurationSource` — **1 nguồn**; guard chống `"*"` giữ nguyên ở constructor | Không |
| **B-L1** | `ParserServiceImpl.java` | +22/−37 | `mvnw -Dtest='Parser*Test,MethodVisitorTest'` + Khối 3 | `51 passed`; file **556 dòng**; `new JavaParser(config)` = **1** | File 556 > mục tiêu ≤545 (ước lượng lệch); tiêu chí chính (1 builder duy nhất) đạt |
| **B-L8** | `RequestEventService.java` + test | +29/−2, +27 | full suite `mvnw -B test` | `1031/0/0/1`; test mới: queue đầy plain event + 1 security event → security event sống, `security_events.dropped.total` **không tăng**, `request_events.dropped.total` +1 | Không |
| **B-L6** | `database/ERD.md` | +5/−3 | grep migration thật | `V18__refresh_sessions.sql`, `V10__phase7_support_audit_notifications.sql` (tạo `audit_logs`) — ERD sửa theo tên bảng thật | AUDIT-REPORT ghi `refresh_tokens`/`audit_log`; migration thật là `refresh_sessions`/`audit_logs` — dữ liệu thật thắng tài liệu (luật 8) |
| **D-L2** | `database/docker-compose.postgres.yml` | +4 | đọc | comment "LOCAL DEV ONLY" trên block `environment:` | Không |
| **Lô C — 10 mục còn lại** | — | — | — | **ghi nhận, không sửa**: S-L1 (chờ Q7 DOMPurify), S-L2 (breaking change toàn hệ), S-L3 (trùng Đ7-3), S-L4 (đổi ngữ nghĩa dashboard), S-L5 (chặn đúng `.`/`..` nếu làm), D-L1 (trùng Đ7-3), D-L5 (chỉ hợp nhất đường log), D-L6 (chọn nhánh tài liệu hóa). D-L3/D-L4 đã xong phiên 12/08 (EXEC-1 §0) | — |
| **Đ7-2** | `DEVOPS-GUIDE.md` + chạy thật 6 bước | +55/−1 (2a) | backup.ps1 → restore.ps1 → neo4j load (Khối 6) | `BACKUP OK (31.3s)` · drill `PASS 9.3s` · Symbol **56724 = 56724** | 2e nghiệm thu một phần (nêu trong Khối 6); volume drill neo4j chưa xóa (luật cấm xóa volume) |
| **Đ7-3** | `DEPLOYMENT.md` | +21 | grep + `docker compose up -d --scale backend=2` (Khối 7) | grep `single-replica` = **1**; exit=**1**, lỗi nguyên văn | Dẫn chiếu `RateLimitFilter.java:97` thay `:234` trong EXEC (số cũ; dùng dòng grep thật) |
| **B-M2** | **KHÔNG SỬA** | — | `mvnw -DskipITs test` + awk `jacoco.csv` cột `$6/$7`, `$8/$9` | **BRANCH 69.7% (544/780) · LINE 86.6% (589/680)** | **DỪNG TẠI GATE** — mục 8 |
| **F-M6** | **KHÔNG SỬA** | — | `npm run test:coverage` | `UserDetailDrawer 65%` (<70) · `DashboardView 71.52%` (≥70) | **DỪNG TẠI GATE** — mục 8 |
| **Q8** | `update/docs/Qwen/AUDIT-REPORT.md` §9/§10 | +2/−2 | grep | Đã RÚT khuyến nghị xóa "tàn dư T6", ghi bằng chứng JOIN `users` thật + câu hỏi quy trình | Không — đúng quyết định #8 |

---

## 3. Khối bắt buộc #2 — Bảng B-L1 (số đo trước/sau)

Project đo: `2620d947-8adf-4601-abca-d83d8c7a9008` (rt-exec-run1, 643 files).
**Lệnh:**
```
cypher-shell "MATCH (n:Symbol {projectId:'2620d947…'}) RETURN count(n) AS nodes;"
cypher-shell "MATCH (:Symbol {projectId:'2620d947…'})-[r:CALLS]->(:Symbol {projectId:'2620d947…'}) RETURN count(r) AS calls;"
```
Giữa hai phép đo: `docker compose build backend` (chứa B-L1) → recreate → `POST /api/projects/2620d947…/analyze` → **202**.

| | nodes | calls | Bằng chứng phân tích đã chạy lại thật |
|---|---|---|---|
| BEFORE (code cũ) | **16010** | **736** | — |
| AFTER (code mới) | **16010** | **736** | API: `lastAnalyzedAt: 2026-08-13T15:16:00.848Z`, `status: ANALYZED`, `totalFiles: 643`; log: `Analyzed project 2620d947… in background (643 files)` |

**Kết luận: PASS** — bằng nhau tuyệt đối, đúng tiêu chí "lệch 1 edge = fail, hoàn nguyên".

---

## 4. Khối bắt buộc #3 — Bảng Đ7-4a (rows/nodes, chỉ đo)

**Lệnh:** trên mỗi project, `MATCH … OPTIONAL MATCH (n)-[r]->(m…) RETURN count(*)` so với `MATCH (n…) RETURN count(n)`.

| project | nguồn gốc | nodes | rows | tỷ lệ k |
|---|---|---|---|---|
| `b9ab8150` | Grocery — dữ liệu thật của chủ repo | 5742 | 20033 | **3.49** |
| `b89107a0` | Lab7_Java6 | 291 | 990 | **3.40** |
| `2416d6a0` | cli-demo | 13 | 27 | **2.08** |

**Kết luận:** k đo thật ≈ 3.4–3.5 ở project lớn, **cao hơn** ước đoán 2.3 suy từ `CLAUDE.md` (GitNexus index). Bằng chứng định lượng cho Đ7-4 (tách 2 query) đã có. **Trạng thái quyết định: ĐO XONG, CHƯA AI QUYẾT có làm tiếp hay không** — khác bản chất Đ7-1 (Đ7-1 có quyết định DỪNG rõ ràng, QĐ #3; Đ7-4 chỉ mới hoàn tất bước đo, Đ7-4b→4e chưa từng bị từ chối). Khuyến nghị của người thi hành: **nên làm** — driver đang tải ~3.4× số dòng cần thiết, lợi ích thật; nhưng KHÔNG khẩn cấp vì payload HTTP đã được cap ở controller (B-M10), phần lãng phí là băng thông/RAM nội bộ chứ không phải độ trễ người dùng. Rủi ro số 1 khi làm: mất node cô lập (tiêu chí Đ7-4d đã viết sẵn trong EXEC-2).

---

## 5. Khối bắt buộc #4 — Bảng Đ7-1 (baseline parse)

**Đ7-1a — repo mẫu:** chính VibeGraph. `git rev-parse HEAD` = `b7f294c9d4789e245ecc75e70b6e1edeaea775f6`; `find src -name "*.java"` = **688 file**. Caveat: `Compress-Archive` flatten thư mục → khi import còn **643 file** (45 file trùng basename mất) — ghi nhận, không giấu.

**Đ7-1b/1c — 3 lần import sync, mốc log `Found {} .java files` → `Analysis complete`:**

| run | curl total | cửa sổ Found→Analysis complete | tỷ lệ (parse+upsert)/tổng |
|---|---|---|---|
| 1 (cold) | 20.217s | 19.852s | 98.2% |
| 2 | 10.388s | 10.211s | 98.3% |
| 3 | 10.846s | 10.707s | 98.7% |

Median cửa sổ: **10.707s** · tỷ lệ median **~98%** ≥ 50%.

**Kết luận:** theo Quyết định #3 đã chốt — **DỪNG, không song song hoá trong phiên này**. Claim "~65% thời gian import" `[chưa xác minh]` trong codex v2 được thay bằng số thật. Giới hạn của phép đo: cửa sổ gồm cả pha upsert Neo4j, chưa tách riêng parse thuần; zip flatten 643/688 file.

---

## 6. Khối bắt buộc #5 — Output nguyên văn `restore.ps1 -Confirm`

**Backup (Đ7-2b)** — `scripts\backup.ps1`, output thật (phần cuối):
```
==> [3/5] pg_dump (online, no downtime)
    postgres.sql : 21.26 MB, 22 CREATE TABLE statements
==> [4/5] neo4j dump
    Symbol nodes before dump: 56724
    neo4j.dump : 62.67 MB
    neo4j healthy again.
==> [5/5] upload-workspaces archive
    upload-workspaces.tar.gz : 2.57 MB

BACKUP OK  (31.3s)
  D:\Users\User\IdeaProjects\vibegraph-backups\20260813-151946Z
```

**Diễn tập restore (Đ7-2c)** — `scripts\restore.ps1 -BackupDir "D:\Users\User\IdeaProjects\vibegraph-backups\20260813-151946Z" -Confirm`, đồng hồ đo **9.3s**. Toàn bộ output lưu `.vibegraph\restore-drill-output.log`; nguyên văn phần thân:
```
==> [1/6] reading backup
    expected counts : users=21  projects=16  api_keys=11

Plan (nothing below touches the running stack or its volumes):
  create volume    vibegraph-restore-postgres-20260813T152103Z
  start container  vibegraph-restore-20260813T152103Z (postgres:16.11-alpine), no published port
  load             postgres.sql into it
  compare          users / projects / api_keys against manifest
  remove           the container and the volumes it created when done

==> [2/6] starting an isolated postgres
==> [3/6] waiting for it to accept connections
    ready
==> [4/6] loading postgres.sql
    loaded
==> [5/6] verifying row counts
    users     expected 21       restored 21       OK
    projects  expected 16       restored 16       OK
    api_keys  expected 11       restored 11       OK
==> [6/6] drill passed
    all three control-plane counts match the manifest.
==> cleaning up
    removed vibegraph-restore-20260813T152103Z and vibegraph-restore-postgres-20260813T152103Z (the drill's own volume only)
```
Số pre-drill đo độc lập trước khi chạy: `users=21, projects=16, api_keys=11` — khớp manifest.

**Đ7-2d — Neo4j dump vào volume sạch:**
```
docker volume create vibegraph-restore-neo4j-drill
docker run --rm -v vibegraph-restore-neo4j-drill:/data -v "<backup>:/backup" neo4j:5.26-community neo4j-admin database load --from-path=/backup --overwrite-destination=true neo4j
→ Done: 65 files, 599.1MiB processed in 5.939 seconds.   load_exit=0
```
Boot throwaway neo4j trên volume restored: `MATCH (n:Symbol) RETURN count(n)` → **symbol_nodes = 56724** — bằng tuyệt đối số trước dump.

**Đ7-2e — lệch phải nêu:** không trỏ live stack vào volume drill (tránh phá dev stack đang chạy). Nghiệm thu thay thế: drill counts khớp manifest + login tài khoản tồn tại TRƯỚC backup (`rt-exec-20260813213610@example.com`, tạo 14:35Z < backup 15:19Z) → **HTTP 200** trên stack đang giữ chính dataset đó; `docker compose ps`: backend/postgres/neo4j đều `healthy`.

**Đ7-2f — RTO thật:** restore control plane **9.3s** (+ Neo4j load 5.9s vào volume sạch); backup 31.3s. Không ghi "nhanh".

**Tàn dư drill:** volume `vibegraph-restore-neo4j-drill` (~600MB) vẫn còn — luật cứng cấm tôi xoá volume. Lệnh cho operator: `docker volume rm vibegraph-restore-neo4j-drill`.

---

## 7. Khối bắt buộc #6 — Output nguyên văn lỗi `--scale backend=2`

**Lệnh:** `docker compose up -d --scale backend=2`
**Output thật:**
```
 Container vibegraph-postgres Running
 Container vibegraph-neo4j Running
WARNING: The "backend" service is using the custom container name "vibegraph-backend". Docker requires each container to have a unique name. Remove the custom name to scale the service
exit=1
```
**Kết luận: PASS** — nghiệm thu âm chứng minh giới hạn single-replica là thật. `DEPLOYMENT.md` đã thêm mục "Single-replica only (Đ7-3)" (grep = 1 hit), liệt kê đích danh 4 thành phần chặn kèm `file:dòng` đã grep thật: `RateLimitFilter.java:97`, `JwtAuthFilter.java:41`, `WebSocketConfig.java:53`, `docker-compose.yml:4,27,58,162`.

---

## 8. Hai mục DỪNG TẠI GATE — số đo thật, không cưỡng hành

### B-M2 — tách `UseCaseInferenceEngine`
Gate EXEC-2 §1.1 yêu cầu BRANCH ≥ 80% cho riêng class **trước khi tách một dòng nào**. Đo thật (jacoco.csv, cột `$6/$7`):
```
BRANCH 69.7% (544/780)  LINE 86.6% (589/680)
```
Phân tích vùng chưa phủ (jacoco HTML): 77 dòng `nc` rải khắp 1399 dòng — cụm merge/disambiguate 373–486, string normalizers 1110–1396, phần lớn là logic private chỉ chạm được qua fixture graph chuyên biệt. Nâng ≥ 80% = viết test phủ ~80+ branch còn thiếu, rồi mới tách 1399 dòng kèm so số accuracy `UseCaseAccuracyEvalTest` trước/sau — khối lượng nhiều giờ, rủi ro heuristic lệch âm thầm.
**Quyết định: dừng tại gate, không tách.** Bước kế tiếp định nghĩa sẵn: (1) viết test phủ cụm `nc` bắt đầu từ merge path 373–414 và normalizers 1251–1396; (2) đo lại BRANCH; (3) chỉ tách khi gate đạt, nghiệm thu bằng số accuracy bằng nhau tuyệt đối.

### F-M6 — tách file frontend lớn
Baseline 6-F1 đo thật bằng `coverage/coverage-summary.json`:
```
src/views/admin/UserDetailDrawer.vue  lines.pct = 65      (< gate 6-F2 ≥ 70%)
src/views/admin/DashboardView.vue     lines.pct = 71.52   (≥ gate)
```
`UserDetailDrawer` **chưa đủ gate** — phải viết thêm test trước khi tách. `DashboardView` đủ gate nhưng việc tách vẫn là refactor nhiều giờ cần so `lines.pct` tổng + tổng byte `dist/assets/*.js` trước/sau — không làm vội trong phiên. Baseline bundle đã ghi cho lần tách: tổng dist JS **1.569.955 bytes / 53 files**; chunk `DashboardView-BGpM4Mhd.js` **582.351 bytes** (đo lại, khớp số reviewer); `vendor-charts` trong `dist/index.html` = **0**.

---

## 9. Lệch tổng hợp phải nêu

1. **`CachingGraphRepository` (B-L2):** thêm constructor seam (clock) + `@Autowired` trên constructor chính. Nếu không Spring không chọn được constructor giữa 2 constructor → boot fail. Lỗi này bị bắt thật khi rebuild docker image cho nghiệm thu profile của B-L7 (`No default constructor found`) — sửa rồi boot xanh.
2. **Spring Framework 7:** static `ObjectProvider.empty()/of()` không còn tồn tại — 3 test class dùng mock provider thay thế (ghi chú trong code test: "a mock returns null from getIfAvailable()").
3. **B-L1:** file sau gom = 556 dòng so mục tiêu ≤ 545 của EXEC (ước lượng lệch 11 dòng); tiêu chí định tính chính (`new JavaParser(config)` = 1) đạt.
4. **Đ7-1:** zip flatten 688→643 file; cửa sổ đo gồm parse+upsert, chưa tách pha parse thuần.
5. **Đ7-2e:** nghiệm thu thay thế (không swap volume live) — lý do ở Khối 6.
6. **Đ7-3:** dẫn chiếu `RateLimitFilter.java:97` thay `:234` trong EXEC (số cũ; tôi dùng dòng grep thật).
7. **B-L6:** tên bảng thật là `refresh_sessions`/`audit_logs` (không phải `refresh_tokens`/`audit_log` như AUDIT-REPORT) — sửa ERD theo migration, đúng luật "dữ liệu thật thắng tài liệu".
8. **B-M2/F-M6:** dừng tại gate theo số đo — không phải bỏ sót.

---

## 10. Q8 — đính chính AUDIT-REPORT (đã làm)

`update/docs/Qwen/AUDIT-REPORT.md` §9/§10: **RÚT** khuyến nghị dọn "tàn dư test T6". Nội dung đính chính đã ghi vào file: truy vấn thật `SELECT p.project_id, u.email FROM projects p JOIN users u ON u.id = p.owner_id WHERE p.project_id IN ('b9ab8150','431ee9dc')` → `user@vibegraph.com` và `thinhtran09177@gmail.com` (tài khoản chủ repo); `SELECT count(*) FROM users WHERE email LIKE 'runtime-t6%'` → **0**. Hai project là dữ liệu người dùng thật — **KHÔNG XÓA**. Kèm câu hỏi quy trình bắt buộc trả lời: cả hai lần kết luận "rác test" đều suy từ HÌNH DẠNG id thay vì JOIN `users` đọc chủ sở hữu; từ nay mọi đề xuất dọn dữ liệu phải kèm kết quả JOIN chủ sở hữu thật.

---

## 11. Tuân thủ luật cứng (§12 prompt EXEC)

| Luật | Trạng thái |
|---|---|
| Không commit | ✅ Mọi thay đổi trong working tree chờ chủ repo |
| Không `mvnw clean`, không `git clean -fdX` | ✅ (chỉ xóa `target/surefire-reports` trước mỗi lần đếm test, theo đúng lệnh trong prompt) |
| Không xoá volume, không `docker compose down -v` | ✅ Volume drill để lại + nêu lệnh cho operator |
| Không in giá trị secret | ✅ (backup/restore script tự giữ kỷ luật này; tôi chỉ grep tên biến) |
| Không chạm `update/docs/claude/**`, `update/graph/`, `task/`, `task-final/`, 7 file C1–C6 | ✅ |
| Đếm test: xóa surefire-reports trước, dán nguyên summary Maven | ✅ Mọi lần đếm trong báo cáo này |
| Cột jacoco.csv: `$6/$7` BRANCH, `$8/$9` LINE | ✅ |
| Tài liệu trái dữ liệu thật → dừng và báo | ✅ (B-L6 tên bảng; Đ7-3 dòng dẫn chiếu; Q8) |

---

## 12. Việc còn chờ operator

| # | Việc | Ghi chú |
|---|---|---|
| 1 | **Xoay secret phía provider** (Đợt 0 — phần còn lại) | Mục chảy máu duy nhất: Supabase password, JWT_SECRET, OAuth Google/GitHub, 8 Gemini key. Phần git cleanup agent đã làm xong (object `388632b` gone, grep `GOCSPX-` rỗng) |
| 2 | Chọn registry cho job CD (D-M4 phần 2) | GHCR / Docker Hub / chưa cần; comment chờ đã để trong `backend.yml` |
| 3 | Xóa volume drill nếu muốn | `docker volume rm vibegraph-restore-neo4j-drill` (~600MB) |
| 4 | Quyết số phận file 200MiB T6 | Chỉ sau khi xác nhận không công cụ nào còn đọc nó; AUDIT-REPORT đã rút khuyến nghị xóa |
| 5 | Cửa tiếp theo của B-M2/F-M6 | Viết test phủ branch (B-M2) và test UserDetailDrawer ≥70% lines (F-M6) — làm xong là đủ điều kiện tách theo gate |

---

*Báo cáo soạn 13/08/2026 theo lệnh operator. Các phiên sau mở file mới, không sửa file này.*
