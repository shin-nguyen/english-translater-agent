# ticket-workflow Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the portable `plugins/ticket-workflow/` Codex plugin — a Skill + 4 Subagents + Jira MCP config + Hooks that implements a Jira ticket end-to-end (investigate → spec → parallel BE/UI dev → test → fix-retest loop → Jira summary), resumable across interruptions.

**Architecture:** Four small, independently-testable Python scripts under `hooks/` (`state_io.py`, `merge_config.py`, `guard_ticket_commit.py`, `ticket_audit.py`, `verify_step.py`) implement every piece of mechanical/deterministic logic; the Skill and 4 Subagent `.toml` role files carry the LLM-driven orchestration prose that calls those scripts as CLI commands. `install.sh` copies everything into a target repo and key-merges the config via `merge_config.py`.

**Tech Stack:** Python 3.11+ stdlib only (`json`, `re`, `pathlib`, `subprocess`, `tomllib`) — no third-party runtime dependency, so the plugin has no install step of its own beyond copying files. `pytest` for this plugin's own test suite (dev-only, not needed by someone who just installs the plugin). Bash for `install.sh`. TOML for Codex config, JSON for hooks config and state.

**Spec:** `docs/specs/2026-09-13-ticket-workflow-plugin-design.md` — this plan implements that spec; read both together. Exact field names, phase names, and status enums below are copied verbatim from it.

## Global Constraints

- Python 3.11+ required (uses `tomllib`, stdlib since 3.11) — `merge_config.py` and `verify_step.py` will not run on older Python.
- No new runtime third-party dependencies in any `plugins/ticket-workflow/**` script — stdlib only, so the plugin installs by copying files, nothing to `pip install`.
- Every script exposes plain functions importable for tests, plus a thin CLI `main()` — never logic embedded only in `if __name__ == "__main__"`.
- Every hook script must exit `0` even when denying a tool call (Codex reads the JSON `permissionDecision`, not the process exit code) — this matches the existing `.codex/hooks/block_secrets.py`/`audit_log.py` pattern in this repo; deviate from it only where the spec explicitly requires (`verify_step.py` is not a lifecycle hook and legitimately uses exit code 0/1 as its own contract with the Skill).
- `state.json` schema field names (`phase`, `base_branch`, `dev_round`, `test_report`, `jira`, `worktrees`) match the spec's JSON example exactly — do not rename.
- Status enums are closed sets, copied from the spec: `bugs.md` `Status` ∈ `{open, fixed-pending-retest, verified-fixed, wont-fix}`; `dev_round.<side>.status` ∈ `{pending, in_progress, committed, merged, no_bugs_assigned}`; `dev_round.<side>.sync_status` ∈ `{pending, ready}`; `test_report.result` ∈ `{passed, failed, blocked}`.
- Commit trailers are exactly: `Ticket-Workflow: <ticket-id>`, `Ticket-Round: <round_id>`, `Ticket-Side: be|ui`, `Ticket-Complete: true`.

---

## File Structure

```
plugins/ticket-workflow/
  README.md
  install.sh
  config-snippet.toml
  hooks-snippet.json
  gitignore-snippet
  skills/implement-ticket/SKILL.md
  agents/
    ticket-investigator.toml
    ticket-be-dev.toml
    ticket-ui-dev.toml
    ticket-tester.toml
  hooks/
    state_io.py
    merge_config.py
    guard_ticket_commit.py
    ticket_audit.py
    verify_step.py
  tests/
    conftest.py
    test_state_io.py
    test_merge_config.py
    test_guard_ticket_commit.py
    test_ticket_audit.py
    test_verify_step.py
    test_install.py
    test_role_files.py

demo-guides/06-demo-ticket-workflow.md   (new)
demo-guides/01-overview.md               (modified — add a row)
```

`tests/` ships inside the plugin folder (dev-only; not copied by `install.sh`, which only touches `skills/`, `agents/`, `hooks/`, and the config snippets — see Task 6).

---

### Task 1: `state_io.py` — atomic state read/write

**Files:**
- Create: `plugins/ticket-workflow/hooks/state_io.py`
- Test: `plugins/ticket-workflow/tests/test_state_io.py`

**Interfaces:**
- Produces: `read_state(path: str | Path) -> dict | None`, `write_state(path: str | Path, data: dict) -> None`. Every later task that reads/writes `tickets/<id>/state.json` (Task 5's `verify_step.py`) imports these.
- CLI: `python3 state_io.py read <path>` (prints JSON, or the literal `null`, to stdout), `python3 state_io.py write <path>` (reads a JSON object from stdin, writes it atomically).

- [ ] **Step 1: Write the failing tests**

```python
# plugins/ticket-workflow/tests/test_state_io.py
import json
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "hooks"))
from state_io import read_state, write_state  # noqa: E402

SCRIPT = Path(__file__).resolve().parents[1] / "hooks" / "state_io.py"


def test_read_missing_returns_none(tmp_path):
    assert read_state(tmp_path / "state.json") is None


def test_write_then_read_roundtrip(tmp_path):
    path = tmp_path / "tickets" / "PROJ-1" / "state.json"
    write_state(path, {"ticket_id": "PROJ-1", "phase": "investigating"})
    assert read_state(path) == {"ticket_id": "PROJ-1", "phase": "investigating"}


def test_write_creates_parent_dirs(tmp_path):
    path = tmp_path / "a" / "b" / "c" / "state.json"
    write_state(path, {"x": 1})
    assert path.exists()


def test_write_does_not_leave_tmp_file(tmp_path):
    path = tmp_path / "state.json"
    write_state(path, {"x": 1})
    leftovers = list(tmp_path.glob("*.tmp"))
    assert leftovers == []


def test_write_overwrites_cleanly(tmp_path):
    path = tmp_path / "state.json"
    write_state(path, {"phase": "investigating"})
    write_state(path, {"phase": "testing"})
    assert read_state(path) == {"phase": "testing"}


def test_cli_write_then_read(tmp_path):
    path = tmp_path / "state.json"
    subprocess.run(
        [sys.executable, str(SCRIPT), "write", str(path)],
        input=json.dumps({"ticket_id": "PROJ-2"}),
        text=True,
        check=True,
    )
    out = subprocess.run(
        [sys.executable, str(SCRIPT), "read", str(path)],
        capture_output=True,
        text=True,
        check=True,
    )
    assert json.loads(out.stdout) == {"ticket_id": "PROJ-2"}


def test_cli_read_missing_prints_null(tmp_path):
    out = subprocess.run(
        [sys.executable, str(SCRIPT), "read", str(tmp_path / "nope.json")],
        capture_output=True,
        text=True,
        check=True,
    )
    assert out.stdout.strip() == "null"
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_state_io.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'state_io'` (file doesn't exist yet).

- [ ] **Step 3: Write the implementation**

```python
# plugins/ticket-workflow/hooks/state_io.py
#!/usr/bin/env python3
"""
Atomic read/write helper for tickets/<id>/state.json.

Never edit state.json with a raw file write — an interruption mid-write can
leave a half-written, unparseable file. write_state() always writes to a
sibling .tmp file and os.replace()s it over the target, which is atomic on
both POSIX and Windows.

CLI:
  python3 state_io.py read <path>     # prints the JSON, or "null" if missing
  python3 state_io.py write <path>    # reads a JSON object from stdin
"""
import json
import os
import sys
from pathlib import Path


def read_state(path):
    p = Path(path)
    if not p.exists():
        return None
    with p.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def write_state(path, data):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    tmp = p.with_name(p.name + ".tmp")
    with tmp.open("w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, sort_keys=True)
        fh.write("\n")
    os.replace(tmp, p)


def main(argv):
    if len(argv) < 3:
        print("usage: state_io.py read|write <path>", file=sys.stderr)
        return 2
    op, path = argv[1], argv[2]
    if op == "read":
        json.dump(read_state(path), sys.stdout)
        sys.stdout.write("\n")
        return 0
    if op == "write":
        write_state(path, json.load(sys.stdin))
        return 0
    print(f"unknown op: {op}", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_state_io.py -v`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add plugins/ticket-workflow/hooks/state_io.py plugins/ticket-workflow/tests/test_state_io.py
git commit -m "ticket-workflow: add state_io.py atomic read/write helper"
```

---

### Task 2: `merge_config.py` — key-aware TOML merge

**Files:**
- Create: `plugins/ticket-workflow/hooks/merge_config.py`
- Test: `plugins/ticket-workflow/tests/test_merge_config.py`

**Interfaces:**
- Produces: `merge_toml_text(target_text: str, snippet_text: str) -> tuple[str, dict]` (returns merged text plus a report dict with boolean keys `features_table_added`, `features_key_inserted`, `mcp_table_added`, `mcp_table_skipped_existing`); `merge_toml_file(target_path, snippet_path) -> dict` (writes the file, makes a `.bak` first, rolls back and raises `MergeError` if the result doesn't parse); `MergeError` exception class.
- Consumes: nothing from earlier tasks.
- CLI: `python3 merge_config.py <target-config.toml> <snippet.toml>`. Task 6's `install.sh` calls this CLI form.

- [ ] **Step 1: Write the failing tests**

```python
# plugins/ticket-workflow/tests/test_merge_config.py
import sys
import tomllib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "hooks"))
from merge_config import MergeError, merge_toml_file, merge_toml_text  # noqa: E402

SNIPPET = (
    "[features]\n"
    "rmcp_client = true\n"
    "\n"
    "[mcp_servers.atlassian]\n"
    'url = "https://mcp.atlassian.com/v2/mcp"\n'
)


def test_empty_target_gets_both_tables():
    merged, report = merge_toml_text("", SNIPPET)
    parsed = tomllib.loads(merged)
    assert parsed["features"]["rmcp_client"] is True
    assert parsed["mcp_servers"]["atlassian"]["url"] == "https://mcp.atlassian.com/v2/mcp"
    assert report["features_table_added"] is True
    assert report["mcp_table_added"] is True


def test_existing_features_table_without_key_gets_key_inserted():
    target = 'sandbox_mode = "workspace-write"\n\n[features]\nsome_other_flag = true\n'
    merged, report = merge_toml_text(target, SNIPPET)
    parsed = tomllib.loads(merged)
    assert parsed["features"]["some_other_flag"] is True
    assert parsed["features"]["rmcp_client"] is True
    assert report["features_table_added"] is False
    assert report["features_key_inserted"] is True
    # only one [features] header in the result
    assert merged.count("[features]") == 1


def test_existing_features_table_with_key_already_true_is_idempotent():
    target = "[features]\nrmcp_client = true\n"
    merged, report = merge_toml_text(target, SNIPPET)
    assert merged.count("rmcp_client") == 1
    assert report["features_key_inserted"] is False


def test_existing_mcp_atlassian_table_is_skipped_not_duplicated():
    target = '[mcp_servers.atlassian]\nurl = "https://old-url.example"\n'
    merged, report = merge_toml_text(target, SNIPPET)
    assert merged.count("[mcp_servers.atlassian]") == 1
    assert "old-url.example" in merged
    assert report["mcp_table_skipped_existing"] is True


