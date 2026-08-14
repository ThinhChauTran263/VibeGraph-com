# BÁO CÁO AUDIT ĐỘC LẬP — VibeGraph (bản Claude)

- **Ngày:** 12/08/2026
- **Vị trí trong bộ tài liệu:** báo cáo thứ 4, ngang cấp `Qwen/`, `codex/`, `claudepostman/`. Để 3 bản kia đối chiếu vào.
- **Quan hệ với tài liệu khác của tôi:** [`CROSS-AUDIT-VERIFICATION.md`](./CROSS-AUDIT-VERIFICATION.md) là kiểm chứng 3 bản kia. **File này thì không lấy finding nào từ chúng** — mọi mục dưới đây tôi tự quét ra.

---

## 1. Phương pháp — và tại sao báo cáo này ngắn

**Nguyên tắc:** chạy lệnh trước, đọc code sau. Mỗi finding kèm lệnh tái lập. Không có mục nào dựa trên "đọc thấy hợp lý".

**Chọn trục có chủ đích:** 3 báo cáo trước đã phủ dày Docker/compose, secrets, filter chain, `SourceFileServiceImpl`, god class, dead code frontend, npm audit, actuator, project registry. Tôi **không lặp lại** những trục đó. Tôi chọn 10 trục chúng bỏ trống:

| # | Trục | Kết quả |
|---|---|---|
| 1 | Nuốt exception toàn backend (240 catch block / 521 file) | **Sạch** — xem §4 |
| 2 | `@Transactional` sai (private/protected, readOnly bọc write) | **Sạch** |
| 3 | Thread-safety: static mutable state, formatter dùng chung | **Sạch** (trừ 1 mục Qwen đã có) |
| 4 | `@Scheduled` — 7 job, pool size, chồng lần chạy | 🟠 **H1** |
| 5 | 21 migration Flyway | **Sạch** (trừ V16 gap Qwen/v2 đã có) |
| 6 | Vue reactivity: mutate props, listener leak | **Sạch** |
| 7 | Chất lượng test (174 file, 1.060 `@Test`) | 🟡 **M2**, ⚪ **L3** |
| 8 | N+1 ngoài `AdminService` | **Sạch** |
| 9 | Rò rỉ thông tin qua `GlobalExceptionHandler` | 🟡 **M1** |
| 10 | Dead code **backend** (không phải frontend) | ⚪ **L1**, **L2** |

**Kết quả: 1 High, 3 Medium, 3 Low = 7 finding.** Con số nhỏ là có chủ đích — 6/10 trục kiểm ra **sạch**, và tôi báo cáo kết quả âm thay vì bơm thêm mục. §4 ghi rõ từng trục sạch và lệnh nào chứng minh.

Ngoài ra §5 **bác bỏ 3 claim** của báo cáo khác bằng bằng chứng.

---

## 2. THỐNG KÊ

| Mức | Số lượng |
|---|---|
| 🔴 Critical | 0 |
| 🟠 High | 1 |
| 🟡 Medium | 3 |
| ⚪ Low | 3 |
| **Tổng finding mới** | **7** |
| Trục kiểm ra sạch | 6 |
| Claim của bên khác bị bác bỏ | 3 |

Không có Critical vì Critical duy nhất của codebase này (secret trong git object DB) đã do Qwen tìm ra — tôi không nhận lại.

---

## 3. FINDING

### 🟠 H1 — Toàn bộ 7 job `@Scheduled` chạy trên **1 thread**; purge ban đêm làm hệ thống mù cảnh báo bảo mật

**Đây là chuỗi qua 4 file mà không báo cáo nào nối lại.** Qwen tìm ra khâu cuối (B-L8) nhưng xếp Low vì cho là "best-effort có chủ đích" — nó không thấy có **cò súng chạy mỗi đêm**.

**Cơ chế, từng khâu đều đã kiểm:**

**(1) Scheduler chỉ có 1 thread.** Không có `spring.task.scheduling.*` trong bất kỳ `application*.yaml`, không có bean `TaskScheduler`/`ThreadPoolTaskScheduler` nào. `AsyncConfig` chỉ định nghĩa `analysisExecutor` — đó là `ThreadPoolTaskExecutor` cho `@Async`, **không phải** scheduler. Spring Boot mặc định `ThreadPoolTaskScheduler` **pool size = 1**.

