# KẾ HOẠCH CÁC ĐỢT CÒN LẠI — VibeGraph (13/08/2026)

> **Đối tượng thực thi:** agent Qwen. **Nguồn sự thật finding:** `update/docs/Qwen/AUDIT-REPORT.md` (76 phát hiện) · `update/docs/Qwen/REMEDIATION-PLAN.md` · `update/docs/Qwen/FINAL-REPORT-DOT2-3.md` (chốt Đợt 2/3).
> **Tài liệu này KHÔNG sửa gì trong `update/docs/Qwen/`.** Mọi mâu thuẫn giữa tài liệu Qwen và code thật đều được ghi lại ở §2 kèm bằng chứng, không sửa tài liệu gốc.
> **Trạng thái nền:** Đợt 0 (phần agent) / 1 / 2 / 3 đã đóng. Phần còn lại là mục tiêu của tài liệu này.

---

## 1. Phạm vi & phương pháp

### 1.1. Đã kiểm bao nhiêu

| Nhóm | Số mục | Đã mở file kiểm | Không kiểm được |
|---|---|---|---|
| Trung bình chưa xếp đợt (REMEDIATION-PLAN §6.3) | 9 | **9/9** | 0 |
| Thấp (REMEDIATION-PLAN §6.1) | 26 | **26/26** | 0 |
| Refactor lớn (B-M2, F-M6) | 2 | **2/2** | 0 |
| Khoảng trống chưa vào backlog (codex v2: H3, H4, M1, M5) | 4 | **4/4** | 0 |
| **Tổng** | **41** | **41/41** | **0** |

Không có mục nào phải đánh `[chưa xác minh]` về **sự tồn tại**. Nhóm `[chưa xác minh]` duy nhất trong tài liệu này là các **số đo hiệu năng cần stack đang chạy** (§7.1) — chúng không phải là "chưa xác minh mục có tồn tại", mà là "chưa có baseline đo lường"; xem §7 và §9.

### 1.2. Cách kiểm

1. Lấy `file:dòng` từng mục trong `AUDIT-REPORT.md`.
2. Mở đúng file đó bằng `grep -n` / `sed -n` và đọc code hiện tại — **không** dựa vào bảng trạng thái ✅ trong tài liệu.
3. Với các mục "dead code": đếm tham chiếu thật (`grep -rlw <symbol> src/main src/test --include=*.java` trừ chính file đó).
4. Với số dòng: **`wc -l`** trên Git Bash. **KHÔNG dùng `Get-Content | Measure-Object -Line`** — lệnh đó đếm sai với file có dòng dài (đã được chứng minh sai ở FINAL-REPORT-DOT2-3 §7.3: báo 2.821/2.681 thay cho 3.201/2.958).
5. Với coverage: đọc `target/site/jacoco/jacoco.csv` (bản hiện có, timestamp 13/08 12:05) thay vì suy đoán.
6. Không chạy `mvnw clean` (có tiến trình java giữ file trong `target/`). Không `git add`, không commit.

### 1.3. Kết luận một dòng

**Đợt 4 (9 mục Trung bình) gần như đã trống**: 6/9 đã sửa xong, 2/9 là quyết định của chủ repo, chỉ **1 mục rưỡi** còn việc code thật. Khối lượng thật nằm ở Đợt 5 (26 mục Thấp — tất cả còn tồn tại), Đợt 6 (2 refactor lớn — bị chặn bởi thiếu test), và Đợt 7 (4 khoảng trống — chưa ai đưa vào backlog, trong đó 2 mục là rủi ro mất dữ liệu/vận hành thật).

---

## 2. Mục đã được sửa rồi — GỠ khỏi backlog

Đây là phần giá trị nhất của tài liệu này: **6 mục Trung bình mà REMEDIATION-PLAN §6.3 vẫn liệt là "chưa xếp đợt" thực tế đã được sửa**. Qwen KHÔNG được làm lại các mục này.

| Mã | Bằng chứng đã sửa (file:dòng hiện tại) | Lệnh kiểm lại |
|---|---|---|
| **S-M1** | `src/main/java/com/vibegraph/common/supabase/SupabaseDatabaseConfig.java:35` khai `SCHEMA_PATTERN = "[a-zA-Z_][a-zA-Z0-9_]*"`; `:156` validate fail-fast `if (!properties.getSchema().matches(SCHEMA_PATTERN))` **trước** DDL ghép chuỗi ở `:181`. Chặt hơn đề xuất gốc (`^[a-z0-9_]+$`). Ghi chú: đường dẫn file trong AUDIT-REPORT (`.../config/SupabaseDatabaseConfig.java`) **không đúng** — file thật ở `common/supabase/`. | `grep -n "SCHEMA_PATTERN\|CREATE SCHEMA" src/main/java/com/vibegraph/common/supabase/SupabaseDatabaseConfig.java` |
| **S-M3** | `src/main/java/com/vibegraph/auth/config/SecurityConfig.java:185` → `auth.requestMatchers("/actuator/**").hasRole("ADMIN")`; `:177` giữ `/actuator/health` công khai. | `grep -n actuator src/main/java/com/vibegraph/auth/config/SecurityConfig.java` |
| **S-M4** | `Neo4jGraphRepository.java:498` có `static String escapeLucene(String raw)`; `:474` áp dụng cho tham số `query`; `:484` `catch (ClientException ex)` map về `IllegalArgumentException` (→ HTTP 400). Test riêng: `src/test/java/com/vibegraph/graph/repository/impl/neo4j/Neo4jGraphRepositorySearchEscapeTest.java`. | `grep -n "escapeLucene\|ClientException" src/main/java/com/vibegraph/graph/repository/impl/neo4j/Neo4jGraphRepository.java` |
| **D-M1** | `scripts/dev-up.ps1:34` `docker compose up -d postgres neo4j`; `:42–51` vòng chờ `pg_isready` 60s + thông báo lỗi. | `grep -n "postgres\|pg_isready" scripts/dev-up.ps1` |
| **D-M2** | 4 image trong danh sách gốc đã pin patch: `Dockerfile:4` `maven:3.9.11-eclipse-temurin-21`, `Dockerfile:13` `eclipse-temurin:21.0.5_11-jre-alpine` (runtime, không chỉ builder), `vibegraph-web/Dockerfile:4` `node:22.11.0-alpine`, `:17` `nginx:1.27-alpine`, `docker-compose.yml:26` `neo4j:5.26-community`. **Còn dư 1 image** — xem D-M2r ở §3. | `grep -n "^FROM" Dockerfile vibegraph-web/Dockerfile; grep -n "image:" docker-compose.yml` |
| **D-M3** | `Dockerfile:22` `COPY --from=builder /build/target/app.jar app.jar` (tên tường minh, hết wildcard); `pom.xml:253` `<finalName>app</finalName>`. | `grep -n "COPY --from=builder" Dockerfile; grep -n finalName pom.xml` |

**Ngoài 6 mục trên, các mục sau cũng đã đóng dù REMEDIATION-PLAN chưa đánh dấu:**

| Mã | Bằng chứng | Lệnh kiểm |
|---|---|---|
| **B-M13** (3 file test 0 byte) | `find src/test -name "*.java" -size 0` → **rỗng**. Không còn file test 0 byte nào bị track. | `find src/test -name "*.java" -size 0` |
| **B-M10** (cap node = 0) | `GraphPayloadProperties.java:27` `nodeLimit = 5000`; `GraphController.java:60–62` `clamp(...)`; `.env.example:126` `VIBEGRAPH_GRAPH_NODE_LIMIT=2500`, `:181` `VITE_GRAPH_SAFE_NODE_LIMIT=3000` (trước là `0`). | `grep -n "GRAPH_NODE_LIMIT\|GRAPH_SAFE_NODE_LIMIT" .env.example` |
| **D-M4 phần 1** (pin SHA actions) | `.github/workflows/backend.yml:39,42,56` + `frontend.yml:33,36` — 4 action pin SHA 40 ký tự kèm comment version. Phần 2 (job CD) → §8. | `grep -n "uses:" .github/workflows/*.yml` |

---

## 3. Đợt 4 — Trung bình còn lại (thực tế: 1 mục + 1 mục dư)

Sau khi trừ §2, danh sách 9 mục của REMEDIATION-PLAN §6.3 co lại còn:

| Mã | `file:dòng` hiện tại | Tiêu chí nghiệm thu (đo được) | Lệnh verify | Rủi ro khi sửa |
|---|---|---|---|---|
| **D-M5** | `task/` và `task-final/` — 8 tên file trùng, **cả 16 file đều bị git track** (`git ls-files task/ task-final/` → 16 dòng) | Chỉ còn **một** thư mục được track: `git ls-files task/ task-final/ \| wc -l` = **8** (hiện tại 16). Và: `diff -r task/ task-final/` không còn được gọi vì một bên đã biến mất. Trước khi xóa, phải chứng minh không mất nội dung: với 5 file CSV DIFFER, chạy `diff <(sed '1s/^\xEF\xBB\xBF//' task/csv_exports/X.csv) task-final/csv_exports/X.csv` → **rỗng** (xem "phát hiện mới" dưới bảng) | `git ls-files task/ task-final/ \| wc -l` · `for f in $(cd task-final && find . -type f); do diff -q "task/$f" "task-final/$f"; done` | Xóa nhầm bản mới hơn → mất tài liệu sprint không có bản sao. **Phải chạy diff từng file trước, không `git rm` cả thư mục.** Thêm nữa: `task/final/` (chỉ có ở `task/`, chứa log/pid/json runtime) không có bản đối ứng — nếu chọn giữ `task-final/` thì `task/final/` phải được xử lý riêng |
| **D-M2r** *(mục dư sau D-M2)* | `docker-compose.yml:3` và `database/docker-compose.postgres.yml:20` — cả hai đều `image: postgres:16-alpine` (minor floating, chưa pin patch) | `grep -rn "image: postgres:" docker-compose.yml database/docker-compose.postgres.yml` → mọi dòng có dạng `postgres:16.<patch>-alpine`; sau đó `docker compose up -d postgres` + `docker compose exec -T postgres pg_isready -q` trả exit 0 | `grep -rn "image: postgres:" docker-compose.yml database/docker-compose.postgres.yml` | Pin sai patch → image không tồn tại, compose fail ngay lúc pull (phát hiện tức thì, rủi ro thấp). Pin patch cũ hơn bản đang chạy trong volume → Postgres từ chối mount data dir; phải pin **≥** version đang chạy: `docker compose exec -T postgres postgres --version` |

