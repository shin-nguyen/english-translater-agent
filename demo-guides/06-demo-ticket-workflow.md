# Demo 4 (bonus) — a full Skill+Subagents+MCP+Hooks workflow

Unlike `02`–`05`, this demo needs its own pre-work — a real Jira Cloud
site — since it exercises a real OAuth-based remote MCP server rather than
this repo's local Postgres.

## Pre-work (do this before presenting)

1. A Jira Cloud site (a free trial site is fine) with one throwaway
   project and one small, concrete sample ticket — small enough that
   BE-dev/UI-dev produce a handful of files, not a sprawling feature.
2. From this repo's root:
   ```bash
   bash plugins/ticket-workflow/install.sh
   codex                        # trust (if not already)
   ```
   Inside that `codex` session: `/hooks`, approve the two new hooks.
3. Commit (or stash) the files `install.sh` just added/modified
   (`.agents/skills/`, `.codex/agents/`, `.codex/hooks/`, `.codex/config.toml`,
   `.codex/hooks.json`, `.gitignore`) so the checkout is clean — the Skill's
   entry preconditions refuse to start a ticket on a dirty tree.
4. Sanity-check the guard (see `plugins/ticket-workflow/README.md`'s
   install section for the exact commands).
5. `codex mcp login atlassian` — OAuth login to your Jira site.
6. Self-check:
   ```bash
   codex mcp list        # expect: atlassian, Status: enabled
   ```

## Running the demo

1. In `codex`, in this repo:
   ```
   implement ticket <YOUR-TICKET-ID>
   ```
2. Narrate as it runs: point out the Jira comment it posts with the BE/UI
   interface design, the two worktrees under `.worktrees/` and their
   parallel commits, `tickets/<ID>/state.json`'s phase changing, and
   `tickets/<ID>/bugs.md` if the tester finds anything.
3. To show resumability live: interrupt the session (Ctrl+D) mid-`fixing`,
   start a fresh `codex` session, say `implement ticket <YOUR-TICKET-ID>`
   again, and point out it resumes from `state.json` instead of starting
   over — including not re-posting the interface-design Jira comment,
   which a marker search finds already there.
4. At the end: the final Jira comment with the summary, and
   `git log --oneline` on the current branch showing the `[<ID>][dev-be]`/
   `[dev-ui]`/`[fix-*]`-tagged merge commits.

Ground rules: same as the other demos — everything here is aimed at a
throwaway Jira ticket and a local git branch; nothing production-facing is
touched.
