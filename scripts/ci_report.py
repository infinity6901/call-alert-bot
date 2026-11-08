#!/usr/bin/env python3
"""Builds the CI report and the failure report from everything the workflow jobs produced.

usage: ci_report.py <artifacts-root> <out-dir>
env  : GITHUB_SHA, GITHUB_RUN_ID, GITHUB_REPOSITORY, GITHUB_SERVER_URL, GITHUB_REF_NAME,
       JOB_RESULTS (JSON of the workflow's `needs` context)

A check is PASS only if CI actually ran it and it passed. Anything that did not run is
reported as NOT RUN, never silently counted as passed.
"""
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root, out = Path(sys.argv[1]), Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
E = os.environ.get

# ----------------------------------------------------------------------------- load results
steps = {}  # id -> dict(status, rc, desc, log)
for tsv in root.rglob("ci-status/*.tsv"):
    try:
        step_id, status, rc, desc = tsv.read_text().rstrip("\n").split("\t", 3)
    except ValueError:
        continue
    log = tsv.parent.parent / "ci-logs" / f"{step_id}.log"
    steps[step_id] = dict(status=status, rc=rc, desc=desc, log=log if log.exists() else None)

tests = []  # instrumented test cases
for xml_path in root.rglob("TEST-*.xml"):
    try:
        for case in ET.parse(xml_path).getroot().iter("testcase"):
            status, message = "PASS", ""
            for tag, name in (("failure", "FAIL"), ("error", "FAIL"), ("skipped", "SKIPPED")):
                element = case.find(tag)
                if element is not None:
                    status = name
                    message = ((element.get("message") or "") + "\n" + (element.text or "")).strip()
                    break
            tests.append(dict(
                name=f"{case.get('classname', '').rsplit('.', 1)[-1]}.{case.get('name')}",
                status=status, message=message, seconds=float(case.get("time") or 0)))
    except ET.ParseError:
        pass

issues = []
for xml_path in root.rglob("lint-results*.xml"):
    try:
        issues += ET.parse(xml_path).getroot().findall("issue")
    except ET.ParseError:
        pass

job_results = json.loads(E("JOB_RESULTS", "{}") or "{}")

# ----------------------------------------------------------------------------- what is checked
GATE1 = [
    ("compile", "Kotlin compilation"),
    ("manifest", "Manifest merge"),
    ("manifest-content", "Merged manifest declares the call receiver and permissions"),
    ("debug-apk", "Debug APK build"),
    ("apk-smoke", "APK smoke test (package, permissions, entry points, no embedded token)"),
    ("lint", "Android lint"),
]
GATE2 = [
    ("instrumented-tests", "Instrumented tests (main screen, saving the config, receiver guard)"),
    ("apk-install", "Install the CI-built APK"),
    ("config-seed", "Seed a throw-away bot config on the device"),
    ("cold-launch", "Cold launch stays alive, no crash/ANR"),
    ("background-foreground", "Background/foreground cycles"),
    ("rotation", "Rotation cycles"),
    ("lock-unlock", "Screen off/on cycles"),
    ("process-restart", "Force-stop and relaunch"),
    ("incoming-call-alert", "Simulated incoming call triggers an alert"),
    ("missed-call-alert", "Simulated missed call triggers an alert"),
    ("call-simulation", "App survives the simulated calls"),
]


def status_of(step_id):
    step = steps.get(step_id)
    if step is None:
        return "NOT RUN", "this check did not run"
    status = step["status"].replace("_", " ")
    return status, ("" if status == "PASS" else step["desc"])


def gate_result(rows):
    results = [status_of(step_id)[0] for step_id, _ in rows]
    if "FAIL" in results:
        return "FAIL"
    return "PASS" if all(r == "PASS" for r in results) else "NOT RUN"


def gate_detail(rows):
    failed = [label for step_id, label in rows if status_of(step_id)[0] == "FAIL"]
    if failed:
        return "failed: " + "; ".join(failed)
    missing = [label for step_id, label in rows if status_of(step_id)[0] != "PASS"]
    if missing:
        return f"{len(missing)} check(s) did not run"
    return "all checks passed"


def cell(value, limit=200):
    value = " ".join(str(value).split()).replace("|", "\\|")
    return value if len(value) <= limit else value[: limit - 1] + "…"


ICON = {"PASS": "✅", "FAIL": "❌", "NOT RUN": "⬜", "SKIPPED": "⏭️"}
repo, run = E("GITHUB_REPOSITORY", ""), E("GITHUB_RUN_ID", "")
run_url = f"{E('GITHUB_SERVER_URL', 'https://github.com')}/{repo}/actions/runs/{run}" if run else ""

# ----------------------------------------------------------------------------- ci-report.md
L = ["# Call Alert Bot CI report", ""]
L.append(f"Commit `{E('GITHUB_SHA', 'unknown')[:7]}` on `{E('GITHUB_REF_NAME', 'local')}`"
         + (f" - [run {run}]({run_url})" if run_url else ""))
L += ["", "> A check is **PASS** only if CI actually ran it and it passed. Anything that did not run is **NOT RUN**.", ""]

g1, g2 = gate_result(GATE1), gate_result(GATE2)
L += ["## Gates", "", "| Gate | Result | Detail |", "| --- | --- | --- |",
      f"| Gate 1 - build, lint and APK checks | {ICON[g1]} **{g1}** | {cell(gate_detail(GATE1))} |",
      f"| Gate 2 - emulator checks (API 34) | {ICON[g2]} **{g2}** | {cell(gate_detail(GATE2))} |", ""]

