# BÁO CÁO AUDIT TOÀN DIỆN — VibeGraph

- **Ngày audit:** 12/08/2026
- **Nhóm thực hiện:** 4 chuyên gia độc lập — Backend/Database (Alex), Frontend (Sam), Bảo mật (Tina), DevOps/Kiến trúc (Eric)
- **Phương pháp:** Rà soát read-only toàn bộ mã nguồn và cấu hình (`src/main`, `vibegraph-web`, `database/`, `Dockerfile`, `docker-compose.yml`, `.env*`, CI workflows, scripts), mọi phát hiện đều kèm bằng chứng file + dòng cụ thể.

---

## 1. Thống kê tổng quan

| Mức độ | Số lượng |
|---|---|
| 🔴 Nghiêm trọng (sửa ngay) | 2 |
| 🟠 Cao | 17 |
| 🟡 Trung bình | 31 |
| ⚪ Thấp | 26 |
| **Tổng** | **76** |

**Phân bố theo lĩnh vực:** Backend/DB: 31 · Frontend: 15 · Bảo mật: 18 · DevOps: 15 (sau khử trùng lặp 3 phát hiện trùng giữa Bảo mật và DevOps).

**Nhận định chung:** Chất lượng kiến trúc ứng dụng tốt — tầng code đã được gia cố kỹ (JWT fail-fast, refresh rotation có replay detection, ownership guard chống IDOR đồng bộ, zip-slip/path-traversal/SSRF bị chặn đúng chuẩn, rate-limit + quota đầy đủ, không có catch rỗng, không FetchType.EAGER, schema có index tốt, CI có test + coverage gate). Toàn bộ vấn đề Nghiêm trọng/Cao tập trung ở **tầng quản lý secret, cấu hình vận hành container và một số điểm nóng logic/backend**.

---

## 2. 🔴 P0 — NGHIÊM TRỌNG, CẦN SỬA NGAY

### S1. Secret production thật nằm plaintext trong `.env`
- **File:** `.env` — dòng 31–38, 41, 53–56, 76, 86
- **Tại sao:** Chứa password Supabase production thật (giá trị đã redact khỏi tài liệu này — xem `docs/ROTATION-CHECKLIST.md`), `JWT_SECRET` đầy đủ, Google/GitHub OAuth client secret (dạng `GOCSPX-...`), 8 Gemini API key. File từng xuất hiện trong backup/chat → phải coi là **đã lộ**. Ai có `JWT_SECRET` có thể giả mạo token hợp lệ của mọi user.
- **Đề xuất:** Xoay (rotate) TOÀN BỘ secret ngay; `.env` chỉ giữ giá trị dev hoặc tham chiếu secret store:
```properties
# Không hardcode giá trị thật — lấy từ secret manager / CI secret
SUPABASE_DB_PASSWORD=${SUPABASE_DB_PASSWORD:?set from secret store}
JWT_SECRET=${JWT_SECRET:?set from secret store}
```

### S2. Secret đã rò vào git object database (stash) + file backup ở root
- **File:** parent thứ 3 của `stash@{0}` (commit `388632b`, chứa file backup `.env` trong untracked-tree) — bản trong object là `.env.codex-backup-before-905919f-...140030`; bản trong working tree (khác file): `.env.codex_backup-before-9e1dfed-20260725-140618` ở root
- **Tại sao:** `git stash -u` đã lưu bản backup `.env` (159 dòng, đầy đủ JWT_SECRET/OAuth/Gemini) vào git object DB — tồn tại vĩnh viễn và truyền đi khi clone/push/bundle dù không nằm trên branch nào. Kiểm chứng bằng `git show 388632b:...`.
- **Bằng chứng forensics git (đã kiểm chứng 12/08/2026):** `stash@{0}` có 3 parent; parent thứ 3 là commit `388632b` chứa file `.env.codex-backup-before-905919f-20260725-140030` trong untracked-tree (xác nhận bằng `git ls-tree`) — chứng minh secret tồn tại trong Git object database vượt ngoài tầm .gitignore.
- **Đề xuất:** Bắt buộc rotate TOÀN BỘ secret. Dọn object khỏi git DB:
```powershell
git stash drop stash@{0}
# kiểm tra cả stash@{1..3} — repo hiện có 4 stash
git reflog expire --expire=now --all
git gc --prune=now
```
KHÔNG cần `git filter-repo` (sai công cụ: `.env` chưa từng commit lên branch nào — object chỉ sống qua stash, nên drop stash + expire reflog + gc là đủ). Xóa thêm bản sao `.env.codex_backup-*` trong working tree.

---

## 3. 🟠 P1 — MỨC CAO (17 phát hiện)

### H1. Container backend chạy root
- **File:** `Dockerfile` dòng 10–14
- **Tại sao:** Không có chỉ thị `USER`; kết hợp bề mặt tấn công rộng (endpoint đọc file source, import archive) và mount writable `./projects:/projects` → nếu có RCE sẽ chiếm full container.
- **Đề xuất:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=builder /build/target/app.jar app.jar   # tên tường minh (fix luôn M-DevOps-3)
RUN chown -R app:app /app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```
- **Đã sửa + xác nhận runtime Đợt 1 (12/08/2026):** JVM PID 1 chạy user `app` non-root qua entrypoint mới `docker/entrypoint.sh` (su-exec chown `/uploads` rồi drop quyền).

### H2. Mount toàn bộ `.env` vào container
- **File:** `docker-compose.yml` dòng 128
- **Tại sao:** Khối `environment:` (dòng 63–126) đã truyền đủ biến; mount này làm lộ cả secret thừa (OAuth/Gemini) cho mọi tiến trình trong container. `spring.config.import` là `optional:` nên bỏ mount vẫn chạy bình thường.
- **Đề xuất:**
```yaml
volumes:
  # - ./.env:/app/.env:ro   # XÓA dòng này — environment: đã đủ
  - ./projects:/projects
  - upload-workspaces:/uploads
```
- **Đã sửa + xác nhận runtime Đợt 1 (12/08/2026):** bỏ mount `.env`, backend vẫn healthy.

### H3. Credentials hạ tầng yếu + port DB expose 0.0.0.0 + APOC không giới hạn
- **File:** `docker-compose.yml` dòng 6–7, 24–30; `.env` dòng 16, 27
- **Tại sao:** Postgres/Neo4j password là `vibegraph`, port 5432/7474/7687 publish ra mọi interface, kèm `NEO4J_dbms_security_procedures_unrestricted: apoc.*` (APOC unrestricted có thể đọc file/exec). Backend gọi DB qua hostname nội bộ nên không cần publish.
- **Đề xuất:**
```yaml
services:
  postgres:
    ports: ["127.0.0.1:${POSTGRES_PORT:-5432}:5432"]
  neo4j:
    ports: ["127.0.0.1:7474:7474", "127.0.0.1:7687:7687"]
    # XÓA: NEO4J_dbms_security_procedures_unrestricted: apoc.*
