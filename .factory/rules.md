# VibeGraph — Factory AI Rules

> **Full project rules: [`RULES.md`](../RULES.md)** — This file extends with Factory AI-specific config.

## Quick Reference

| Task | Action |
|------|--------|
| Before editing any symbol | Run impact analysis (RULES.md Section 1) |
| Before commit | Run commit checklist (RULES.md Section 2 Phase 4) |
| Java backend | Follow RULES.md Section 3 (Java) |
| Vue/TS frontend | Follow RULES.md Section 3 (TypeScript/Vue) |
| Security | Follow RULES.md Section 4 |
| Testing | Follow RULES.md Section 5 |

## Factory AI-Specific Notes

### Available Skills (`~/.factory/skills/`)
- `understand`, `understand-dashboard`, `understand-onboard`, `understand-domain`
- `ui-ux-pro-max` — UI design intelligence
- `gitnexus-*` — Code intelligence
- `addy-spec`, `addy-plan`, `addy-build`, `addy-review`, `addy-ship`

### Available Droids (`~/.factory/droids/`)
- `java-reviewer` — Review Java code
- `typescript-reviewer` — Review Vue/TS code
- `security-reviewer` — Security audit
- `architect` — Architecture decisions

### MCP Servers (`~/.factory/mcp-configs/`)
- **GitNexus** at `http://localhost:8080/mcp`

### Workflow
1. **Understand** → use `understand` skill or `gitnexus_query`
2. **Plan** → write spec in `VibeGraph-specs/`
3. **Execute** → free-flow within scope
4. **Commit** → run checklist (RULES.md Section 2 Phase 4)

## Commit Checklist

See **RULES.md Section 2 Phase 4** — run before every commit.
