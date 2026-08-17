# LẦN 2 — THI HÀNH ĐỢT 6 + ĐỢT 7

- **Ngày lập:** 13/08/2026
- **Nguồn:** `update/docs/claude/PLAN-REMAINING-2026-08-13.md` (§5, §6)
- **Người thực thi:** Qwen · **Người nghiệm thu:** reviewer
- **Tiền đề:** phải hoàn tất `EXEC-1-DOT-4-5.md` trước. Đợt 6 tách file lớn — làm khi backlog nhỏ còn treo là tự tạo xung đột merge với chính mình.

> **Đọc trước khi bắt đầu:** đây là lần khó hơn lần 1 rất nhiều. Đợt 6 là refactor không đổi hành vi; Đợt 7 là 4 mục **chưa ai đưa vào backlog** và **3 trong 4 mục có thể hỏng âm thầm** (không test nào fail). Vì vậy mọi tiêu chí dưới đây là **số đo trước/sau**, không phải "pass/fail".

---

## 0. Trạng thái đã kiểm hôm nay

| Mục | Trạng thái | Bằng chứng |
|---|---|---|
| **Đ7-2** script | ✅ **ĐÃ CÓ** | `scripts/backup.ps1` **324 dòng**, `scripts/restore.ps1` **298 dòng**, cả hai parse OK, đã commit `b7f294c` |
| **Đ7-2** chương tài liệu | ⛔ **CHƯA** | `grep -c pg_dump DEVOPS-GUIDE.md` → **0** · `grep -c neo4j-admin` → **0** · heading `## *backup*` → **0** |
| **Đ7-2** diễn tập restore thật | ⛔ **CHƯA** | Chưa ai chạy `restore.ps1 -Confirm` lần nào |
| **Đ7-3** tuyên bố giới hạn | ⛔ **CHƯA** | `DEPLOYMENT.md`: `grep -ci replica` → **0**. `docker-compose.yml` còn **4** `container_name` |
| **Đ7-1**, **Đ7-4** | ⛔ chưa bắt đầu | Xem §3, §4 |
| **B-M2**, **F-M6** | ⛔ chưa bắt đầu | Xem §1, §2 |

**Mốc để so sánh** (đo sau khi `EXEC-1` xong, ghi lại vào báo cáo trước khi sửa gì):

```
rm -rf target/surefire-reports && ./mvnw -B test   → dán summary
cd vibegraph-web && npm run build                  → ghi tổng byte dist/assets/*.js
```

---

## 1. Đợt 6 · B-M2 — tách `UseCaseInferenceEngine` (1398 dòng)

**Xác minh:** `wc -l src/main/java/com/vibegraph/diagram/service/impl/UseCaseInferenceEngine.java` → **1398** (khớp AUDIT-REPORT).

### 1.1. Cửa vào bắt buộc — coverage BRANCH của chính class đó

Class này đã có coverage. **Đo lại trước khi làm gì** (số này đổi theo mỗi lần chạy test, đừng lấy từ tài liệu):

```bash
./mvnw -DskipITs test
awk -F, '$3=="UseCaseInferenceEngine"{printf "INSTRUCTION %.1f%%  BRANCH %.1f%%  LINE %.1f%%  METHOD %.1f%%\n", 100*$5/($4+$5), 100*$7/($6+$7), 100*$9/($8+$9), 100*$13/($12+$13)}' target/site/jacoco/jacoco.csv
```

Cột trong `jacoco.csv`: `$4/$5` = INSTRUCTION missed/covered · `$6/$7` = **BRANCH** · `$8/$9` = **LINE** · `$12/$13` = METHOD. Dùng sai cột là ra số lệch — reviewer đã mắc đúng lỗi này khi so tổng toàn project với số của một class.

**Mốc đo phiên 13/08 (chỉ để tham khảo, PHẢI đo lại):** BRANCH **69,7 %** (544/780) · LINE ~**87 %** · METHOD **89,4 %**.

