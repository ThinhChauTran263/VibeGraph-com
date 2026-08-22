# CLI browser authorization and MCP sharing

## Goal

Allow one VibeGraph CLI authorization to connect a local repository, push/watch source, and configure an IDE MCP client without exposing a user JWT or requiring manual API-key copy/paste.

## Contract

- `POST /api/cli/device/start` creates a short-lived device request and returns a human code, verification URL, polling token, and interval.
- The browser opens the verification URL, authenticates with the normal VibeGraph session, selects an owned project, and approves the request.
- Approval reuses the existing project-bound API key when one exists, or creates one when absent. The plaintext secret is transferred only once to the polling CLI and is never stored in plaintext.
- `POST /api/cli/device/status` accepts the polling token and returns `PENDING`, `APPROVED`, `EXPIRED`, or `CONSUMED`. An approved secret is atomically consumed.
- `vibegraph login` and `vibegraph init` use device authorization. `vibegraph mcp install` writes a local IDE configuration that invokes `vibegraph mcp-proxy`, so IDEs never receive a raw secret in a workspace file.
- `/api/projects/current/patch` and `/mcp/**` continue to use the existing project-bound `X-API-Key` authorization and shared user credit balance.
- `list_projects` is restricted to the API key's bound project when called through a project-bound key.

## Security invariants

- Device codes expire quickly and are single-use.
- Polling tokens are stored hashed; approval requires the browser session; API-key secrets are encrypted at rest and returned only once.
- No endpoint accepts a user JWT as a substitute for project-bound patch/MCP authorization.
- MCP and CLI charges remain separate ledger sources (`MCP`/`CLI`) but debit the same user balance.
