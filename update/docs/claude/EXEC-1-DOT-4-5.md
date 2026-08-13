# LẦN 1 — THI HÀNH ĐỢT 4 + ĐỢT 5

- **Ngày lập:** 13/08/2026
- **Nguồn:** `update/docs/claude/PLAN-REMAINING-2026-08-13.md` (§3, §4)
- **Người thực thi:** Qwen · **Người nghiệm thu:** reviewer
- **Tiền đề:** Đợt 0 (một phần) / 1 / 2 / 3 đã xong và đã commit — `e9fe0bb`, `7130507`, `96b9409`, `b7f294c`

---

## 0. Trạng thái đã kiểm hôm nay — 5 mục ĐÃ XONG, gỡ khỏi phạm vi

Trước khi giao việc, tôi chạy lại từng mục của Lô A. Một agent đã làm xong 5 mục trong lúc chờ. **Đừng làm lại.**

| Mã | Trạng thái | Lệnh + kết quả thật |
|---|---|---|
| B-L9 (6 DTO chết) | ✅ XONG | `for c in PaginationRequest AnalyzeRequest ClassContextRequest LayerPatternRequest ParseFileRequest ParseResultResponse; do find src/main -name "$c.java"; done` → **0/6 còn** |
| B-L10 (entity `UserNotification`) | ✅ XONG | `find src/main -name UserNotification.java` → rỗng. Bảng `user_notifications` + migration V10 **còn nguyên** (4 hit trong V10) — đúng yêu cầu |
| B-L11 (`TarballImportServiceTest`) | ✅ XONG | `find src/test -name TarballImportServiceTest.java` → rỗng. Suite thật `service/impl/TarballImportServiceImplTest.java` còn |
| D-L3 (106 MB log rác root) | ✅ XONG | `ls -1 ./*.log ./*.diff ./*.stackdump` → **0 file**. `.vibegraph/` **không** bị xoá (đúng — nó chứa script vận hành thật) |
| D-L4 (`.gitignore` 2 script) | ✅ XONG | `grep -c quick-start .gitignore` → **0**. 2 file ở root sẵn sàng track |
| D-M2r (pin `postgres`) | ✅ XONG | `grep -h 'image: postgres' docker-compose.yml database/docker-compose.postgres.yml \| sort -u` → **`postgres:16.11-alpine`** ở cả 2 file |

**Mốc test hiện tại — dùng làm baseline cho mọi so sánh trong lần này:**

```
rm -rf target/surefire-reports && ./mvnw -B test
→ Tests run: 1031, Failures: 0, Errors: 0, Skipped: 1   ·   BUILD SUCCESS
```

Frontend: `vue-tsc` sạch · **533/533** test · build OK · `npm audit` **0 vulnerabilities**.

---

## 1. Đợt 4 — còn đúng 1 mục, và nó chờ quyết định của chủ repo

### D-M5 — `task/` và `task-final/`

**Đo lại hôm nay** (so theo **đường dẫn đầy đủ**, không dùng `basename` — sai này tôi đã mắc một lần và bỏ sót cả thư mục con):

```
GIỐNG HỆT    : PROJECT_DOCUMENTATION_MASTER.md
GIỐNG HỆT    : VibeGraph_WS3_Sprint-Trello-BBCH-ERD.md
GIỐNG HỆT    : export_to_csv.py
CHỈ KHÁC BOM : csv_exports/ed_calculation.csv
CHỈ KHÁC BOM : csv_exports/pps_calculation.csv
CHỈ KHÁC BOM : csv_exports/product_backlog.csv
CHỈ KHÁC BOM : csv_exports/release_backlog.csv
CHỈ KHÁC BOM : csv_exports/sprint_backlog.csv
```

Lệnh tái lập:

```bash
git ls-files task/ | while read -r a; do
  b="task-final/${a#task/}"
  if diff -q "$a" "$b" >/dev/null 2>&1; then echo "SAME  ${a#task/}"
  elif diff -q <(sed '1s/^\xEF\xBB\xBF//' "$a") <(sed '1s/^\xEF\xBB\xBF//' "$b") >/dev/null 2>&1; then
    echo "BOM   ${a#task/}"
  else echo "DIFF  ${a#task/}"; fi
done
```