> Ghi chú trung thực: bản kế hoạch ghi LINE 86,6 % (589/680); đo lại sau khi chạy test thêm cho 595/683 ≈ 87,1 %. Chênh lệch là do coverage dịch theo mỗi lần chạy, **không** phải ai sai. Đây là lý do bắt buộc đo lại chứ không trích số cũ.

**Cửa vào:** BRANCH cho **riêng class này** phải **≥ 80 %** (`BRANCH_MISSED ≤ 156`) **trước khi** tách một dòng nào. 236 nhánh chưa phủ chính là chỗ heuristic dễ lệch nhất.

Test đang phủ: `src/test/java/com/vibegraph/diagram/eval/UseCaseAccuracyEvalTest.java`, `src/test/java/com/vibegraph/diagram/service/UmlUseCaseServiceTest.java`.

### 1.2. Tiêu chí nghiệm thu sau khi tách

| # | Tiêu chí | Cách đo |
|---|---|---|
| 1 | File gốc ≤ **400** dòng | `wc -l UseCaseInferenceEngine.java` |
| 2 | BRANCH của **tổng** các class con **≥** BRANCH baseline | awk trên `jacoco.csv`, cộng dồn các class mới |
| 3 | **Accuracy không giảm** | Chạy `UseCaseAccuracyEvalTest` trước/sau, so **số accuracy** in ra, không chỉ pass/fail |
| 4 | Test suite xanh, số test **không giảm** | `rm -rf target/surefire-reports && ./mvnw -B test`, dán summary |
| 5 | Gate JaCoCo `LINE ≥ 0.70` còn xanh | `./mvnw verify` |

### 1.3. Rủi ro — đọc trước khi sửa

- **Heuristic lệch âm thầm.** Đây là engine suy luận. Tách sai **thứ tự áp rule** làm kết quả use-case đổi mà **không test nào fail**, vì test dựa trên eval accuracy chứ không phải equality. Tiêu chí 3 là bắt buộc, không phải tuỳ chọn.
- **8 kiểu lồng** (`ActorGuess`, `DomainAgg`, `AuthKind`, `ClassFallback`, `DomainGuess`, `InferenceResult`, `Endpoint`, …). Đưa ra ngoài đổi visibility → có thể vỡ `UmlUseCaseServiceTest`.

---

## 2. Đợt 6 · F-M6 — tách file frontend lớn

### 2.1. Phạm vi: **giới hạn ở 4 file > 1.400 dòng**, không phải 30 file

Đo bằng `wc -l` ngày 13/08 — **30 file** trong `vibegraph-web/src` vượt 400 dòng (trừ `__tests__`), không phải 10 như AUDIT-REPORT ghi:

```bash
find vibegraph-web/src -name '*.vue' -o -name '*.ts' | grep -v __tests__ | xargs wc -l | awk '$1>400 && $2!="total"' | sort -rn
```

4 file mục tiêu của đợt này:

| Dòng | File |
|---|---|
| **3201** | `src/views/admin/UserDetailDrawer.vue` |
| **2958** | `src/views/LandingView.vue` |
| **1524** | `src/views/admin/DashboardView.vue` |
| **1468** | `src/components/graph/GraphCanvas.vue` |

26 file còn lại (1123 → 431 dòng): **không refactor hồi tố**. Chuyển thành quy tắc "file mới không vượt 400 dòng" — chi phí sửa 26 file không tương xứng giá trị.

**Đính chính đường dẫn:** AUDIT-REPORT ghi `UserDetailDrawer.vue` và `DashboardView.vue` không kèm đường dẫn; cả hai ở `src/views/admin/`, **không** ở `src/components/admin/`.

### 2.2. ⛔ Mục CHẶN: chưa đo được coverage frontend

Nguyên tắc "có test trước khi tách" hiện **không thực hiện được**:

```bash
grep -c coverage vibegraph-web/package.json        # → 0  (chỉ có @vitest/eslint-plugin, không phải coverage)
ls vibegraph-web/node_modules/@vitest/             # → không có coverage-v8 / coverage-istanbul
```

Và 2 file mục tiêu **không có test nào**:

```bash
find vibegraph-web/src -name 'LandingView.spec.ts'   # → 0
find vibegraph-web/src -name 'SecurityView.spec.ts'  # → 0
```

> **Đính chính (Qwen phản biện, đã kiểm):** `lib/api.ts` **có** test — `grep -rl "from '\.\./api'\|from '@/lib/api'" --include=*.spec.ts` → **18 spec**. Nó có coverage từng phần theo domain. Đừng ghi nó là "0 test".

### 2.3. Cửa vào bắt buộc — theo thứ tự, không bỏ bước

| Bước | Việc | Tiêu chí nghiệm thu |
|---|---|---|
| **6-F0** | Cài `@vitest/coverage-v8`; thêm block `test.coverage` (provider `v8`, reporter `text` + `json-summary`); script `"test:coverage": "vitest run --coverage"` | `npm run test:coverage` chạy được và in bảng; `coverage/coverage-summary.json` tồn tại |
| **6-F1** | Lấy baseline **từng file mục tiêu** | `node -e "const s=require('./coverage/coverage-summary.json');for(const k in s){if(/UserDetailDrawer\|LandingView\|DashboardView\|GraphCanvas/.test(k))console.log(k,s[k].lines.pct)}"` → **ghi 4 số vào báo cáo**. Đây là baseline, thiếu nó thì bước 6-F3 vô nghĩa |
| **6-F2** | Viết test **trước khi tách** | Mỗi file mục tiêu `lines.pct ≥ 70`. `LandingView`/`SecurityView` phải tạo mới từ đầu |
| **6-F3** | Tách file | `lines.pct` của **tổng** các file con **≥** baseline file gốc; `vitest run` xanh và số test **không giảm**; `vue-tsc --build` sạch; `npm run build` OK |
| **6-F4** | Chứng minh bundle không phình | `npm run build` trước/sau, so tổng byte `dist/assets/*.js`: chênh **≤ 3 %**. Riêng `DashboardView-*.js` hiện **582.351 B** — sau tách phải **giảm**, nêu con số thật |

**6-F0 nên làm sớm và độc lập** — nó rẻ, không chặn ai, và cho baseline trước mọi đụng chạm frontend (F-L1/F-L3 ở lần 1 cũng nằm trong frontend).

### 2.4. Rủi ro

| Rủi ro | Chi tiết |
|---|---|
| `UserDetailDrawer` 3201 dòng | File lớn nhất repo. Tách thành sub-panel (quota / API keys / sessions) làm vỡ prop drilling và emit chain. Test hiện có chỉ **1 file** cho 3201 dòng → baseline gần chắc là thấp |
| `LandingView` 2958 dòng, **0 test** | Chính file này chứa F-L1/F-L2 (timer/listener) của lần 1. **Nếu lần 1 đã sửa F-L1/F-L2 thì làm `LandingView` sau cùng** để không tách trên code vừa đổi |
| `api.ts` | **Hoãn sau cùng** — 987 dòng là đường ra duy nhất tới backend cho ~40 endpoint. Lý do hoãn là **bề rộng ảnh hưởng**, không phải thiếu test |

---

## 3. Đợt 7 · Đ7-1 — Parse CPG tuần tự

**Xác minh còn tồn tại:** `src/main/java/com/vibegraph/parser/service/impl/ParserServiceImpl.java:409–445` (`parseProject`):

```java
ProjectSymbolRegistry projectSymbols = ProjectSymbolRegistry.fromFiles(javaFiles);   // :423
JavaParser parser = createProjectParser(projectRoot, javaFiles);                      // :424
for (Path javaFile : javaFiles) {                                                     // :426  ← tuần tự
    ParseResult result = parseFileInternal(javaFile, parser, projectSymbols);          // :428  ← 1 parser dùng chung
```

