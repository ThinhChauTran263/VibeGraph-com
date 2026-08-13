# CHI TIẾT SỬA LỖI — BACKEND & DATABASE

- **Ngày tạo:** 12/08/2026 · **Nguồn:** `AUDIT-REPORT.md` (76 phát hiện) — mọi file/dòng trích từ báo cáo gốc.
- **Cấu trúc mỗi mục:** Hiện trạng (file:dòng) → Code đề xuất → Tiêu chí nghiệm thu.
- Snippet là **đề xuất triển khai** dựa trên phần "Đề xuất" của AUDIT-REPORT; khi sửa thật phải đối chiếu lại code tại thời điểm sửa.

---

## P0 — Nghiêm trọng

### S2. Dọn secret khỏi git object database (lệnh ĐÃ SỬA)

**Hiện trạng:** git stash object `388632b` (`stash@{0}`) chứa bản `.env.codex-backup-before-905919f-...140030` (159 dòng, đầy đủ JWT_SECRET/OAuth/Gemini); bản trong working tree là file KHÁC: `.env.codex_backup-before-9e1dfed-20260725-140618` ở root. Object tồn tại vĩnh viễn và truyền đi khi clone/push/bundle dù không nằm trên branch nào.

**Lệnh đề xuất (theo AUDIT-REPORT S2):**
```powershell
git stash drop stash@{0}
# kiểm tra cả stash@{1..3} — repo hiện có 4 stash
git reflog expire --expire=now --all
git gc --prune=now
```
KHÔNG dùng `git filter-repo` — sai công cụ: `.env` chưa từng commit lên branch nào, object chỉ sống qua stash nên drop stash + expire reflog + gc là đủ. Xóa thêm bản sao `.env.codex_backup-*` trong working tree.

**Tiêu chí nghiệm thu:**
- `git show 388632b:...` báo lỗi (object không còn).
- `git stash list` không còn stash chứa `.env`; kiểm tra trước đó cả `stash@{1..3}` không chứa secret.
- File `.env.codex_backup-before-9e1dfed-20260725-140618` đã xóa khỏi working tree.
- Toàn bộ secret đã rotate (điều kiện tiên quyết từ S1 — chạy S1 trước).

---

## P1 — Mức Cao

### H6. Project registry chỉ nằm in-memory

**Hiện trạng:** `ProjectServiceImpl.java` dòng 34 — toàn bộ status/progress/name sống trong `ConcurrentHashMap`; recovery từ Neo4j chỉ khôi phục name/rootPath. Bảng `projects` Postgres đã có sẵn đủ cột.

**Code đề xuất:**
```java
// Dùng DB làm nguồn sự thật, map chỉ là cache đọc:
ProjectResponse project = ownershipRepository.findByProjectId(id)
        .map(this::toResponse)
        .orElseThrow(() -> new ProjectNotFoundException(id));
```

**Tiêu chí nghiệm thu:** status/progress/name đọc từ bảng `projects`; restart backend không mất trạng thái project; `./mvnw verify` xanh.

### H7. Project ID 8 ký tự, không chống trùng

**Hiện trạng:** `ProjectServiceImpl.java` dòng 62, 91, 101 — `UUID.randomUUID().toString().substring(0, 8)` = 32 bit không gian ID ở cả 3 điểm tạo project; khi trùng, `projects.put(id, ...)` ghi đè lặng lẽ metadata project khác. Ghi chú AUDIT-REPORT: ngưỡng trùng ~50% cần ~77.000 project; giữ mức Cao do hậu quả ghi đè lặng lẽ.

**Code đề xuất:**
```java
private String newProjectId() {
    for (int attempt = 0; attempt < 5; attempt++) {
        String id = UUID.randomUUID().toString(); // full UUID
        if (!projects.containsKey(id)) return id;
    }
    throw new IllegalStateException("Unable to allocate unique project id");
}
```
Thay cả 3 điểm dòng 62, 91, 101 bằng `newProjectId()`.

**Tiêu chí nghiệm thu:** grep không còn `substring(0, 8)` trong `ProjectServiceImpl`; tạo 2 project liên tiếp nhận ID full UUID khác nhau; mọi nơi lưu/hiển thị project id (URL, log, frontend) tương thích ID dài.