**Đây không còn là bài toán "merge từng file"** như `AUDIT-REPORT.md` mô tả. Khác biệt duy nhất là **BOM UTF-8** (`EF BB BF`) ở đầu 5 file CSV. Nội dung dòng y nguyên.

**⛔ KHÔNG TỰ QUYẾT.** Chủ repo phải chọn 1 trong 3, vì cả ba đều hợp lý và hệ quả khác nhau:

| Nhánh | Việc | Hệ quả |
|---|---|---|
| **A** | Chuẩn hoá BOM về một chuẩn, giữ cả 2 thư mục | An toàn nhất. Nhưng vẫn còn 2 thư mục trùng nội dung |
| **B** | Giữ 1 thư mục, `git rm` thư mục kia | Sạch. Nhưng phải biết bên nào là bản chính — tài liệu không nói |
| **C** | Không làm gì, ghi chú lại | Rẻ nhất. Nợ vẫn còn |

**Nếu chưa có câu trả lời:** bỏ qua D-M5, đừng đoán. 11 file `task/`+`task-final/` hiện đang **modified chưa commit** — **để nguyên**, đừng commit, đừng revert.

⇒ **Đợt 4 thực tế: 0 mục có thể thi hành ngay.** Chuyển sang Đợt 5.

---

## 2. Đợt 5 · Lô A — còn 2 mục

Cả 2 đều là xoá code chết. Gộp một lần sửa, một lần nghiệm thu.

### B-L3 — overload `JwtService.issue(User)` không còn dùng ở production

**Xác minh còn tồn tại:**
```bash
grep -c 'public String issue(User user)' src/main/java/com/vibegraph/auth/service/JwtService.java   # → 1
```

`file:dòng`: `src/main/java/com/vibegraph/auth/service/JwtService.java:57`.
Production dùng bản **2 tham số** ở `:62`. Overload 1 tham số chỉ được gọi từ `JwtServiceTest.java:39,76`.

**Việc:** xoá overload, sửa 2 dòng test sang bản 2 tham số.

**Tiêu chí nghiệm thu:**
1. `grep -c 'public String issue(User user)' src/main/java/com/vibegraph/auth/service/JwtService.java` → **0**
2. `grep -rn 'issue(' src/main/java --include=*.java | grep -v 'issue(.*,' ` → rỗng (không còn caller 1 tham số trong production)
3. `./mvnw -Dtest=JwtServiceTest test` → BUILD SUCCESS

**Rủi ro:** thấp. Nhưng **phải grep cả `src/test`** trước khi xoá — nếu có test khác gọi bản 1 tham số mà bạn không sửa, build fail (đó là fail tốt, không phải fail xấu).

### B-L4 — `AnnotationVisitor` đã `@Deprecated`, production không dùng

**Xác minh còn tồn tại:**
```bash
find src/main -name AnnotationVisitor.java     # → còn file
```

`file:dòng`: `src/main/java/com/vibegraph/parser/visitor/AnnotationVisitor.java:18` `@Deprecated(forRemoval = false)`.
Tham chiếu duy nhất: `src/test/java/com/vibegraph/parser/visitor/AnnotationVisitorTest.java`.
Production dùng `SpringAnnotationVisitor` (`ParserServiceImpl.java:35,120`).

**Việc:** xoá cả `AnnotationVisitor.java` và `AnnotationVisitorTest.java`.

**Tiêu chí nghiệm thu:**
1. `find src/main -name AnnotationVisitor.java` → rỗng
2. `find src/test -name AnnotationVisitorTest.java` → rỗng
3. `grep -rn 'AnnotationVisitor' src/main src/test --include=*.java | grep -v SpringAnnotationVisitor` → **rỗng**
4. `grep -c 'SpringAnnotationVisitor' src/main/java/com/vibegraph/parser/service/impl/ParserServiceImpl.java` → **≥ 1** (chứng minh bản đang dùng còn nguyên)

**Rủi ro:** grep dễ nhầm vì `SpringAnnotationVisitor` **chứa** chuỗi `AnnotationVisitor`. Mọi lệnh grep cho mục này **phải** loại `SpringAnnotationVisitor`, nếu không sẽ kết luận sai theo cả hai chiều.

### Nghiệm thu chung Lô A

```bash
rm -rf target/surefire-reports
./mvnw -B test
```

**Con số kỳ vọng: `Tests run: 1029, Failures: 0, Errors: 0, Skipped: 1`**

