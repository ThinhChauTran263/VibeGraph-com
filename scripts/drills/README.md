# T2 drill — local TLS verification of Secure-cookie behaviour

Re-run the throwaway HTTPS proxy drill that verified T2 (audit finding: cookies
must carry the `Secure` flag behind a TLS edge). First executed 2026-08-13;
result PASS with `X-Forwarded-Proto: https` and WITHOUT it no Secure flag
(`AUTH_COOKIE_SECURE=false` in `.env`, backend leg plaintext). Keep this drill
re-runnable after any change to cookies, auth filters, or reverse-proxy config.

**Precondition:** the dev stack is up (`docker compose ps` shows
`vibegraph-backend` healthy on network `vibegraph_default`).

## Steps (run from repo root, PowerShell)

```powershell
# 1. One-shot cert + proxy. nginx:alpine has no openssl, so use plain alpine
#    with openssl+nginx installed; certs are generated, then nginx runs in the
#    foreground of the same throwaway container.
docker run -d --name vg-t2-tls-proxy --network vibegraph_default -p 8443:8443 `
  -v "${PWD}\scripts\drills\nginx-t2-drill.conf:/etc/nginx/nginx.conf:ro" alpine `
  sh -c "apk add --no-cache openssl nginx && mkdir -p /certs && openssl req -x509 -newkey rsa:2048 -nodes -days 1 -keyout /certs/tls.key -out /certs/tls.crt -subj '/CN=localhost' -addext 'subjectAltName=DNS:localhost' && nginx -g 'daemon off;'"

# 2. Login over real HTTPS and inspect the Set-Cookie flags:
curl -k -i -c t2-cookies.txt -X POST https://localhost:8443/api/auth/login `
  -H "Content-Type: application/json" -d '{"email":"<user>","password":"<pass>"}'
# PASS criterion: Set-Cookie for vg_session AND vg_refresh both contain "Secure".

# 3. Roundtrip: replay the cookies over HTTPS — must return 200:
curl -k -s -o NUL -w "%{http_code}`n" -b t2-cookies.txt https://localhost:8443/api/projects

# 4. Teardown (leave no residue):
docker rm -f vg-t2-tls-proxy
Remove-Item t2-cookies.txt
```

## What "fail" looks like (known-bad state, measured 2026-08-13)

Without `proxy_set_header X-Forwarded-Proto https` the login succeeds but the
cookies have NO `Secure` flag, because the backend leg is plaintext HTTP and
`AUTH_COOKIE_SECURE=false` in `.env`. That configuration is the exact gap T2
was opened for; any reverse proxy placed in front of VibeGraph MUST send
`X-Forwarded-Proto` (or set `AUTH_COOKIE_SECURE=true`).

## Files

- `nginx-t2-drill.conf` — the proxy config (port 8443 → `vibegraph-backend:8080`).
- Findings history: `update/docs/Qwen/SESSION-REPORT-BM2-T2-2026-08-13.md` §1(c).
