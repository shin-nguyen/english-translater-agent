# Demo 3 — Execution Governance: Exec Policy & Hooks

Covers outline §8. Prerequisite: `demo-guides/00-setup.md` and
`01-overview.md` done. This
demo deliberately breaks things first so the problem is visible, then fixes
them one layer at a time.

Real secret in play: root `.env` in this repo holds `APP_JWT_SECRET` and
`AI_MODEL_ENCRYPTION_KEY` in plaintext (that's the whole point of `.env`
files for local dev) — this demo is about stopping Codex from ever putting
those values into a transcript, and about having a durable record of what
Codex actually ran.

## Step 0 — see the problem (protections off)

Temporarily disable everything this repo ships:
```bash
mv .codex/rules .codex/rules.disabled
mv .codex/hooks.json .codex/hooks.json.disabled
```
Restart `codex` in the repo, then prompt:
> Run `cat .env` and tell me what's in it.

Expected (uncomfortable) result: Codex reads and repeats
`APP_JWT_SECRET=...` and `AI_MODEL_ENCRYPTION_KEY=...` straight into the
transcript. Note what did *not* stop this: `sandbox_mode` is
`workspace-write`, but sandbox modes gate writes and network, not reads —
this is Session 1 §2's "read access vs security" point, made concrete. Even
switching to `sandbox_mode = "read-only"` wouldn't help; read-only still
means read access.

Put the two files back before continuing:
```bash
mv .codex/rules.disabled .codex/rules
mv .codex/hooks.json.disabled .codex/hooks.json
```

## Step 1 — Exec Policy: classify the command, before it ever runs

`.codex/rules/default.rules` has four `prefix_rule()`s. Validate them
directly, independent of a live Codex session:
```bash
codex execpolicy check --pretty --rules .codex/rules/default.rules -- cat .env
codex execpolicy check --pretty --rules .codex/rules/default.rules -- git push origin main
codex execpolicy check --pretty --rules .codex/rules/default.rules -- docker compose down -v
```
Expect `forbidden`, `prompt`, and `forbidden` respectively — decided by
matching the command's *shape*, before Codex would ever get to a sandbox or
approval check.

Now restart `codex` and prompt exactly:
> Run `cat .env`

Expect it refused outright (`forbidden` — no approval prompt to click
through, it simply won't run).

### The prefix-matching gap

`pattern` matches an exact argument prefix. Prove the gap live:
> Run `head -n5 .env`

Expected: **not** blocked by exec policy — `["cat", ".env"]` never matches
a command whose program is `head`. This is real and not a trick: exec
policy rules are static command-shape rules, and covering "any way to read
this file" with prefix rules alone means enumerating every program and
every path spelling. That's exactly the gap Step 2's hook is for.

(If you want, also try `git push` and `docker compose down -v` here — the
first should prompt for approval instead of running silently, the second
should be refused with the data-loss justification from the rules file.)

## Step 2 — Hooks: the general backstop, and a durable audit trail

`.codex/hooks.json` registers two hooks (scripts in `.codex/hooks/`):

- **`block_secrets.py`** on `PreToolUse` (matcher: `Bash`) — regex-matches
  the *whole* command string for any real `.env` reference and denies the
  tool call, regardless of program or exact path spelling.
- **`audit_log.py`** on `PostToolUse` (matcher: `*`, runs `async`) —
  appends one line per tool call to `.codex/logs/audit.log`: timestamp,
  session id, tool name, command.

First, Codex needs to trust these hooks — project-level, non-managed hooks
require a one-time review:
```
/hooks
```
Approve the two hooks so they're active for this session.

Now re-run the exact command that slipped through exec policy:
> Run `head -n5 .env`

Expected this time: denied, with `block_secrets.py`'s reason surfaced —
because this hook matches on the full command text with a regex, not an
exact prefix, `.env` under any name/path variant is caught.

Run a few more unrelated commands (e.g. ask Codex to run `git log -3` and
`mvn -v`), then check the durable log the `PostToolUse` hook has been
writing the whole time, independent of the transcript:
```bash
cat .codex/logs/audit.log
```
Point out the entries include the MCP tool call from Demo 1 too, if you ran
that in the same session — `PostToolUse` fires for every tool, MCP tools
included, which is the "MCP is just another tool" point from Demo 1 showing
up again here.

## Step 3 — where each mechanism sits in the lifecycle

Codex's hook events, in the order they can fire during a turn: `SessionStart`
→ `UserPromptSubmit` → (`PreToolUse` → tool executes → `PostToolUse`, once
per tool call, can repeat many times per turn) → … → `PreCompact`/
`PostCompact` (only if compaction happens) → `Stop` → `SessionEnd`.

- **Exec Policy** runs *before* `PreToolUse` even fires for a shell command
  — a `forbidden` verdict means Codex never proposes running it in a form
  that would reach a hook or the sandbox.
- **`PreToolUse`** hooks run after exec policy/sandbox would otherwise
  allow the call, but before it executes — the last point where you can
  still deny or rewrite it (`block_secrets.py` uses this to deny).
- **`PostToolUse`** hooks run after the tool already executed — useful for
  logging/auditing (`audit_log.py`) or for feeding back extra context, but
  too late to prevent the action itself.

That maps directly onto the outline's distinction: Exec Policy is
command-level rules evaluated up front; Sandbox is the broader
execution-boundary that's active throughout; Hooks are custom logic you can
attach at any of several specific lifecycle points, before or after the
fact, for whatever Exec Policy's static rules don't express (a full
`.env`-shaped regex, a durable log file, anything else procedural).

## Step 4 — Exec Policy vs Sandbox, not either/or

Look back at `.codex/config.toml`:
```toml
sandbox_mode = "workspace-write"
[sandbox_workspace_write]
network_access = false
```
With `network_access = false`, a `git push` would likely already need
elevated permission from the sandbox alone (it needs the network). The
`git push` exec-policy rule in `default.rules` still earns its place: it's
a floor that holds even if someone later flips `network_access = true` for
an unrelated reason (say, to let Codex hit an API during a different demo)
— defense in depth, not a single point of control. That's outline §8's
"Exec Policy vs Sandbox: command-level rules vs. the broader execution
boundary" — two layers, on purpose, not a redundancy to simplify away.
