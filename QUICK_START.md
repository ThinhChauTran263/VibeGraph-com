# VibeGraph Quick Start Scripts

Use these scripts on a fresh machine after cloning the repository and placing the internal `.env` file in the repository root.

## Windows

Open PowerShell in the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .\quick-start-win.ps1
```

If Docker Desktop is missing and `winget` exists, the script attempts to install Docker Desktop. First-time Docker installation may require WSL setup, logout, or reboot. After Docker Desktop says it is running, run the script again.

## macOS

Open Terminal in the repository root:

```bash
chmod +x ./quick-start-mac.sh
./quick-start-mac.sh
```

If Docker Desktop is missing and Homebrew exists, the script attempts to install Docker Desktop. After Docker Desktop says it is running, run the script again if needed.

## What The Scripts Do

1. Verify `.env` exists in the repository root.
2. Check Docker and Docker Compose v2.
3. Start Docker Desktop when possible.
4. Run `docker compose up -d --build`.
5. Wait for:
   - Neo4j: `http://localhost:7474`
   - Backend: `http://localhost:8080/actuator/health`
   - Frontend: `http://localhost:3000`

## Useful Options

Windows:

```powershell
.\quick-start-win.ps1 -Reset
.\quick-start-win.ps1 -NoBuild
```

macOS:

```bash
./quick-start-mac.sh --reset
./quick-start-mac.sh --no-build
```

`reset` removes Docker volumes, including the local Neo4j data volume. Use it only when you want a clean local database.
