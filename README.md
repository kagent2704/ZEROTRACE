# ZeroTrace

ZeroTrace is now split into two runnable parts:

- `auth-server`: Spring Boot auth, public-key directory, encrypted message relay, inbox polling, and threat detection.
- `ZeroTrace_Core`: client-side key generation, client-side encryption/signing, token-authenticated desktop app, inbox decryption, and attack simulation CLI.

## What changed

- Encryption moved fully back to the client.
- Private keys are no longer hardcoded or stored on the server.
- Each client auto-generates a local RSA keypair and syncs only the public key during register/login.
- The server now issues session tokens on register/login, and message send/inbox/public-key lookups require authenticated requests.
- ZeroTrace now supports `PRIVATE` and `AUDIT` message modes.
- `PRIVATE` messages carry a per-message TTL and are purged after expiry.
- `AUDIT` messages are retained for 7 days and can be exported only after an organization admin approves an export request.
- Export requests expire automatically after 48 hours if no admin approves them.
- The first user created inside an organization becomes that org's `ADMIN`.
- The attached threat-detection logic was integrated into the relay path as `FeatureExtractor` + `AIAnalyzer`.
- The server now defaults to a portable file-based H2 database instead of a hardcoded local MySQL setup.

## Build

### Server

```powershell
cd auth-server
.\mvnw.cmd clean package -DskipTests
java -jar target\auth-server-0.0.1-SNAPSHOT.jar
```

The server listens on `0.0.0.0:8080` by default so it can be reached from other machines if the host firewall/router allows it.

Optional environment variables:

- `SERVER_PORT`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATASOURCE_DRIVER`
- `SPRING_PROFILES_ACTIVE`
- `ZEROTRACE_H2_CONSOLE_ENABLED`

### Client

```powershell
cd ZeroTrace_Core
.\mvnw.cmd package -DskipTests
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar
```

Windows app bundle output:

```text
ZeroTrace_Core\dist\ZeroTrace\
ZeroTrace_Core\dist\ZeroTrace-Windows.zip
```

Point the client at a remote server by setting:

```powershell
$env:ZEROTRACE_SERVER_URL="http://YOUR_PUBLIC_IP_OR_DOMAIN:8080"
```

Launching the jar with no arguments opens the desktop UI.

## Client commands

```powershell
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar          # desktop UI
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar register alice pass123 acme
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar login alice pass123
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar send bob PRIVATE 60 "hello from alice"
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar send bob AUDIT 0 "audited message"
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar inbox
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar approve-audit bob true
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar request-export 7
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar my-exports
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar pending-exports
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar approve-export 3 true
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar export-audit 3
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar whoami
```

Local private keys are stored under:

```text
%USERPROFILE%\.zerotrace\keys\<username>\
```

## Attack simulations

```powershell
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar attack bruteforce alice wrongpass
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar attack tamper alice pass123 bob
java -jar target\zerotrace-core-1.0-SNAPSHOT.jar attack flood alice pass123 bob 25
```

Expected outcomes:

- `bruteforce`: repeated `401` invalid-credential responses
- `tamper`: receiver inbox rejects the corrupted packet
- `flood`: threat monitor reports spam-bot style anomalies

## Remote-use checklist

1. Run `auth-server` on a machine with Java 17+.
2. Open inbound TCP `8080` on the host firewall or deploy behind a reverse proxy.
3. Set `ZEROTRACE_SERVER_URL` on each client to the server's public address.
4. Register each user once from their own machine so their public key is published automatically and a server token is issued.
5. Share usernames, not keys; the server acts as the public-key directory.
6. Package `ZeroTrace_Core` as the shipped desktop jar; it includes both the UI and the CLI tools.

## Permanent Hosting

The cleanest way to host `auth-server` permanently is to deploy it with Docker on a public VPS or cloud VM, then put it behind HTTPS with a domain.

### Option A: Docker on a server

Copy the `auth-server` folder to your server, then run:

```bash
cd auth-server
docker compose up -d --build
```

This uses:

- [Dockerfile](C:\Users\kashm\OneDrive\Desktop\ZEROTRACE\auth-server\Dockerfile)
- [docker-compose.yml](C:\Users\kashm\OneDrive\Desktop\ZEROTRACE\auth-server\docker-compose.yml)

It will:

- build the Spring Boot server image
- start the server on port `8080`
- persist the H2 database in a Docker volume
- run with `SPRING_PROFILES_ACTIVE=prod`
- disable the H2 console in production

### Public HTTPS deployment with a domain

If you want teammates to download the app and use it from anywhere, this is the recommended setup:

1. Buy or use a VPS with a public IPv4 address.
2. Point your domain or subdomain to that server IP.
3. Open ports `80`, `443`, and optionally `22` for SSH.
4. Deploy `auth-server` with the public compose file and Caddy.

On the server:

```bash
cd auth-server
cp .env.example .env
```

Edit `.env` and set:

```text
ZEROTRACE_DOMAIN=zerotrace.yourdomain.com
```

Then start the public stack:

```bash
docker compose -f docker-compose.public.yml up -d --build
```

This will:

- run `auth-server` internally on Docker networking
- expose Caddy on ports `80` and `443`
- automatically obtain and renew HTTPS certificates
- route `https://zerotrace.yourdomain.com` to the Spring Boot server

