# VibeGraph User Guide

VibeGraph turns a Java codebase into an explorable knowledge graph and generates
architecture diagrams from it. This guide walks through the UI flow end to end.

To start the app, see [DEVOPS-GUIDE.md](DEVOPS-GUIDE.md). Once running, open
**http://localhost:3000**.

## 1. Import a project

The home page offers three ways to add a project:

| Method | When to use | Notes |
| --- | --- | --- |
| **Local folder** | The project already exists on the machine running VibeGraph | The graph updates in realtime as you edit those files — no re-upload needed. The folder must be inside the server's configured projects root. |
| **Archive** | You have a `.zip`, `.tar`, `.tar.gz`, or `.tgz` | Uses your account's remaining storage quota. Enter a project name, choose the file, upload. |
| **GitHub** | A public repository | Paste the HTTPS URL (e.g. `https://github.com/owner/repo`). |

All three show a progress bar (Uploading → Analyzing… N% → Finalizing) while the
backend parses and builds the graph. When analysis finishes you are taken to the
project's graph view.

## 2. Explore the graph

The graph view renders classes, methods, and their relationships:

- **Pan / zoom** with mouse drag and wheel.
- **Click a node** to open its detail panel (incoming/outgoing connections).
- **Expand** a node to pull its neighbors on demand instead of loading everything.
- **Filter** by node type or search to focus on a subsystem.
- The graph **updates live** for local-folder projects as you edit the source.

## 3. View architecture diagrams

Open the **Diagrams** view. It has three tabs:

- **API Map** — endpoint-to-handler map (Mermaid).
- **UML Use Case** — a standards-correct OMG UML 2.5 use case diagram inferred from
  the code: stick-figure actors, oval use cases inside a named system boundary,
  `«include»`/`«extend»` dependencies, and actor generalization. External systems
  render as `«system»` boxes.
- **Class** — class diagram (Mermaid).

Each diagram supports **zoom**, **Fullscreen**, and **Export Mode** (hides developer
warnings so a screenshot looks like a finished figure).

## 4. Use the MCP integration (optional)

VibeGraph exposes an MCP server so AI assistants can query the graph (architecture
context, impact analysis, source search, etc.). See
[MCP_INTEGRATION.md](MCP_INTEGRATION.md) for how to connect a client.

## Troubleshooting

- **Import seems stuck near 100%** — the graph is being persisted; it will finish.
- **Frontend loads but API calls fail** — confirm the backend is healthy at
  http://localhost:8080/actuator/health.
- For Docker-level issues (ports, Neo4j auth, large uploads) see the troubleshooting
  section of [DEVOPS-GUIDE.md](DEVOPS-GUIDE.md).
