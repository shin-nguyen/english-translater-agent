# Pre-work — set up your machine before Session 2

Do this **before** the session, on your own time (~15–20 minutes). Nothing
here depends on the workshop repo's content — it's generic Codex CLI setup
plus getting one local database running. If you get stuck, the
Troubleshooting section at the bottom covers the most common blockers; if
you're still stuck, message the organizer before the day, not during it.

You'll end the pre-work with: Codex CLI installed and logged in, Docker
running, and the demo repo cloned with a local Postgres already seeded — so
Session 2 itself starts straight at the demos (`01-overview.md` onward),
not at installers.

## 0. What you need

- A laptop you can install software on (admin/sudo rights).
- Either a ChatGPT account with Codex access (Plus, Team, Enterprise, or an
  org that's enabled it), **or** an OpenAI API key. Either works — see
  step 2. If you're not sure which your organization uses, ask before the
  session; don't spend the pre-work window guessing.
- About 1 GB of free disk space (Docker image + npm packages).

## 1. Install prerequisites

Pick your OS. The end state either way: a working `node`, `npm`, `git`,
`docker`, and `docker compose` on the command line.

### macOS

```bash
# Homebrew, if you don't have it: https://brew.sh
brew install node@20 git
brew install --cask docker   # then open Docker.app once so it finishes setup
```
`git` usually already ships with macOS (Xcode Command Line Tools) — `brew
install git` is harmless if you already have one.

### Windows

Codex CLI (and this repo's `docker compose`/`psql` steps) expect a
Linux-like shell — **use WSL2**, not raw PowerShell/cmd:

1. In an elevated PowerShell: `wsl --install` (installs WSL2 + Ubuntu),
   then reboot if asked.
2. Open the "Ubuntu" app from the Start menu once to finish first-time
   setup (pick a Linux username/password — separate from your Windows
   login).
3. Install [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/),
   then in Docker Desktop → Settings → Resources → WSL Integration, enable
   integration for your Ubuntu distro.
4. From here on, do **every** command in this pre-work (and in the demos)
   inside the Ubuntu/WSL2 terminal, not PowerShell. Inside that Ubuntu
   shell:
   ```bash
   sudo apt update && sudo apt install -y nodejs npm git
   node --version   # if this is older than 18, see the nvm note below
   ```

### Linux

```bash
# Debian/Ubuntu
sudo apt update && sudo apt install -y git
# Docker Engine + Compose plugin: follow https://docs.docker.com/engine/install/
# (the distro's own docker.io package is often an old version — prefer Docker's own repo)
sudo usermod -aG docker "$USER"   # then log out/in once so it takes effect
```
For Node, any distro: prefer [nvm](https://github.com/nvm-sh/nvm) over an
old distro package:
```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
# open a new shell, then:
nvm install 20
```

### Verify, whichever OS

```bash
node --version     # v18+ (v20+ preferred)
npm --version
git --version
docker --version
docker compose version
```
If any of these error instead of printing a version, stop and fix that one
before moving on — everything below assumes all four work.

## 2. Install Codex CLI

```bash
npm install -g @openai/codex
codex --version
```
This kit was written and verified against **0.154.0**. If you get a much
newer version and something in the demos behaves differently, that's a
real signal, not necessarily a mistake — flag it to the group, it might
become a discussion point in `05-demo-putting-together.md` about the
project moving fast.

## 3. Log in

Two ways — use whichever matches how your organization gives you Codex
access. If you don't know, try (a) first; it's the simpler path.

**(a) ChatGPT login** (Plus / Team / Enterprise / Business seat with Codex):
```bash
codex login
```
This opens a browser to sign in with your ChatGPT account and hands a
token back to the CLI. No key to copy/paste.

**(b) API key** (you have, or your org gives you, an OpenAI API key):
```bash
codex login --api-key "sk-..."
```
or set it as an environment variable before running `codex` (check
`codex login --help` for the exact accepted env var name on your installed
version, since this has changed across releases).

**Verify either way:**
```bash
codex doctor
```
Look at the `auth` line under "Notes" — it should NOT say "no Codex
credentials were found." If it does, login didn't take; re-run step 3.

## 4. Get the demo repo running locally

```bash
git clone https://github.com/shin-nguyen/english-translater-agent.git
cd english-translater-agent
cp .env.example .env
```

Fill in the two required secrets in `.env` (throwaway local values — never
reuse real secrets here):
```bash
python3 - <<'PY'
import secrets, base64, pathlib
p = pathlib.Path(".env")
t = p.read_text()
t = t.replace("APP_JWT_SECRET=", "APP_JWT_SECRET=" + base64.b64encode(secrets.token_bytes(32)).decode())
t = t.replace("AI_MODEL_ENCRYPTION_KEY=", "AI_MODEL_ENCRYPTION_KEY=" + base64.b64encode(secrets.token_bytes(32)).decode())
p.write_text(t)
PY
```
(No `python3` handy? By hand: run `openssl rand -base64 32` twice and paste
the two values into `.env` yourself.)

Start just the database and load the real schema (the demos don't need the
Java backend or React frontend running):
```bash
docker compose up -d postgres
```
Wait a few seconds for it to report healthy, then:
```bash
for f in backend/src/main/resources/db/migration/V*.sql; do
  docker compose exec -T postgres psql -U translator -d translator -f - < "$f"
done
```
(This runs `psql` *inside* the Postgres container via `docker compose
exec`, so you don't need a local `psql` client installed at all — if you
do have one locally and prefer it: `PGPASSWORD=translator psql -h
localhost -U translator -d translator -f "$f"` works identically.)

## 5. Trust the project in Codex

```bash
codex
```
The first time Codex opens a folder it hasn't seen, it asks whether to
trust it — say yes. Project-level config in this repo (`.codex/config.toml`,
the exec-policy rules, the hooks) only takes effect once trusted. Exit
(`Ctrl+D` or `/exit`) once you've confirmed the trust prompt.

## 6. Self-check — confirm you're actually ready

Run each of these and compare against the expected result. Don't move on
until all four are green; this is exactly what `01-overview.md` will ask
you to re-confirm at the start of the session, so doing it now means
Session 2 starts on time instead of everyone debugging installs together.

| Check | Command | Expect |
|---|---|---|
| Codex installed | `codex --version` | Prints a version (e.g. `codex-cli 0.154.0`) |
| Logged in | `codex doctor` → `auth` line | Does **not** say "no Codex credentials were found" |
| Config loads clean | `codex doctor` → "Configuration" section | `config.toml parse: ok`, `MCP servers: 1` |
| Database up | `docker compose ps` (from repo root) | `translator-postgres` shows `running`/`healthy` |
| MCP server registered | `codex mcp list` | `translator_db` row, `Status: enabled` |

If `codex doctor` instead prints an `Error loading config.toml: ...` line,
don't debug it blind — that's a known failure shape covered directly in
`.codex/config.toml`'s own top comment (a TOML table-scoping gotcha that
bit an earlier version of this exact file); read that first.

## Troubleshooting

- **`npm install -g` fails with `EACCES`/permission errors** (common on
  macOS/Linux if Node was installed via the OS package manager as root):
  switch to `nvm` (see step 1's Linux note; works on macOS too) instead of
  fighting npm's global-install permissions — don't `sudo npm install -g`,
  it causes more permission problems later.
- **WSL2: `docker` not found inside Ubuntu** — Docker Desktop's WSL
  integration toggle (step 1, Windows) needs your specific distro enabled
  and Docker Desktop actually running on the Windows side; restart the
  Ubuntu terminal after enabling it.
- **Corporate network blocks `codex login`'s browser flow or `npm
  install`** — try a personal hotspot for the pre-work if your office
  network filters unfamiliar domains; if it's still blocked at home, that's
  worth flagging to the organizer ahead of time, not discovering during the
  session.
- **Port 5432 already in use** (another local Postgres running) —
  `docker compose up -d postgres` will fail to bind the port; either stop
  the other Postgres, or add `SERVER_PORT`-style overrides / change the
  `ports:` mapping in `docker-compose.yml` for your local copy only.
- **`codex doctor` shows `reachability`/`websocket` failures** — those two
  lines are about Codex's own connectivity check hitting your network; as
  long as `auth` doesn't say "no credentials," and later a real `codex`
  prompt actually gets a response, you're fine — some corporate proxies
  fail the specific diagnostic probe without blocking real traffic.
