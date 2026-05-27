# Module: `watcher/` — File Watcher Service

> **Vai trò:** Tự động detect file `.java` thay đổi trong project directory, debounce, trigger incremental re-analyze. Đây là tính năng quan trọng giúp VibeGraph "realtime" — developer save file ở **bất kỳ IDE nào** (IntelliJ, VS Code, Vim, Eclipse) là graph tự cập nhật trong < 3 giây.

> **Dev phụ trách:** Dev 5 (Integration).

> **Sprint:** Sprint 1 (Java WatchService basic), Sprint 2 (debounce + integrate với AnalyzeService).

> **Phụ thuộc:** `common/` (FileUtils, AsyncConfig), `graph/` (AnalyzeService).

---

## Mục tiêu module

1. Java WatchService monitor project directory **recursive** (FR-08)
2. Detect events: CREATE, MODIFY, DELETE cho file `.java`
3. Debounce 500ms — tránh trigger liên tục khi save nhiều file (Ctrl+S nhiều lần)
4. Configurable ignore patterns: `target/`, `build/`, `.git/`, `node_modules/`, `out/`
5. Hoạt động với mọi IDE (không cần plugin)
6. Trigger `AnalyzeService.analyzeFile(...)` cho file thay đổi
7. Latency từ file save → graph update < 3s (FR-07)
8. Robust: không crash khi project path không tồn tại, không leak thread khi shutdown

---

## Cấu trúc thư mục

```
watcher/
├── config/
│   └── WatcherProperties.java    # @ConfigurationProperties
└── service/
    ├── FileWatcherService.java        # Interface
    ├── DebouncedEventHandler.java     # Helper
    └── impl/
        └── FileWatcherServiceImpl.java
```

---

## Files & Specs

### `config/WatcherProperties.java`
**Mục tiêu:** Bind config từ `application.yaml` thành object dùng được.

**Phải làm:**
- `@ConfigurationProperties(prefix = "vibegraph.watcher")`
- Fields:
  - `boolean enabled` — default `true`
  - `Duration debounceMs` — default `500ms`
  - `List<String> ignorePatterns` — default `["target/**", "build/**", "out/**", ".git/**", "node_modules/**", "*.class"]`
  - `boolean recursive` — default `true`
  - `Duration pollFallbackInterval` — fallback polling khi WatchService miss event (default `5s`, NFR-03 mitigation)
  - `int maxConcurrentReanalyze` — default `4`
- Use `@Validated` để validate giá trị

**Yaml mẫu:**
```yaml
vibegraph:
  watcher:
    enabled: true
    debounce-ms: 500ms
    ignore-patterns:
      - "target/**"
      - "build/**"
      - ".git/**"
    recursive: true
```

**Đạt được khi:**
- [ ] `@EnableConfigurationProperties(WatcherProperties.class)` trong main app
- [ ] Inject được vào `FileWatcherServiceImpl`
- [ ] Override qua env var: `VIBEGRAPH_WATCHER_ENABLED=false` để tắt

**Tham chiếu:** `requirements.md` FR-08, `task-breakdown.md` 5.2

---

### `service/FileWatcherService.java` (interface)
**Mục tiêu:** Public API quản lý watcher lifecycle.

**Phải có method:**
- `void startWatching(String projectId, Path projectRoot)` — bắt đầu watch 1 project
- `void stopWatching(String projectId)` — dừng watcher cho project
- `boolean isWatching(String projectId)` — query status
- `WatcherStats getStats(String projectId)` — eventCount, lastEventAt, errors

**Đạt được khi:**
- [ ] Có thể mock trong test
- [ ] ProjectService gọi `startWatching` sau khi `createProject` thành công

---

### `service/impl/FileWatcherServiceImpl.java`
**Mục tiêu:** Implementation chính — Java WatchService + debounce + dispatch.

**Phải làm:**
- `@Service`, inject `WatcherProperties`, `AnalyzeService`, `DebouncedEventHandler`
- Field: `Map<String, WatchSession> sessions` — projectId → WatchSession (chứa WatchService + thread + key map)
- `@PostConstruct init()`: dùng `Executors.newVirtualThreadPerTaskExecutor()` cho watch loop
- `@PreDestroy shutdown()`: close tất cả WatchService, interrupt thread

**Method `startWatching(projectId, root)`:**
1. Check `properties.enabled` — nếu false, log INFO và return
2. Validate `root.toFile().isDirectory()` — throw `IllegalArgumentException` nếu sai
3. `WatchService ws = FileSystems.getDefault().newWatchService()`
4. Register **recursive** tất cả subdirectory (Java WatchService không tự recursive — phải walk tree, register từng dir)
5. Filter: skip dir match `ignorePatterns` (dùng `PathMatcher`)
6. Spawn virtual thread chạy `watchLoop(projectId, ws, keyMap)`
7. Lưu session vào `sessions` map

**Method `watchLoop(projectId, ws, keyMap)`:**
1. Loop vô tận: `WatchKey key = ws.take()` (blocking)
2. Cho mỗi event trong `key.pollEvents()`:
   - Resolve full path: `keyMap.get(key).resolve(event.context())`
   - Skip nếu không phải `.java` file
   - Skip nếu match `ignorePatterns`
   - Khi event là `ENTRY_CREATE` và path là directory → register thêm sub-watcher (recursive)
   - Tạo `FileChangeEvent{projectId, path, kind, timestamp}`
   - Dispatch tới `DebouncedEventHandler.submit(event)`