### H8. `POST /{id}/analyze` chạy đồng bộ trên request thread

**Hiện trạng:** `ProjectController.java` dòng 106–119 — parse toàn bộ repo (JavaParser + Symbol Solver + upsert Neo4j) ngay trên thread Tomcat; tốn phút, chiếm thread pool, dính timeout reverse-proxy, không hủy được. Pattern chạy nền đã có sẵn trong `PatchAnalysisScheduler` (dòng 53–81).

**Code đề xuất:**
```java
@PostMapping("/{id}/analyze")
public ResponseEntity<ApiResponse<Void>> analyze(@PathVariable String id) {
    ownershipGuard.assertOwner(id);
    analyzeScheduler.schedule(id);              // nền, coalesce
    return ResponseEntity.accepted().build();   // 202 + WebSocket progress
}
```

**Tiêu chí nghiệm thu:** endpoint trả 202 ngay (< 1s) dù repo lớn; tiến độ phân tích đẩy qua WebSocket `/topic/projects/{id}/status`; frontend (phối hợp Sam) chuyển sang theo dõi WebSocket thay vì chờ response đồng bộ.

### H9. N+1 query trong AdminService

**Hiện trạng:** `AdminService.java` dòng 518–530 — mỗi user tốn 2 query (`settingsRepository.findById` + `sumStorageBytesByOwnerId`) khi map danh sách phân trang; trang 20 user = 40 query cộng thêm.

**Code đề xuất:**
```java
@Query("""
    SELECT p.ownerId AS ownerId, SUM(p.sizeBytes) AS total
    FROM ProjectUsage p WHERE p.ownerId IN :ids GROUP BY p.ownerId""")
List<StorageSum> sumStorageByOwners(@Param("ids") Collection<UUID> ids);

// Service: 2 query batch cho cả trang
Map<UUID, UserAccountSettings> settingsById = settingsRepository.findAllById(ids)
        .stream().collect(toMap(UserAccountSettings::getUserId, identity()));
```

**Tiêu chí nghiệm thu:** bật log SQL (profile dev) xác nhận trang 20 user chỉ tốn ≤ 2 query bổ sung; response endpoint admin nhanh hơn đo được trước/sau.

### H13. Rate-limit chạy SAU bước băm BCrypt — ĐÃ đo, CONFIRMED

**Hiện trạng:** `SecurityConfig.java` dòng 179–184 đặt `rateLimitFilter` bằng `addFilterBefore(..., AuthorizationFilter.class)` (dòng 184) → chạy SAU `jwtAuthFilter` (dòng 182) và `apiKeyAuthFilter` (dòng 183). `ApiKeyAuthFilter.findMatch` chạy tối đa 5 lần `passwordEncoder.matches()` (BCrypt, `MAX_PREFIX_CANDIDATES = 5`) TRƯỚC khi rate-limit kịp chặn. Kết hợp `VIBEGRAPH_TRUST_PROXY=true` (`.env:104`).

**Đã đo — CONFIRMED (V2.2 + V2.3, 12/08/2026):** median 30 request/nhóm: A không key 4,25ms / B prefix ngẫu nhiên 4,64ms / **C trùng prefix 54,84ms** (+~50,20ms chi phí bcrypt trước khi trả 401); 90/90 request nhận 401, 0 nhận 429. V2.3 xác nhận API-key filter chạy TRƯỚC rate-limit filter. (T5 trước đó không tái hiện do key giả prefix ngẫu nhiên → lookup rỗng; key test `runtime-h13-20260812` đã xóa, PostgreSQL xác nhận `deleted_at IS NOT NULL`.)

**Phụ thuộc bắt buộc: sửa S-M2 TRƯỚC.** Sửa H13 một mình vô hiệu: với API key sai, principal là anonymous → `RateLimitFilter.java:88` chỉ consume bucket IP (`"ip:"+ip`); IP do `ClientAddressResolver` phân giải — khi S-M2 chưa sửa (trust proxy true + `.findFirst()` token trái nhất), attacker xoay `X-Forwarded-For` mỗi request = khóa bucket rate-limit mới mỗi lần → bcrypt vẫn bị gọi dù filter đã đổi thứ tự. Trình tự: S-M2 (resolver right-most untrusted / thu hẹp trusted proxies) → H13 (đổi thứ tự filter).