Suy ra: 1031 hiện tại − 2 test (`AnnotationVisitorTest`) = 1029. Số test của `JwtServiceTest` **không đổi** (sửa 2 dòng, không xoá test).

**Nếu ra số khác 1029 → DỪNG và báo.** Đừng tự giải thích cho khớp. Nếu `AnnotationVisitorTest` có nhiều/ít hơn 2 `@Test` thì con số kỳ vọng đổi theo — hãy **đếm trước** bằng `grep -c '@Test' src/test/java/com/vibegraph/parser/visitor/AnnotationVisitorTest.java` và ghi vào báo cáo, rồi mới xoá.

Thêm: `./mvnw verify` — gate JaCoCo `LINE ≥ 0.70` (`pom.xml`, rule `<counter>LINE</counter>` `<minimum>0.70</minimum>`) phải còn xanh. Xoá code không có test làm mẫu số giảm → tỷ lệ thường **tăng**, nhưng **phải đo**, không giả định.

---

## 3. Đợt 5 · Lô B — 9 mục sửa hành vi, mỗi mục cần test riêng

Mỗi mục dưới đây đổi hành vi thật. Không gộp nghiệm thu. Thứ tự trong bảng là thứ tự đề nghị làm.

| # | Mã | `file:dòng` | Tiêu chí nghiệm thu (đo được) | Rủi ro — đọc trước khi sửa |
|---|---|---|---|---|
| 1 | **F-L4** | `components/projects/ImportProjectPanel.vue:92` `iconPath()`, 3 SVG path hardcode `:95,:97,:100`, dùng ở `:150` | `grep -c "'M[0-9]" ImportProjectPanel.vue` → **0**. `expect(wrapper.findAll('path')).toHaveLength(3)` với `d` không rỗng | Thấp nhất trong lô — làm đầu để lấy đà. Kiểm trước `ls vibegraph-web/src/components/ui/AppIcon.vue` (**đã xác minh tồn tại, 76 dòng**); nếu thiếu thì mục này biến thành "tạo hạ tầng icon" — lúc đó DỪNG và báo |
| 2 | **F-L2** | `LandingView.vue:490–493` 4 `window.addEventListener(..., { once: true })`; `onBeforeUnmount:500` chỉ remove `onScroll` | `grep -c "window.addEventListener" LandingView.vue` = `grep -c "window.removeEventListener"` (hiện **5 vs 1**). Test: mount → unmount → `window.dispatchEvent(new Event('keydown'))` → spy `stopAutoTour` gọi **0** lần | ⚠️ **Đừng nâng mức độ mục này.** `{ once: true }` đã tự huỷ listener sau lần fire đầu — rủi ro thật chỉ là listener sống tới event đầu tiên sau unmount, **thấp hơn** báo cáo mô tả |
| 3 | **F-L1** | `LandingView.vue:353,355` `setTimeout` không lưu handle; 6 `setTimeout` toàn file (`:263,353,355,435,467,496`) | Test: mount, gọi `typeCommand`, `unmount()`, `vi.advanceTimersByTime(2000)` → **0** cảnh báo Vue, `terminalInput` **không đổi** sau unmount | `:263` và `:435` là timer trong animation/await — **clear quá tay làm mất hiệu ứng**. Chỉ clear timer **tự lặp** (`typeCmd`) và timer sống dài. `tourTimeout` đã clear ở `:473` — đừng clear hai lần |
| 4 | **F-L3** | `components/graph/SearchBar.vue:21` `computed` → `:26` `.filter` toàn bộ `props.nodes` → `:31` `.slice(0, LIMIT)`; không debounce (222 dòng) | `vi.useFakeTimers()`, gõ 10 ký tự trong 100ms → filter chạy **≤ 2** lần (hiện 10). Kết quả cuối **giống trước** khi debounce | `SearchBar.spec.ts` có **6 case** sẽ fail vì kết quả không xuất hiện ngay → phải cập nhật kèm `await vi.advanceTimersByTime`. Debounce > 200ms làm search cảm giác chậm |
| 5 | **B-L2** | `CachingGraphRepository.java:76–92` `pruneIfOverflowing()` — `while` lồng `for` quét toàn bộ `snapshots` tìm `loadedAt` nhỏ nhất | `grep -A20 "private void pruneIfOverflowing" ... \| grep -c "for ("` → **0**. Test mới: nạp `MAX_ENTRIES + 3` project → assert `snapshots.size() == MAX_ENTRIES` và 3 key cũ nhất đã evict | Dùng Caffeine `expireAfterWrite` sẽ **chồng** với TTL 5 phút thủ công ở `:60–62`. Nếu chọn Caffeine thì **bỏ hẳn** kiểm TTL thủ công — không để cả hai lớp hết hạn cùng lúc |
| 6 | **B-L7** | `ProjectServiceImpl.java:42,53,60,68` 4 field `@Autowired(required = false)` + `:45` `@Autowired` | `grep -c "@Autowired" ProjectServiceImpl.java` → **0**; constructor nhận `ObjectProvider<T>` cho 4 dependency optional. `./mvnw -Dtest=ProjectServiceImplTest test` xanh | Quên `.getIfAvailable()` → `NoSuchBeanDefinitionException` lúc bootstrap ở profile không có bean đó. **Nghiệm thu bắt buộc: khởi động cả profile `dev` VÀ `docker`**, không chỉ chạy unit test |
| 7 | **B-L5** | 2 nguồn CORS: `common/config/CorsConfig.java:24` `implements WebMvcConfigurer` + `:38–39` `addCorsMappings("/api/**")`; và `SecurityConfig.java:141` `.cors(withDefaults())` + `:240` `corsConfigurationSource()` | `grep -rn "addCorsMappings" src/main/java` → **rỗng**. Hành vi: preflight từ `http://localhost:5173` **vẫn** nhận `Access-Control-Allow-Origin`; từ `https://evil.example` **không** nhận (lặp lại test T8) | ⚠️ `CorsConfig` (45 dòng) chứa **guard startup chống `"*"`** ở constructor `:30–34`, và `SecurityConfig:237` đang dẫn chiếu tới nó. **Xoá cả file = mất guard.** Chỉ bỏ `addCorsMappings`, **giữ** phần validate |
| 8 | **B-L1** | `ParserServiceImpl.java:447` `createParser(Path)` và `:477` `createProjectParser(Path, List<Path>)` — trùng lặp phần dựng `CombinedTypeSolver`+`ReflectionTypeSolver`+`JavaSymbolSolver`+`ParserConfiguration(JAVA_21)` | `grep -c "new JavaParser(config)" ParserServiceImpl.java` → **1** (hiện 2). File giảm từ **571** dòng xuống ≤ 545. `./mvnw -Dtest='Parser*Test,MethodVisitorTest' test` xanh | ⚠️ **Nặng nhất lô.** `createParser` được gọi ở `:88` trong nhánh fallback của `parseFileInternal` — gom sai làm mất `JavaParserTypeSolver(sourceRoot)` cho đường parse-một-file → **CALLS edge biến mất âm thầm, không test nào fail**. **Bắt buộc** so số edge trước/sau trên cùng 1 repo mẫu (xem §5) |
| 9 | **B-L8** | `RequestEventService.java:350–369` — `offer()` khi queue đầy thì `freshQueue.poll()` (`:359`) bỏ event **cũ nhất bất kể loại**, kể cả security event; chỉ counter `:56` ghi nhận | Test: nạp queue đầy bằng event **non-security**, offer 1 security event → assert security event **vẫn trong queue** và `security_events.dropped.total` **không tăng**. Đo qua `MeterRegistry` trong test, **không** bằng đọc code | ⚠️ Ưu tiên security event làm non-security bị drop nhiều hơn. Phải đo **cả 2** counter (`request_events.dropped.total` `:47` và `security_events.dropped.total` `:56`) trước/sau, ghi tỷ lệ đánh đổi vào comment. Đây là mục gây hỏng **âm thầm** — làm cuối lô |

