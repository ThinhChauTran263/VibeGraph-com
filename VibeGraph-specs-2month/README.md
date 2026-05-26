# VibeGraph — 2-Month Realistic Plan

**Deadline:** 8 tuần (6 dev + 2 buffer)
**Status:** Active plan
**Replaces:** `VibeGraph-specs/` (giữ làm reference dài hạn)

## Mục tiêu duy nhất sau 2 tháng

User truy cập `vibegraph.com` (hoặc chạy Docker local) → paste GitHub URL Java project → nhìn thấy graph như GitNexus → AI tools (Cursor/Claude Code) kết nối qua MCP và đọc được architecture.

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
- npm wrapper + GraalVM native-image
- Kuzu embedded mode
- Postgres+AGE SaaS multi-tenant

### Thêm mới (so với spec gốc)
- **GitHub URL import** — backend clone vào temp dir, parse, cleanup
- **GraphRepository interface** — tách abstraction để Phase 2 swap DB

## Cấu trúc folder

| File | Mục đích |
|---|---|
| `README.md` | File này |
| `requirements-trimmed.md` | 9 functional requirements giữ lại |
| `architecture.md` | System design rút gọn |
| `task-breakdown-8week.md` | Task cụ thể 5 dev × 8 tuần |
| `file-checklist.md` | Files cần tạo (rút gọn từ 128 → ~80) |
| `deployment-plan.md` | Docker deploy + domain + SSL |
| `presentation.html` | Trình bày dự án cho non-tech |

## Thay đổi cấu trúc code so với spec gốc?

**KHÔNG cần thay đổi nhiều.** Chỉ thêm:
- `graph/repository/GraphRepository.java` — interface (mới)
- `graph/repository/impl/neo4j/Neo4jGraphRepository.java` — di chuyển impl Neo4j vào subpackage
- `graph/controller/ImportController.java` — endpoint clone GitHub URL
- `graph/service/GithubImportService.java` + impl
- Thêm dep `org.eclipse.jgit:org.eclipse.jgit:6.x` vào `vibegraph-server/pom.xml`

Mọi thứ khác giữ nguyên theo `VibeGraph-specs/file-checklist.md` nhưng bỏ:
- Bỏ toàn bộ `steering/` module (FR-12 defer)
- Bỏ MCP tools `get_coding_rules`, `get_usecase_context` (giữ 4 tools đủ MVP)
