# CHI TIẾT SỬA LỖI — FRONTEND & DEVOPS

- **Ngày tạo:** 12/08/2026 · **Nguồn:** `AUDIT-REPORT.md` (76 phát hiện) — mọi file/dòng trích từ báo cáo gốc.
- **Cấu trúc mỗi mục:** Hiện trạng → Snippet đề xuất → Tiêu chí nghiệm thu.
- Snippet là **đề xuất triển khai** dựa trên phần "Đề xuất" của AUDIT-REPORT; khi sửa thật phải đối chiếu lại code tại thời điểm sửa.

---

## P1 — Mức Cao: Docker & bảo mật vận hành (H1–H5)

### H1. Container backend chạy root (+ kèm D-M3: tên jar tường minh)

**Hiện trạng:** `Dockerfile` dòng 10–14 — không có chỉ thị `USER`; kết hợp bề mặt tấn công rộng (endpoint đọc file source, import archive) và mount writable `./projects:/projects` → nếu có RCE sẽ chiếm full container. Kèm D-M3: dòng 12 dùng wildcard `*.jar`.

**Snippet đề xuất (theo AUDIT-REPORT H1):**
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

**Nghiệm thu:** `docker exec vibegraph-backend whoami` ≠ root; import project vào `./projects` vẫn ghi được (volume đã chown cho user app); build fail sớm nếu xuất hiện jar thứ hai (D-M3).

### H2. Mount toàn bộ `.env` vào container

**Hiện trạng:** `docker-compose.yml` dòng 128 — mount `./.env:/app/.env:ro` làm lộ cả secret thừa (OAuth/Gemini) cho mọi tiến trình trong container; khối `environment:` (dòng 63–126) đã truyền đủ biến; `spring.config.import` là `optional:` nên bỏ mount vẫn chạy.

**Snippet đề xuất:**
```yaml
volumes:
  # - ./.env:/app/.env:ro   # XÓA dòng này — environment: đã đủ
  - ./projects:/projects
  - upload-workspaces:/uploads
```

**Nghiệm thu:** container khởi động bình thường không có mount `.env`; rà soát trước đó rằng mọi biến backend cần đều đã có trong khối `environment:` dòng 63–126.

### H3. Credentials yếu + port DB expose 0.0.0.0 + APOC không giới hạn

**Hiện trạng:** `docker-compose.yml` dòng 6–7, 24–30; `.env` dòng 16, 27 — Postgres/Neo4j password là `vibegraph`, port 5432/7474/7687 publish ra mọi interface, kèm `NEO4J_dbms_security_procedures_unrestricted: apoc.*` (APOC unrestricted có thể đọc file/exec). Backend gọi DB qua hostname nội bộ nên không cần publish.

**Snippet đề xuất:**
```yaml
services:
  postgres:
    ports: ["127.0.0.1:${POSTGRES_PORT:-5432}:5432"]
  neo4j:
    ports: ["127.0.0.1:7474:7474", "127.0.0.1:7687:7687"]
    # XÓA: NEO4J_dbms_security_procedures_unrestricted: apoc.*
```
Kèm đổi password mạnh ngẫu nhiên trong `.env`.

**Nghiệm thu:** `nmap`/kết nối từ máy khác trong LAN tới 5432/7474/7687 bị từ chối; backend vẫn nối DB qua hostname nội bộ; các flow phân tích graph dùng APOC vẫn chạy sau khi bỏ unrestricted.

### H4. Cookie phiên Secure mặc định `false` trong Docker — RT (T2)

**Hiện trạng:** `docker-compose.yml` dòng 102 + `.env` dòng 44 — JWT session cookie có thể truyền qua HTTP plaintext khi truy cập bằng địa chỉ LAN (`AuthCookieService.java:73` chỉ bật Secure khi cấu hình hoặc request secure). Runtime T2 CONFIRMED: `vg_session` và `vg_refresh` có HttpOnly, SameSite=Lax nhưng thiếu cờ Secure.

**Snippet đề xuất:**
```yaml
AUTH_COOKIE_SECURE: ${AUTH_COOKIE_SECURE:-true}
```

**Nghiệm thu:** chạy lại test T2 (`RUNTIME-VERIFICATION-PROMPT.md`) qua HTTPS: cả 2 cookie có cờ Secure. Lưu ý vận hành: cần reverse proxy TLS trước khi bật, nếu không cookie không gửi qua HTTP thuần.

### H5. Thiếu `.dockerignore` cho frontend

**Hiện trạng:** `vibegraph-web/Dockerfile` dòng 13 (`COPY . .`) — xác nhận `vibegraph-web/.dockerignore` không tồn tại; build context kéo theo `node_modules/`, `dist/`, `*.log` → phình hàng trăm MB, chậm, hỏng cache layer.