`grep -n "parallelStream\|ExecutorService\|CompletableFuture\|ForkJoin" ParserServiceImpl.java` → **rỗng**.

**Có vòng lặp thứ hai** (Qwen phản biện, đã kiểm): `:525` cũng là `for (Path javaFile : javaFiles)`. Phải phân loại **cả hai** — cùng đường thi hành hay hai đường khác nhau — trước khi song song hoá. Nếu chỉ song song hoá `:426`, tiêu chú Đ7-1e có thể pass trên đường này mà lệch ở đường kia.

**Tiền đề thuận lợi đã kiểm:** `ProjectSymbolRegistry.java` (130 dòng) là **immutable** (`:24` `Set.copyOf`) và truyền qua `ThreadLocal` (`:20`, `:67` `Scope open(...)`) → registry **không** phải rào cản. Rào cản thật là `CombinedTypeSolver`/`JavaSymbolSolver` dùng chung (cache nội bộ không cam kết thread-safe).

### 3.1. ⚠️ ĐO TRƯỚC, QUYẾT SAU

Claim "parse chiếm ~65 % thời gian import" đến từ `docs/audit-report-v2-2026-08-12.md:113` và **`[chưa xác minh]`** — không có benchmark nào trong repo chứng minh. **Không được sửa trước khi đo.**

| Bước | Cách đo | Tiêu chí |
|---|---|---|
| Đ7-1a | Chọn **một** repo mẫu cố định. Ghi: đường dẫn + `find <repo> -name "*.java" \| wc -l` + commit SHA | Repo mẫu và số file có trong báo cáo |
| Đ7-1b | **Baseline:** import 3 lần, đo wall-clock giai đoạn parse, lấy **median**. Mốc từ log `ParserServiceImpl:415` (`Found {} .java files`) → `listener.onFileParsed(total, total)` | `baseline_parse_ms = <median>` + 3 số thô |
| Đ7-1c | Tỷ lệ parse/tổng import trên cùng lần chạy | `parse_ms / total_import_ms = <x> %` — con số này **thay thế** claim "~65 %" |
| Đ7-1d | Sau song song hoá: đo lại cùng repo, cùng máy, 3 lần, median | `after_parse_ms ≤ 0,6 × baseline_parse_ms` trên máy ≥ 4 core. **Không đạt → giữ nguyên bản tuần tự và báo lý do** |
| Đ7-1e | **Tính đúng đắn — quan trọng hơn tốc độ** | Số node **bằng nhau tuyệt đối**; số edge **bằng nhau tuyệt đối**, đặc biệt `CALLS`. `MATCH (n:Symbol {projectId:$p}) RETURN count(n)` và `MATCH (:Symbol {projectId:$p})-[r:CALLS]->(:Symbol {projectId:$p}) RETURN count(r)`. **Lệch 1 edge = fail** |
| Đ7-1f | Chạy 2 lần liên tiếp trên cùng repo | Node/edge 2 lần **giống nhau** (chứng minh không non-determinism do race) |

**Nếu Đ7-1c cho ra tỷ lệ thấp (ví dụ < 30 %), DỪNG.** Song song hoá một pha chiếm 25 % thời gian mà đổi lại rủi ro race trong symbol solver là đánh đổi tồi. Báo số và chờ quyết định.

### 3.2. Rủi ro

