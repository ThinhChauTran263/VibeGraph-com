# VibeGraph — Kiro Rules

> **Full project rules: [`RULES.md`](../RULES.md)** — This file extends with Kiro-specific config.

## Quick Reference

| Task | Action |
|------|--------|
| Before editing any symbol | Run impact analysis (RULES.md Section 1) |
| Before commit | Run commit checklist (RULES.md Section 2 Phase 4) |
| Java backend | Follow RULES.md Section 3 (Java) |
| Vue/TS frontend | Follow RULES.md Section 3 (TypeScript/Vue) |
| Security | Follow RULES.md Section 4 |
| Testing | Follow RULES.md Section 5 |

## Kiro-Specific Notes

### Available Skills (`~/.kiro/skills/`)
- `understand` — Codebase exploration
- `understand-dashboard`, `understand-onboard`, `understand-domain`
- `ui-ux-pro-max` — UI design intelligence
- `gitnexus-*` — Code intelligence (exploring, impact-analysis, debugging, refactoring)
- `addy-spec`, `addy-plan`, `addy-build`, `addy-review`, `addy-ship`

### MCP Servers
- **GitNexus** at `http://localhost:8080/mcp` (streamable-http)

### Workflow (Spec-driven)
1. **Understand** → use `understand` skill or `gitnexus_query`
2. **Plan** → write 1-page spec in `VibeGraph-specs/`
3. **Execute** → free-flow within planned scope
4. **Commit** → run mandatory checklist (RULES.md Section 2 Phase 4)

## Commit Checklist

See **RULES.md Section 2 Phase 4** — run before every commit.