---

## 4. Đợt 5 · Lô C — 12 mục: KHÔNG sửa code, chỉ ghi nhận

Đề xuất mặc định cho cả 12: **ghi nhận, không sửa**. Chi phí sửa cao hơn giá trị, hoặc bản chất là đánh đổi thiết kế. Nếu bạn muốn sửa mục nào thì **báo trước**, đừng tự làm.

| Mã | `file:dòng` | Vì sao không sửa |
|---|---|---|
| B-L6 | `database/ERD.md:64–67` liệt `refresh_tokens`/`audit_log` là "có thể thêm sau" dù đã tồn tại (V18, V10) | **Nên sửa** — rẻ, 0 rủi ro code. Tiêu chí: `for t in refresh_tokens audit_log; do grep -rlc "CREATE TABLE.*$t" src/main/resources/db/migration; done` — mục nào ≥1 phải chuyển sang phần "đã có". Rủi ro duy nhất: viết ERD từ trí nhớ → tài liệu lệch mới. **Đối chiếu từng cột với migration thật** |
| S-L1 | `DiagramPanel.vue:500,583` + `CodeViewerModal.vue:242` `v-html` | DOMPurify strip mất element SVG hợp lệ (marker, foreignObject) làm diagram vỡ → phải so ảnh render trước/sau trên ≥3 diagram thật. Thêm ~20KB bundle. **Nhánh rẻ: thêm comment tại 3 dòng nêu rõ nguồn dữ liệu là server-escape** |
| S-L2 | `CookieCsrfFilter.java:25,67` custom header thay token | Chuyển sang CSRF token **phá vỡ mọi client hiện có** (frontend, MCP, API key). Breaking change toàn hệ — không làm trong đợt dọn dẹp |
| S-L3 | `RateLimitFilter.java:17,234` Caffeine in-process | **Trùng Đ7-3** (Đợt 7). Thêm Redis = thêm điểm chết mới cho luồng auth. Chỉ làm cùng quyết định scale ngang |
| S-L4 | `JwtAuthFilter.java:41` `static ACTIVE_USERS`, `:61` chỉ `removeIf` khi có người đọc | Đổi sang Caffeine đổi luôn ngữ nghĩa "active user count" mà admin dashboard đang đọc. **Phải kiểm endpoint nào phơi số này trước** |
| S-L5 | `GitHubUrlParser.java:15` `SEGMENT = "[A-Za-z0-9_.-]+"` cho phép `.`/`..`; + `Redirect.NORMAL` ở `GitHubTarballClient.java:33`, `GitHubPreFlightService.java:33` | Siết regex quá tay chặn repo tên hợp lệ có dấu chấm (`foo.js`, `bar.github.io`). **Nếu sửa: chỉ chặn segment bằng đúng `.` hoặc `..`**, không chặn dấu chấm nói chung. Test bằng `assertThrows`, không bằng đọc regex |
| D-L1 | `docker-compose.yml:4,27,58,162` 4 `container_name` cố định | Bỏ `container_name` **hỏng mọi lệnh trong tài liệu vận hành** (`DEVOPS-GUIDE.md:51,270,289`, `scripts/dev-up.ps1:51,57`). **Trùng Đ7-3** |
| D-L2 | `database/docker-compose.postgres.yml:26–28` fallback `vibegraph/vibegraph` | Đổi sang `${VAR:?}` fail-fast làm file tiện ích dev độc lập này không chạy được mà không có `.env`. **Nhánh đúng: giữ nguyên + thêm comment cảnh báo chỉ dùng local** |
| D-L5 | `logs/` (6 file) và `.vibegraph/` — 2 convention log song song | ⚠️ `.vibegraph/` **không chỉ là log** — chứa script vận hành thật + ảnh bằng chứng + `eval-repos`. "Chuẩn hoá về một chỗ" mà hiểu sai sẽ xoá dữ liệu không phải log. Nếu làm: **chỉ hợp nhất đường ghi log**, giữ nguyên phần còn lại |
| D-L6 | `vibegraph-web/Dockerfile:6–11` `ARG VITE_*` → bake vào bundle lúc build | Runtime injection cho Vite đòi placeholder + entrypoint thay thế trong JS đã build — dễ hỏng CSP mà nginx đang phát (`nginx.conf.template`). **Chọn nhánh tài liệu hoá**: `grep -c "VITE_API_URL" DEVOPS-GUIDE.md` ≥ 1 và đoạn đó nêu rõ "đổi giá trị ⇒ rebuild image" |
| *(đã xong)* | D-L3, D-L4 | Xem §0 |

