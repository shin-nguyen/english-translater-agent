# EN Translator for IT

A web app that helps Vietnamese IT professionals (devs, tech leads, BAs, PMs, QAs) translate
and polish text into natural, professional English for meetings, requirement clarification,
Jira comments, team chat, and email/reports — with AI-generated grammar/style feedback and a
searchable note library to review past translations.

## Tech stack

- **Backend**: Java 21, Spring Boot 3.3 (Web, Data JPA, Validation, Security), Maven
- **Auth**: stateless JWT bearer tokens (`spring-boot-starter-security` + `jjwt`), BCrypt password
  hashing. Two roles: `ADMIN` and `USER`. The very first account ever signed up becomes `ADMIN`
  automatically; every account after that is a basic `USER`.
- **Database**: PostgreSQL 16, schema managed with Flyway
- **AI**: provider-agnostic `TranslationService` — a single `TranslationServiceImpl` holds the
  shared prompt/parsing/retry logic and dispatches each request to one of two `AiProviderClient`
  strategies (`AnthropicProviderClient` for Anthropic's Messages API, `OpenAiCompatibleProviderClient`
  for any OpenAI-compatible Chat Completions host — OpenRouter, a self-hosted vLLM/llama.cpp/Ollama
  server, etc.) based on which admin-configured **AI model** the caller picked. AI model connections
  (provider, base URL, API key, model id) are managed entirely through the admin UI and stored in
  Postgres — API keys are encrypted at rest (AES-256-GCM) via a JPA attribute converter, never
  exposed back to the client. There are no `TRANSLATION_PROVIDER`/`OPENAI_*`/`ANTHROPIC_*` env vars
  anymore.
- **Frontend**: React 19 + TypeScript, Vite, Tailwind CSS v4, `lucide-react`, `react-router-dom`

## Assumptions made (per the original spec's own guidance to proceed rather than block on every ambiguity)

- **First signup becomes admin.** There's no seeded admin account or invite flow — whoever signs
  up first via `/signup` gets `ADMIN`, everyone after that gets `USER`. Admins can promote/demote,
  enable/disable, and delete other accounts afterward (with a guard preventing the last remaining
  admin from being demoted/disabled/deleted).
- **Notes are fully private per user, including for admins.** There is no admin override to browse
  another user's saved notes — `GET /api/notes/{id}` on someone else's note returns 404 (not 403),
  so existence can't be inferred either.
- **Roles/Contexts (the IT-role concept — Developer/PM/etc., unrelated to auth roles) stay shared
  and open to any logged-in user** — not admin-gated. Any authenticated user (basic or admin) can
  create/edit/delete them via "Roles & Contexts" in the nav.
- **AI model credentials live in the database, not env vars**, entered via the admin-only "AI
  Models" screen. This was a deliberate choice over env-var-based provider config so a running
  instance can support multiple simultaneous models (e.g. one Anthropic + one OpenAI-compatible)
  and let each user pick which one to use per translation, without a restart.
- **Local Postgres runs via Docker Compose** (`docker-compose.yml` at the repo root). If you
  already run Postgres another way, just point `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` at it instead.
- CORS is restricted to `http://localhost:5173` (the Vite dev server) in the `local` profile;
  in `prod` it's driven entirely by the `CORS_ALLOWED_ORIGINS` env var (comma-separated, empty by default).

## Project layout

```
english_translater/
├── docker-compose.yml       # Postgres (+ backend/frontend images) for local/docker use
├── .env.example             # Config template for the docker-compose stack
├── backend/                 # Spring Boot API
│   ├── Dockerfile
│   └── src/main/java/com/example/translator/
│       ├── auth/             # signup/login/me/change-password
│       ├── user/             # AppUser (auth accounts) + admin user management
│       ├── security/         # JWT filter/service, Spring Security config, UserDetails
│       ├── aimodel/          # Admin-managed AI model configs + API-key encryption
│       ├── role/             # Role CRUD (developer, tech lead, BA, PM, QA... — NOT an auth role)
│       ├── context/          # Context CRUD (meeting, Jira comment, team chat...)
│       ├── note/             # Saved translations (CRUD + filter/search), scoped per user
│       ├── translation/      # /api/translate + Claude/OpenAI-compatible provider clients
│       ├── config/           # CORS config
│       └── common/           # Global exception handling
└── frontend/                # Vite + React + TS SPA
    ├── Dockerfile
    ├── nginx.conf            # Serves the built SPA in the frontend image
    └── src/
        ├── api/              # fetch client + typed endpoints
        ├── components/       # shared UI (Layout, Modal, Toast, Badge, AuthProvider, ...)
        ├── pages/             # TranslatePage, NotesPage, NoteDetailPage, RolesContextsPage,
        │                      # LoginPage, SignupPage, AccountPage, AdminUsersPage, AdminAiModelsPage
        └── hooks/
```