```bash
grep -rn 'task:' -A6 src/main/resources/application*.yaml | grep -iE 'scheduling|pool'   # rỗng
grep -rn 'TaskScheduler' src/main/java --include=*.java | grep -v import                  # rỗng
grep -nE '@Bean|ThreadPoolTask' src/main/java/com/vibegraph/common/config/AsyncConfig.java
```

**(2) 7 job dùng chung 1 thread đó, 5 job dồn vào khung 02:00–03:30.**

| Job | Lịch |
|---|---|
| `RequestEventService.flush` | `fixedDelay 2000ms` ← **drainer duy nhất của telemetry** |
| `OnlineUserHistoryService.sampleCurrentUsers` | `fixedRate 30s` |
| `FeedbackReportService.cleanupExpiredReports` | cron `0 0 2 * * ?` |
| `AuditService` (dòng 140) | cron `0 30 2 * * ?` |
| `SupabaseRetentionService.cleanupExpiredData` | cron `0 0 3 * * ?` |
| `RefreshSessionService.purgeExpiredSessions` | cron `0 15 3 * * ?` |
| `ProjectTrashService.purgeExpiredProjects` | cron `0 30 3 * * ?` |

```bash
grep -rn 'Scheduled(' src/main/java --include=*.java | grep -v '^\s*\*'   # 7 kết quả
```

**(3) `purgeExpiredProjects` không có trần và rất đắt.** `ProjectTrashService.java:113` gọi `ownershipRepository.findByDeletedAtLessThan(cutoff)` — **không LIMIT, không phân trang**. Mỗi phần tử gọi `purgeQuietly` → `deletionOrchestrator.purge(projectId)`, tức xoá graph Neo4j + `Files.walk` xoá đệ quy toàn bộ thư mục source (`ProjectDeletionOrchestrator.java:179–200`). Job này chạy bao lâu là hàm của số project trong thùng rác — không có chặn trên.

**(4) Trong lúc nó chạy, `flush()` không thể chạy** — cùng 1 thread. Queue là `ArrayBlockingQueue` mặc định **10.000** (`RequestEventService.java:85`, `application.yaml:125`).

**(5) Queue đầy → shed-oldest → mất security event.** `RequestEventService.java:359` `freshQueue.poll()` bỏ phần tử cũ nhất; `:343` `securityDropped.increment(lostSecurityEvents)`.

**Hậu quả cụ thể:** ở 50 req/s, queue 10.000 đầy sau **200 giây**. Một lần trash sweep xoá vài chục project (mỗi cái = Neo4j delete + walk xoá cây file) vượt 200s là bình thường. Từ đó trở đi, mỗi request mới **đẩy một event cũ ra khỏi queue** — gồm cả event `RATE_LIMIT`. Nghĩa là **cứ 03:30 mỗi đêm, hệ thống có một cửa sổ mù cảnh báo tấn công**, và cửa sổ đó dài đúng bằng thời gian purge. Đây cũng chính là khung giờ hấp dẫn nhất để tấn công.

Metric `security_events.dropped.total` có ghi nhận, nhưng không ai đặt alert trên nó (không có alerting rule nào trong repo).

**Giải pháp:**

```yaml
# application.yaml — tách scheduler khỏi thế cổ chai 1 thread
spring:
  task:
    scheduling:
      pool:
        size: 4
      thread-name-prefix: vg-sched-
```

```java
// ProjectTrashService — chặn trên mỗi lần chạy, phần dư để lần sau
List<ProjectOwnership> expired =
        ownershipRepository.findByDeletedAtLessThan(cutoff, PageRequest.of(0, 200));
```

```java
// RequestEventService.offer — ưu tiên giữ security event khi queue đầy
if (!freshQueue.offer(event)) {
    if (event.securityEvent() != null) {
        evictNonSecurityOldest();          // hy sinh event thường trước
        if (freshQueue.offer(event)) return;
    }
    countDrop(event);
}
```

