# Demo 1 — MCP: Extending Codex Capabilities

Covers outline §6. Prerequisite: `demo-guides/00-setup.md` and
`01-overview.md` done (repo trusted, Postgres up, schema loaded).

What you're demonstrating: MCP is just another tool source Codex can pull
from — but one that runs as its own process, with its own trust boundary,
and its own cost to every single model call, whether you use it that turn
or not.

## Step 1 — See the server Codex already knows about

`.codex/config.toml` in this repo has:

```toml
[mcp_servers.translator_db]
command = "npx"
args = ["-y", "@modelcontextprotocol/server-postgres", "postgresql://translator:translator@localhost:5432/translator"]
```

Open Codex in the repo (`codex`) and run:

```
/mcp
```

You should see `translator_db` listed as connected, exposing exactly **one**
tool: `query` ("Run a read-only SQL query"). That's the whole surface area —
this reference server deliberately does one thing. (It's the archived
`@modelcontextprotocol/server-postgres` package — fine for a local demo, not
actively maintained; say so if someone asks.)

You can confirm the same thing outside any session, no Codex prompt needed:
```bash
codex mcp list
codex mcp get translator_db
```
should show `translator_db`, `Status: enabled`, `command: npx`, the
`startup_timeout_sec`/`tool_timeout_sec` from `config.toml`.

## Step 2 — Execute a simple MCP tool call

Prompt Codex:

> Using the `translator_db` MCP tool, run a read-only query that lists the
> `name` and `description` of every row in the `roles` table.

Expected: Codex calls the `query` tool (you'll see it in the transcript as
something like `mcp__translator_db__query` — MCP tools show up alongside
`Bash` and `apply_patch` as just another named tool, not a special case),
and comes back with the 5 real seeded roles (Developer, Tech Lead, BA, PM,
QA/Tester) in Vietnamese. First call may prompt for approval — that's the
per-tool `approval_mode` from `config.toml` (unset here, so it falls back to
the ambient `approval_policy`).

Point out: nothing about *this* app changed. You added ~10 lines of TOML and
Codex gained a new capability — a live read path into the translator's own
database — with no code written.

## Step 3 — Context cost, made visible

Every model call includes the schema of every enabled tool, MCP or not —
that's the "context cost of MCP" line in the outline. One honest note
before you run this: `codex debug prompt-input` (Session 1 §1's tool)
renders the assembled *instructions* (AGENTS.md, skills list, permissions
text) — checked directly against a real build of this kit, toggling
`mcp_servers.translator_db.enabled` produces **no difference** in that
dump. Tool schemas (what actually costs the tokens) are sent separately,
as part of the turn's tools list, not in this instructions dump — so don't
present `prompt-input` as showing the MCP cost.

What *is* real and checkable:

1. In the TUI, `/mcp` (Step 1) shows you the live tool and its JSON
   `inputSchema` — that block, however small here, is what gets attached to
   every single turn from now on, whether or not that turn calls it.
2. The on/off switch is checkable non-interactively and does change: set
   `enabled = false` under `[mcp_servers.translator_db]` in `.codex/config.toml`,
   then:
   ```bash
   codex mcp list
   ```
   `Status` flips to `disabled`. Set it back to `true` when done.
3. The scaling argument is the one to say out loud, not the one to try to
   measure precisely with this CLI: a server with 20 tools instead of 1
   attaches 20 schema blocks instead of 1, every turn, for the rest of the
   session — enable servers for what you're using this session, not "just
   in case."

## Step 4 — MCP vs Codex's sandbox (the trust-boundary demo)

This is the part that surprises people: Codex's own sandbox does not wrap
an MCP server's process. That's not an inference from behavior — it's
visible in Codex's own source (`rmcp-client`'s stdio launcher spawns the
MCP server with a plain `Command::new(...)`, no sandbox, vs. the shell
tool's exec path, which always goes through a sandboxing wrapper).

Prove it with a **filesystem write**, not a network connection — see
`00-setup.md`'s "Known limitation" note for why: on native Windows, Codex's
"block network" enforcement is frequently a no-op (either nothing is
applied at all, or it's only an env-var hint that a tool opening a raw
socket, like `psql`, simply ignores), so a network-based version of this
step can silently pass when it should fail. A file write is checked by the
OS's own permission system on every platform Codex runs on — that's the
version to actually put in front of an audience.

### 4a — prove it without needing a live Codex turn at all

`codex sandbox` runs one command under a given sandbox config directly —
no model, no auth needed, so this works even before anyone's logged in.
Use whichever form matches where you're actually running (mixing them up
matters here — see the note below):

Inside WSL2, or on macOS/Linux:
```bash
codex sandbox -c 'sandbox_mode="read-only"' -- sh -c 'echo test > ./sandbox_write_test.txt'
```
On native Windows (PowerShell/cmd, not WSL2):
```powershell
codex sandbox -c 'sandbox_mode="read-only"' -- cmd.exe /c "echo test > sandbox_write_test.txt"
```
Expect this to fail — `Permission denied` (bash) or `Access is denied.`
(cmd) — no file created either way (confirmed on both: exit code 1,
`sandbox_write_test.txt` never appears; `Remove-Item`/`rm` it if a prior
attempt did create one). `read-only` means exactly that; the shell tool's
exec path always goes through the OS-level sandbox wrapper, on every
platform, unlike the network case above.

One Windows-specific gotcha, in case you hit it while adapting this: the
Windows form must use `cmd.exe` (or a full path to `sh.exe`), not a bare
`sh` — `codex sandbox` spawns the child directly via
`CreateProcessAsUserW`, which (unlike a normal shell) does **not** search
`PATH` for an unqualified name, so a bare `sh` fails with "the system
cannot find the file specified" before the sandbox even gets a chance to
allow or deny anything. `cmd.exe` always resolves because
`C:\Windows\System32` is searched unconditionally.

### 4b — see it with an actual session

1. Temporarily tighten the sandbox. In `.codex/config.toml`:
   ```toml
   sandbox_mode = "read-only"
   ```
   Restart `codex` in the repo.
2. Ask Codex to write a file directly, via a normal shell command:
   > Run `echo test > ./sandbox_write_test.txt` in the shell.

   Expect this to be blocked or need explicit approval, for the same
   permission-denied reason as 4a.
3. Now ask Codex to repeat Step 2's query, through MCP instead:
   > Using the `translator_db` MCP tool, run `select count(*) from roles;`

   Expect this to succeed, with no sandbox denial at all —
   `sandbox_mode = "read-only"` never touched this process. The
   `translator_db` server was spawned once, outside Codex's sandbox wrapper
   entirely, the moment it started; tightening the *shell* sandbox
   afterward doesn't retroactively apply to it.
4. Set `sandbox_mode` back to `"workspace-write"` when you're done.

Say the quiet part out loud here: an MCP server is a trusted program with
its own execution capabilities (outline §6), not a sandboxed extension of
Codex. Anyone who can edit `.codex/config.toml` — or anyone who published
the npm package in `command`/`args` — has exactly the access that process
has, sandbox settings notwithstanding. That's why "more MCP servers" is a
bigger trust surface, not just a bigger context bill.
