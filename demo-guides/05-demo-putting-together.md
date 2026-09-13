# Demo 4 — Putting Everything Together: Designing a Codex Harness

Covers outline §9. This is a design worksheet, not a scripted demo — the
outline gives §9 no "Demo:" bullet either; it's the session's synthesis.
Budget it as a discussion, working from what's already sitting in this repo
(Demos 1–3), not new hands-on steps.

## The three-bucket framework

Everything from both sessions sorts into one of three buckets:

| Bucket | Question it answers | Mechanisms |
|---|---|---|
| **Instructions** | How should the model behave? | `AGENTS.md`, Skills |
| **Capabilities** | What can it reach or delegate to? | MCP servers, Subagents |
| **Controls** | What's it actually allowed to execute? | Sandbox mode, Approval policy, Exec-policy rules, Hooks |

Instructions are guidance the model can still choose to ignore or misjudge.
Capabilities are things the model can now call, that didn't exist before.
Controls are the only bucket enforced outside the model's judgment —
everything in it still works even if the model is confused, or actively
being steered by something in a tool result it shouldn't trust.

## Part 1 — classify what's already in this repo

Go through every demo artifact and place it. Suggested answer key:

| Artifact | Bucket | Why |
|---|---|---|
| `AGENTS.md` | Instructions | Guides behavior; nothing stops a confused model from still doing the wrong thing |
| `.agents/skills/add-ai-provider/SKILL.md` | Instructions | Same, just conditionally loaded |
| `.codex/config.toml` → `[mcp_servers.translator_db]` | Capabilities | New thing the model can call that didn't exist before |
| `.codex/agents/secret-auditor.toml` — the *capability* of having this role to delegate to | Capabilities | New thing to delegate to |
| `.codex/agents/secret-auditor.toml` — its `developer_instructions` telling it to stay read-only | Instructions, not Controls | Verified against Codex's source: a role file's `sandbox_mode` is silently dropped, only `developer_instructions`/`model`/a few others actually apply. This one looks like a Control (it names a security property, "read-only") but is really only an Instruction — worth pointing at directly as the clearest real example in this whole kit of the distinction mattering. |
| `sandbox_mode`, `approval_policy` | Controls | Enforced regardless of what the model "decides" |
| `.codex/rules/default.rules` | Controls | Same — a `forbidden` verdict isn't a suggestion |
| `.codex/hooks.json` + scripts | Controls | Same — runs whether or not the model would have asked permission |

## Part 2 — extend the harness (work this live)

Scenario: the team wants three new things from Codex on this repo:

1. When Codex finishes a change, it should open a draft PR instead of just
   leaving local commits.
2. It should be able to post a one-line summary to a `#translator-dev`
   Slack channel when asked.
3. A junior dev on the team should only be able to run Codex against
   `frontend/` — never touch `backend/` or `.codex/` itself.

For each, work out: which bucket, which specific mechanism, and roughly
what you'd add (file + a few lines — no need to write a working config).

**Suggested answers** (don't reveal until the group has tried):

1. **Capability** — a `gh` CLI is already usable via shell, so this might
   need nothing new at all; if you want it as a first-class, approval-aware
   action rather than "however the model phrases a shell command," add an
   MCP server for GitHub (`[mcp_servers.github]`) instead. Either way, pair
   it with a **Control**: an exec-policy `prompt` rule on `gh pr create` so
   opening a PR is never silent.
2. **Capability** — a Slack MCP server. Needs a **Control** alongside it:
   the message content is model-generated, so a `PreToolUse` hook that
   checks the outgoing text doesn't contain anything from `.env`/secrets
   before it's allowed to post is worth pairing with this from day one —
   posting to Slack is exactly the kind of external, hard-to-unsend action
   Exec Policy's `git push` rule already treats as "always confirm."
3. **Control**, specifically `sandbox_mode`/writable roots scoped per
   profile — a `[profiles.junior]` in that dev's own `~/.codex/config.toml`
   (or a project-level restriction, if Codex's project-trust model allows
   scoping it per-user) with a narrower `sandbox_workspace_write` root of
   just `frontend/`. Note this is NOT something `AGENTS.md` can do — an
   instruction like "please only touch `frontend/`" is exactly the kind of
   thing that belongs in Controls instead, because it must hold even if the
   model is convinced (correctly or not) that touching `backend/` would
   help.

## Part 3 — don't over-engineer it

Signals that a harness has grown past what it needs, worth checking against
THIS repo's own demo config, not just hypothetically:

- An MCP server kept "just in case" nobody's used in weeks — is
  `translator_db` actually pulling its weight for this team, or did it only
  ever exist for Demo 1? (Fair to say: for a repo this size, most people
  would just read the Flyway SQL files instead of standing up a live DB
  MCP server — the honest answer for a team this size might be "no, drop
  it.")
- A Skill written for something that happened once — would `add-ai-provider`
  still earn a Skill file if this team only adds a new provider once a
  year? (Arguably yes here — the "reuse `ApiKeyAttributeConverter`, don't
  invent new encryption" instruction is exactly the kind of thing worth
  making unmissable every time, rare or not — but the question is worth
  asking of every skill, not assuming yes.)
- A Subagent for something one file-read would have answered — is
  `secret_auditor` overkill for a codebase this size? Probably borderline;
  it earns its place here mostly as a teaching example of context
  isolation, not because this specific repo is large enough to need it.
- Exec-policy rules copy-pasted from a different org's template, where the
  `justification` field is generic instead of naming an actual, current
  risk for this repo (all four rules in `default.rules` name a specific
  file, volume, or action in *this* app on purpose — that's the bar).
- Hooks or rules that default to `prompt`/`forbidden` on nearly everything
  — approval fatigue makes people click through warnings without reading
  them, which quietly defeats the control. Fewer, sharper rules beat broad
  ones.

Close on the outline's own line: adding more tools, skills, agents, and
rules does not automatically make the agent better. Every addition in
today's four demos should be defensible on its own — if you can't say
*why* a specific MCP server, skill, subagent, rule, or hook exists for
*this* repo, that's the one to cut.