**Thứ tự sửa:** đặt `pool.size` trước (1 dòng yaml, rủi ro ~0, cắt ngay khâu 4). Chặn trần query sau. Sửa ưu tiên queue cuối vì đụng vào `offer` — chạy `gitnexus_impact` trước.

---

### 🟡 M1 — `IllegalStateException` bị map thành 409 kèm nguyên văn `getMessage()`

**Vị trí:** `GlobalExceptionHandler.java:236–246`

```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
    // ...directory browsing disabled because no allowed-root is configured...
    ErrorResponse error = ErrorResponse.builder()
            .code("PRECONDITION_FAILED")
            .message(ex.getMessage())      // ← nguyên văn ra client
            .build();
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(error));
}
```

**Hai vấn đề, không phải một:**

1. **Rò rỉ.** Comment cho thấy ý định là bắt lỗi precondition *của app*. Nhưng `IllegalStateException` là ngoại lệ JDK mà **mọi thư viện đều ném**: Spring (`getReaderForRequest`, bean lifecycle), Neo4j driver (session/transaction đã đóng), JavaParser, `HttpClient`, Jackson. Bất kỳ cái nào trong số đó ném ra ở request path → client nhận nguyên văn thông điệp nội bộ của thư viện.

2. **Sai status che mất 500.** Ngoại lệ hạ tầng thật (Neo4j session closed, connection pool exhausted) sẽ trả **409 Conflict** thay vì 500. Handler bắt-tất-cả ở `:316–323` mới log `log.error("Unhandled exception", ex)`; nhánh 409 này **không log gì**. Nghĩa là một lớp lỗi hạ tầng đi ra ngoài mà không để lại dấu trong log, và mọi dashboard đếm 5xx đều không thấy.

**Đối chiếu:** handler bắt-tất-cả ở `:316–323` làm đúng — trả `"An unexpected error occurred"` chung, không rò. Vấn đề chỉ ở nhánh `IllegalStateException`.

**Giải pháp:** thay bằng exception của chính app, đừng bắt ngoại lệ JDK.

```java
// Ném BrowseDisabledException (extends AppPreconditionException) từ service
// rồi map đúng nó. IllegalStateException để handler bắt-tất-cả xử lý.
@ExceptionHandler(AppPreconditionException.class)
public ResponseEntity<ApiResponse<Void>> handlePrecondition(AppPreconditionException ex) {
    log.warn("Precondition failed: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ErrorResponse.builder()
                    .code(ex.getCode()).message(ex.getMessage()).build()));
}
```

```bash
grep -nE 'IllegalStateException|INTERNAL_SERVER_ERROR' src/main/java/com/vibegraph/common/exception/GlobalExceptionHandler.java
```

---

### 🟡 M2 — 3 file test **rỗng 0 byte** đang được git track; con số "174 file test" mà cả 3 báo cáo khen là bị thổi

**Vị trí:**

| File | Kích thước | Tracked |
|---|---|---|
| `src/test/java/com/vibegraph/VibeGraphIT.java` | **0 byte** | YES |
| `src/test/java/com/vibegraph/graph/importer/github/GitHubImportIT.java` | **0 byte** | YES |
| `src/test/java/com/vibegraph/integration/FinalIntegrationTest.java` | **0 byte** | YES |

**Tại sao là vấn đề, không chỉ là rác:**

- Hai file `*IT.java` **nằm trong include của failsafe** (`pom.xml:346–348`: `**/*IT.java`). CI chạy integration-test phase, quét 2 file này, thấy 0 test, báo pass. File `.java` rỗng là Java hợp lệ — compile ra không class nào, **không cảnh báo gì**.
- `GitHubImportIT.java` rỗng nghĩa là **luồng import GitHub không có integration test nào** — trong khi đó là luồng duy nhất chạm mạng ngoài, và chính là bề mặt mà v2 §10/F10 + Qwen S-L5 lo (`Redirect.NORMAL`, owner/repo cho phép `.`/`..`). Bề mặt được cảnh báo nhất lại là bề mặt có file test rỗng mang đúng tên nó.
- Con số **"174 file test"** xuất hiện trong cả 3 báo cáo như một điểm mạnh. Đo lại:

| | |
|---|---|
| File `.java` trong `src/test` | 174 |
| File có ít nhất 1 `@Test`/`@ArchTest` | **155** |
| Chênh | **19 file không chứa test nào**, 3 trong đó rỗng hoàn toàn |
| Tổng `@Test` | 1.060 |
| Tổng `@Disabled` | 9 |

```bash
find src/test -name '*.java' -size -2c
grep -rl '@Test\|@ArchTest' src/test --include=*.java | wc -l    # 155, không phải 174
grep -n -A8 'failsafe' pom.xml | grep include -A3
```

**Giải pháp:** viết nội dung hoặc `git rm` cả 3. Ưu tiên viết `GitHubImportIT` thật (WireMock chặn redirect ra host khác + reject `.`/`..` trong owner/repo) vì nó khoá luôn 2 finding phòng thủ chiều sâu kia. Thêm gate CI chặn file test rỗng:

```bash
# thêm vào CI: fail nếu có file test không chứa @Test
! find src/test -name '*Test*.java' -o -name '*IT.java' | xargs grep -L '@Test\|@ArchTest' | grep .
```

---

### 🟡 M3 — `purgeExpiredProjects` truy vấn không trần *(độc lập với H1)*

**Vị trí:** `ProjectTrashService.java:113`

Đã mô tả ở H1 khâu (3), nhưng tách riêng vì **vẫn là vấn đề dù đã tăng pool size**: `findByDeletedAtLessThan(cutoff)` nạp toàn bộ ownership hết hạn vào `List`, rồi `purgeQuietly` từng cái. Không LIMIT, không phân trang, không giới hạn thời gian chạy. Số project trong thùng rác tăng theo người dùng; job này không có chặn trên về bộ nhớ lẫn thời gian.

Cùng dạng với `AdminService` N+1 (Qwen H9) và `ProjectController.list` load-toàn-tenant (v2 M2) — nhưng ở job nền nên không ai thấy khi test tay.

**Giải pháp:** phân trang như snippet ở H1, và log rõ số còn lại để lần chạy sau xử lý tiếp.

---

### ⚪ L1 — 6 DTO chết, 98 dòng, 0 tham chiếu kể cả trong test

| File | Dòng |
|---|---|
| `common/dto/request/PaginationRequest.java` | 17 |
| `graph/dto/request/AnalyzeRequest.java` | 15 |
| `mcp/dto/request/ClassContextRequest.java` | 15 |
| `mcp/dto/request/LayerPatternRequest.java` | 15 |
| `parser/dto/request/ParseFileRequest.java` | 16 |
| `parser/dto/response/ParseResultResponse.java` | 20 |

Cả 6 **không có annotation Spring nào** và **0 tham chiếu** trong `src/main`, `src/test`, `*.yaml`, `*.xml`. DTO không thể được dùng mà không có tham chiếu kiểu ở đâu đó (không phải bean được inject theo interface) — nên 0 ref ở đây là dead thật, khác với các `*Impl` bị Spring inject theo interface.

**Lưu ý phương pháp:** quét thô "class 0 tham chiếu" ban đầu ra 15 kết quả. 9 trong đó là **false positive** — `@Service`/`@Repository`/`@Configuration`/`@Entity` được Spring wire theo interface (`CachingGraphRepository` có `@Primary @Repository` và đang chạy thật). Chỉ 6 DTO không annotation là dead. Ai chạy lại quét này mà không lọc annotation sẽ báo sai 9 mục.

**Giải pháp:** `git rm` cả 6.

```bash
for c in PaginationRequest AnalyzeRequest ClassContextRequest LayerPatternRequest ParseFileRequest ParseResultResponse; do
  echo "$c: $(grep -rlw "$c" src/main src/test --include=*.java | grep -vc "/$c\.java$") ref"
done
```

---

### ⚪ L2 — Chỉ **entity JPA** `UserNotification` là chết; bảng `user_notifications` vẫn đang được dùng qua JDBC

> **ĐÃ SỬA 12/08/2026 — bản đầu của mục này SAI.** Xem §9.4.

**Vị trí:** `auth/domain/UserNotification.java` — `@Entity`, và `grep -rlw UserNotification src/main/java` trả về **đúng 1 file: chính nó**. Không repository JPA, không service, không controller nào dùng entity này.

