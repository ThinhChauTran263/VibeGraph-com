# PROMPT CHO QWEN — thi hành Đợt 4/5/6/7 (13/08/2026, bản EXEC hoàn chỉnh)

Gửi nguyên văn phần dưới cho Qwen. File này thay thế mọi lệnh rời rạc đã trao đổi trước đó trong chat — nếu có mâu thuẫn, **file này thắng**.

---

## 0. Luật tối cao — đọc trước khi làm bất cứ gì

**Mọi con số trong báo cáo PHẢI là output thật của một lệnh bạn vừa chạy, dán nguyên văn. Không suy luận, không nói xuông, không "chắc là", không lấy số từ tài liệu cũ rồi ghi lại như thể vừa đo.**

Đây không phải yêu cầu về hình thức. Ba lần trước trong dự án này, số liệu "đo lại" hoá ra là số cũ chép nguyên (2.821/2.681 dòng — file chưa hề bị sửa; "tàn dư T6" — 2 lần liền suy đoán từ hình dạng ID thay vì JOIN vào bảng `users`). Lần này, mỗi dòng trong báo cáo mà không kèm lệnh + output thật sẽ bị coi là **không hợp lệ**, và toàn bộ phần việc liên quan phải làm lại.

**Định dạng bắt buộc cho MỌI mục đã làm:**

```
### <Mã việc>
**Lệnh đã chạy:**
```
<lệnh thật, copy-paste được>
```
**Output thật (dán nguyên văn, không rút gọn, không diễn giải lại thành lời):**
```
<output>
```
**Kết luận:** <so output với tiêu chí, PASS/FAIL/DỪNG>
```

Nếu một mục không đo được (thiếu credential, thiếu Docker, thiếu dữ liệu), viết đúng `[không đo được]` + lý do cụ thể. **Không được điền số phỏng đoán vào chỗ đó.**

---

## 1. Baseline bắt buộc — chạy TRƯỚC khi sửa bất kỳ dòng code nào

```bash
git status --porcelain | wc -l
git log --oneline -1
rm -rf target/surefire-reports
./mvnw -B test
```

**Baseline đã xác nhận (reviewer, ngay trước khi gửi prompt này):**
```
git log --oneline -1  →  b7f294c
git status --porcelain | wc -l  →  3 (2 file EXEC-*.md mới + 1 thư mục update/graph/ lạ — không phải của bạn, đừng động)
Tests run: 1031, Failures: 0, Errors: 0, Skipped: 1
```

Nếu bạn chạy lại và ra số khác 1031/0/0/1, **DỪNG NGAY, báo cáo con số thật, đừng tiếp tục**. Một baseline sai làm mọi so sánh sau này vô nghĩa.

Dán output thật của bước này làm dòng đầu tiên trong báo cáo.

---

## 2. Nguồn — đọc đầy đủ trước khi bắt đầu

1. `update/docs/claude/PLAN-REMAINING-2026-08-13.md` — nguồn danh sách việc, đã xác minh 41/41 mục còn tồn tại.
2. `update/docs/claude/EXEC-1-DOT-4-5.md` — chi tiết Đợt 4+5: `file:dòng`, tiêu chí nghiệm thu, rủi ro từng mục.
3. `update/docs/claude/EXEC-2-DOT-6-7.md` — chi tiết Đợt 6+7: cùng cấu trúc.
4. File này (PROMPT-FOR-QWEN-2026-08-13-EXEC.md) — **thứ tự thi hành + 8 quyết định đã chốt**. Khi 2 file trên và file này khác nhau về **thứ tự làm** hay **phạm vi**, file này thắng. Khi khác nhau về **chi tiết kỹ thuật** (tiêu chí, rủi ro, lệnh verify), đọc lại 2 file trên.

---

## 3. Trạng thái đã xong — ĐỪNG LÀM LẠI

Đã verify bằng lệnh, không phải suy đoán:

| Mã | Trạng thái | Bằng chứng |
|---|---|---|
| B-L9 (6 DTO chết) | ✅ XONG | `find src/main -name "<Class>.java"` cho cả 6 tên → 0/6 còn |
| B-L10 (entity `UserNotification`) | ✅ XONG | Entity đã xoá; bảng `user_notifications` + migration V10 còn nguyên (`JdbcNotificationRepository` vẫn dùng) |
| B-L11 (`TarballImportServiceTest`) | ✅ XONG | File đã xoá; suite thật `TarballImportServiceImplTest` còn |
| D-L3 (log rác root) | ✅ XONG | `ls ./*.log` → 0 file |
| D-L4 (gitignore 2 script) | ✅ XONG | `grep -c quick-start .gitignore` → 0 |
| D-M2r (pin postgres) | ✅ XONG | `postgres:16.11-alpine` ở cả 2 compose file |
| D-M5 (`task/` vs `task-final/`) | ✅ CHỐT — nhánh C | `git restore task/ task-final/` đã chạy, cây sạch, khớp `df64de3`. **Không làm gì thêm.** |
| S-M5 (trần multipart 2048MB) | ✅ CHỐT — giữ nguyên | Plan MAX = 2 GiB = đúng trần. Không siết |

---

## 4. Tám quyết định đã chốt

| # | Quyết định | Áp dụng vào bước nào |
|---|---|---|
| 1 | F-M6 giới hạn **2 file**: `UserDetailDrawer.vue` + `DashboardView.vue`. **Không đụng** `GraphCanvas.vue`. **Cấm** `LandingView.vue` (0 test) | §6 |
| 2 | Đ7-3 → **nhánh A** (tuyên bố giới hạn, không scale thật) | §8 |
| 3 | Đ7-1 → **chỉ đo** (`1a/1b/1c`). Tỷ lệ < 50% → dừng, ghi số, **không song song hoá** | §9 |
| 4 | Đ7-2 diễn tập restore → **máy này**, theo đúng 6 bước con | §7 |
| 5 | D-M4 phần 2 (registry CD) → để **comment chờ**, không chọn | §5 |
| 6 | D-L4 → **track** 2 script quick-start (đã verify không có secret thật — chỉ giá trị mặc định dev cục bộ) | §5 |
| 7 | S-L1 (`v-html`) → **chỉ comment** tại 3 điểm, không thêm DOMPurify | §5 |
| 8 | File T6 `.vibegraph/uploads/github-04e0b065-…/runtime-t6-large.txt` → **⛔ KHÔNG XOÁ** | §10 |

### Về quyết định #8 — đọc kỹ, đây là chỗ 2 lần trước đã sai

`AUDIT-REPORT.md` §10 gọi file này là "tàn dư T6 chờ operator duyệt dọn" — **lần thứ hai** cùng một cụm dữ liệu bị gắn nhãn sai bằng suy đoán thay vì tra cứu thật (lần đầu là 2 project `b9ab8150`/`431ee9dc` bị gọi nhầm "thuộc tài khoản `runtime-t6-*`" trong khi JOIN thật cho `user@vibegraph.com` và chủ repo).

Reviewer đã verify bằng lệnh thật (Docker đang chạy):
```bash
MSYS_NO_PATHCONV=1 docker compose exec -T backend find /uploads -iname 'runtime-t6-large.txt'
# → /uploads/github-04e0b065-39f6-484b-bc84-7bf25f8b2704/source/ThinhChauTran263-fatc-Grocery-Store-ce1c762/runtime-t6-large.txt

docker compose exec -T postgres psql -U vibegraph -d vibegraph -t -A -c \
  "SELECT project_id, name, source_type FROM projects WHERE project_id IN ('b9ab8150','431ee9dc');"
# → 431ee9dc|ThinhChauTran263/fatc-Grocery-Store|GITHUB
# → b9ab8150|ThinhChauTran263/fatc-Grocery-Store|GITHUB
```

Tên repo trong đường dẫn workspace (`ThinhChauTran263-fatc-Grocery-Store`) khớp **tuyệt đối** với tên project `431ee9dc`, chủ `user@vibegraph.com`. Đây là source code thật của một project sống, không phải rác test.

**Việc của bạn ở §10 không phải "xoá file" — mà là sửa `AUDIT-REPORT.md`: rút hẳn khuyến nghị xoá, ghi rõ vì sao (đúng như đoạn trên), và tự hỏi trong báo cáo: hai lần liền cùng cụm dữ liệu này bị gọi nhầm "tàn dư test" — quy trình nào đã tạo ra kết luận đó, để nó không lặp lại lần ba.**