### Phát hiện mới trong lúc kiểm D-M5 (chưa có trong bất kỳ báo cáo nào)

`task/` vs `task-final/` đã **hội tụ gần hết**, khác REMEDIATION-PLAN mô tả:

```
SAME   PROJECT_DOCUMENTATION_MASTER.md
SAME   VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md
SAME   export_to_csv.py
DIFFER csv_exports/ed_calculation.csv     (19 vs 19 dòng)
DIFFER csv_exports/pps_calculation.csv    (68 vs 68 dòng)
DIFFER csv_exports/product_backlog.csv    (26 vs 26 dòng)
DIFFER csv_exports/release_backlog.csv    (66 vs 66 dòng)
DIFFER csv_exports/sprint_backlog.csv     (235 vs 235 dòng)
```

Khác biệt của 5 file DIFFER **chỉ là BOM UTF-8**, không phải nội dung:

```
$ diff task/csv_exports/ed_calculation.csv task-final/csv_exports/ed_calculation.csv
1c1
< ﻿Tổ chức,1,Đã có nhóm/đội từng áp dụng Scrum thành công?,Có,2
---
> Tổ chức,1,Đã có nhóm/đội từng áp dụng Scrum thành công?,Có,2
```

Bản `task/` có BOM (`\xEF\xBB\xBF`), bản `task-final/` không. Nội dung dòng giống hệt. **Hệ quả:** D-M5 không còn là bài toán "merge từng file bằng diff" như tài liệu mô tả — nó là bài toán một dòng quyết định (giữ bên nào) cộng một lần chuẩn hóa BOM. Nhưng **quyết định giữ bên nào là của chủ repo** (§8), vì operator đã từ chối xóa một lần theo quyết định A3.

---

## 4. Đợt 5 — 26 mục Thấp (tất cả 26 mục ĐỀU CÒN TỒN TẠI)

Đã kiểm 26/26. **Không mục nào đã được sửa.** Chia thành 3 lô theo rủi ro sửa, không theo lĩnh vực báo cáo — vì lô A có thể làm trong một lần với một lệnh nghiệm thu duy nhất.

### 4.1. Lô A — "dọn dẹp một lần": xóa code chết, 0 rủi ro hành vi (5 mục)

Cả 5 mục dưới đây đã được xác minh **0 tham chiếu từ code production**. Gộp thành **một** lần sửa, **một** lần nghiệm thu.

| Mã | `file:dòng` hiện tại | Bằng chứng còn tồn tại |
|---|---|---|
| B-L9 | 6 DTO, tất cả `refs=0`: `common/dto/request/PaginationRequest.java`, `graph/dto/request/AnalyzeRequest.java`, `mcp/dto/request/ClassContextRequest.java`, `mcp/dto/request/LayerPatternRequest.java`, `parser/dto/request/ParseFileRequest.java`, `parser/dto/response/ParseResultResponse.java` | Đã đếm từng class: 0 tham chiếu ngoài chính nó, trong cả `src/main` và `src/test` |
| B-L10 | `src/main/java/com/vibegraph/auth/domain/UserNotification.java` | `grep -rlw UserNotification src/main/java` → **1 file** (chính nó). **GIỮ** bảng `user_notifications` + migration V10 — `JdbcNotificationRepository` vẫn dùng qua JDBC |
| B-L11 | `src/test/java/com/vibegraph/graph/service/TarballImportServiceTest.java` (104 dòng, **8/8 `@Test` đều `@Disabled`**) | Suite thật tồn tại và xanh: `src/test/java/com/vibegraph/graph/service/impl/TarballImportServiceImplTest.java` — 219 dòng, 5 `@Test`, **0 `@Disabled`**. Ghi chú: đường dẫn trong AUDIT-REPORT thiếu `/impl/` |
| B-L3 | `src/main/java/com/vibegraph/auth/service/JwtService.java:57` `public String issue(User user)` | Overload 1 tham số chỉ được gọi từ `JwtServiceTest.java:39,76`. Production dùng bản 2 tham số (`:62`). Xóa overload → phải sửa 2 dòng test |
| B-L4 | `src/main/java/com/vibegraph/parser/visitor/AnnotationVisitor.java:18` `@Deprecated(forRemoval = false)` | Tham chiếu duy nhất: `src/test/java/com/vibegraph/parser/visitor/AnnotationVisitorTest.java`. Production dùng `SpringAnnotationVisitor` (`ParserServiceImpl.java:35,120`) |

**Tiêu chí nghiệm thu lô A (đo được):**
1. `for c in PaginationRequest AnalyzeRequest ClassContextRequest LayerPatternRequest ParseFileRequest ParseResultResponse UserNotification; do find src/main -name "$c.java"; done` → **rỗng**.
2. `find src/test -name TarballImportServiceTest.java` → **rỗng**; `find src/test -name TarballImportServiceImplTest.java` → còn 1 file.
3. `grep -rn "public String issue(User user)" src/main/java/com/vibegraph/auth/service/JwtService.java` → rỗng (nếu chọn xóa).
4. `find src/main -name AnnotationVisitor.java` → rỗng, và `find src/test -name AnnotationVisitorTest.java` → rỗng (xóa kèm test).
5. `grep -rn "user_notifications" src/main/java --include=*.java | wc -l` → **5** (giảm từ 6; 5 hit còn lại thuộc `JdbcNotificationRepository`). Migration V10 **không** thay đổi: `git diff --stat src/main/resources/db/migration` rỗng.
6. `./mvnw -DskipITs test` → BUILD SUCCESS. Số test kỳ vọng: hiện tại **1037, 9 skipped** → sau lô A phải là **skipped ≤ 1** (bỏ 8 `@Disabled` của TarballImportServiceTest) và failures **0**. Trước khi đo: `rm -rf target/surefire-reports` rồi dán nguyên summary Maven (bài học FINAL-REPORT §7.3).
7. `./mvnw verify` — JaCoCo gate `LINE ≥ 0.70` (`pom.xml:310–314`) vẫn xanh. **Lưu ý:** xóa DTO chết làm mẫu số coverage giảm → tỷ lệ thường **tăng**, nhưng phải đo, không giả định.

**Rủi ro lô A:** thấp nhưng không bằng 0. `AnalyzeRequest`/`ParseFileRequest` là DTO request — nếu có endpoint nào deserialize theo tên class qua cấu hình (không qua kiểu Java) thì grep không thấy. Kiểm thêm: `grep -rn "AnalyzeRequest\|ParseFileRequest" src/main/resources --include=*.yaml --include=*.yml --include=*.xml` → phải rỗng trước khi xóa.

### 4.2. Lô B — sửa hành vi nhỏ, cần test riêng (9 mục)

