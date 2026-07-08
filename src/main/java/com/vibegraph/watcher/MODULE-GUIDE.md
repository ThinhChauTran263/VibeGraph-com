# Module: watcher

## Mục đích
File watcher service tự động detect file changes trong project directory, trigger incremental re-analysis. Hoạt động với mọi IDE (IntelliJ, VS Code, Eclipse) — không cần plugin.

## Cấu trúc

```
watcher/
├── config/
│   └── WatcherProperties.java        — @ConfigurationProperties("vibegraph.watcher")
└── service/
    ├── FileWatcherService.java       — Interface: start/stop watching, register callbacks
    ├── FileChangeEvent.java          — Event record (projectId, path, type)
    ├── EventType.java                — CREATE / MODIFY / DELETE
    ├── DebouncedEventHandler.java    — Helper: debounce rapid events
    └── impl/
        └── FileWatcherServiceImpl.java — Java WatchService implementation
```

## Yêu cầu chức năng (FR-08)

### FileWatcherService Interface
```java
public interface FileWatcherService {
    void startWatching(String projectId, Path projectDir);
    void stopWatching(String projectId);
    void onFileChange(Consumer<FileChangeEvent> handler);
    boolean isWatching(String projectId);
}

public record FileChangeEvent(
    String projectId,
    Path filePath,
    EventType type,  // CREATE, MODIFY, DELETE
    Instant timestamp
) {}
```

### FileWatcherServiceImpl
- [ ] Sử dụng `java.nio.file.WatchService` (JDK built-in, no external deps)
- [ ] Watch recursively (recursive cho subdirectories)
- [ ] Detect events: ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE
- [ ] Filter chỉ `.java` files
- [ ] Bỏ qua paths trong `ignoredPaths` (default: `build/`, `target/`, `.git/`, `node_modules/`, `.idea/`)
- [ ] Run trong background thread (Java 21 virtual thread)
- [ ] Graceful shutdown khi app stop

### DebouncedEventHandler
- [ ] Debounce 500ms (config qua `WatcherProperties.debounceMs`)
- [ ] Khi nhận events liên tiếp cho cùng 1 file → chỉ trigger 1 lần sau debounce window
- [ ] Batch events: nếu nhiều files thay đổi trong window → trigger 1 lần với danh sách files
- [ ] Sử dụng `ScheduledExecutorService` để delay execution

### WatcherProperties
```java
@Data
@Configuration
@ConfigurationProperties(prefix = "vibegraph.watcher")
public class WatcherProperties {
    private boolean enabled = true;                 // default: true
    private long debounceMs = 500;                  // default: 500
    private List<String> ignoredPaths =             // default below (no .gradle)
            List.of("target", "build", ".git", ".idea", "node_modules");
    private List<String> watchedExtensions = List.of(".java");
}
```

### Integration với AnalyzeService
- [ ] On file change → call `AnalyzeService.analyzeIncremental(projectId, changedFiles)`
- [ ] Pipeline:
  1. WatchService detect file change
  2. DebouncedEventHandler buffer events
  3. After 500ms idle → trigger callback
  4. Callback calls AnalyzeService
  5. AnalyzeService re-parses changed files
  6. WebSocket pushes update to frontend

### Logging
- [ ] Log file change events (DEBUG level): `Detected MODIFY: src/main/java/com/example/UserService.java`
- [ ] Log re-analysis trigger (INFO level): `Re-analyzing 3 changed files for project foo`
- [ ] Log errors (ERROR level): `Failed to register watcher for path X`

## Edge Cases

- [ ] **OS differences**: WatchService có behavior khác nhau trên Windows/Mac/Linux
  - macOS: WatchService dùng polling internally → có thể slow
  - Windows: NTFS có file locking → handle exception
  - Linux: inotify limit → log warning nếu hit limit
- [ ] **Large directories**: > 10000 files → log warning, vẫn watch
- [ ] **Symlinks**: Default không follow symlinks (tránh infinite loop)
- [ ] **Atomic save**: IDE lưu bằng "rename temp file" → có thể trigger DELETE + CREATE
  - Treat consecutive DELETE → CREATE trong < 100ms như MODIFY
- [ ] **Multi-project**: Support watch nhiều projects cùng lúc, mỗi project 1 watcher

## Quy tắc code

1. **Single watcher per project**: Không tạo nhiều watchers cho cùng project
2. **Resource cleanup**: Stop watcher khi project bị xóa hoặc app shutdown
3. **Thread safety**: Concurrent access tới watching state phải thread-safe
4. **No blocking**: Event handler không được block (delegate sang executor)
5. **Configurable**: Tất cả timing/paths config qua application.yaml

## Configuration

```yaml
vibegraph:
  watcher:
    enabled: true
    debounce-ms: 500
    ignored-paths:
      - build
      - target
      - .git
      - node_modules
      - .idea
      - .gradle
    fallback-polling: false
    polling-interval-ms: 5000
```

## Performance Targets

| Metric | Target |
|--------|--------|
| Detect latency (file save → event) | < 100ms |
| Debounce window | 500ms |
| File change → graph update | < 3 seconds (NFR-01) |
| Memory per project | < 10MB |

## Acceptance Criteria

- [ ] Detect .java file create/modify/delete events
- [ ] Debounce hoạt động đúng (rapid saves chỉ trigger 1 lần)
- [ ] Ignored paths không bị watch
- [ ] Hoạt động trên Windows, macOS, Linux
- [ ] Support multiple projects cùng lúc
- [ ] Graceful shutdown không bị resource leak
- [ ] Integration với AnalyzeService → graph update sau file change
- [ ] Configurable qua application.yaml
- [ ] Unit tests với mock WatchService
- [ ] Integration test: tạo file → assert callback triggered
