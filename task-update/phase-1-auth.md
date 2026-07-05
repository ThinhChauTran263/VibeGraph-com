# Phase 1 — Xác thực + Quyền sở hữu (V1, V2)

**Mục tiêu:** Người dùng đăng ký/đăng nhập; mỗi project gắn `ownerId`; mọi API có `projectId` chỉ phục vụ đúng chủ sở hữu (chống IDOR). Đây là phần hội đồng yêu cầu.

**Định nghĩa hoàn thành (DoD):**
- Đăng ký + đăng nhập chạy, trả JWT.
- Gọi API không token → 401; gọi project của người khác → 403.
- FE có trang Login/Register, chặn route khi chưa đăng nhập, gắn token vào mọi request.
- Project cũ (chưa có owner) được gán cho 1 tài khoản admin.
- Test: unit cho JwtService + ownership guard; IT cho luồng đăng nhập.

---

## 1. Chuẩn bị & phụ thuộc

- ⬜ **1.1** Thêm dependency `pom.xml`: `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `org.flywaydb:flyway-core` (+ `flyway-database-postgresql`), `io.jsonwebtoken:jjwt-api/jjwt-impl/jjwt-jackson` (0.12.x).
- ⬜ **1.2** Thêm service `postgres` vào `docker-compose.yml` chính (tái dùng `database/docker-compose.postgres.yml`); `backend` `depends_on` postgres healthy.
- ⬜ **1.3** `application.yaml`: cấu hình `spring.datasource` (URL/user/pass từ env `POSTGRES_*`), `spring.jpa.hibernate.ddl-auto=validate` (KHÔNG để `update` — Flyway quản schema), `spring.flyway.enabled=true`.
- ⬜ **1.4** Copy `database/schema/V1__init_auth_schema.sql` → `src/main/resources/db/migration/V1__init_auth_schema.sql` (giữ 2 bản khớp nhau).
- ⬜ **1.5** Thêm env vào `.env.example`: `POSTGRES_HOST/PORT/DB/USER/PASSWORD`, `JWT_SECRET` (≥32 ký tự), `JWT_EXPIRATION_MS`. Xác nhận `.env` trong `.gitignore`.

## 2. Mô hình dữ liệu (Postgres — đã thiết kế ở `database/`)

- ⬜ **2.1** JPA entity `User` (bảng `users`): id UUID, email, passwordHash, displayName, role, quotaBytes, usedBytes, timestamps.
- ⬜ **2.2** JPA entity `ProjectOwnership` (bảng `projects`): projectId (PK, = id Neo4j), ownerId (FK), name, sourceType, sizeBytes, status.
- ⬜ **2.3** JPA entity `ApiKey` (bảng `api_keys`) — có thể để Phase 3, tạo entity trước cũng được.
- ⬜ **2.4** Khi tạo project (import-local/archive/github) → ghi 1 dòng vào `projects` với `owner_id = currentUser` (song song với ghi graph vào Neo4j).
- ⬜ **2.5** Migrate dữ liệu cũ: với mỗi `:Project` đang có trong Neo4j chưa có dòng ownership → tạo dòng `projects` gán `owner_id = admin`. (script 1 lần khi bật auth)

> Ghi chú: ownership giờ là **nguồn sự thật ở Postgres** (`projects.owner_id`), KHÔNG lưu `ownerId` trên node Neo4j nữa — tránh 2 nguồn lệch nhau.

## 3. Backend — Auth core

- ⬜ **3.1** `UserRepository` (Neo4j): `findByEmail`, `save`, `findById`.
- ⬜ **3.2** `JwtService`: phát token (subject = userId, claim email/role), verify + parse. Dùng jjwt HS256, đọc secret từ config.
- ⬜ **3.3** `PasswordEncoder` bean = BCrypt.
- ⬜ **3.4** `AuthService`: `register(email, password, name)` (băm mật khẩu, chặn email trùng), `login(email, password)` (verify → trả JWT).
- ⬜ **3.5** DTO: `RegisterRequest`, `LoginRequest`, `AuthResponse{token, user}`. **KHÔNG** trả passwordHash trong bất kỳ response nào.
- ⬜ **3.6** `AuthController`: `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`.

## 4. Backend — Spring Security

- ⬜ **4.1** `SecurityConfig` (SecurityFilterChain): stateless, tắt session; permit `/api/auth/**`, `/actuator/health`; còn lại yêu cầu authenticated.
- ⬜ **4.2** `JwtAuthFilter` (OncePerRequestFilter): đọc header `Authorization: Bearer`, verify, set `SecurityContext`.
- ⬜ **4.3** Cấu hình CORS trong SecurityConfig khớp `vibegraph.cors.allowed-origins` hiện có (không wildcard kèm credentials).
- ⬜ **4.4** `CurrentUser` helper: lấy userId từ SecurityContext cho controller/service.

## 5. Backend — Ownership check (chống IDOR, fix V2)

- ⬜ **5.1** Tạo `ProjectOwnershipGuard` (đặt ở `common/`): `assertOwner(projectId, currentUserId)` → `SELECT owner_id FROM projects WHERE project_id=?` (qua JPA repo), khác → ném `ForbiddenException` (403); không tồn tại → 404.
- ⬜ **5.2** Gọi guard ở **đầu mọi endpoint có projectId**: `GET /{id}`, `/{id}/analyze`, `DELETE /{id}`, `/{id}/graph`, `/graph/impact`, `/graph/neighbors/**`, `/{id}/source`, `/{id}/diagrams/**`.
- ⬜ **5.3** `GET /api/projects` chỉ trả project của current user: lấy danh sách `project_id` từ Postgres theo `owner_id`, rồi đọc metadata (kết hợp Neo4j nếu cần).
- ⬜ **5.4** Khi tạo project → ghi dòng ownership vào Postgres (mục 2.4) trong cùng luồng import.
- ⬜ **5.5** `ForbiddenException` + mapping 403 (và 404 cho project lạ) trong `GlobalExceptionHandler`.
- ⬜ **5.6** Khi `DELETE` project → xoá cả dòng Postgres `projects` + graph Neo4j (đặt nền cho `purgeProject()` ở V15).

## 6. Frontend — Auth

- ⬜ **6.1** `stores/auth.ts` (Pinia): state `token`/`user`, action `login/register/logout`, lưu token vào `localStorage`, khôi phục khi F5.
- ⬜ **6.2** Axios interceptor trong `lib/api.ts`: gắn `Authorization: Bearer <token>`; bắt 401 → logout + đẩy về `/login`.
- ⬜ **6.3** Trang `LoginView.vue` + `RegisterView.vue` (form email/mật khẩu, hiển thị lỗi rõ ràng, có label + validation).
- ⬜ **6.4** Router guard (`router/index.ts`): route cần đăng nhập → chưa có token thì chuyển `/login`; đã đăng nhập vào `/login` thì chuyển dashboard.
- ⬜ **6.5** Header UI: hiện email + nút Logout.

## 7. Test

- ⬜ **7.1** Unit: `JwtServiceTest` (phát→verify→hết hạn), `AuthServiceTest` (register trùng email, login sai mật khẩu).
- ⬜ **7.2** Unit: `ProjectOwnershipGuardTest` (đúng owner pass, sai owner ném 403).
- ⬜ **7.3** IT: `AuthApiIT` (register→login→gọi API có token OK, không token 401, project người khác 403). Dùng Testcontainers Neo4j sẵn có.
- ⬜ **7.4** Đảm bảo JaCoCo vẫn ≥ 70%.

## 8. Hoàn tất

- ⬜ **8.1** Cập nhật `README.md` / `USER_GUIDE.md`: cách đăng ký/đăng nhập.
- ⬜ **8.2** `gitnexus_detect_changes` trước commit; tách commit hợp lý (be-auth, be-ownership, fe-auth, tests).
- ⬜ **8.3** Push nhánh `poc` (hoặc `feat/auth`), tạo PR nếu cần.

---

## Rủi ro & lưu ý

- **Realtime WebSocket** hiện chưa auth — Phase 1 CHƯA chặn WS (để V6/Phase 3 làm), nhưng nhớ: sau khi bật security, endpoint STOMP `/ws` cần được permit tạm hoặc token, kẻo realtime gãy. Ghi rõ khi làm 4.1.
- **MCP `/mcp`** cũng chưa auth ở Phase 1 (V10/Phase 3). Nếu bật security chặn hết, nhớ permit `/mcp` tạm thời để không vỡ tính năng đang demo, kèm ghi chú "chưa bảo vệ".
- Thứ tự đề xuất: **2 → 3 → 4 → 5 → 6 → 7**. Làm BE chạy + test bằng curl trước, rồi mới ghép FE.