| Mã | `file:dòng` hiện tại | Tiêu chí nghiệm thu (đo được) | Lệnh verify | Rủi ro khi sửa |
|---|---|---|---|---|
| B-L1 | `ParserServiceImpl.java:447` `createParser(Path)` và `:477` `createProjectParser(Path, List<Path>)` — 2 method dựng `CombinedTypeSolver` + `ReflectionTypeSolver` + `JavaSymbolSolver` + `ParserConfiguration(JAVA_21)` giống hệt, chỉ khác cách thu source root | Còn **1** chỗ dựng `new JavaParser(config)`: `grep -c "new JavaParser(config)" src/main/java/com/vibegraph/parser/service/impl/ParserServiceImpl.java` = **1** (hiện tại 2). File giảm từ **571** dòng (`wc -l`) xuống ≤ 545. `./mvnw -Dtest='Parser*Test,MethodVisitorTest' test` xanh | `grep -n "new JavaParser(config)\|private JavaParser create" src/main/java/com/vibegraph/parser/service/impl/ParserServiceImpl.java` | `createParser` được gọi ở `:88` trong nhánh fallback của `parseFileInternal` — gom sai làm mất `JavaParserTypeSolver(sourceRoot)` cho đường parse-một-file → CALLS edge biến mất âm thầm. **Bắt buộc** so số edge trước/sau trên cùng 1 repo mẫu |
| B-L2 | `CachingGraphRepository.java:76–92` `pruneIfOverflowing()` — `while` lồng `for` quét toàn bộ `snapshots` để tìm `loadedAt` nhỏ nhất | Hết vòng lặp lồng: `grep -A20 "private void pruneIfOverflowing" ... \| grep -c "for ("` = **0**. Test mới chứng minh: nạp `MAX_ENTRIES + 3` project rồi assert `snapshots.size() == MAX_ENTRIES` và 3 key cũ nhất đã bị evict | `grep -n -A20 "pruneIfOverflowing" src/main/java/com/vibegraph/graph/repository/impl/CachingGraphRepository.java` | Đổi sang Caffeine `expireAfterWrite` làm mất TTL 5 phút thủ công hiện có ở `:60–62` → hai lớp hết hạn chồng nhau. Nếu dùng Caffeine, phải bỏ hẳn kiểm TTL thủ công, không để cả hai |
| B-L5 | 2 nơi đăng ký CORS: `common/config/CorsConfig.java:24` `implements WebMvcConfigurer` + `:38–39` `addCorsMappings(...).addMapping("/api/**")`; và `SecurityConfig.java:141` `.cors(Customizer.withDefaults())` + `:240` `corsConfigurationSource()` | Còn **1** nguồn: `grep -rn "addCorsMappings" src/main/java` → **rỗng** (giữ `SecurityConfig.corsConfigurationSource`). Nghiệm thu hành vi: preflight `curl -i -X OPTIONS -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: GET" http://localhost:8080/api/projects` vẫn trả `Access-Control-Allow-Origin: http://localhost:5173`; và Origin lạ `https://evil.example` **không** nhận header đó (lặp lại test T8) | `grep -rn "addCorsMappings\|CorsConfigurationSource" src/main/java --include=*.java` | `CorsConfig` (45 dòng) có thể chứa validate startup chống `"*"` mà `SecurityConfig:237` đang dẫn chiếu tới ("`CorsConfig` already rejects at startup"). **Xóa cả file sẽ mất guard đó** — chỉ bỏ `addCorsMappings`, giữ phần validate |
| B-L7 | `ProjectServiceImpl.java:42,53,60,68` — 4 field `@Autowired(required = false)` + `:45` `@Autowired` | `grep -c "@Autowired" src/main/java/com/vibegraph/graph/service/impl/ProjectServiceImpl.java` = **0**; constructor nhận `ObjectProvider<T>` cho 4 dependency optional. `./mvnw -Dtest=ProjectServiceImplTest test` xanh | `grep -n "@Autowired" src/main/java/com/vibegraph/graph/service/impl/ProjectServiceImpl.java` | Đổi optional field → `ObjectProvider` mà quên gọi `.getIfAvailable()` sẽ ném `NoSuchBeanDefinitionException` lúc bootstrap trong profile không có bean đó. Nghiệm thu bắt buộc: khởi động cả profile `dev` **và** `docker` |
| B-L8 | `RequestEventService.java:350–369` — `offer()` khi `freshQueue.offer` thất bại thì `freshQueue.poll()` (`:359`) bỏ event **cũ nhất bất kể loại**, kể cả security event; chỉ có counter `:56` `SECURITY_DROPPED_METRIC` ghi nhận | Test chứng minh ưu tiên: nạp queue đầy bằng event **non-security**, rồi offer 1 security event → assert security event **vẫn nằm trong queue** và counter `security_events.dropped.total` **không tăng**. Đo bằng `MeterRegistry` trong test, không bằng đọc code | `grep -n -A20 "private void offer" src/main/java/com/vibegraph/abuse/RequestEventService.java` | Ưu tiên security event làm non-security bị drop nhiều hơn → mất dữ liệu telemetry thường. Phải đo: cả 2 counter (`request_events.dropped.total` ở `:47` và `security_events.dropped.total`) trước/sau, và ghi rõ tỷ lệ đánh đổi vào comment |
| F-L1 | `LandingView.vue:353` `setTimeout(typeCmd, 20)` và `:355` `setTimeout(...)` — **không** lưu handle; `onBeforeUnmount` (`:499–502`) chỉ `removeEventListener('scroll')`, `clearInterval(stepInterval)`, `stopAutoTour()` | Mọi `setTimeout` trong file có handle được clear: `grep -c "setTimeout" src/views/LandingView.vue` (hiện **6**: dòng 263, 353, 355, 435, 467, 496) và số `clearTimeout`/handle tương ứng phủ hết các timer chạy sau unmount. Test: mount `LandingView`, gọi `typeCommand`, `unmount()`, `vi.advanceTimersByTime(2000)` → **0** cảnh báo Vue và `terminalInput` không thay đổi sau unmount | `grep -n "setTimeout\|clearTimeout\|clearInterval" src/views/LandingView.vue` | `:263` và `:435` là timer trong hàm animation/await — clear quá tay làm mất hiệu ứng. Chỉ clear timer **tự lặp** (typeCmd) và timer sống dài (tourTimeout đã clear ở `:473`) |
| F-L2 | `LandingView.vue:490–493` — 4 `window.addEventListener(..., { once: true })` cho `stopAutoTour` (scroll/mousemove/mousedown/keydown); `onBeforeUnmount:500` chỉ remove `onScroll` | `grep -c "window.addEventListener" src/views/LandingView.vue` = **5** và `grep -c "window.removeEventListener"` = **5** (hiện 5 vs 1). Test: mount → unmount → `window.dispatchEvent(new Event('keydown'))` → `stopAutoTour` không được gọi (spy assert 0 lần) | `grep -n "addEventListener\|removeEventListener" src/views/LandingView.vue` | Rủi ro **thấp hơn** báo cáo mô tả: `{ once: true }` đã tự huỷ listener sau lần fire đầu. Rủi ro thật chỉ là listener sống tới lần event đầu tiên sau unmount. Đừng nâng mức độ mục này |
| F-L3 | `src/components/graph/SearchBar.vue:21` `results = computed(...)` → `:26` `.filter(...)` toàn bộ `props.nodes` rồi `:31` `.slice(0, SEARCH_SUGGESTIONS_LIMIT)`; không có debounce (file 222 dòng) | Số lần `.filter` chạy trên chuỗi keystroke giảm đo được: test dùng `vi.useFakeTimers()`, gõ 10 ký tự trong 100ms → hàm filter (spy qua computed getter hoặc đếm qua watcher) chạy **≤ 2** lần thay vì 10. Đồng thời assert kết quả cuối vẫn đúng như trước debounce | `grep -n "computed\|filter(\|debounce" src/components/graph/SearchBar.vue` | Debounce làm test hiện có trong `src/components/graph/__tests__/SearchBar.spec.ts` (6 case) fail vì kết quả không xuất hiện ngay → phải cập nhật test kèm `await vi.advanceTimersByTime`. Debounce quá dài (>200ms) làm search cảm giác chậm |
| F-L4 | `src/components/projects/ImportProjectPanel.vue:92` `iconPath(id: Method)` với 3 SVG path hardcode ở `:95, :97, :100`; dùng ở `:150` `<path :d="iconPath(tab.id)" />` | `grep -c "'M[0-9]" src/components/projects/ImportProjectPanel.vue` = **0**; 3 path chuyển vào icon registry/`AppIcon`. Nghiệm thu hình ảnh: 3 tab icon vẫn render — snapshot test hoặc `expect(wrapper.findAll('path')).toHaveLength(3)` với `d` không rỗng | `grep -n "iconPath\|'M[0-9]" src/components/projects/ImportProjectPanel.vue` | Thấp. Rủi ro duy nhất: registry icon chưa tồn tại thì mục này biến thành "tạo hạ tầng icon" — kiểm trước: `ls vibegraph-web/src/components/ui/AppIcon.vue` |

### 4.3. Lô C — chấp nhận có ý thức / chỉ cần tài liệu (12 mục)

Các mục này **còn tồn tại** nhưng chi phí sửa cao hơn giá trị, hoặc bản chất là đánh đổi thiết kế. Đề xuất: **không sửa code, chỉ ghi nhận** — trừ D-L3/D-L4 rẻ và rõ ràng.

