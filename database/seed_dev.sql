-- =====================================================================
-- seed_dev.sql — dữ liệu mẫu cho DEV (KHÔNG dùng cho production)
-- =====================================================================
-- Chạy sau khi schema đã tạo:
--   psql postgresql://vibegraph:vibegraph@localhost:5432/vibegraph -f seed_dev.sql
--
-- LƯU Ý về mật khẩu:
--   password_hash phải là BCrypt hash THẬT. Không tự bịa chuỗi.
--   Cách lấy hash cho mật khẩu "admin123":
--     - Để backend tự tạo admin lúc khởi động (khuyến nghị — bootstrap trong code), HOẶC
--     - Sinh bằng công cụ BCrypt bất kỳ rồi dán vào dưới.
--   Hash ví dụ dưới đây là PLACEHOLDER — THAY trước khi dùng, nếu không sẽ không đăng nhập được.
-- =====================================================================

INSERT INTO users (email, password_hash, display_name, role)
VALUES (
    'admin@vibegraph.local',
    '$2a$10$REPLACE_ME_WITH_A_REAL_BCRYPT_HASH_00000000000000000000',  -- << THAY
    'Admin',
    'ADMIN'
)
ON CONFLICT DO NOTHING;

-- Gán mọi project cũ (chưa có chủ) cho admin — chạy SAU khi đã có bảng projects
-- và đã đồng bộ project_id từ Neo4j sang (thường do backend làm khi migrate).
-- Ví dụ minh hoạ (bỏ comment khi cần):
-- UPDATE projects SET owner_id = (SELECT id FROM users WHERE email = 'admin@vibegraph.local')
-- WHERE owner_id IS NULL;