## Prerequisites

- Java 21, Maven 3.9+ (only needed if running the backend without Docker)
- Node.js 20+ and npm (only needed if running the frontend without Docker)
- Docker + Docker Compose (for Postgres, and/or the full containerized stack)
- An OpenRouter API key ([openrouter.ai/keys](https://openrouter.ai/keys)) or an Anthropic API key
  ([console.anthropic.com](https://console.anthropic.com)) — not needed to start the app, but
  you'll enter one via the admin "AI Models" screen after your first signup to actually exercise
  `/api/translate` end to end (or point at a self-hosted OpenAI-compatible server instead, which
  usually needs no key at all)

## Configuration (do this first)

Every way of running this app (bare-metal, Docker Compose, or a manual deploy) is driven by the
same env vars — set these up before you start anything.

| Variable                    | Default                          | Purpose                                                          |
|------------------------------|-----------------------------------|--------------------------------------------------------------------|
| `APP_JWT_SECRET`             | *(none, required)*               | Signs/verifies login JWTs. Generate with `openssl rand -base64 32`. Changing it invalidates every issued token. |
| `APP_JWT_EXPIRATION_MINUTES` | `1440`                            | How long a login session lasts before re-login is required (no refresh tokens) |
| `AI_MODEL_ENCRYPTION_KEY`    | *(none, required)*               | AES-256 key encrypting AI model API keys at rest. Generate with `openssl rand -base64 32`. Changing it makes previously-saved keys undecryptable — re-enter them via the admin UI. |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/translator` / `translator` / `translator` | Postgres connection |
| `SERVER_PORT`                | `8080`                           | Backend HTTP port                                                  |
| `CORS_ALLOWED_ORIGINS`       | `http://localhost:5173` (local)  | Comma-separated origins allowed to call the API                  |
| `SPRING_PROFILES_ACTIVE`     | `local`                          | `local` (defaults everything for dev, including dev-only fixed values for the two secrets above) or `prod` (requires everything explicitly) |
| `VITE_API_BASE_URL`          | `http://localhost:8080`          | Frontend-only. Baked into the static bundle at **build time** — must be a URL your browser can reach |

There is no env var for AI provider credentials — those are entered through the admin "AI Models"
screen after your first signup (see [Assumptions](#assumptions-made-per-the-original-specs-own-guidance-to-proceed-rather-than-block-on-every-ambiguity) above).

Never commit real secrets. Two ways to provide these, depending on how you run the app:

- **Bare-metal (Maven/npm directly)** — export them as shell env vars before starting the process
  (see [Running locally](#running-locally) below).
- **Docker Compose** — copy `.env.example` to `.env` at the repo root and fill it in; `docker
  compose` loads `.env` automatically and the values flow into the `backend`/`frontend`
  containers (see [Running with Docker](#running-with-docker) below). `.env` is already
  git-ignored — never commit it.

## Running locally

### 1. Database

```bash
docker compose up -d postgres
```

This starts Postgres on `localhost:5432` (db `translator`, user/password `translator`).
Flyway migrates the schema and seeds default roles/contexts automatically on backend startup.

### 2. Backend

The `local` Spring profile ships fixed, clearly-marked dev-only defaults for `APP_JWT_SECRET` and
`AI_MODEL_ENCRYPTION_KEY`, so you can start the backend with no env vars set at all. For anything
beyond local dev, generate your own (see [Configuration](#configuration-do-this-first)):

```bash
# macOS/Linux
export APP_JWT_SECRET=$(openssl rand -base64 32)
export AI_MODEL_ENCRYPTION_KEY=$(openssl rand -base64 32)

# Windows PowerShell
$env:APP_JWT_SECRET = [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
$env:AI_MODEL_ENCRYPTION_KEY = [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
```

Then, from `backend/`:

```bash
mvn spring-boot:run
```

The API starts on `http://localhost:8080` under the `local` Spring profile by default. Once the
frontend is up (next step), sign up — your first account becomes an admin — then add an AI model
under "AI Models" in the nav before `/api/translate` will actually work.

### 3. Frontend

From `frontend/`:

```bash
npm install
npm run dev
```

Opens on `http://localhost:5173` and talks to the backend at `http://localhost:8080` by default.
To point at a different backend URL, copy `frontend/.env.example` to `frontend/.env` and set
`VITE_API_BASE_URL` (dev mode reads this at runtime via Vite, unlike the Docker build below where
it's baked in at build time).

## Running with Docker

Both `backend/Dockerfile` and `frontend/Dockerfile` are multi-stage builds (Maven/Node build
stage → slim JRE/nginx runtime stage), so the images you ship don't carry build tooling.

### Option A: full stack via Docker Compose (recommended)

1. **Configure first** — from the repo root:
   ```bash
   cp .env.example .env
   ```
   Edit `.env` and set `APP_JWT_SECRET` and `AI_MODEL_ENCRYPTION_KEY` (each `openssl rand -base64 32`
   — `docker-compose.yml` fails fast with a clear message if either is missing). See
   [Configuration](#configuration-do-this-first) for what each variable does.

2. **Build and start everything** (Postgres + backend + frontend):
   ```bash
   docker compose up -d --build
   ```
   - Backend: `http://localhost:8080`
   - Frontend: `http://localhost:5173`
   - Postgres: `localhost:5432`

   The backend waits for Postgres's healthcheck before starting, and Flyway migrates the schema
   automatically — no manual DB setup needed. Sign up at the frontend URL (your first account
   becomes an admin), then add an AI model under "AI Models" before translating.

3. **Rebuild after code changes**:
   ```bash
   docker compose up -d --build backend   # or frontend
   ```

4. **Tear down** (add `-v` only if you also want to wipe the Postgres data volume):
   ```bash
   docker compose down
   ```

### Option B: build/run individual images by hand

Useful if you're pushing images to a registry for deployment rather than running Compose locally.

```bash
# Backend
docker build -t en-translator-backend ./backend
docker run --rm -p 8080:8080 \
  -e APP_JWT_SECRET=$(openssl rand -base64 32) \
  -e AI_MODEL_ENCRYPTION_KEY=$(openssl rand -base64 32) \
  -e DB_URL=jdbc:postgresql://<host>:5432/translator \
  -e DB_USERNAME=translator \
  -e DB_PASSWORD=translator \
  -e SPRING_PROFILES_ACTIVE=prod \
  en-translator-backend

# Frontend — VITE_API_BASE_URL must be set as a BUILD ARG (baked into the static
# bundle), not a runtime -e flag, since it's consumed by Vite at build time.
docker build -t en-translator-frontend \
  --build-arg VITE_API_BASE_URL=https://api.yourdomain.com \
  ./frontend
docker run --rm -p 5173:80 en-translator-frontend
```

### Option C: move a built stack to another PC without a registry

Both `backend`/`frontend` services in `docker-compose.yml` are pinned to explicit image names
(`en-translator-backend:latest`, `en-translator-frontend:latest`) rather than Compose's
folder-derived default — this matters here because it's what lets a `docker load`-ed image get
picked up and *run as-is* on another machine, regardless of what that machine's project folder is
named, without Compose deciding it needs to rebuild.

**On the source PC:**
```bash
docker compose build
docker save -o translator-images.tar en-translator-backend:latest en-translator-frontend:latest
```
The tar is roughly 140MB for both images combined. Copy that file, plus `docker-compose.yml` and
`.env.example`, to the other PC (USB drive, network share, cloud storage) — the `backend/`/`frontend/`
source folders aren't needed there at all, just those three files.

**On the target PC:**
```bash
docker load -i translator-images.tar
cp .env.example .env
# edit .env, set APP_JWT_SECRET and AI_MODEL_ENCRYPTION_KEY (see Configuration above)
docker compose up -d
```
Use `docker compose up -d` **without** `--build` here — that flag would force a rebuild and fail
since there's no source present. Without it, Compose sees the loaded images already tagged
`en-translator-backend:latest`/`en-translator-frontend:latest` and just runs them; Postgres's
own image (`postgres:16-alpine`) still gets pulled from Docker Hub automatically, so the target
PC needs internet for that (or `docker save`/`load` that image too for a fully offline move).

This was verified end-to-end in this session: build → save → delete local images → load →
temporarily remove the `backend`/`frontend` source folders → `docker compose up -d` (no `--build`)
still came up clean and served correctly.

Note this only moves the *application* — the actual data (saved notes, user accounts, AI model
configs) lives in the separate `translator-pgdata` Docker volume, not in these images, so the
target PC starts with a fresh, auto-seeded (roles/contexts only) empty database: you'll need to
sign up again (first signup becomes admin) and re-add AI models. To bring existing data along
instead, `pg_dump` on the source and restore on the target.

## Deploying

The three pieces (Postgres, backend image, frontend image) can go anywhere that runs containers
(a VM with Docker Compose, ECS/Cloud Run/App Runner, a Kubernetes cluster, etc.). Regardless of
target:

1. **Configure before deploying** — set the same env vars from
   [Configuration](#configuration-do-this-first) in your host/platform's secret or env-var
   store. In particular for a real deploy:
   - `SPRING_PROFILES_ACTIVE=prod` (the `prod` profile requires `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`
     explicitly — it has no local defaults, so the app fails fast if the DB isn't configured)
   - `CORS_ALLOWED_ORIGINS` set to your actual frontend origin(s) — the `prod` profile defaults
     this to empty (nothing allowed) until you set it
   - `VITE_API_BASE_URL` set to your public backend URL **at frontend image build time**
   - Real, randomly-generated `APP_JWT_SECRET` and `AI_MODEL_ENCRYPTION_KEY` values from a
     secrets manager, not plain env vars in source control — the `prod` profile has no defaults
     for either, so the app fails fast if they're unset
2. **Build and push images**:
   ```bash
   docker build -t <registry>/en-translator-backend:<tag> ./backend
   docker build --build-arg VITE_API_BASE_URL=https://api.yourdomain.com \
     -t <registry>/en-translator-frontend:<tag> ./frontend
   docker push <registry>/en-translator-backend:<tag>
   docker push <registry>/en-translator-frontend:<tag>
   ```
3. **Run migrations**: nothing manual to do — Flyway runs automatically on backend startup
   against whatever `DB_URL` points at.
4. **Point a real Postgres instance** at `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` (a managed DB
   service is recommended over running Postgres in a container for anything beyond local/staging).
5. Put TLS termination (a reverse proxy / load balancer / platform ingress) in front of both
   services — neither Dockerfile here does HTTPS itself.

## Running tests

```bash
cd backend
mvn test
```

Covers: Role/Context CRUD service logic, per-user Note create/update/search/ownership logic
(including that a note belonging to another user 404s), signup/login/change-password logic
(first-signup-is-admin, duplicate email, wrong-password rejection), admin user management
(last-admin-removal guards), AI model config management (single-default enforcement, blank-API-key
update keeps the old key, dispatch-time enabled/id validation), the API-key encryption converter
(round-trip, fresh IV per call, tamper detection, bad-key-length rejection), and both AI provider
clients (Anthropic Messages API and OpenAI-compatible chat-completions) — prompt construction
(role/context descriptions get embedded correctly) and JSON response parsing (clean JSON,
markdown-fenced JSON, JSON embedded in prose, malformed JSON triggering the one-shot retry path).

The frontend currently has no automated tests (not requested in scope); `npm run build` runs a
full TypeScript typecheck plus a production Vite build as a compile-time safety net.

## API summary

```
POST                 /api/auth/signup             # public
POST                 /api/auth/login               # public
GET                  /api/auth/me                  # authenticated
POST                 /api/auth/change-password     # authenticated

GET/POST              /api/users[/{id}]            # admin only
PATCH                 /api/users/{id}/role         # admin only, { appRole }
PATCH                 /api/users/{id}/status       # admin only, { enabled }
DELETE                /api/users/{id}              # admin only

GET/POST/PUT/DELETE   /api/ai-models[/{id}]        # admin only — full config incl. write-only apiKey
GET                   /api/ai-models/enabled       # authenticated — { id, label } only, no secrets

GET/POST/PUT/DELETE  /api/roles[/{id}]             # authenticated (any role)
GET/POST/PUT/DELETE  /api/contexts[/{id}]          # authenticated (any role)
POST                 /api/translate                # authenticated, { text, roleId?, contextId?, modelConfigId } -> not persisted
GET/POST             /api/notes                    # authenticated, scoped to caller — GET supports roleId, contextId, keyword, page, size
GET/PUT/DELETE        /api/notes/{id}              # authenticated, scoped to caller — 404 (not 403) if it belongs to someone else
```

All `/api/**` endpoints except `/api/auth/signup` and `/api/auth/login` require an
`Authorization: Bearer <token>` header (obtained from signup/login).

## Known limitations / what I couldn't fully verify

- The multi-user/auth/admin-AI-model refactor (signup, login, JWT auth, per-user notes, admin
  user management, admin-configured AI models) was verified via `mvn test` (57 backend tests
  covering the new auth/user/aimodel/translation-dispatch logic) and a clean `npm run build`
  (TypeScript typecheck + production Vite build), but **not yet exercised end-to-end in a running
  browser** in this session — no real AI provider key was available to click through the full
  signup → add model → translate → save note flow live. Worth a manual pass before relying on it:
  sign up, confirm you land as admin, add a real AI model config, translate, save a note, sign up
  a second account and confirm it can't see the first account's notes, and exercise the admin
  user-management actions (promote/demote, enable/disable, delete, including the last-admin guard).
- No automated browser/E2E test exists for the UI (not requested in scope).
- The scaffolded frontend's `create-vite` defaulted to Vite 8 (Rolldown-based), whose Windows
  native binding failed to resolve on this machine's Node version (20.17, one minor below what
  that package expects). Pinned to the stable, non-Rolldown `vite@^6` + `@vitejs/plugin-react@^4`
  instead — mature, widely used, no native-binding fragility.
