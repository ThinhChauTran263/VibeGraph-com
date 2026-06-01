# VibeGraph — Gemini CLI Rules

> **Full project rules: [`RULES.md`](./RULES.md)** — This file extends with Gemini-specific config.

## Quick Reference

| Task | Action |
|------|--------|
| Before editing any symbol | Run `gitnexus_impact` first — see RULES.md Section 1 |
| Before commit | Run commit checklist — RULES.md Section 2 Phase 4 |
| Java code quality | Follow RULES.md Section 3 (Java) |
| Vue/TS code quality | Follow RULES.md Section 3 (TypeScript/Vue) |
| Security | Follow RULES.md Section 4 |
| Testing | Follow RULES.md Section 5 |

## Gemini-Specific Notes

### Context Loading
When Gemini CLI starts, prioritize loading:
1. `RULES.md` — Full project rules (single source of truth)
2. `VibeGraph-specs-2month/` — Architecture & feature specs
3. `pom.xml` + `vibegraph-web/package.json` — Tech stack

### MCP Tools Available
- **GitNexus MCP** at `http://localhost:8080/mcp` — code intelligence (impact, query, context, rename, detect_changes)

### Workflow
1. **Understand** → `gitnexus_query` or read `VibeGraph-specs-2month/`
2. **Plan** → write 1-page spec for non-trivial features
3. **Execute** → free-flow within planned scope
4. **Commit** → run mandatory checklist (RULES.md Section 2 Phase 4)

## Commit Checklist

See **RULES.md Section 2 Phase 4** — run before every commit.
