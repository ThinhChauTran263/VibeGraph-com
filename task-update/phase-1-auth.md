# Phase 1 — Xác thực + Quyền sở hữu (V1, V2)

**Mục tiêu:** Người dùng đăng ký/đăng nhập; mỗi project gắn `ownerId`; mọi API có `projectId` chỉ phục vụ đúng chủ sở hữu (chống IDOR). Đây là phần hội đồng yêu cầu.

**Định nghĩa hoàn thành (DoD):**
- Đăng ký + đăng nhập bằng **email/mật khẩu** chạy, trả JWT.
- **Đăng nhập bằng Google** chạy: tạo/link user qua `user_identities`, trả JWT nội bộ.
- Gọi API không token → 401; gọi project của người khác → 403.
- FE có trang Login/Register, chặn route khi chưa đăng nhập, gắn token vào mọi request.
- Project cũ (chưa có owner) được gán cho 1 tài khoản admin.
- Test: unit cho JwtService + ownership guard; IT cho luồng đăng nhập + ownership với PostgreSQL và Neo4j Testcontainers.
- Không còn endpoint REST đọc/sửa/xóa project bỏ qua ownership guard.

**Quyết định kiến trúc bắt buộc:**
- User, ownership, quota, API key là **Postgres/JPA**. Không tạo `UserRepository` Neo4j, không lưu API key/user trên Neo4j.
- Neo4j chỉ là data plane cho graph code. `projects.project_id` trong Postgres trùng `:Project.id` trong Neo4j.
- `projects.owner_id` trong Postgres là nguồn sự thật duy nhất cho quyền sở hữu.
- Các service graph/import không được tự suy luận owner; owner lấy từ `CurrentUser` tại boundary hoặc truyền qua command/service method rõ ràng.

---

## 1. Chuẩn bị & phụ thuộc