**Code đề xuất:**
```java
// SecurityConfig — đưa rate-limit lên trước các filter xác thực:
.addFilterBefore(rateLimitFilter(clientAddressResolver(), meterRegistry),
        JwtAuthFilter.class)
```

**Tiêu chí nghiệm thu:** (1) sau khi sửa: key sai trùng prefix bị 429 trước khi tốn chi phí bcrypt (đo lại theo phép đo V2.2); (2) **biến thể bắt buộc:** đo lại V2.2 nhưng kèm `X-Forwarded-For` xoay vòng mỗi request — vẫn phải chạm 429 (chỉ pass khi S-M2 đã sửa trước); (3) regression toàn bộ auth flow (JWT, API key hợp lệ, rate-limit IP thật) xanh.

### H14. `readRange` nạp cả file vào RAM trước khi kiểm tra trần → OOM

**Hiện trạng:** `SourceFileServiceImpl.java` dòng 110 — `readAllLines(candidate)` nạp toàn bộ file vào RAM trước; trần `MAX_LINES`/`MAX_BYTES` chỉ áp sau đó (dòng 122–136). Nhánh search có chốt `Files.size()` (dòng 196) nhưng `readRange` thì không. Bằng chứng runtime T6: file 200MiB làm memory backend +211,6MiB dù response chỉ trả 300 dòng.

**Code đề xuất:**
```java
// Chốt kích thước trước khi đọc, cùng pattern với scanFile():
if (Files.size(candidate) > MAX_FILE_BYTES_TO_SCAN) {
    return notServed(relativePath, "File too large for source reading.");
}
List<String> lines = readAllLines(candidate);
```

**Tiêu chí nghiệm thu:** (1) chạy lại test T6 (`RUNTIME-VERIFICATION-PROMPT.md`) với file 200MiB: response trả lỗi giới hạn rõ ràng, memory container không tăng ~200MiB; (2) đo concurrency: 3 request đồng thời trên file 200MiB không còn cộng dồn ~+600MB heap, container không OOM-kill; (3) đo heap 60s sau request (`docker stats`): memory tụt về ~mức nền sau GC (spike, không phải leak) — nếu không tụt, báo lại để nâng mức độ phát hiện.

### H15. Redact private key chỉ che dòng header

**Hiện trạng:** `SourceFileServiceImpl.java` dòng 68 — regex `PRIVATE_KEY_HEADER` chỉ khớp dòng `-----BEGIN ... PRIVATE KEY-----`; `redact()` dòng 305–320 chạy per-line → thân base64 trả nguyên văn (file `.pem`/`.key` đổi đuôi thành extension được phép như `.txt` là lọt).

**Code đề xuất:**
```java
// Redact theo block: từ header tới dòng -----END tương ứng
private static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
        "-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----");
// áp trên nội dung toàn file thay vì từng dòng
```

**Tiêu chí nghiệm thu:** test với file chứa private key (đổi đuôi `.txt`): endpoint đọc source trả `[REDACTED]` cho toàn bộ block gồm thân base64; file nhiều key không bị che nhầm phần ngoài block (regex non-greedy).

### H17. Scheduler đơn luồng — tăng pool + purge phân trang (cùng cụm B-M14)

**Hiện trạng:** 7 job `@Scheduled` chạy chung 1 thread: `RequestEventService.java:146`, `OnlineUserHistoryService.java:21`, `FeedbackReportService.java:171`, `AuditService.java:140` (dạng FQN annotation), `SupabaseRetentionService.java:29`, `RefreshSessionService.java:193`, `ProjectTrashService.java:108` — không có cấu hình `spring.task.scheduling.*`, không bean `TaskScheduler` tùy chỉnh. Chuỗi nguyên nhân: job purge trash (`ProjectTrashService.java:113` query không phân trang → `ProjectDeletionOrchestrator.java:183–190` `Files.walk` xóa đệ quy) chiếm thread duy nhất → chặn `flush()` telemetry → queue 10.000 (`application.yaml:125`, `RequestEventService.java:85`) đầy → shed-oldest drop security event (B-L8).