---

## 5. Bước 1 — làm ngay, không phụ thuộc gì

Bốn việc dưới đây **độc lập với 8 quyết định**, chạy trước tiên, thứ tự tuỳ ý.

### 5.1. Lô A — xoá 2 mục code chết còn lại

- **B-L3**: `JwtService.java:57` — xoá overload `issue(User user)` 1 tham số. Sửa `JwtServiceTest.java:39,76` sang bản 2 tham số.
- **B-L4**: xoá `AnnotationVisitor.java` + `AnnotationVisitorTest.java` (production dùng `SpringAnnotationVisitor`).

**Trước khi xoá `AnnotationVisitorTest`, đếm số `@Test` trong nó và dán vào báo cáo** — mốc test kỳ vọng sau lô A phụ thuộc con số này:
```bash
grep -c '@Test' src/test/java/com/vibegraph/parser/visitor/AnnotationVisitorTest.java
```

**Nghiệm thu (dán output thật):**
```bash
grep -c 'public String issue(User user)' src/main/java/com/vibegraph/auth/service/JwtService.java   # kỳ vọng 0
find src/main -name AnnotationVisitor.java                                                            # kỳ vọng rỗng
find src/test -name AnnotationVisitorTest.java                                                        # kỳ vọng rỗng
grep -rn 'AnnotationVisitor' src/main src/test --include=*.java | grep -v SpringAnnotationVisitor      # kỳ vọng rỗng
rm -rf target/surefire-reports && ./mvnw -B test
```

Mốc test kỳ vọng: **1031 − (số `@Test` đếm được ở trên) = ?**. Ghi rõ phép tính, không chỉ ghi kết quả. Ra số khác → DỪNG, dán summary Maven thật, không tự giải thích cho khớp.

### 5.2. 6-F0 — cài coverage frontend

```bash
cd vibegraph-web
npm install -D @vitest/coverage-v8
```

Thêm `test.coverage` (provider `v8`, reporter `text` + `json-summary`) vào `vite.config.ts`, script `"test:coverage": "vitest run --coverage"` vào `package.json`.

**Nghiệm thu:**
```bash
npm run test:coverage
ls coverage/coverage-summary.json
node -e "const s=require('./coverage/coverage-summary.json');for(const k in s){if(/UserDetailDrawer|LandingView|DashboardView|GraphCanvas/.test(k))console.log(k,s[k].lines.pct)}"
```
Dán 4 số ra được — đây là baseline cho §6.

### 5.3. Đ7-4a — đo tỷ lệ `rows/nodes` (chỉ đo, không sửa code)

Trên **≥ 2 project khác cỡ** (lấy `project_id` thật từ `SELECT project_id FROM projects LIMIT 5`), chạy trong `cypher-shell`:
```cypher
MATCH (n:Symbol {projectId:'<id>'}) RETURN count(n) AS nodes;
MATCH (n:Symbol {projectId:'<id>'}) OPTIONAL MATCH (n)-[r]->(m:Symbol {projectId:'<id>'}) RETURN count(*) AS rows;
```
Ghi cả 2 số + tỷ lệ `rows/nodes` cho từng project vào báo cáo.

### 5.4. Đ7-1a/1b/1c — đo baseline parse (chỉ đo)

- **1a**: chọn repo mẫu (đề xuất chính VibeGraph — `find src -name "*.java" | wc -l`), ghi số file + commit SHA.
- **1b**: import repo mẫu 3 lần, đo wall-clock giai đoạn parse (mốc log `ParserServiceImpl:415` `Found {} .java files` → hoàn tất parse), lấy **median**.
- **1c**: tỷ lệ `parse_ms / total_import_ms`.

**Nếu tỷ lệ < 50%: DỪNG tại đây, ghi số vào báo cáo, KHÔNG làm Đ7-1d/1e/1f (song song hoá). Đây là quyết định #3 đã chốt, không phải gợi ý.**

---

## 6. Bước 2 — Lô B (Đợt 5), đúng thứ tự

`F-L4 → F-L2 → F-L1 → F-L3 → B-L2 → B-L7 → B-L5 → B-L1 → B-L8`

