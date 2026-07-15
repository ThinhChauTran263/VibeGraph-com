# Supervisor Checklist - Phase 6

Use this after Claude/Kiro complete their handoffs.

## Integration Order

1. Backend contracts and migrations
2. Backend tests/security gates
3. Frontend API client/types alignment
4. Frontend UI integration
5. CLI/MCP smoke
6. Gemini strict review
7. Final browser QA

## Acceptance Checklist

### Blocked Account
- [ ] Admin block kicks/logs out active product session quickly.
- [ ] Login blocked account shows safe reason.
- [ ] Product actions disabled/rejected for blocked account.

### Feature Flags
- [ ] Backend rejects disabled features.
- [ ] Frontend disables controls with clear reason.
- [ ] API key, import methods, MCP tools, CLI push, registration, usecase generation covered.

### User UX
- [ ] Sidebar expanded/collapsed redesigned with real icons.
- [ ] Account card compact.
- [ ] Overview summary: repo count, credits, plan.
- [ ] Quick Actions: Repo, API Key, Reports.
- [ ] Repositories default project cards.
- [ ] `New Repository` shows existing VibeGraph import form.
- [ ] Import success opens graph/loading graph.
- [ ] API key creation selects project.
- [ ] Notifications from announcements available.

### Admin UX
- [ ] System sections collapsible.
- [ ] MCP child tool controls visible and connected.
- [ ] Admin profile works like user profile.
- [ ] Audit retention in Settings.
- [ ] Security includes request monitor, IP block/watchlist, audit logs.

### Backend/CLI/MCP
- [ ] API keys are project-bound and enforced.
- [ ] CLI/MCP use key-bound project context.
- [ ] Concurrent import guard works.
- [ ] Request monitoring/rate limit works.
- [ ] Exact IP block works.
- [ ] Audit logs and retention work.

## Final Gates

- [ ] Backend: `./mvnw clean test`
- [ ] Frontend: `npm run type-check`
- [ ] Frontend: `npm run test:unit -- --run`
- [ ] Frontend: `npm run build`
- [ ] Browser QA via Chrome DevTools
- [ ] Gemini verdict PASS or all HIGH/CRITICAL findings fixed
- [ ] `git diff --check`
- [ ] `npx gitnexus detect-changes --repo VibeGraph-com`

