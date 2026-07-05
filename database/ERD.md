# ERD — Postgres (control plane)

```
┌───────────────────────────┐
│           users           │
├───────────────────────────┤
│ PK id            UUID      │
│    email         (unique)  │
│    password_hash  BCrypt   │
│    display_name            │
│    role          USER/ADMIN│
│    quota_bytes   BIGINT    │
│    used_bytes    BIGINT    │
│    created_at / updated_at │
└──────────┬────────────────┘
           │ 1
           │
     ┌─────┴───────┐  N            ┌───────────────────────────┐
     │             ├──────────────▶│         projects          │
     │             │  owner_id     ├───────────────────────────┤
     │             │               │ PK project_id  (= Neo4j id)│
     │             │               │ FK owner_id → users.id     │
     │             │               │    name / source_type      │
     │             │               │    size_bytes / status     │
     │             │               │    created_at / updated_at │
     │             │               └───────────────────────────┘
     │ 1           │  N            ┌───────────────────────────┐
     │             └──────────────▶│         api_keys          │
     │                 user_id     ├───────────────────────────┤
     │                             │ PK id          UUID        │
     │                             │ FK user_id → users.id      │
     │                             │    key_hash    (unique)    │
     │                             │    prefix / name           │
     │                             │    expires_at / revoked_at │
     │                             │    last_used_at            │
     └─────────────────────────────└───────────────────────────┘
```

## Quan hệ
- **users 1 — N projects**: một user sở hữu nhiều project. Xoá user → `ON DELETE CASCADE` xoá luôn bản ghi ownership project của họ (dữ liệu graph trong Neo4j phải purge riêng, xem V15).
- **users 1 — N api_keys**: một user có nhiều API key (CLI, MCP). Xoá user → xoá key.

## Cầu nối Postgres ↔ Neo4j
`projects.project_id` **trùng đúng** id của node `:Project` trong Neo4j.
- Postgres = "ai sở hữu project này" (nguồn sự thật cho ownership).
- Neo4j = nội dung đồ thị của project đó.
- Ownership check: mọi API có `projectId` → `SELECT owner_id FROM projects WHERE project_id = ?`
  → so với user hiện tại; khác → 403.

## Ghi chú thiết kế
- **Không lưu bí mật thô**: `password_hash` là BCrypt; `api_keys.key_hash` là hash — key thô chỉ hiện 1 lần lúc tạo.
- **Quota**: `users.used_bytes` cộng dồn `projects.size_bytes`; kiểm `used + incoming <= quota` trước khi nhận import (V5). Có thể để Phase 2.
- **email** unique không phân biệt hoa/thường (index trên `lower(email)`).
- **Thời gian** dùng `TIMESTAMPTZ` (có timezone) — chuẩn cho app đa múi giờ.
- `status` project đồng bộ với trạng thái phân tích ở Neo4j (ANALYZING/ANALYZED/FAILED).

## Bảng có thể thêm sau (chưa cần Phase 1)
- `refresh_tokens` — nếu làm refresh token thay vì JWT hết hạn ngắn.
- `audit_log` — ghi ai gọi gì lúc nào (V16 giám sát vận hành).
- `teams` / `team_members` — nếu làm tính năng nhóm (lúc đó Postgres phát huy thế mạnh quan hệ).