**Snippet đề xuất — tạo mới `vibegraph-web/.dockerignore`:**
```
node_modules
dist
*.log
.vite
.vscode
.idea
```

**Nghiệm thu:** `docker build vibegraph-web` không còn gửi hàng trăm MB context; image vẫn build/serve đúng; kiểm tra không pattern nào loại nhầm file mà Dockerfile đang COPY.

---

## P1 — Mức Cao: Frontend logic (H10–H12)

### H10. Vòng lặp polling import GitHub không thể hủy (T3 BLOCKED)

**Hiện trạng:** `vibegraph-web/src/composables/useGitHubImport.ts` (~dòng 124–160) + `GitHubImportForm.vue` (script dòng 1–46 không có `onBeforeUnmount`) + `ImportProjectPanel.vue` dòng 171 — vòng lặp `for(;;)` chỉ dừng ở trạng thái terminal/stall timeout/trần 1 giờ (`IMPORT_ABSOLUTE_TIMEOUT_MS = 3.600.000ms`); form render bằng `v-else` → chuyển tab là unmount nhưng polling + WebSocket vẫn chạy mồ côi. Trạng thái bằng chứng: T3 BLOCKED (backend timeout GitHub) — giữ phân tích tĩnh.

**Snippet đề xuất:**
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

**Nghiệm thu:** unit test mô phỏng unmount giữa vòng polling → vòng lặp dừng, không ghi vào refs đã unmount; chạy lại T3 khi GitHub liên hệ được để xác nhận thực tế.

### H11. UsersTableView — 5 điểm gọi API không try/catch — RT (T4)

**Hiện trạng:** `vibegraph-web/src/views/admin/UsersTableView.vue` dòng 70–72, 74–79, 104–110, 316–321, 340–345 — backend 500 hoặc lỗi mạng gây unhandled rejection âm thầm, bảng trống. Runtime T4 CONFIRMED: Offline + Search → UI không thông báo, Console `ERR_INTERNET_DISCONNECTED` + `Uncaught (in promise)`.

**Snippet đề xuất:**
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

**Nghiệm thu:** chạy lại test T4: Network Offline + Search → UI hiển thị thông báo lỗi i18n (`admin.users.loadFailed`), Console không còn `Uncaught (in promise)`; cả 5 điểm gọi (load, phân trang, lọc, BAN, unban) đều có try/catch.

### H12. Router import tĩnh kéo stack đồ họa vào main bundle — RT (T1) + F-M5 manualChunks

**Hiện trạng:** `vibegraph-web/src/router/index.ts` dòng 2–6 — `GraphView` (Sigma/Graphology/ForceAtlas2 — phần nặng nhất app) cùng HomeView/LoginView/RegisterView/LandingView import tĩnh. Runtime T1 CONFIRMED: landing tải 117 module ≈ 4,17 MB gồm `sigma.js` 161KB, `graphology.js` 154KB, 2 worker ForceAtlas2/NoOverlap; `/login` vẫn kéo cùng graph stack. Kèm F-M5: `vite.config.ts` không có `manualChunks` → vendor lớn gộp chung, cache kém.

**Snippet đề xuất:**
```ts
// router/index.ts — lazy toàn bộ view nặng
const GraphView = () => import('@/views/GraphView.vue')
const LoginView = () => import('@/views/LoginView.vue')
const RegisterView = () => import('@/views/RegisterView.vue')
const HomeView = () => import('@/views/HomeView.vue')
const LandingView = () => import('@/views/LandingView.vue')
```
```ts
// vite.config.ts — tách vendor theo nhóm (F-M5)
build: {
  rollupOptions: {
    output: {
      manualChunks: {
        'vendor-graph': ['sigma', 'graphology', 'graphology-layout-forceatlas2'],
        'vendor-charts': ['echarts'],
      },
    },
  },
}
```

**Nghiệm thu:** chạy lại test T1: landing/login không còn tải `sigma.js`/`graphology.js`/worker ForceAtlas2; báo cáo `npm run build` cho thấy chunk vendor tách riêng; không circular chunk warning.

---

## P2 — Trung bình frontend nổi bật

### F-M3. Gỡ axios — 2 HTTP client song song

**Hiện trạng:** `src/lib/http.ts` (59 dòng) + `package.json` (axios ^1.16.1) + `src/lib/api.ts` dòng 7, 622 — axios chỉ dùng cho đúng 1 endpoint `authApi.me()`; ~40 endpoint còn lại dùng fetch wrapper cùng logic refresh 401 → dependency thừa ~14KB gzip.

