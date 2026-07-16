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

## 3. Configure and authenticate

```bash
vibegraph config set-url http://localhost:8080
vibegraph register --email you@example.com --password "YourPass123!" --name "Your Name"
```

Or login to an existing account:

```bash
vibegraph login --email you@example.com --password "YourPass123!"
```

Verify:

```bash
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

Save the `id` — you'll use it for all subsequent commands.

## 6. Push a patch

Edit the file on your host (in your IDE or editor), then push:

```bash
vibegraph projects push <projectId> --root ./projects/demo
```

Output:

```
Pushed patch: 1 changed, 0 deleted
```

Preview without sending:

```bash
vibegraph projects push <projectId> --root ./projects/demo --dry-run
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
vibegraph watch <projectId> --root ./projects/demo
```

The watcher detects changes, pushes patches, and prints timestamps:

```
Watching: D:\...\projects\demo
Project: <projectId>
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
vibegraph ignore init --root ./projects/demo
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
| `HTTP 401` / exit code 3 | Token expired or invalid | Run `vibegraph login` again |
| `HTTP 403` | Not the project owner | Check you're logged in as the correct user |
| `HTTP 404` | Project ID not found | Verify with `vibegraph projects list` |
| `PATCH_REJECTED` | Backend rejected unsafe file/path | Check file is not in blocked list |
| `fetch failed` / `ECONNREFUSED` | Backend not reachable | Ensure `docker compose ps` shows healthy |

### Docker path mapping

| Context | Path |
|---------|------|
| Host (your machine) | `./projects/demo/src/main/java/...` |
| Backend container | `/projects/demo/src/main/java/...` |
| CLI `--root` flag | `./projects/demo` (host-relative) |
| CLI `import-local --path` | `/projects/demo` (container-visible) |

The `--root` flag always uses the **host** path (where your files are). The `--path` for `import-local` uses the **container** path (how the backend sees it).

## Full command reference

```bash
vibegraph help
vibegraph config show
vibegraph config set-url <url>
vibegraph register --email <e> --password <p> --name <n>
vibegraph login --email <e> --password <p>
vibegraph logout
vibegraph me
vibegraph doctor
vibegraph projects list
vibegraph projects import-local --path <containerPath> --name <name>
vibegraph projects push <projectId> --root <hostPath> [--dry-run]
vibegraph projects analyze <projectId>
vibegraph projects status <projectId>
vibegraph projects delete <projectId>
vibegraph watch <projectId> --root <hostPath>
vibegraph ignore init [--root <path>]
```
