# VibeGraph CLI

Local command-line client for the VibeGraph API. Push Java source patches, watch for changes, and manage projects from your terminal.

## Install

```bash
npm install -g ./vibegraph-cli
```

Requires Node.js 20+.

## Quick Start

```bash
vibegraph config set-url http://localhost:8080
# Create a project-bound API key in the VibeGraph web app, then copy it once.
vibegraph auth set-key vbg_...
vibegraph push --root ./projects/demo
vibegraph watch --root ./projects/demo
```

For the full walkthrough, see **[docs/local-patch.md](../docs/local-patch.md)**.

## Commands

### Auth

```bash
vibegraph auth set-key <apiKey>
vibegraph auth status
vibegraph auth clear
```

API keys are created by users in the web app and are bound to one repository/project. The CLI
stores the key in `~/.vibegraph/config.json`, sends it as `X-API-Key`, and never prints the full
value. `auth clear` removes the stored key but does not remove a `VIBEGRAPH_API_KEY` environment
override.

Legacy login commands remain available for compatibility and local development:

```bash
vibegraph register --email <e> --password <p> --name <displayName>
vibegraph login --email <e> --password <p>
vibegraph logout
vibegraph me
```

### Config

```bash
vibegraph config show
vibegraph config set-url <url>
```

### Projects

```bash
vibegraph push --root <hostPath> [--dry-run]
vibegraph projects list
vibegraph projects import-local --path <containerPath> --name <name>
vibegraph projects push <projectId> --root <hostPath> [--dry-run]
vibegraph projects analyze <projectId>
vibegraph projects status <projectId>
vibegraph projects delete <projectId>
```

### Watch

```bash
vibegraph watch --root <hostPath>
vibegraph watch <projectId> --root <hostPath>
```

The root-only forms resolve the project from the configured API key. The project-ID forms are
retained for compatibility. Push and watch prefer `X-API-Key`; they use the legacy Bearer token
only when a project ID is explicitly supplied and no API key is configured.

Watch continuously monitors for file changes and auto-pushes patches. Debounces at 800ms. Press
Ctrl+C to stop.

### Ignore

```bash
vibegraph ignore init [--root <path>]
```

Generates a `.vibegraphignore` file with default rules.

### Diagnostics

```bash
vibegraph doctor
```

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