```
Kèm đổi password mạnh ngẫu nhiên trong `.env`.
- **Đã sửa + xác nhận runtime Đợt 1 (12/08/2026):** 3 port bind loopback (kiểm chứng `Get-NetTCPConnection`); Neo4j healthy sau khi bỏ APOC unrestricted; host-port Postgres đổi 5432→5433 do máy dev có Postgres native (backend vẫn nối `postgres:5432` nội bộ).

### H4. Cookie phiên Secure mặc định `false` trong Docker
- **File:** `docker-compose.yml` dòng 102 + `.env` dòng 44
- **Tại sao:** JWT session cookie có thể truyền qua HTTP plaintext khi truy cập bằng địa chỉ LAN (`AuthCookieService.java:73` chỉ bật Secure khi cấu hình hoặc request secure).
- **Bằng chứng runtime (T2, 12/08/2026):** CONFIRMED — `vg_session` và `vg_refresh` đều có HttpOnly, SameSite=Lax nhưng **thiếu cờ Secure** trên HTTP.
- **Đề xuất:**
```yaml
AUTH_COOKIE_SECURE: ${AUTH_COOKIE_SECURE:-true}
```
- **Đã sửa + xác nhận runtime Đợt 1 (12/08/2026):** mặc định đã `true`; kiểm chứng T2 end-to-end chờ TLS termination (chưa có reverse proxy — chuyển Backlog).

### H5. Thiếu `.dockerignore` cho frontend
- **File:** `vibegraph-web/Dockerfile` dòng 13 (`COPY . .`) — xác nhận `vibegraph-web/.dockerignore` không tồn tại
- **Tại sao:** Build context kéo theo `node_modules/`, `dist/`, `*.log` → phình hàng trăm MB, chậm, hỏng cache layer.
- **Đề xuất:** tạo `vibegraph-web/.dockerignore`:
```
node_modules
dist
*.log
.vite
.vscode
.idea
```
- **Đã sửa + xác nhận runtime Đợt 1 (12/08/2026):** tạo `vibegraph-web/.dockerignore` + pin tag toàn bộ image (kèm D-M2): `node:22.11.0-alpine`, `nginx:1.27-alpine`, `maven:3.9.11`, `neo4j:5.26-community`; build vẫn thành công.

### H6. Project registry chỉ nằm in-memory, mất khi restart
- **File:** `src/main/java/com/vibegraph/graph/service/impl/ProjectServiceImpl.java` dòng 34
- **Tại sao:** Toàn bộ status/progress/name sống trong `ConcurrentHashMap`; recovery từ Neo4j chỉ khôi phục name/rootPath. Rủi ro mất trạng thái lớn nhất của backend. Bảng `projects` Postgres đã có sẵn đủ cột.
- **Đề xuất:**
```java
// Dùng DB làm nguồn sự thật, map chỉ là cache đọc:
ProjectResponse project = ownershipRepository.findByProjectId(id)
        .map(this::toResponse)
        .orElseThrow(() -> new ProjectNotFoundException(id));
```

### H7. Project ID 8 ký tự, không chống trùng
- **File:** `ProjectServiceImpl.java` dòng 62, 91, 101
- **Tại sao:** `UUID.randomUUID().toString().substring(0, 8)` = 32 bit không gian ID (xuất hiện ở cả 3 điểm tạo project: dòng 62, 91, 101) → xác suất trùng ~50% ở ~77k project (birthday problem); khi trùng, `projects.put(id, ...)` **ghi đè lặng lẽ** metadata project khác (nguy cơ rối dữ liệu chéo user).
- **Ghi chú:** Ngưỡng xác suất trùng ~50% cần tới ~77.000 project — cơ sở để cân nhắc mức độ nếu muốn điều chỉnh; phát hiện giữ mức Cao do hậu quả ghi đè lặng lẽ khi xảy ra.
- **Đề xuất:**
```java
private String newProjectId() {
    for (int attempt = 0; attempt < 5; attempt++) {
        String id = UUID.randomUUID().toString(); // full UUID
        if (!projects.containsKey(id)) return id;
    }
    throw new IllegalStateException("Unable to allocate unique project id");
}
```

### H8. `POST /{id}/analyze` chạy đồng bộ trên request thread
- **File:** `src/main/java/com/vibegraph/graph/controller/ProjectController.java` dòng 106–119
- **Tại sao:** Parse toàn bộ repo (JavaParser + Symbol Solver + upsert Neo4j) ngay trên thread Tomcat — tốn phút, chiếm thread pool, dính timeout reverse-proxy, không hủy được. Pattern chạy nền đã có sẵn trong `PatchAnalysisScheduler` (dòng 53–81).
- **Đề xuất:**
```java
@PostMapping("/{id}/analyze")
public ResponseEntity<ApiResponse<Void>> analyze(@PathVariable String id) {
    ownershipGuard.assertOwner(id);
    analyzeScheduler.schedule(id);              // nền, coalesce
    return ResponseEntity.accepted().build();   // 202 + WebSocket progress
}
```

### H9. N+1 query trong AdminService
- **File:** `src/main/java/com/vibegraph/auth/service/AdminService.java` dòng 518–530
- **Tại sao:** Mỗi user tốn 2 query (`settingsRepository.findById` + `sumStorageBytesByOwnerId`) khi map danh sách phân trang → trang 20 user = 40 query cộng thêm; admin dashboard gọi nhiều lần → độ trễ tăng tuyến tính.
- **Đề xuất:**
```java
@Query("""
    SELECT p.ownerId AS ownerId, SUM(p.sizeBytes) AS total
    FROM ProjectUsage p WHERE p.ownerId IN :ids GROUP BY p.ownerId""")
List<StorageSum> sumStorageByOwners(@Param("ids") Collection<UUID> ids);

// Service: 2 query batch cho cả trang
Map<UUID, UserAccountSettings> settingsById = settingsRepository.findAllById(ids)
        .stream().collect(toMap(UserAccountSettings::getUserId, identity()));
```

### H10. Vòng lặp polling import GitHub không thể hủy
- **File:** `vibegraph-web/src/composables/useGitHubImport.ts` (~dòng 124–160) + `GitHubImportForm.vue` (script dòng 1–46 không có `onBeforeUnmount`) + `ImportProjectPanel.vue` dòng 171
- **Tại sao:** Vòng lặp `for(;;)` chỉ dừng ở trạng thái terminal/stall timeout/trần 1 giờ (`IMPORT_ABSOLUTE_TIMEOUT_MS = 3.600.000ms`). Form render bằng `v-else` → chuyển tab là unmount nhưng polling + WebSocket vẫn chạy mồ côi, tiếp tục ghi vào refs đã unmount.
- **Đề xuất:**
```ts
let cancelled = false
function cancel(): void { cancelled = true }
onScopeDispose(cancel)   // tự dừng khi component unmount
async function waitForGithubAnalysis(project, onProgress) {
  for (;;) {
    if (cancelled) return null
    await delay(POLL_INTERVAL_MS)
    // ...
  }
}
```
- **Kiểm chứng runtime 12/08/2026 (T3): BLOCKED** — backend timeout kết nối GitHub (`GITHUB_IMPORT_ERROR: Failed to contact GitHub: request timed out`), không tạo được import trạng thái ANALYZING nên không quan sát được polling sau unmount. Phát hiện giữ trạng thái phân tích tĩnh.

### H11. UsersTableView — 5 điểm gọi API không try/catch
- **File:** `vibegraph-web/src/views/admin/UsersTableView.vue` dòng 70–72, 74–79, 104–110, 316–321, 340–345
- **Tại sao:** Trang quản trị quan trọng (danh sách user, phân trang, lọc, BAN/unban) không xử lý lỗi → backend 500 hoặc lỗi mạng gây unhandled rejection âm thầm, bảng trống, người dùng không nhận phản hồi.
- **Đề xuất:**
```ts
const loadError = ref('')
async function loadUsers(page = currentPage.value) {
  loading.value = true; loadError.value = ''
  try { await adminStore.fetchUsers({ search: ..., status: ..., plan: ..., page }) }
  catch (e) { loadError.value = e instanceof ApiError ? e.message : t('admin.users.loadFailed') }
  finally { loading.value = false }
}
onMounted(() => { void loadUsers(0); void loadPlansSafe() })
// template: @click="loadUsers(currentPage - 1)" thay vì gọi async inline
```
- **Bằng chứng runtime (T4, 12/08/2026):** CONFIRMED — bật Network Offline rồi bấm Search: UI giữ bảng cũ, không thông báo lỗi; Console ghi `ERR_INTERNET_DISCONNECTED`, Vue warning tại `UsersTableView` và `Uncaught (in promise)`.

### H12. Router import tĩnh kéo stack đồ họa vào main bundle
- **File:** `vibegraph-web/src/router/index.ts` dòng 2–6
- **Tại sao:** `GraphView` (kéo Sigma/Graphology/ForceAtlas2 — phần nặng nhất app) cùng HomeView/LoginView/RegisterView/LandingView import tĩnh → mọi người dùng phải tải full stack đồ họa ngay cả khi chỉ xem landing. Các route admin đã lazy đúng chuẩn.
- **Đề xuất:**
```ts
const GraphView = () => import('@/views/GraphView.vue')
const LoginView = () => import('@/views/LoginView.vue')
const RegisterView = () => import('@/views/RegisterView.vue')
const HomeView = () => import('@/views/HomeView.vue')
const LandingView = () => import('@/views/LandingView.vue')
```
- **Bằng chứng runtime (T1, 12/08/2026):** CONFIRMED — landing tải 117 module/script ≈ 4,17 MB transfer, gồm `sigma.js` (161KB), `graphology.js` (154KB), `GraphCanvas.vue`, 2 worker ForceAtlas2/NoOverlap và `GraphView.vue`; `/login` vẫn kéo cùng graph stack.

### H13. Rate-limit chạy SAU bước băm BCrypt (bề mặt DoS thực tế)
- **File:** `src/main/java/com/vibegraph/auth/config/SecurityConfig.java` dòng 179–184 + `src/main/java/com/vibegraph/auth/web/ApiKeyAuthFilter.java` dòng 86–94 + `.env` dòng 104
- **Tại sao:** `SecurityConfig` đặt `rateLimitFilter` bằng `addFilterBefore(..., AuthorizationFilter.class)` (dòng 184) tức chạy SAU `jwtAuthFilter` (dòng 182) và `apiKeyAuthFilter` (dòng 183). `ApiKeyAuthFilter.findMatch` chạy tối đa 5 lần `passwordEncoder.matches()` (BCrypt ~100ms/lần, `MAX_PREFIX_CANDIDATES = 5`) TRƯỚC khi rate-limit kịp chặn. Kết hợp `VIBEGRAPH_TRUST_PROXY=true` (`.env:104`) → attacker gửi API key sai kèm IP giả mạo qua `X-Forwarded-For` để bào CPU server mà không bao giờ chạm hạn mức rate-limit.
- **Đề xuất:**
```java
// SecurityConfig — đưa rate-limit lên trước các filter xác thực:
.addFilterBefore(rateLimitFilter(clientAddressResolver(), meterRegistry),
        JwtAuthFilter.class)