**Snippet đề xuất:**
```ts
// src/lib/api.ts — chuyển me() sang fetch wrapper đang dùng cho 40 endpoint còn lại:
export const authApi = {
  me: () => http.get<UserProfile>('/auth/me'),   // fetch wrapper, tự refresh 401
  // ...
}
// sau đó: xóa src/lib/http.ts, gỡ "axios" khỏi package.json + npm install
```

**Nghiệm thu:** `grep axios` trong `src/` rỗng; `authApi.me()` hoạt động cả khi access token hết hạn (refresh 401 tương đương interceptor cũ); bundle bớt ~14KB gzip.

### F-M4. Lazy-load locale

**Hiện trạng:** `src/language/index.ts` dòng 2–3 — cả 2 locale JSON import eager (≈140KB vào main bundle).

**Snippet đề xuất:**
```ts
// Chỉ eager locale mặc định:
import vi from './locales/vi.json'
const messages: Record<string, LocaleMessages> = { vi }

export async function setLocale(locale: string) {
  if (!messages[locale]) {
    messages[locale] = (await import(`./locales/${locale}.json`)).default
  }
  i18n.global.locale.value = locale
}
```

**Nghiệm thu:** main bundle bớt ≈140KB (báo cáo build); chuyển ngôn ngữ lần đầu vẫn mượt (locale tải xong mới apply); test cả 2 ngôn ngữ hiển thị đúng.

---

## P2 — Trung bình DevOps nổi bật

### D-M1. `dev-up.ps1` thiếu Postgres

**Hiện trạng:** `scripts/dev-up.ps1` dòng 32–44 — script lỗi thời chỉ khởi động Neo4j; backend hiện bắt buộc Postgres (Flyway + `ddl-auto: validate`) → dev chạy script này backend sẽ fail.

**Snippet đề xuất:**
```powershell
docker compose up -d postgres neo4j
# chờ Postgres sẵn sàng trước khi khởi động backend:
do { Start-Sleep -Seconds 1 } until (docker compose exec -T postgres pg_isready -q)
```

**Nghiệm thu:** chạy `scripts/dev-up.ps1` từ máy sạch → backend khởi động thành công, Flyway migrate/validate qua, không lỗi thiếu Postgres.

### D-M2 + D-M3. Pin image tag + finalName jar

**Hiện trạng:** D-M2 — `Dockerfile` dòng 3, 10; `vibegraph-web/Dockerfile` dòng 3, 16; `docker-compose.yml` dòng 3, 21: image tag không pin minor/patch (`maven:3.9-eclipse-temurin-21`, `node:22-alpine`, `nginx:alpine`, `neo4j:5-community`) → build không tái lập được. D-M3 — `Dockerfile` dòng 12 dùng wildcard `COPY --from=builder /build/target/*.jar app.jar` → fail nếu xuất hiện jar thứ hai.

**Snippet đề xuất:**
```dockerfile
# Pin tag cụ thể (hoặc digest) — ví dụ:
FROM maven:3.9.9-eclipse-temurin-21 AS builder
FROM node:22.11.0-alpine AS frontend-builder
FROM nginx:1.27-alpine
# docker-compose: neo4j:5.26-community
```
```xml
<!-- pom.xml — tên jar tường minh cho COPY không wildcard -->
<build>
  <finalName>app</finalName>
</build>
```
```dockerfile
COPY --from=builder /build/target/app.jar app.jar
```

**Nghiệm thu:** build 2 lần cách nhau cho image cùng phiên bản thành phần; `Dockerfile` COPY tên jar tường minh, build fail sớm nếu có jar lạ; snippet H1 dùng chính `app.jar` này.

### D-M4. Pin SHA GitHub Actions + bổ sung CD

**Hiện trạng:** `.github/workflows/backend.yml` dòng 39, 42, 56; `frontend.yml` dòng 33, 36 — Actions chỉ pin major version (`actions/checkout@v4`), rủi ro supply-chain; chưa có job build/push Docker image (thiếu CD).

**Snippet đề xuất:**
```yaml
# Pin commit SHA thay vì major tag (kèm comment tên version để dễ đọc):
- uses: actions/checkout@<full-40-char-sha>   # v4.x.y
- uses: actions/setup-java@<full-40-char-sha>  # v4.x.y
```
```yaml
# Job CD bổ sung (sau khi test xanh):
deploy:
  needs: [test]
  steps:
    - uses: docker/build-push-action@<sha>
      with:
        push: true
        tags: registry.../vibegraph-backend:${{ github.sha }}
```

**Nghiệm thu:** mọi `uses:` trong 2 workflow đều dạng `@<sha>`; workflow CI xanh với pin mới; job CD build + push được image (chạy thử trên registry staging trước khi bật production).
