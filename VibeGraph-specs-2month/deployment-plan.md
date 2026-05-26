# VibeGraph — Deployment Plan (2-Month Scope)

## Mục tiêu

Sau 8 tuần: `vibegraph.com` chạy public, AI tool kết nối MCP qua URL public, user paste GitHub URL → thấy graph.

---

## Stage gate

| Tuần | Môi trường | URL | Trạng thái |
|---|---|---|---|
| 2 | Dev | `dev.vibegraph.com` | Internal team test |
| 6 | Staging | `staging.vibegraph.com` | Beta tester mời (3-5 người) |
| 8 | Production | `vibegraph.com` | Public launch |

---

## Hạ tầng tối thiểu

### VPS — Hetzner CX22
- 4GB RAM, 2 vCPU, 40GB SSD, 20TB traffic
- Giá: ~$5-7/tháng
- Location: Nuremberg (gần VN ping ~250ms) hoặc Helsinki

### Domain
- `vibegraph.com` — Namecheap/Cloudflare Registrar ~$10-12/năm
- DNS qua Cloudflare (free, có DDoS protection cơ bản)

### SSL
- Let's Encrypt qua certbot trong docker-compose
- Auto-renew mỗi 60 ngày

---

## Production docker-compose.yml

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
    build: ./vibegraph-server
    restart: unless-stopped
    depends_on: [neo4j]
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_NEO4J_URI: bolt://neo4j:7687
      SPRING_NEO4J_AUTHENTICATION_USERNAME: neo4j
      SPRING_NEO4J_AUTHENTICATION_PASSWORD: ${NEO4J_PASSWORD}
      VIBEGRAPH_GITHUB_TEMP_DIR: /tmp/vibegraph
      JAVA_TOOL_OPTIONS: "-Xmx1g -XX:+UseZGC"
    volumes:
      - github-temp:/tmp/vibegraph
    networks: [internal]

  frontend:
    build: ./vibegraph-web
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
  github-temp:

networks:
  internal:
  web:
```

## nginx.conf (rút gọn)

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

    # Frontend SPA
    location / {
        proxy_pass http://frontend:80;
    }

    # Backend API
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # WebSocket
    location /ws/ {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # MCP endpoint
    location /mcp {
        proxy_pass http://backend:8080;
        proxy_buffering off;            # Streamable HTTP cần unbuffered
        proxy_read_timeout 3600s;
    }
}
```

---

## Lệnh deploy production

```bash
# Lần đầu setup VPS
ssh root@vibegraph.com
apt update && apt install -y docker.io docker-compose-plugin git
git clone https://github.com/yourorg/vibegraph.git /opt/vibegraph
cd /opt/vibegraph
cp .env.example .env && vi .env    # set NEO4J_PASSWORD

# Lần đầu lấy SSL cert
docker compose up -d nginx
docker compose run --rm certbot certonly --webroot \
  -w /var/www/certbot -d vibegraph.com -m admin@vibegraph.com --agree-tos

# Chạy full stack
docker compose up -d

# Update sau khi push code mới
git pull && docker compose up -d --build
```

---

## CI/CD — GitHub Actions

```yaml
# .github/workflows/deploy.yml
name: Deploy
on:
  push:
    branches: [main]
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VPS_HOST }}
          username: deploy
          key: ${{ secrets.VPS_SSH_KEY }}
          script: |
            cd /opt/vibegraph
            git pull
            docker compose up -d --build
            docker system prune -f
```

---

## Monitoring (đơn giản, miễn phí)

- **Uptime:** UptimeRobot (free, ping `/actuator/health` mỗi 5 phút)
- **Logs:** `docker compose logs -f` ssh xem trực tiếp
- **Metrics:** Spring Boot Actuator `/actuator/metrics` (defer Grafana/Prometheus sau 2 tháng)

---

## Security checklist

- [ ] `NEO4J_PASSWORD` random 32-char
- [ ] Neo4j không expose port ra ngoài (chỉ internal network)
- [ ] Backend không expose port 8080 ra ngoài (qua nginx)
- [ ] Rate limit endpoint `/api/projects/import-github` (max 5 req/min/IP)
- [ ] Validate GitHub URL pattern, reject non-github.com
- [ ] CORS chỉ allow `vibegraph.com`
- [ ] Firewall: chỉ mở 80, 443, 22

---

## MCP integration sau deploy

User config trong Cursor/Claude Code:

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

(Bản local: thay `https://vibegraph.com` thành `http://localhost:8080`.)

---

## Cost estimate

| Item | Monthly |
|---|---|
| Hetzner CX22 VPS | $7 |
| Domain (amortized) | $1 |
| Cloudflare DNS | $0 |
| Let's Encrypt SSL | $0 |
| Email transactional (chưa cần) | $0 |
| **Total** | **~$8/tháng** |

Khi có user thật + auth + Stripe (post-2-month) cộng thêm:
- Postgres managed (Supabase free tier): $0 → $25/mo khi scale
- Stripe fee: 2.9% + $0.30 mỗi transaction
- Resend email: $0 (free tier 3k/mo)
