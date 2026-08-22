# VibeGraph CLI

Local command-line client for the VibeGraph API. Push Java source patches, watch for changes, and manage projects from your terminal.

## Install

```bash
npm install -g ./vibegraph-cli
```

For a published release:

```bash
npm install -g vibegraph-cli
```

Requires Node.js 20+.

## Quick Start

```bash
vibegraph config set-url http://localhost:8080
# Sign in in the browser and choose the project API key owned by your account.
vibegraph login
vibegraph push
vibegraph watch
```

For the full walkthrough, see **[docs/local-patch.md](../docs/local-patch.md)**.

## Commands

### Auth

```bash
vibegraph login
vibegraph key change
vibegraph key list
vibegraph auth status
vibegraph auth clear
```

`vibegraph login` opens a short-lived browser authorization page. After sign-in, choose one of your
owned, active project keys. The CLI stores the selected credential and its `apiKeyId` in
`~/.vibegraph/config.json`, then uses the same credential for push, watch, and the MCP proxy.
`vibegraph key change` repeats the browser flow and refreshes the account's key list. `key list`
prints only masked metadata (`key prefix | project name`) from the last browser refresh.

Raw-key commands (`login <apiKey>`, `login --key`, `key add`, and `auth set-key`) are rejected in
production because a bearer key alone cannot prove that it belongs to the signed-in account. This
prevents accidentally configuring a leaked key from another user. Use browser login instead.

Legacy login commands remain available for compatibility and local development:

```bash
vibegraph register --email <e> --password <p> --name <displayName>
vibegraph login --email <e> --password <p>
vibegraph logout
vibegraph me
```

The CLI keeps the rotating `vg_refresh` session cookie returned by the backend and refreshes an
expired access token automatically. `vibegraph doctor` reports whether the legacy session is still
active. For production automation, prefer a project-bound API key because it is scoped to one
project and works without an interactive user session.

Project management commands (`projects list/create/analyze/status/delete`) use the JWT user
session. A project-bound API key intentionally authenticates only patch/watch and MCP operations,
so a leaked key cannot enumerate or delete projects.

### Config

```bash
vibegraph config show
vibegraph config set-url <url>
```

### Projects

```bash
vibegraph push
vibegraph push --dry-run
vibegraph projects list
vibegraph projects import-local --path <containerPath> --name <name>
vibegraph projects analyze <projectId>
vibegraph projects status <projectId>
vibegraph projects delete <projectId>
```

### Watch

```bash
vibegraph watch
```

Push and watch use the current folder and resolve the project from the configured API key.

Watch continuously monitors for file changes and auto-pushes patches. Debounces at 800ms. Press
Ctrl+C to stop.

### Ignore

```bash
vibegraph ignore init
```

Generates a `.vibegraphignore` file with default rules.

### Diagnostics

```bash
vibegraph doctor
```

`vibegraph --version` prints the package version. `vibegraph config set-url` requires HTTPS in
production; plain HTTP is accepted only for localhost development.

## IDE and MCP production setup

The recommended developer-machine setup is the local stdio proxy. It lets the IDE start
`vibegraph mcp-proxy` and reuse the credential selected by `vibegraph login`; the generated JSON
never contains the raw project API key.

```bash
vibegraph config set-url https://api.vibegraph.tech
vibegraph login
vibegraph doctor
vibegraph mcp config
vibegraph mcp doctor
```

`vibegraph mcp config` prints copy/paste JSON in the common `mcpServers` format:

```json
{
  "mcpServers": {
    "vibegraph": {
      "command": "/absolute/path/to/node",
      "args": [
        "/absolute/path/to/vibegraph-cli/bin/vibegraph.js",
        "mcp-proxy",
        "--stdio"
      ]
    }
  }
}
```

`cursor` and `generic` use this `mcpServers` shape. VS Code uses a different `servers` shape, so
use its preset instead:

```bash
vibegraph mcp install cursor
vibegraph mcp install vscode
vibegraph mcp install generic --path ./path/to/the/ide-mcp.json
```

The install command creates parent directories, merges the existing JSON, and replaces a stale
`vibegraph` entry while preserving every other MCP server. It verifies initialize and tools/list
before reporting that MCP is ready. For an IDE that is not listed, use `generic --path` if it accepts the
standard `mcpServers` format, or run `vibegraph mcp config` and paste the output into that IDE's
MCP settings file. You can choose a different server key when copying JSON:

```bash
vibegraph mcp config my-vibegraph
```

If the IDE says the server was added but no tools appear, run `vibegraph mcp doctor`. A successful
result reports the server name, protocol version, and tool count. Then restart or reload the IDE's
MCP server. If the doctor command reports an authentication error, run `vibegraph key change` and
select an active key owned by your account. Do not paste a `vibegraph>` shell prompt into JSON and
do not wrap the generated object inside a second `mcpServers` object.

Some clients only support remote Streamable HTTP servers. In that case, configure the `/mcp`
endpoint directly and put the project API key in the IDE's secret/environment store, never in a
committed workspace file:

```json
{
  "mcpServers": {
    "vibegraph": {
      "url": "https://api.vibegraph.tech/mcp",
      "transport": "streamable-http",
      "headers": {
        "X-API-Key": "<PROJECT_API_KEY>"
      }
    }
  }
}
```

`<PROJECT_API_KEY>` is the active key created for the exact project that should be exposed to the
IDE. It is not an OAuth client secret, JWT secret, or account-wide credential. In direct HTTP mode,
switch projects by replacing this header value with the new project's key and restarting/reloading
the IDE MCP server. With the CLI stdio proxy, run `vibegraph key change`, choose the new project,
then restart/reload MCP; no JSON edit is needed. The local proxy and direct HTTP modes both charge
the selected project. If a key was rotated,
deleted, disabled, expired, or belongs to another account, MCP returns an actionable error; run
`vibegraph key change` to refresh and select an owned key.

## Path conventions

| Flag | Uses | Example |
|------|------|---------|
| `--path` (import-local) | Container-visible path | `/projects/demo` |
| `--root` (push, watch) | Host-relative path | `./projects/demo` |

The Docker Compose stack mounts `./projects` on the host as `/projects` in the backend container.

## Safety

The CLI automatically skips:
- `.env`, `.env.*`, `*.pem`, `*.key`, SSH keys
- `*.zip`, `*.tar`, `*.tgz`, `*.gz`, `*.rar`, `*.7z`
- `.git/`, `node_modules/`, `dist/`, `build/`, `target/`, `out/`, `bin/`
- Binary files (NUL byte detection)
- Files >1MB
- Symlinks

These rules apply at any directory depth. Customize with `.vibegraphignore`.

## Environment variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `VIBEGRAPH_CONFIG_DIR` | `~/.vibegraph` | Config/snapshot directory |
| `VIBEGRAPH_API_URL` | (from config) | Override API URL |
| `VIBEGRAPH_API_KEY` | (from config) | Override the project-bound API key |
| `VIBEGRAPH_MAX_FILE_SIZE` | `1048576` (1MB) | Max file size in bytes |
| `VIBEGRAPH_MAX_FILES` | `200` | Max files per push |
| `VIBEGRAPH_MAX_TOTAL_BYTES` | `5242880` (5MB) | Max changed content per push; matches the backend default |
| `VIBEGRAPH_HTTP_TIMEOUT_MS` | `30000` | HTTP request timeout |
