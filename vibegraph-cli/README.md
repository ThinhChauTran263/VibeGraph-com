# VibeGraph CLI

Local command-line client for the VibeGraph API.

## Install

```bash
npm install -g ./vibegraph-cli
```

## Configure

```bash
vibegraph config set-url http://localhost:8080
vibegraph register --email you@example.com --password "change-me-123" --name "Your Name"
vibegraph login --email you@example.com --password "change-me-123"
vibegraph me
```

## Projects

```bash
vibegraph projects list
vibegraph projects create --path /projects/demo --name demo
vibegraph projects import-local --path /projects/demo --name demo
vibegraph projects analyze <projectId>
vibegraph projects delete <projectId>
```

When the backend runs through the repository Docker Compose stack, local folders are visible inside
the backend container only through configured mounts. By default, `./projects` is mounted as
`/projects`, so pass container-visible paths such as `/projects/demo`.

The future local patch/watch workflow will build on this CLI package once the backend has a
dedicated delta upload endpoint.
