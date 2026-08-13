# Báo cáo kiểm chứng B-M11 và H13

Ngày kiểm chứng: 12/08/2026  
Phạm vi: chỉ rà soát tĩnh và runtime; không sửa mã nguồn, không commit.

| Test | Kết luận | Bằng chứng | Số liệu |
|---|---|---|---|
| V1.1 (B-M11 tĩnh) | `CONFIRMED` | `V1-static.txt`; `Neo4jGraphRepository` gọi `session.run` theo từng nhóm label/type, không có `executeWrite`/transaction; nhánh FAILED gọi `cleanup(..., null)` nên giữ project/graph đã ghi | Số câu lệnh mỗi analysis = 1 project + số label node khác nhau + số relationship type khác nhau |
| V1.2 (B-M11 runtime) | `BLOCKED` | `V1-runtime-cypher.txt`; DB hiện không có project FAILED để đối chiếu; không kill backend đang dùng | FAILED = 0; Neo4j có 13 project với 1-5742 node và 0-16323 edge |
| V2.2 (H13 timing) | `CONFIRMED` | `V2-timing.txt`; đo đúng route `/api/projects/431ee9dc/patch`, header `X-API-Key`, 30 mẫu/nhóm | Median A/B/C = 4,250 / 4,640 / 54,840 ms; C-B = +50,200 ms; 90/90 trả 401, 0 trả 429 |
| V2.3 (H13 thứ tự filter) | `CONFIRMED` | `V2-filter-order.txt`; API-key filter chạy BCrypt cho candidate trùng prefix; `SecurityConfig` không đặt rate-limit trước API-key filter | API key sau `UsernamePasswordAuthenticationFilter`; rate-limit chỉ được ràng buộc trước `AuthorizationFilter` |

## Môi trường

- Frontend `http://localhost:5173`: HTTP 200; thao tác API key bằng Chrome DevTools trong browser context riêng.
- Backend `http://localhost:8080/actuator/health`: HTTP 200.
- Docker: backend, PostgreSQL và Neo4j đều healthy trong thời gian kiểm chứng.
- API key test `runtime-h13-20260812` đã xóa qua UI; PostgreSQL xác nhận `deleted_at IS NOT NULL`.
- Không tạo project test cho V1.2, không xóa project có sẵn và không dọn thư mục uploads.

B-M11: xác nhận bằng phân tích tĩnh; runtime bị chặn do không có FAILED project và không chủ động kill backend dùng chung.  
H13: xác nhận runtime; key sai nhưng trùng prefix làm tăng median khoảng 50,2 ms/request trước khi nhận 401.
