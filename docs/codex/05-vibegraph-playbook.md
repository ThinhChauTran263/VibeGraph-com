# VibeGraph Playbook cho Codex

File này gom các lệnh và guardrail riêng của VibeGraph. Với repo khác, dùng workflow chung nhưng thay build/test commands và project roles.

## Mở Codex đúng project

```powershell
Set-Location D:\Users\User\IdeaProjects\VibeGraph
codex --strict-config
```

Sau khi mở:

```text
/status
/debug-config
```

Kỳ vọng: cwd là VibeGraph, project `.codex/config.toml` được load, multi-agent bật và sandbox/approval đúng ý bạn.

## Quy tắc bắt buộc trước khi sửa code

Repo yêu cầu GitNexus impact analysis trước khi sửa function/class/method:

```text
Trước khi sửa symbol <NAME>, gọi gitnexus_impact với direction upstream.
Báo direct callers, affected execution flows và risk. Nếu HIGH/CRITICAL,
dừng trước khi edit và báo mình.
```

Khi chưa biết symbol:

```text
Dùng gitnexus_query để map concept <FLOW>, sau đó gitnexus_context cho
symbol sở hữu flow. Chỉ khi hiểu blast radius mới đề xuất edit.
```

Kiểm tra index:

```powershell
npx gitnexus status

# Chỉ chạy khi index stale
npx gitnexus analyze
```

## Chạy local: database + BE + FE

### Terminal 1 - PostgreSQL và Neo4j

```powershell
docker compose up -d postgres neo4j
docker compose ps
```

### Terminal 2 - Backend

```powershell
Set-Location D:\Users\User\IdeaProjects\VibeGraph
.\mvnw.cmd spring-boot:run
```

Backend mặc định: `http://localhost:8080`.

### Terminal 3 - Frontend Vite

```powershell
Set-Location D:\Users\User\IdeaProjects\VibeGraph\vibegraph-web
npm run dev -- --port 5173
```

Frontend local: `http://localhost:5173`.

### Full Docker stack

```powershell
docker compose up -d --build
docker compose ps
docker compose logs --tail 100 backend
```

Frontend Docker dùng port `3000`; frontend Vite local dùng `5173`.

## Health checks

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-WebRequest http://localhost:5173 -UseBasicParsing
docker compose ps
```

Khi `ECONNREFUSED`, kiểm tra process/port/health trước khi sửa code:

```powershell
Get-NetTCPConnection -LocalPort 8080,5173 -ErrorAction SilentlyContinue
Get-Process java,node -ErrorAction SilentlyContinue
```

## Backend feedback loop

```powershell
# Compile nhanh
.\mvnw.cmd compile

# Unit tests
.\mvnw.cmd test

# Một test class
.\mvnw.cmd test -Dtest=RefreshSessionServiceTest

# Full verification: unit + integration + JaCoCo gate
.\mvnw.cmd verify
```

`verify` có thể cần Docker/Testcontainers cho PostgreSQL và Neo4j integration tests.

## Frontend feedback loop

```powershell
Push-Location .\vibegraph-web

# Check không chủ ý sửa code
npm run type-check
npm run test:unit -- --run
npm run build

# Lưu ý: script lint hiện có --fix và có thể sửa file
npm run lint

Pop-Location
```

Sau `npm run lint`, luôn xem lại `git diff` vì cả Oxlint và ESLint scripts đang chạy chế độ auto-fix.

## Verification theo loại thay đổi

| Thay đổi | Tối thiểu |
|---|---|
| Java thuần/service | test class liên quan + `compile` |
| Auth/JWT/session | unit + controller/integration liên quan |
| Repository/PostgreSQL | integration test với PostgreSQL/Testcontainers |
| Neo4j/Cypher | integration test Neo4j + query parameterization check |
| WebSocket | message-flow integration test, auth/revoke behavior |
| Vue component/store | Vitest liên quan + type-check |
| Cross-stack | backend test + frontend test/build + manual smoke path |
| Migration | Flyway validate/migrate trong test + schema/index query |

## Review bằng role project

```text
Sau Java: dùng java_reviewer read-only.
Sau Vue/TS: dùng typescript_reviewer read-only.
Auth/session/input/secrets: thêm security_reviewer.
Thay đổi boundary hoặc cross-module: thêm architect.
```

Prompt mẫu:

```text
Review thay đổi hiện tại bằng java_reviewer và security_reviewer song song.
Cả hai read-only. Chờ đủ rồi tổng hợp findings theo severity, cite file/line,
ưu tiên correctness, authorization, concurrency và missing regression tests.
```

## Pre-commit gate

Codex không được commit nếu chưa được yêu cầu. Trước commit:

```text
1. Chạy gitnexus_detect_changes và báo affected symbols/flows.
2. Xem /diff và git diff --check.
3. Chạy backend/frontend verification theo scope.
4. Kiểm tra secrets, debug logs và artifact ngoài scope.
5. Chỉ đề xuất commit message; không commit cho đến khi mình xác nhận.
```

PowerShell kiểm tra cơ bản:

```powershell
git status --short
git diff --check
git diff --stat
```

## Dừng service an toàn

```powershell
# Dừng container nhưng giữ volume/data
docker compose stop backend frontend

# Dừng toàn stack nhưng vẫn giữ named volumes
docker compose down
```

`docker compose down -v` xóa named volumes PostgreSQL/Neo4j. Chỉ chạy khi bạn chủ ý reset dữ liệu và đã xác nhận target.

## Prompt khởi động task VibeGraph

```text
Task: <mục tiêu>.

Đọc AGENTS.md và RULES.md áp dụng cho scope.
Trước symbol edit, chạy GitNexus impact analysis và báo blast radius.
Giữ thay đổi nhỏ, không đụng file ngoài scope, không commit.
Chạy verification phù hợp với backend/frontend/database/WebSocket.
Nếu thấy thay đổi bất ngờ từ session khác, dừng và báo path cụ thể.

Cuối cùng báo: outcome, file đã đổi, command/result, việc chưa kiểm chứng và risk.
```