**Nhưng bảng thì đang chạy thật.** `JdbcNotificationRepository.java` (`common/supabase/repository/`) truy vấn bảng bằng SQL thô — 5 chỗ: `LEFT JOIN user_notifications` (`:37`), `DELETE FROM user_notifications` (`:91`), `INSERT INTO user_notifications` (`:106`) kèm 2 nhánh `ON CONFLICT` (`:103`, `:104`).

Vậy đây **không phải** tính năng dở dang, mà là **entity JPA bị thay thế bởi đường JDBC** và không được xoá đi. Index `idx_user_notifications_user_created` (`V10:21`) **có được dùng**, không hề bị bảo trì vô ích.

**Giải pháp:** xoá **chỉ** `UserNotification.java`. **GIỮ** bảng và migration V10.

```bash
grep -rn 'user_notifications' src/main/java --include=*.java   # 6 hit: 1 ở entity, 5 ở JdbcNotificationRepository
grep -rlw 'UserNotification' src/main/java --include=*.java    # 1 file — chính nó
```

**Tương ứng:** Qwen `B-L10` — Qwen là bên phát hiện lỗi này của tôi.

---

### ⚪ L3 — `TarballImportServiceTest` tắt 8/8 test, trùng lặp với file test thật

**Vị trí:** `src/test/java/com/vibegraph/graph/service/TarballImportServiceTest.java` — 8 `@Test`, **8 `@Disabled`**, lý do đều là *"Chờ TarballImportServiceImpl..."*.

Nhưng `TarballImportServiceImpl.java` **đã tồn tại** (301 dòng), và `TarballImportServiceImplTest.java` (218 dòng, 5 test, 0 disabled) **đang phủ nó thật**. Đây là bộ khung TDD cũ bị bỏ lại sau khi impl xong bằng file test khác.

**Đây là 8 trong 9 `@Disabled` của toàn repo** — nên nếu ai nhìn con số `@Disabled` để đánh giá sức khoẻ test suite thì đang nhìn vào một file rác duy nhất.

**Không phải lỗ coverage** — tôi đã kiểm và tự bác bỏ giả thuyết đầu tiên của mình. Chỉ là dead test code gây nhiễu.

**Giải pháp:** `git rm` file khung cũ.

```bash
grep -c '@Test' src/test/java/com/vibegraph/graph/service/TarballImportServiceTest.java       # 8
grep -c '@Disabled' src/test/java/com/vibegraph/graph/service/TarballImportServiceTest.java   # 8
grep -rh '@Disabled' src/test --include=*.java | wc -l                                        # 9 toàn repo
```

---

## 4. TRỤC ĐÃ KIỂM RA **SẠCH** — kết quả âm, kèm lệnh

Phần này quan trọng ngang phần finding. Nó cho 3 báo cáo kia biết trục nào **không cần soi lại**, và lệnh nào chứng minh.

### 4.1. Xử lý exception backend — sạch, tốt hơn tôi tưởng

- **0 catch block rỗng** trên 240 catch / 521 file.
- 20 chỗ `catch (... ignored)`. Kiểm từng chỗ: 13 chỗ **có comment giải thích hoặc có `log.warn`**; 7 chỗ còn lại đều là **ngoại lệ hẹp với giá trị mặc định hợp lý** (`NumberFormatException` → `false`/`null`, `InvalidPathException` → `false`, `IllegalArgumentException` → `Optional.empty()`).
- `RateLimitFilter.java:135` và `ProjectDeletionOrchestrator.java:187,215` từng làm tôi nghi fail-open — đọc ra đều **có chủ đích và có comment**: telemetry không được phép đổi response; lỗi xoá file được báo một lần qua kiểm tra "thư mục còn tồn tại" ở dưới.
- Chỗ duy nhất đáng sửa là `Neo4jGraphRepository.java:125,128` — `catch (RuntimeException)` lồng 2 tầng rồi `return null`, nuốt lỗi dữ liệu. **Qwen B-M1 đã tìm ra**, tôi không nhận lại.

**Kết luận: câu "0 catch rỗng" của Qwen đúng, và mạnh hơn thế — codebase này xử lý exception có kỷ luật.**