- `JavaSymbolSolver` + `CombinedTypeSolver` chia sẻ giữa nhiều thread → race làm `resolve()` trả sai type → **CALLS edge biến mất hoặc trỏ sai**, và **không test nào fail**. Đó là lý do Đ7-1e là tiêu chí cứng.
- Mỗi thread một `JavaParser` = mỗi thread một `JavaParserTypeSolver` index toàn bộ source root → **RAM × số thread**. Backend **không có `mem_limit`** (chỉ Neo4j có) → pool 8 thread trên repo lớn có thể OOM-kill container. **Bound pool** theo `Runtime.availableProcessors()` và đo RSS bằng `docker stats` trước/sau.
- `progressListener.onFileParsed(parsed, total)` (`:441`) đếm bằng `int parsed` không đồng bộ → quên `AtomicInteger` làm progress WebSocket nhảy lùi/mất số.
- `results.add(...)` (`:429`, `:433`) trên `ArrayList` (`:410`) — **không** thread-safe.

---

## 4. Đợt 7 · Đ7-2 — Backup/restore: script đã có, phần còn lại chưa

**Đã xong:** `scripts/backup.ps1` (324 dòng) và `scripts/restore.ps1` (298 dòng), commit `b7f294c`. `restore.ps1` restore vào **volume mới**, so `users`/`projects`/`api_keys` với `manifest.json`, bắt buộc `-Confirm`.

**Còn lại:**

| Bước | Việc | Tiêu chí nghiệm thu |
|---|---|---|
| Đ7-2a | Viết chương backup vào `DEVOPS-GUIDE.md` (guide viết **tiếng Anh** — giữ văn phong đó) | `grep -c "pg_dump" DEVOPS-GUIDE.md` ≥ 1 · `grep -c "neo4j-admin database dump"` ≥ 1 · có mục snapshot volume `upload-workspaces` · có heading `## Backup and restore`. Phải phân biệt rõ: **control plane (Postgres) không tái tạo được**; **data plane (Neo4j) tái tạo được** bằng analyze lại |
| Đ7-2b | Chạy `backup.ps1` thật | `manifest.json` tồn tại; `postgres.sql` **> 0 byte**; `grep -c "CREATE TABLE" postgres.sql` ≥ số bảng trong `src/main/resources/db/migration` |
| Đ7-2c | **Diễn tập restore thật** — `restore.ps1 -Confirm` | Script tự so `users`/`projects`/`api_keys` với manifest và fail nếu lệch. Dán **nguyên output** vào báo cáo. ⚠️ Dùng `projects`, **không** `project_ownership` — bảng đó không tồn tại (`ProjectOwnership` map vào `@Table(name = "projects")`) |
| Đ7-2d | Restore Neo4j vào instance sạch | `MATCH (n:Symbol) RETURN count(n)` = số trước dump |
| Đ7-2e | Backend nối được vào dữ liệu đã restore | `docker compose ps` cả 4 service `healthy`; login bằng tài khoản có trước backup → **200** + cookie `vg_session` |
| Đ7-2f | Ghi **RTO thật** | `restore_wall_clock = <phút>` đo bằng đồng hồ, kèm dung lượng dump. **Không ghi "nhanh"** |

**Rủi ro:**
- ⚠️ **Diễn tập vào stack đang chạy sẽ phá dữ liệu dev thật.** `restore.ps1` đã thiết kế để tạo volume mới, nhưng **kiểm lại trước khi chạy**: `docker volume ls` — volume đích **không được** là `vibegraph_postgres-data`.
- Chương backup mà không nêu **credential lấy từ đâu** sẽ khiến người vận hành hardcode password vào cron → tái tạo đúng lỗi S1 mà Đợt 0 vừa dọn. **Phải dùng biến môi trường/secret store.**
- **Không dán output có credential/PII** vào `DEVOPS-GUIDE.md`.

---

## 5. Đợt 7 · Đ7-3 — Single-replica, và điều đó chưa được ghi ở đâu

**Xác minh:** `DEPLOYMENT.md` → `grep -ci "replica"` = **0**. `docker-compose.yml` còn **4** `container_name`.

Trạng thái per-instance còn thật: `RateLimitFilter.java:17,234` (Caffeine in-process — cũng là S-L3), `JwtAuthFilter.java:41` `static ACTIVE_USERS` (cũng là S-L4), `docker-compose.yml:4,27,58,162` `container_name` cố định (cũng là D-L1).

