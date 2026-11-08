#!/usr/bin/env python3
"""Turns CI output into readable Markdown for the GitHub job summary.

usage:
  ci_summary.py gradle-failure <title> <gradle.log>
  ci_summary.py lint <lint-results.xml>
  ci_summary.py junit <results-dir>

Writes to $GITHUB_STEP_SUMMARY when it is set, otherwise to stdout.
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET


def emit(text=""):
    target = os.environ.get("GITHUB_STEP_SUMMARY")
    if target:
        with open(target, "a", encoding="utf-8") as handle:
            handle.write(text + "\n")
    else:
        print(text)


def cell(value, limit=200):
    value = " ".join(str(value).split()).replace("|", "\\|")
    return value if len(value) <= limit else value[: limit - 1] + "…"


def gradle_failure(title, log_path):
    try:
        with open(log_path, encoding="utf-8", errors="replace") as handle:
            log = handle.read()
    except OSError:
        emit(f"### ❌ {title} failed\n")
        emit("No Gradle log was captured. Check the log of the failed step for details.")
        return

    sections = []
    where = re.search(r"\* Where:\n(.*?)\n\n", log, re.S)
    if where:
        sections.append(("Where", where.group(1).strip()))
    wrong = re.search(r"\* What went wrong:\n(.*?)(?:\n\* Try:|\Z)", log, re.S)
    if wrong:
        sections.append(("What went wrong", wrong.group(1).strip()))
    errors = [line for line in log.splitlines() if re.match(r"^(e: |error: |.*: error: )", line)]
    if errors:
        sections.append(("Compiler errors", "\n".join(errors[:25])))
    if not sections:
        sections.append(("Last lines of the log", "\n".join(log.splitlines()[-40:])))

    emit(f"### ❌ {title} failed\n")
    for heading, body in sections:
        emit(f"**{heading}**\n")
        emit("```\n" + body[:4000] + "\n```\n")


def lint(xml_path):
    if not os.path.exists(xml_path):
        emit("### ⚠️ Lint\n")
        emit("No lint report was produced because the build stopped before lint finished.")
        return

    issues = ET.parse(xml_path).getroot().findall("issue")
    counts = {}
    for issue in issues:
        severity = issue.get("severity", "")
        counts[severity] = counts.get(severity, 0) + 1
    errors = counts.get("Error", 0) + counts.get("Fatal", 0)
    warnings = counts.get("Warning", 0)

    icon = "❌" if errors else "✅"
    emit(f"### {icon} Lint: {errors} error(s), {warnings} warning(s)\n")
    if not issues:
        emit("No issues found.")
        return

    order = {"Fatal": 0, "Error": 1, "Warning": 2}
    issues.sort(key=lambda i: order.get(i.get("severity"), 3))
    emit("| Severity | Check | Location | Message |")
    emit("| --- | --- | --- | --- |")
    for issue in issues[:40]:
        location = issue.find("location")
        where = ""
        if location is not None:
            path = location.get("file", "")
            where = os.path.relpath(path) if path else ""
            if where.startswith(".."):
                where = path
            if location.get("line"):
                where += ":" + location.get("line")
        emit(f"| {issue.get('severity')} | `{issue.get('id')}` | {cell(where, 80)} | {cell(issue.get('message'))} |")
    if len(issues) > 40:
        emit(f"\n...and {len(issues) - 40} more. Download the **lint-report** artifact for the full HTML report.")


def junit(results_dir):
    files = glob.glob(os.path.join(results_dir, "**", "TEST-*.xml"), recursive=True)
    if not files:
        emit("### ⚠️ Emulator tests\n")
        emit("No test results were produced because the build or the emulator failed before the tests ran.")
        return

    rows, failures = [], []
    failed = skipped = 0
    for path in sorted(files):
        for case in ET.parse(path).getroot().iter("testcase"):
            name = f"{case.get('classname', '').rsplit('.', 1)[-1]}.{case.get('name')}"
            seconds = float(case.get("time") or 0)
            bad = case.find("failure")
            if bad is None:
                bad = case.find("error")
            if bad is not None:
                failed += 1
                status = "❌ failed"
                failures.append((name, (bad.get("message") or "") + "\n" + (bad.text or "")))
            elif case.find("skipped") is not None:
                skipped += 1
                status = "⏭️ skipped"
            else:
                status = "✅ passed"
            rows.append((name, status, seconds))

    total = len(rows)
    passed = total - failed - skipped
    icon = "❌" if failed else "✅"
    extra = (f", {failed} failed" if failed else "") + (f", {skipped} skipped" if skipped else "")
    emit(f"### {icon} Emulator tests: {passed} of {total} passed{extra}\n")
    emit("| Test | Result | Time |")
    emit("| --- | --- | --- |")
    for name, status, seconds in rows:
        emit(f"| `{name}` | {status} | {seconds:.1f}s |")
    for name, detail in failures:
        emit(f"\n<details><summary>{name}</summary>\n\n```\n{detail.strip()[:3000]}\n```\n</details>")


def main(argv):
    if len(argv) == 3 and argv[0] == "gradle-failure":
        gradle_failure(argv[1], argv[2])
    elif len(argv) == 2 and argv[0] == "lint":
        lint(argv[1])
    elif len(argv) == 2 and argv[0] == "junit":
        junit(argv[1])
    else:
        print(__doc__, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