```bash
grep -rnzoP 'catch\s*\([^)]*\)\s*\{\s*(//[^\n]*\s*)*\}' src/main/java --include=*.java   # rỗng
```

### 4.2. Ranh giới `@Transactional` — sạch

- **0** `@Transactional` trên method `private`/`protected` (Spring proxy sẽ bỏ qua âm thầm — lỗi rất phổ biến, ở đây không có).
- **0** trường hợp `@Transactional(readOnly = true)` bọc `save`/`delete`/`flush`.

```bash
grep -rn -A2 '@Transactional' src/main/java --include=*.java | grep -E 'private |protected '   # rỗng
```

### 4.3. Thread-safety — sạch

- **0** `SimpleDateFormat`/`DateFormat` dùng chung (bẫy kinh điển).
- 3 static collection: `JwtAuthFilter.ACTIVE_USERS` (cả 3 báo cáo đã có), `ReferenceAnalyzerImpl.VALID_EDGE_TYPES` (dựng từ enum, chỉ đọc), `GenericRelationInferer.CAPABILITY_VERB` — `LinkedHashMap` nhưng **chỉ ghi trong static initializer** (dòng 82–104), runtime chỉ `entrySet()` đọc ở dòng 185. An toàn.

### 4.4. Migration Flyway — sạch

21 file. **0** trường hợp `ADD COLUMN ... NOT NULL` thiếu `DEFAULT` (bẫy rewrite bảng / fail khi có data).

Về `CREATE INDEX` không `CONCURRENTLY`: **tôi cố tình không tính đây là finding.** Flyway chạy migration trong transaction, mà Postgres **không cho** `CREATE INDEX CONCURRENTLY` trong transaction. Trên bảng nhỏ hiện tại thì lock không đáng kể. Báo mục này là bơm số — trục này sạch.

Gap V16 (V15 → V17) + `ignore-migration-patterns: "*:missing"`: **v2 M7 / Qwen đã có**, tôi xác nhận đúng chứ không nhận lại.

### 4.5. Vue reactivity — sạch

- **0** trường hợp mutate `props.*` trực tiếp.
- 19 `addEventListener` / 14 `removeEventListener`. Chênh 5 nhìn như leak, nhưng đọc ra thì không: `stores/admin.ts:415,580` gắn listener lên **chính object `EventSource`** (không phải `window`), và `startSecurityStream:397` có guard `if (securityEventSource) return`. Listener chết cùng `EventSource` khi `close()`. Không leak.
- `LandingView.vue` (F-L1/F-L2 của Qwen) là leak thật — Qwen đã có.

### 4.6. N+1 ngoài `AdminService` — sạch

Quét repository-call-trong-loop trên toàn bộ `*Service*.java`. Kết quả đều là **pattern đúng**: `findAllById(ids)` batch (`AdminSecurityMonitorService:48,109`, `AuditService:173`), hoặc `findAll().stream()` trên bảng cấu hình nhỏ (plans, pricing rules, feature flags — vài chục dòng), hoặc query aggregate có `LIMIT` (`findTopStorageUsers(5)`).

Chỗ N+1 thật duy nhất là `AdminService.toAdminUserResponse` — **Qwen H9 đã có**, tôi đã xác minh đúng trong `CROSS-AUDIT-VERIFICATION.md`.

### 4.7. Handler bắt-tất-cả — sạch

`GlobalExceptionHandler:316–323` trả `"An unexpected error occurred"` chung và `log.error` đầy đủ. Không rò stack trace, không rò `ex.getMessage()`. Chỉ nhánh `IllegalStateException` có vấn đề (M1).

---

## 5. BÁC BỎ — claim của báo cáo khác, đã kiểm là SAI

| Claim | Nguồn | Kiểm chứng |
|---|---|---|
| `Neo4jGraphRepository.getNeighborhood` **ném `UnsupportedOperationException`** (chưa làm) | ClaudePostman L2 | `grep -rn 'getNeighborhood' src/main/java` → **0 kết quả**. Method này không tồn tại. CP tự ghi nguồn là *"README tự nhận"* — nó lấy từ văn bản README, không kiểm code |
| `ImpactController` là **scaffold rỗng** | ClaudePostman L2 | `find src -name 'ImpactController*.java'` → **0 file**. Class này không tồn tại |
| "Không có `.dockerignore` rõ ràng cho context" | ClaudePostman C3 | Root `.dockerignore` **tồn tại**, 112 byte. (Thiếu thật là `vibegraph-web/.dockerignore` — Qwen H5 đúng) |

