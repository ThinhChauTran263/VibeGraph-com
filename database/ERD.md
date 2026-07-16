# ERD — Postgres (control plane)

```
                          ┌───────────────────────────┐
                          │           users           │
                          ├───────────────────────────┤
                          │ PK id            UUID      │
                          │    email         (unique)  │
                          │    password_hash  BCrypt?  │  ← NULL nếu chỉ đăng nhập OAuth
                          │    email_verified BOOL     │
                          │    avatar_url              │
                          │    display_name            │
                          │    role          USER/ADMIN│
                          │    quota_bytes / used_bytes│
                          │    created_at / updated_at │
                          └────┬───────────┬───────┬───┘
              1               │ 1         │ 1     │ 1
      ┌───────────────────────┘           │       └─────────────────┐
      │ N                            N     │                    N    │
┌─────┴──────────────┐   ┌────────────────┴─────────┐   ┌───────────┴───────────────┐
│  user_identities   │   │        projects          │   │         api_keys          │
├────────────────────┤   ├──────────────────────────┤   ├───────────────────────────┤
│ PK id      UUID     │   │ PK project_id (= Neo4j id)│   │ PK id          UUID        │
│ FK user_id          │   │ FK owner_id → users.id    │   │ FK user_id → users.id      │
│    provider GOOGLE  │   │    name / source_type     │   │    key_hash    (unique)    │
│    provider_user_id │   │    size_bytes / status    │   │    prefix / name           │
│    email            │   │    created_at / updated_at│   │    expires_at / revoked_at │
│  UNIQUE(provider,   │   └──────────────────────────┘   │    last_used_at            │
│         provider_uid)│                                  └───────────────────────────┘
└────────────────────┘
```

## Quan hệ
- **users 1 — N user_identities**: một user link nhiều nhà cung cấp OAuth (Google, sau này GitHub). Đăng nhập local (mật khẩu) KHÔNG nằm bảng này — dùng `users.password_hash`.
- **users 1 — N projects**: một user sở hữu nhiều project. Xoá user → `ON DELETE CASCADE` xoá luôn bản ghi ownership project của họ (dữ liệu graph trong Neo4j phải purge riêng, xem V15).
- **users 1 — N api_keys**: một user có nhiều API key (CLI, MCP). Xoá user → xoá key.

## Đăng nhập bằng Google — luồng dữ liệu
1. FE lấy **Google ID token** (Google Identity Services), gửi backend `POST /api/auth/google`.
2. Backend verify ID token với Google (JWKS), đọc `sub` (id Google) + `email` + `email_verified` + `name` + `picture`.
3. Tra `user_identities` theo `(provider='GOOGLE', provider_user_id=sub)`:
   - **Có** → lấy `user_id` tương ứng → phát JWT của VibeGraph.
   - **Chưa** → tra `users` theo `email`:
     - Có user cùng email → **link**: thêm dòng `user_identities` trỏ vào user đó (account linking).
     - Chưa có → tạo `users` mới (password_hash = NULL, email_verified = giá trị Google) + dòng `user_identities`.
4. Sau đó mọi thứ giống hệt login thường: trả JWT VibeGraph, FE dùng như bình thường.

> Điểm quan trọng: **sau bước 3, hệ thống chỉ dùng JWT nội bộ của VibeGraph** — Google chỉ để xác thực danh tính ban đầu. Ownership/quota/API key không đổi.

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
