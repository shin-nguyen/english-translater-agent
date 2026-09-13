#!/usr/bin/env python3
"""
PostToolUse hook — demo 3 (Exec Policy & Hooks).

Appends one line per tool call to .codex/logs/audit.log: timestamp, session
id, tool name, and (for Bash) the command. This is the "logging / auditing"
use case from the outline — a hook that observes rather than blocks, so you
end up with a durable record of what an agent actually did in the repo,
independent of what the transcript UI shows.

Run manually to see a line appended:
    echo '{"session_id":"demo","tool_name":"Bash","tool_input":{"command":"mvn test"}}' \
      | python3 .codex/hooks/audit_log.py
    cat .codex/logs/audit.log
"""
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

LOG_PATH = Path(__file__).resolve().parents[1] / "logs" / "audit.log"


def main() -> int:
    try:
        event = json.load(sys.stdin)
    except json.JSONDecodeError:
        return 0

    session_id = event.get("session_id", "unknown-session")
    tool_name = event.get("tool_name", "unknown-tool")
    tool_input = event.get("tool_input") or {}
    command = tool_input.get("command", "")
    if isinstance(command, list):
        command = " ".join(str(part) for part in command)

    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    line = (
        f"{datetime.now(timezone.utc).isoformat()} "
        f"session={session_id} tool={tool_name} command={command!r}\n"
    )
    with LOG_PATH.open("a", encoding="utf-8") as fh:
        fh.write(line)

    # PostToolUse hooks are feedback/observational here: exit 0, no JSON
    # needed, so the run continues normally with just the log written.
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