for title, rows in (("Gate 1 checks", GATE1), ("Gate 2 checks", GATE2)):
    L += [f"## {title}", "", "| Check | Result | Note |", "| --- | --- | --- |"]
    for step_id, label in rows:
        status, note = status_of(step_id)
        L.append(f"| {label} | {ICON.get(status, '')} {status} | {cell(note)} |")
    L.append("")

counts = {}
for issue in issues:
    counts[issue.get("severity", "")] = counts.get(issue.get("severity", ""), 0) + 1
errors = counts.get("Error", 0) + counts.get("Fatal", 0)
L += ["## Lint", ""]
if not issues:
    L += ["No lint report was found, or lint reported no issues.", ""]
else:
    L += [f"{errors} error(s), {counts.get('Warning', 0)} warning(s).", "",
          "| Severity | Check | Location | Message |", "| --- | --- | --- | --- |"]
    order = {"Fatal": 0, "Error": 1, "Warning": 2}
    for issue in sorted(issues, key=lambda i: order.get(i.get("severity"), 3))[:40]:
        location = issue.find("location")
        where = ""
        if location is not None:
            path = location.get("file", "")
            where = os.path.relpath(path) if path else ""
            if where.startswith(".."):
                where = path
            if location.get("line"):
                where += ":" + location.get("line")
        L.append(f"| {issue.get('severity')} | `{issue.get('id')}` | {cell(where, 80)} | {cell(issue.get('message'))} |")
    if len(issues) > 40:
        L.append(f"\n...and {len(issues) - 40} more. The full HTML report is in the **lint-reports** artifact.")
    L.append("")

L += ["## Instrumented tests", ""]
if not tests:
    L += ["No test results were found (the emulator job did not get as far as running them).", ""]
else:
    failed = sum(t["status"] == "FAIL" for t in tests)
    skipped = sum(t["status"] == "SKIPPED" for t in tests)
    L += [f"{len(tests) - failed - skipped} of {len(tests)} passed"
          + (f", {failed} failed" if failed else "") + (f", {skipped} skipped" if skipped else "") + ".", "",
          "| Test | Result | Time |", "| --- | --- | --- |"]
    for t in tests:
        L.append(f"| `{t['name']}` | {ICON.get(t['status'], '')} {t['status']} | {t['seconds']:.1f}s |")
    L.append("")

model = next(iter(root.rglob("device-model.txt")), None)
version = next(iter(root.rglob("android-version.txt")), None)
if model:
    L += ["## Emulator", "",
          f"{model.read_text().strip()} (emulator), Android {version.read_text().strip() if version else '?'}.", ""]
(out / "ci-report.md").write_text("\n".join(L))


# ----------------------------------------------------------------------------- failure-report.md
def tail(path, n=60):
    try:
        return "\n".join(Path(path).read_text(errors="ignore").splitlines()[-n:])
    except OSError:
        return "(log not available)"


def gradle_errors(path, n=40):
    try:
        lines = Path(path).read_text(errors="ignore").splitlines()
    except OSError:
        return ""
    picked = [i for i, line in enumerate(lines) if re.search(r"^e: |What went wrong|FAILED|error:|Execution failed", line)]
    keep, last = [], -10
    for i in picked:
        if i < last + 6:
            continue  # already shown by the previous excerpt
        keep += lines[i:i + 6] + ["..."]
        last = i
        if len(keep) >= n:
            break
    return "\n".join(keep[:n])


F = ["# Call Alert Bot failure report", ""]
failed_steps = [(k, v) for k, v in steps.items() if v["status"] == "FAIL"]
failed_tests = [t for t in tests if t["status"] == "FAIL"]
bad_jobs = {k: v.get("result") for k, v in job_results.items() if v.get("result") not in ("success", "skipped")}

if not (failed_steps or failed_tests or bad_jobs):
    F += ["**No failures were recorded.** Items marked NOT RUN in ci-report.md are not failures.", ""]
if bad_jobs:
    F += ["## Jobs that did not succeed", ""] + [f"- `{k}`: **{v}**" for k, v in bad_jobs.items()] + [""]
if failed_steps:
    F += ["## Failed CI steps", ""]
    for step_id, step in failed_steps:
        F += [f"### `{step_id}` - {step['desc']} (exit {step['rc']})", ""]
        errors_text = gradle_errors(step["log"]) if step["log"] else ""
        if errors_text:
            F += ["Key errors:", "```", errors_text, "```", ""]
        F += ["Last lines of the log:", "```", tail(step["log"]) if step["log"] else "(log not available)", "```", ""]
if failed_tests:
    F += ["## Failing tests", ""]
    for t in failed_tests:
        F += [f"<details><summary><code>{t['name']}</code></summary>", "", "```",
              "\n".join(t["message"].splitlines()[:25]), "```", "</details>", ""]
bad_lint = [i for i in issues if i.get("severity") in ("Error", "Fatal")]
if bad_lint:
    F += ["## Lint errors", ""]
    for issue in bad_lint[:20]:
        location = issue.find("location")
        where = f"{location.get('file', '')}:{location.get('line', '')}" if location is not None else ""
        F.append(f"- `{issue.get('id')}` {where} - {cell(issue.get('message'))}")
    F.append("")
(out / "failure-report.md").write_text("\n".join(F))

print(f"gate1={g1} gate2={g2} failed steps={len(failed_steps)} failed tests={len(failed_tests)} bad jobs={len(bad_jobs)}")