def test_root_keys_before_first_table_rule_preserved():
    # The gotcha documented at the top of this repo's own .codex/config.toml:
    # root scalar keys must precede the first [table]. A correct merge must
    # never insert a table header before an existing root key.
    target = 'sandbox_mode = "workspace-write"\napproval_policy = "on-request"\n'
    merged, report = merge_toml_text(target, SNIPPET)
    lines = [l for l in merged.splitlines() if l.strip()]
    first_table_index = next(i for i, l in enumerate(lines) if l.startswith("["))
    assert all(not lines[i].startswith("[") for i in range(0, 2))
    assert lines[0] == 'sandbox_mode = "workspace-write"'
    assert lines[1] == 'approval_policy = "on-request"'
    assert first_table_index == 2


def test_merge_toml_file_writes_backup_and_result(tmp_path):
    target = tmp_path / "config.toml"
    target.write_text('sandbox_mode = "workspace-write"\n', encoding="utf-8")
    snippet = tmp_path / "snippet.toml"
    snippet.write_text(SNIPPET, encoding="utf-8")

    report = merge_toml_file(target, snippet)

    assert report["features_table_added"] is True
    backup = target.with_name(target.name + ".bak")
    assert backup.exists()
    assert backup.read_text(encoding="utf-8") == 'sandbox_mode = "workspace-write"\n'
    parsed = tomllib.loads(target.read_text(encoding="utf-8"))
    assert parsed["features"]["rmcp_client"] is True


def test_merge_toml_file_creates_new_config_when_none_exists(tmp_path):
    target = tmp_path / "config.toml"
    snippet = tmp_path / "snippet.toml"
    snippet.write_text(SNIPPET, encoding="utf-8")

    merge_toml_file(target, snippet)

    assert target.exists()
    assert not target.with_name(target.name + ".bak").exists()
    tomllib.loads(target.read_text(encoding="utf-8"))


def test_merge_toml_file_rejects_unparseable_base_without_modifying_it(tmp_path):
    target = tmp_path / "config.toml"
    broken = "sandbox_mode = \n[features\n"  # deliberately invalid TOML
    target.write_text(broken, encoding="utf-8")
    snippet = tmp_path / "snippet.toml"
    snippet.write_text(SNIPPET, encoding="utf-8")

    with pytest.raises(MergeError):
        merge_toml_file(target, snippet)

    assert target.read_text(encoding="utf-8") == broken


import pytest  # noqa: E402
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_merge_config.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'merge_config'`.

- [ ] **Step 3: Write the implementation**

```python
# plugins/ticket-workflow/hooks/merge_config.py
#!/usr/bin/env python3
"""
Key-aware merge of the ticket-workflow plugin's MCP/feature config into an
existing Codex config.toml.

Blindly appending a snippet is unsafe: if the target already has its own
[features] table, TOML forbids a second [features] header and the file
stops parsing entirely. This merges by inspecting existing table headers
line-by-line instead of round-tripping through a TOML *writer* (the stdlib
only ships a reader, tomllib) — insert missing keys into an existing table,
append whole new tables at EOF (safe: table names are unique), and always
re-parse the result before trusting it, restoring a backup on failure.

CLI:
  python3 merge_config.py <target-config.toml> <snippet.toml>
"""
import shutil
import sys
import tomllib
from pathlib import Path


class MergeError(Exception):
    pass


def _header_line(lines, table_name):
    target = f"[{table_name}]"
    for i, line in enumerate(lines):
        if line.strip() == target:
            return i
    return None


def _block_end(lines, start_index):
    for i in range(start_index + 1, len(lines)):
        if lines[i].strip().startswith("["):
            return i
    return len(lines)


def _key_present(lines, start, end, key):
    prefix = f"{key} ="
    return any(lines[i].strip().startswith(prefix) for i in range(start, end))


def merge_toml_text(target_text, snippet_text):
    lines = target_text.splitlines()
    snippet_lines = snippet_text.splitlines()
    report = {
        "features_table_added": False,
        "features_key_inserted": False,
        "mcp_table_added": False,
        "mcp_table_skipped_existing": False,
    }

    snip_features_start = _header_line(snippet_lines, "features")
    rmcp_line = None
    if snip_features_start is not None:
        snip_features_end = _block_end(snippet_lines, snip_features_start)
        for i in range(snip_features_start + 1, snip_features_end):
            if snippet_lines[i].strip().startswith("rmcp_client"):
                rmcp_line = snippet_lines[i]
                break

    target_features_start = _header_line(lines, "features")
    if target_features_start is not None:
        target_features_end = _block_end(lines, target_features_start)
        if rmcp_line is not None and not _key_present(
            lines, target_features_start, target_features_end, "rmcp_client"
        ):
            lines.insert(target_features_start + 1, rmcp_line)
            report["features_key_inserted"] = True
    elif snip_features_start is not None:
        snip_features_end = _block_end(snippet_lines, snip_features_start)
        block = snippet_lines[snip_features_start:snip_features_end]
        if lines and lines[-1].strip() != "":
            lines.append("")
        lines.extend(block)
        report["features_table_added"] = True

    mcp_table = "mcp_servers.atlassian"
    if _header_line(lines, mcp_table) is not None:
        report["mcp_table_skipped_existing"] = True
    else:
        snip_mcp_start = _header_line(snippet_lines, mcp_table)
        if snip_mcp_start is not None:
            snip_mcp_end = _block_end(snippet_lines, snip_mcp_start)
            block = snippet_lines[snip_mcp_start:snip_mcp_end]
            if lines and lines[-1].strip() != "":
                lines.append("")
            lines.extend(block)
            report["mcp_table_added"] = True

    merged = "\n".join(lines)
    if merged and not merged.endswith("\n"):
        merged += "\n"

    try:
        tomllib.loads(merged)
    except tomllib.TOMLDecodeError as exc:
        raise MergeError(f"merged config.toml would not parse: {exc}") from exc

    return merged, report


def merge_toml_file(target_path, snippet_path):
    target_path = Path(target_path)
    snippet_path = Path(snippet_path)

    if target_path.exists():
        original_text = target_path.read_text(encoding="utf-8")
        try:
            tomllib.loads(original_text)
        except tomllib.TOMLDecodeError as exc:
            raise MergeError(f"existing config.toml does not parse, refusing to merge: {exc}") from exc
    else:
        original_text = ""

    snippet_text = snippet_path.read_text(encoding="utf-8")

    backup_path = target_path.with_name(target_path.name + ".bak")
    if target_path.exists():
        shutil.copyfile(target_path, backup_path)

    merged, report = merge_toml_text(original_text, snippet_text)

    target_path.write_text(merged, encoding="utf-8")

    try:
        tomllib.loads(target_path.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError as exc:
        if backup_path.exists():
            shutil.copyfile(backup_path, target_path)
        raise MergeError(f"post-write validation failed, restored backup: {exc}") from exc

    return report


def main(argv):
    if len(argv) != 3:
        print("usage: merge_config.py <target-config.toml> <snippet.toml>", file=sys.stderr)
        return 2
    try:
        report = merge_toml_file(argv[1], argv[2])
    except MergeError as exc:
        print(f"merge_config.py: {exc}", file=sys.stderr)
        return 1
    for key, value in report.items():
        print(f"{key}: {value}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_merge_config.py -v`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add plugins/ticket-workflow/hooks/merge_config.py plugins/ticket-workflow/tests/test_merge_config.py
git commit -m "ticket-workflow: add merge_config.py key-aware TOML merge"
```

---

### Task 3: `guard_ticket_commit.py` — commit-message enforcement hook

**Files:**
- Create: `plugins/ticket-workflow/hooks/guard_ticket_commit.py`
- Test: `plugins/ticket-workflow/tests/test_guard_ticket_commit.py`

**Interfaces:**
- Produces: `evaluate(command: str, active_ticket: str | None) -> dict | None` — returns the `hookSpecificOutput` deny payload, or `None` to allow. `find_active_ticket(workflow_root: Path) -> str | None` reads `.codex/tickets_active`.
- Consumes: nothing from earlier tasks (independent of `state_io`/`merge_config`).

- [ ] **Step 1: Write the failing tests**

```python
# plugins/ticket-workflow/tests/test_guard_ticket_commit.py
import json
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "hooks"))
from guard_ticket_commit import evaluate, find_active_ticket  # noqa: E402

SCRIPT = Path(__file__).resolve().parents[1] / "hooks" / "guard_ticket_commit.py"


def test_no_active_ticket_always_allows():
    assert evaluate('git commit -m "bad message"', active_ticket=None) is None


def test_active_ticket_non_git_command_allows():
    assert evaluate("mvn test", active_ticket="PROJ-1") is None


def test_active_ticket_correct_message_allows():
    assert evaluate('git commit -m "[PROJ-1][dev-be] add endpoint"', active_ticket="PROJ-1") is None


def test_active_ticket_wrong_ticket_id_denies():
    result = evaluate('git commit -m "[OTHER-999][dev-be] x"', active_ticket="PROJ-1")
    assert result is not None
    assert result["hookSpecificOutput"]["permissionDecision"] == "deny"


def test_active_ticket_bad_phase_tag_denies():
    result = evaluate('git commit -m "[PROJ-1][anything] x"', active_ticket="PROJ-1")
    assert result["hookSpecificOutput"]["permissionDecision"] == "deny"


def test_active_ticket_no_brackets_denies():
    result = evaluate('git commit -m "just a message"', active_ticket="PROJ-1")
    assert result["hookSpecificOutput"]["permissionDecision"] == "deny"


def test_all_four_phase_tags_allowed():
    for tag in ("dev-be", "dev-ui", "fix-be", "fix-ui"):
        assert evaluate(f'git commit -m "[PROJ-1][{tag}] x"', active_ticket="PROJ-1") is None


def test_compound_command_with_echo_first_is_not_caught_documented_limit():
    # Documented scope limit (spec: guard_ticket_commit.py section): the
    # hook inspects tool_input.command as a single string and only treats
    # it as a commit if the FIRST token is `git`. A chained command whose
    # first token isn't `git` is intentionally out of scope.
    cmd = 'echo hi && git commit -m "bad message"'
    assert evaluate(cmd, active_ticket="PROJ-1") is None


def test_git_status_is_not_treated_as_commit():
    assert evaluate("git status", active_ticket="PROJ-1") is None


def test_find_active_ticket_reads_pointer_file(tmp_path):
    (tmp_path / ".codex").mkdir()
    (tmp_path / ".codex" / "tickets_active").write_text("PROJ-7\n", encoding="utf-8")
    assert find_active_ticket(tmp_path) == "PROJ-7"


def test_find_active_ticket_missing_returns_none(tmp_path):
    assert find_active_ticket(tmp_path) is None


def test_cli_denies_via_stdin(tmp_path, monkeypatch):
    codex_dir = tmp_path / ".codex"
    codex_dir.mkdir()
    (codex_dir / "tickets_active").write_text("PROJ-1", encoding="utf-8")
    event = {"tool_name": "Bash", "tool_input": {"command": "git commit -m \"bad\""}}
    out = subprocess.run(
        [sys.executable, str(SCRIPT), str(tmp_path)],
        input=json.dumps(event),
        capture_output=True,
        text=True,
        check=True,
    )
    payload = json.loads(out.stdout)
    assert payload["hookSpecificOutput"]["permissionDecision"] == "deny"


