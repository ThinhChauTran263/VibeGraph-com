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
| OAuth GitHub | Phase 2 (tùy chọn) |
| Redis | ❌ chưa — chỉ cần khi scale nhiều instance |

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

---

## Bản đồ pha

| Pha | Mục tiêu | Fix lỗ hổng | Trạng thái |
|---|---|---|---|
| **Phase 1** | Đăng ký/đăng nhập + ownership check (chống IDOR) | V1, V2 | ⬜ chưa bắt đầu |
| **Phase 2** | Sandbox theo user + quota | V3, V4, V5 | ⬜ |
| **Phase 3** | Auth cho WebSocket + MCP theo user | V6, V10 | ⬜ |
| **Phase 4** | CLI `vibegraph` (login + push) | (đưa code lên server) | ⬜ |
| **Phase 5** | Hardening: TLS/headers/CORS/CSRF/rate-limit, quét secret, xoá sạch, giám sát | V7–V9, V11–V16 | ⬜ |

Chi tiết task từng pha:
- `phase-1-auth.md` — **làm trước, ưu tiên cao nhất** (đúng thứ hội đồng yêu cầu)
- `phase-2-5-backlog.md` — các pha sau

---

## Cách dùng tài liệu

- Mỗi task có checkbox `⬜/✅`. Làm xong tick lại.
- Theo AGENTS.md: chạy `gitnexus_impact` trước khi sửa symbol, `gitnexus_detect_changes` trước khi commit.
- Mỗi task nên là 1 commit nhỏ, nhánh `poc` (hoặc nhánh feature `feat/auth`).
