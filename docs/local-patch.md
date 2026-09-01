# Local Patch — Quickstart (5 minutes)

Edit Java files locally, push patches to the VibeGraph backend, and watch the knowledge graph update in real time.

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Java | 21+ | `java --version` |
| Node.js | 20+ | `node --version` |
| Docker Desktop | latest | `docker compose version` |

## One-command demo

Run the full local patch flow against the Docker stack:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\demo-local-patch.ps1
```

Add `-Clean` to delete the demo project and sample directory after the run.

## 1. Start the stack

```bash
docker compose up -d --build
```

Wait for all containers to be healthy:

```bash
docker compose ps
```

You should see `backend`, `frontend`, `neo4j`, and `postgres` all with status `Up (healthy)`.

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Neo4j Browser: http://localhost:7474

## 2. Install the CLI

```bash
npm install -g ./vibegraph-cli
```

Verify:

```bash
vibegraph help
```

## 3. Configure and authenticate with a project-bound API key

```bash
vibegraph config set-url http://localhost:8080
vibegraph login
vibegraph key status
```

The browser flow verifies your account and lets you choose an active project key:

1. Open the repository/project in your user console.
2. Sign in when the browser opens.
3. Select the project key to use for this local folder.

Verify the local configuration without revealing the full key:

```bash
vibegraph key status
vibegraph config show
vibegraph doctor
```

`VIBEGRAPH_API_KEY` overrides the stored key, and `VIBEGRAPH_API_URL` overrides the configured API
URL. Push and watch send `X-API-Key` and do not send a Bearer token when a key is available.

Legacy login remains available for compatibility and development commands that still use a
project ID:

```bash
vibegraph login --email you@example.com --password "YourPass123!"
vibegraph me
```

## 4. Create a sample project

Create a Java project under `./projects/` (this directory is mounted into the backend container as `/projects`):

```bash
mkdir -p projects/demo/src/main/java/com/demo
```

Create `projects/demo/src/main/java/com/demo/App.java`:

```java
package com.demo;

public class App {
    private String name;

    public App(String name) {
        this.name = name;
    }

    public String greet() {
        return "Hello, " + name + "!";
    }

    public static void main(String[] args) {
        App app = new App("World");
        System.out.println(app.greet());
    }
}
```

## 5. Import the project

The backend sees the host `./projects` directory as `/projects` inside its container:

```bash
vibegraph projects import-local --path /projects/demo --name demo
```

Output:

```
{
  "id": "<projectId>",
  "name": "demo",
  "status": "ANALYZING"
}
```

The web-created API key is already bound to this project, so run push/watch from the local project
folder without a project ID or `--root`. Keep the ID only for legacy project-management commands.

## 6. Push a patch

Edit the file on your host (in your IDE or editor), then push:

```bash
vibegraph push
```

Output:

```
Pushed patch: 1 changed, 0 deleted
```

Preview without sending:

```bash
vibegraph push --dry-run
```

## 7. Re-analyze

After pushing, trigger analysis to update the knowledge graph:

```bash
vibegraph projects analyze <projectId>
```

Output:

```
{
  "projectId": "<projectId>",
  "filesParsed": 1,
  "nodesUpserted": 7,
  "edgesUpserted": 16,
  "warnings": 0
}
```

## 8. Watch mode (continuous)

Auto-push on every file save:

```bash
vibegraph watch
```

The watcher detects changes, pushes patches, and prints timestamps:

```
Watching: D:\...\projects\demo
Project: API key binding
Press Ctrl+C to stop.

Baseline: 1 files tracked.

[10:30:15] Pushed: 1 changed, 0 deleted
```

## 9. Check status

```bash
vibegraph projects status <projectId>
vibegraph projects list
```

## Safety

### What the CLI skips automatically

The CLI never uploads these files, regardless of what's in your project root:

| Category | Patterns |
|----------|----------|
| Secrets | `.env`, `.env.*`, `*.pem`, `*.key`, `id_rsa`, `id_dsa`, `id_ed25519` |
| Archives | `*.zip`, `*.tar`, `*.tgz`, `*.gz`, `*.rar`, `*.7z` |
| Directories | `.git/`, `node_modules/`, `dist/`, `build/`, `target/`, `out/`, `bin/` |
| Binary files | Any file with NUL bytes in the first 8KB |
| Large files | >1MB (override: `VIBEGRAPH_MAX_FILE_SIZE` env var) |
| Symlinks | Always skipped |

These rules apply at **any depth** — `secrets/prod.pem` and `config/.env.local` are both skipped.

### Custom ignore rules

Create a `.vibegraphignore` in your project root:

```bash
vibegraph ignore init
```

Edit `.vibegraphignore` to add project-specific patterns (same syntax as `.gitignore` basics).

### Backend defense-in-depth

The backend independently enforces:
- Ownership verification (only your projects)
- Blocked file extensions (`*.pem`, `*.key`, etc.)
- Path traversal protection (no `../`)
- File size limits

## Troubleshooting

| Error | Meaning | Fix |
|-------|---------|-----|
| `HTTP 401` / exit code 3 | API key invalid/deleted or legacy token expired | Run `vibegraph doctor`, then set a valid key |
| `HTTP 403` | API key disabled/locked, account blocked, or project mismatch | Check the key status in the web app or contact support |
| `HTTP 404` | Project ID not found | Verify with `vibegraph projects list` |
| `PATCH_REJECTED` | Backend rejected unsafe file/path | Check file is not in blocked list |
| `fetch failed` / `ECONNREFUSED` | Backend not reachable | Ensure `docker compose ps` shows healthy |

### Docker path mapping

| Context | Path |
|---------|------|
| Host (your machine) | `./projects/demo/src/main/java/...` |
| Backend container | `/projects/demo/src/main/java/...` |
| CLI `import-local --path` | `/projects/demo` (container-visible) |

Run `vibegraph push`, `vibegraph watch`, and `vibegraph ignore init` from the project folder on the host. The `--path` for `import-local` uses the **container** path (how the backend sees it).

## Full command reference

```bash
vibegraph help
vibegraph config show
vibegraph config set-url <url>
vibegraph login
vibegraph key list
vibegraph key change
vibegraph key status
vibegraph key clear
vibegraph register --email <e> --password <p> --name <n>
vibegraph login --email <e> --password <p>
vibegraph logout
vibegraph me
vibegraph doctor
vibegraph projects list
vibegraph projects import-local --path <containerPath> --name <name>
vibegraph push
vibegraph push --dry-run
vibegraph projects analyze <projectId>
vibegraph projects status <projectId>
vibegraph projects delete <projectId>
vibegraph watch
vibegraph ignore init
```