def test_cli_allows_prints_nothing(tmp_path):
    event = {"tool_name": "Bash", "tool_input": {"command": "git status"}}
    out = subprocess.run(
        [sys.executable, str(SCRIPT), str(tmp_path)],
        input=json.dumps(event),
        capture_output=True,
        text=True,
        check=True,
    )
    assert out.stdout.strip() == ""
    assert out.returncode == 0
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_guard_ticket_commit.py -v`
Expected: FAIL — `ModuleNotFoundError`.

- [ ] **Step 3: Write the implementation**

```python
# plugins/ticket-workflow/hooks/guard_ticket_commit.py
#!/usr/bin/env python3
"""
PreToolUse hook (matcher: Bash). While a ticket workflow is active
(.codex/tickets_active names one), denies any `git commit` whose message
doesn't match `[<ACTIVE-TICKET-ID>][dev-be|dev-ui|fix-be|fix-ui] ...`.

Scope limit, by design (see docs/specs/2026-09-13-ticket-workflow-plugin-design.md,
Hooks section): only the literal tool_input.command string is inspected, and
only when its first whitespace-separated token is `git` with `commit` among
the remaining args. A message via `-F <file>`, `--amend`, or a `git commit`
issued as a later command in a `&&`/`;` chain are not caught — the workflow
itself never issues those forms.

CLI:
  python3 guard_ticket_commit.py [workflow_root]
  (reads a PreToolUse JSON event on stdin; workflow_root defaults to this
  script's own repo root, resolved from its own file location)
"""
import json
import re
import sys
from pathlib import Path

PHASE_TAGS = ("dev-be", "dev-ui", "fix-be", "fix-ui")


def _resolve_workflow_root(argv):
    if len(argv) > 1:
        return Path(argv[1])
    # <root>/.codex/hooks/guard_ticket_commit.py -> parents[2] is <root>
    return Path(__file__).resolve().parents[2]


def find_active_ticket(workflow_root):
    pointer = Path(workflow_root) / ".codex" / "tickets_active"
    if not pointer.exists():
        return None
    value = pointer.read_text(encoding="utf-8").strip()
    return value or None


def _is_git_commit(command):
    tokens = command.strip().split()
    return bool(tokens) and tokens[0] == "git" and "commit" in tokens[1:]


def evaluate(command, active_ticket):
    if not active_ticket:
        return None
    if not _is_git_commit(command):
        return None

    escaped_ticket = re.escape(active_ticket)
    phase_group = "|".join(PHASE_TAGS)
    pattern = rf"\[{escaped_ticket}\]\[({phase_group})\]"
    if re.search(pattern, command):
        return None

    return {
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": (
                f"Blocked by guard_ticket_commit.py: while ticket {active_ticket} is "
                f"active, every commit message must match "
                f"[{active_ticket}][dev-be|dev-ui|fix-be|fix-ui] <summary>. "
                f"Got: {command!r}"
            ),
        }
    }


def main(argv):
    workflow_root = _resolve_workflow_root(argv)
    try:
        event = json.load(sys.stdin)
    except json.JSONDecodeError:
        return 0

    command = (event.get("tool_input") or {}).get("command", "")
    if isinstance(command, list):
        command = " ".join(str(part) for part in command)

    active_ticket = find_active_ticket(workflow_root)
    result = evaluate(command, active_ticket)
    if result is not None:
        json.dump(result, sys.stdout)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_guard_ticket_commit.py -v`
Expected: PASS (13 tests).

- [ ] **Step 5: Commit**

```bash
git add plugins/ticket-workflow/hooks/guard_ticket_commit.py plugins/ticket-workflow/tests/test_guard_ticket_commit.py
git commit -m "ticket-workflow: add guard_ticket_commit.py PreToolUse hook"
```

---

### Task 4: `ticket_audit.py` — PostToolUse audit hook

**Files:**
- Create: `plugins/ticket-workflow/hooks/ticket_audit.py`
- Test: `plugins/ticket-workflow/tests/test_ticket_audit.py`

**Interfaces:**
- Consumes: `state_io.read_state` (Task 1) and `guard_ticket_commit.find_active_ticket` (Task 3) — imported directly, not subprocessed.
- Produces: `format_log_line(event: dict, ticket_id: str | None, phase: str | None) -> str`.

- [ ] **Step 1: Write the failing tests**

```python
# plugins/ticket-workflow/tests/test_ticket_audit.py
import json
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "hooks"))
from ticket_audit import format_log_line  # noqa: E402

SCRIPT = Path(__file__).resolve().parents[1] / "hooks" / "ticket_audit.py"


def test_format_log_line_includes_ticket_and_phase():
    event = {"session_id": "s1", "tool_name": "Bash", "tool_input": {"command": "mvn test"}}
    line = format_log_line(event, ticket_id="PROJ-1", phase="testing")
    assert "PROJ-1" in line
    assert "testing" in line
    assert "mvn test" in line
    assert "s1" in line


def test_format_log_line_without_active_ticket():
    event = {"session_id": "s1", "tool_name": "Bash", "tool_input": {"command": "ls"}}
    line = format_log_line(event, ticket_id=None, phase=None)
    assert "ticket=none" in line


def test_cli_appends_line_and_creates_dirs(tmp_path):
    (tmp_path / ".codex").mkdir()
    (tmp_path / ".codex" / "tickets_active").write_text("PROJ-1", encoding="utf-8")
    ticket_dir = tmp_path / "tickets" / "PROJ-1"
    ticket_dir.mkdir(parents=True)
    (ticket_dir / "state.json").write_text(
        json.dumps({"phase": "dev_in_progress"}), encoding="utf-8"
    )

    event = {"session_id": "s1", "tool_name": "Bash", "tool_input": {"command": "mvn test"}}
    subprocess.run(
        [sys.executable, str(SCRIPT), str(tmp_path)],
        input=json.dumps(event),
        text=True,
        check=True,
    )

    log_path = ticket_dir / "audit.log"
    assert log_path.exists()
    content = log_path.read_text(encoding="utf-8")
    assert "dev_in_progress" in content
    assert "mvn test" in content


def test_cli_two_calls_append_two_lines(tmp_path):
    (tmp_path / ".codex").mkdir()
    (tmp_path / ".codex" / "tickets_active").write_text("PROJ-1", encoding="utf-8")
    (tmp_path / "tickets" / "PROJ-1").mkdir(parents=True)

    event = {"session_id": "s1", "tool_name": "Bash", "tool_input": {"command": "ls"}}
    for _ in range(2):
        subprocess.run(
            [sys.executable, str(SCRIPT), str(tmp_path)],
            input=json.dumps(event),
            text=True,
            check=True,
        )

    log_path = tmp_path / "tickets" / "PROJ-1" / "audit.log"
    lines = log_path.read_text(encoding="utf-8").splitlines()
    assert len(lines) == 2


def test_cli_no_active_ticket_does_not_crash(tmp_path):
    event = {"session_id": "s1", "tool_name": "Bash", "tool_input": {"command": "ls"}}
    result = subprocess.run(
        [sys.executable, str(SCRIPT), str(tmp_path)],
        input=json.dumps(event),
        text=True,
        capture_output=True,
    )
    assert result.returncode == 0
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_ticket_audit.py -v`
Expected: FAIL — `ModuleNotFoundError`.

- [ ] **Step 3: Write the implementation**

```python
# plugins/ticket-workflow/hooks/ticket_audit.py
#!/usr/bin/env python3
"""
PostToolUse hook (matcher: *). Extends this repo's existing audit_log.py
pattern: appends one line per tool call to tickets/<id>/audit.log, with the
current ticket ID and phase (read from state.json) included, so a session
that gets interrupted still leaves a durable trace independent of the
transcript UI. Pure observability — never denies anything.

CLI:
  python3 ticket_audit.py [workflow_root]
  (reads a PostToolUse JSON event on stdin)
"""
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from guard_ticket_commit import find_active_ticket  # noqa: E402
from state_io import read_state  # noqa: E402


def _resolve_workflow_root(argv):
    if len(argv) > 1:
        return Path(argv[1])
    return Path(__file__).resolve().parents[2]


def format_log_line(event, ticket_id, phase):
    session_id = event.get("session_id", "unknown-session")
    tool_name = event.get("tool_name", "unknown-tool")
    tool_input = event.get("tool_input") or {}
    command = tool_input.get("command", "")
    if isinstance(command, list):
        command = " ".join(str(part) for part in command)
    return (
        f"{datetime.now(timezone.utc).isoformat()} "
        f"ticket={ticket_id or 'none'} phase={phase or 'none'} "
        f"session={session_id} tool={tool_name} command={command!r}\n"
    )


def main(argv):
    workflow_root = _resolve_workflow_root(argv)
    try:
        event = json.load(sys.stdin)
    except json.JSONDecodeError:
        return 0

    ticket_id = find_active_ticket(workflow_root)
    phase = None
    if ticket_id:
        state = read_state(workflow_root / "tickets" / ticket_id / "state.json")
        if state:
            phase = state.get("phase")

    if ticket_id:
        log_path = workflow_root / "tickets" / ticket_id / "audit.log"
        log_path.parent.mkdir(parents=True, exist_ok=True)
        with log_path.open("a", encoding="utf-8") as fh:
            fh.write(format_log_line(event, ticket_id, phase))

    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_ticket_audit.py -v`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add plugins/ticket-workflow/hooks/ticket_audit.py plugins/ticket-workflow/tests/test_ticket_audit.py
git commit -m "ticket-workflow: add ticket_audit.py PostToolUse hook"
```

---

### Task 5: `verify_step.py` — phase-gate verification script

**Files:**
- Create: `plugins/ticket-workflow/hooks/verify_step.py`
- Test: `plugins/ticket-workflow/tests/test_verify_step.py`
- Test: `plugins/ticket-workflow/tests/conftest.py` (shared git-repo fixture)

**Interfaces:**
- Consumes: `state_io.read_state` (Task 1).
- Produces: `parse_bugs_table(path: Path) -> list[dict]`; `check_spec_ready(state: dict, workflow_root: Path) -> tuple[bool, str]`; `check_dev_round(state: dict, workflow_root: Path) -> tuple[bool, str]`; `check_done(state: dict, workflow_root: Path) -> tuple[bool, str]`. Each returns `(ok, reason)`. CLI `verify_step.py <workflow_root> <ticket-id> <phase>` exits `0`/`1` and prints `reason`.

- [ ] **Step 1: Write the shared git fixture**