- ⬜ **1.1** Thêm dependency `pom.xml`: `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `org.flywaydb:flyway-core` (+ `flyway-database-postgresql`), `io.jsonwebtoken:jjwt-api/jjwt-impl/jjwt-jackson` (0.12.x), **`com.google.api-client:google-api-client`** (verify Google ID token — cho OAuth Google).
- ⬜ **1.2** Thêm service `postgres` vào `docker-compose.yml` chính (tái dùng `database/docker-compose.postgres.yml`); `backend` `depends_on` postgres healthy.
- ⬜ **1.3** `application.yaml`: cấu hình `spring.datasource` (URL/user/pass từ env `POSTGRES_*`), `spring.jpa.hibernate.ddl-auto=validate` (KHÔNG để `update` — Flyway quản schema), `spring.flyway.enabled=true`.
- ⬜ **1.4** Tạo Flyway migration `src/main/resources/db/migration/V1__init_auth_schema.sql` từ `database/schema/V1__init_auth_schema.sql`. Migration trong `src/main/resources` là bản backend chạy thật; nếu giữ bản `database/schema`, cập nhật cùng commit để không drift.
- ⬜ **1.5** Thêm env vào `.env.example`: `POSTGRES_HOST/PORT/DB/USER/PASSWORD`, `JWT_SECRET` (≥32 ký tự), `JWT_EXPIRATION_MS`. Xác nhận `.env` trong `.gitignore`.
- ⬜ **1.6** Thêm PostgreSQL Testcontainers dependency/scope test nếu chưa có; cấu hình test profile để Flyway chạy trên container Postgres.

## 2. Mô hình dữ liệu (Postgres — đã thiết kế ở `database/`)

- ⬜ **2.1** JPA entity `User` (bảng `users`): id UUID, email, passwordHash, displayName, role, quotaBytes, usedBytes, timestamps.
- ⬜ **2.2** JPA entity `ProjectOwnership` (bảng `projects`): projectId (PK, = id Neo4j), ownerId (FK), name, sourceType, sizeBytes, status.
- ⬜ **2.3** JPA entity `UserIdentity` (bảng `user_identities`): id, userId (FK), provider (GOOGLE), providerUserId, email. Unique `(provider, providerUserId)`.
- ⬜ **2.3b** JPA entity `ApiKey` (bảng `api_keys`) — có thể để Phase 3, tạo entity trước cũng được.
- ⬜ **2.4** Khi tạo project (import-local/archive/github) → ghi 1 dòng vào `projects` với `owner_id = currentUser` (song song với ghi graph vào Neo4j).
- ⬜ **2.5** Migrate dữ liệu cũ: với mỗi `:Project` đang có trong Neo4j chưa có dòng ownership → tạo dòng `projects` gán `owner_id = admin`. (script 1 lần khi bật auth)
- ⬜ **2.6** Admin bootstrap cho migrate project cũ:
  - Đọc `ADMIN_EMAIL`, `ADMIN_PASSWORD` hoặc `ADMIN_PASSWORD_HASH` từ env ở dev/bootstrap.
  - Tạo admin nếu chưa tồn tại; nếu thiếu env và vẫn có project cũ cần owner → fail fast với thông báo rõ.
  - Script idempotent: chạy lại không tạo trùng user/project ownership.
- ⬜ **2.7** Đồng bộ status/name/size khi import/analyze/delete: trạng thái user nhìn thấy ưu tiên từ Postgres, graph detail vẫn đọc từ Neo4j khi cần.

> Ghi chú: ownership giờ là **nguồn sự thật ở Postgres** (`projects.owner_id`), KHÔNG lưu `ownerId` trên node Neo4j nữa — tránh 2 nguồn lệch nhau.

## 3. Backend — Auth core

- ⬜ **3.1** `UserRepository` (JPA/Postgres): `findByEmailIgnoreCase` hoặc query theo `lower(email)`, `save`, `findById`.
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
- ⬜ **4.5** Quyết định tạm cho `/ws/**` và `/mcp/**` trong Phase 1:
  - Nếu permit tạm để giữ demo, comment rõ trong `SecurityConfig` + ghi vào README/Phase 3 là dữ liệu realtime/MCP chưa multi-user safe.
  - Không permit các REST API project.

## 4b. Backend — Đăng nhập Google (OAuth)

> Làm SAU khi email+mật khẩu chạy ổn (dễ hơn, không phụ thuộc bên ngoài). Google chỉ để xác thực danh tính ban đầu; sau đó dùng JWT nội bộ như login thường.

- ⬜ **4b.1** Đăng ký OAuth Client trên Google Cloud Console → lấy `GOOGLE_CLIENT_ID`. Thêm vào `.env.example` (client id không phải secret khi dùng ID-token flow phía SPA, nhưng vẫn để env cho gọn).
- ⬜ **4b.2** `GoogleTokenVerifier`: verify Google **ID token** bằng `GoogleIdTokenVerifier` (kiểm chữ ký qua JWKS Google + đúng `aud = GOOGLE_CLIENT_ID` + issuer). Sai → 401.
- ⬜ **4b.3** `AuthService.loginWithGoogle(idToken)`:
  - verify → lấy `sub`, `email`, `emailVerified`, `name`, `picture`.
  - tra `user_identities (GOOGLE, sub)` → có thì lấy user; chưa thì tra `users.email`:
    - trùng email → thêm dòng `user_identities` (account linking).
    - chưa có → tạo `users` (password_hash NULL, email_verified theo Google, avatar_url = picture) + `user_identities`.
  - phát JWT nội bộ, trả `AuthResponse`.
- ⬜ **4b.4** `POST /api/auth/google` nhận `{ idToken }` → gọi service. Permit endpoint này trong SecurityConfig.
- ⬜ **4b.5** Lưu ý bảo mật: luôn verify `aud` + `iss` + hạn token; KHÔNG tin `email` nếu `email_verified=false` khi account-linking (tránh chiếm tài khoản qua email chưa xác thực).

## 5. Backend — Ownership check (chống IDOR, fix V2)

- ⬜ **5.1** Tạo `ProjectOwnershipGuard` (đặt ở `common/` hoặc `auth/ownership`): `assertOwner(projectId, currentUserId)` → `SELECT owner_id FROM projects WHERE project_id=?` (qua JPA repo), khác → ném `ForbiddenException` (403); không tồn tại → 404.
- ⬜ **5.2** Gọi guard ở **đầu mọi endpoint có projectId**:
  - `GET /api/projects/{id}`
  - `POST /api/projects/{id}/analyze`
  - `DELETE /api/projects/{id}`
  - `GET /api/projects/{projectId}/graph`
  - `GET /api/projects/{projectId}/graph/impact`
  - `GET /api/projects/{projectId}/graph/neighbors`
  - `GET /api/projects/{projectId}/graph/neighbors/{nodeId}`
  - `GET /api/projects/{projectId}/source`
  - `GET /api/projects/{projectId}/diagrams/usecase`
  - `GET /api/projects/{projectId}/diagrams/class`
- ⬜ **5.3** `GET /api/projects` chỉ trả project của current user: lấy danh sách `project_id` từ Postgres theo `owner_id`, rồi đọc metadata (kết hợp Neo4j nếu cần).
- ⬜ **5.4** Khi tạo project → ghi dòng ownership vào Postgres (mục 2.4) trong cùng luồng import. Áp dụng đủ:
  - `POST /api/projects`
  - `POST /api/projects/import-local`
  - `POST /api/projects/import-archive`
  - `POST /api/projects/import-github`
- ⬜ **5.5** `ForbiddenException` + mapping 403 (và 404 cho project lạ) trong `GlobalExceptionHandler`.
- ⬜ **5.6** Khi `DELETE` project → xoá cả dòng Postgres `projects` + graph Neo4j (đặt nền cho `purgeProject()` ở V15).
- ⬜ **5.7** `GET /api/projects/browse` phải yêu cầu authenticated ngay Phase 1. Nếu vẫn còn browse server-side, chỉ cho phép trong root đã cấu hình; Phase 2 sẽ thay bằng sandbox/user workspace.
- ⬜ **5.8** Thêm test "negative path": user A không thể đọc graph/source/diagram/analyze/delete project của user B.

## 6. Frontend — Auth

- ⬜ **6.1** `stores/auth.ts` (Pinia): state `token`/`user`, action `login/register/logout`, lưu token vào `localStorage`, khôi phục khi F5.
- ⬜ **6.2** Axios interceptor trong `lib/api.ts`: gắn `Authorization: Bearer <token>`; bắt 401 → logout + đẩy về `/login`.
- ⬜ **6.3** Trang `LoginView.vue` + `RegisterView.vue` (form email/mật khẩu, hiển thị lỗi rõ ràng, có label + validation).
- ⬜ **6.3b** Nút **"Đăng nhập với Google"** (Google Identity Services): lấy ID token → gọi `POST /api/auth/google` → lưu JWT như login thường. Cùng chỗ với form login.
- ⬜ **6.4** Router guard (`router/index.ts`): route cần đăng nhập → chưa có token thì chuyển `/login`; đã đăng nhập vào `/login` thì chuyển dashboard.
- ⬜ **6.5** Header UI: hiện email + nút Logout.

## 7. Test

- ⬜ **7.1** Unit: `JwtServiceTest` (phát→verify→hết hạn), `AuthServiceTest` (register trùng email, login sai mật khẩu).
- ⬜ **7.2** Unit: `ProjectOwnershipGuardTest` (đúng owner pass, sai owner ném 403).
- ⬜ **7.3** IT: `AuthApiIT` (register→login→me→gọi API có token OK, không token 401). Dùng PostgreSQL Testcontainers + Flyway.
- ⬜ **7.4** IT: `OwnershipApiIT` tạo user A/B, project A/B, xác nhận A không truy cập được project B qua get/graph/source/diagram/analyze/delete. Dùng PostgreSQL + Neo4j Testcontainers.
- ⬜ **7.5** IT: `ImportOwnershipIT` xác nhận import-local/import-archive/import-github tạo `projects.owner_id = currentUser`.
- ⬜ **7.5b** Unit/IT Google OAuth: mock/verifier trả `sub` → (a) lần đầu tạo user + identity, (b) lần hai reuse đúng user, (c) trùng email thì link vào user cũ (không tạo trùng), (d) `email_verified=false` không tự link.
- ⬜ **7.6** Test migrate/bootstrap: có project cũ trong Neo4j → tạo ownership cho admin; chạy lần 2 không đổi số dòng.
- ⬜ **7.7** Đảm bảo JaCoCo vẫn ≥ 70%.

## 7b. Ma trận endpoint bắt buộc test thủ công/curl

| Endpoint | Không token | Sai owner | Đúng owner |
|---|---:|---:|---:|
| `GET /api/projects` | 401 | chỉ thấy project của mình | 200 |
| `GET /api/projects/{id}` | 401 | 403 | 200 |
| `POST /api/projects/{id}/analyze` | 401 | 403 | 200/202 |
| `DELETE /api/projects/{id}` | 401 | 403 | 204 |
| `GET /api/projects/{id}/graph` | 401 | 403 | 200 |
| `GET /api/projects/{id}/graph/impact` | 401 | 403 | 200 |
| `GET /api/projects/{id}/source` | 401 | 403 | 200 |
| `GET /api/projects/{id}/diagrams/*` | 401 | 403 | 200/409 nếu chưa analyze |
| `POST /api/projects/import-*` | 401 | n/a | tạo owner đúng |

## 8. Hoàn tất

- ⬜ **8.1** Cập nhật `README.md` / `USER_GUIDE.md`: cách đăng ký/đăng nhập.
- ⬜ **8.2** `gitnexus_detect_changes` trước commit; tách commit hợp lý (be-auth, be-ownership, fe-auth, tests).
- ⬜ **8.3** Push nhánh `poc` (hoặc `feat/auth`), tạo PR nếu cần.

---

## Rủi ro & lưu ý

- **Realtime WebSocket** hiện chưa auth — Phase 1 chưa hoàn tất V6. Có 2 lựa chọn rõ ràng:
  - Làm luôn JWT CONNECT/SUBSCRIBE tối thiểu cho `/ws` nếu muốn môi trường shared an toàn.
  - Permit tạm `/ws/**` chỉ cho demo/local, comment rõ trong `SecurityConfig`, docs, và không đánh dấu multi-user realtime là xong cho đến Phase 3.
- **MCP `/mcp`** cũng chưa auth ở Phase 1 (V10/Phase 3). Nếu permit tạm để không vỡ demo, phải ghi rõ "chưa bảo vệ", không dùng cho dữ liệu user thật, và tạo task Phase 3 bắt buộc.
- Thứ tự đề xuất: **1 → 2 → 3 → 4 → 5 → 7 → 6**. Làm BE + Postgres/Neo4j test + curl matrix trước, rồi mới ghép FE để lỗi security không bị che bởi UI.