Files used for this setup:

- [docker-compose.public.yml](C:\Users\kashm\OneDrive\Desktop\ZEROTRACE\auth-server\docker-compose.public.yml)
- [Caddyfile](C:\Users\kashm\OneDrive\Desktop\ZEROTRACE\auth-server\Caddyfile)
- [.env.example](C:\Users\kashm\OneDrive\Desktop\ZEROTRACE\auth-server\.env.example)

### Option B: Plain jar on a server

If you do not want Docker:

```bash
cd auth-server
./mvnw -DskipTests package
SPRING_PROFILES_ACTIVE=prod java -jar target/auth-server-0.0.1-SNAPSHOT.jar
```

### Recommended production setup

For a proper always-online deployment:

1. Host `auth-server` on a public VPS or cloud VM.
2. Put it behind Nginx or Caddy.
3. Terminate HTTPS at the reverse proxy.
4. Point a domain at the server, for example `https://zerotrace.yourdomain.com`.
5. Set `ZEROTRACE_SERVER_URL` on the client to that HTTPS URL.

### Quick VPS checklist

Example flow on an Ubuntu VPS:

1. Install Docker and Docker Compose plugin.
2. Copy the `auth-server` folder to the VPS.
3. Set DNS `A` record for `zerotrace.yourdomain.com` to the VPS IP.
4. Allow inbound `80` and `443` in your cloud firewall and OS firewall.
5. Run:

```bash
docker compose -f docker-compose.public.yml up -d --build
```

6. Verify:

```bash
curl https://zerotrace.yourdomain.com/message/ping
```

You should get:

```text
Message relay working
```

### Recommended reverse proxy target

Proxy public HTTPS traffic to:

```text
http://127.0.0.1:8080
```

### Database note

For demos and small-team use, the file-based H2 database is acceptable.
For a more durable production deployment, set `SPRING_DATASOURCE_URL` to PostgreSQL or MySQL instead of H2.

## Security model in this build

- Private keys never leave the client machine.
- The server stores public keys, encrypted payloads, and expiring opaque session tokens.
- The server enforces same-organization messaging and admin-governed audit/export access.
- `/message/send`, `/message/inbox/{username}`, and authenticated public-key lookup are protected by bearer-token auth.
- The authenticated username must match the sender and inbox owner on the server side.
- Export data can be downloaded only after an approved request id is presented by the requesting user.

## Governance model

- `PRIVATE` mode: encrypted message is relayed without server plaintext access and expires after its TTL.
- `AUDIT` mode: encrypted message is relayed and retained for up to 7 days for approved users inside the same organization.
- Audit access: only org admins can approve or revoke audit access for users in their own organization.
- Export requests: approved audit users can request export windows from 1-7 days.
- Export approvals: only org admins can approve or reject pending export requests.
- Export expiry: if approval does not happen within 48 hours, the request expires and cannot be used.
- Desktop UI: users can request exports and download approved export bundles to `%USERPROFILE%\.zerotrace\exports\`.

## Notes about the attached files

- The old socket-chat files (`Server.java`, `Client.java`, `ClientHandler.java`) were treated as legacy transport prototypes and superseded by the Spring relay architecture.
- The threat detector files were folded into the live server pipeline.
- The JavaFX mockup (`ZeroTrace_final.java`) remains a useful UI prototype, but the end-to-end secure workflow completed in this sprint is the CLI path above.