```python
# plugins/ticket-workflow/tests/conftest.py
import subprocess
from pathlib import Path

import pytest


def _git(repo, *args):
    return subprocess.run(
        ["git", "-C", str(repo), *args],
        capture_output=True,
        text=True,
        check=True,
    )


@pytest.fixture
def git_repo(tmp_path):
    """A throwaway git repo on branch 'main' with one initial commit.

    Includes a .gitignore for tickets/ and .worktrees/, matching the real
    target-repo setup install.sh produces (see Task 6's gitignore-snippet).
    Without this, files check_done writes under tickets/<id>/ (e.g. bugs.md)
    show up as untracked in `git status --porcelain`, which would trip the
    "checkout must be clean" check for a reason that has nothing to do with
    what the test is actually verifying — tickets/ is never meant to be
    tracked at all.
    """
    repo = tmp_path / "repo"
    repo.mkdir()
    _git(repo, "init", "-b", "main")
    _git(repo, "config", "user.email", "test@example.com")
    _git(repo, "config", "user.name", "Test")
    (repo / "README.md").write_text("init\n", encoding="utf-8")
    (repo / ".gitignore").write_text("tickets/\n.worktrees/\n", encoding="utf-8")
    _git(repo, "add", "README.md", ".gitignore")
    _git(repo, "commit", "-m", "init")
    return repo


@pytest.fixture
def make_commit():
    def _make(repo, filename, content, message, trailers=None):
        (repo / filename).write_text(content, encoding="utf-8")
        _git(repo, "add", filename)
        full_message = message
        if trailers:
            full_message += "\n\n" + "\n".join(f"{k}: {v}" for k, v in trailers.items())
        _git(repo, "commit", "-m", full_message)
        return _git(repo, "rev-parse", "HEAD").stdout.strip()

    return _make
```

- [ ] **Step 2: Write the failing tests**

```python
# plugins/ticket-workflow/tests/test_verify_step.py
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "hooks"))
from state_io import write_state  # noqa: E402
from verify_step import (  # noqa: E402
    check_dev_round,
    check_done,
    check_spec_ready,
    parse_bugs_table,
)

SCRIPT = Path(__file__).resolve().parents[1] / "hooks" / "verify_step.py"

BUGS_HEADER = (
    "| ID | Area | Severity | Description | Status | Found in commit | Fixed in commit |\n"
    "|----|------|----------|--------------|--------|------------------|------------------|\n"
)


def test_parse_bugs_table_empty(tmp_path):
    path = tmp_path / "bugs.md"
    path.write_text(BUGS_HEADER, encoding="utf-8")
    assert parse_bugs_table(path) == []


def test_parse_bugs_table_missing_file_is_empty(tmp_path):
    assert parse_bugs_table(tmp_path / "nope.md") == []


def test_parse_bugs_table_reads_rows(tmp_path):
    path = tmp_path / "bugs.md"
    path.write_text(
        BUGS_HEADER + "| BUG-1 | BE | high | crashes | open | abc123 |  |\n",
        encoding="utf-8",
    )
    rows = parse_bugs_table(path)
    assert len(rows) == 1
    assert rows[0]["id"] == "BUG-1"
    assert rows[0]["status"] == "open"


def test_check_spec_ready_missing_file(tmp_path):
    state = {"ticket_id": "PROJ-1", "spec_file": "tickets/PROJ-1/PROJ-1-spec.md"}
    ok, reason = check_spec_ready(state, tmp_path)
    assert ok is False
    assert "spec" in reason.lower()


def test_check_spec_ready_present(tmp_path):
    spec = tmp_path / "tickets" / "PROJ-1" / "PROJ-1-spec.md"
    spec.parent.mkdir(parents=True)
    spec.write_text("# spec\n", encoding="utf-8")
    state = {"ticket_id": "PROJ-1", "spec_file": "tickets/PROJ-1/PROJ-1-spec.md"}
    ok, _ = check_spec_ready(state, tmp_path)
    assert ok is True


def test_check_dev_round_no_bugs_assigned_side_passes_without_commit(git_repo):
    state = {
        "base_branch": "main",
        "dev_round": {
            "round_id": 1,
            "be": {"status": "no_bugs_assigned", "commit": None, "bug_ids": []},
            "ui": {"status": "no_bugs_assigned", "commit": None, "bug_ids": []},
        },
    }
    ok, _ = check_dev_round(state, git_repo)
    assert ok is True


def test_check_dev_round_merged_side_requires_trailers_and_ancestry(git_repo, make_commit):
    sha = make_commit(
        git_repo,
        "backend.txt",
        "code\n",
        "[PROJ-1][dev-be] add endpoint",
        trailers={
            "Ticket-Workflow": "PROJ-1",
            "Ticket-Round": "1",
            "Ticket-Side": "be",
            "Ticket-Complete": "true",
        },
    )
    state = {
        "ticket_id": "PROJ-1",
        "base_branch": "main",
        "dev_round": {
            "round_id": 1,
            "be": {"status": "merged", "commit": sha, "bug_ids": []},
            "ui": {"status": "no_bugs_assigned", "commit": None, "bug_ids": []},
        },
    }
    ok, reason = check_dev_round(state, git_repo)
    assert ok is True, reason


def test_check_dev_round_commit_without_completion_trailer_fails(git_repo, make_commit):
    sha = make_commit(git_repo, "backend.txt", "wip\n", "[PROJ-1][dev-be] wip")
    state = {
        "ticket_id": "PROJ-1",
        "base_branch": "main",
        "dev_round": {
            "round_id": 1,
            "be": {"status": "merged", "commit": sha, "bug_ids": []},
            "ui": {"status": "no_bugs_assigned", "commit": None, "bug_ids": []},
        },
    }
    ok, reason = check_dev_round(state, git_repo)
    assert ok is False
    assert "trailer" in reason.lower()


def test_check_dev_round_merged_but_not_ancestor_of_base_fails(git_repo, make_commit):
    subprocess.run(["git", "-C", str(git_repo), "checkout", "-b", "side"], check=True)
    sha = make_commit(
        git_repo,
        "backend.txt",
        "code\n",
        "[PROJ-1][dev-be] add endpoint",
        trailers={
            "Ticket-Workflow": "PROJ-1",
            "Ticket-Round": "1",
            "Ticket-Side": "be",
            "Ticket-Complete": "true",
        },
    )
    subprocess.run(["git", "-C", str(git_repo), "checkout", "main"], check=True)
    state = {
        "ticket_id": "PROJ-1",
        "base_branch": "main",
        "dev_round": {
            "round_id": 1,
            "be": {"status": "merged", "commit": sha, "bug_ids": []},
            "ui": {"status": "no_bugs_assigned", "commit": None, "bug_ids": []},
        },
    }
    ok, reason = check_dev_round(state, git_repo)
    assert ok is False
    assert "ancestor" in reason.lower() or "merge" in reason.lower()


def test_check_done_requires_passed_result_not_just_empty_bugs(git_repo):
    bugs = git_repo / "tickets" / "PROJ-1" / "bugs.md"
    bugs.parent.mkdir(parents=True)
    bugs.write_text(BUGS_HEADER, encoding="utf-8")
    head = subprocess.run(
        ["git", "-C", str(git_repo), "rev-parse", "HEAD"], capture_output=True, text=True, check=True
    ).stdout.strip()
    state = {
        "ticket_id": "PROJ-1",
        "base_branch": "main",
        "bug_track_file": "tickets/PROJ-1/bugs.md",
        "test_report": {
            "commit": head,
            "result": "blocked",
            "acceptance_criteria_checked": True,
            "clean_before": True,
            "clean_after": True,
        },
    }
    ok, reason = check_done(state, git_repo)
    assert ok is False
    assert "blocked" in reason.lower() or "result" in reason.lower()


def test_check_done_passes_with_passed_result_matching_head(git_repo):
    bugs = git_repo / "tickets" / "PROJ-1" / "bugs.md"
    bugs.parent.mkdir(parents=True)
    bugs.write_text(BUGS_HEADER, encoding="utf-8")
    head = subprocess.run(
        ["git", "-C", str(git_repo), "rev-parse", "HEAD"], capture_output=True, text=True, check=True
    ).stdout.strip()
    state = {
        "ticket_id": "PROJ-1",
        "base_branch": "main",
        "bug_track_file": "tickets/PROJ-1/bugs.md",
        "test_report": {
            "commit": head,
            "result": "passed",
            "acceptance_criteria_checked": True,
            "clean_before": True,
            "clean_after": True,
        },
    }
    ok, reason = check_done(state, git_repo)
    assert ok is True, reason


def test_check_done_fails_when_report_commit_is_stale(git_repo, make_commit):
    bugs = git_repo / "tickets" / "PROJ-1" / "bugs.md"
    bugs.parent.mkdir(parents=True)
    bugs.write_text(BUGS_HEADER, encoding="utf-8")
    stale_head = subprocess.run(
        ["git", "-C", str(git_repo), "rev-parse", "HEAD"], capture_output=True, text=True, check=True
    ).stdout.strip()
    make_commit(git_repo, "later.txt", "x\n", "[PROJ-1][dev-be] later change")
    state = {
        "ticket_id": "PROJ-1",
        "base_branch": "main",
        "bug_track_file": "tickets/PROJ-1/bugs.md",
        "test_report": {
            "commit": stale_head,
            "result": "passed",
            "acceptance_criteria_checked": True,
            "clean_before": True,
            "clean_after": True,
        },
    }
    ok, reason = check_done(state, git_repo)
    assert ok is False
    assert "stale" in reason.lower() or "commit" in reason.lower()


def test_check_done_fails_with_open_bug_row(git_repo):
    bugs = git_repo / "tickets" / "PROJ-1" / "bugs.md"
    bugs.parent.mkdir(parents=True)
    bugs.write_text(
        BUGS_HEADER + "| BUG-1 | BE | high | x | open | abc |  |\n", encoding="utf-8"
    )
    head = subprocess.run(
        ["git", "-C", str(git_repo), "rev-parse", "HEAD"], capture_output=True, text=True, check=True
    ).stdout.strip()
    state = {
        "ticket_id": "PROJ-1",
        "base_branch": "main",
        "bug_track_file": "tickets/PROJ-1/bugs.md",
        "test_report": {
            "commit": head,
            "result": "passed",
            "acceptance_criteria_checked": True,
            "clean_before": True,
            "clean_after": True,
        },
    }
    ok, reason = check_done(state, git_repo)
    assert ok is False


def test_cli_exits_nonzero_and_prints_reason_on_failure(tmp_path):
    write_state(tmp_path / "tickets" / "PROJ-1" / "state.json", {"ticket_id": "PROJ-1", "spec_file": "tickets/PROJ-1/PROJ-1-spec.md"})
    result = subprocess.run(
        [sys.executable, str(SCRIPT), str(tmp_path), "PROJ-1", "spec_ready"],
        capture_output=True,
        text=True,
    )
    assert result.returncode == 1
    assert result.stdout.strip() != ""
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_verify_step.py -v`
Expected: FAIL — `ModuleNotFoundError`.

- [ ] **Step 4: Write the implementation**