```
- **Đã tái hiện runtime (đo lại V2, 12/08/2026): CONFIRMED** — median 30 request/nhóm: không key 4,25ms / prefix ngẫu nhiên 4,64ms / **trùng prefix 54,84ms** → mỗi request mang key sai trùng prefix tốn ~50,20ms BCrypt trước khi nhận 401, trong khi rate-limit chưa kịp chặn (V2.3 xác nhận API-key filter chạy TRƯỚC rate-limit filter; 90/90 request nhận 401, 0 nhận 429). Key test `runtime-h13-20260812` đã xóa sau đo, PostgreSQL xác nhận `deleted_at IS NOT NULL`. Bằng chứng: `runtime-evidence/VERIFICATION-BM11-H13-REPORT.md`, `V2-timing.txt`, `V2-filter-order.txt`.
- **Lịch sử test T5 (12/08/2026, KHÔNG tái hiện — đã rõ nguyên nhân):** endpoint `GET /api/projects`, fake key trung bình 3,0ms vs no-key 3,2ms, 401×22 + 429×8. Key giả khi đó có prefix không khớp bản ghi nào nên `findTop6ByKeyPrefix...` trả danh sách rỗng, `passwordEncoder.matches()` không được gọi (`ApiKeyAuthFilter.java:80–94`); đo lại V2 dùng key trùng prefix key thật đã tái hiện đúng chi phí bcrypt.
- **Phụ thuộc bắt buộc — sửa S-M2 trước:** với API key sai, principal là anonymous → `RateLimitFilter.java:88` chỉ consume bucket IP (`"ip:"+ip`), mà IP lấy từ `ClientAddressResolver` (S-M2: trust proxy true + `.findFirst()` token trái nhất) → xoay `X-Forwarded-For` mỗi request tạo bucket rate-limit mới mỗi lần, bypass hạn mức; H13 chỉ hiệu lực sau khi S-M2 đã sửa.
- **Đã sửa + xác nhận runtime Đợt 1 (12/08/2026):** rate-limit chặn trước bước xác thực — burst 150 request key sai trùng prefix: 120×401 + 30×429, 429 đầu tiên đúng request #121 (ngưỡng 120/phút/IP); trước sửa 90/90 trả 401, 0×429. Biến thể XFF xoay vòng: 429 ngay request #1 của phút kế tiếp (S-M2 đã khóa bypass). Anchor đổi sang `UsernamePasswordAuthenticationFilter.class` — xem lệch có lý do tại mục §10.

### H14. `readRange` nạp cả file vào RAM trước khi kiểm tra trần → OOM
- **File:** `src/main/java/com/vibegraph/mcp/source/impl/SourceFileServiceImpl.java` dòng 110, 122–136, 196
- **Tại sao:** `readRange` gọi `readAllLines(candidate)` (dòng 110) nạp toàn bộ file vào RAM trước; trần `MAX_LINES`/`MAX_BYTES` chỉ áp sau đó (dòng 122–136). Nhánh search có chốt `Files.size()` trước khi đọc (dòng 196) nhưng `readRange` thì không. File vài GB (log lớn, dữ liệu text) sẽ OOM JVM qua endpoint đọc source.
- **Đề xuất:**
```java
// Chốt kích thước trước khi đọc, cùng pattern với scanFile():
if (Files.size(candidate) > MAX_FILE_BYTES_TO_SCAN) {
    return notServed(relativePath, "File too large for source reading.");
}
List<String> lines = readAllLines(candidate);
```
- **Bằng chứng runtime (T6, 12/08/2026):** CONFIRMED — đọc file text tạm 200MiB (3.883.615 dòng): response chỉ trả 300 dòng/16.769 ký tự trong 915ms nhưng memory container tăng 680,6 → 892,2MiB (**+211,6MiB heap** ≈ **1,06× kích thước file mỗi request**). Backend KHÔNG có `mem_limit` (chỉ Neo4j có) và endpoint đọc source KHÔNG giới hạn concurrency → 3 request đồng thời trên file 200MB ≈ +600MB → nguy cơ OOM-kill container; tác động tính bằng số đo, không còn là khả năng.
- **Kiểm chứng mở:** đo heap 60s sau request (`docker stats`) để phân biệt spike (GC tụt về ~mức nền) vs leak (không tụt) — nếu leak, nâng mức độ phát hiện.
- **Đã sửa + xác nhận runtime Đợt 1 (12/08/2026):** chốt `Files.size()` + try/catch `IOException` — file vượt trần trả `found=false` + warning "File too large for source reading." trong 75ms; heap 581,7→581,7 MiB, 3 request đồng thời +0,2 MiB, 60s sau 581,9 MiB — không spike, không leak (trước sửa +211,6 MiB/request); test mới `refusesOversizedFile`.

### H15. Redact private key chỉ che dòng header — thân base64 trả nguyên văn
- **File:** `src/main/java/com/vibegraph/mcp/source/impl/SourceFileServiceImpl.java` dòng 68, 305–320
- **Tại sao:** Regex `PRIVATE_KEY_HEADER` (dòng 68) chỉ khớp dòng `-----BEGIN ... PRIVATE KEY-----`; `redact()` (dòng 305–320) chạy per-line → dòng header bị thay bằng `[REDACTED]` nhưng toàn bộ thân base64 của private key vẫn được trả nguyên văn qua endpoint đọc source (file `.pem`/`.key` đổi đuôi thành extension được phép như `.txt` là lọt).
- **Đề xuất:**
```java
// Redact theo block: từ header tới dòng -----END tương ứng
private static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
        "-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----");