**Code đề xuất:**
```yaml
# application.yaml
spring:
  task:
    scheduling:
      pool:
        size: 4   # tách flush telemetry khỏi job dài
```
Kèm purge trash theo batch + `Pageable` (xem B-M14 bên dưới) và ưu tiên không để job dài chiếm thread của job thời gian thực.

**Tiêu chí nghiệm thu:** actuator/metrics cho thấy scheduler pool 4 thread; tạo trash lớn + giả lập purge: `flush()` telemetry không bị trễ, counter `security_events.dropped.total` không tăng do nghẽn scheduler, gauge `request_events.queue.fresh.size` ổn định.

**Alert production (BẮT BUỘC khi ship — tác hại của H17 là im lặng, không log không lỗi):**
```yaml
# Ví dụ alert rule (Prometheus/Alertmanager hoặc tương đương):
- alert: SecurityEventsDropped
  expr: increase(security_events_dropped_total[5m]) > 0   # rate > 0 trong cửa sổ
  for: 1m
  labels: { severity: critical }
- alert: TelemetryQueueNearFull
  expr: request_events_queue_fresh_size > 8000            # > 80% sức chứa 10.000
  for: 5m
  labels: { severity: warning }
```
Không có alert = coi như chưa sửa xong phần vận hành của H17/B-L8.

---

## P2 — Trung bình nổi bật

### B-M12. `IllegalStateException` → 409 trả raw message, không log

**Hiện trạng:** `GlobalExceptionHandler.java` dòng 236–246 — map `IllegalStateException` → HTTP 409 trả nguyên văn `ex.getMessage()` cho client, nhánh này không log (catch-all có log tại :316–323).

**Code đề xuất:**
```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ApiResponse<Void>> conflict(IllegalStateException ex) {
    log.warn("Conflict state: {}", ex.getMessage(), ex);   // log nội bộ đầy đủ
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error("CONFLICT", "Thao tác không hợp lệ ở trạng thái hiện tại."));
}
```

**Tiêu chí nghiệm thu:** gọi endpoint gây `IllegalStateException`: response 409 với message chung an toàn; log backend có warn kèm stacktrace; kiểm tra frontend nơi hiển thị lỗi 409 không vỡ.

### B-M14. Purge trash truy vấn không phân trang

**Hiện trạng:** `ProjectTrashService.java` dòng 113 — `findByDeletedAtLessThan(cutoff)` kéo toàn bộ danh sách vào 1 transaction (độc lập với chuỗi H17 nhưng cùng được sửa chung đợt).

**Code đề xuất:**
```java
// Repository
Page<ProjectTrash> findByDeletedAtLessThan(Instant cutoff, Pageable pageable);

// Job: lặp theo batch thay vì 1 query toàn bộ
Page<ProjectTrash> page;
do {
    page = trashRepository.findByDeletedAtLessThan(cutoff, PageRequest.of(0, 200));
    page.forEach(this::purgeOne);
} while (page.hasNext());
```

**Tiêu chí nghiệm thu:** trash > 200 bản ghi vẫn được purge hết qua nhiều batch; mỗi batch giữ transaction ngắn; không bỏ sót bản ghi tạo mới giữa các batch (cutoff cố định đầu job).

### B-M9. IpBlockService query DB mỗi request, không cache

**Hiện trạng:** `IpBlockService.java` dòng 32–35 — `findActive` query DB mỗi request, không cache (`@Transactional(readOnly)` không `@Cacheable`), được gọi sớm cho mọi request qua `ipBlockFilter`.

**Code đề xuất:**
```java
@Cacheable(cacheNames = "ipBlocks", key = "#ip")
@Transactional(readOnly = true)
public Optional<IpBlock> findActive(String ip) { ... }
```
Kèm cấu hình cache TTL ngắn (30–60s) — Caffeine đã có sẵn trong pom (xem B-M6 để dùng chung hạ tầng cache).

**Tiêu chí nghiệm thu:** 2 request liên tiếp cùng IP → request sau không phát sinh query `ip_blocks` (log SQL/metrics); unblock IP có hiệu lực trong ≤ TTL đã chọn (ghi chú vận hành).