```python
# plugins/ticket-workflow/hooks/verify_step.py
#!/usr/bin/env python3
"""
Deterministic per-phase gate the Skill calls explicitly after each subagent
returns, before advancing state.json — NOT a Codex lifecycle hook.

CLI:
  python3 verify_step.py <workflow_root> <ticket-id> <phase>
Exit 0 = phase's requirements are satisfied; exit 1 = not yet, reason on
stdout. phase is one of: spec_ready, dev_round, done (dev_round covers both
the initial dev_in_progress round and every fixing round — same check).
"""
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from state_io import read_state  # noqa: E402

REQUIRED_TRAILERS = ("Ticket-Workflow", "Ticket-Round", "Ticket-Side", "Ticket-Complete")


def _git(repo, *args):
    return subprocess.run(
        ["git", "-C", str(repo), *args],
        capture_output=True,
        text=True,
    )


def parse_bugs_table(path):
    path = Path(path)
    if not path.exists():
        return []
    lines = path.read_text(encoding="utf-8").splitlines()
    rows = []
    for line in lines:
        stripped = line.strip()
        if not stripped.startswith("|"):
            continue
        cells = [c.strip() for c in stripped.strip("|").split("|")]
        if not cells or cells[0].lower() == "id":
            continue
        if set(cells[0]) <= {"-"}:
            continue
        if len(cells) < 5:
            continue
        rows.append(
            {
                "id": cells[0],
                "area": cells[1],
                "severity": cells[2],
                "description": cells[3],
                "status": cells[4],
                "found_commit": cells[5] if len(cells) > 5 else "",
                "fixed_commit": cells[6] if len(cells) > 6 else "",
            }
        )
    return rows


def check_spec_ready(state, workflow_root):
    spec_file = state.get("spec_file")
    if not spec_file:
        return False, "state.json has no spec_file recorded"
    path = Path(workflow_root) / spec_file
    if not path.exists():
        return False, f"spec file not found: {spec_file}"
    return True, "spec file present"


def _has_completion_trailers(repo, sha, ticket_id, round_id, side):
    result = _git(repo, "show", "-s", "--format=%B", sha)
    if result.returncode != 0:
        return False, f"commit {sha} not found in repo"
    body = result.stdout
    expected = {
        "Ticket-Workflow": ticket_id,
        "Ticket-Round": str(round_id),
        "Ticket-Side": side,
        "Ticket-Complete": "true",
    }
    for key, value in expected.items():
        if f"{key}: {value}" not in body:
            return False, f"commit {sha} missing trailer '{key}: {value}'"
    return True, "trailers present"


def check_dev_round(state, workflow_root):
    ticket_id = state.get("ticket_id")
    base_branch = state.get("base_branch")
    dev_round = state.get("dev_round") or {}
    round_id = dev_round.get("round_id")

    for side in ("be", "ui"):
        side_state = dev_round.get(side) or {}
        status = side_state.get("status")

        if status == "no_bugs_assigned":
            continue
        if status not in ("merged", "committed"):
            return False, f"{side} dev_round status is {status!r}, not yet complete"

        sha = side_state.get("commit")
        if not sha:
            return False, f"{side} has status {status!r} but no commit recorded"

        ok, reason = _has_completion_trailers(workflow_root, sha, ticket_id, round_id, side)
        if not ok:
            return False, f"{side}: {reason}"

        if status == "merged":
            result = _git(workflow_root, "merge-base", "--is-ancestor", sha, base_branch)
            if result.returncode == 1:
                return False, f"{side} commit {sha} is not an ancestor of {base_branch} (merge pending)"
            if result.returncode not in (0, 1):
                return False, f"{side} ancestry check errored: {result.stderr.strip()}"

    return True, "dev_round complete"


def check_done(state, workflow_root):
    ticket_id = state.get("ticket_id")
    base_branch = state.get("base_branch")
    bug_track_file = state.get("bug_track_file")
    report = state.get("test_report") or {}

    if report.get("result") != "passed":
        return False, f"test_report.result is {report.get('result')!r}, not 'passed'"

    for flag in ("acceptance_criteria_checked", "clean_before", "clean_after"):
        if not report.get(flag):
            return False, f"test_report.{flag} is not true"

    head = _git(workflow_root, "rev-parse", base_branch).stdout.strip()
    if not head or report.get("commit") != head:
        return False, (
            f"test_report.commit ({report.get('commit')}) is stale — "
            f"{base_branch} is now at {head}"
        )

    status_result = _git(workflow_root, "status", "--porcelain")
    if status_result.stdout.strip():
        return False, "main checkout is not clean"

    if bug_track_file:
        rows = parse_bugs_table(Path(workflow_root) / bug_track_file)
        for row in rows:
            if row["status"] not in ("verified-fixed", "wont-fix"):
                return False, f"bug {row['id']} status is {row['status']!r}, not resolved"

    return True, f"done: {ticket_id} passed at {head}"


def main(argv):
    if len(argv) != 4:
        print("usage: verify_step.py <workflow_root> <ticket-id> <phase>", file=sys.stderr)
        return 2
    workflow_root, ticket_id, phase = Path(argv[1]), argv[2], argv[3]

    state = read_state(workflow_root / "tickets" / ticket_id / "state.json")
    if state is None:
        print(f"no state.json found for {ticket_id}")
        return 1

    checks = {
        "spec_ready": check_spec_ready,
        "dev_round": check_dev_round,
        "done": check_done,
    }
    check = checks.get(phase)
    if check is None:
        print(f"unknown phase: {phase}")
        return 2

    ok, reason = check(state, workflow_root)
    print(reason)
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_verify_step.py -v`
Expected: PASS (14 tests).

- [ ] **Step 6: Commit**

```bash
git add plugins/ticket-workflow/hooks/verify_step.py plugins/ticket-workflow/tests/test_verify_step.py plugins/ticket-workflow/tests/conftest.py
git commit -m "ticket-workflow: add verify_step.py phase-gate script"
```

---

### Task 6: `install.sh` + config/hooks/gitignore snippets

**Files:**
- Create: `plugins/ticket-workflow/config-snippet.toml`
- Create: `plugins/ticket-workflow/hooks-snippet.json`
- Create: `plugins/ticket-workflow/gitignore-snippet`
- Create: `plugins/ticket-workflow/install.sh`
- Test: `plugins/ticket-workflow/tests/test_install.py`

**Interfaces:**
- Consumes: `merge_config.py` CLI (Task 2), calls it as a subprocess with `python3`.
- Produces: nothing new for later tasks to import — this is the end-user-facing entry point tasks 7-10's files get copied by.

- [ ] **Step 1: Write the snippet files**

```toml
# plugins/ticket-workflow/config-snippet.toml
[features]
rmcp_client = true

[mcp_servers.atlassian]
url = "https://mcp.atlassian.com/v2/mcp"
startup_timeout_sec = 15
tool_timeout_sec = 30
enabled = true
```

```json
// plugins/ticket-workflow/hooks-snippet.json
//
// NOTE: nested under a top-level "hooks" key — matches the real shape of
// this repo's own .codex/hooks.json (verified by reading it directly), not
// a flat {"PreToolUse": [...]} object.
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "python3 .codex/hooks/guard_ticket_commit.py",
            "timeout": 10,
            "statusMessage": "Checking commit message against active ticket"
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "*",
        "hooks": [
          {
            "type": "command",
            "command": "python3 .codex/hooks/ticket_audit.py",
            "timeout": 10,
            "statusMessage": "Writing ticket-workflow audit log entry",
            "async": true
          }
        ]
      }
    ]
  }
}
```

```
tickets/
.worktrees/
.codex/tickets_active
```

- [ ] **Step 2: Write the failing tests**

```python
# plugins/ticket-workflow/tests/test_install.py
import json
import subprocess
import tomllib
from pathlib import Path

PLUGIN_ROOT = Path(__file__).resolve().parents[1]
INSTALL_SH = PLUGIN_ROOT / "install.sh"


def _init_target_repo(tmp_path):
    target = tmp_path / "target-repo"
    target.mkdir()
    subprocess.run(["git", "-C", str(target), "init", "-b", "main"], check=True)
    return target


def _run_install(target):
    return subprocess.run(
        ["bash", str(INSTALL_SH)],
        cwd=str(target),
        capture_output=True,
        text=True,
    )


def test_install_copies_skill_agents_hooks(tmp_path):
    target = _init_target_repo(tmp_path)
    result = _run_install(target)
    assert result.returncode == 0, result.stderr

    assert (target / ".agents" / "skills" / "implement-ticket" / "SKILL.md").exists()
    for name in ("ticket-investigator", "ticket-be-dev", "ticket-ui-dev", "ticket-tester"):
        assert (target / ".codex" / "agents" / f"{name}.toml").exists()
    for name in ("state_io.py", "merge_config.py", "guard_ticket_commit.py", "ticket_audit.py", "verify_step.py"):
        assert (target / ".codex" / "hooks" / name).exists()


def test_install_merges_config_toml(tmp_path):
    target = _init_target_repo(tmp_path)
    (target / ".codex").mkdir()
    (target / ".codex" / "config.toml").write_text(
        'sandbox_mode = "workspace-write"\n', encoding="utf-8"
    )
    _run_install(target)

    parsed = tomllib.loads((target / ".codex" / "config.toml").read_text(encoding="utf-8"))
    assert parsed["features"]["rmcp_client"] is True
    assert parsed["mcp_servers"]["atlassian"]["url"] == "https://mcp.atlassian.com/v2/mcp"


def test_install_creates_config_toml_when_absent(tmp_path):
    target = _init_target_repo(tmp_path)
    _run_install(target)
    config_path = target / ".codex" / "config.toml"
    assert config_path.exists()
    tomllib.loads(config_path.read_text(encoding="utf-8"))


def test_install_merges_hooks_json_into_existing(tmp_path):
    # Real shape, matching this repo's own .codex/hooks.json: everything
    # nested under a top-level "hooks" key.
    target = _init_target_repo(tmp_path)
    (target / ".codex").mkdir()
    existing = {
        "hooks": {
            "PreToolUse": [
                {"matcher": "Bash", "hooks": [{"type": "command", "command": "python3 x.py"}]}
            ]
        }
    }
    (target / ".codex" / "hooks.json").write_text(json.dumps(existing), encoding="utf-8")

    _run_install(target)

    merged = json.loads((target / ".codex" / "hooks.json").read_text(encoding="utf-8"))
    pre_commands = [
        h["command"] for entry in merged["hooks"]["PreToolUse"] for h in entry["hooks"]
    ]
    assert "python3 x.py" in pre_commands
    assert "python3 .codex/hooks/guard_ticket_commit.py" in pre_commands
    assert "PostToolUse" in merged["hooks"]


def test_install_appends_gitignore_lines(tmp_path):
    target = _init_target_repo(tmp_path)
    (target / ".gitignore").write_text("node_modules/\n", encoding="utf-8")

    _run_install(target)

    content = (target / ".gitignore").read_text(encoding="utf-8")
    assert "tickets/" in content
    assert ".worktrees/" in content
    assert ".codex/tickets_active" in content
    assert "node_modules/" in content


def test_install_is_idempotent(tmp_path):
    target = _init_target_repo(tmp_path)
    _run_install(target)
    result = _run_install(target)
    assert result.returncode == 0, result.stderr

    content = (target / ".gitignore").read_text(encoding="utf-8")
    assert content.count("tickets/") == 1

    hooks = json.loads((target / ".codex" / "hooks.json").read_text(encoding="utf-8"))
    pre_commands = [
        h["command"] for entry in hooks["hooks"]["PreToolUse"] for h in entry["hooks"]
    ]
    assert pre_commands.count("python3 .codex/hooks/guard_ticket_commit.py") == 1
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_install.py -v`
Expected: FAIL — `install.sh` doesn't exist yet (`No such file or directory`).