// áp trên nội dung toàn file thay vì từng dòng
```
- **Đã sửa + xác nhận runtime Đợt 1 (12/08/2026):** redact theo block đầy đủ (BEGIN→END), phủ cả 2 trường hợp block bị cắt giữa range đọc; 3 test mới: `redactsPrivateKeyBlock`, `redactsMultiplePrivateKeyBlocks`, `redactsPrivateKeyBodyWhenRangeCutsTheBlock`.

### H16. 8 lỗ hổng npm dependencies (1 critical)
- **File:** `vibegraph-web/package-lock.json` — kết quả `npm audit` thực tế ngày 12/08/2026
- **Tại sao:** 8 advisory: `websocket-driver` (critical), 6 high + 1 moderate trải trên `axios`, `brace-expansion`, `nanoid`, `postcss`, `shell-quote`, `undici`, `jsdom`. Chuỗi supply-chain/frontend có thể bị khai thác qua dependency bị tổn thương.
- **Đề xuất:**
```bash
npm audit fix
# ưu tiên vá/nâng cấp trước: websocket-driver, undici, axios
```

### H17. Scheduler đơn luồng — job purge trash có thể chặn flush telemetry, drop security event
- **File:** 7 job `@Scheduled` chạy chung 1 thread: `RequestEventService.java:146`, `OnlineUserHistoryService.java:21`, `FeedbackReportService.java:171`, `AuditService.java:140` (dạng FQN annotation), `SupabaseRetentionService.java:29`, `RefreshSessionService.java:193`, `ProjectTrashService.java:108` — không có cấu hình `spring.task.scheduling.*`, không bean `TaskScheduler` tùy chỉnh
- **Tại sao:** Chuỗi nguyên nhân đầy đủ: job purge trash (`ProjectTrashService.java:113` query không phân trang → `ProjectDeletionOrchestrator.java:183–190` `Files.walk` xóa đệ quy từng project) có thể chiếm thread scheduler duy nhất đủ lâu để chặn `flush()` telemetry → queue 10.000 (`application.yaml:125`, `RequestEventService.java:85`) đầy → shed-oldest drop cả security event (chính là hệ quả đã ghi ở B-L8 — xem chuỗi đầy đủ tại mục này).
- **Đề xuất:**
```yaml
spring:
  task:
    scheduling:
      pool:
        size: 4   # tách flush telemetry khỏi job dài