Cả 3 đều từ ClaudePostman, và cả 3 đều cùng một nguyên nhân: **lấy phát biểu từ tài liệu (README) thay vì từ code**.

---

## 6. ĐỐI CHIẾU VỚI 3 BÁO CÁO KIA

| Finding của tôi | Có trong Qwen? | codex/v2? | ClaudePostman? |
|---|---|---|---|
| H1 — 7 job / 1 thread → mù security event ban đêm | khâu cuối (B-L8, xếp Low) | khâu cuối (§10 F11, chưa xếp hạng) | khâu cuối (L3) |
| M1 — `IllegalStateException` → 409 + raw message | ✗ | ✗ (khen "map ~25 exception" là điểm mạnh) | ✗ |
| M2 — 3 file test rỗng; "174 test" bị thổi | ✗ (khen "CI có coverage gate") | ✗ (khen "174 file test, JaCoCo 70%") | ✗ |
| M3 — `purgeExpiredProjects` không trần | ✗ | ✗ | ✗ |
| L1 — 6 DTO chết backend | ✗ (chỉ tìm dead code FE) | ✗ | ✗ (đề nghị "chạy tool đi") |
| L2 — entity `UserNotification` không dùng | ✗ | ✗ | ✗ |
| L3 — `TarballImportServiceTest` tắt 8/8 | ✗ | ✗ | ✗ |

**Không có mục nào trùng.** Khâu cuối của H1 thì cả 3 đều thấy — nhưng cả 3 đều xếp Low/chưa-xếp-hạng vì coi là thiết kế best-effort. Điểm mới của tôi là chứng minh nó **có cò súng chạy mỗi đêm 03:30**, biến "best-effort" thành "mù định kỳ".

Điểm mạnh mà cả 3 khen sai, tôi phản biện: `"174 file test"` (thật: 155 có test, 3 file rỗng) và `"GlobalExceptionHandler map ~25 exception sang ApiResponse chuẩn"` (đúng về số lượng, nhưng 1 trong số đó rò nguyên văn thông điệp nội bộ và trả sai status).

---

## 7. TRỤC TÔI **KHÔNG** PHỦ — đừng coi tài liệu này là đã audit toàn bộ

Trung thực về phạm vi. Những vùng sau tôi **không mở file**:

- **Toàn bộ tầng parser** (`ParserServiceImpl` 505 dòng, `MethodVisitor` 780, `UseCaseInferenceEngine` 1398) — trừ 1 dòng `Boolean.getBoolean` mà Qwen đã có.
- **Neo4j upsert atomicity / chunking** (v2 H1) — không tự kiểm.
- **`getFullGraph` shape và chi phí query** (v2 M1) — không tự kiểm.
- **Registry project in-memory** (v2 C1, Qwen H6) — không tự kiểm.
- **TOCTOU quota `used_bytes`** (CP M3) — không tự kiểm.
- **Toàn bộ frontend ngoài 2 trục ở §4.5** — không review component, không đo bundle, không kiểm i18n.
- **Docker/compose, secrets, filter chain, actuator** — cố tình bỏ vì 3 bản kia đã phủ dày; tôi chỉ *kiểm chứng lại* chúng trong `CROSS-AUDIT-VERIFICATION.md`.
- **`searchNodes` reachability** (v2 L7) — tôi khen phương pháp của v2 §9 dựa trên **đọc** lập luận, không tự truy caller.
- **Hiệu năng thực đo** — không chạy load test, không profile. Mọi phát biểu về hiệu năng ở đây là phân tích cơ chế, không phải đo.

Ai đối chiếu vào tài liệu này: 7 finding ở §3 là đã kiểm; 6 trục ở §4 là đã kiểm-và-sạch; **mọi thứ ngoài hai danh sách đó tôi chưa biết.**

---

## 8. THỨ TỰ ĐỀ XUẤT