### B-M6. Cache LLM không giới hạn

**Hiện trạng:** `LlmUseCaseRefiner.java` dòng 71 — `responseCache` là `ConcurrentHashMap` không giới hạn, không TTL → heap tăng không chặn.

**Code đề xuất:**
```java
private final Cache<String, String> responseCache = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .build();
```
(đọc/ghi chuyển sang `cache.get(key, k -> ...)` / `put`).

**Tiêu chí nghiệm thu:** sau vượt `maximumSize` cache tự evict (kiểm tra metrics Caffeine hoặc test đơn vị); hit-rate LLM cache không giảm đáng kể trong flow refine.

### B-M1. `instantOrNull` nuốt exception 2 tầng

**Hiện trạng:** `Neo4jGraphRepository.java` dòng 119–132 — `catch (RuntimeException ignored)` / `ignoredAgain` ẩn hoàn toàn lỗi dữ liệu Neo4j.

**Code đề xuất:**
```java
} catch (RuntimeException ex) {
    log.warn("Failed to parse instant for node {}", id, ex);
    return null;
}
```

**Tiêu chí nghiệm thu:** test đơn vị với dữ liệu instant hỏng → có log.warn kèm id node, vẫn return null (hành vi ngoài không đổi).

### B-M3. `Boolean.getBoolean` đọc system property thay vì Spring config

**Hiện trạng:** `MethodVisitor.java` dòng 68 — `Boolean.getBoolean("vibegraph.parser.emit-unresolved-call-stubs")` đọc system property JVM → cấu hình trong application.yaml bị vô hiệu lặng lẽ.

**Code đề xuất:**
```java
// Cấu hình:
@ConfigurationProperties(prefix = "vibegraph.parser")
public record ParserFlags(boolean emitUnresolvedCallStubs) {}

// MethodVisitor nhận qua constructor (giữ default = giá trị hiện hành):
if (flags.emitUnresolvedCallStubs()) { ... }
```

**Tiêu chí nghiệm thu:** đặt `vibegraph.parser.emit-unresolved-call-stubs: true` trong application.yaml → hành vi bật (không cần `-D` JVM); mặc định giữ nguyên hành vi cũ.

### B-M5. Diff full-graph 2 lần mỗi save

**Hiện trạng:** `FileChangeBroadcaster.java` dòng 103–113 — mỗi lần đổi 1 file gọi 2 lần `getFullGraph` (before/after) để tính diff → O(kích thước graph) mỗi save; IDE autosave nhân chi phí.

**Code đề xuất:**
```java
// Diff theo file thay vì full-graph snapshot:
Set<GraphNode> before = repository.findNodesByFile(projectId, path);
// ... save ...
Set<GraphNode> after = repository.findNodesByFile(projectId, path);
DiffResult diff = DiffResult.between(before, after);
```

**Tiêu chí nghiệm thu:** save 1 file trong graph lớn: số query/khối lượng dữ liệu đọc giảm rõ rệt (so log/metrics trước-sau); kết quả diff gửi WebSocket giống hành vi cũ với bộ test fixture.

### B-M11. Upsert Neo4j không nguyên tử — đã kiểm chứng tĩnh (V1.1)

**Hiện trạng (đã tự kiểm chứng tĩnh, 12/08/2026 — `runtime-evidence/V1-static.txt`):** `Neo4jGraphRepository.upsertProject` gọi `session.run` (dòng 51–61); `upsertNodes` gom theo từng node label riêng biệt và gọi `session.run` 1 lần/nhóm label (dòng 135–184); `upsertEdges` gom theo từng relationship type và gọi `session.run` 1 lần/nhóm type (dòng 188–231) — KHÔNG có `session.executeWrite`/transaction tường minh nào bao các vòng lặp. `AnalyzeServiceImpl` gọi tuần tự `upsertProject` → `upsertNodes` → `upsertEdges` (dòng 104–112), nên mỗi analysis = 1 câu project + số câu bằng số node label khác nhau + số câu bằng số relationship type khác nhau (số câu phụ thuộc dữ liệu, không phải 3 câu cố định). Nhánh FAILED: `ArchiveImportServiceImpl.java:240–243` và `TarballImportServiceImpl.java:217–220` đánh dấu project FAILED rồi gọi `cleanup(workspace, null)` — cleanup chỉ xóa project khi projectId khác null → graph dở đã ghi KHÔNG bị dọn. Runtime V1.2 BLOCKED (không có project FAILED để đối chiếu, không kill backend dùng chung).