**Đề nghị cho Lô C:** làm **B-L6** (sửa ERD) và **D-L2** (thêm comment) — cả hai là tài liệu, 0 rủi ro code. 10 mục còn lại: ghi nhận trong báo cáo, không sửa.

---

## 5. Nghiệm thu B-L1 — bắt buộc, tách riêng vì đây là mục duy nhất có thể hỏng âm thầm

B-L1 gom 2 method dựng parser. Nếu gom sai, `CALLS` edge biến mất mà **không test nào fail**. Nên nghiệm thu B-L1 **không** được dựa vào test suite.

**Trước khi sửa** — chọn 1 project đã import, ghi lại 2 số bằng `cypher-shell`:

```cypher
MATCH (n:Symbol {projectId:'<id>'}) RETURN count(n) AS nodes;
MATCH (:Symbol {projectId:'<id>'})-[r:CALLS]->(:Symbol {projectId:'<id>'}) RETURN count(r) AS calls;
```

**Sau khi sửa:** re-analyze **đúng project đó**, đo lại 2 số.

**Tiêu chí: cả hai bằng nhau tuyệt đối. Lệch 1 edge = fail, hoàn nguyên.**

Ghi vào báo cáo: projectId, `nodes_before/after`, `calls_before/after`. Không ghi "graph vẫn đúng".

