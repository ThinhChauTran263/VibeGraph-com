# VibeGraph — Database (Postgres, control plane)

Thư mục này chứa **CSDL quan hệ Postgres** cho tầng điều khiển của VibeGraph:
tài khoản người dùng, quyền sở hữu project, quota, API key.

> Đồ thị mã nguồn vẫn nằm ở **Neo4j** (không đổi). Postgres chỉ lo **ai là ai / ai sở hữu gì** (auth + ownership). Đây là tách bạch "control plane (Postgres) vs data plane (Neo4j)".

## Nội dung thư mục

| File | Vai trò |
|------|---------|
| `schema/V1__init_auth_schema.sql` | Toàn bộ bảng: `users`, `projects`, `api_keys`. Cũng là Flyway migration V1. |
| `docker-compose.postgres.yml` | Chạy Postgres cục bộ trên máy dev bằng Docker. |
| `ERD.md` | Sơ đồ quan hệ + giải thích từng bảng/cột. |
| `seed_dev.sql` | (tuỳ chọn) dữ liệu mẫu cho dev. |

## Team chạy trên máy mình — 3 bước

```bash
# 1) (tuỳ chọn) đặt biến — nếu bỏ qua sẽ dùng mặc định vibegraph/vibegraph
#    tạo file .env cạnh file compose hoặc export biến môi trường:
#    POSTGRES_USER=vibegraph  POSTGRES_PASSWORD=vibegraph  POSTGRES_DB=vibegraph

# 2) Bật Postgres
cd database
docker compose -f docker-compose.postgres.yml up -d

# 3) Kiểm tra sống
docker exec -it vibegraph-postgres pg_isready -U vibegraph
#   hoặc kết nối:  psql postgresql://vibegraph:vibegraph@localhost:5432/vibegraph
```

Kết nối mặc định: `postgresql://vibegraph:vibegraph@localhost:5432/vibegraph`

## Ai tạo bảng? (đọc kỹ để khỏi lỗi)

Có 2 cách, **chỉ chọn 1**:

1. **Flyway trong backend (khuyến nghị).** Khi backend khởi động, Flyway đọc
   `src/main/resources/db/migration/V1__init_auth_schema.sql` (bản copy của file trong
   `schema/`) và tự tạo bảng. → Cứ để Postgres trống, chạy backend là xong.
2. **Postgres tự chạy schema lúc init.** Bỏ comment dòng mount `./schema` trong
   `docker-compose.postgres.yml`. Postgres chạy `*.sql` **1 lần khi tạo volume mới**.
   Dùng khi muốn có DB sẵn bảng mà chưa chạy backend.

> ⚠️ ĐỪNG bật cả hai. Nếu Postgres đã có bảng sẵn rồi Flyway lại chạy V1 → Flyway
> báo lỗi "bảng đã tồn tại". Chọn Flyway thì để initdb tắt, và ngược lại.

## Biến môi trường backend cần (thêm vào `.env` gốc dự án)

```properties
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=vibegraph
POSTGRES_USER=vibegraph
POSTGRES_PASSWORD=vibegraph
# JWT
JWT_SECRET=doi-thanh-chuoi-ngau-nhien-tren-32-ky-tu
JWT_ACCESS_EXPIRATION_MS=1800000
JWT_REFRESH_EXPIRATION_MS=604800000
AUTH_COOKIE_SECURE=true
```

## Đổi schema về sau

- Không sửa trực tiếp `V1__...`. Thêm file mới `V2__ten_thay_doi.sql` (quy tắc Flyway).
- Giữ `database/schema/` và `src/main/resources/db/migration/` **giống nhau**.
