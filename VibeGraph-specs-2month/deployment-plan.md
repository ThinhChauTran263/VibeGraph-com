# VibeGraph - Kế hoạch triển khai (Phạm vi 2 tháng)

## Mục tiêu

Đến cuối giai đoạn MVP, `vibegraph.tech` phục vụ frontend qua HTTPS, proxy lưu lượng API/WebSocket/MCP đến backend Spring Boot, và có thể cho người dùng upload một project Java bằng ZIP/TAR qua `POST /api/projects/import-archive` để ghi graph vào Neo4j. Import GitHub công khai là flow phụ dùng lại pipeline archive.

## Các cổng giai đoạn

| Tuần | Môi trường | URL | Yêu cầu |
|---|---|---|---|
| 2 | Local dev | `http://localhost:5173` hoặc Docker `http://localhost:3000` | Lát cắt dọc cục bộ hoạt động |
| 6 | Staging | `staging.vibegraph.tech` | Upload ZIP/TAR end-to-end, import GitHub phụ, sơ đồ, kiểm thử nhanh MCP |
| 8 | Production | `vibegraph.tech` | Ra mắt bản demo công khai |

`dev.vibegraph.tech` là tùy chọn. Đừng để việc triển khai domain chặn Sprint 1.

## Phát triển cục bộ

Repo gốc đã có sẵn đủ môi trường để khởi động Neo4j:

```powershell
docker compose up -d neo4j
docker compose ps neo4j
```

Chạy backend cục bộ:

```powershell
.\mvnw.cmd spring-boot:run
```

Chạy frontend cục bộ:

```powershell
cd vibegraph-web
npm install
npm run dev
```

## Hạ tầng production

- VPS: Hetzner CX22 hoặc tương đương, 4 GB RAM, 2 vCPU, tối thiểu 40 GB SSD.
- Domain: `vibegraph.tech`.
- DNS: Cloudflare hoặc tương đương.
- SSL: Let's Encrypt qua certbot.
- Cổng công khai: chỉ 22, 80, 443.

## Mẫu Production Docker Compose

Không duy trì một Compose production rút gọn riêng trong tài liệu này vì nó rất dễ lệch khỏi
`docker-compose.yml` chính (đặc biệt là PostgreSQL/Flyway, CLI device auth và API-key encryption).
Sử dụng file Compose ở repo làm nguồn chuẩn, rồi đặt các giá trị production trong `.env` của máy
triển khai:

```bash
cp .env.example .env
# Bắt buộc đổi các giá trị production:
# POSTGRES_*, NEO4J_*, JWT_SECRET, API_KEY_ENCRYPTION_KEY_CURRENT,
# FRONTEND_URL, OAUTH_REDIRECT_BASE_URL, GOOGLE_*/GITHUB_*
SPRING_PROFILES_ACTIVE=docker docker compose config --quiet
SPRING_PROFILES_ACTIVE=docker docker compose up -d --build
```

Compose hiện tại khởi động PostgreSQL, Neo4j, backend và frontend; Flyway tự áp dụng các migration
control-plane khi backend sẵn sàng. Backend không được expose trực tiếp ra Internet trong mô hình
reverse-proxy production; chỉ publish 80/443 ở Nginx/Cloudflare. `API_KEY_ENCRYPTION_KEY_CURRENT`
phải là Base64 của đúng 32 byte ngẫu nhiên. Giữ `API_KEY_ENCRYPTION_KEY_PREVIOUS` và
`API_KEY_ENCRYPTION_LEGACY_SECRET` trong thời gian chuyển khóa, sau đó xóa sau khi rewrap ciphertext.

Các biến CLI device và MCP retention có thể điều chỉnh mà không sửa image:

```dotenv
SERVER_BIND_ADDRESS=127.0.0.1
VIBEGRAPH_CLI_DEVICE_FRONTEND_URL=https://vibegraph.tech
VIBEGRAPH_TRUST_PROXY=true
# Proxy peer visible inside the backend container, not an end-user address.
# Confirm the gateway on the production host before deploy.
VIBEGRAPH_TRUSTED_PROXIES=172.18.0.1,127.0.0.1
VIBEGRAPH_CLI_DEVICE_TTL_SECONDS=600
VIBEGRAPH_CLI_DEVICE_POLL_INTERVAL_SECONDS=2
VIBEGRAPH_CLI_DEVICE_CLEANUP_CRON=0 20 3 * * ?
VIBEGRAPH_MCP_TASK_RETENTION_ENABLED=true
VIBEGRAPH_MCP_TASK_RETENTION_DAYS=90
VIBEGRAPH_MCP_TASK_RETENTION_BATCH_SIZE=500
VIBEGRAPH_MCP_TASK_RETENTION_MAX_BATCHES=20
VIBEGRAPH_MCP_TASK_RETENTION_CRON=0 15 3 * * ?
```

`SERVER_BIND_ADDRESS=127.0.0.1` applies when Caddy/Nginx runs on the VPS host and
connects to the published backend port. If the reverse proxy itself is a Compose
container and connects through the service network (`http://backend:8080`), use
`SERVER_BIND_ADDRESS=0.0.0.0` and set `VIBEGRAPH_TRUSTED_PROXIES` to that proxy
container's IP (or a small proxy-only CIDR) instead.