```
Kèm purge trash theo batch + `Pageable` (xem B-M14) và ưu tiên không để job dài chiếm thread của job thời gian thực. Kèm alert rule khi ship (xem REMEDIATION-PLAN — tác hại của chuỗi này là im lặng, không log không lỗi).
- **Đã sửa + xác nhận runtime Đợt 1 (12/08/2026):** pool size 4 hiệu lực + tạo mới `ops/prometheus/vibegraph-alerts.yml` (2 rule) vì repo chưa có stack Prometheus.

---

## 4. 🟡 P2 — MỨC TRUNG BÌNH (31 phát hiện)

### Backend (14)

| # | File : Dòng | Vấn đề | Đề xuất sửa |
|---|---|---|---|
| B-M1 | `Neo4jGraphRepository.java` : 119–132 | `instantOrNull` nuốt exception 2 tầng (`catch (RuntimeException ignored)` / `ignoredAgain`) — lỗi dữ liệu Neo4j bị ẩn hoàn toàn | `log.warn("Failed to parse instant for node {}", id, ex);` trước khi return null |
| B-M2 | `UseCaseInferenceEngine.java` : 1–1398 | God class 1.398 dòng vi phạm SRP: trộn suy luận heuristic, chuẩn hóa chuỗi (singularize/pluralize/splitCamel), lọc grounding | Tách Strategy/Heuristic rules thành class nhỏ + `StringNormalizer` util |
| B-M3 | `MethodVisitor.java` : 68 | `Boolean.getBoolean("vibegraph.parser.emit-unresolved-call-stubs")` đọc **system property JVM**, không phải Spring config → cấu hình trong application.yaml bị vô hiệu lặng lẽ | Inject qua `@ConfigurationProperties` hoặc constructor param |
| B-M4 | `AdminService.java` : 478–499 | Hardcode `List.of("ACTIVE","BLOCKED","DEACTIVATED")` và `List.of("FREE","PRO","PRO_PLUS","MAX","ENTERPRISE")` — trùng lặp enum miền, dễ lệch khi thêm plan | Validate bằng `Plan.valueOf(...)` hoặc đọc từ bảng `plans` |
| B-M5 | `FileChangeBroadcaster.java` : 103–113 | Mỗi lần đổi 1 file: 2 lần `getFullGraph` (before/after) để tính diff → O(kích thước graph) mỗi lần save; IDE autosave nhân chi phí | Diff theo file (`findNodesByFile`) thay vì full-graph snapshot |
| B-M6 | `LlmUseCaseRefiner.java` : 71 | `responseCache` là `ConcurrentHashMap` không giới hạn, không TTL → heap tăng không chặn | Dùng Caffeine (đã có trong pom): `maximumSize + expireAfterWrite` |
| B-M7 | `application.yaml` : ~316 | `com.vibegraph: DEBUG` ở profile mặc định → log ồn, chậm, nguy cơ lộ thông tin nội bộ | Mặc định INFO; DEBUG chỉ ở profile `dev` |
| B-M8 | `database/seed_dev.sql` : 15–22 | Hash BCrypt placeholder `'$2a$10$REPLACE_ME...'` cho admin → seed xong không đăng nhập được, bẫy cho dev mới | Sinh hash thật + ghi chú lệnh sinh, hoặc dựa vào `AdminBootstrapRunner` (đã có) |
| B-M9 | `IpBlockService.java` : 32–35 | `findActive` query DB mỗi request, không cache (`@Transactional(readOnly)` không `@Cacheable`), lại được gọi sớm cho mọi request qua `ipBlockFilter` → mỗi request tốn 1 round-trip DB | Thêm `@Cacheable` TTL ngắn (vd 30–60s) keyed theo IP |
| B-M10 | `.env` : 116 (`VITE_GRAPH_SAFE_NODE_LIMIT=0`) + `VIBEGRAPH_GRAPH_NODE_LIMIT` không đặt | Không giới hạn node graph nào hiệu lực → `getFullGraph` có thể trả graph khổng lồ, frontend render không chốt an toàn. **Bằng chứng runtime (T7):** CONFIRMED — request `nodeLimit=0&edgeLimit=0` render ~2.405 node, không cảnh báo cap/truncation | Đặt cap mặc định hợp lý cả backend (property) lẫn frontend (safe node limit > 0) |
| B-M11 | Neo4j upsert batch (autocommit `session.run` theo lô) | Upsert không nguyên tử: project FAILED giữ lại graph dở dang trong Neo4j. **Đã tự kiểm chứng tĩnh (V1.1, 12/08/2026):** upsert Neo4j = nhiều `session.run` autocommit (1 câu project + 1 câu/node label + 1 câu/relationship type), không transaction bao graph; nhánh FAILED giữ graph dở — runtime chưa tái hiện (V1.2 BLOCKED: không có project FAILED, không kill backend dùng chung) | Bọc lô trong transaction tường minh hoặc đánh dấu + dọn graph của project FAILED |
| B-M12 | `GlobalExceptionHandler.java` : 236–246 | Map `IllegalStateException` → HTTP 409 trả raw `ex.getMessage()` cho client, nhánh này không log (catch-all có log tại :316–323) → lộ thông điệp nội bộ + che mất lỗi 500 thật | `log.warn` ở nhánh này + trả message chung an toàn. **Đã sửa + xác nhận runtime Đợt 1 (12/08/2026):** message chung tiếng Anh theo convention, giữ code `PRECONDITION_FAILED` (grep xác nhận frontend không parse message) |
| B-M13 | 3 file test 0 byte đang được git track (gồm 2 IT rỗng `VibeGraphIT.java`, `GitHubImportIT.java`) | Số liệu test thật: 174 file test Java, 155 chứa `@Test`/`@ArchTest`, 9 `@Disabled`; `pom.xml:346–348` failsafe include `**/*IT.java` khớp đúng 2 file IT rỗng → tạo ảo giác có coverage tích hợp | Xóa file 0 byte hoặc triển khai test thật |
| B-M14 | `ProjectTrashService.java` : 113 | `findByDeletedAtLessThan(cutoff)` không phân trang (độc lập với chuỗi H17) — trash lớn kéo toàn bộ danh sách vào 1 transaction | Query theo batch + `Pageable`. **Đã sửa + xác nhận runtime Đợt 1 (12/08/2026):** repository phân trang + guard "batch 0 tiến độ → dừng" (chống vòng lặp vô hạn khi purge thất bại lặp) + bỏ `@Transactional` bao toàn sweep; 3 test sweep cập nhật |

### Frontend (7)

| # | File : Dòng | Vấn đề | Đề xuất sửa |
|---|---|---|---|
| F-M1 | `HeaderBar.vue`, `MainLayout.vue`, `SidePanel.vue`, `StatusBar.vue`, `GraphControls.vue`, `CodeInspector.vue`, `AddProjectLocal.vue`, `DirectoryBrowserModal.vue`, `useLocalImport.ts` | **9 file dead code (~1.319 dòng)** — grep toàn repo xác nhận 0 import (DirectoryBrowserModal + useLocalImport chỉ được dùng bởi AddProjectLocal — bản thân nó cũng dead) | Xóa các file + test tương ứng |
| F-M2 | `src/lib/runtimeConfig.ts` : 100–104 | `PROJECTS_AUTO_REFRESH_INTERVAL_MS` khai báo nhưng không dùng ở bất kỳ đâu | Xóa hoặc triển khai tính năng |
| F-M3 | `src/lib/http.ts` (59 dòng) + `package.json` (axios ^1.16.1) + `src/lib/api.ts` : 7, 622 | 2 HTTP client song song: axios chỉ dùng cho đúng 1 endpoint `authApi.me()`; ~40 endpoint còn lại dùng fetch wrapper cùng logic refresh 401 → dependency thừa ~14KB gzip | Chuyển `me()` sang fetch wrapper; gỡ axios + `lib/http.ts` |
| F-M4 | `src/language/index.ts` : 2–3 | Cả 2 locale JSON import eager (≈140KB vào main bundle) | Chỉ eager locale mặc định; lazy-load locale còn lại bằng dynamic import trong `setLocale()` |
| F-M5 | `vite.config.ts` | Không có `build.rollupOptions.output.manualChunks` → vendor lớn (sigma, echarts, graphology) gộp chung, cache kém | Thêm manualChunks tách vendor theo nhóm |
| F-M6 | `UserDetailDrawer.vue` (3.202 dòng), `LandingView.vue` (2.959), `DashboardView.vue` (1.525), `GraphCanvas.vue` (1.469), `UsersTableView.vue` (1.115), `SecurityView.vue` (1.047), `useSigma.ts` (1.038), `api.ts` (990), `DiagramPanel.vue` (869), `stores/admin.ts` (783) | 10 file vượt xa ngưỡng 400 dòng → khó test/bảo trì | Tách: UserDetailDrawer → sub-panel (quota, API keys, sessions); LandingView → section components; api.ts tách theo domain |
| F-M7 | `GitHubImportForm.vue` : 137–138 | Text cứng tiếng Anh không qua i18n trong khi toàn app dùng vue-i18n | `t('user.import.success', {...})` |

### Bảo mật (5)

| # | File : Dòng | Vấn đề | Đề xuất sửa |
|---|---|---|---|
| S-M1 | `SupabaseDatabaseConfig.java` : 181 | DDL ghép chuỗi: `"CREATE SCHEMA IF NOT EXISTS " + schema` từ property môi trường | Validate `schema` theo regex `^[a-z0-9_]+$` trước khi dùng |
| S-M2 | `.env` : 104–105 + `ClientAddressResolver.java` : 28–35 | `VIBEGRAPH_TRUST_PROXY=true` + trusted `172.18.0.0/16,127.0.0.1` → mọi tiến trình trong docker bridge/localhost giả mạo được `X-Forwarded-For` bypass rate-limit. Cơ chế: `resolve()` lấy token **TRÁI NHẤT** của header (`.findFirst()` dòng 34) → mọi client trong trusted range giả mạo được IP gốc. **Đã sửa + xác nhận runtime Đợt 1 (12/08/2026):** resolver lấy token **phải nhất ngoài trusted range** (+27/−9; `ClientAddressResolverTest` 8 test xanh, gồm test mới `resolve_trustedProxyConfiguration_ignoresSpoofedLeftmostAddress`); `.env` đặt `VIBEGRAPH_TRUST_PROXY=false` (compose publish 8080 trực tiếp, không proxy thật) | Chỉ bật khi đứng sau reverse proxy thật; thu hẹp trusted proxies; đổi resolver lấy token **phải nhất ngoài trusted range** (right-most untrusted) |
| S-M3 | `application-prod.yaml` : 107–118 + `SecurityConfig.java` : 159–164 | Actuator `info, metrics, prometheus` expose; `anyRequest().authenticated()` → USER thường cũng đọc được metrics | `hasRole('ADMIN')` cho `/actuator/**` trừ health |
| S-M4 | `Neo4jGraphRepository.java` : 342–348 | Fulltext Lucene nhận input thô — ký tự đặc biệt (`"`, `&&`, `/regex/`) gây `QueryParseException` → 500 (hiện dead path nhưng thành DoS nhỏ khi nối lại) | Escape Lucene query + try-catch map về 400 |
| S-M5 | `application.yaml` : 32–35 | Trần multipart **2048MB** — quota business vẫn chặn trước khi ghi (`ArchiveImportServiceImpl.java:177` `assertQuotaNotExceeded` chạy TRƯỚC `Files.copy` :188); rủi ro thật là trần host-level cho phép request tới 2 GiB đi qua tầng web trước khi chạm quota business, không phải "buffer 2GB chắc chắn xảy ra" | Hạ trần host-level sát quota gói lớn nhất (vd 512MB) |

### DevOps (5)

| # | File : Dòng | Vấn đề | Đề xuất sửa |
|---|---|---|---|
| D-M1 | `scripts/dev-up.ps1` : 32–44 | Script lỗi thời: chỉ khởi động Neo4j, không khởi động Postgres — backend hiện bắt buộc Postgres (Flyway + `ddl-auto: validate`) → dev chạy script này backend sẽ fail | Thêm `docker compose up -d postgres` + chờ `pg_isready` |
| D-M2 | `Dockerfile` : 3, 10; `vibegraph-web/Dockerfile` : 3, 16; `docker-compose.yml` : 3, 21 | Image tag không pin minor/patch (`maven:3.9-eclipse-temurin-21`, `node:22-alpine`, `nginx:alpine`, `neo4j:5-community`) → build không tái lập được | Pin tag cụ thể hoặc digest |
| D-M3 | `Dockerfile` : 12 | `COPY --from=builder /build/target/*.jar app.jar` dùng wildcard — fail build nếu xuất hiện jar thứ hai | Copy tên jar tường minh + `<finalName>app</finalName>` trong pom |
| D-M4 | `.github/workflows/backend.yml` : 39, 42, 56; `frontend.yml` : 33, 36 | Actions chỉ pin major version (`actions/checkout@v4`) — rủi ro supply-chain; chưa có job build/push Docker image (thiếu CD) | Pin SHA + bổ sung workflow CD |
| D-M5 | `task/` vs `task-final/` | Hai thư mục cùng track 8 tên file nhưng **không phải bản sao hoàn toàn** → không thể xóa máy móc một thư mục | Xác định ownership của `task-final` (output hay nhánh tài liệu độc lập) rồi merge từng file bằng diff; KHÔNG `git rm` cả thư mục chỉ vì tên giống nhau |

---

## 5. ⚪ P3 — MỨC THẤP (26 phát hiện)

### Backend (11)

| # | File : Dòng | Vấn đề | Đề xuất |
|---|---|---|---|
| B-L1 | `ParserServiceImpl.java` : ~380–505 | `createParser` và `createProjectParser` gần như trùng lặp | Gom về 1 factory method dùng chung |
| B-L2 | `CachingGraphRepository.java` : 76–92 | `pruneIfOverflowing` quét tuyến tính tìm entry cũ nhất | `LinkedHashMap` access-order hoặc Caffeine |
| B-L3 | `JwtService.java` : 57–59 | Overload `issue(User)` 1 tham số không còn được gọi | Xóa hoặc đánh dấu mục đích test |
| B-L4 | `AnnotationVisitor.java` : 18 | `@Deprecated`, không còn tham chiếu | Xóa hoặc ghi chú kế hoạch giữ |
| B-L5 | `CorsConfig.java` : 37–44 + `SecurityConfig.java` : 198–208 | CORS đăng ký 2 nơi (MVC + Security) → dễ lệch cấu hình | Giữ 1 nguồn duy nhất (Security `CorsConfigurationSource`) |
| B-L6 | `database/ERD.md` : 64–67 | Tài liệu nói `refresh_tokens`/`audit_log` "có thể thêm sau" nhưng đã tồn tại (V18, V10) | Cập nhật ERD theo schema thực tế |
| B-L7 | `ProjectServiceImpl.java` : 36–58 | Field injection `@Autowired(required=false)` nhiều chỗ | Constructor injection với `ObjectProvider<T>` |
| B-L8 | `RequestEventService.java` : 350–366 (counter dòng 69, 314–317) | Telemetry queue đầy dùng shed-oldest (`freshQueue.poll()` khi offer thất bại) → drop cả security event, chỉ có counter `security_events.dropped.total` ghi nhận (xem chuỗi nguyên nhân đầy đủ tại H17) | Thiết kế best-effort có chủ đích; cân nhắc ưu tiên giữ security event khi queue đầy (vd queue riêng hoặc evict non-security trước); kèm alert rule khi ship (xem REMEDIATION-PLAN) |
| B-L9 | 6 DTO chết (0 tham chiếu, tổng 98 dòng) | Class DTO không còn được bất kỳ đâu tham chiếu | Xóa |
| B-L10 | `UserNotification.java` (auth/domain) + `V10__phase7_support_audit_notifications.sql` : 11, 18, 21 + `JdbcNotificationRepository.java` (common/supabase/repository, 168 dòng, 5 tham chiếu `user_notifications`) | Chỉ entity JPA `UserNotification` là chết; bảng `user_notifications` vẫn ĐANG ĐƯỢC DÙNG qua đường JDBC bởi `JdbcNotificationRepository` | Xóa entity JPA chết `UserNotification.java`; GIỮ bảng và migration V10 (bảng đang được `JdbcNotificationRepository` dùng qua JDBC) |
| B-L11 | `src/test/java/com/vibegraph/graph/service/TarballImportServiceTest.java` | 8/8 `@Test` đều `@Disabled` ("Chờ TarballImportServiceImpl...") — test chết từ khi chưa có implementation. Nay implementation đã tồn tại (`TarballImportServiceImpl.java` 301 dòng) và được phủ bởi test suite thật `TarballImportServiceImplTest.java` (218 dòng, 5 `@Test`, 0 `@Disabled`) | XÓA `TarballImportServiceTest` — không "bật lại": bật 8 test chết chỉ tạo coverage trùng lặp với suite thật đang chạy |

### Frontend (4)

| # | File : Dòng | Vấn đề | Đề xuất |
|---|---|---|---|
| F-L1 | `LandingView.vue` : 349–362 | Chuỗi `setTimeout(typeCmd, 20)` đệ quy không hủy khi unmount | Lưu timer handle + clear trong `onBeforeUnmount` |
| F-L2 | `LandingView.vue` : 490–493 | 4 listener `window.addEventListener(...)` đăng ký trong onMounted không được remove | Lưu reference và remove trong `onBeforeUnmount` |
| F-L3 | `SearchBar.vue` (results computed) | Mỗi keystroke quét O(n) toàn bộ node | Debounce input hoặc giới hạn tập quét khi graph lớn |
| F-L4 | `ImportProjectPanel.vue` : 95–100 | SVG path hardcode inline trong `iconPath()` | Đưa vào AppIcon/icon registry |

### Bảo mật (5)

| # | File : Dòng | Vấn đề | Đề xuất |
|---|---|---|---|
| S-L1 | `CodeViewerModal.vue` : 242, `DiagramPanel.vue` : 500, 583 | Dùng `v-html` (highlight.js HTML, SVG server-render) — hiện nội dung do backend escape nên rủi ro tồn dư thấp | Giữ nguyên tắc không đưa raw user input vào `v-html`; cân nhắc DOMPurify cho SVG |
| S-L2 | `CookieCsrfFilter.java` : 25–27, 66–68 | CSRF bảo vệ bằng custom header thay vì token (hợp lệ vì CORS không wildcard + SameSite=Lax, nhưng yếu hơn chuẩn) | Nếu thêm tính năng nhạy cảm → dùng CSRF token |
| S-L3 | `RateLimitFilter.java` : 32–44 | Rate limit per-instance, không cluster-wide (N replica = N lần hạn mức) | Redis/shared store khi scale ngang |
| S-L4 | `JwtAuthFilter.java` : 41, 59–63 | `ACTIVE_USERS` static map chỉ dọn khi có người đọc → phình nhẹ theo thời gian | Dọn định kỳ hoặc Caffeine TTL |
| S-L5 | `GitHubUrlParser.java` : 15 + `GitHubTarballClient.java` : 33, `GitHubPreFlightService.java` : 33 | Regex SEGMENT `[A-Za-z0-9_.-]+` cho phép `.`/`..` làm owner/repo, kết hợp `HttpClient.Redirect.NORMAL` tự theo redirect | Defense-in-depth: chặn segment `.`/`..` trong parser; hiện đã có regex khóa `github.com` phía request nên rủi ro thấp |

### DevOps (6)

| # | File : Dòng | Vấn đề | Đề xuất |
|---|---|---|---|
| D-L1 | `docker-compose.yml` : 4, 22, 51, 151 | `container_name` cố định → không scale được nhiều replica | Bỏ `container_name` nếu cần scale |
| D-L2 | `database/docker-compose.postgres.yml` : 26–28 | Credential mặc định `vibegraph/vibegraph` (fallback) — chấp nhận cho dev local | Giữ nguyên + comment cảnh báo chỉ dùng local |
| D-L3 | root workspace | ~**105 MB rác** untracked (28 file rác, lớn nhất `backend_run.log` 71M): `backend.out.log` 14 MB, `graph_check.json` 2.9 MB, `replay_pid*.log`, `scratch.diff`, `bash.exe.stackdump`... Không bị git track nhưng tốn disk | Xóa ĐÍCH DANH nhóm log/dump ở root đã liệt kê. **KHÔNG dùng `git clean -fdX`** — lệnh này xóa ~985MB gồm cả `target/`, `node_modules/`, `.gitnexus/`, `.vibegraph/` (đều trong .gitignore) chứ không chỉ 105MB rác root; chỉ dùng khi chấp nhận mất cache/build |
| D-L4 | `.gitignore` : 5–6 | `quick-start-win.ps1`, `quick-start-mac.sh` bị ignore dù là script setup ở root → teammate clone repo thiếu file | Bỏ 2 dòng này khỏi `.gitignore` |
| D-L5 | `logs/` vs `.vibegraph/` | Hai thư mục log runtime chồng chéo | Chuẩn hóa về 1 convention trong dev script |
| D-L6 | `vibegraph-web/Dockerfile` : 5–10 | `VITE_API_URL`/`VITE_WS_URL` bake vào image lúc build → đổi môi trường phải rebuild | Tài liệu hóa hoặc runtime env injection |

---

## 6. ✅ Những điểm đã kiểm chứng là SẠCH / TỐT

**Bảo mật ứng dụng (tầng code):**
- Không có SQL/Cypher injection — mọi `@Query` dùng `:param`; phần động trong Cypher chỉ từ enum nội bộ + depth đã validate (`Neo4jGraphRepository.java:216–224, 447–460`).
- Không có zip-slip — chặn `../`, absolute path, Windows drive, symlink/hardlink, bomb guard (`ArchiveExtractor.java:125–128, 161–182, 193–205`).
- Không có path traversal — `toRealPath` + `startsWith` (`LocalImportServiceImpl.java:209–211`), chặn symlink escape (`LocalPatchServiceImpl.java:354–381`), allow-list extension (`SourceFileServiceImpl.java:49–58` — phần redact secret cùng file chỉ che dòng header private key, xem H15).
- Không có SSRF import GitHub — URL khóa regex `^https://github\.com/...` (`GithubImportRequest.java:20–23`).
- Không có IDOR — mọi endpoint project-scoped gọi `ownershipGuard.assertOwner`; account endpoints đều "ForCurrentUser".
- Không leo quyền khi register — hardcode `Role.USER` (`AuthService.java:98, 228`).
- Không log lộ secret — key in dạng masked (`GeminiChatClientConfig.java:75–79`).
- JWT HS512 fail-fast khi secret < 64 bytes; refresh rotation có replay detection + grace window; HttpOnly cookie; CORS không wildcard (đã xác nhận bằng runtime T8: Origin lạ `https://evil.example` không nhận `Access-Control-Allow-Origin`); CSP chặt phía nginx.
- Dependencies backend mới, không CVE nổi bật (Spring Boot 4.0.6, commons-compress 1.26.0, JJWT 0.12.6). Riêng frontend: `npm audit` phát hiện 8 lỗ hổng (1 critical `websocket-driver`, 6 high, 1 moderate) — xem H16.

**Backend chất lượng:**
- 0 catch rỗng, 0 `printStackTrace()`/`System.out`, 0 `FetchType.EAGER`, không dùng `@Async` sai cách.
- Batch Cypher dùng UNWIND; schema Postgres có index tốt (`lower(email)`, `owner_id`, composite trên `refresh_sessions`); `AtomicPatchApplier` có journal/rollback nguyên tử; `ddl-auto: validate`.

**Frontend chất lượng:**
- Memory leak cleanup hầu hết đúng: `useSigma.dispose()` dọn đủ ResizeObserver/rAF/listener; các composable realtime dùng `onScopeDispose`; dashboard/SSE dừng polling khi unmount.
- Race condition guard nhất quán (`loadSeq`, `sessionStateRequestId`, stale-event guard).
- 0 catch rỗng, 0 `console.*` tồn đọng; tree-shaking tốt (ECharts theo module, highlight.js lazy).
- Auth forms xử lý lỗi đầy đủ (try/catch + i18n + finally).

**DevOps chất lượng:**
- `.gitignore` chặn file rác đầy đủ — đối chiếu `git ls-files`: 0 file rác bị track.
- Không secret plaintext trong compose — tất cả dùng `${VAR:?Set X in .env}` fail-fast.
- Healthcheck + `depends_on: condition: service_healthy` đầy đủ cho cả 4 service; Neo4j giới hạn RAM.
- CI backend chạy `mvnw verify` với JaCoCo gate; CI frontend đủ type-check → test → lint → build → audit; cả hai có `permissions: contents: read` + timeout.

---

## 7. Lộ trình hành động đề xuất

| Đợt | Nội dung | Liên quan |
|---|---|---|
| **Ngay hôm nay** | Xoay toàn bộ secret trong `.env`; drop `stash@{0}`; xóa file `.env.codex_backup-*` | S1, S2 |
| **Tuần 1** | Non-root Dockerfile; bỏ mount `.env`; `.dockerignore` frontend; bind DB ports về 127.0.0.1; `AUTH_COOKIE_SECURE=true`; password DB mạnh; bỏ APOC unrestricted | H1–H5 |
| **Tuần 1** | Rate-limit filter lên trước bước xác thực; chốt `Files.size()` trước `readAllLines` trong `readRange`; redact private key theo block; `npm audit fix` (ưu tiên websocket-driver/undici/axios) | H13, H14, H15, H16 |
| **Tuần 1–2** | Fix `ProjectServiceImpl` (registry + ID collision cùng đợt); async hóa `/analyze`; batch query AdminService | H6–H9 |
| **Tuần 2** | Cancellation token cho polling import; try/catch UsersTableView; lazy-load router + manualChunks | H10–H12, F-M4, F-M5 |
| **Backlog refactor** | Xóa ~1.319 dòng dead code frontend; tách god class/file lớn; cập nhật `dev-up.ps1`; gộp `task/` + `task-final/`; dọn đích danh ~105MB log/dump ở root (KHÔNG dùng `git clean -fdX`) | F-M1, F-M6, B-M2, D-M1, D-M5, D-L3 |

---

## 8. Ghi chú đối chiếu chéo (12/08/2026)

Báo cáo đã được đối chiếu với 3 báo cáo audit độc lập khác; các bổ sung H13–H16, B-M9–B-M11, B-L8, S-L5 là phát hiện đã kiểm chứng bằng code thật (riêng B-M11 ban đầu là xác nhận chéo; ngày 12/08/2026 đã tự kiểm chứng tĩnh V1.1 — xem mục B-M11 và bảng §9; runtime V1.2 BLOCKED). Phát hiện "serve `projects/` qua HTTP port 8080" từng được nêu trong đối chiếu đã xác minh là KHÔNG tồn tại trong code (không có resource handler/`LocalSourceConfig`/`WebMvcConfig` nào) nên không đưa vào báo cáo.

**Đối chiếu lần 2 (12/08/2026) với `update/docs/claude`** (báo cáo độc lập thứ 4): 7/7 phát hiện mới xác nhận CÓ THẬT, đã nạp vào báo cáo này (H17, B-M12–B-M14, B-L9–B-L11); đồng thời chữa số liệu B-M2 (1.398 dòng), H7 (dòng 62, 91, 101), F-M1 (~1.319 dòng) và remediation S2, D-L3.

**Đối chiếu lần 3 (12/08/2026, chốt 17:10) với codex v2.4** (54 finding): 13/13 nội dung mới xác nhận CÓ THẬT, 12/13 map về mã Qwen hiện có — vài cặp mapping chính: C2↔S1+S2, M23↔H17, M25↔B-M12, M30↔H11, M31↔D-M1. 3 phê phán của v2.4 nhắm vào Qwen đều đúng và đã áp dụng đính chính: B-L10 (bảng `user_notifications` vẫn đang dùng qua JDBC — **đã đính chính**), S-M5 (diễn đạt trần multipart — **đã đính chính**), diễn đạt `task/`/`task-final/` (không phải bản sao hoàn toàn, không `git rm` máy móc — **đã đính chính** tại D-M5).

---

## 9. Kết quả kiểm chứng runtime (Chrome, 12/08/2026)

Agent test Chrome độc lập đã chạy 8 test runtime (Chrome 151.0.7922.110, frontend `localhost:5173`, backend `localhost:8080`). Chi tiết đầy đủ: `runtime-evidence/RUNTIME-VERIFICATION-REPORT.md`; ảnh/log bằng chứng trong thư mục `runtime-evidence/` (`T1-landing.png`…`T8-cors.txt`).

| Test | Phát hiện liên quan | Kết luận | Số liệu bằng chứng |
|---|---|---|---|
| T1 | H12 (router import tĩnh) | **CONFIRMED** | Landing tải 117 module ≈ 4,17 MB, gồm `sigma.js` 161KB, `graphology.js` 154KB, 2 worker ForceAtlas2/NoOverlap; `/login` cùng graph stack |
| T2 | H4 (cookie Secure) | **CONFIRMED** | `vg_session` + `vg_refresh`: HttpOnly, SameSite=Lax, **thiếu cờ Secure** trên HTTP |
| T3 | H10 (polling không hủy) | **BLOCKED** | Backend timeout GitHub (422 `GITHUB_IMPORT_ERROR`), không import nào đạt ANALYZING → không quan sát được polling sau unmount |
| T4 | H11 (UsersTableView thiếu xử lý lỗi) | **CONFIRMED** | Network Offline + Search: UI không thông báo; Console `ERR_INTERNET_DISCONNECTED` + `Uncaught (in promise)` |
| T5 | H13 (rate-limit sau bcrypt) | **NOT REPRODUCED** | Fake key 3,0ms vs no-key 3,2ms (delta −0,2ms); fake key 401×22 + 429×8; không thấy chi phí bcrypt — xem ghi chú tại H13 |
| T6 | H14 (readRange OOM) | **CONFIRMED** | File 200MiB → memory backend +211,6MiB (680,6 → 892,2MiB) dù response chỉ trả 300 dòng |
| T7 | B-M10 (cap node = 0) | **CONFIRMED** | Request `nodeLimit=0&edgeLimit=0` (~577/559ms), render ~2.405 node không cảnh báo cap |
| T8 | CORS wildcard | **NOT REPRODUCED** | Origin giả `https://evil.example` trả 200 nhưng `Access-Control-Allow-Origin=null` — CORS KHÔNG wildcard (khớp mục SẠCH/TỐT) |
| V1.1 | B-M11 (upsert không nguyên tử, tĩnh) | **CONFIRMED** | Nhiều `session.run` autocommit (1 câu project + 1 câu/node label + 1 câu/relationship type), không transaction bao graph; nhánh FAILED giữ project + graph đã ghi |
| V1.2 | B-M11 (runtime) | **BLOCKED** | PostgreSQL không có project FAILED để đối chiếu (FAILED = 0; Neo4j có 13 project); không chủ động kill backend dùng chung |
| V2.2 | H13 (chi phí bcrypt trước rate-limit) | **CONFIRMED** | Median 30 request/nhóm: không key 4,25ms / prefix ngẫu nhiên 4,64ms / **trùng prefix 54,84ms** (+~50,20ms); 90/90 trả 401, 0 trả 429 |
| V2.3 | H13 (thứ tự filter) | **CONFIRMED** | API-key filter chạy TRƯỚC rate-limit filter (rate-limit chỉ ràng buộc trước `AuthorizationFilter`) |

**Lưu ý:** File tạm 200MiB của T6 vẫn được giữ tại `.vibegraph/uploads/github-04e0b065-39f6-484b-bc84-7bf25f8b2704/source/ThinhChauTran263-fatc-Grocery-Store-ce1c762/runtime-t6-large.txt` (chưa xóa — người dùng tự quyết định dọn).

**Lưu ý (vòng V1/V2, 12/08/2026):** Key test `runtime-h13-20260812` của V2 đã được tạo rồi xóa qua UI — PostgreSQL xác nhận `deleted_at IS NOT NULL`. Chi tiết đầy đủ 4 test: `runtime-evidence/VERIFICATION-BM11-H13-REPORT.md` (kèm `V1-static.txt`, `V1-runtime-cypher.txt`, `V2-timing.txt`, `V2-filter-order.txt`).

---

## 10. Kết quả triển khai Đợt 1 (12/08/2026)

Agent triển khai bên ngoài đã hoàn thành Đợt 1 (REMEDIATION-PLAN §3) và nộp báo cáo nghiệm thu đạt yêu cầu. Tổng kiểm: `mvnw -DskipITs test` — **1.008 test, 0 failure, BUILD SUCCESS**. Số phát hiện giữ nguyên 76 (chỉ cập nhật trạng thái).

| Mã | Thay đổi chính | Bằng chứng nghiệm thu |
|---|---|---|
| H1 | User non-root trong container backend + entrypoint mới `docker/entrypoint.sh` | JVM PID 1 chạy user `app` non-root; entrypoint dùng su-exec chown `/uploads` rồi drop quyền |
| H2 | Bỏ mount `./.env:/app/.env:ro` | Backend healthy không mount `.env` |
| H3 | Bind 3 port DB về loopback; bỏ APOC unrestricted; password mạnh | 3 port bind loopback (kiểm chứng `Get-NetTCPConnection`); Neo4j healthy sau bỏ APOC unrestricted; host-port Postgres đổi 5432→5433 do máy dev có Postgres native (backend vẫn nối `postgres:5432` nội bộ) |
| H4 | `AUTH_COOKIE_SECURE` mặc định `true` | Default true; T2 end-to-end chờ TLS termination (chưa có reverse proxy) — chuyển Backlog |
| H5 (+D-M2) | Tạo `vibegraph-web/.dockerignore` + pin tag image | Build context gọn; tag pin: `node:22.11.0-alpine`, `nginx:1.27-alpine`, `maven:3.9.11`, `neo4j:5.26-community` |
| S-M2 | Resolver lấy token phải nhất ngoài trusted range; `.env` đặt `VIBEGRAPH_TRUST_PROXY=false` | +27/−9 dòng; `ClientAddressResolverTest` 8 test xanh, gồm test mới `resolve_trustedProxyConfiguration_ignoresSpoofedLeftmostAddress`; biến thể XFF xoay vòng: 429 ngay request #1 của phút kế tiếp — xoay XFF không tạo bucket mới (bypass đã khóa) |
| H13 | Rate-limit chạy trước bước xác thực (đăng ký qua `addFilterAt`) | Burst 150 request key sai trùng prefix: **120×401 + 30×429**, 429 đầu tiên đúng request #121 (ngưỡng 120/phút/IP); trước sửa: 90/90 trả 401, 0×429 |
| H14 | `readRange` chốt `Files.size()` + try/catch `IOException` | T6 sau sửa: file vượt trần → `found=false` + warning "File too large for source reading." trong 75ms; heap 581,7→581,7 MiB; 3 request đồng thời +0,2 MiB; 60s sau 581,9 MiB — không spike, không leak (trước sửa +211,6 MiB/request); test mới `refusesOversizedFile` |
| H15 | Redact private key theo block đầy đủ (BEGIN→END) | Phủ cả 2 trường hợp block bị cắt giữa range đọc; 3 test mới: `redactsPrivateKeyBlock`, `redactsMultiplePrivateKeyBlocks`, `redactsPrivateKeyBodyWhenRangeCutsTheBlock` |
| H17 | `spring.task.scheduling.pool.size: 4` + alert rules | Pool size hiệu lực; tạo mới `ops/prometheus/vibegraph-alerts.yml` (2 rule) vì repo chưa có stack Prometheus |
| B-M12 | `log.warn` + message chung an toàn cho `IllegalStateException` → 409 | Message chung tiếng Anh theo convention; giữ code `PRECONDITION_FAILED` (grep xác nhận frontend không parse message) |
| B-M14 | Purge trash theo batch + `Pageable` | Repository phân trang + guard "batch 0 tiến độ → dừng" (chống vòng lặp vô hạn khi purge thất bại lặp) + bỏ `@Transactional` bao toàn sweep; 3 test sweep cập nhật |

### Lệch có lý do so với FIX-DETAILS

- **H1 (entrypoint chown):** thay vì chỉ thêm `USER app` trong Dockerfile, triển khai dùng entrypoint mới `docker/entrypoint.sh` với su-exec: chown `/uploads` khi khởi động rồi drop quyền — đảm bảo volume đã có dữ liệu vẫn ghi được mà tiến trình JVM không chạy root.
- **H13 (anchor filter):** snippet gợi ý `JwtAuthFilter.class` nhưng Spring Security 7.0.5 ném `IllegalArgumentException` (class tự viết không có registered order) → dùng `UsernamePasswordAuthenticationFilter.class` (JWT đăng ký tại đó qua `addFilterAt`), thứ tự tương đương, có comment giải thích trong code.
- **H15 (mở rộng redact):** ngoài block đầy đủ, bổ sung phủ trường hợp block bị cắt giữa range đọc (thân base64 vẫn phải redact khi range không chứa dòng `-----END`) — vượt yêu cầu tối thiểu của FIX-DETAILS.
- **B-M14 (batch-0 stop):** bổ sung guard "batch 0 tiến độ → dừng" ngoài yêu cầu phân trang — chống vòng lặp vô hạn khi purge thất bại lặp lại cùng batch; kèm bỏ `@Transactional` bao toàn sweep.
- **B-M12 (message tiếng Anh):** message chung dùng tiếng Anh theo convention hiện hành của codebase (thay vì tiếng Việt như một số đề xuất), giữ code `PRECONDITION_FAILED`.
- **H3 (Postgres host port 5433):** đổi host-port 5432→5433 do máy dev đã có Postgres native chiếm 5432; backend vẫn nối `postgres:5432` nội bộ nên hành vi ứng dụng không đổi.

### Mục mở chuyển sang Backlog/Đợt 0

- **H4/T2 chờ TLS termination:** `AUTH_COOKIE_SECURE` đã mặc định `true` nhưng kiểm chứng T2 end-to-end (cookie có cờ Secure qua HTTPS) chưa chạy được vì môi trường chưa có reverse proxy TLS.
- **Tàn dư test T6:** 2 tài khoản disposable `runtime-t6-2026081220*@example.com` + project `be9ab43e`, `d0b1f52d` — chờ operator duyệt dọn.
- **Đợt 0 (S1/S2) chưa làm:** xoay secret và dọn git object vẫn chờ xác nhận (xem REMEDIATION-PLAN §2).
- **GitNexus MCP không kết nối:** trong đợt này việc tìm tham chiếu thực hiện thủ công thay vì qua GitNexus; khuyến nghị chạy `npx gitnexus analyze` sau loạt sửa để đối chiếu tham chiếu tự động.

---

*Báo cáo được tổng hợp từ 4 audit thành phần độc lập (read-only, không có file nào bị sửa trong quá trình audit). Các phát hiện mức Cao/Nghiêm trọng đều kèm bằng chứng trích dẫn dòng code thực tế; khuyến nghị xác nhận thêm bằng test tích hợp khi triển khai sửa chữa.*