3. `key.reset()` — nếu fail → directory bị xóa, remove khỏi keyMap
4. Catch `InterruptedException` → exit gracefully
5. Catch `ClosedWatchServiceException` → exit gracefully (sau shutdown)

**Method `stopWatching(projectId)`:**
1. Lấy session từ `sessions.remove(projectId)`
2. `ws.close()` → unblock `take()`, thread tự exit
3. Cancel debounce timer cho project này

**Method polling fallback (NFR-03):**
- `@Scheduled(fixedDelayString = "${vibegraph.watcher.poll-fallback-interval:5s}")`
- Mỗi 5s: walk project tree, compare `lastModified` với cache → catch event WatchService miss

**Đạt được khi:**
- [ ] Save file `.java` trong IntelliJ → event detected < 100ms
- [ ] Save file `.java` trong VS Code → event detected
- [ ] Save 5 files cùng lúc → debounce, chỉ trigger 1 batch sau 500ms
- [ ] Tạo file mới trong subfolder → tự register watcher cho folder mới
- [ ] Xóa file → DELETE event → AnalyzeService xóa node
- [ ] Stop project → thread không leak (kiểm tra qua thread dump)
- [ ] Project path bị xóa → log ERROR, không crash app

**Tham chiếu:** `requirements.md` FR-07, FR-08; `architecture.md` §4.2; `task-breakdown.md` 5.1, 5.3

---

### `service/DebouncedEventHandler.java`
**Mục tiêu:** Gom event trong window 500ms, xử lý 1 lần.

**Phải làm:**
- `@Component`, inject `WatcherProperties`, `AnalyzeService`, virtual thread executor
- Field: `Map<String, ScheduledFuture<?>> pendingByProject` — projectId → future của batch hiện tại
- Field: `Map<String, Set<FileChangeEvent>> bufferByProject` — gom events theo project
- Sync access bằng `ConcurrentHashMap` + `synchronized` block trên project key

**Method `submit(FileChangeEvent event)`:**
1. Lock theo `event.projectId`
2. Add event vào `bufferByProject.get(projectId)` (Set để dedupe theo path)
3. Cancel `pendingByProject.get(projectId)` nếu có (reset timer)
4. Schedule new task `scheduledExecutor.schedule(() -> flush(projectId), debounceMs, MILLISECONDS)`
5. Lưu future vào `pendingByProject`

**Method `flush(projectId)`:**
1. Lock theo projectId
2. Lấy events từ buffer, clear buffer
3. Dedupe: nếu cùng file có cả CREATE + MODIFY → giữ MODIFY
4. Cùng file CREATE + DELETE → bỏ qua (tạo rồi xóa = no-op)
5. Cho mỗi event:
   - MODIFY/CREATE → `analyzeService.analyzeFile(projectId, path)` async
   - DELETE → `analyzeService.removeFile(projectId, path)`
6. Limit concurrent re-analyze = `maxConcurrentReanalyze` (semaphore)
7. Log: `"Re-analyzed {N} files for project {id} in {duration}ms"`

**Đạt được khi:**
- [ ] Save 1 file → đợi 500ms → analyze 1 lần
- [ ] Save 10 files trong 200ms → đợi 500ms từ event cuối → analyze 10 file 1 batch
- [ ] CREATE + DELETE cùng file → không trigger analyze
- [ ] Concurrent analyze giới hạn 4 → không overload Neo4j
- [ ] Latency end-to-end < 3s (NFR-01) — đo bằng integration test

**Tham chiếu:** `requirements.md` FR-07, FR-08, NFR-01; `task-breakdown.md` 5.10

---

## Definition of Done cho module watcher/

- [ ] Java WatchService recursive watch hoạt động trên Windows + Linux + macOS
- [ ] Debounce 500ms verified bằng test (gửi 10 events trong 100ms → 1 flush)
- [ ] Ignore patterns: `target/`, `build/`, `.git/` skip đúng
- [ ] Polling fallback hoạt động (test bằng cách disable WatchService, chỉnh file → vẫn detect sau 5s)
- [ ] Save file ở IntelliJ + VS Code + Vim → đều trigger được (manual test)
- [ ] Latency end-to-end (file save → WebSocket push) < 3s (FR-07)
- [ ] Coverage > 70%
- [ ] Load test: 500 files với rapid save → không crash, không memory leak (task 5.12)

---

## Lưu ý cross-module

- KHÔNG gọi Neo4jRepository trực tiếp — chỉ qua `AnalyzeService` (graph module)
- WatchService trên macOS có lag vài giây so với Linux/Windows — đó là JDK quirk, polling fallback giúp giảm tác động
- Ignore pattern dùng `PathMatcher` glob syntax (`**/target/**`), KHÔNG regex
- Khi project bị move/rename → user phải `DELETE /api/projects/{id}` rồi register lại (Phase 1 không support auto-detect rename)
- Steering module có thể subscribe `FileChangeEvent` để regenerate steering files (debounced thêm 5s nữa để tránh viết liên tục)
- Symlink: WatchService không follow symlink — Phase 1 ignore symlink luôn (document trong README)
- Số lượng watch key cao (>1000 dirs) trên Linux có thể chạm limit `inotify max_user_watches` — log WARN nếu detect được, hint user tăng limit
