# VibeGraph - Kế hoạch triển khai (Phạm vi 2 tháng)

## Mục tiêu

Đến cuối giai đoạn MVP, `vibegraph.com` phục vụ frontend qua HTTPS, proxy lưu lượng API/WebSocket/MCP đến backend Spring Boot, và có thể import một repo Java công khai trên GitHub vào Neo4j.

## Các cổng giai đoạn

| Tuần | Môi trường | URL | Yêu cầu |
|---|---|---|---|
| 2 | Local dev | `http://localhost:5173` hoặc Docker `http://localhost:3000` | Lát cắt dọc cục bộ hoạt động |
| 6 | Staging | `staging.vibegraph.com` | Import GitHub, sơ đồ, kiểm thử nhanh MCP |
| 8 | Production | `vibegraph.com` | Ra mắt bản demo công khai |

`dev.vibegraph.com` là tùy chọn. Đừng để việc triển khai domain chặn Sprint 1.

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
- Domain: `vibegraph.com`.
- DNS: Cloudflare hoặc tương đương.
- SSL: Let's Encrypt qua certbot.
- Cổng công khai: chỉ 22, 80, 443.

## Mẫu Production Docker Compose

Backend build từ thư mục gốc của repo, không phải `./vibegraph-server`.

```yaml
services:
  neo4j:
    image: neo4j:5-community
    restart: unless-stopped
    environment:
      NEO4J_AUTH: neo4j/${NEO4J_PASSWORD}
      NEO4J_PLUGINS: '["apoc"]'
      NEO4J_dbms_memory_heap_max__size: 1G
      NEO4J_dbms_memory_pagecache_size: 512M
    volumes:
      - neo4j-data:/data
      - neo4j-logs:/logs
    networks: [internal]

  backend:
    build:
      context: .
      dockerfile: Dockerfile
    restart: unless-stopped
    depends_on: [neo4j]
    environment:
      SPRING_PROFILES_ACTIVE: prod
      NEO4J_URI: bolt://neo4j:7687
      NEO4J_USERNAME: neo4j
      NEO4J_PASSWORD: ${NEO4J_PASSWORD}
      GITHUB_TOKEN: ${GITHUB_TOKEN}
      JAVA_TOOL_OPTIONS: "-Xmx1g"
    networks: [internal]

  frontend:
    build:
      context: ./vibegraph-web
      dockerfile: Dockerfile
    restart: unless-stopped
    networks: [internal]

  nginx:
    image: nginx:alpine
    restart: unless-stopped
    ports: ["80:80", "443:443"]
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./certbot/conf:/etc/letsencrypt:ro
      - ./certbot/www:/var/www/certbot:ro
    depends_on: [backend, frontend]
    networks: [internal, web]

  certbot:
    image: certbot/certbot
    volumes:
      - ./certbot/conf:/etc/letsencrypt
      - ./certbot/www:/var/www/certbot
    entrypoint: "/bin/sh -c 'trap exit TERM; while :; do certbot renew; sleep 12h & wait $${!}; done;'"

volumes:
  neo4j-data:
  neo4j-logs:

networks:
  internal:
  web:
```

## Mẫu nginx

```nginx
server {
    listen 80;
    server_name vibegraph.com;
    location /.well-known/acme-challenge/ { root /var/www/certbot; }
    location / { return 301 https://$host$request_uri; }
}

server {
    listen 443 ssl http2;
    server_name vibegraph.com;
    ssl_certificate     /etc/letsencrypt/live/vibegraph.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/vibegraph.com/privkey.pem;

    location / {
        proxy_pass http://frontend:80;
    }

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /ws/graph-updates {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
    }

    location /mcp {
        proxy_pass http://backend:8080;
        proxy_buffering off;
        proxy_read_timeout 3600s;
    }

    location /actuator/health {
        proxy_pass http://backend:8080;
    }
}
```

## Thiết lập production lần đầu

```bash
ssh root@vibegraph.com
apt update && apt install -y docker.io docker-compose-plugin git
git clone https://github.com/yourorg/vibegraph.git /opt/vibegraph
cd /opt/vibegraph
cp .env.example .env
# Set strong NEO4J_PASSWORD and GITHUB_TOKEN in .env

docker compose up -d nginx
docker compose run --rm certbot certonly --webroot \
  -w /var/www/certbot -d vibegraph.com -m admin@vibegraph.com --agree-tos

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
> Cấu hình prod hiện có default `CORS_ALLOWED_ORIGINS=https://vibegraph.io` trong `application-prod.yaml`; trước khi deploy `vibegraph.com` phải set env đúng domain hoặc đổi default để khớp tài liệu này.

## Giám sát

- UptimeRobot kiểm tra `https://vibegraph.com/actuator/health`.
- Log qua `docker compose logs -f backend frontend nginx`.
- Số liệu thông qua Spring Boot Actuator. Prometheus/Grafana là phần sau MVP.

## Checklist bảo mật

- [ ] `NEO4J_PASSWORD` ngẫu nhiên và mạnh.
- [ ] Các cổng Neo4j không công khai trong production.
- [ ] Cổng backend 8080 không công khai trong production.
- [ ] `/api/projects/import-github` được giới hạn tần suất (rate-limit).
- [ ] Việc xác thực GitHub URL từ chối các host không phải GitHub.
- [ ] CORS chỉ cho phép origin production trong prod.
- [ ] Firewall chỉ mở 22, 80, và 443.
- [ ] Endpoint MCP có quyết định về auth/rate-limit trước khi ra mắt công khai.

## Cấu hình MCP sau khi deploy

```json
{
  "mcpServers": {
    "vibegraph": {
      "url": "https://vibegraph.com/mcp",
      "transport": "streamable-http"
    }
  }
}
```

Phiên bản cục bộ:

```json
{
  "mcpServers": {
    "vibegraph": {
      "url": "http://localhost:8080/mcp",
      "transport": "streamable-http"
    }
  }
}
```

## Ước tính chi phí

| Hạng mục | Hàng tháng |
|---|---|
| VPS | ~$7 |
| Domain phân bổ | ~$1 |
| Cloudflare DNS | $0 |
| Let's Encrypt SSL | $0 |
| **Tổng** | **~$8/tháng** |