Chi tiết từng mục (file:dòng, tiêu chí, rủi ro) ở `EXEC-1-DOT-4-5.md` §3. Không gộp nghiệm thu — mỗi mục một lệnh test riêng, dán output riêng.

**Hai mục cần chú ý đặc biệt vì có thể hỏng mà không test nào báo:**

- **B-L1** (gom `createParser`/`createProjectParser`): nghiệm thu **bắt buộc** bằng đếm edge Cypher trước/sau trên cùng 1 project:
  ```cypher
  MATCH (n:Symbol {projectId:'<id>'}) RETURN count(n) AS nodes;
  MATCH (:Symbol {projectId:'<id>'})-[r:CALLS]->(:Symbol {projectId:'<id>'}) RETURN count(r) AS calls;
  ```
  Đo trước khi sửa, re-analyze đúng project đó, đo lại. **Lệch 1 số = fail, hoàn nguyên.** Test suite xanh không đủ.

- **B-L8** (ưu tiên security event khi queue đầy): nghiệm thu bằng `MeterRegistry` trong test, đo **cả 2 counter** (`request_events.dropped.total`, `security_events.dropped.total`) trước/sau, không chỉ đọc code.

- **B-L5** (gộp CORS về 1 nguồn): `CorsConfig.java` chứa guard startup chống `"*"` ở constructor — **chỉ xoá `addCorsMappings`, giữ phần validate**. Nghiệm thu phải lặp lại test T8 (Origin lạ không nhận header CORS).

---

## 7. Bước 3 — Lô C (Đợt 5), chỉ 2 mục

- **B-L6**: sửa `database/ERD.md:64–67` — `refresh_tokens`/`audit_log` đã tồn tại (V18, V10), không còn là "có thể thêm sau".
- **D-L2**: thêm comment cảnh báo trên block `environment:` của `database/docker-compose.postgres.yml:26–28` — chỉ dùng local.

10 mục còn lại của Lô C: **ghi nhận trong báo cáo bằng đúng câu "ghi nhận, không sửa"**, không được để trống.

---

## 8. Đ7-2 — backup/restore, đúng 6 bước, KHÔNG được nhảy cóc

**Đã có sẵn:** `scripts/backup.ps1` (324 dòng), `scripts/restore.ps1` (298 dòng), commit `b7f294c`. **Không viết lại.**

Thứ tự bắt buộc — bỏ bước nào thì bước sau sẽ tự fail vì thiếu input, đó là fail lãng phí:

| Bước | Việc | Lệnh |
|---|---|---|
| **2a** | Viết chương `## Backup and restore` vào `DEVOPS-GUIDE.md` (tiếng Anh, giữ văn phong hiện có). Phân biệt rõ: control plane (Postgres) không tái tạo được vs data plane (Neo4j) tái tạo được | — |
| **2b** | Chạy backup thật | `./scripts/backup.ps1` |
| **2c** | Diễn tập restore thật, vào **volume mới** | `./scripts/restore.ps1 -BackupDir <dir từ 2b> -Confirm` |
| **2d** | Restore Neo4j vào instance sạch | `neo4j-admin database load` |
| **2e** | Backend nối được vào dữ liệu đã restore | `docker compose ps` cả 4 `healthy`; login tài khoản có trước backup → 200 |
| **2f** | Ghi RTO thật | Đo bằng đồng hồ, không ghi "nhanh" |

**Trước khi chạy 2c, kiểm** `docker volume ls` — volume đích **không được** là `vibegraph_postgres-data` đang chạy. `restore.ps1` đã tự chặn việc này, nhưng verify lại trước khi bấm `-Confirm`.

Dán **nguyên văn output** của `restore.ps1` vào báo cáo — không tóm tắt lại bằng lời.

---

## 9. Đ7-3 — nhánh A

1. Viết vào `DEPLOYMENT.md`: `grep -c -i "single-replica" DEPLOYMENT.md` phải ≥1, đoạn đó liệt kê đích danh 4 thành phần chặn (`RateLimitFilter.java:234`, `JwtAuthFilter.java:41`, SimpleBroker in-process, `container_name` trong `docker-compose.yml:4,27,58,162`).
2. **Nghiệm thu âm — bắt buộc chạy thật:**
   ```bash
   docker compose up -d --scale backend=2
   ```
   Dán **nguyên văn lỗi** vào báo cáo. Nếu nó *không* fail, giả thuyết sai — báo lại ngay, đừng tự sửa cho fail.