| Mã | `file:dòng` hiện tại | Tiêu chí nghiệm thu (đo được) | Lệnh verify | Rủi ro khi sửa |
|---|---|---|---|---|
| B-L6 | `database/ERD.md:64–67` — mục "Bảng có thể thêm sau (chưa cần Phase 1)" vẫn liệt `refresh_tokens` và `audit_log` dù cả hai đã tồn tại (V18, V10) | Mọi bảng nêu trong "có thể thêm sau" **không** xuất hiện trong migration: `for t in refresh_tokens audit_log teams team_members; do echo "$t: $(grep -rlc "CREATE TABLE.*$t" src/main/resources/db/migration \| wc -l)"; done` — mục nào ≥1 phải bị chuyển sang phần "đã có" | `sed -n '60,70p' database/ERD.md; grep -rln "refresh_tokens\|audit_log" src/main/resources/db/migration` | Không có rủi ro code. Rủi ro duy nhất: sửa ERD sai tên bảng/cột thành tài liệu lệch mới. Đối chiếu từng cột với migration thật, đừng viết từ trí nhớ |
| S-L1 | `DiagramPanel.vue:500` và `:583` `v-html="renderedSvg"`; `CodeViewerModal.vue:242` `v-html="highlightedHtml"` | Nếu chọn sửa: `renderedSvg` đi qua DOMPurify với `USE_PROFILES: {svg: true}`; test nạp SVG chứa `<script>alert(1)</script>` + `onload=` → assert DOM sau render **0** thẻ `script` và **0** attribute `on*`. Nếu chọn không sửa: thêm comment tại 3 dòng nêu rõ nguồn dữ liệu là server-escape | `grep -rn "v-html" vibegraph-web/src/` | DOMPurify strip mất element SVG hợp lệ (marker, foreignObject) làm diagram vỡ → phải so ảnh render trước/sau trên ≥3 diagram thật. Thêm dependency mới vào bundle frontend (~20KB) |
| S-L2 | `CookieCsrfFilter.java:25` `CLIENT_HEADER = "X-VibeGraph-Client"`; `:67` chỉ so `equalsIgnoreCase(request.getHeader(...))` | Chỉ nghiệm thu khi có tính năng nhạy cảm mới. Tiêu chí lúc đó: token CSRF per-session, test chứng minh request thiếu/sai token bị **403**, request đúng token qua | `grep -n "CLIENT_HEADER\|getHeader" src/main/java/com/vibegraph/auth/web/CookieCsrfFilter.java` | Chuyển sang token CSRF **phá vỡ mọi client hiện có** (frontend, MCP, API key). Đây là breaking change toàn hệ — không làm trong đợt dọn dẹp |
| S-L3 | `RateLimitFilter.java:17,234` — bucket dựng bằng Caffeine in-process | Trùng Đợt 7-M5 (§7.3). Nghiệm thu chung ở đó | `grep -n "Caffeine" src/main/java/com/vibegraph/abuse/RateLimitFilter.java` | Thêm Redis = thêm một điểm chết mới cho luồng auth. Chỉ làm cùng quyết định scale ngang |
| S-L4 | `JwtAuthFilter.java:41` `static final Map<UUID, Long> ACTIVE_USERS = new ConcurrentHashMap<>()`; `:61` chỉ `removeIf` khi **có người đọc** `:62` | Test: put 10.000 entry với timestamp quá hạn, **không** gọi hàm đọc, chờ TTL, assert `ACTIVE_USERS.size()` giảm (chứng minh dọn không phụ thuộc lượt đọc). Hoặc chuyển Caffeine `expireAfterWrite` và assert qua `cache.estimatedSize()` | `grep -n "ACTIVE_USERS" src/main/java/com/vibegraph/auth/web/JwtAuthFilter.java` | `static` map trong filter là trạng thái chia sẻ toàn JVM — đổi sang Caffeine đổi luôn ngữ nghĩa "active user count" mà admin dashboard đang đọc. Phải kiểm endpoint nào phơi số này trước |
| S-L5 | `GitHubUrlParser.java:15` `SEGMENT = Pattern.compile("[A-Za-z0-9_.-]+")` (cho phép `.` và `..`); `:40` áp cho owner+repo. Kèm `GitHubTarballClient.java:33` và `GitHubPreFlightService.java:33` đều `HttpClient.Redirect.NORMAL` | Test parser: `parse("https://github.com/../x")`, `parse("https://github.com/./x")`, `parse("https://github.com/x/..")` → cả 3 ném exception. Assert bằng `assertThrows`, không bằng đọc regex | `grep -n "SEGMENT\|Redirect" src/main/java/com/vibegraph/graph/importer/github/*.java` | Siết regex quá tay chặn repo tên hợp lệ có dấu chấm (`foo.js`, `bar.github.io`) → import repo thật fail. Chỉ chặn segment **bằng đúng** `.` hoặc `..`, không chặn dấu chấm nói chung |
| D-L1 | `docker-compose.yml:4,27,58,162` — 4 `container_name` cố định (`vibegraph-postgres/neo4j/backend/frontend`) | Trùng Đợt 7-M5 (§7.3): chỉ bỏ `container_name` **khi** thật sự chạy nhiều replica. Nghiệm thu lúc đó: `docker compose up -d --scale backend=2` khởi động 2 container, không lỗi tên trùng | `grep -n container_name docker-compose.yml` | Bỏ `container_name` làm **hỏng mọi lệnh trong tài liệu vận hành** (`docker logs vibegraph-backend`, `docker compose exec` trong `DEVOPS-GUIDE.md:51,270,289`, và `scripts/dev-up.ps1:51,57`). Phải sửa tài liệu + script cùng lúc |
| D-L2 | `database/docker-compose.postgres.yml:26–28` — fallback `${POSTGRES_USER:-vibegraph}` / `${POSTGRES_PASSWORD:-vibegraph}` | Đề xuất gốc là "giữ nguyên + comment". Nghiệm thu: có comment cảnh báo ngay trên block `environment:` nêu rõ chỉ dùng local; `grep -n -B3 "POSTGRES_PASSWORD" database/docker-compose.postgres.yml` thấy dòng cảnh báo | `sed -n '20,32p' database/docker-compose.postgres.yml` | Đổi fallback thành `${VAR:?}` fail-fast làm compose phụ **này** không chạy được mà không có `.env` — file này là tiện ích dev độc lập, siết sẽ làm hỏng đường dev |
| D-L3 | Root workspace: **28 file, 106 MB** (đo `du -ch *.log *.json *.diff *.stackdump`). Lớn nhất: `backend_run.log` 72.212 KB, `backend.out.log` 13.656 KB, `backend-run.log` 10.860 KB, `backend-dev.out.log` 5.360 KB, `graph_check.json` 2.876 KB, `scratch.diff` 548 KB, 3 file `replay_pid*.log` 440–712 KB | `du -ch *.log *.json *.diff *.stackdump 2>/dev/null \| tail -1` → **0 hoặc thư mục không còn file khớp**; và `git status --porcelain` **không** xuất hiện file mới bị xóa (chứng minh chúng chưa từng được track) | `ls -1 *.log *.json *.diff *.stackdump \| wc -l` (hiện **28**) · `du -ch *.log *.json *.diff *.stackdump \| tail -1` | ⚠️ **KHÔNG dùng `git clean -fdX`** — xóa ~985 MB gồm `target/`, `node_modules/`, `.gitnexus/`, `.vibegraph/`. Nguy hiểm riêng ở đây: `.vibegraph/` chứa **script vận hành thật** (`cleanup-t6-remnants.ps1`, `dot0-git-cleanup.ps1`, `run-backend-smoke.ps1`) và ảnh bằng chứng (`landing-before.png`, `landing-after.png`) — mất là mất vĩnh viễn. Xóa đích danh bằng danh sách 28 file, không dùng wildcard đệ quy |
| D-L4 | `.gitignore:5–6` ignore `quick-start-win.ps1` và `quick-start-mac.sh`; **cả 2 file có thật ở root** (`quick-start-mac.sh` 9.234 B, `quick-start-win.ps1` 13.251 B) | `git check-ignore -v quick-start-win.ps1 quick-start-mac.sh` → **exit 1** (không còn bị ignore); `git status --porcelain \| grep quick-start` → hiện 2 dòng `??` (sẵn sàng track). **Không** `git add` trong đợt này (luật: không commit) | `git check-ignore -v quick-start-win.ps1 quick-start-mac.sh` | Bỏ ignore rồi track sẽ đưa 2 script vào repo — phải grep secret trước: `grep -nE "password\|secret\|token\|GOCSPX\|AIza" quick-start-win.ps1 quick-start-mac.sh`. **Nếu có secret, DỪNG và báo chủ repo**, đừng track |
| D-L5 | `logs/` (6 file: backend/frontend `.out.log`/`.err.log` + 1 cặp có timestamp) và `.vibegraph/` (backend-8080/frontend-5173 log + script + ảnh + `eval-repos`) — hai convention log runtime song song | Một convention duy nhất được **script** dùng: `grep -rn "logs/\|\.vibegraph/" scripts/*.ps1` chỉ trả về một prefix. Nghiệm thu hành vi: chạy `scripts/dev-up.ps1` trên máy sạch → log mới chỉ xuất hiện ở đúng một thư mục | `ls logs/ .vibegraph/` · `grep -rn "logs/\|\.vibegraph/" scripts/` | `.vibegraph/` **không chỉ là log** — nó chứa script + ảnh bằng chứng + `eval-repos`. Chuẩn hóa "về một chỗ" mà hiểu sai sẽ xóa dữ liệu không phải log. Chỉ hợp nhất **đường ghi log**, giữ nguyên phần còn lại |
| D-L6 | `vibegraph-web/Dockerfile:6–11` — `ARG VITE_API_URL`/`ARG VITE_WS_URL` → `ENV` → bake vào bundle lúc `npm run build` (`:15`) | Nếu chọn runtime injection: đổi môi trường **không** cần rebuild — nghiệm thu bằng `docker run -e VITE_API_URL=https://a ... ` rồi `docker run -e VITE_API_URL=https://b ...` **trên cùng image digest** (`docker images --digests`), và `curl` trang trả về đúng origin tương ứng. Nếu chọn tài liệu hóa: `grep -n "VITE_API_URL" DEVOPS-GUIDE.md` ≥ 1 và đoạn đó nêu rõ "đổi giá trị ⇒ rebuild image" | `sed -n '1,20p' vibegraph-web/Dockerfile` | Runtime injection cho Vite đòi placeholder + entrypoint thay thế trong file JS đã build — kỹ thuật này dễ hỏng CSP đang được nginx phát (`nginx.conf.template`). **Đề xuất chọn nhánh tài liệu hóa**, không đổi kiến trúc build |

---

## 5. Đợt 6 — Refactor lớn (B-M2, F-M6)

### 5.1. Số dòng THẬT (đo bằng `wc -l` trên Git Bash, 13/08/2026)

**B-M2 — `src/main/java/com/vibegraph/diagram/service/impl/UseCaseInferenceEngine.java`: 1398 dòng** (khớp chính xác AUDIT-REPORT).

**F-M6 — mọi file `.vue`/`.ts` trong `vibegraph-web/src` vượt 400 dòng (trừ `__tests__`), 30 file:**

| Dòng | File |
|---|---|
| 3201 | `src/views/admin/UserDetailDrawer.vue` |
| 2958 | `src/views/LandingView.vue` |
| 1524 | `src/views/admin/DashboardView.vue` |
| 1468 | `src/components/graph/GraphCanvas.vue` |
| 1123 | `src/views/admin/UsersTableView.vue` |
| 1046 | `src/views/admin/SecurityView.vue` |
| 1037 | `src/composables/useSigma.ts` |
| 992 | `src/views/admin/PlansCreditsView.vue` |
| 987 | `src/lib/api.ts` |
| 968 | `src/components/diagram/DiagramPanel.vue` |
| 810 | `src/views/admin/FeatureFlagsView.vue` |
| 803 | `src/views/admin/AnnouncementsView.vue` |
| 791 | `src/views/user/ReportsView.vue` |
| 782 | `src/stores/admin.ts` |
| 706 | `src/views/admin/AdminReportsView.vue` |
| 698 | `src/components/layouts/UserLayout.vue` |
| 642 | `src/components/panels/ImpactAnalysisPanel.vue` |
| 603 | `src/components/projects/AddProjectArchive.vue` |
| 589 | `src/views/user/ProjectsView.vue` |
| 564 | `src/components/panels/NodeDetailPanel.vue` |
| 556 | `src/components/panels/CodeViewerModal.vue` |
| 547 | `src/types/api.ts` |
| 542 | `src/lib/dataFlow.ts` |
| 537 | `src/views/user/ApiKeysView.vue` |
| 532 | `src/views/user/NotificationsView.vue` |
| 521 | `src/components/layouts/AdminLayout.vue` |
| 487 | `src/lib/umlUseCaseSvg.ts` |
| 450 | `src/stores/account.ts` |
| 433 | `src/views/admin/AuditView.vue` |
| 431 | `src/components/projects/GitHubImportForm.vue` |

**Ba đính chính so với AUDIT-REPORT F-M6:**

1. **Đường dẫn sai:** báo cáo ghi `UserDetailDrawer.vue` và `DashboardView.vue` không kèm đường dẫn; cả hai thật ra ở `src/views/admin/`, **không** ở `src/components/admin/`.
2. **Số dòng lệch 1:** `wc -l` hôm nay cho `3201 / 2958`, báo cáo ghi `3.202 / 2.959`. Nguyên nhân đã được FINAL-REPORT-DOT2-3 §7.3 truy: cách đếm trailing newline. `GraphCanvas` 1468 (báo cáo 1.469), `UsersTableView` 1123 (báo cáo 1.115 — **lệch 8, tăng thật** do H11 thêm 5 khối try/catch + banner lỗi), `DiagramPanel` 968 (báo cáo 869 — **lệch 99, tăng thật**), `api.ts` 987 (báo cáo 990 — giảm 3 do F-M3 gỡ axios).
3. **Danh sách 10 file là thiếu:** thực tế **30 file** vượt 400 dòng. Nếu tiêu chí nghiệm thu là "≤ 400 dòng", phạm vi F-M6 gấp 3 lần báo cáo mô tả. **Đề xuất: giới hạn F-M6 vào 4 file trên 1.400 dòng**, phần còn lại chuyển thành quy tắc "file mới không vượt 400 dòng" thay vì refactor hồi tố.

### 5.2. Điều kiện tiên quyết — coverage của CHÍNH các file đó