**Điểm quan trọng:** S-L3, S-L4, D-L1 trong backlog là **triệu chứng** của cùng mục này. Sửa lẻ S-L3 (thêm Redis) mà chưa quyết kiến trúc là làm ngược thứ tự — đó là lý do 3 mục đó nằm ở Lô C lần 1 với ghi chú "phụ thuộc Đ7-3".

### Nhánh A — tuyên bố giới hạn (khuyến nghị, chi phí ~0)

1. `grep -c -i "single-replica" DEPLOYMENT.md` ≥ 1, và đoạn đó **liệt kê đích danh** 4 thành phần chặn kèm `file:dòng`.
2. **Nghiệm thu âm — chứng minh giới hạn là thật, không phải phỏng đoán:**
   ```bash
   docker compose up -d --scale backend=2
   ```
   → phải **fail** vì tên container trùng. **Dán nguyên văn lỗi vào báo cáo.** Nếu nó *không* fail thì giả thuyết sai và phải báo lại.
3. Đánh dấu S-L3/S-L4/D-L1 là "phụ thuộc quyết định Đ7-3", không sửa lẻ.

### Nhánh B — thật sự hỗ trợ nhiều replica (đắt, **cần chủ repo quyết**)

1. Bỏ `container_name` → kéo theo sửa `DEVOPS-GUIDE.md:51,270,289` và `scripts/dev-up.ps1:51,57`.
2. Rate-limit chuyển shared store. Nghiệm thu: `--scale backend=2`, gửi **240** request cùng user chia đều 2 replica → **429 ở request thứ 241 tổng cộng**, không phải 481.
3. WebSocket: client nối replica 1 vẫn nhận progress của import chạy ở replica 2 — assert nhận đúng message `ANALYZED`.

**Rủi ro nhánh B:** thêm Redis vào **đường auth nóng**. Redis chết = mọi request bị chặn hoặc fail-open — cả hai đều tệ. Nếu chọn B, **định nghĩa trước** hành vi khi shared store không khả dụng và test đúng nhánh đó.

**Đề nghị: làm nhánh A trong đợt này.** Nhánh B chỉ khi có nhu cầu scale thật.

---

## 6. Đợt 7 · Đ7-4 — `getFullGraph` nhân bản node theo số cạnh

**Xác minh:** `Neo4jGraphRepository.java:387–397`:

```java
"MATCH (n:Symbol {projectId: $projectId}) " +
"OPTIONAL MATCH (n)-[r]->(m:Symbol {projectId: $projectId}) " +
"RETURN n, r, m"
```

Mỗi node xuất hiện **một lần cho mỗi cạnh đi ra**. Khử trùng lặp diễn ra **phía client** trong `LinkedHashMap nodeMap` (`:398`) → số **dòng** truyền từ Neo4j về driver là O(số cạnh), không phải O(số node). Cùng file còn 2 chỗ tương tự: `:338` và `:101–105`.

**Đã tốt hơn báo cáo v2:** cap **đã** hiệu lực (B-M10 đóng rồi) — `GraphPayloadProperties.java:27` `nodeLimit = 5000`, `GraphController.java:60–62` `clamp(...)`. Nhưng cap áp ở **tầng controller**, tức **sau khi** Neo4j đã trả toàn bộ dòng. Phần nhân bản trên đường truyền **chưa** được xử lý.