## Mẫu nginx

```nginx
server {
    listen 80;
    server_name vibegraph.tech;
    location /.well-known/acme-challenge/ { root /var/www/certbot; }
    location / { return 301 https://$host$request_uri; }
}

server {
    listen 443 ssl http2;
    server_name vibegraph.tech;
    ssl_certificate     /etc/letsencrypt/live/vibegraph.tech/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/vibegraph.tech/privkey.pem;

    location / {
        proxy_pass http://frontend:80;
    }

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws/graph-updates {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 3600s;
    }

    location /mcp {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
        proxy_read_timeout 3600s;
    }

    location /actuator/health {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## Thiết lập production lần đầu

```bash
ssh root@vibegraph.tech
apt update && apt install -y docker.io docker-compose-plugin git
git clone https://github.com/yourorg/vibegraph.git /opt/vibegraph
cd /opt/vibegraph
cp .env.example .env
# Set strong NEO4J_PASSWORD and GITHUB_TOKEN in .env

docker compose up -d nginx
docker compose run --rm certbot certonly --webroot \
  -w /var/www/certbot -d vibegraph.tech -m admin@vibegraph.tech --agree-tos

docker compose up -d --build
```

## Lệnh cập nhật

```bash
cd /opt/vibegraph
git pull
docker compose up -d --build
docker system prune -f
```

## Mục tiêu CI/CD

GitHub Actions cần chạy trước khi deploy:

- Backend unit tests: `./mvnw test`
- Backend full verification/CI, including `*IT` integration tests via Failsafe: `./mvnw verify`
- Frontend: `cd vibegraph-web && npm ci && npm run type-check && npm run test:unit -- --run && npm run build`
- Tùy chọn deploy qua SSH sau khi `main` vượt qua CI.

> Audit 2026-05-30: script `npm run lint` hiện chạy ESLint với `--fix`, tức có thể sửa file trong CI. Chỉ đưa lint vào CI sau khi thêm script không mutate (ví dụ `lint:check`).
> `FRONTEND_URL` và `VIBEGRAPH_CLI_DEVICE_FRONTEND_URL` phải cùng trỏ đến origin HTTPS phục vụ
> trang `/cli/authorize`; CORS production lấy origin từ `FRONTEND_URL`.

## Giám sát

- UptimeRobot kiểm tra `https://vibegraph.tech/actuator/health`.
- Log qua `docker compose logs -f backend frontend nginx`.
- Số liệu thông qua Spring Boot Actuator. Prometheus/Grafana là phần sau MVP.

## Checklist bảo mật

- [ ] `NEO4J_PASSWORD` ngẫu nhiên và mạnh.
- [ ] Các cổng Neo4j không công khai trong production.
- [ ] Cổng backend 8080 không công khai trong production.
- [ ] `/api/projects/import-archive` và `/api/projects/import-github` được giới hạn tần suất (rate-limit).
- [ ] Archive upload có giới hạn dung lượng, chống path traversal, symlink nguy hiểm và archive bomb trước khi expose public.
- [ ] Việc xác thực GitHub URL từ chối các host không phải GitHub.
- [ ] CORS chỉ cho phép origin production trong prod.
- [ ] Firewall chỉ mở 22, 80, và 443.
- [ ] `/mcp` yêu cầu project API key, kiểm tra ownership và áp dụng rate-limit/credit trước khi chạy tool.
- [ ] `API_KEY_ENCRYPTION_KEY_CURRENT` là khóa Base64 32-byte riêng, không dùng chung `JWT_SECRET`.
- [ ] `vibegraph login` mở đúng `https://vibegraph.tech/cli/authorize`, exchange credential một lần.

## Cấu hình MCP sau khi deploy

Không cấu hình `/mcp` trực tiếp mà thiếu authentication. Luồng khuyến nghị cho mọi IDE hỗ trợ
stdio là:

```bash
npm install -g vibegraph-cli
vibegraph config set-url https://vibegraph.tech
vibegraph login
vibegraph mcp install cursor
# Hoặc: vibegraph mcp install vscode
# Hoặc IDE khác: vibegraph mcp install generic --path /path/to/mcp.json
```

`vibegraph mcp config` in ra JSON để copy/paste cho IDE không có preset. Cấu hình tạo ra chạy
`vibegraph mcp-proxy --stdio`, đọc key từ kho cấu hình user của CLI và không nhúng raw key vào repo.
Client chỉ hỗ trợ Streamable HTTP phải lấy key từ secret manager/environment và gửi header
`X-API-Key`; giá trị này là project API key của đúng project muốn dùng, không phải OAuth secret
hoặc JWT secret. Muốn đổi project ở chế độ JSON HTTP trực tiếp thì thay bằng project key mới và
reload MCP trong IDE. Với CLI proxy, chạy `vibegraph key change`, chọn project mới rồi reload MCP;
không phải sửa JSON. Không commit key raw vào `mcp.json`.

## Ước tính chi phí

| Hạng mục | Hàng tháng |
|---|---|
| VPS | ~$7 |
| Domain phân bổ | ~$1 |
| Cloudflare DNS | $0 |
| Let's Encrypt SSL | $0 |
| **Tổng** | **~$8/tháng** |