---

## 10. B-M2 — tách `UseCaseInferenceEngine` (Đợt 6)

**Không phụ thuộc quyết định nào về F-M6 — làm độc lập, song song được với §6.**

1. Đo BRANCH coverage **hiện tại** của riêng class này:
   ```bash
   ./mvnw -DskipITs test
   awk -F, '$3=="UseCaseInferenceEngine"{printf "BRANCH %.1f%% (%d/%d)  LINE %.1f%%\n", 100*$7/($6+$7), $7, $6+$7, 100*$9/($8+$9)}' target/site/jacoco/jacoco.csv
   ```
   Cột đúng: `$6/$7` = BRANCH missed/covered, `$8/$9` = LINE. **Dùng sai cột ra số sai — đã có tiền lệ.**
2. Nếu BRANCH < 80%: viết thêm test cho `UseCaseAccuracyEvalTest`/`UmlUseCaseServiceTest` tới khi đạt, đo lại.
3. Tách file gốc (1398 dòng) xuống ≤ 400 dòng.
4. Nghiệm thu: BRANCH tổng các file con ≥ baseline; **số accuracy** của `UseCaseAccuracyEvalTest` in ra trước/sau phải bằng nhau (không chỉ pass/fail — đây là engine heuristic, tách sai thứ tự áp rule đổi kết quả mà không test nào fail).

---

## 11. F-M6 — 2 file, theo quyết định #1

Chỉ `UserDetailDrawer.vue` (3201 dòng) và `DashboardView.vue` (1524 dòng). Cửa vào bắt buộc theo `EXEC-2-DOT-6-7.md` §2.3: baseline coverage (từ §5.2) → viết test nếu thiếu → tách → so `lines.pct` tổng không giảm, so tổng byte `dist/assets/*.js` chênh ≤3%.

`DashboardView-*.js` hiện **582.351 byte** (đo được) — sau tách phải **giảm**, ghi con số thật.

---

## 12. Luật cứng

1. **Không commit.** Reviewer commit sau khi nghiệm thu.
2. **Không `mvnw clean`** (tiến trình java giữ file trong `target/`). **Không `git clean -fdX`** (xoá ~985 MB gồm `.gitnexus/`, `node_modules/`).
3. **Không xoá volume**, không `docker compose down -v`.
4. **Không in giá trị secret.**
5. **KHÔNG chạm**: `update/docs/claude/**`, `update/graph/` (không rõ nguồn gốc, không phải của bạn), `task/`, `task-final/` (đã chốt xong, đừng động lại).
6. **Đếm test:** luôn `rm -rf target/surefire-reports` trước khi chạy, dán nguyên summary Maven — không gộp XML tồn đọng.
7. **Cột `jacoco.csv`:** `$6/$7`=BRANCH, `$8/$9`=LINE. Nhầm cột = số sai.
8. Tài liệu trái dữ liệu thật → **DỪNG và báo**, đừng tự ứng biến.

---

## 13. Định dạng báo cáo cuối

Một dòng mỗi mục:

| Mã | File đã sửa | Dòng thay đổi | Lệnh nghiệm thu | Output thật | Lệch so với prompt này |
|---|---|---|---|---|---|

Kèm 6 khối bắt buộc, **mỗi khối phải có lệnh + output thật theo định dạng ở §0**:
1. Baseline đầu và cuối (so sánh 2 lần chạy `./mvnw -B test`).
2. Bảng B-L1: `nodes`/`calls` trước & sau, project dùng để đo.
3. Bảng Đ7-4a: `rows/nodes` cho từng project đã đo.
4. Bảng Đ7-1: repo mẫu, 3 số thô + median, tỷ lệ %, quyết định dừng hay tiếp.
5. Output nguyên văn `restore.ps1 -Confirm`.
6. Output nguyên văn lỗi `docker compose up -d --scale backend=2`.

Mục nào `[không đo được]`: lý do cụ thể, không để trống, không đoán số thay vào.