| Bước | Cách đo | Tiêu chí |
|---|---|---|
| Đ7-4a | Trong `cypher-shell`, trên **≥ 2 project khác cỡ**: `MATCH (n:Symbol {projectId:$p}) OPTIONAL MATCH (n)-[r]->(m:Symbol {projectId:$p}) RETURN count(*)` so với `MATCH (n:Symbol {projectId:$p}) RETURN count(n)` | Ghi 2 số và tỷ lệ `rows / nodes = k` cho từng project. **Đây là bằng chứng định lượng duy nhất cho mục này** |
| Đ7-4b | Sau khi tách 2 query (nodes riêng, edges riêng) | `rows_nodes + rows_edges` ≤ `nodes + edges`; tỷ lệ mới = **1,0** |
| Đ7-4c | **Tính đúng đắn** | `curl` cùng endpoint `/api/graph/{id}` trước/sau: **số node** và **số edge** trong JSON bằng nhau tuyệt đối; `meta.nodeLimit`/`meta.truncated` không đổi |
| Đ7-4d | **Node cô lập không bị mất** | Chọn project có ≥1 node **không có cạnh đi ra**: node đó **vẫn** phải xuất hiện sau khi tách. Đây chính là lý do `OPTIONAL MATCH` tồn tại |
| Đ7-4e | Thời gian endpoint | `curl -w "%{time_total}"` 5 lần, median, trước/sau, cùng project. ⚠️ `CachingGraphRepository` cache **5 phút** → phải invalidate hoặc dùng project khác nhau, nếu không bạn đo cache chứ không đo query |

> **`[chưa xác minh]`:** tỷ lệ `k ≈ 2,3` chỉ được **suy ra** từ số GitNexus index trong `CLAUDE.md` (17.079 symbol / 39.748 relationship), **chưa** đo trên Neo4j thật. Đ7-4a là để thay con số suy ra bằng số đo.

**Rủi ro:**
- **Mất node cô lập** — rủi ro số 1, xem Đ7-4d.
- **Hai query = hai điểm nhất quán:** giữa 2 query có thể có ghi mới → edge trỏ tới node không có trong tập nodes. Xử lý: bọc cả hai trong một `session.executeRead`, **hoặc** lọc edge thiếu đầu mút **và log khi lọc**, không im lặng.
- `addNodeToMap` (`:404`, `:415`) tích lũy `nodeStats` — tách query phải giữ nguyên `nodeStats`/`edgeStats`, nếu không dashboard đọc thống kê sẽ lệch.
- `stableNodeId(n)`/`stableEdgeId(...)` (`:419–421`) tính từ `Node` object trong cùng dòng. Tách query đổi nguồn dữ liệu của chúng — **phải assert ID không đổi trước/sau**. ID là thứ frontend dùng để chọn node; đổi ID = vỡ mọi deep link.

---

## 7. Việc cần CHỦ REPO quyết trước khi bắt đầu

| # | Câu hỏi | Chặn mục nào |
|---|---|---|
| 1 | F-M6 giới hạn ở **4 file > 1.400 dòng**, hay làm cả 30 file > 400 dòng? | §2 — quyết định này đổi phạm vi gấp 7 lần |
| 2 | Đ7-3 chọn **nhánh A** (tuyên bố giới hạn) hay **nhánh B** (thật sự scale)? | §5, và kéo theo S-L3/S-L4/D-L1 ở lần 1 |
| 3 | Nếu Đ7-1c cho tỷ lệ parse thấp (< 30 %), có còn muốn song song hoá không? | §3 |
| 4 | Đ7-2 diễn tập restore chạy trên máy này hay môi trường riêng? | §4 — liên quan an toàn dữ liệu dev |

**Không có câu trả lời thì đừng đoán.** Làm phần không phụ thuộc: 6-F0 (cài coverage), Đ7-2a (chương tài liệu), Đ7-4a (đo tỷ lệ), Đ7-1a/1b/1c (đo baseline) — cả 4 đều là đo/tài liệu, không đổi hành vi.

---

## 8. Luật cứng