---

## 6. Luật cứng

1. **Không commit.** Reviewer commit sau khi nghiệm thu.
2. **Mọi số liệu tự báo phải đo lại bằng lệnh trước khi ghi.** Không dùng PowerShell `Get-Content | Measure-Object -Line` để đếm dòng — nó đếm sai với file có dòng dài; dùng `wc -l`. Đây là gốc của con số sai 2.821/2.681 hồi trước.
3. **Đếm test:** `rm -rf target/surefire-reports` trước, rồi dán **nguyên dòng summary của Maven**. Đừng gộp XML tồn đọng — reviewer đã mắc đúng lỗi này và ra con số 1.065 sai.
4. **Con số kỳ vọng đã ghi sẵn ở §2 (1029).** Ra số khác thì DỪNG và báo, **không tự giải thích cho khớp**.
5. **Không `git clean -fdX`** — xoá ~985 MB gồm `node_modules/`, `.gitnexus/`, `target/`, `.vibegraph/`.
6. **Không `mvnw clean`** — có tiến trình java giữ file trong `target/`.
7. **Không xoá volume**, không `docker compose down -v`.
8. **Không in giá trị secret.** Nếu đọc `.env`, chỉ grep tên biến.
9. **KHÔNG chạm** `task/`, `task-final/` (D-M5 chờ quyết định) và `update/docs/claude/**` (reviewer sở hữu).
10. **Tài liệu trái dữ liệu thật thì DỪNG và báo**, đừng tự ứng biến — cách bạn xử lý S-M5 là đúng, giữ nguyên lối đó.

---

## 7. Báo cáo phải có gì

Mỗi mục một dòng, đúng định dạng này:

| Mã | File đã sửa | Dòng thay đổi | Lệnh nghiệm thu đã chạy | Kết quả thật (dán output) | Lệch so với tài liệu này |
|---|---|---|---|---|---|

Kèm 4 phần:

1. **Summary Maven nguyên văn** sau `rm -rf target/surefire-reports && ./mvnw -B test`.
2. **Kết quả frontend**: `npm run type-check`, `npx vitest run`, `npm run build` — dán dòng kết quả.
3. **Bảng B-L1**: projectId + `nodes_before/after` + `calls_before/after`.
4. **Mục nào bạn DỪNG và vì sao** — kèm lệnh cho thấy dữ liệu trái tài liệu.

Với mỗi mục Lô C bạn chọn **không** sửa: ghi rõ "ghi nhận, không sửa" — đừng để trống, vì trống không phân biệt được với bỏ sót.

---

## 8. Thứ tự đề nghị

1. **Lô A** (B-L3, B-L4) — xoá code chết, một lần nghiệm thu, đưa test về 1029.
2. **Lô B mục 1–4** (F-L4, F-L2, F-L1, F-L3) — toàn frontend, rủi ro tăng dần, không đụng backend.
3. **Lô B mục 5–7** (B-L2, B-L7, B-L5) — backend, mỗi mục một test riêng. B-L7 phải khởi động 2 profile; B-L5 phải giữ guard `"*"`.
4. **Lô C**: B-L6 + D-L2 (tài liệu, 0 rủi ro).
5. **B-L1** (Lô B mục 8) — làm gần cuối, nghiệm thu bằng số edge Cypher ở §5.
6. **B-L8** (Lô B mục 9) — cuối cùng, vì nó đổi hành vi drop telemetry và cần đo 2 counter.

D-M5 và 10 mục Lô C còn lại: **không làm**, chờ quyết định.
