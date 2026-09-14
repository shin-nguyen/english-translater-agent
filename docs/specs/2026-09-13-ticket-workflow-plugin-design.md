# Design: `ticket-workflow` Codex plugin — end-to-end ticket implementation

Status: approved by user, pending implementation plan
Date: 2026-09-13
Author: Claude (design session with repo owner)

## Purpose

A portable Codex CLI plugin, demoed as a 6th/bonus demo for the Session 2
kit (`demo-guides/06-demo-ticket-workflow.md`), showing Skills + Subagents +
MCP + Hooks composed into one real workflow: a user says "implement ticket
PROJ-123" and the agent autonomously reads the ticket from Jira,
investigates and designs, updates the ticket with a BE/UI interface design,
implements both sides, tests against the ticket's own acceptance criteria,
loops fixes until clean, and reports back on the ticket — resumable if the
session is interrupted at any point.

It must be installable into **any** repo's `.codex/`/`.agents/` — not tied
to this app's Spring Boot/React stack — so workshop attendees can drop it
into their own Codex setup and try it against their own Jira ticket.

## Non-goals

- Not a production ticket-automation tool; no attempt at handling every Jira
  workflow/field type, only the read/comment/update-description surface
  needed for the demo.
- Not adding a mock Jira — real Jira via Atlassian's official Remote MCP
  Server was chosen deliberately over a mock, so the demo also exercises a
  real OAuth-based remote MCP server, not just a stdio one (the repo already
  has a stdio MCP example in `translator_db`).
- No attempt to make BE-dev/UI-dev subagents framework-aware; they read
  whatever conventions the target repo already has, like `add-ai-provider`
  already models for a narrower case.

## Jira connectivity

Atlassian's official Remote MCP Server (GA, OAuth 2.1, Cloud-only):

```toml
[features]
rmcp_client = true

[mcp_servers.atlassian]
url = "https://mcp.atlassian.com/v2/mcp"
```

First connection triggers `codex mcp login atlassian` — an OAuth
authorization-code + PKCE browser flow; Codex stores the resulting token.
This requires an Atlassian Cloud site with Jira, and the org must allow the
Rovo MCP Server (Cloud-only; on by default for most sites, but a workshop
attendee on a locked-down org may need an admin to enable it — call this
out in the plugin README and in `06-demo-ticket-workflow.md`'s pre-work,
since — unlike the other 5 demos — this one cannot be fully self-contained
without an external Jira site).

Known rough edge (found during design research): some Codex versions hit
`Error: failed to handle OAuth callback` with `rmcp_client`/
`experimental_use_rmcp_client`; document as a troubleshooting entry rather
than treating it as unexpected.

Exact Jira tool names are not hardcoded anywhere in the plugin — the skill
and the investigator's instructions tell the agent to discover available
Jira tools via the live MCP tool list (mirroring how `02-demo-mcp.md`
already teaches `/mcp` for tool discovery) rather than assuming specific
tool identifiers, which the server maintainer can change.

## Components

### Plugin layout (portable; this is the thing attendees copy into their own repo)

```
plugins/ticket-workflow/
  README.md                    — what it is, prerequisites (Jira Cloud site),
                                  install steps, how to run the demo end to end
  install.sh                   — see "Installer" below
  merge_config.py              — key-aware TOML merge used by install.sh (see below)
  config-snippet.toml          — the [features]/[mcp_servers.atlassian] block above
  hooks-snippet.json           — PreToolUse/PostToolUse entries (below) to merge
  gitignore-snippet            — "tickets/", ".worktrees/", ".codex/tickets_active"
  skills/
    implement-ticket/SKILL.md
  agents/
    ticket-investigator.toml
    ticket-be-dev.toml
    ticket-ui-dev.toml
    ticket-tester.toml
  hooks/
    ticket_audit.py
    guard_ticket_commit.py
    verify_step.py
    state_io.py               — atomic read/write helper for state.json (see below)
```

### Installer (`install.sh`)

