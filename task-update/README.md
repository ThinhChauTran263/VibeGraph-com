# VibeGraph — Kế hoạch nâng cấp sau Review Giai đoạn 1

**Bối cảnh:** Đã qua review giai đoạn 1 (đánh giá tốt). Hội đồng khuyến nghị **bổ sung xác thực + người dùng đăng nhập**. Tài liệu này gom toàn bộ việc đã tư vấn thành kế hoạch có thứ tự, chia pha, để thực hiện tuần tự.

**Nguồn gốc yêu cầu:** khớp `VibeGraph-specs-2month/security-multiuser-roadmap.md` (V1–V16). Tài liệu này là bản "biến roadmap thành task thực thi".

---

## Stack đã chốt

| Hạng mục | Dùng |
|---|---|
| Bảo mật backend | Spring Security (`spring-boot-starter-security`) |
| Token | JWT HS256 tự phát qua **jjwt** (`io.jsonwebtoken`) |
| Băm mật khẩu | BCrypt (`BCryptPasswordEncoder`) |
| Lưu user/ownership/quota/API key | **PostgreSQL** + Spring Data JPA (control plane) |
| Migration Postgres | **Flyway** (`flyway-core`) |
| Đồ thị code | **Neo4j** giữ nguyên (data plane) |
| Cache (khi cần) | **Caffeine** in-memory (Spring Cache) — KHÔNG Redis |
| FE | Pinia store `auth` + axios interceptor + vue-router guard (lib đã có) |
| WebSocket/MCP auth | cùng JWT, gắn vào STOMP connectHeaders / header MCP |
| **OAuth Google** | **Phase 1 (thêm)** — FE lấy Google ID token → BE verify (Google JWKS) → phát JWT nội bộ. Lib: `google-api-client` (GoogleIdTokenVerifier) |
| OAuth GitHub | Phase 3+ (tùy chọn) — dùng lại bảng `user_identities`, không đổi schema |
| Redis | ❌ chưa — chỉ cần khi scale nhiều instance |

> **Đăng nhập đa phương thức:** local (email + mật khẩu) **và** Google cùng tồn tại. Schema tách bảng `user_identities` để 1 user link nhiều provider + account-linking theo email (xem `database/ERD.md`). `users.password_hash` NULLABLE (user chỉ dùng Google thì không có mật khẩu).

## Tiêu chuẩn 10/10 trước khi triển khai

Kế hoạch được xem là "sẵn sàng giao dev" khi thỏa các điểm sau:

- **Không mâu thuẫn nguồn dữ liệu:** user, ownership, quota, API key nằm ở Postgres/JPA; Neo4j chỉ giữ graph code và metadata cần cho phân tích.
- **Mọi đường vào project đều có chủ sở hữu:** create/import-local/import-archive/import-github phải tạo hoặc cập nhật dòng `projects.owner_id`.
- **Mọi đường đọc/sửa/xóa theo `projectId` đều qua ownership guard:** REST, diagram, source viewer, MCP tool, WebSocket subscribe.
- **Migration dữ liệu cũ idempotent:** project cũ trong Neo4j được gán cho admin bootstrap đúng 1 lần, chạy lại không tạo trùng.
- **Test có cả Postgres và Neo4j:** auth/ownership dùng PostgreSQL Testcontainers; graph/import vẫn dùng Neo4j Testcontainers.
- **Không có "permit tạm" âm thầm:** nếu Phase 1 tạm permit `/ws` hoặc `/mcp`, phải ghi rõ trong config, docs, và backlog Phase 3.
- **Rollback/dev ergonomics rõ:** `.env.example`, Docker Compose, Flyway, healthcheck, và tài liệu chạy local phải đồng bộ.

**Tổ chức mã (Mức 1 — tách package, cùng 1 app):**
```
com.vibegraph
├── auth/     ← MỚI: controller/service/repository/entity/config cho auth (Postgres/JPA)
├── graph/    ← VibeGraph hiện tại (Neo4j), KHÔNG đụng logic
├── parser/  mcp/  diagram/
└── common/   ← dùng chung: ownership guard, exception, config
```
- 1 app · 1 lần deploy · 1 JVM (không tách microservice).
- **Control plane (Postgres) tách data plane (Neo4j)** rõ ràng bằng package + bằng DB.
- Cân nhắc thêm rule **ArchUnit**: cấm `graph` phụ thuộc ngược vào `auth`.

**Schema DB:** đã thiết kế sẵn ở thư mục **`database/`** (team `docker compose` là có Postgres chạy).

**Nguyên tắc:** làm theo pha, mỗi pha chạy được + test được rồi mới sang pha sau. Không ôm hết một lần.

**Quy tắc nguồn schema:** Flyway migration trong `src/main/resources/db/migration/` là nguồn chạy thật của backend. Bản trong `database/schema/` chỉ dùng làm tài liệu/tham khảo hoặc phải được cập nhật cùng commit để tránh drift.

---

## Bản đồ pha

| Pha | Mục tiêu | Fix lỗ hổng | Trạng thái |
|---|---|---|---|
| **Phase 1** | Đăng ký/đăng nhập + ownership check (chống IDOR) | V1, V2 | ⬜ chưa bắt đầu |
| **Phase 2** | Sandbox theo user + quota | V3, V4, V5 | ⬜ |
| **Phase 3** | Auth cho WebSocket + MCP theo user | V6, V10 | ⬜ |
| **Phase 4** | CLI `vibegraph` (login + push) | (đưa code lên server) | ⬜ |
| **Phase 5** | Hardening: TLS/headers/CORS/CSRF/rate-limit, quét secret, xoá sạch, giám sát | V7–V9, V11–V16 | ⬜ |

## Cổng nghiệm thu từng pha

- **Phase 1 pass:** register/login/me hoạt động; mọi REST endpoint có `projectId` trả 401/403/404 đúng; import mới gắn owner; project cũ gán admin; FE login/register/router guard chạy; `mvnw verify` pass với PostgreSQL + Neo4j Testcontainers.
- **Phase 2 pass:** không còn browse ổ đĩa host cho user thường; mọi file nằm dưới `<storage-root>/<userId>/<projectId>`; quota và file cap trả lỗi đúng.
- **Phase 3 pass:** WebSocket CONNECT/SUBSCRIBE và MCP tool đều xác thực, dùng chung ownership guard, không còn permit tạm cho dữ liệu user.
- **Phase 4 pass:** CLI login/push/analyze dùng API key hash/rotate/revoke, có quota và ownership.
- **Phase 5 pass:** hardening, secret masking, audit log, backup/restore drill, purge account/project có test.

Chi tiết task từng pha:
- `phase-1-auth.md` — **làm trước, ưu tiên cao nhất** (đúng thứ hội đồng yêu cầu)
- `phase-2-5-backlog.md` — các pha sau

---

## Cách dùng tài liệu

- Mỗi task có checkbox `⬜/✅`. Làm xong tick lại.
- Theo AGENTS.md: chạy `gitnexus_impact` trước khi sửa symbol, `gitnexus_detect_changes` trước khi commit.
- Mỗi task nên là 1 commit nhỏ, nhánh `poc` (hoặc nhánh feature `feat/auth`).
