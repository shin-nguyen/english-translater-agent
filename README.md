# EN Translator for IT

A web app that helps Vietnamese IT professionals (devs, tech leads, BAs, PMs, QAs) translate
and polish text into natural, professional English for meetings, requirement clarification,
Jira comments, team chat, and email/reports — with AI-generated grammar/style feedback and a
searchable note library to review past translations.

## Tech stack

- **Backend**: Java 21, Spring Boot 3.3 (Web, Data JPA, Validation), Maven
- **Database**: PostgreSQL 16, schema managed with Flyway
- **AI**: provider-agnostic `TranslationService` interface with two implementations —
  `ClaudeTranslationServiceImpl` (Anthropic Messages API) and `OpenRouterTranslationServiceImpl`
  (OpenRouter's OpenAI-compatible Chat Completions API, default model `openai/gpt-oss-20b:free`).
  Active provider is chosen at runtime via the `translation.provider` property
  (`TRANSLATION_PROVIDER` env var, `anthropic` or `openrouter`, defaults to `openrouter`).
- **Frontend**: React 19 + TypeScript, Vite, Tailwind CSS v4, `lucide-react`, `react-router-dom`

## Assumptions made (per the original spec's own guidance to proceed rather than block on every ambiguity)

- **No auth in v1.** This is treated as a single-user local tool. All `/api/**` endpoints are
  open with no login/API-key gate. Adding Spring Security + JWT later is a additive change and
  doesn't require reshaping anything already built.
- **Default AI provider is OpenRouter** (`openai/gpt-oss-20b:free`), overridable via `OPENROUTER_MODEL`.
  Set `TRANSLATION_PROVIDER=anthropic` to switch back to calling Claude directly
  (model defaults to `claude-sonnet-5`, overridable via `ANTHROPIC_MODEL`).
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
│       ├── role/            # Role CRUD (developer, tech lead, BA, PM, QA...)
│       ├── context/         # Context CRUD (meeting, Jira comment, team chat...)
│       ├── note/            # Saved translations (CRUD + filter/search)
│       ├── translation/     # /api/translate + Claude/OpenRouter integrations
│       ├── config/          # Anthropic + OpenRouter clients, CORS config
│       └── common/          # Global exception handling
└── frontend/                # Vite + React + TS SPA
    ├── Dockerfile
    ├── nginx.conf            # Serves the built SPA in the frontend image
    └── src/
        ├── api/              # fetch client + typed endpoints
        ├── components/       # shared UI (Layout, Modal, Toast, Badge, ...)
        ├── pages/             # TranslatePage, NotesPage, NoteDetailPage, RolesContextsPage
        └── hooks/
```

## Prerequisites

- Java 21, Maven 3.9+ (only needed if running the backend without Docker)
- Node.js 20+ and npm (only needed if running the frontend without Docker)
- Docker + Docker Compose (for Postgres, and/or the full containerized stack)
- An OpenRouter API key ([openrouter.ai/keys](https://openrouter.ai/keys)) to actually exercise
  `/api/translate` end to end with the default provider — or an Anthropic API key
  ([console.anthropic.com](https://console.anthropic.com)) if you switch `TRANSLATION_PROVIDER` to `anthropic`

## Configuration (do this first)

Every way of running this app (bare-metal, Docker Compose, or a manual deploy) is driven by the
same env vars — set these up before you start anything.

| Variable                    | Default                          | Purpose                                                          |
|------------------------------|-----------------------------------|--------------------------------------------------------------------|
| `TRANSLATION_PROVIDER`       | `openrouter`                     | `openrouter` or `anthropic` — picks which `TranslationService` bean loads |
| `OPENROUTER_API_KEY`         | *(none)*                        | **Required** when using OpenRouter (the default)                 |
| `OPENROUTER_MODEL`           | `openai/gpt-oss-20b:free`        | Any model slug OpenRouter supports                                |
| `OPENROUTER_BASE_URL`        | `https://openrouter.ai/api/v1`   | Rarely needs changing                                             |
| `OPENROUTER_TIMEOUT_SECONDS` | `20`                              | HTTP timeout for the OpenRouter call                              |
| `OPENROUTER_MAX_TOKENS`      | `1536`                           | Max response tokens                                               |
| `OPENROUTER_SITE_URL`        | *(none)*                        | Optional, sent as `HTTP-Referer` for OpenRouter attribution       |
| `OPENROUTER_APP_NAME`        | `EN Translator for IT`           | Optional, sent as `X-Title` for OpenRouter attribution            |
| `ANTHROPIC_API_KEY`          | *(none)*                        | Required only when `TRANSLATION_PROVIDER=anthropic`               |
| `ANTHROPIC_MODEL`            | `claude-sonnet-5`                | Model used for translate/polish                                   |
| `ANTHROPIC_TIMEOUT_SECONDS`  | `20`                              | HTTP timeout for the Claude call                                  |
| `ANTHROPIC_MAX_TOKENS`       | `1536`                           | Max response tokens                                               |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/translator` / `translator` / `translator` | Postgres connection |
| `SERVER_PORT`                | `8080`                           | Backend HTTP port                                                  |
| `CORS_ALLOWED_ORIGINS`       | `http://localhost:5173` (local)  | Comma-separated origins allowed to call the API                  |
| `SPRING_PROFILES_ACTIVE`     | `local`                          | `local` (defaults everything for dev) or `prod` (requires `DB_*` explicitly) |
| `VITE_API_BASE_URL`          | `http://localhost:8080`          | Frontend-only. Baked into the static bundle at **build time** — must be a URL your browser can reach |

Never commit real API keys. Two ways to provide these, depending on how you run the app:

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

Set your OpenRouter API key (required for `/api/translate` to actually call the AI; the rest of
the app works without it) — see [Configuration](#configuration-do-this-first) for the full list
of env vars and how to switch providers:

```bash
# macOS/Linux
export OPENROUTER_API_KEY=sk-or-v1-...

# Windows PowerShell
$env:OPENROUTER_API_KEY = "sk-or-v1-..."
```

Then, from `backend/`:

```bash
mvn spring-boot:run
```

The API starts on `http://localhost:8080` under the `local` Spring profile by default.

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
   Edit `.env` and set at least `OPENROUTER_API_KEY` (or switch `TRANSLATION_PROVIDER=anthropic`
   and set `ANTHROPIC_API_KEY`). See [Configuration](#configuration-do-this-first) for what each
   variable does.

2. **Build and start everything** (Postgres + backend + frontend):
   ```bash
   docker compose up -d --build
   ```
   - Backend: `http://localhost:8080`
   - Frontend: `http://localhost:5173`
   - Postgres: `localhost:5432`

   The backend waits for Postgres's healthcheck before starting, and Flyway migrates/seeds the
   schema automatically — no manual DB setup needed.

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
  -e TRANSLATION_PROVIDER=openrouter \
  -e OPENROUTER_API_KEY=sk-or-v1-... \
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
# edit .env, set OPENROUTER_API_KEY (see Configuration above)
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

Note this only moves the *application* — the actual data (saved notes) lives in the separate
`translator-pgdata` Docker volume, not in these images, so the target PC starts with a fresh,
auto-seeded (roles/contexts only) empty database. To bring existing notes along too, `pg_dump` on
the source and restore on the target instead of/in addition to this.

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
   - A real `OPENROUTER_API_KEY` (or `ANTHROPIC_API_KEY`) from a secrets manager, not a plain env
     var in source control
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

Covers: Role/Context CRUD service logic, Note create/update/search logic, and both translation
service implementations (Claude and OpenRouter) — prompt construction (role/context descriptions
get embedded correctly) and JSON response parsing (clean JSON, markdown-fenced JSON, JSON embedded
in prose, malformed JSON triggering the one-shot retry path).

The frontend currently has no automated tests (not requested in scope); `npm run build` runs a
full TypeScript typecheck plus a production Vite build as a compile-time safety net.

## API summary

```
GET/POST/PUT/DELETE  /api/roles[/{id}]
GET/POST/PUT/DELETE  /api/contexts[/{id}]
POST                 /api/translate              # { text, roleId?, contextId? } -> not persisted
GET/POST             /api/notes                   # GET supports roleId, contextId, keyword, page, size
GET/PUT/DELETE        /api/notes/{id}
```

## Known limitations / what I couldn't fully verify

- I don't have a real `OPENROUTER_API_KEY`/`ANTHROPIC_API_KEY` in this environment, so
  `/api/translate` was verified structurally (request/response plumbing, error handling for a
  failed AI call returns a clean 502 with a friendly message) and via mocked unit tests, but not
  against a live AI response. Once you set a real key, the retry-on-malformed-JSON path and the
  actual translation quality are worth a manual spot check.
- No automated browser/E2E test was run against the UI in this session; the backend was smoke
  tested directly (roles/contexts seed correctly with Vietnamese diacritics intact, note
  create/search round-trips JSONB `alternatives`/`analysis` correctly) and the frontend passes a
  full TypeScript build. The full Docker Compose stack (Postgres + backend + frontend) was also
  built and smoke tested end-to-end in this session. Both dev servers, or `docker compose up`,
  can be used to check the UI by hand.
- The scaffolded frontend's `create-vite` defaulted to Vite 8 (Rolldown-based), whose Windows
  native binding failed to resolve on this machine's Node version (20.17, one minor below what
  that package expects). Pinned to the stable, non-Rolldown `vite@^6` + `@vitejs/plugin-react@^4`
  instead — mature, widely used, no native-binding fragility.