> **Nguyên tắc bắt buộc (giữ nguyên từ REMEDIATION-PLAN):** phải có test bao phủ **trước** khi tách file. Tách trước, test sau = không có gì chứng minh hành vi không đổi.

**Backend (B-M2) — coverage đã có, ĐO ĐƯỢC ngay:**

Đọc `target/site/jacoco/jacoco.csv` (bản hiện có, timestamp 13/08 12:05), dòng `UseCaseInferenceEngine`:

```
INSTRUCTION_MISSED=391  INSTRUCTION_COVERED=3780
BRANCH_MISSED=236       BRANCH_COVERED=544
LINE_MISSED=91          LINE_COVERED=589
METHOD_MISSED=7         METHOD_COVERED=59
```

→ **LINE 86,6 %** (589/680) · **BRANCH 69,7 %** (544/780) · **METHOD 89,4 %** (59/66).

Lệnh tự đo lại (không được `mvnw clean`):
```bash
./mvnw -DskipITs test        # sinh lại jacoco.exec + báo cáo
grep "^VibeGraph,com.vibegraph.diagram.service.impl,UseCaseInferenceEngine," target/site/jacoco/jacoco.csv
```

Test đang phủ: `src/test/java/com/vibegraph/diagram/eval/UseCaseAccuracyEvalTest.java` và `src/test/java/com/vibegraph/diagram/service/UmlUseCaseServiceTest.java`.

**Kết luận B-M2: coverage LINE 86,6 % là ĐỦ để bắt đầu tách.** Điểm yếu là BRANCH 69,7 % — 236 nhánh chưa phủ chính là chỗ heuristic dễ lệch nhất khi tách. **Cửa vào (gate) trước khi tách:** nâng BRANCH lên **≥ 80 %** (`BRANCH_MISSED ≤ 156`) cho riêng class này, đo bằng lệnh trên.

**Frontend (F-M6) — coverage KHÔNG ĐO ĐƯỢC HÔM NAY. Đây là mục chặn.**

- `vibegraph-web/package.json` **không có** `@vitest/coverage-v8` / `@vitest/coverage-istanbul` (`grep -n "coverage" package.json` chỉ trả về `@vitest/eslint-plugin`).
- Không có block `coverage` trong `vitest.config.ts`/`vite.config.ts`.
- Script duy nhất: `"test:unit": "vitest"` — **không** có `test:coverage`.
- **2**/10 file trong danh sách F-M6 **không có file test nào**: `LandingView.vue` (2958 dòng), `SecurityView.vue` (1046 dòng) — kiểm bằng `find vibegraph-web/src -name 'LandingView.spec.ts'` → 0, tương tự `SecurityView`.

> **Đính chính 13/08 (Qwen phản biện, đã kiểm):** bản đầu ghi `lib/api.ts` cũng 0 test — **sai**. `grep -rl "from '\.\./api'\|from '@/lib/api'" --include=*.spec.ts` → **18 spec** import từ nó (`graphApi.spec.ts`, `importApi.spec.ts`, `DiagramPanel`, `ImpactAnalysisPanel`, `AddProjectArchive`, `useGitHubImport`, auth store…). `api.ts` có coverage **từng phần theo domain**, không phải blind.

**Cửa vào bắt buộc cho F-M6 (theo thứ tự, không được bỏ bước):**

| Bước | Việc | Tiêu chí nghiệm thu (đo được) |
|---|---|---|
| 6-F0 | Cài công cụ coverage: thêm `@vitest/coverage-v8`, block `test.coverage` (provider v8, reporter `text` + `json-summary`), script `"test:coverage": "vitest run --coverage"` | `npm run test:coverage` chạy được và in bảng; `coverage/coverage-summary.json` tồn tại |
| 6-F1 | Lấy baseline từng file mục tiêu | `node -e "const s=require('./coverage/coverage-summary.json');for(const k in s){if(/UserDetailDrawer\|LandingView\|DashboardView\|GraphCanvas/.test(k))console.log(k,s[k].lines.pct)}"` → in ra 4 số. **Ghi lại 4 số này vào báo cáo** — đây là baseline |
| 6-F2 | Viết test cho file chưa có test, **trước khi tách** | Mỗi file mục tiêu đạt `lines.pct ≥ 70`. `LandingView`/`SecurityView` hiện **0 test** → phải tạo mới. `api.ts` **đã có 18 spec import** → đo baseline trước, chỉ bổ sung domain chưa phủ, **không viết lại từ đầu** |
| 6-F3 | Tách file | Sau tách: `lines.pct` của **tổng** các file con **≥** baseline của file gốc (không giảm); `vitest run` 533+ test xanh; `vue-tsc --build` sạch; `npm run build` OK |
| 6-F4 | Chứng minh không đổi hành vi bundle | `npm run build` trước/sau, so tổng byte `dist/assets/*.js`: chênh lệch **≤ 3 %**. Riêng `DashboardView-*.js` hiện **582.351 B** (đo phiên 13/08) — sau tách phải **giảm**, và phải nêu con số thật |

**Rủi ro khi sửa Đợt 6:**

| Rủi ro | Chi tiết |
|---|---|
| B-M2: heuristic lệch âm thầm | `UseCaseInferenceEngine` là suy luận heuristic — tách sai thứ tự áp rule làm kết quả use-case đổi mà **không** test nào fail (vì test dựa trên eval accuracy, không phải equality). **Bắt buộc:** chạy `UseCaseAccuracyEvalTest` trước/sau và so **số accuracy**, không chỉ pass/fail |
| B-M2: 8 nested type | Class có 8 kiểu lồng (`ActorGuess`, `DomainAgg`, `AuthKind`, `ClassFallback`, `DomainGuess`, `InferenceResult`, `Endpoint`, …) — tách ra ngoài đổi visibility, có thể vỡ `UmlUseCaseServiceTest` |
| F-M6: `UserDetailDrawer` 3201 dòng | File lớn nhất repo. Tách thành sub-panel (quota / API keys / sessions) làm vỡ prop drilling và emit chain; test hiện có (`__tests__/UserDetailDrawer.spec.ts`) chỉ 1 file cho 3201 dòng → coverage baseline gần chắc là thấp |
| F-M6: `LandingView` 0 test | 2958 dòng **không có bất kỳ test nào**, và chính file này chứa F-L1/F-L2 (timer/listener). Tách trước khi có test = tách blind |
| F-M6: tách `api.ts` | 987 dòng, là đường ra duy nhất tới backend cho ~40 endpoint. **Không phải "0 test"** (18 spec import từ nó — xem đính chính §5.2), nhưng coverage là từng phần theo domain, chưa đo được toàn cục cho tới khi có bước 6-F0. Đề xuất: **hoãn `api.ts` sau cùng** vì bề rộng ảnh hưởng, không vì thiếu test |

---

## 6. Đợt 7 — 4 khoảng trống chưa ai đưa vào backlog

4 mục này có trong `docs/audit-report-v2-2026-08-12.md` (codex v2) nhưng **không** có mã tương ứng trong `AUDIT-REPORT.md` của Qwen, nên không nằm trong bất kỳ đợt nào. Đã kiểm cả 4 trên code hiện tại: **cả 4 đều còn đúng**.

### 6.1. Đ7-1 — Parse CPG tuần tự (bottleneck import lớn nhất)

**Xác minh còn tồn tại:** `src/main/java/com/vibegraph/parser/service/impl/ParserServiceImpl.java:409–445` (method `parseProject`). Vòng lặp thật:

```java
ProjectSymbolRegistry projectSymbols = ProjectSymbolRegistry.fromFiles(javaFiles);   // :423
JavaParser parser = createProjectParser(projectRoot, javaFiles);                      // :424
int parsed = 0;
for (Path javaFile : javaFiles) {                                                     // :426  ← tuần tự
    ParseResult result = parseFileInternal(javaFile, parser, projectSymbols);          // :428  ← 1 parser dùng chung
    ...
}
```

`grep -n "parallelStream\|ExecutorService\|CompletableFuture\|ForkJoin" ParserServiceImpl.java` → **rỗng**. Một `JavaParser` + một `CombinedTypeSolver` dùng chung cho toàn bộ file, một luồng.

**Điều kiện tiên quyết đã kiểm — thuận lợi:** `src/main/java/com/vibegraph/parser/ProjectSymbolRegistry.java` (130 dòng) là **immutable** (`:24` `this.typeFullNames = Set.copyOf(typeFullNames)`) và truyền qua `ThreadLocal` (`:20` `private static final ThreadLocal<ProjectSymbolRegistry> CURRENT`, `:67` `Scope open(...)`). Nghĩa là registry **không** phải rào cản — mỗi thread chỉ cần mở `Scope` riêng. Rào cản thật là `CombinedTypeSolver`/`JavaSymbolSolver` dùng chung (JavaParser symbol solver có cache nội bộ không cam kết thread-safe).

**Ước lượng tác động:** codex v2 nêu parse chiếm **~65 % thời gian import** (`docs/audit-report-v2-2026-08-12.md:113`). Con số này `[chưa xác minh]` — không có phép đo nào trong repo chứng minh. **Phải đo trước khi sửa**, xem tiêu chí dưới.

**Tiêu chú nghiệm thu — số đo, KHÔNG phải "nhanh hơn":**

