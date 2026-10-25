# DropCode — Backend Deployment & Operations Guide

## 1. Overview
DropCode is an ephemeral, code-based file-transfer platform designed to prevent permanent file storage while maintaining comprehensive audit, history, subscription, and payment transaction persistence in PostgreSQL / Supabase.

---

## 2. Prerequisites
- **Java 21 LTS**: Required (`java -version`).
- **PostgreSQL 15+ / Supabase**: Supabase Managed Database or local PostgreSQL instance.
- **Firebase Project**: Pre-configured Firebase Authentication (Project ID: `dropcode-7568e`).

---

## 3. Local Setup & Execution

### Step 3.1: PostgreSQL / Supabase Database
In Supabase:
Create a new Supabase project or use an existing one. Tables and initial indexes will be migrated automatically by Flyway on application startup.

### Step 3.2: Environment Configuration
Copy `.env.example` to `.env` or set environment variables:
```bash
SERVER_PORT=4000
DB_URL=jdbc:postgresql://db.[PROJECT-REF].supabase.co:5432/postgres?sslmode=require
DB_USER=postgres
DB_PASSWORD=[YOUR-PASSWORD]
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
STAGING_LOCAL_DIR=./staging-data
STAGING_MASTER_KEY=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
CODE_PEPPER=dropcode-secure-code-pepper-key-minimum-32-chars-long
IP_ENCRYPTION_KEY=12345678901234567890123456789012
```

### Step 3.3: Build & Run with Maven
```bash
cd backend

# Execute automated test suite (25 unit and rule tests)
./mvnw test

# Package executable JAR
./mvnw clean package -DskipTests

# Start the Spring Boot application
java -jar target/dropcode-backend-1.0.0.jar
```
*(On Windows Command Prompt, use `.\mvnw.cmd`)*

The server starts on port `4000`.

- Health Checks:
  - Liveness: `http://localhost:4000/healthz`
  - Readiness (Validates MySQL): `http://localhost:4000/readyz`
- Swagger OpenAPI Documentation: `http://localhost:4000/swagger-ui.html`

---

## 4. Production Deployment on Render / Cloud Platform

### Step 4.1: Render Deployment Configuration
1. **New Web Service**: Connect your GitHub repository.
2. **Root Directory**: `backend`
3. **Environment**: `Java` (or Docker / Native Java 21)
4. **Build Command**:
   ```bash
   ./mvnw clean package -DskipTests
   ```
5. **Start Command**:
   ```bash
   java -XX:+UseG1GC -Xmx1024m -jar target/dropcode-backend-1.0.0.jar
   ```

### Step 4.2: Environment Variables on Cloud
Set the following environment variables in the Render Dashboard:

| Key | Value / Description |
|---|---|
| `SERVER_PORT` | `4000` (or `$PORT`) |
| `DB_URL` | Cloud PostgreSQL / Supabase JDBC URL (e.g. `jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require`) |
| `DB_USER` | Supabase DB username (typically `postgres` or `postgres.<project-ref>`) |
| `DB_PASSWORD` | Supabase DB password |
| `CORS_ALLOWED_ORIGINS` | Production frontend domain (e.g. `https://dropcode.yourdomain.com`) |
| `STAGING_MASTER_KEY` | 64-character hexadecimal encryption key |
| `CODE_PEPPER` | Cryptographic pepper string (min 32 chars) |
| `IP_ENCRYPTION_KEY` | 32-character encryption key for IP auditing |
| `S3_ENABLED` | `false` for local staging buffer or `true` for private AWS S3 bucket |

---

## 5. Security & DPDP Compliance
- **Authenticated IP Encryption**: IP addresses are encrypted at rest with AES-256-GCM. Raw IP addresses are strictly omitted from standard API responses and transfer history.
- **Zero-Trace Payload Purge**: When a receiver finishes downloading a transfer (`DELIVERED`) or upon expiration (`EXPIRED`) or cancellation (`CANCELLED`), actual file bytes are destroyed immediately.
- **HMAC Sharing Code Storage**: 8-character codes are never saved in plaintext; only `HMAC-SHA256(code, CODE_PEPPER)` is persisted.
- **Bearer Token Verification**: Firebase ID tokens are verified cryptographically via Firebase Admin SDK.