Copies (does not symlink — Windows/WSL-friendly) into the **target** repo,
run from the target repo's root:
1. `skills/implement-ticket/` → `.agents/skills/implement-ticket/`
2. `agents/*.toml` → `.codex/agents/`
3. `hooks/*.py` → `.codex/hooks/` (chmod +x)
4. Merges `config-snippet.toml` into `.codex/config.toml` via `merge_config.py`
   (**not** a blind append — a target repo may already have its own
   `[features]` table, and TOML forbids redefining a table, so a naive
   append can break an existing config):
   - copy `.codex/config.toml` to `.codex/config.toml.bak` first
   - if a `[features]` table header already exists, insert `rmcp_client = true`
     as a new line directly under that header, only if the key isn't already
     present in that table's block (scanned up to the next `[...]` header or
     EOF); if no `[features]` table exists at all, append the whole new
     `[features]` block at EOF
   - `[mcp_servers.atlassian]` is a table name unique to this plugin, so it's
     always safe to append at EOF — unless a table of that exact name already
     exists, in which case skip it and print a message to merge by hand
   - re-parse the resulting file with `tomllib.load()`; if that fails, restore
     from the `.bak` and abort the install with the parse error shown, rather
     than leaving Codex with a config that won't load
5. Merges `hooks-snippet.json` into `.codex/hooks.json` via a small inline
   `python3 -c "import json; ..."` (never hand-roll JSON merging in bash) —
   adds the two new hook entries under existing `PreToolUse`/`PostToolUse`
   arrays if `hooks.json` already exists, or creates the file if the target
   repo has no hooks yet.
6. Appends `tickets/`, `.worktrees/`, and `.codex/tickets_active` to
   `.gitignore` if not already present. The runtime pointer must not make
   the main checkout dirty during verification.
7. Prints next steps, in order:
   - `codex` — trust the project (new config only takes effect once trusted)
   - inside that `codex` session, run `/hooks` and approve the two new
     hooks — project-level, non-managed hooks require this explicitly (this
     repo's own `demo-guides/04-demo-exec-policy-hooks.md` already documents
     the same requirement for the existing hooks; installing/copying the
     files does **not** by itself make Codex trust them)
   - a sanity check for the guard, done right, in two parts (a bare
     terminal `git commit` never triggers a Codex lifecycle hook at all —
     hooks only fire for tool calls Codex's own agent loop makes — so
     neither of these runs `git commit` from a plain shell):
     1. **Script-level, no Codex needed** (same pattern this repo's own
        `block_secrets.py`/`audit_log.py` docstrings already use):
        ```bash
        echo DEMO-1 > .codex/tickets_active
        echo '{"tool_name":"Bash","tool_input":{"command":"git commit -m \"bad message\""}}' \
          | python3 .codex/hooks/guard_ticket_commit.py
        # expect JSON with permissionDecision: "deny"
        ```
        Note this must be a plain `git commit ...` command, not wrapped in
        `echo ... && git commit ...` — a compound command's first token is
        `echo`, not `git`, which is exactly the "chained command" scope
        limit `guard_ticket_commit.py` already documents as not reliably
        caught; testing it with a command shaped like that would prove
        nothing (an earlier draft of this exact check made that mistake).
     2. **Live, inside `codex`** (optional but more convincing): with
        `.codex/tickets_active` still set to `DEMO-1`, ask Codex to run
        exactly `git commit --allow-empty -m "bad message"` as its own tool
        call — a single, non-compound command — and confirm Codex reports
        the call denied and `git rev-parse HEAD` is unchanged afterward
        (rather than trusting the transcript alone, since a denied call
        should never create a commit at all).
     Either way, `rm .codex/tickets_active` afterward to clear the fake
     state before a real run.
   - `codex mcp login atlassian`

Idempotent: re-running after already-installed detects each piece present
and skips it rather than duplicating entries.

### State file — `tickets/<TICKET-ID>/state.json`

Not committed (`tickets/` is gitignored — confirmed with user: spec/bugs
tracking is working state + what lives durably on the Jira ticket itself,
not a second copy in git history).