- [ ] **Step 4: Write `install.sh`**

```bash
#!/usr/bin/env bash
# plugins/ticket-workflow/install.sh
#
# Installs the ticket-workflow plugin into the CURRENT directory (run this
# from the target repo's root). Copies the skill, subagent roles, and hook
# scripts; key-merges the MCP/feature config and hooks registration instead
# of overwriting; appends the required .gitignore lines. Idempotent.
set -euo pipefail

PLUGIN_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_ROOT="$(pwd)"

echo "Installing ticket-workflow plugin into: $TARGET_ROOT"

mkdir -p "$TARGET_ROOT/.agents/skills"
cp -R "$PLUGIN_ROOT/skills/implement-ticket" "$TARGET_ROOT/.agents/skills/implement-ticket"

mkdir -p "$TARGET_ROOT/.codex/agents"
cp "$PLUGIN_ROOT"/agents/*.toml "$TARGET_ROOT/.codex/agents/"

mkdir -p "$TARGET_ROOT/.codex/hooks"
cp "$PLUGIN_ROOT"/hooks/*.py "$TARGET_ROOT/.codex/hooks/"
chmod +x "$TARGET_ROOT"/.codex/hooks/*.py

python3 "$PLUGIN_ROOT/hooks/merge_config.py" \
  "$TARGET_ROOT/.codex/config.toml" \
  "$PLUGIN_ROOT/config-snippet.toml"

python3 - "$TARGET_ROOT/.codex/hooks.json" "$PLUGIN_ROOT/hooks-snippet.json" <<'PYEOF'
# Both files nest their event lists under a top-level "hooks" key — this
# matches the real shape of Codex's hooks.json (verified against this
# repo's own .codex/hooks.json), not a flat {"PreToolUse": [...]} object.
import json
import sys
from pathlib import Path

target_path = Path(sys.argv[1])
snippet_path = Path(sys.argv[2])

target = {"hooks": {"PreToolUse": [], "PostToolUse": []}}
if target_path.exists():
    target = json.loads(target_path.read_text(encoding="utf-8"))
    target.setdefault("hooks", {})
    target["hooks"].setdefault("PreToolUse", [])
    target["hooks"].setdefault("PostToolUse", [])

snippet = json.loads(snippet_path.read_text(encoding="utf-8"))

for event_name, entries in snippet["hooks"].items():
    for entry in entries:
        for hook in entry["hooks"]:
            already_present = any(
                h.get("command") == hook["command"]
                for existing_entry in target["hooks"].get(event_name, [])
                for h in existing_entry.get("hooks", [])
            )
            if not already_present:
                target["hooks"].setdefault(event_name, []).append(entry)

target_path.parent.mkdir(parents=True, exist_ok=True)
target_path.write_text(json.dumps(target, indent=2) + "\n", encoding="utf-8")
PYEOF

GITIGNORE="$TARGET_ROOT/.gitignore"
touch "$GITIGNORE"
while IFS= read -r line; do
  if ! grep -qxF "$line" "$GITIGNORE"; then
    echo "$line" >> "$GITIGNORE"
  fi
done < "$PLUGIN_ROOT/gitignore-snippet"

echo ""
echo "Installed. Next steps:"
echo "  1. codex                          # trust the project"
echo "  2. inside codex: /hooks           # approve guard_ticket_commit.py and ticket_audit.py"
echo "  3. codex mcp login atlassian      # OAuth login to Jira"
echo "  4. see README.md for the guard sanity check and how to start a ticket"
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_install.py -v`
Expected: PASS (6 tests). (On Windows, run this task's tests inside WSL/Git Bash — `install.sh` requires a POSIX shell, matching this repo's existing `demo-guides/00-setup.md` Windows guidance.)

- [ ] **Step 6: Commit**

```bash
git add plugins/ticket-workflow/install.sh plugins/ticket-workflow/config-snippet.toml \
  plugins/ticket-workflow/hooks-snippet.json plugins/ticket-workflow/gitignore-snippet \
  plugins/ticket-workflow/tests/test_install.py
git commit -m "ticket-workflow: add install.sh and config/hooks/gitignore snippets"
```

---

### Task 7: Subagent role files

**Files:**
- Create: `plugins/ticket-workflow/agents/ticket-investigator.toml`
- Create: `plugins/ticket-workflow/agents/ticket-be-dev.toml`
- Create: `plugins/ticket-workflow/agents/ticket-ui-dev.toml`
- Create: `plugins/ticket-workflow/agents/ticket-tester.toml`
- Test: `plugins/ticket-workflow/tests/test_role_files.py`

**Interfaces:**
- Consumes: nothing (pure prompt/config content); referenced by name (`ticket-investigator`, `ticket-be-dev`, `ticket-ui-dev`, `ticket-tester`) from Task 8's `SKILL.md`.
- Produces: four `.toml` files, each with required keys `name`, `description`, `developer_instructions`, validated structurally by this task's test (not behaviorally — behavior is exercised by the spec's own live dry-run, out of scope for this pytest suite).

- [ ] **Step 1: Write the failing test**

```python
# plugins/ticket-workflow/tests/test_role_files.py
import tomllib
from pathlib import Path

AGENTS_DIR = Path(__file__).resolve().parents[1] / "agents"
EXPECTED = ["ticket-investigator", "ticket-be-dev", "ticket-ui-dev", "ticket-tester"]


def test_all_four_role_files_exist_and_parse():
    for name in EXPECTED:
        path = AGENTS_DIR / f"{name}.toml"
        assert path.exists(), f"missing {path}"
        data = tomllib.loads(path.read_text(encoding="utf-8"))
        assert data["name"] == name
        assert isinstance(data["description"], str) and data["description"]
        assert isinstance(data["developer_instructions"], str) and data["developer_instructions"]


def test_investigator_mentions_spec_file_and_no_jira_access():
    text = (AGENTS_DIR / "ticket-investigator.toml").read_text(encoding="utf-8")
    assert "spec" in text.lower()
    assert "jira" in text.lower()  # instructed that it does NOT call Jira itself


def test_dev_roles_mention_worktree_root_and_never_read_ticket():
    for name in ("ticket-be-dev", "ticket-ui-dev"):
        text = (AGENTS_DIR / f"{name}.toml").read_text(encoding="utf-8")
        assert "worktree_root" in text
        assert "spec" in text.lower()


def test_tester_mentions_ticket_not_spec():
    text = (AGENTS_DIR / "ticket-tester.toml").read_text(encoding="utf-8")
    assert "ticket" in text.lower()
    assert "bugs.md" in text or "bug" in text.lower()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_role_files.py -v`
Expected: FAIL — files don't exist.

- [ ] **Step 3: Write `ticket-investigator.toml`**

```toml
# plugins/ticket-workflow/agents/ticket-investigator.toml
name = "ticket-investigator"
description = "Reads a ticket's requirements (text supplied by the caller, not fetched from Jira itself) and this repo's existing conventions, then produces a concrete BE/UI interface design as tickets/<id>/<id>-spec.md. Read-only against the codebase; the only file it writes is the spec."

developer_instructions = """
You investigate a single ticket's requirements against THIS repository's
existing code and produce an implementation spec. You do not have Jira MCP
access yourself — the caller already read the ticket and will give you its
full text in the prompt; work from that, not from any assumption about
Jira's API.

Given: the ticket's full text, and workflow_root (the repo root).

Do, in order:
1. Read the ticket text carefully. Identify every concrete requirement and
   acceptance criterion — do not paraphrase away specifics.
2. Explore the existing codebase under workflow_root to find the patterns,
   modules, and conventions relevant to this ticket (routing, data model,
   existing similar features). Do not assume a stack; read what's there.
3. Design a concrete BE interface (endpoints/functions/data shapes as
   applicable) and a concrete UI interface (components/screens/state as
   applicable), each with enough detail that an implementer needs no
   further clarification from you. Prefer assigning BE and UI to disjoint
   files/directories wherever the ticket allows, to minimize merge
   conflicts between the two sides that implement from this spec later.
4. Write the full spec, including both interface designs, to
   `<workflow_root>/tickets/<ticket-id>/<ticket-id>-spec.md` (create the
   directory). You are the only writer of this file — do not ask the
   caller to write it for you and do not touch state.json or bugs.md.
5. Return, as your final message: the full BE/UI interface design section
   verbatim (the caller posts this to the Jira ticket) and a short summary
   of what you found in the codebase that shaped the design.

Do not write any application code. Do not run git commands. Do not touch
state.json or bugs.md — those belong to the caller only.
"""
```

- [ ] **Step 4: Write `ticket-be-dev.toml`**

```toml
# plugins/ticket-workflow/agents/ticket-be-dev.toml
name = "ticket-be-dev"
description = "Implements the backend side of a ticket from its spec file only — never reads the ticket itself. Works entirely inside a delegated git worktree and commits there with a [<ticket-id>][dev-be|fix-be] message carrying a completion trailer."

developer_instructions = """
You implement the BACKEND portion of a ticket, reading ONLY the spec file
given to you — you do not have and do not need the original ticket text.

Given, in every delegation: workflow_root (absolute path to the main repo),
worktree_root (absolute path to YOUR isolated git worktree — treat this as
your working directory for every file edit and git command; never edit or
commit anything under workflow_root itself), the ticket ID, the round kind
(initial dev, or a fixing round with a specific bug-ID list and their
descriptions from bugs.md), and the spec file's absolute path under
workflow_root (read it from there; do not copy it into the worktree).

Do, in order:
1. Read the spec's BE interface design (or, for a fixing round, the
   specific bug descriptions you were given — do not attempt to fix bugs
   you were not assigned).
2. Implement inside worktree_root, following the target repo's own existing
   conventions (read nearby code first, match its patterns — do not assume
   a framework).
3. Run whatever the repo's own backend build/test commands are, from
   worktree_root, before committing.
4. Commit inside worktree_root with a message starting with
   `[<ticket-id>][dev-be]` for an initial round or `[<ticket-id>][fix-be]`
   for a fixing round, followed by a short summary. As the LAST line of the
   commit body, include these four trailers, each on its own line, only
   once your work for this task is genuinely finished (never on an
   intermediate/WIP commit):
   Ticket-Workflow: <ticket-id>
   Ticket-Round: <round-id>
   Ticket-Side: be
   Ticket-Complete: true
5. Return, as your final message: the completion commit's full SHA, the
   round ID, "be", and (for a fixing round) exactly which bug IDs you
   addressed.

Never touch state.json or bugs.md yourself — report back instead, the
caller updates those. Never read or reference the Jira ticket.
"""
```

- [ ] **Step 5: Write `ticket-ui-dev.toml`** (same shape as `ticket-be-dev.toml`, UI side)