**Code đề xuất:**
```java
// 1) Gộp toàn bộ upsert của 1 analysis vào MỘT write transaction —
//    thay các session.run autocommit hiện có trong upsertProject/upsertNodes/upsertEdges:
session.executeWrite(tx -> {
    upsertProjectInTx(tx, projectMeta);                 // thay session.run ở dòng 51–61
    for (var group : nodesByLabel) {
        upsertNodeGroupInTx(tx, group);                 // thay session.run ở dòng 135–184
    }
    for (var group : edgesByType) {
        upsertEdgeGroupInTx(tx, group);                 // thay session.run ở dòng 188–231
    }
    return null;
}); // một bước thất bại → rollback toàn bộ graph của analysis này

// 2) Nhánh FAILED: dừng gọi cleanup(workspace, null) rồi bỏ qua —
//    chủ động dọn graph dở của project đã ghi trước đó:
graphRepository.deleteGraphByProjectId(projectId);   // DETACH DELETE node/edge theo projectId
```

**Tiêu chí nghiệm thu:**
- Flow analyze bình thường: graph đầy đủ như cũ; mô phỏng lỗi giữa `upsertNodes`/`upsertEdges` → Neo4j không còn graph nửa vời (transaction rollback).
- Tạo chủ động 1 project FAILED trong môi trường riêng (KHÔNG kill backend dùng chung): graph dở được dọn, không còn node orphan.
- RAM Neo4j ổn định với graph lớn (transaction to hơn trước — theo dõi khi chạy lô lớn).

### Các mục Backend Trung bình còn lại (hướng sửa theo AUDIT-REPORT)

| Mã | Hiện trạng (file:dòng) | Hướng sửa | Nghiệm thu |
|---|---|---|---|
| B-M2 | `UseCaseInferenceEngine.java` dòng 1–1398 (god class 1.398 dòng) | Tách Strategy/heuristic rules thành class nhỏ + `StringNormalizer` util; viết test bao phủ TRƯỚC khi tách | File ≤ ~400 dòng; test cũ + mới xanh; hành vi suy luận không đổi |
| B-M4 | `AdminService.java` dòng 478–499 (hardcode `List.of("ACTIVE",...)`, `List.of("FREE","PRO",...)`) | Validate bằng `Plan.valueOf(...)` hoặc đọc từ bảng `plans` | Thêm plan mới không cần sửa code; dữ liệu ngoài enum bị từ chối rõ ràng |
| B-M7 | `application.yaml` ~dòng 316 (`com.vibegraph: DEBUG` mặc định) | Mặc định INFO; DEBUG chỉ ở profile `dev` | Log mặc định mức INFO; `--spring.profiles.active=dev` mới ra DEBUG |
| B-M8 | `database/seed_dev.sql` dòng 15–22 (hash BCrypt placeholder `$2a$10$REPLACE_ME...`) | Sinh hash thật + ghi chú lệnh sinh, hoặc ghi chú dựa `AdminBootstrapRunner` (đã có) | Seed xong đăng nhập được admin theo tài liệu dev |
| B-M10 | `.env` dòng 116 (`VITE_GRAPH_SAFE_NODE_LIMIT=0`) + `VIBEGRAPH_GRAPH_NODE_LIMIT` không đặt | Đặt cap mặc định hợp lý cả backend (property) lẫn frontend (safe node limit > 0) | Chạy lại T7: request `nodeLimit=0&edgeLimit=0` bị áp cap + có cảnh báo truncation |
| B-M13 | 3 file test 0 byte được git track (gồm `VibeGraphIT.java`, `GitHubImportIT.java`); `pom.xml:346–348` failsafe include `**/*IT.java` khớp đúng 2 file IT rỗng | Xóa file 0 byte hoặc triển khai test thật | `git ls-files` hết file 0 byte loại test; failsafe không chạy file rỗng |