```json
{
  "ticket_id": "PROJ-123",
  "base_branch": "session2-codex-demo",
  "phase": "fixing",
  "retry_count": 1,
  "max_retries": 5,
  "spec_file": "tickets/PROJ-123/PROJ-123-spec.md",
  "bug_track_file": "tickets/PROJ-123/bugs.md",
  "worktrees": {
    "be": {"path": ".worktrees/PROJ-123-be", "branch": "ticket/PROJ-123-be"},
    "ui": {"path": ".worktrees/PROJ-123-ui", "branch": "ticket/PROJ-123-ui"}
  },
  "dev_round": {
    "kind": "fix",
    "round_id": 1,
    "synced_from_commit": "aaaa111",
    "be": {"sync_status": "ready", "pre_sync_commit": "bbbb222", "status": "merged", "commit": "abc1234", "merge_commit": "def5678", "bug_ids": ["BUG-1"]},
    "ui": {"sync_status": "ready", "pre_sync_commit": "cccc333", "status": "no_bugs_assigned", "commit": null, "merge_commit": null, "bug_ids": []}
  },
  "test_report": {
    "commit": "aaaa111",
    "result": "failed",
    "acceptance_criteria_checked": true,
    "clean_before": true,
    "clean_after": true,
    "tested_at": "..."
  },
  "jira": {"interface_comment_id": "10042", "summary_comment_id": null},
  "phase_history": [
    {"phase": "investigating", "started_at": "...", "completed_at": "..."}
  ],
  "updated_at": "..."
}
```

`base_branch` records whichever branch the workflow was actually invoked
on — never assumed to be `main`, since a workshop attendee may run this
from any branch.

All SHA fields store full commit IDs; abbreviated IDs above are illustrative.
The workflow requires a named branch and a clean main working tree at entry.
If either condition fails, preserve the checkout and report the prerequisite
that must be resolved before starting.

`dev_round` is round-scoped, not a single fixed `dev_tasks` object: `kind`
is `initial` (the first `dev_in_progress` pass, reading the full spec) or
`fix` (a `fixing` pass, reading only its assigned bug IDs from `bugs.md`);
`round_id` increments per `fixing` iteration (the initial pass is
`round_id: 0`); `synced_from_commit` is the immutable baseline selected for
this round. Each side records `pre_sync_commit` and `sync_status`
(`pending` / `ready`) so an interrupted preparation can be reconciled without
resetting work that has already started. See "Resyncing between rounds".
`be`/`ui` status is one of `pending` / `in_progress` / `committed` /
`merged` / **`no_bugs_assigned`** — the last one for a side that wasn't
given any bugs to fix in a `fix` round, so the skill can skip dispatching
that side entirely instead of waiting on a call that was never made. The
main agent writes this object (status `pending` for assigned sides and
`no_bugs_assigned` for unassigned fix sides, with `bug_ids` already assigned)
**before** dispatching either subagent, not after — so an
interruption mid-round leaves a clear record of what was assigned and to
whom, not just silence.

`test_report` is a separate, explicit result of the most recent
`ticket-tester` run — see the `done` gate below for why an empty `bugs.md`
alone was not a safe enough signal.

`jira.*_comment_id` stores the **actual Jira comment ID** returned after
posting, not just a boolean — see "Resume reconciliation" below for why a
boolean flag alone isn't safe against an interruption between "Jira
accepted the comment" and "state.json recorded that."

Phases, in order: `investigating` → `spec_ready` → `dev_in_progress` →
`dev_done` → `testing` → (`bugs_found` → `fixing` → `testing` again, up to
`max_retries`) → `done` → `summarized` (terminal). A merge conflict during
`dev_in_progress`/`fixing` moves to `dev_conflict` instead (see Hooks/error
handling) and stops for a human rather than guessing a resolution.

**Single-writer rule:** only the main agent (the top-level session
following the skill) ever writes `state.json` or `bugs.md`. Subagents never
touch either file — not even `ticket-investigator` or `ticket-tester` — they
report results (spec content, bug list, fix summary) in their final
message, and the main agent does all the bookkeeping. One rule, no
exceptions, specifically to avoid two concurrently running subagents
(`ticket-be-dev`, `ticket-ui-dev`) corrupting a shared file — confirmed with
user as a deliberate deviation from having each subagent "self-track."

**Atomic writes:** `state.json` is never edited via a raw file write in
place — the main agent always goes through a small `state_io.py` helper
(`plugins/ticket-workflow/hooks/state_io.py`, alongside the other scripts)
that writes to `state.json.tmp` and `os.replace()`s it over `state.json`.
Relying on an LLM-driven Edit/Write call to "remember" to do this reliably,
turn after turn, isn't a real guarantee — a helper script is.