```toml
# plugins/ticket-workflow/agents/ticket-ui-dev.toml
name = "ticket-ui-dev"
description = "Implements the frontend/UI side of a ticket from its spec file only — never reads the ticket itself. Works entirely inside a delegated git worktree and commits there with a [<ticket-id>][dev-ui|fix-ui] message carrying a completion trailer."

developer_instructions = """
You implement the UI/FRONTEND portion of a ticket, reading ONLY the spec
file given to you — you do not have and do not need the original ticket
text.

Given, in every delegation: workflow_root (absolute path to the main repo),
worktree_root (absolute path to YOUR isolated git worktree — treat this as
your working directory for every file edit and git command; never edit or
commit anything under workflow_root itself), the ticket ID, the round kind
(initial dev, or a fixing round with a specific bug-ID list and their
descriptions from bugs.md), and the spec file's absolute path under
workflow_root (read it from there; do not copy it into the worktree).

Do, in order:
1. Read the spec's UI interface design (or, for a fixing round, the
   specific bug descriptions you were given — do not attempt to fix bugs
   you were not assigned).
2. Implement inside worktree_root, following the target repo's own existing
   conventions (read nearby code first, match its patterns — do not assume
   a framework).
3. Run whatever the repo's own frontend build/test/lint commands are, from
   worktree_root, before committing.
4. Commit inside worktree_root with a message starting with
   `[<ticket-id>][dev-ui]` for an initial round or `[<ticket-id>][fix-ui]`
   for a fixing round, followed by a short summary. As the LAST line of the
   commit body, include these four trailers, each on its own line, only
   once your work for this task is genuinely finished (never on an
   intermediate/WIP commit):
   Ticket-Workflow: <ticket-id>
   Ticket-Round: <round-id>
   Ticket-Side: ui
   Ticket-Complete: true
5. Return, as your final message: the completion commit's full SHA, the
   round ID, "ui", and (for a fixing round) exactly which bug IDs you
   addressed.

Never touch state.json or bugs.md yourself — report back instead, the
caller updates those. Never read or reference the Jira ticket.
"""
```

- [ ] **Step 6: Write `ticket-tester.toml`**

```toml
# plugins/ticket-workflow/agents/ticket-tester.toml
name = "ticket-tester"
description = "Tests the integrated BE+UI implementation against the ORIGINAL ticket's acceptance criteria (not the spec — deliberately, to catch drift between the implementer's interpretation and the real requirement). Returns a structured pass/fail report; never edits bugs.md itself."

developer_instructions = """
You test whether a ticket's acceptance criteria are actually satisfied by
the current code on base_branch. You are given the ORIGINAL TICKET TEXT,
not the spec — test against what was actually asked for, not against how
the implementers chose to build it.

Given: the ticket's full text, workflow_root (absolute path, checked out on
base_branch with both BE and UI worktrees already merged in), the commit
SHA you're testing, and — on a retest round — the list of previously-open
bug IDs and their descriptions to specifically re-check.

Do, in order:
1. Confirm workflow_root's checkout is clean before you start (report
   `blocked` immediately if it is not — do not attempt to test a dirty
   tree).
2. Read the ticket's acceptance criteria carefully.
3. Run whatever the repo's own build/test commands are, plus manual
   verification of the acceptance criteria where automated tests don't
   cover them.
4. Do not modify any tracked file. If you must run something that could
   change tracked files (e.g. a formatter), revert it before finishing.
5. Confirm workflow_root's checkout is still clean and HEAD is unchanged
   when you finish (again: `blocked`, not `passed` or `failed`, if not).

Return, as your final message, a structured report:
- overall result: exactly one of `passed`, `failed`, `blocked` (`blocked`
  means you could not complete testing — a build failure, a missing
  dependency, a dirty/changed checkout — this is different from `failed`,
  which means testing completed and a criterion was not met)
- the commit SHA you tested
- whether the checkout was clean before and after
- a findings list: for each problem found, its area (BE or UI), severity,
  and a clear description
- on a retest round: for each previously-open bug ID you were given, state
  plainly whether you could still reproduce it or not

Never write bugs.md yourself — the caller creates/updates it from this
report.
"""
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_role_files.py -v`
Expected: PASS (4 tests).

- [ ] **Step 8: Commit**

```bash
git add plugins/ticket-workflow/agents/*.toml plugins/ticket-workflow/tests/test_role_files.py
git commit -m "ticket-workflow: add the 4 subagent role files"
```

---

### Task 8: `implement-ticket/SKILL.md`

**Files:**
- Create: `plugins/ticket-workflow/skills/implement-ticket/SKILL.md`
- Modify: `plugins/ticket-workflow/tests/test_role_files.py` (add a skill-frontmatter check)

**Interfaces:**
- Consumes: every CLI/script from Tasks 1-5 by exact path (`state_io.py`, `merge_config.py` is install-time only so not referenced here, `guard_ticket_commit.py`/`ticket_audit.py` run automatically as hooks so not called directly, `verify_step.py`), and the four role names from Task 7.
- Produces: the `implement-ticket` skill, triggered implicitly or via `$implement-ticket`.

- [ ] **Step 1: Write the failing test**

```python
# add to plugins/ticket-workflow/tests/test_role_files.py
import yaml  # noqa: E402  (only used by this test)

SKILL_PATH = Path(__file__).resolve().parents[1] / "skills" / "implement-ticket" / "SKILL.md"


def test_skill_file_has_valid_frontmatter():
    text = SKILL_PATH.read_text(encoding="utf-8")
    assert text.startswith("---\n")
    end = text.index("\n---", 4)
    frontmatter = yaml.safe_load(text[4:end])
    assert frontmatter["name"] == "implement-ticket"
    assert isinstance(frontmatter["description"], str) and frontmatter["description"]


def test_skill_mentions_all_four_subagents_and_verify_step():
    text = SKILL_PATH.read_text(encoding="utf-8")
    for name in ("ticket-investigator", "ticket-be-dev", "ticket-ui-dev", "ticket-tester"):
        assert name in text
    assert "verify_step.py" in text
    assert "state_io.py" in text
```