1. **Không commit.** Reviewer commit sau nghiệm thu.
2. **Mọi số phải đo lại, không trích từ tài liệu.** Coverage và số dòng dịch theo mỗi lần chạy. Dùng `wc -l`, **không** dùng PowerShell `Measure-Object -Line`.
3. **Đếm test:** `rm -rf target/surefire-reports` trước, dán **nguyên summary Maven**.
4. **Cột `jacoco.csv`:** `$6/$7` = BRANCH, `$8/$9` = LINE. Dùng sai cột ra số lệch.
5. **Không `mvnw clean`** (tiến trình java giữ file trong `target/`), **không `git clean -fdX`** (xoá ~985 MB gồm `.gitnexus/`, `node_modules/`).
6. **Không xoá volume.** Diễn tập restore phải vào volume mới — kiểm `docker volume ls` trước.
7. **Không in giá trị secret.**
8. **KHÔNG chạm** `update/docs/claude/**` (reviewer sở hữu) và `task/`, `task-final/`.
9. **Ba mục có thể hỏng âm thầm — Đ7-1, Đ7-4, B-M2.** Với cả ba, tiêu chí là **số đo trước/sau**, không phải test xanh. Test xanh mà số lệch = fail.
10. **Tài liệu trái dữ liệu thật thì DỪNG và báo.**

---

## 9. Báo cáo phải có gì

| Mã | File đã sửa | Dòng thay đổi | Lệnh nghiệm thu | Kết quả thật (dán output) | Lệch so với tài liệu này |
|---|---|---|---|---|---|

Kèm **bảng số đo trước/sau** cho từng mục sau (thiếu bảng này thì mục coi như chưa nghiệm thu):

- **B-M2:** BRANCH/LINE/METHOD trước & sau · số accuracy `UseCaseAccuracyEvalTest` trước & sau
- **F-M6:** `lines.pct` 4 file trước & sau · tổng byte `dist/assets/*.js` trước & sau · `DashboardView-*.js` trước & sau
- **Đ7-1:** repo mẫu + số file · `baseline_parse_ms` (3 số + median) · `parse_ms/total_import_ms` · `after_parse_ms` · node/edge/`CALLS` trước & sau · RSS `docker stats`
- **Đ7-2:** output `restore.ps1` nguyên văn · `restore_wall_clock` phút
- **Đ7-3:** nguyên văn lỗi `--scale backend=2`
- **Đ7-4:** `rows/nodes = k` cho ≥ 2 project · trước & sau · số node/edge JSON trước & sau · kiểm node cô lập

Mục nào **`[không đo được]`**: ghi rõ lý do. Mục nào **DỪNG**: kèm lệnh cho thấy dữ liệu trái tài liệu.

---

## 10. Thứ tự đề nghị

1. **6-F0** — cài coverage frontend. Rẻ, độc lập, cho baseline trước mọi đụng chạm FE.
2. **Đ7-2a** — chương backup vào `DEVOPS-GUIDE.md`. Tài liệu, 0 rủi ro code.
3. **Đ7-2b → 2f** — diễn tập restore thật. **Đây là mục có hậu quả nặng nhất nếu không làm**: mất `postgres-data` = mất control plane vĩnh viễn.
4. **Đ7-3 nhánh A** — tuyên bố giới hạn + nghiệm thu âm `--scale`. Chi phí ~0.
5. **Đ7-4a** — đo `rows/nodes`. Chỉ đo, chưa sửa. Nếu `k` gần 1,0 thì mục Đ7-4 tự động hạ ưu tiên.
6. **Đ7-1a → 1c** — đo baseline parse. Chỉ đo. Nếu tỷ lệ < 30 % thì DỪNG và báo.
7. **B-M2** — nâng BRANCH ≥ 80 % rồi tách. Chờ quyết định câu 1 §7.
8. **F-M6** — 4 file, `api.ts` và `LandingView` sau cùng. Chờ quyết định câu 1 §7.
9. **Đ7-4b → 4e** và **Đ7-1d → 1f** — chỉ làm nếu số đo ở bước 5/6 cho thấy đáng làm.

Ba bước đầu không phụ thuộc bất kỳ quyết định nào — bắt đầu ngay được.
