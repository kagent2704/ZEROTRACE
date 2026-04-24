# ZeroTrace Architecture Deep Dive

This document is the "I need to defend the whole codebase tomorrow" guide.

It is written to help you explain:

- what the system does
- how the client and server are split
- where each major feature lives in code
- how data moves through the system
- what the security model is
- what the limitations are
- how to answer code-level questions with confidence

This is not just a feature summary. It is an architecture walkthrough tied directly to the repository layout.

---

## 1. Executive Summary

ZeroTrace is a secure messaging system built as a split-client architecture:

- a JavaFX desktop client in `ZeroTrace_Core`
- a Spring Boot backend in `auth-server`

The core design principle is:

- encryption and decryption happen on the client
- private keys remain on the client
- the server stores and relays encrypted payloads
- the server still handles authentication, public key distribution, message relay, governance, TTL lifecycle, and threat detection

At a high level, the system combines:

- client-side RSA + AES encryption
- signed message integrity verification
- token-based session authentication
- organization-based policy enforcement
- `PRIVATE` and `AUDIT` message modes
- TTL-based disappearance for private messages
- audit retention and admin-approved exports
- anomaly detection using the supplied threat model

The best one-line explanation is:

> ZeroTrace is an end-to-end encrypted messaging client with a policy-aware relay server and a server-side anomaly detector.

---

## 2. Repository Structure

At the top level:

- `auth-server/`
- `ZeroTrace_Core/`
- `README.md`
- `DEMO_RUNBOOK.md`
- `ARCHITECTURE_DEEP_DIVE.md`

### 2.1 Backend Folder

`auth-server` contains the Spring Boot application.

Important subfolders:

- `src/main/java/com/zerotrace/auth_server/config`
- `src/main/java/com/zerotrace/auth_server/controller`
- `src/main/java/com/zerotrace/auth_server/model`
- `src/main/java/com/zerotrace/auth_server/repository`
- `src/main/java/com/zerotrace/auth_server/security`
- `src/main/java/com/zerotrace/auth_server/service`
- `src/main/java/com/zerotrace/auth_server/detector`
- `src/main/resources/models`

### 2.2 Client Folder

`ZeroTrace_Core` contains the Java desktop client and CLI.

Important subfolders:

- `src/main/java/com/zerotrace`
- `src/main/java/com/zerotrace/core`
- `src/main/java/com/zerotrace/core/client`
- `src/main/java/com/zerotrace/core/safety`
- `src/main/java/com/zerotrace/crypto`
- `src/main/java/com/zerotrace/model`
- `src/main/java/com/zerotrace/ui`
- `dist/`

---

## 3. System Components and Responsibilities

The cleanest way to understand the project is by responsibility.

### 3.1 Client Responsibilities

The client is responsible for:

- generating or loading the user's RSA keypair
- storing the private key locally
- encrypting outgoing plaintext
- signing outgoing messages
- decrypting incoming ciphertext
- verifying signatures on received messages
- rendering the JavaFX UI
- calling backend APIs
- visually surfacing threat flags and safety alerts

### 3.2 Server Responsibilities

The server is responsible for:

- registering and logging in users
- hashing and storing passwords
- issuing session tokens
- storing user public keys
- enforcing access control
- storing encrypted messages
- relaying new inbox items
- enforcing message-mode rules
- enforcing audit export governance
- starting TTL timers on delivery
- running anomaly detection on outbound traffic

### 3.3 Database Responsibilities

The database stores:

- users
- password hashes
- public keys
- session tokens
- encrypted messages
- audit export requests

The database does not store:

- plaintext messages
- private keys

---

## 4. Important Files by Role

This is the most useful "map of the codebase" section.

## 4.1 Client Entry and UI

### `ZeroTrace_Core/src/main/java/com/zerotrace/Main.java`

Purpose:

- entry point for both UI mode and CLI mode

What it does:

- if no CLI arguments are given, launches the JavaFX app
- otherwise handles CLI commands like `register`, `login`, `send`, `inbox`, `approve-audit`, `request-export`, `attack`, and `whoami`

Why it matters:

- this is the top-level launcher for the client application
- it proves the project supports both GUI and CLI usage

### `ZeroTrace_Core/src/main/java/com/zerotrace/ui/ZeroTraceFinalApp.java`

Purpose:

- main JavaFX desktop UI

What it does:

- login and registration screen
- home/dashboard screen
- contact list rendering
- chat rendering
- send message flow
- refresh inbox flow
- governance actions
- threat popups and threat bubble flags
- TTL live expiry behavior for delivered `PRIVATE` messages

Why it matters:

- this is where the finished user-facing product lives
- almost all visible behavior in the app is ultimately wired here

## 4.2 Client Networking and Session

### `ZeroTrace_Core/src/main/java/com/zerotrace/core/client/AuthClient.java`

Purpose:

- HTTP client wrapper for all server API calls

What it does:

- `register(...)`
- `login(...)`
- `getPublicKey(...)`
- `syncPublicKey(...)`
- `sendMessage(...)`
- `fetchInbox(...)`
- `fetchHistory(...)`
- export/governance endpoints

Why it matters:

- this is the client's gateway to the backend
- the UI and CLI both depend on it

### `ZeroTrace_Core/src/main/java/com/zerotrace/core/client/SessionManager.java`

Purpose:

- stores the current logged-in client session locally

What it does:

- saves username, server URL, token, organization, role, and audit approval state
- reloads session on startup
- clears session on logout

Why it matters:

- explains how the app stays logged in between launches

### `ZeroTrace_Core/src/main/java/com/zerotrace/core/client/KeyManager.java`

Purpose:

- manages local RSA key storage

What it does:

- creates user-specific key directories under the local `.zerotrace` folder
- loads existing keypairs
- generates new keypairs if missing
- returns public key strings for server sync

Why it matters:

- this file is central to the statement "private keys stay on the client"

Local key location:

- `%USERPROFILE%\.zerotrace\keys\<username>\`

## 4.3 Client Crypto

### `ZeroTrace_Core/src/main/java/com/zerotrace/core/EncryptionService.java`

Purpose:

- core end-to-end encryption and decryption logic

What it does when sending:

- generates an AES key
- encrypts the plaintext message with AES
- encrypts the AES key with the receiver's RSA public key
- hashes the plaintext
- signs the hash with the sender's private key

What it does when receiving:

- decrypts the AES key using the receiver's RSA private key
- decrypts the message using AES
- recomputes the hash
- verifies the signature using the sender's public key

Why it matters:

- this is the strongest proof that encryption is client-side

### `ZeroTrace_Core/src/main/java/com/zerotrace/crypto/*`

Important helpers:

- `AESUtil.java`
- `RSAUtil.java`
- `HashUtil.java`
- `SignatureUtil.java`

Purpose:

- low-level crypto helpers used by `EncryptionService`

## 4.4 Client Safety and Attack Tools

### `ZeroTrace_Core/src/main/java/com/zerotrace/core/safety/TextSafetyInspector.java`

Purpose:

- scans for suspicious hidden or embedded text patterns

What it does:

- flags content patterns that should trigger safety warnings

Why it matters:

- this is separate from the network anomaly detector
- it is more content-pattern-oriented than traffic-pattern-oriented

### `ZeroTrace_Core/src/main/java/com/zerotrace/core/client/AttackSimulator.java`

Purpose:

- generates test attack traffic

What it does:

- flood simulation
- tamper simulation
- other attack testing support

Why it matters:

- useful for proving the anomaly module and integrity checks in a demo

## 4.5 Backend Security and Auth

### `auth-server/src/main/java/com/zerotrace/auth_server/config/SecurityConfig.java`

Purpose:

- central Spring Security configuration

What it does:

- disables server-side session state
- sets the app to stateless token auth
- permits only a small set of public endpoints:
  - `/auth/register`
  - `/auth/login`
  - `/actuator/health`
  - `/message/ping`
  - `/h2-console/**`
- requires authentication for everything else
- injects the custom token filter into the request chain

Why it matters:

- this is the top-level access-control gate for the backend

### `auth-server/src/main/java/com/zerotrace/auth_server/security/TokenAuthenticationFilter.java`

Purpose:

- extracts and validates bearer tokens on incoming requests

What it does:

- reads the `Authorization: Bearer ...` header
- validates the token
- sets the authenticated Spring Security principal

Why it matters:

- this is what turns opaque tokens into authenticated requests

### `auth-server/src/main/java/com/zerotrace/auth_server/service/SessionTokenService.java`

Purpose:

- issues and validates session tokens

What it does:

- generates cryptographically random token bytes
- encodes them into a URL-safe token string
- stores them in the database
- expires them after 24 hours
- revokes previous active tokens for the same user

Why it matters:

- this is the real session/auth backbone of the system

## 4.6 Backend User Management

### `auth-server/src/main/java/com/zerotrace/auth_server/service/UserService.java`

Purpose:

- registration, login, public key sync, user lookup, and admin checks

What it does:

- hashes passwords using bcrypt
- stores user public keys
- updates the public key on login if the client provides one
- records last known IP and last login time
- increments failed login attempts on wrong password
- auto-assigns the first user in an organization as `ADMIN`
- automatically enables audit/export approval for the first org admin

Why it matters:

- this file connects identity, key registration, and org-level role assignment

### `auth-server/src/main/java/com/zerotrace/auth_server/model/User.java`

Purpose:

- JPA entity for a user record

Fields include:

- `username`
- `passwordHash`
- `organization`
- `publicKey`
- `role`
- `auditModeApproved`
- `exportApproved`
- `failedLoginAttempts`
- `lastKnownIp`
- `createdAt`
- `lastLoginAt`

Why it matters:

- this is the authoritative identity record in the system

## 4.7 Backend Messaging

### `auth-server/src/main/java/com/zerotrace/auth_server/controller/MessageController.java`

Purpose:

- REST API for messaging endpoints

Important endpoints:

- `GET /message/ping`
- `POST /message/send`
- `GET /message/inbox/{username}`
- `GET /message/history/{username}`

Why it matters:

- this is the web entry point into message relay and inbox retrieval

### `auth-server/src/main/java/com/zerotrace/auth_server/service/MessageRelayService.java`

Purpose:

- core backend message lifecycle service

This is one of the most important files in the whole project.

What it does:

- validates sender/receiver identities
- enforces same-organization messaging
- enforces audit-mode permissions
- computes TTL for `PRIVATE` messages
- computes 7-day retention for `AUDIT` messages
- stores encrypted messages in the database
- runs threat detection on outbound traffic
- serves new inbox items
- serves visible history

Why it matters:

- if `UserService` is the center of identity,
- then `MessageRelayService` is the center of message lifecycle

### `auth-server/src/main/java/com/zerotrace/auth_server/model/EncryptedMessage.java`

Purpose:

- JPA entity for stored encrypted messages

Important fields:

- sender
- receiver
- mode
- encryptedMessage
- encryptedAESKey
- signature
- iv
- messageSize
- ttlSeconds
- createdAt
- deliveredAt
- expiresAt
- auditRetainUntil

Why it matters:

- this is the database representation of a relayed ciphertext packet

### `auth-server/src/main/java/com/zerotrace/auth_server/repository/EncryptedMessageRepository.java`

Purpose:

- custom query layer for encrypted messages

What it supports:

- undelivered inbox queries
- visible history queries
- receiver-window audit queries
- threat feature counts
- retention cleanup queries

Why it matters:

- the message lifecycle logic depends heavily on these repository methods

## 4.8 Backend Governance

### `auth-server/src/main/java/com/zerotrace/auth_server/service/GovernanceService.java`

Purpose:

- org-admin audit and export control

What it does:

- grants or revokes audit approval
- creates export requests
- enforces 48-hour expiry on export requests
- allows org admins to approve or reject export requests
- produces audit export bundles

Why it matters:

- this is the main implementation of enterprise-style audit governance

### Governance-related models and repositories

Important supporting files:

- `AuditExportRequestEntity.java`
- `AuditExportResponse.java`
- `AuditExportRequestRepository.java`

Why they matter:

- these define the audit export approval workflow and persistence

## 4.9 Backend Threat Detection

### `auth-server/src/main/java/com/zerotrace/auth_server/service/ThreatDetectionService.java`

Purpose:

- computes runtime traffic features for the AI detector

Current features:

- message count in the recent window
- average send gap
- message size
- number of distinct peers
- failed login attempts
- IP changes

Why it matters:

- this converts raw system behavior into detector input features

### `auth-server/src/main/java/com/zerotrace/auth_server/detector/FeatureExtractor.java`

Purpose:

- converts individual threat signals into the six-element feature vector

### `auth-server/src/main/java/com/zerotrace/auth_server/detector/AIAnalyzer.java`

Purpose:

- runs the threat detector model

What it does now:

- launches Python
- executes `predict.py`
- loads the supplied `anomaly_model.pkl`
- sends the six traffic features
- receives `ANOMALY` or `NORMAL`
- converts the result into the app's `ThreatAnalysisResult` object

### `auth-server/src/main/resources/models/anomaly_model.pkl`

Purpose:

- the actual anomaly model supplied for the threat detector

### `auth-server/src/main/resources/models/predict.py`

Purpose:

- Python bridge used to invoke the model

What it does:

- loads the `.pkl`
- constructs the NumPy feature array
- runs prediction
- emits a machine-readable result string

Why the threat module matters overall:

- this is how the system detects abnormal traffic patterns at the relay layer

---

## 5. End-to-End Request Flows

This section is the most important for presentation and viva.

## 5.1 Registration Flow

### Step-by-step

1. User opens the client.
2. The client uses `KeyManager` to load or create the RSA keypair locally.
3. The client sends:
   - username
   - password
   - public key
   - organization
4. The server receives the request in the auth controller and forwards to `UserService`.
5. `UserService`:
   - validates the request
   - hashes the password with bcrypt
   - stores the public key
   - sets organization and role
   - auto-promotes the first org user to admin
6. `SessionTokenService` issues a token.
7. The client stores the token locally through `SessionManager`.

### Key architectural point

Registration is not just identity creation. It is also public-key onboarding.

---

## 5.2 Login Flow

### Step-by-step

1. User enters username and password.
2. Client loads local keypair.
3. Client sends username, password, and current public key.
4. `UserService.login(...)`:
   - validates credentials
   - increments failed attempts on bad password
   - resets failed count on success
   - updates stored public key if needed
   - updates `lastKnownIp` and `lastLoginAt`
5. `SessionTokenService` issues a fresh token and revokes older active tokens.
6. Client stores the token/session.

### Key architectural point

The login flow doubles as a public-key resync mechanism.

---

## 5.3 Send Message Flow

### Step-by-step

1. Sender types plaintext in the UI.
2. UI calls `sendSecureMessage(...)`.
3. Client asks the server for the receiver's public key.
4. `EncryptionService.encryptForRelay(...)`:
   - generates AES key
   - encrypts plaintext with AES
   - encrypts AES key with receiver RSA public key
   - hashes plaintext
   - signs hash with sender private key
5. Client sends only the encrypted packet to `/message/send`.
6. `MessageRelayService.relay(...)`:
   - checks that authenticated user matches sender
   - loads sender and receiver users
   - enforces same-org policy
   - enforces audit-approval rules if mode is `AUDIT`
   - extracts threat features and runs the AI detector
   - stores encrypted message
7. Server returns:
   - message id
   - mode
   - threat analysis verdict and detail
8. Client shows:
   - normal send status for normal traffic
   - bright red detection banner for anomalies

### Key architectural point

The server never encrypts plaintext for the user. The client does all encryption before sending.

---

## 5.4 Receive Message Flow

### Step-by-step

1. Receiver clicks refresh or opens chat.
2. Client calls `/message/inbox/{username}`.
3. Server returns still-undelivered messages.
4. On first delivery, `MessageRelayService` sets:
   - `deliveredAt`
   - `expiresAt = deliveredAt + ttlSeconds` for private messages
5. Client receives ciphertext packets.
6. `EncryptionService.decryptFromRelay(...)`:
   - decrypts AES key with receiver private key
   - decrypts message with AES
   - verifies sender signature
7. If signature fails, the client marks it as tampered and rejects it.
8. If valid, UI displays the plaintext.

### Key architectural point

The server knows message lifecycle metadata, but the receiver's client performs the actual decryption and integrity verification.

---

## 5.5 TTL Flow

### Intended behavior

For `PRIVATE` messages:

1. sender sets a TTL in seconds
2. receiver receives and opens/fetches the message
3. TTL countdown starts from delivery time
4. once the TTL window ends, the received message disappears

### Server role

The server sets:

- `deliveredAt`
- `expiresAt`

### Client role

The UI maintains a live sweep timer and removes delivered private messages once:

- `deliveredAt + ttlSeconds <= now`

### Key architectural point

TTL is not a cosmetic timer. It is a lifecycle rule backed by both:

- server-side visibility windows
- client-side live disappearance

---

## 5.6 Audit Mode Flow

### Intended behavior

For `AUDIT` messages:

- no private TTL countdown
- stored up to 7 days
- available only for approved users
- export allowed only after admin approval

### Step-by-step

1. sender chooses `AUDIT`
2. server verifies both sender and receiver are approved for audit mode
3. server stores encrypted message with `auditRetainUntil`
4. receiver can access it within the policy window
5. receiver can request export
6. org admin can approve export
7. export request expires in 48 hours if not approved

### Key architectural point

Audit mode is an organizational governance feature layered on top of encrypted messaging, not a separate messaging system.

---

## 6. Security Model

This section is important for any "is this secure?" question.

## 6.1 What the Server Can and Cannot See

### The server can see

- usernames
- organizations
- public keys
- metadata like sender, receiver, created time, TTL, mode
- encrypted payload sizes
- traffic patterns used for anomaly detection

### The server cannot normally see

- plaintext messages
- client private keys

### Caveat

This assumes the client is trusted and that keys are managed correctly on the client.

## 6.2 Password Security

Passwords are stored as:

- bcrypt hashes

They are not stored as plaintext.

## 6.3 Key Security

Private keys are stored:

- only on the client machine

Public keys are stored:

- on the server for lookup and routing

## 6.4 Integrity Security

Message integrity is enforced by:

- SHA-256 hashing
- RSA-based signature verification

If the payload is tampered with:

- the client rejects it during decryption/verification

## 6.5 Session Security

Session state uses:

- opaque random bearer tokens
- stateless server request authentication
- expiry and per-user revocation of older tokens

---

## 7. Threat Detection Architecture

This section matters because your professor may ask what the "AI" actually does.

## 7.1 Feature Extraction

The anomaly detector uses six features:

1. message velocity
2. average time gap between sends
3. message size
4. number of distinct peers
5. failed login attempts
6. IP switching count

These are assembled by:

- `ThreatDetectionService`
- `FeatureExtractor`

## 7.2 Model Execution

Current execution path:

1. Java backend computes numeric features.
2. `AIAnalyzer` launches Python.
3. Python loads `anomaly_model.pkl`.
4. Python predicts `ANOMALY` or `NORMAL`.
5. Python returns a score and label.
6. Java wraps that into `ThreatAnalysisResult`.

## 7.3 UI Output

The client renders detection results in two ways:

- status text after sending
- bold red message-level alert banner on the chat bubble

This is intentionally loud because the goal is visible demonstration, not subtle UX.

## 7.4 Why the Detector Is on the Server

The server sees:

- aggregate traffic behavior
- login failures
- peer fan-out
- IP churn

The client cannot reliably see those global patterns.

Therefore:

- threat detection is best enforced server-side
- UI only visualizes the result

---

## 8. Data Model Map

This section helps with "where is it stored?" questions.

## 8.1 User Table

Entity:

- `User.java`

Stores:

- username
- password hash
- organization
- public key
- role
- audit/export flags
- failed login count
- last IP
- login timestamps

## 8.2 Session Token Table

Entity:

- `SessionToken.java`

Stores:

- username
- token
- expiry
- revocation state

## 8.3 Encrypted Message Table

Entity:

- `EncryptedMessage.java`

Stores:

- sender
- receiver
- encrypted message
- encrypted AES key
- signature
- iv
- mode
- ttlSeconds
- createdAt
- deliveredAt
- expiresAt
- auditRetainUntil

## 8.4 Audit Export Request Table

Entity:

- `AuditExportRequestEntity.java`

Stores:

- requesting username
- organization
- lookback window
- approval state
- expiry state
- timestamps

---

## 9. Database and Local Storage Locations

## 9.1 Backend Database

The default backend uses an H2 file-based database.

Meaning:

- it runs embedded with the Spring Boot app
- it stores data in local files
- it does not need a separate MySQL/PostgreSQL server for the demo

Project location:

- `auth-server/data`

## 9.2 Client Local Storage

Private keys:

- `%USERPROFILE%\.zerotrace\keys\<username>\`

Session:

- `%USERPROFILE%\.zerotrace\session.properties`

Exports:

- `%USERPROFILE%\.zerotrace\exports\`

---

## 10. Controllers, Services, Repositories Pattern

The backend follows a typical Spring layered architecture.

## 10.1 Controllers

Controllers expose HTTP endpoints.

Examples:

- `AuthController`
- `MessageController`
- `GovernanceController`

Responsibility:

- map requests to service methods
- keep controller code thin

## 10.2 Services

Services contain business logic.

Examples:

- `UserService`
- `SessionTokenService`
- `MessageRelayService`
- `GovernanceService`
- `ThreatDetectionService`

Responsibility:

- enforce rules
- orchestrate repositories
- return domain results

## 10.3 Repositories

Repositories talk to the database.

Examples:

- `UserRepository`
- `SessionTokenRepository`
- `EncryptedMessageRepository`
- `AuditExportRequestRepository`

Responsibility:

- persistence and custom query methods

This separation is worth explaining because it shows architectural discipline.

---

## 11. UI Architecture

The client UI is not split into FXML files. It is built programmatically in JavaFX.

Main UI file:

- `ZeroTraceFinalApp.java`

It builds:

- login screen
- top navigation bar
- left-side rail
- contacts panel
- chat area
- governance pages
- settings pages

### State held in the UI

- current session
- selected contact
- active view
- in-memory chat history
- current theme

### Why the UI matters architecturally

The UI is not just display. It also coordinates:

- sending
- refreshing
- threat popup behavior
- live TTL pruning

---

## 12. Deployment and Packaging Model

## 12.1 Backend

The server can be run:

- locally with Spring Boot
- publicly through Cloudflare tunnel
- later through Docker or a VPS

Important deployment files:

- `auth-server/Dockerfile`
- `auth-server/docker-compose.yml`
- `auth-server/docker-compose.public.yml`
- `auth-server/Caddyfile`
- `auth-server/src/main/resources/application-prod.properties`

## 12.2 Client

The client can be run:

- as a jar
- as the packaged Windows app image

Packaged Windows output:

- `ZeroTrace_Core/dist/ZeroTrace/ZeroTrace.exe`
- `ZeroTrace_Core/dist/ZeroTrace-Windows.zip`

## 12.3 Current Demo Hosting Model

For the demo:

- the backend runs on the local laptop
- Cloudflare quick tunnel exposes it publicly
- teammates use the packaged client and paste the tunnel URL

Important limitation:

- this is publicly reachable, but not a permanent production deployment

---

## 13. Known Design Tradeoffs and Limitations

Be honest here. This helps with trick questions.

## 13.1 Strengths

- end-to-end encryption direction is correct
- private keys stay client-side
- passwords are hashed
- token auth is in place
- org-level governance exists
- audit export approval flow exists
- live TTL behavior exists
- anomaly detection exists and is visible in UI

## 13.2 Limitations

- current public hosting path uses a temporary Cloudflare quick tunnel
- Windows package is polished; Mac native package is not built
- client chat history across sessions is lighter than a full canonical conversation archive
- Python dependency exists for the threat model runtime
- H2 is convenient for demo/development, but PostgreSQL/MySQL would be stronger for production

## 13.3 Best honest phrasing

> This is a strong working secure-messaging prototype with governance and anomaly detection, but the production-hardening layer would still need more deployment and operational work.

---

## 14. How to Explain the Project in 2 Minutes

Here is a strong summary you can say almost as-is:

> ZeroTrace is a secure messaging system split into a JavaFX desktop client and a Spring Boot backend. The client generates RSA keys locally, keeps private keys on the device, encrypts plaintext with AES, encrypts the AES key with the receiver's RSA public key, and signs the message before sending. The server never needs plaintext to relay messages. It stores encrypted packets, authenticates users with bearer tokens, enforces organization and audit policies, applies TTL to private messages, and runs anomaly detection on outbound traffic. On the receiver side, the client decrypts the message locally and verifies the signature. For audit mode, the server retains encrypted messages for up to seven days and supports admin-approved exports. We also integrated a Python-backed anomaly model that raises visible red flags on suspicious traffic in the chat UI.

---

## 15. How to Sound Like You Know the Code

The trick is to answer from structure, not from panic.

Use this formula:

1. say the file/class purpose
2. say the main logic
3. say why it exists in the architecture

### Example 1

Question:

"What does `MessageRelayService` do?"

Good answer:

> `MessageRelayService` is the core backend message lifecycle service. It validates sender and receiver policy, stores encrypted messages, applies private TTL or audit retention rules, runs the threat detector on outbound traffic, and serves inbox/history packets back to the client.

### Example 2

Question:

"Where does encryption happen?"

Good answer:

> Encryption happens on the client in `EncryptionService`. The client generates an AES key, encrypts the plaintext, encrypts the AES key with RSA, signs the message, and sends only the encrypted packet to the server.

### Example 3

Question:

"Where is the AI?"

Good answer:

> The AI detection is server-side. `ThreatDetectionService` computes the traffic features, `FeatureExtractor` packages them, and `AIAnalyzer` runs the Python predictor using the supplied `.pkl` model. The result is then sent back to the client UI for visual alerting.

---

## 16. Likely Viva Questions and Strong Answers

### "Why does the server need the public key?"

Because senders need a trusted way to fetch the receiver's public key before encrypting the AES session key.

### "Why not store private keys on the server?"

That would weaken the end-to-end model. The design goal is that only the user device can decrypt messages.

### "What proves message integrity?"

The signature verification done during `decryptFromRelay(...)`.

### "What exactly is stored in the database for a message?"

Encrypted message, encrypted AES key, signature, IV, sender/receiver metadata, mode, TTL, and timestamps.

### "Can admins read the plaintext in audit mode?"

Not automatically. Audit mode retains encrypted packets and controls export rights, but decryption still depends on the cryptographic design and key availability on the client side.

### "How does TTL really work?"

For private messages, the delivery moment is recorded and the TTL countdown starts from there. The UI removes the received message live after the TTL window expires, and the server also uses expiry timestamps for visibility.

### "What does the anomaly detector actually inspect?"

Traffic statistics, not message semantics: send rate, send gap, message size, fan-out, login failures, and IP changes.

### "Is this production ready?"

It is production-shaped, but not production-hardened. The architecture is solid for a prototype, but long-term deployment, ops hardening, and a more permanent hosting model would still be needed.

---

## 17. What to Memorize If You Have Limited Time

If you only have time to memorize a subset, memorize these:

### The story

- client encrypts
- server relays and enforces policy
- client decrypts

### The core backend files

- `UserService`
- `SessionTokenService`
- `MessageRelayService`
- `GovernanceService`
- `ThreatDetectionService`
- `AIAnalyzer`

### The core client files

- `Main`
- `AuthClient`
- `KeyManager`
- `EncryptionService`
- `ZeroTraceFinalApp`

### The major concepts

- RSA for key exchange/signatures
- AES for message encryption
- bcrypt for password hashes
- bearer tokens for sessions
- TTL for private messages
- audit retention for governed storage
- threat detector for network anomaly scoring

---

## 18. Closing Frame

If you are challenged tomorrow, the safest framing is:

> I understand the project in terms of architecture, trust boundaries, message lifecycle, and code ownership. The key classes are organized so that the client owns keys and cryptography, while the server owns identity, policy, storage, and anomaly detection.

That answer is strong because it is architectural, specific, and true.