| Bước | Lệnh / cách đo | Tiêu chí |
|---|---|---|
| Đ7-1a | Chọn **một** repo mẫu cố định và ghi lại: đường dẫn + số file `.java` (`find <repo> -name "*.java" \| wc -l`) + commit SHA của repo mẫu. Đề xuất dùng chính VibeGraph (`find src -name "*.java" \| wc -l`) để tái lập được | Repo mẫu và số file được ghi vào báo cáo |
| Đ7-1b | **Baseline trước sửa:** import repo mẫu 3 lần, mỗi lần đo wall-clock của giai đoạn parse. Lấy **median 3 lần**. Đo bằng log `ParserServiceImpl:415` (`Found {} .java files`) → thời điểm `listener.onFileParsed(total, total)`, hoặc thêm log tạm ghi `System.nanoTime()` 2 đầu | Báo cáo ghi: `baseline_parse_ms = <median>` (3 số thô kèm theo) |
| Đ7-1c | **Đo tỷ lệ parse/tổng import** trên cùng lần chạy | Báo cáo ghi `parse_ms / total_import_ms = <x> %` — con số này **thay thế** claim "~65 %" chưa xác minh |
| Đ7-1d | Sau khi song song hóa: đo lại **cùng repo mẫu, cùng máy, 3 lần, median** | `after_parse_ms ≤ 0,6 × baseline_parse_ms` trên máy ≥ 4 core. Nếu không đạt, **giữ nguyên bản tuần tự** và báo lý do |
| Đ7-1e | **Tính đúng đắn — quan trọng hơn tốc độ:** so kết quả graph trước/sau | Số node **bằng nhau tuyệt đối**; số edge **bằng nhau tuyệt đối**; đặc biệt số edge `CALLS`. Đo bằng Cypher: `MATCH (n:Symbol {projectId:$p}) RETURN count(n)` và `MATCH (:Symbol {projectId:$p})-[r:CALLS]->(:Symbol {projectId:$p}) RETURN count(r)`. **Lệch 1 edge = fail** |
| Đ7-1f | Chạy song song 2 lần liên tiếp trên cùng repo | Kết quả node/edge của 2 lần **giống nhau** (chứng minh không có non-determinism do race) |

**Rủi ro khi sửa:**
- `JavaSymbolSolver` + `CombinedTypeSolver` chia sẻ giữa nhiều thread có cache nội bộ — race làm `resolve()` trả sai type → **CALLS edge biến mất hoặc trỏ sai**, và điều này **không** làm test nào fail. Đây là lý do Đ7-1e là tiêu chí cứng, không phải tùy chọn.
- Mỗi thread một `JavaParser` = mỗi thread một `JavaParserTypeSolver` index toàn bộ source root → RAM × số thread. Container backend **không có `mem_limit`** (chỉ Neo4j có, `docker-compose.yml`) → pool 8 thread trên repo lớn có thể OOM-kill container. **Phải bound pool** theo `Runtime.availableProcessors()` và đo RSS bằng `docker stats` trước/sau.
- `progressListener.onFileParsed(parsed, total)` (`:441`) đếm bằng biến `int parsed` không đồng bộ → song song hóa mà quên `AtomicInteger` sẽ làm progress WebSocket nhảy lùi/mất số.
- `results.add(...)` (`:429`, `:433`) trên `ArrayList` (`:410`) — **không** thread-safe. Bắt buộc đổi sang collector an toàn.

### 6.2. Đ7-2 — Không có chiến lược backup/restore cho 3 kho dữ liệu

**Xác minh còn tồn tại:**
- 4 named volume trong `docker-compose.yml:184–188`: `postgres-data`, `neo4j-data`, `neo4j-logs`, `upload-workspaces`.
- `grep -n -i "pg_dump\|neo4j-admin\|backup\|restore" DEVOPS-GUIDE.md` → **1 hit duy nhất**, dòng 138, và nó **không phải** về backup: *"fixed, then restore the original limit"* (nói về hạ/nâng lại một giới hạn cấu hình).
- `DEVOPS-GUIDE.md` có 19 heading (`grep -n "^#"`), **không** heading nào về backup/restore.
- `DEPLOYMENT.md` chỉ **34 dòng**; dòng 30 nhắc "Persisted Neo4j data and the upload workspace live in named volumes" nhưng **không** có `pg_dump`, `neo4j-admin`, hay quy trình restore.

**Ước lượng tác động:** mất volume `postgres-data` = mất **toàn bộ control plane vĩnh viễn**: user, ownership project, API key, quota, audit log, refresh session. Graph Neo4j có thể phân tích lại từ source; control plane **không thể**. Đây là mục có hậu quả nặng nhất trong toàn bộ tài liệu này, và là mục duy nhất **chưa ai đưa vào bất kỳ đợt nào**.

**Tiêu chí nghiệm thu — phải là một lần restore THÀNH CÔNG, không phải một chương tài liệu:**

| Bước | Lệnh / cách đo | Tiêu chí |
|---|---|---|
| Đ7-2a | Viết chương backup vào `DEVOPS-GUIDE.md` | `grep -c "pg_dump" DEVOPS-GUIDE.md` ≥ 1 · `grep -c "neo4j-admin database dump" DEVOPS-GUIDE.md` ≥ 1 · có mục nêu cách snapshot volume `upload-workspaces` |
| Đ7-2b | Tạo backup thật | `docker compose exec -T postgres pg_dump -U $POSTGRES_USER $POSTGRES_DB > backup.sql` → `wc -c backup.sql` **> 0** và `grep -c "CREATE TABLE" backup.sql` ≥ số bảng trong `src/main/resources/db/migration` |
| Đ7-2c | **Diễn tập restore (bắt buộc)** — vào stack SẠCH, không phải stack đang chạy | Trước: ghi lại `SELECT count(*) FROM users`, `count(*) FROM projects`, `count(*) FROM api_keys`. Sau restore vào volume mới: **cả 3 số bằng nhau tuyệt đối**.<br>**Đính chính 13/08 (Qwen phản biện, đã kiểm):** bản đầu viết `FROM project_ownership` — **bảng đó không tồn tại**. Entity `ProjectOwnership` map vào `@Table(name = "projects")` (`auth/domain/ProjectOwnership.java:26`); `information_schema.tables` chỉ có `projects`. Để nguyên thì buổi diễn tập dừng ngay câu SQL đầu với `relation "project_ownership" does not exist` |
| Đ7-2d | Restore Neo4j | `neo4j-admin database load` vào instance sạch, rồi `MATCH (n:Symbol) RETURN count(n)` — bằng số trước dump |
| Đ7-2e | Backend nối được vào dữ liệu đã restore | `docker compose up -d` → cả 4 service `healthy` (`docker compose ps`); login bằng một tài khoản có trước backup **thành công** (HTTP 200 + cookie `vg_session`) |
| Đ7-2f | Ghi lại RTO thật | Báo cáo ghi `restore_wall_clock = <phút>` đo bằng đồng hồ, kèm dung lượng dump. Không ghi "nhanh" |

**Rủi ro khi sửa:**
- **Diễn tập restore vào stack đang chạy sẽ phá dữ liệu dev thật.** Bắt buộc dùng project name riêng (`docker compose -p vibegraph-restore-test`) và volume riêng. Trước khi bắt đầu: xác nhận volume đích **không** phải `postgres-data` đang dùng (`docker volume ls`).
- `pg_dump` trong tài liệu mà không nêu **credential lấy từ đâu** sẽ dẫn tới việc người vận hành hardcode password vào cron → tạo ra chính lỗi S1 mà Đợt 0 vừa dọn. Chương backup **phải** dùng biến môi trường/secret store, không giá trị thật.
- Nếu chương backup ghi kèm ví dụ đầu ra thật, **không được** dán nội dung có credential/PII vào `DEVOPS-GUIDE.md`.

### 6.3. Đ7-3 — Chỉ chạy được single-replica, và điều đó không được ghi ở đâu

**Xác minh còn tồn tại:**
- `DEPLOYMENT.md` (34 dòng): `grep -n -i "replica\|scale" DEPLOYMENT.md` → **0 hit**. Không có tuyên bố "single-replica only".
- `DEVOPS-GUIDE.md:144` có nêu đúng bản chất — *"enforcement is per instance: N replicas allow up to N times the configured rate"* — nhưng đó chỉ là ghi chú về rate-limit, **không** phải một tuyên bố kiến trúc, và không nằm trong tài liệu triển khai.
- Trạng thái per-instance vẫn còn thật: `RateLimitFilter.java:17,234` (Caffeine in-process — cũng chính là S-L3), `JwtAuthFilter.java:41` `static ACTIVE_USERS`, và `docker-compose.yml:4,27,58,162` gán `container_name` cố định (cũng chính là D-L1) khiến `--scale` không chạy được về mặt cơ học.

**Điểm đáng chú ý:** S-L3 và D-L1 trong backlog Qwen là **hai triệu chứng** của cùng mục này. Sửa lẻ S-L3 (thêm Redis) mà không quyết định kiến trúc là làm ngược thứ tự.

**Ước lượng tác động:** thấp **cho tới khi** ai đó chạy `--scale backend=2`. Lúc đó: rate-limit nới N lần (bề mặt DoS quay lại đúng chỗ H13 vừa đóng), phiên WebSocket rơi ngẫu nhiên vì SimpleBroker in-process, và `ConcurrentImportGuard` cho phép N lần số import đồng thời. Chi phí sửa **đúng cách** cao (Redis + broker ngoài); chi phí **ghi rõ giới hạn** gần bằng 0.

**Tiêu chí nghiệm thu — chọn nhánh A (khuyến nghị) hoặc B:**

**Nhánh A — tuyên bố giới hạn (rẻ, khuyến nghị làm ngay):**
1. `grep -c -i "single-replica" DEPLOYMENT.md` ≥ 1, và đoạn đó **liệt kê đích danh** 4 thành phần chặn: rate-limit Caffeine per-instance (`RateLimitFilter.java:234`), `ACTIVE_USERS` static (`JwtAuthFilter.java:41`), SimpleBroker in-process, `container_name` cố định (`docker-compose.yml:4,27,58,162`).
2. Nghiệm thu **âm** — chứng minh giới hạn là thật, không phải phỏng đoán: `docker compose up -d --scale backend=2` → **fail** với lỗi tên container trùng. Dán nguyên văn lỗi vào báo cáo.
3. S-L3 và D-L1 trong Đợt 5 lô C được đánh dấu "phụ thuộc quyết định Đ7-3", không sửa lẻ.

**Nhánh B — thật sự hỗ trợ nhiều replica (đắt, cần chủ repo quyết — xem §8):**
1. Bỏ `container_name` (kéo theo sửa `DEVOPS-GUIDE.md:51,270,289` và `scripts/dev-up.ps1:51,57`).
2. Rate-limit chuyển shared store. Nghiệm thu: `--scale backend=2`, gửi **240** request cùng user chia đều 2 replica → **429 xuất hiện ở request thứ 241 tổng cộng**, không phải 481. Đây là con số đo được, không phải "rate-limit hoạt động".
3. WebSocket: với 2 replica, client kết nối replica 1 vẫn nhận progress của import chạy ở replica 2 — assert bằng nhận đúng message `ANALYZED`.

