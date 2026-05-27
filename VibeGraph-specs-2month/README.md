# VibeGraph — 2-Month Realistic Plan

**Deadline:** 8 tuần (6 dev + 2 buffer)
**Status:** Active plan
**Replaces:** `VibeGraph-specs/` (giữ làm reference dài hạn)

## Mục tiêu duy nhất sau 2 tháng

User truy cập `vibegraph.com` (hoặc chạy Docker local) → paste GitHub URL Java project → nhìn thấy graph như GitNexus → AI tools (Cursor/Claude Code/Kiro) kết nối qua MCP và đọc được architecture. Hoặc user chạy `vibegraph watch` local → graph cập nhật real-time khi tạo/sửa/xóa file.

## Cắt scope so với spec gốc

### Giữ (Critical)
- FR-01 Java parsing
- FR-02 Neo4j storage (Docker, không Kuzu/Postgres)
- FR-03 Force Graph Sigma.js
- FR-04 Use Case diagram
- FR-05 Class diagram
- FR-07 Realtime update
- FR-08 Auto File Watcher
- FR-09 REST API
- FR-10 MCP Server (USP)

### Defer (post-2-month)
- FR-06 Sequence diagram (nice-to-have)
- FR-11 Context API (đã có MCP, REST API context có thể ghép sau)
- FR-12 Steering file auto-gen
- FR-13 Pre-code hook templates
- Multi-language (TypeScript/Vue/Python)
- Auth + Stripe + Pro/Ultra plans
- GraalVM native-image cho CLI (2 tháng dùng JAR + Java 21)
- Kuzu embedded mode
- Postgres+AGE SaaS multi-tenant

### Thêm mới (so với spec gốc)
- **GitHub URL import (Tarball stream)** — backend stream tarball từ GitHub API, parse in-memory, KHÔNG ghi disk
- **Local Watch CLI** — `vibegraph watch` real-time sync local folder (privacy mức 1, chỉ gửi metadata)
- **GraphRepository interface** — tách abstraction để Phase 2 swap DB

## Cấu trúc folder

| File | Mục đích |
|---|---|
| `README.md` | File này |
| `requirements-trimmed.md` | 9 functional requirements + FR-NEW (GitHub Import) + FR-NEW-2 (CLI) |
| `architecture.md` | System design rút gọn |
| `task-breakdown-8week.md` | Task cụ thể 5 dev × 8 tuần |
| `file-checklist.md` | Files cần tạo (~130 items) |
| `deployment-plan.md` | Docker deploy + domain + SSL |
| `presentation.html` | Trình bày dự án cho non-tech |

## Thay đổi cấu trúc code so với spec gốc?

**Thêm:**
- `graph/repository/GraphRepository.java` — interface (mới)
- `graph/repository/impl/neo4j/Neo4jGraphRepository.java` — di chuyển impl Neo4j vào subpackage
- `graph/controller/ImportController.java` — endpoint POST /api/projects/import-github
- `graph/service/TarballImportService.java` + impl (stream tarball, parse in-memory)
- `vibegraph-cli/` — Maven module mới (LocalWatcher + DiffExtractor + WsClient + commands)
- `vibegraph-cli-npm/` — npm wrapper (`vibegraph` package, requires Java 21)
- Thêm dep `org.apache.commons:commons-compress:1.26.0` vào `vibegraph-server/pom.xml`
- Thêm dep `io.methvin:directory-watcher`, `info.picocli:picocli` vào `vibegraph-cli/pom.xml`

**Bỏ:**
- Toàn bộ `steering/` module (FR-12 defer)
- MCP tools `get_coding_rules`, `get_usecase_context` (giữ 4 tools đủ MVP)
- JGit dependency (thay bằng commons-compress, không cần clone)
