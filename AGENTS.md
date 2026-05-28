<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **VibeGraph-com** (1295 symbols, 1652 relationships, 0 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/VibeGraph-com/context` | Codebase overview, check index freshness |
| `gitnexus://repo/VibeGraph-com/clusters` | All functional areas |
| `gitnexus://repo/VibeGraph-com/processes` | All execution flows |
| `gitnexus://repo/VibeGraph-com/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->

---

# VibeGraph — Codex / Universal Agent Rules

> **Full project rules: [`RULES.md`](./RULES.md)** — This file extends with Codex/agent-specific config.

## Quick Reference

| Task | Action |
|------|--------|
| Before editing any symbol | Run `gitnexus_impact` (RULES.md Section 1) |
| Before commit | Run commit checklist (RULES.md Section 2 Phase 4) |
| Java backend | Follow RULES.md Section 3 (Java) |
| Vue/TS frontend | Follow RULES.md Section 3 (TypeScript/Vue) |
| Security | Follow RULES.md Section 4 |
| Testing | Follow RULES.md Section 5 |

## Available Skills (`~/.codex/skills/`)
- `understand`, `understand-dashboard`, `understand-onboard`, `understand-domain`
- `ui-ux-pro-max` — UI design intelligence
- `gitnexus-*` — Code intelligence
- `addy-spec`, `addy-plan`, `addy-build`, `addy-review`, `addy-ship`

## Available Agents (`~/.codex/agents/`)
- `java-reviewer`, `typescript-reviewer`, `security-reviewer`, `architect`

## Commit Checklist

See **RULES.md Section 2 Phase 4** — run before every commit.