**Rủi ro khi sửa:** nhánh A gần như không có rủi ro code. Nhánh B thêm Redis vào **đường auth nóng** — Redis chết = toàn bộ request bị chặn hoặc fail-open (cả hai đều tệ). Nếu chọn B, phải định nghĩa trước hành vi khi shared store không khả dụng và test đúng nhánh đó.

### 6.4. Đ7-4 — Hình dạng query `getFullGraph` nhân bản node theo số cạnh

**Xác minh còn tồn tại:** `src/main/java/com/vibegraph/graph/repository/impl/neo4j/Neo4jGraphRepository.java:387–397`:

```java
public GraphDataResponse getFullGraph(String projectId) {
    ...
    var result = session.run(
            "MATCH (n:Symbol {projectId: $projectId}) " +
            "OPTIONAL MATCH (n)-[r]->(m:Symbol {projectId: $projectId}) " +
            "RETURN n, r, m",
            Map.of("projectId", projectId));
```

Vẫn **một** query với `OPTIONAL MATCH` trả `n, r, m` → mỗi node xuất hiện **một lần cho mỗi cạnh đi ra** của nó. Việc khử trùng lặp diễn ra **phía client** trong `LinkedHashMap nodeMap` (`:398`) — nghĩa là số **dòng** truyền từ Neo4j về driver là O(số cạnh), không phải O(số node). Cùng file cũng còn 2 chỗ tương tự: `:338` và `:101–105`.

**Điểm đã tốt hơn báo cáo v2:** cap **đã** hiệu lực (B-M10 đã đóng) — `GraphPayloadProperties.java:27` `nodeLimit = 5000`, `GraphController.java:60–62` `clamp(...)`, `.env.example:126` `VIBEGRAPH_GRAPH_NODE_LIMIT=2500`. Nhưng cap áp ở **tầng controller**, tức **sau khi** Neo4j đã trả toàn bộ dòng và driver đã dựng xong `nodeMap`. Phần "nhân bản node theo cạnh trên đường truyền" **chưa** được xử lý.

**Ước lượng tác động:** với graph có tỷ lệ cạnh/node = k, số dòng result set ≈ k × số node. Với repo trung bình k ≈ 2–3 (VibeGraph-com: 17.079 symbol / 39.748 relationship ⇒ k ≈ 2,3 theo `CLAUDE.md`), tức ~2,3× số dòng và mỗi dòng mang **toàn bộ property của node `n`** lặp lại. Đây là chi phí băng thông driver + RAM tạm, không phải chi phí payload HTTP (payload đã được cap).

**Tiêu chí nghiệm thu — số đo, KHÔNG phải "hiệu năng tốt hơn":**

| Bước | Lệnh / cách đo | Tiêu chí |
|---|---|---|
| Đ7-4a | Đo số dòng result set hiện tại trên một project thật. Chạy trong `cypher-shell`: `MATCH (n:Symbol {projectId:$p}) OPTIONAL MATCH (n)-[r]->(m:Symbol {projectId:$p}) RETURN count(*)` và so với `MATCH (n:Symbol {projectId:$p}) RETURN count(n)` | Báo cáo ghi 2 số và tỷ lệ `rows / nodes = <k>`. **Đây là bằng chứng định lượng cho mục này** |
| Đ7-4b | Sau khi tách thành 2 query (nodes riêng, edges riêng): đo lại | `rows_nodes + rows_edges` ≤ `nodes + edges`, và tỷ lệ mới = 1,0 (mỗi node đúng 1 dòng) |
| Đ7-4c | **Tính đúng đắn:** so payload API trước/sau | `curl` cùng endpoint `/api/graph/{id}` trước/sau, so **số node** và **số edge** trong JSON: bằng nhau tuyệt đối. So cả `meta.nodeLimit`/`meta.truncated` để chắc cap không đổi hành vi |
| Đ7-4d | Node cô lập không bị mất | Project có ≥1 node không có cạnh đi ra: node đó **vẫn** xuất hiện trong response sau khi tách query. Đây là chính lý do `OPTIONAL MATCH` được dùng — tách 2 query mà dùng `MATCH` thường sẽ **âm thầm mất node cô lập** |
| Đ7-4e | Đo thời gian endpoint | `curl -w "%{time_total}"` 5 lần, median, trước/sau, trên **cùng** project. Ghi cả 2 số. **Lưu ý:** `CachingGraphRepository` cache 5 phút → phải invalidate hoặc dùng project khác nhau giữa các phép đo, nếu không đo được cache chứ không đo được query |

**Rủi ro khi sửa:**
- **Mất node cô lập** (rủi ro số 1) — xem Đ7-4d. `OPTIONAL MATCH` tồn tại chính để giữ node không cạnh.
- Hai query = **hai điểm nhất quán**: giữa query nodes và query edges có thể xảy ra ghi mới → edge trỏ tới node không có trong tập nodes. Phải xử lý: hoặc bọc cả hai trong một `session.executeRead`, hoặc lọc edge có đầu mút thiếu (và **log** khi lọc, không im lặng).
- `addNodeToMap` (`:404`, `:415`) cũng tích lũy `nodeStats` — tách query phải giữ nguyên `nodeStats`/`edgeStats` trong response, nếu không dashboard đọc thống kê sẽ lệch.
- `stableNodeId(n)`/`stableEdgeId(...)` (`:419–421`) đang được tính từ chính `Node` object trong cùng dòng. Tách query đổi nguồn dữ liệu của chúng — phải assert ID **không đổi** trước/sau (ID là thứ frontend dùng để chọn node; đổi ID = vỡ mọi deep link).

---

## 7. Việc cần CHỦ REPO quyết, không phải agent

Qwen **không** được tự quyết các mục dưới đây. Nếu chưa có câu trả lời, ghi "chờ quyết định" và chuyển sang mục khác.

| # | Việc | Trạng thái đã kiểm | Cần quyết gì |
|---|---|---|---|
| Q1 | **Xoay secret phía provider (Đợt 0)** | Phần agent đã xong: `git cat-file -t 388632b` → "Not a valid object name"; backup working tree đã xóa | Rotate Supabase password, `JWT_SECRET`, OAuth client secret Google/GitHub, 8 Gemini key — cần quyền tài khoản. **Đây là mục duy nhất đang chảy máu thật.** Mọi việc khác trong tài liệu này đứng sau nó |
| Q2 | **D-M4 phần 2 — registry cho job CD** | Phần 1 xong (4 action pin SHA). `.github/workflows/backend.yml:65` đã có comment ghi rõ đang chờ | GHCR / Docker Hub / chưa cần. Không có câu trả lời thì **không** viết job CD — job đã từng được thêm rồi gỡ một lần theo quyết định A4 |
| Q3 | **D-M5 — ownership của `task-final/`** | 16 file bị track; 3/8 file **giống hệt**, 5/8 khác **chỉ ở BOM UTF-8** (§3). `task/final/` chỉ có ở `task/` | Giữ `task/` hay `task-final/`? Và `task/final/` (log/pid/json runtime) xử lý sao? Operator đã **từ chối xóa một lần** (quyết định A3) → không được tự xóa lại |
| Q4 | **S-M5 — có siết trần multipart hay không** | `application.yaml:42` vẫn `2048MB` / `:43` `2050MB`. Đã có lớp phòng thủ mới: `ArchiveUploadLimitFilter.java` (`:56` `OncePerRequestFilter implements Ordered`, `:127` `Math.min(snapshot.remainingBytes(), hostCeilingBytes)`) | Operator đã phán **GIỮ 2048MB** (quyết định A1), lý do: lỗ hổng thật là thiếu pre-check, đã vá bằng filter. **Xác nhận lại quyết định này còn hiệu lực** hay muốn hạ trần host xuống 512MB như đề xuất gốc |
| Q5 | **Đ7-3 — nhánh A hay B** | `DEPLOYMENT.md` 0 hit "replica"; 4 thành phần chặn còn nguyên (§6.3) | Chấp nhận "single-replica only" và ghi vào `DEPLOYMENT.md` (nhánh A, ~0 rủi ro), hay đầu tư shared store để scale ngang (nhánh B, thêm Redis vào đường auth nóng)? **S-L3 và D-L1 bị chặn bởi quyết định này** |
| Q6 | **Đ7-2 — môi trường diễn tập restore** | Không có chương backup nào trong `DEVOPS-GUIDE.md`/`DEPLOYMENT.md` | Diễn tập restore cần một stack riêng (project name + volume riêng). Cấp phép chạy `docker compose -p vibegraph-restore-test up`, hay chủ repo tự chạy? **Agent không được restore vào volume đang dùng** |
| Q7 | **S-L1 — có thêm DOMPurify không** | 3 điểm `v-html` còn nguyên (`DiagramPanel.vue:500,583`, `CodeViewerModal.vue:242`) | Thêm dependency (~20KB bundle, rủi ro strip element SVG hợp lệ) để phòng thủ theo lớp, hay chấp nhận rủi ro tồn dư thấp vì nội dung do server escape? |
| Q8 | **F-M6 — phạm vi thật** | **30 file** vượt 400 dòng, không phải 10 như báo cáo (§5.1) | Refactor 4 file trên 1.400 dòng rồi áp quy tắc cho file mới, hay giữ tiêu chí "≤400 dòng" cho cả 30 file (phạm vi gấp 3)? |
| Q9 | **D-L4 — có track 2 script quick-start không** | `.gitignore:5–6` ignore chúng; cả 2 file có thật ở root (9.234 B / 13.251 B) | Bỏ ignore và track (teammate clone được), hay giữ ignore? **Điều kiện:** phải grep secret trong 2 file trước; nếu có secret thì DỪNG |

---

## 8. Thứ tự đề nghị + lý do

Xếp theo **rủi ro thật × chi phí**, **không** theo mức độ trong báo cáo. Lý do lệch khỏi thứ tự báo cáo được ghi ở cột cuối.