**Resume reconciliation:** `state.json` is a checkpoint, not a ledger — an
interruption can land between an action actually succeeding (a Jira post
goes through, a merge lands) and the checkpoint recording it. So resume
never just trusts the file; the skill's first instruction is: read
`state.json` for a rough starting point, then reconcile each piece against
its real source of truth before acting on it:
- **Jira comments:** before posting the interface-design comment or the
  final summary comment, if the corresponding `jira.*_comment_id` is null,
  first search the ticket's existing comments (via the Jira MCP tools) for
  a hidden marker unique to that comment type — e.g.
  `<!-- ticket-workflow:PROJ-123:interface -->` embedded in the comment
  body when it's first posted. Found → record that comment's real ID into
  `state.json` without re-posting. Not found → post it, then record the ID
  it comes back with. This is what actually prevents a duplicate post, not
  the boolean flag alone (which the earlier draft of this spec relied on
  and which doesn't survive a crash between "Jira accepted it" and "the
  flag got written").
- **Worktree completion:** a changed branch tip is not proof of completion.
  Each dev subagent marks only its final commit with trailers
  `Ticket-Workflow: <ticket-id>`, `Ticket-Round: <round_id>`,
  `Ticket-Side: be|ui`, and `Ticket-Complete: true`. Intermediate commits
  carry no completion trailer. Before recording `committed`, require a clean
  worktree, a tip distinct from and descended from `synced_from_commit`, and
  matching trailers for this ticket, round, and side. Record that exact SHA.
  A synced baseline or an intermediate commit must not advance task status.
  If interrupted before a completion marker, continue the assigned task from
  its existing files and commits; preserve uncommitted work and never reset it.
  Unexpected tip changes after a recorded completion stop reconciliation for
  inspection rather than silently replacing the recorded SHA.
- **`base_branch` integration:** for the validated completion SHA, run
  `git merge-base --is-ancestor <completion-sha> <base_branch>`. Exit 0 means
  that exact result is integrated and the side can be marked `merged`; exit 1
  means integration is pending; other failures stop reconciliation. Branch
  names and `git log --grep` matches are not evidence, because names are reused
  across rounds. Recover `merge_commit`, when present, from a merge on the
  base branch's first-parent history whose parent is that completion SHA;
  leave it null if no such merge exists. The ancestry check is authoritative.

Only once state is reconciled does the skill decide what to do next —
resume from `phase`, skip a subagent whose round-scoped status is already
`committed`/`merged`/`no_bugs_assigned`, and never re-post a Jira comment a
marker search finds already there. This is what makes stopping mid-session
(including mid-round, mid-merge, or right after a Jira post) safe to resume
from, rather than only safe between clean phase boundaries.

### Bug track file — `tickets/<TICKET-ID>/bugs.md`

Markdown table, written **only** by the main agent, per the single-writer
rule above:

| ID | Area | Severity | Description | Status | Found in commit | Fixed in commit |
|----|------|----------|--------------|--------|------------------|------------------|

`Status` is one of `open` / `fixed-pending-retest` / `verified-fixed` /
`wont-fix`. `ticket-tester` never writes this file directly — it returns a
structured list of findings (and, on a retest round, which previously-open
IDs it could no longer reproduce) in its final report; the main agent
creates new rows or updates `Status` from that report. Same for
`ticket-be-dev`/`ticket-ui-dev` during `fixing`: they report which bug IDs
they addressed, and the main agent — not the subagent — flips those rows to
`fixed-pending-retest` (never straight to `verified-fixed`; only
`ticket-tester` re-confirming on the next `testing` pass earns that state).

`wont-fix` is never set autonomously by the agent — it requires the main
agent to stop and explicitly ask the user, since it's overriding an
acceptance criterion rather than satisfying it; the row records that it was
user-approved. This exists specifically so the fix-retest loop can't quietly
talk itself out of a criterion it's failing to satisfy in order to reach
`done`.

### Skill — `implement-ticket`

`.agents/skills/implement-ticket/SKILL.md`. Triggered implicitly ("implement
ticket PROJ-123", "continue ticket PROJ-123") or explicitly (`$implement-ticket
PROJ-123`). Encodes the full state machine above as ordered steps, including:
- the resume check, always followed by the reconciliation pass described
  under "Resume reconciliation" above — never act on `state.json` alone
- before first dispatching `ticket-be-dev`/`ticket-ui-dev` for a ticket,
  create their worktrees if `state.json.worktrees` isn't already populated
  (see "Git isolation for parallel dev" below); reuse the same worktrees
  across every `dev_in_progress`/`fixing` round rather than recreating them,
  but prepare each one once per new fixing round using the checked,
  checkpointed resync procedure below; resuming an existing round never
  restarts preparation for a side already marked `ready`
- for a `fixing` round: write `dev_round` (new `round_id`, `bug_ids` per
  side, status `pending`/`no_bugs_assigned`) to `state.json` **before**
  dispatching anyone; only dispatch a side whose `bug_ids` for this round is
  non-empty
- dispatching `ticket-be-dev` and `ticket-ui-dev` **in true parallel**, each
  confined to its own worktree, in both `dev_in_progress` and `fixing`
- calling `verify_step.py` after every subagent return, before advancing phase
- merging each worktree branch back into `base_branch` as soon as that side
  reports done — independently, not waiting for both — via
  `git merge --no-ff <validated-completion-sha>` from a clean main checkout
  on `base_branch`; on conflict, `git merge --abort`, set phase
  to `dev_conflict`, and stop for the user rather than guessing a resolution
- never having any subagent touch `state.json`/`bugs.md` directly — they
  only edit code (in their worktree) and report
- `done` requires `test_report.result == "passed"` for the commit currently
  at `base_branch` HEAD, not merely an empty open-bugs list (see
  `verify_step.py` below)
- the retry cap and what to do at cap (stop, report, ask user); `wont-fix`
  on any bug always stops and asks the user first, it's never automatic
- the final steps: write a human-readable summary, reconcile-then-post the
  Jira summary comment (see "Resume reconciliation"), remove only clean
  worktrees whose tips are integrated into `base_branch` (never force removal),
  done

#### Git isolation for parallel dev

The original design let `ticket-be-dev`/`ticket-ui-dev` `git commit`
directly on the shared working tree. That's unsafe: git's staging area
(the index) is one file per working tree, not per process — if BE stages
and commits while UI has also staged (but not yet committed), BE's commit
can silently sweep in UI's staged changes, and UI's later commit then has
nothing left of its own to commit. This is a real correctness bug, not a
transient lock contention, and it doesn't go away just because BE and UI
usually touch disjoint paths.

Fix: each gets its own [`git worktree`](https://git-scm.com/docs/git-worktree)
— a second working directory backed by the same repo but with its own
index and its own branch, so two `git commit`s can genuinely happen at the
same time without sharing a staging area:
```bash
git worktree add .worktrees/<TICKET-ID>-be -b ticket/<TICKET-ID>-be
git worktree add .worktrees/<TICKET-ID>-ui -b ticket/<TICKET-ID>-ui
```
Placed under `.worktrees/` **inside** the repo root (gitignored, like
`tickets/`) rather than as a sibling directory outside it, so both stay
within whatever path Codex's `workspace-write` sandbox already treats as
writable — a worktree outside the trusted project root risks a sandbox
denial that a worktree nested inside it doesn't.

**Resyncing between rounds:** merging a worktree branch into `base_branch`
does not update the *other* worktree branch — `ticket/<id>-be` never
receives `ticket/<id>-ui`'s commits just because both were merged into
`base_branch`. Left alone, a later `fixing` round would hand `ticket-be-dev`
a worktree that's missing UI's code entirely, and missing the exact
integrated commit `ticket-tester` actually tested against — it could easily
fail to reproduce or correctly fix an integration bug. So immediately
before dispatching a **new** fixing round:

1. Confirm no previous dev subagent is still running. Require the main
   checkout to be clean, on `base_branch`, and at `test_report.commit`.
   If HEAD changed since testing, test the new integrated commit first.
2. Require both worktrees to be clean and each old tip to be an ancestor of
   the selected baseline. Otherwise preserve the files and commits and stop
   preparation for reconciliation; do not stash, clean, or discard them.
3. Atomically record the new round, its immutable `synced_from_commit`, and
   each side's old tip as `pre_sync_commit`, with `sync_status: pending`.
4. For each side, recheck cleanliness and its expected old tip, reset to the
   recorded baseline, verify HEAD, then checkpoint `sync_status: ready`:

   ```bash
   git -C "<absolute-be-worktree>" reset --hard <synced_from_commit>
   git -C "<absolute-ui-worktree>" reset --hard <synced_from_commit>
   ```

5. Dispatch only after both sides are `ready`. Before each dispatch, persist
   that side's task status as `in_progress`.

On resume, a clean `pending` side already at the recorded baseline can be
marked `ready` without another reset. A clean side still at `pre_sync_commit`
can finish preparation after the same checks. Any other tip or dirty state
requires reconciliation without destructive commands. A `ready` side is
never reset on resume, even if its task is still `in_progress`. Initial-round
worktrees are created at a recorded base SHA and marked `ready` after verifying
their branch, HEAD, and cleanliness; no reset is needed.

Here, clean means `git status --porcelain=v1 --untracked-files=all` has no
entries: no staged, unstaged, or non-ignored untracked files. Ignored build
outputs and workflow state are allowed; ignored source overrides must not
stand in for committed code during verification.

#### Workflow paths and test checkout

`workflow_root` is the absolute original repository root where the skill
starts; `worktree_root` is the absolute checkout assigned to a dev subagent.
Relative paths in `state.json` are always resolved against `workflow_root`,
never the current shell directory. Every delegation includes both roots and
an absolute spec path (or the assigned bug content for a fixing task).
The investigator writes the spec under `workflow_root/tickets/`; dev agents
read it there, and edit code and run Git only under their `worktree_root`.
Gitignored `tickets/` files are not copied into linked worktrees.

Installed hook scripts resolve `workflow_root` from their own location
(`Path(__file__).resolve().parents[2]` for `<root>/.codex/hooks/<script>.py`).
Hook commands must invoke that installed script using its quoted absolute
path. The active pointer, state, bugs, and audit log all live under this root;
scripts must not find them relative to the tool call's cwd or a linked
worktree's Git top-level. Reject state paths that resolve outside the root.
The installer supplies absolute hook command paths; moving the checkout
requires refreshing those paths. `verify_step.py` and `state_io.py` use the
same root convention and are invoked by absolute path.

The tester runs in `workflow_root` on `base_branch` with no concurrent code
writers or merges. Require cleanliness before testing and again afterward,
with unchanged HEAD; store `clean_before` and `clean_after` in `test_report`.
Dirty input, code changes during testing, or changed HEAD yield `blocked`,
not `passed`. Preserve the changes for review and rerun on a clean committed
checkout. The `done` gate rechecks current cleanliness as well as report SHA.

Each dev delegation explicitly uses `worktree_root` as the working directory
for code edits and Git commands (subagent role config has no
first-class "starting cwd" field to lean on instead, as far as this design
could confirm — flagged in the Testing plan below to verify during the dry
run rather than assumed). `guard_ticket_commit.py` still applies inside a
worktree, since it's the same trusted project tree — also flagged to
confirm during the dry run rather than assumed, since Codex's hook-scoping
behavior for a subagent working in a nested worktree directory wasn't
something this design could verify from documentation alone.

### Subagent roles — `.codex/agents/*.toml`

All four follow the existing `secret-auditor.toml` pattern (name,
description, `developer_instructions`), each scoped to know only what it
needs — this is the point of using subagents at all (context isolation),
so:

- **`ticket-investigator`** — gets the ticket text and codebase access, NOT
  Jira MCP access itself (main agent already read the ticket; passing text
  down avoids every subagent needing its own Jira auth context). Produces
  `tickets/<id>/<id>-spec.md` (creates the folder) with a concrete BE/UI
  interface design, and returns that same interface-design section as its
  report so the main agent can post it to Jira. Sole writer of the spec
  file/folder — safe because it's the only subagent active at this phase.

- **`ticket-be-dev`** / **`ticket-ui-dev`** — read ONLY the spec file, never
  the ticket ("implement your part from the spec, you don't need the ticket"
  — directly from the user's ask). Read the absolute spec path at
  `workflow_root`; implement and commit inside the delegated `worktree_root`
  with message
  `[<TICKET-ID>][dev-be]`/`[dev-ui]` prefix (`fixing`-phase commits use
  `[fix-be]`/`[fix-ui]` instead — enforced by `guard_ticket_commit.py`).
  Mark the final commit with the round-specific completion trailers described
  above and return its full SHA, round ID, side, and addressed bug IDs. Only
  emit the completion marker after the assigned work is finished.
  Reused unmodified for the `fixing` phase, given a specific bug-ID subset
  from `bugs.md` instead of the full spec; report back which bug IDs were
  addressed rather than editing `bugs.md` themselves.

- **`ticket-tester`** — reads the TICKET (not the spec — deliberate, so it's
  testing against the actual acceptance criteria, not re-deriving the
  implementer's own interpretation) and works on `base_branch`, after the
  main agent has merged both worktrees in. Runs whatever the target repo's
  test/build commands are (discovered, not hardcoded) plus manual
  verification where relevant, and returns a structured report — overall
  `result` (`passed`/`failed`/`blocked`, for `test_report`), the commit it
  ran against, and a findings list (plus, on a retest round, which
  previously-open bug IDs no longer reproduce) — it does not write
  `bugs.md` itself.

Known real limitation to document, following this repo's existing
`secret-auditor.toml` precedent: a role's `sandbox_mode` field is silently
dropped by Codex (verified against `core/src/agent/role.rs` for that role
already) — none of these four roles get OS-enforced sandboxing beyond what
the parent session has; any read-only framing is by instruction only.

### Hooks

- **`ticket_audit.py`** (PostToolUse, matcher `*`) — extends this repo's
  existing `audit_log.py` pattern: appends tool-call lines to
  `tickets/<id>/audit.log`, additionally including the current `phase` read
  from `state.json` if present. Pure observability — never blocks.
- **`guard_ticket_commit.py`** (PreToolUse, matcher `Bash`) — when
  `.codex/tickets_active` (a one-line pointer file the skill writes/clears
  as it starts/finishes a ticket, containing the exact active ticket ID)
  names an active ticket and the command's first token is `git` with `commit`
  among its args, denies it unless the message matches
  `^\[<ACTIVE-TICKET-ID>\]\[(dev|fix)-(be|ui)\]` — the literal ticket ID
  from the pointer file, not any ticket-shaped pattern, and one of the four
  known phase tags, not an open-ended `\w+`. Documented scope limit: the
  hook only inspects the literal `tool_input.command` string — a message
  supplied via `-F <file>`, an interactive `--amend`, or a commit issued as
  a later command in a `&&`/`;` chain where `git commit` isn't the first
  token, are not reliably caught. The workflow itself never needs those
  forms (subagents always issue a plain `git commit -m "..."` per batch), so
  this is stated as an accepted scope limit rather than claimed as complete
  enforcement.
- **`verify_step.py`** — not a lifecycle hook; a script the skill calls
  explicitly (`python3 "<workflow_root>/.codex/hooks/verify_step.py" <ticket-id> <phase>`)
  after each subagent returns, before the main agent advances `state.json`.
  Deterministic per-phase checks: `spec_ready` → spec file exists;
  `dev_done`/round-complete → every side with a non-empty `bug_ids` (or, for
  the initial round, every side) has `dev_round.*.status == "merged"`, and
  every `no_bugs_assigned` side is accounted for without being waited on.
  Revalidate each assigned side's completion trailers, baseline ancestry,
  and completion SHA's ancestry in `base_branch`; state labels alone do not
  prove round completion;
  `done` → **all** of: `bugs.md` (parsed by splitting table rows on `|` and
  reading the `Status` cell, not a raw string search) has every row
  `verified-fixed`/`wont-fix`; `test_report.result == "passed"` (an empty
  `bugs.md` with `test_report.result` still `"failed"` or `"blocked"` — e.g.
  the test run itself couldn't complete — must NOT satisfy `done`, which is
  exactly the gap the previous draft of this spec left open); and
  `test_report.commit` equals the current `git rev-parse HEAD` on
  `base_branch`, with `acceptance_criteria_checked`, `clean_before`, and
  `clean_after` all true and the main checkout still clean. A passing report
  recorded before a later commit or uncommitted edit landed doesn't count.
  Non-zero exit = the main agent must stop and report, not
  assume success.

## Residual risk

`git worktree` removes the shared-index problem, but a merge conflict is
still possible if BE and UI genuinely touch the same file (e.g. a shared
interface/config file neither side treats as "theirs"). Handled by stopping
at `dev_conflict` for a human rather than an agent guessing a resolution —
see "Git isolation for parallel dev" above. The investigator's spec should
explicitly assign disjoint file ownership between BE/UI where practical, to
make this rare rather than relying on the conflict handler alone.

## Demo guide changes

- New `demo-guides/06-demo-ticket-workflow.md`: pre-work specific to this
  demo (a Jira Cloud test site + one sample ticket — cannot be bundled like
  the other demos' local Postgres), `install.sh`, `codex mcp login
  atlassian`, then a full walkthrough against a real ticket, pointing out
  `state.json`'s phase changes, `bugs.md`, and the final Jira comment.
- `demo-guides/01-overview.md`: add a row for this demo, flagged as needing
  separate Jira setup (unlike `02`–`05` which need nothing beyond
  `00-setup.md`).
- `plugins/ticket-workflow/README.md`: standalone install/usage doc so the
  plugin is usable by someone who found the folder without reading the
  workshop's demo guides at all.

## Testing plan for the demo itself

Since this drives real subagents/MCP/hooks, "testing" here means a live
dry run before the session, using a real (throwaway) Jira Cloud ticket with
a small, concrete ask (small enough that BE-dev/UI-dev produce a handful of
files, not a sprawling feature) — confirm:
- full happy path completes and comments back on the ticket
- **the two flagged assumptions from "Git isolation for parallel dev":**
  each subagent actually operates inside its assigned worktree path rather
  than the main tree, and `guard_ticket_commit.py` actually fires for a
  commit made from inside a worktree — both need to be observed directly,
  not assumed from documentation
- killing the session mid-`dev_in_progress` and restarting: confirm
  reconciliation (not just the stale `state.json`) correctly identifies an
  already-`merged` side and doesn't redispatch it
- **specifically kill the session right after a Jira post succeeds but
  before `state.json` would record it** (e.g. interrupt between the MCP
  call returning and the next write), restart, and confirm the marker
  search finds the existing comment instead of posting a duplicate — this
  is the scenario the plain boolean flag in the first draft didn't survive
- forcing a same-file BE/UI edit to confirm the `dev_conflict` path stops
  cleanly instead of guessing a merge resolution
- confirm the worktree resync before a `fixing` round actually gives each
  side the other side's code and the tested commit — e.g. have a
  deliberately-planted integration bug only reproducible with both sides'
  code present, and confirm the assigned dev subagent can actually see it
- deliberately breaking one subagent's output to confirm `ticket-tester`
  catches it, `bugs.md` gets an `open` row, the fix loop runs, the row
  reaches `verified-fixed` only after a genuine retest, and the loop
  terminates on either a clean `bugs.md` or the retry cap
- confirm `done` is correctly refused when `bugs.md` is empty but
  `test_report.result` is `"failed"`/`"blocked"` (e.g. simulate the test
  command itself erroring out) — this is the gap the first draft's
  "no open rows" check alone would have missed
- confirm attempting to mark any bug `wont-fix` always stops and asks the
  user first, never happens silently as a way to reach `done`
- reuse a branch across two fixing rounds; leave the current round unmerged
  and confirm its previous merge cannot satisfy reconciliation. Interrupt
  after an intermediate commit and after a final completion-marked commit;
  only the latter may be recovered as `committed`. Interrupt after its merge
  but before checkpointing and confirm exact-SHA ancestry recovers `merged`
- interrupt preparation after one worktree reset but before its checkpoint;
  resume without resetting any side already ready or running. Leave an
  uncommitted edit in a worktree and confirm resync and cleanup preserve it
- test with staged, unstaged, and non-ignored untracked code in the main
  checkout, and with a test command that changes tracked code; each must
  block a passing report. An edit after a clean passing test must block `done`
- from a linked worktree, read the original gitignored spec by absolute path
  and trigger the guard/audit scripts; confirm they use the original active
  pointer and ticket directory. Repeat with spaces in the repository path