(If `pyyaml` isn't available in the environment, `pip install pyyaml` before running — note this in the plugin's `tests/` as a dev-only dependency, same tier as `pytest`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_role_files.py -v`
Expected: FAIL — `SKILL.md` doesn't exist.

- [ ] **Step 3: Write `SKILL.md`**

```markdown
---
name: implement-ticket
description: Use when asked to implement, continue, or resume a Jira ticket end-to-end (investigate, spec, implement BE+UI in parallel, test against acceptance criteria, fix-retest loop, report back on the ticket). Trigger phrases include "implement ticket <ID>" and "continue ticket <ID>".
---

# Implement a Jira ticket end-to-end

You orchestrate the full lifecycle for one ticket ID. You never write
application code or touch git yourself for the dev phases — you delegate to
subagents and do only the bookkeeping (state.json, bugs.md, Jira posts,
worktree/merge git commands) described below. Full design rationale:
`docs/specs/2026-09-13-ticket-workflow-plugin-design.md` in the workshop
repo, if present — this file is the operational summary.

`workflow_root` = the repo root you're running in. All relative paths below
are relative to `workflow_root`, never to any subagent's own working
directory.

## 0. Resume check and reconciliation — always first, every invocation

```bash
python3 .codex/hooks/state_io.py read "tickets/<TICKET-ID>/state.json"
```

If it returns `null`: this is a fresh ticket, start at step 1.

If it returns a state object: **do not act on it directly.** Reconcile each
piece against ground truth first:
- For each `jira.*_comment_id` that is `null`: before posting that comment
  type, search the ticket's existing comments (via the Jira MCP tools) for
  the hidden marker `<!-- ticket-workflow:<TICKET-ID>:interface -->` (or
  `:summary` for the final comment). Found → record that comment's real ID
  via `state_io.py write` without posting again. Not found → you'll post it
  when you reach that step.
- For each worktree branch in `state.json.worktrees`: compare its actual
  tip (`git rev-parse <branch>`) against what `dev_round` recorded for that
  side. If it differs, treat the branch tip as ground truth.
- For each side recorded `merged`: verify with
  `git merge-base --is-ancestor <recorded-commit> <base_branch>` — exit 0
  confirms it, anything else means treat it as not yet merged.

Then resume from `state.json.phase`, skipping any subagent dispatch whose
round-scoped status is already `committed`/`merged`/`no_bugs_assigned`, and
never re-posting a Jira comment a marker search already found.

## 1. Entry preconditions

Confirm the workflow_root checkout is on a named branch (not detached HEAD)
and clean (`git status --porcelain` empty). If either fails, stop and
report the prerequisite to resolve — do not proceed. Initialize a new
`state.json` with `ticket_id: <TICKET-ID>`, the current branch name as
`base_branch`, `bug_track_file: "tickets/<TICKET-ID>/bugs.md"`,
`retry_count: 0`, `max_retries: 5`, and `phase: "investigating"`. Every
later `verify_step.py` check reads `ticket_id` and `bug_track_file` from
this file and they are never written again after this step — set them here
or the `dev_round`/`done` gates fail closed on every real ticket.

## 2. Investigating

1. Read the ticket via the Jira MCP tools (discover the available tool
   names live via the MCP tool list — don't assume specific identifiers).
2. Delegate to `ticket-investigator`, passing the full ticket text.
3. On its return: `python3 .codex/hooks/state_io.py write "tickets/<TICKET-ID>/state.json"`
   with `spec_file` set and `phase: "spec_ready"`.
4. Run `python3 .codex/hooks/verify_step.py . <TICKET-ID> spec_ready` — non-zero exit
   means stop and report, do not proceed.
5. Post the investigator's returned interface-design section as a Jira
   comment on the ticket, with the hidden marker
   `<!-- ticket-workflow:<TICKET-ID>:interface -->` embedded in the body.
   Record the returned comment ID as `jira.interface_comment_id`.

## 3. Dev round (initial) — `dev_in_progress`

1. Create the two worktrees (first time only):
   ```bash
   git worktree add .worktrees/<TICKET-ID>-be -b ticket/<TICKET-ID>-be
   git worktree add .worktrees/<TICKET-ID>-ui -b ticket/<TICKET-ID>-ui
   ```
   Record their absolute paths and branch names under `state.json.worktrees`.
2. Write `dev_round` with `kind: "initial"`, `round_id: 0`, both sides
   `status: "pending"`, before dispatching anyone.
3. Dispatch `ticket-be-dev` and `ticket-ui-dev` **in true parallel**, each
   given: `workflow_root` (absolute), its `worktree_root` (absolute), the
   ticket ID, round kind `initial`, and the spec file's absolute path.
4. As each reports back (independently — do not wait for both): record its
   completion SHA under `dev_round.<side>.commit`, set `status: "committed"`,
   then from a clean `workflow_root` checkout on `base_branch`:
   ```bash
   git merge --no-ff <completion-sha>
   ```
   On success, record the resulting merge commit SHA under
   `dev_round.<side>.merge_commit` and set `status: "merged"`. On conflict:
   `git merge --abort`, set `phase: "dev_conflict"`, stop and report to the
   user — never guess a resolution.
5. Run `python3 .codex/hooks/verify_step.py . <TICKET-ID> dev_round` after both
   sides are `merged` — non-zero exit means stop and report.
6. Set `phase: "testing"`.

## 4. Testing

1. Confirm `workflow_root` checkout is clean and on `base_branch`.
2. Delegate to `ticket-tester`, passing the ticket text, the current
   `base_branch` HEAD SHA, and — on a retest round only — the list of
   currently-`open`/`fixed-pending-retest` bug IDs with descriptions.
3. On its return, write `test_report` (`commit`, `result`,
   `acceptance_criteria_checked`, `clean_before`, `clean_after`,
   `tested_at`).
4. From its findings list: create new `bugs.md` rows (`status: "open"`) for
   anything not already tracked; for previously-open IDs it says no longer
   reproduce, set `status: "verified-fixed"`.
5. If `test_report.result == "passed"` and every `bugs.md` row is
   `verified-fixed`/`wont-fix`: go to step 6 (done). Otherwise: set
   `phase: "fixing"`, go to step 5 below.

## 5. Fixing round

Only if `state.json.retry_count < max_retries` (default 5) — otherwise
stop, report the unresolved bugs, and ask the user how to proceed.

1. Confirm no dev subagent from a previous round is still running, and
   `workflow_root` is clean, on `base_branch`, at `test_report.commit`.
2. Confirm both worktrees are clean and each old tip is an ancestor of
   `test_report.commit`. If not, stop for reconciliation — never stash,
   clean, or discard uncommitted work in a worktree.
3. Increment `retry_count`. Write a new `dev_round` (`kind: "fix"`,
   `round_id` incremented, `synced_from_commit: test_report.commit`), each
   side's `pre_sync_commit` = its current tip, `sync_status: "pending"`,
   `bug_ids` = the open bugs assigned to that area (a side with none gets
   `status: "no_bugs_assigned"`, `bug_ids: []`, and is never dispatched).
4. For each side with assigned bugs:
   ```bash
   git -C .worktrees/<TICKET-ID>-be reset --hard <synced_from_commit>
   git -C .worktrees/<TICKET-ID>-ui reset --hard <synced_from_commit>
   ```
   then checkpoint that side's `sync_status: "ready"`.
5. Dispatch only the sides with assigned bugs, in parallel, each given its
   specific bug IDs and descriptions from `bugs.md` (not the full spec).
6. As each reports back: same merge/verify sequence as step 3.4-3.5 above,
   using `[fix-be]`/`[fix-ui]`-tagged commits. Update `bugs.md`: rows for
   addressed bug IDs move to `status: "fixed-pending-retest"` (never
   straight to `verified-fixed`).
7. Set `phase: "testing"`, go back to step 4 (Testing).

If the user ever needs to mark a bug `wont-fix`: stop and ask explicitly —
this is never automatic, and the row records it as user-approved.

## 6. Done

1. Run `python3 .codex/hooks/verify_step.py . <TICKET-ID> done` — non-zero exit
   means stop and report, the gate isn't actually satisfied yet.
2. Write a human-readable summary of what was implemented and how it was
   verified.
3. Search existing Jira comments for the `:summary` marker (see step 0);
   if absent, post the summary with marker
   `<!-- ticket-workflow:<TICKET-ID>:summary -->` and record the returned
   comment ID as `jira.summary_comment_id`.
4. Remove worktrees whose tips are clean and confirmed integrated into
   `base_branch` (never force-remove a dirty or unmerged one):
   ```bash
   git worktree remove .worktrees/<TICKET-ID>-be
   git worktree remove .worktrees/<TICKET-ID>-ui
   ```
5. Set `phase: "summarized"`. Report the summary to the user. Done.
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd plugins/ticket-workflow && python3 -m pytest tests/test_role_files.py -v`
Expected: PASS (6 tests total in this file).

- [ ] **Step 5: Commit**

```bash
git add plugins/ticket-workflow/skills/implement-ticket/SKILL.md plugins/ticket-workflow/tests/test_role_files.py
git commit -m "ticket-workflow: add implement-ticket SKILL.md"
```

---

### Task 9: Plugin `README.md`

**Files:**
- Create: `plugins/ticket-workflow/README.md`

**Interfaces:** None — pure documentation, standalone (must make sense to someone who found this folder without reading the workshop's `demo-guides/`).

- [ ] **Step 1: Write the README**

```markdown
# ticket-workflow — a Codex plugin for end-to-end Jira ticket implementation

Say "implement ticket PROJ-123" and this plugin's Skill orchestrates: read
the ticket from Jira → investigate the codebase and design a BE/UI
interface → post that design back to the ticket → implement both sides in
parallel (isolated git worktrees, so two subagents can commit at once
safely) → test against the ticket's real acceptance criteria → loop fixes
until clean → summarize and comment on the ticket. Resumable if the session
stops at any point.

Full design rationale and the "why" behind every mechanism:
`docs/specs/2026-09-13-ticket-workflow-plugin-design.md` in the repo this
plugin was built alongside (if you have it) — this README is the practical
how-to.

## Prerequisites

- Codex CLI, logged in and trusted for the target repo.
- Python 3.11+ (uses `tomllib`, stdlib since 3.11) and `git`.
- An Atlassian Cloud site with Jira, with the Rovo MCP Server available
  (on by default for most sites; a locked-down org may need an admin to
  enable it).

## Install

From the target repo's root:

```bash
bash /path/to/plugins/ticket-workflow/install.sh
```

This copies the skill, the 4 subagent roles, and the hook scripts into
`.agents/skills/` and `.codex/`, and key-merges (never blindly overwrites)
the MCP/feature config into `.codex/config.toml` and the two new hooks into
`.codex/hooks.json`. Safe to re-run — it's idempotent.

Then:
```bash
codex                        # trust the project (required once)
```
Inside that session, run `/hooks` and approve the two new hooks
(`guard_ticket_commit.py`, `ticket_audit.py`) — project-level hooks need
this explicit approval before they're active.

**Sanity-check the commit guard** before relying on it (a bare terminal
`git commit` never triggers a Codex hook — hooks only fire for tool calls
Codex's own agent loop makes):
```bash
echo DEMO-1 > .codex/tickets_active
echo '{"tool_name":"Bash","tool_input":{"command":"git commit -m \"bad message\""}}' \
  | python3 .codex/hooks/guard_ticket_commit.py
# expect JSON with permissionDecision: "deny"
rm .codex/tickets_active
```

Finally:
```bash
codex mcp login atlassian    # OAuth browser login to Jira
```

## Use it

Inside `codex`, in the target repo:
```
implement ticket PROJ-123
```
or explicitly: `$implement-ticket PROJ-123`.

To resume after an interrupted session, say the same thing again with the
same ticket ID — the skill checks `tickets/PROJ-123/state.json` first and
picks up where it left off, reconciling against Jira/git ground truth
rather than blindly trusting the saved state.

Progress artifacts (all local-only, gitignored by the installer):
- `tickets/<ID>/state.json` — machine-readable progress/resume state
- `tickets/<ID>/<ID>-spec.md` — the BE/UI interface design
- `tickets/<ID>/bugs.md` — the bug tracker for the fix-retest loop
- `tickets/<ID>/audit.log` — every tool call made during this ticket's work

## Known limitations

- `guard_ticket_commit.py` only inspects the literal `tool_input.command`
  string — a message via `-F <file>`, an interactive `--amend`, or a
  `git commit` issued later in a `&&`/`;` chain aren't reliably caught.
  The workflow itself never issues those forms.
- Subagent role `sandbox_mode` fields are silently dropped by Codex (a
  known behavior, not specific to this plugin) — the 4 roles are read/
  write-scoped by instruction, not OS-enforced sandboxing.
- Requires an Atlassian Cloud Jira site; there is no offline/mock mode.

## Running this plugin's own test suite (dev-only)

```bash
cd plugins/ticket-workflow
pip install pytest pyyaml   # dev-only, not needed to just use the plugin
python3 -m pytest tests/ -v
```
```

- [ ] **Step 2: Commit**

```bash
git add plugins/ticket-workflow/README.md
git commit -m "ticket-workflow: add plugin README"
```

---

### Task 10: Demo guide — `06-demo-ticket-workflow.md` + update `01-overview.md`

**Files:**
- Create: `demo-guides/06-demo-ticket-workflow.md`
- Modify: `demo-guides/01-overview.md`

**Interfaces:** None — documentation only, for the workshop repo this plugin was designed alongside (skip this task entirely if `plugins/ticket-workflow/` is being installed into a repo that has no `demo-guides/`).

- [ ] **Step 1: Write `demo-guides/06-demo-ticket-workflow.md`**

```markdown
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
3. Sanity-check the guard (see `plugins/ticket-workflow/README.md`'s
   install section for the exact commands).
4. `codex mcp login atlassian` — OAuth login to your Jira site.
5. Self-check:
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
```

- [ ] **Step 2: Update `demo-guides/01-overview.md`**

Add a row to the existing table (after the row for topic #9) and a short
note, matching that file's existing table format:

```markdown
| bonus | A full Skill+Subagents+MCP+Hooks workflow | `06-demo-ticket-workflow.md` | Yes — needs its own Jira Cloud pre-work, see that file |
```

Add, right after the existing "Have you done `00-setup.md` yet?" section:

```markdown
## Bonus demo needs its own setup

`06-demo-ticket-workflow.md` is not covered by `00-setup.md` — it needs a
real Jira Cloud site, which can't be bundled the way the other demos'
local Postgres is. Read that file's own pre-work section before presenting
it; skip it entirely if a Jira site isn't available.
```

- [ ] **Step 3: Commit**

```bash
git add demo-guides/06-demo-ticket-workflow.md demo-guides/01-overview.md
git commit -m "docs: add demo guide for the ticket-workflow plugin"
```

---

## Self-Review Notes

- **Spec coverage:** Jira connectivity (Task 6 config-snippet + Task 9 README) ✓; state.json schema incl. `dev_round`/`test_report`/`jira` (Task 5, 8) ✓; single-writer rule (Task 8 SKILL.md steps 2-6, never delegates a write to a subagent) ✓; resume reconciliation incl. Jira marker search + git ancestry (Task 8 step 0) ✓; git worktree isolation + resync between rounds (Task 8 steps 3-5) ✓; commit trailers + `verify_step.py` ancestry-based completion check (Tasks 5, 7) ✓; `wont-fix` requires human approval (Task 8 step 5) ✓; `done` gate requiring `test_report.result == "passed"` + clean checkout + matching commit (Task 5 `check_done`) ✓; installer TOML/JSON merge + `/hooks` approval + corrected guard sanity check (Task 6, Task 9) ✓; demo guide + overview update (Task 10) ✓.
- **Placeholder scan:** no TBD/TODO; every code block is complete, runnable code; every test asserts a concrete outcome.
- **Type consistency:** `read_state`/`write_state` (Task 1) used identically in Tasks 4 and 5; `find_active_ticket` (Task 3) reused by Task 4 rather than reimplemented; `check_spec_ready`/`check_dev_round`/`check_done` signatures in Task 5 match their test calls and the CLI dispatch table.
- **Scope:** this plan covers the plugin itself end-to-end; the spec's own "Testing plan" section (a live dry run against a real Jira ticket, including the two flagged Codex-behavior assumptions — subagent cwd control and hook-scoping inside a worktree) is a separate, manual activity after this plan's tasks are done and merged — not additional automated tests in this plan, since it requires a real Jira site and a live Codex session.