| Ưu tiên | Việc | Chi phí | Vì sao ở đây |
|---|---|---|---|
| **0** | **Q1 — xoay secret (chủ repo)** | — | Mục duy nhất đang chảy máu. Mọi việc dưới đây vô nghĩa nếu `JWT_SECRET` cũ còn hiệu lực |
| **1** | **Đ7-2 — backup/restore + diễn tập một lần** | Trung bình | **Nâng lên trên toàn bộ Đợt 5.** Đây là mục có hậu quả nặng nhất còn lại (mất `postgres-data` = mất control plane vĩnh viễn) và là mục **duy nhất chưa ai đưa vào bất kỳ đợt nào**. Báo cáo Qwen không có mã cho nó — nếu đi theo REMEDIATION-PLAN nguyên bản, nó sẽ **không bao giờ** được làm. Cần Q6 |
| **2** | **Đ7-3 nhánh A — ghi "single-replica only" vào `DEPLOYMENT.md`** | Rất thấp | Rẻ nhất trong toàn tài liệu (một đoạn văn + một lệnh nghiệm thu âm) nhưng **mở khóa** S-L3 và D-L1 khỏi trạng thái "sửa lẻ sai thứ tự". Làm ngay sau khi có Q5 |
| **3** | **Đợt 5 lô A — dọn code chết (5 mục, một lần)** | Thấp | 0 rủi ro hành vi, đã xác minh 0 tham chiếu, một lệnh nghiệm thu (`mvnw -DskipITs test` + `verify`). Bỏ được **8 `@Disabled`** khỏi bộ test (skipped 9 → ≤1), làm số liệu test đáng tin hơn cho mọi đợt sau. Làm sớm vì nó **giảm nhiễu** cho các phép đo về sau |
| **4** | **D-M2r + D-L3 + D-L4** | Rất thấp | Ba mục vệ sinh rẻ: pin `postgres:16.<patch>`, xóa đích danh 28 file/106 MB, bỏ ignore 2 script. D-L4 cần Q9 và **phải grep secret trước** |
| **5** | **Đ7-4 — đo hình dạng `getFullGraph`, rồi mới quyết tách query** | Trung bình | **Bước Đ7-4a (đo tỷ lệ rows/nodes) làm TRƯỚC, độc lập với việc sửa.** Nếu k đo được ≈ 1,2 thì mục này không đáng sửa và bị đóng bằng số liệu; nếu k ≈ 2,3 như suy ra từ `CLAUDE.md` thì tách query có cơ sở. Đo trước, quyết sau — đừng sửa rồi mới đo |
| **6** | **Đợt 5 lô B — 9 mục sửa hành vi nhỏ** | Trung bình | Mỗi mục cần test riêng. Thứ tự trong lô: B-L2, F-L4, F-L2 (rẻ nhất) → B-L5, B-L7, F-L1, F-L3 → **B-L1 và B-L8 làm cuối** (B-L1 có thể làm mất CALLS edge âm thầm; B-L8 đổi đánh đổi telemetry — cả hai cần nghiệm thu bằng số, không bằng test pass/fail) |
| **7** | **Đ7-1 — parse song song** | Cao | Xếp **sau** Đợt 5 dù nó là "bottleneck lớn nhất", vì: (a) claim "~65 %" **chưa được xác minh** bởi bất kỳ phép đo nào — bước Đ7-1b/c phải chạy trước để biết mục này có đáng làm; (b) rủi ro mất CALLS edge âm thầm là loại lỗi tệ nhất trong repo này (không test nào fail); (c) container backend **không có `mem_limit`** nên song song hóa có thể đổi một bottleneck thành một OOM-kill. Chỉ làm khi Đ7-1c cho ra tỷ lệ đủ lớn |
| **8** | **Đợt 6 bước 6-F0/6-F1 — cài coverage frontend + lấy baseline** | Thấp | Tách khỏi phần refactor. Bản thân việc **có** coverage frontend là giá trị độc lập (hiện `@vitest/coverage-*` chưa được cài, 3 file lớn nhất có **0 test**). Làm bước này sớm cũng được — nó không chặn ai |
| **9** | **B-M2 — nâng BRANCH lên ≥80 % rồi tách god class** | Cao | Coverage LINE 86,6 % đã đủ để bắt đầu, nhưng BRANCH 69,7 % (236 nhánh trống) là đúng chỗ heuristic dễ lệch. Nâng branch trước, tách sau. Nghiệm thu phải so **số accuracy** của `UseCaseAccuracyEvalTest`, không chỉ pass/fail |
| **10** | **F-M6 — tách 4 file > 1.400 dòng** | Rất cao | Đứng cuối vì phụ thuộc Q8 (phạm vi) + bước 8 (coverage) + bước 6-F2 (viết test cho `LandingView`/`SecurityView` đang có 0 test). Tách `api.ts` **sau cùng** trong nhóm này — 987 dòng là đường ra duy nhất tới backend cho ~40 endpoint (nó **có** 18 spec import, xem đính chính §5.2) |
| **—** | **Đợt 5 lô C (12 mục)** | — | Không xếp ưu tiên: B-L6/D-L2/D-L6 là tài liệu (làm xen kẽ); S-L2/S-L4/S-L5 là phòng thủ theo lớp (chờ tính năng mới hoặc quyết định); S-L3/D-L1 bị chặn bởi Q5; S-L1 chờ Q7 |
| **—** | **D-M5** | — | Bị chặn bởi Q3. Đã kiểm xong (§3): khối lượng thật chỉ là một quyết định + một lần chuẩn hóa BOM |

### Ba lệch có ý thức so với thứ tự trong AUDIT-REPORT §7 / REMEDIATION-PLAN §6

1. **Đ7-2 (backup) vượt lên trước toàn bộ Đợt 5 dù nó không có mã Qwen nào.** Báo cáo Qwen không chứa mục này; đi đúng REMEDIATION-PLAN nghĩa là không bao giờ làm nó. Hậu quả (mất control plane vĩnh viễn) nặng hơn mọi mục Thấp cộng lại.
2. **Đ7-1 (parse tuần tự) bị đẩy xuống dưới Đợt 5 dù codex v2 xếp nó High.** Lý do: con số "~65 % thời gian import" `[chưa xác minh]` — không có phép đo nào trong repo. Ưu tiên theo tác động **chưa được đo** là ưu tiên theo phỏng đoán. Bước đo (Đ7-1b/c) rẻ và phải chạy trước.
3. **B-L1 và B-L8 bị đẩy xuống cuối lô B dù chúng là mức Thấp.** Cả hai có thể gây hỏng **âm thầm** (mất CALLS edge / đổi hành vi drop telemetry) — loại lỗi mà bộ test hiện tại không bắt được. Mức độ trong báo cáo phản ánh *tác động khi bình thường*, thứ tự ở đây phản ánh *rủi ro khi sửa*.

---

## 9. Nhóm `[chưa xác minh]`

Không có mục nào chưa xác minh được **sự tồn tại** (41/41 đã kiểm bằng cách mở file). Nhóm dưới đây là các **con số** chưa có bằng chứng đo lường trong repo — Qwen phải đo trước khi dùng chúng làm cơ sở quyết định:

| Con số | Nguồn | Vì sao chưa xác minh | Đo thế nào |
|---|---|---|---|
| "Parse chiếm **~65 %** thời gian import" | `docs/audit-report-v2-2026-08-12.md:113` | Không có log, benchmark, hay profile nào trong repo chứng minh. Không chạy được stack + import trong phiên read-only này | Bước Đ7-1b + Đ7-1c (§6.1) |
| Tỷ lệ `rows / nodes` của `getFullGraph` | Suy ra ≈ 2,3 từ `CLAUDE.md` (17.079 symbol / 39.748 relationship) — nhưng đó là số của **GitNexus index**, không phải của một project trong Neo4j | Cần Neo4j đang chạy + một project thật | Bước Đ7-4a (§6.4) |
| Coverage frontend của 10 file F-M6 | Không có công cụ coverage nào được cài (`@vitest/coverage-*` không có trong `package.json`) | Không thể đo mà chưa cài | Bước 6-F0 + 6-F1 (§5.2) |
| "8 lỗ hổng npm trước khi vá" (H16) | `AUDIT-REPORT.md` H16 | Lockfile cũ không còn để chạy lại `npm audit`. Trạng thái **hiện tại** (0 vulnerability) thì đo được | `cd vibegraph-web && npm audit` |
| Lệch 1 test (1.036 kỳ vọng vs 1.037 thực đo) | `FINAL-REPORT-DOT2-3.md` §1 | Chưa truy nguyên. Không ảnh hưởng kế hoạch này, nhưng mọi phép đếm test sau phải `rm -rf target/surefire-reports` trước và dán nguyên summary Maven | `rm -rf target/surefire-reports && ./mvnw -DskipITs test` |

---

## 10. Luật cứng cho Qwen khi thực thi tài liệu này

1. **Không sửa `update/docs/Qwen/`.** Mọi mâu thuẫn tài liệu ↔ code ghi vào báo cáo mới, không sửa nguồn.
2. **Không `mvnw clean`** — có tiến trình java giữ file trong `target/`. Dùng `./mvnw -DskipITs test` / `./mvnw verify`.
3. **Không commit, không `git add`.** Để thay đổi trong working tree cho chủ repo.
4. **Không chạm `docs/ROTATION-CHECKLIST.md`** — agent khác đang sở hữu file đó.
5. **Đếm dòng bằng `wc -l`** (Git Bash). **Không** `Get-Content | Measure-Object -Line` — đã được chứng minh đếm sai.
6. **Mọi tuyên bố "đã xong" phải kèm lệnh chứng minh VÀ giới hạn của lệnh đó.** Ví dụ đúng: "grep không thấy `addCorsMappings`; lệnh này không kiểm được cấu hình CORS đến từ `application.yaml`."
7. **Số dẫn xuất từ báo cáo cũ phải đánh `[chưa xác minh lại]`.** Chỉ số tự đo trong phiên được ghi trần.
8. **Dừng-và-báo** khi tài liệu trái dữ liệu thật, thay vì tự sửa theo tài liệu.
9. **Với mục nào cần chủ repo quyết (§7): không tự quyết.** Ghi "chờ quyết định" và chuyển mục khác.

---

*Soạn 13/08/2026. Đã xác minh 41/41 mục bằng cách mở file thật, không dựa vào bảng trạng thái trong tài liệu. Phát hiện chính: 6 mục Trung bình mà REMEDIATION-PLAN vẫn liệt "chưa xếp đợt" đã được sửa xong (§2) — Đợt 4 gần như trống; đổi lại, 26/26 mục Thấp đều còn nguyên và 4 khoảng trống ở §6 chưa nằm trong bất kỳ đợt nào, trong đó backup/restore là mục có hậu quả nặng nhất còn lại.*