| Đợt | Việc | Rủi ro sửa |
|---|---|---|
| **Ngay** | `spring.task.scheduling.pool.size: 4` — 1 dòng yaml, cắt ngay khâu 4 của H1 | ~0 |
| **Ngay** | `git rm` 6 DTO chết (L1) + `TarballImportServiceTest` (L3) | ~0, đã xác minh 0 ref |
| **Tuần này** | Phân trang `purgeExpiredProjects` (M3) | thấp, job nền |
| **Tuần này** | Viết `GitHubImportIT` thật hoặc `git rm` 3 file rỗng (M2) + gate CI chặn file test rỗng | ~0 |
| **Sprint sau** | Thay `IllegalStateException` handler bằng exception của app (M1) | trung bình — cần rà mọi chỗ ném `IllegalStateException` trong service |
| **Sprint sau** | Ưu tiên security event khi queue đầy (H1 khâu 5) — **chạy `gitnexus_impact` trên `RequestEventService.offer` trước** | trung bình |
| **Quyết định** | `UserNotification` (L2): làm tiếp hay `DROP TABLE` | — |

---

## 9. GHI CHÚ PHƯƠNG PHÁP — 3 lần tôi tự bác bỏ giả thuyết của mình

Ghi lại vì đây là phần khó nhất và là chỗ 3 báo cáo kia hụt.

1. **"15/156 file test không có assertion"** — pattern grep của tôi thiếu `andExpect`. Tính lại: **4**, và 3 trong đó là file rỗng. Con số 15 sẽ là một finding sai nếu tôi báo luôn.
2. **"Luồng import GitHub tarball không có test"** — sai. `TarballImportServiceTest` tắt 8/8, nhưng `TarballImportServiceImplTest` (218 dòng, 5 test, 0 disabled) đang phủ thật. Finding tụt từ giả-định-HIGH xuống L3.
3. **"15 class backend dead code"** — 9 là false positive: Spring wire theo interface (`CachingGraphRepository` có `@Primary @Repository`, đang chạy). Chỉ 6 DTO không annotation là dead thật.

Cả 3 lần đều do lệnh quét đầu tiên **quá thô**, và đều được bắt bằng một lệnh thứ hai hẹp hơn. Đây là lý do §4 (kết quả âm) dài gần bằng §3 (finding).

### 9.4. Lần thứ tư — lỗi đã ship, do **Qwen** bắt được (không phải tôi)

**Bản đầu của L2 viết:** *"Postgres đang bảo trì index cho một bảng không có gì ghi vào"*, và đề xuất *"xoá entity + thêm migration `DROP TABLE`"*.

**Sai.** Bảng `user_notifications` đang được `JdbcNotificationRepository` dùng qua SQL thô (5 tham chiếu). Nếu ai làm theo đề xuất `DROP TABLE` của tôi thì **hỏng tính năng notification đang chạy**. Đây là finding duy nhất trong tài liệu này từng có remediation gây thiệt hại.

**Nguyên nhân:** tôi grep tên **class** (`UserNotification`) trong `src/main/java`, và grep tên **bảng** (`user_notifications`) chỉ trong `db/migration/*.sql` — **không bao giờ** grep tên bảng trong code Java. Thiếu đúng một lệnh.

**Đây chính xác là lỗi tôi đã phê ở `CROSS-AUDIT-VERIFICATION.md` §7.4:** dùng một lệnh không nhìn được nơi cần nhìn, rồi kết luận. ClaudePostman và codex/v2 dùng `git ls-files` (không thấy object DB); tôi dùng `git stash show` (không thấy parent thứ 3); rồi tôi lặp lại lần thứ ba với `grep UserNotification` (không thấy đường JDBC). Ba lần cùng một hình dạng.

**Bài học bổ sung cho §8:** khi kết luận một thứ là "chết", phải quét **mọi tên gọi** của nó — tên class, tên bảng, tên cột, tên endpoint, chuỗi trong SQL/YAML — chứ không chỉ định danh ở tầng mình đang đọc.

---

*Không sửa file nào của dự án trong quá trình audit. Mọi lệnh trong tài liệu này chạy lại được độc lập; các lệnh kiểm chứng chéo 3 báo cáo kia nằm trong [`verify-claims.sh`](./verify-claims.sh).*
