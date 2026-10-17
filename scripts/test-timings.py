#!/usr/bin/env python3
"""Summarize existing Maven JUnit reports without running any tests."""

import argparse
from pathlib import Path
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", type=Path, default=Path("target"))
    parser.add_argument("--limit", type=int, default=10, help="Number of slow classes to show")
    args = parser.parse_args()
    if args.limit < 1:
        parser.error("--limit must be positive")

    suites = []
    for group, directory in (("unit", "surefire-reports"), ("integration", "failsafe-reports")):
        for report in sorted((args.target / directory).glob("TEST-*.xml")):
            root = ET.parse(report).getroot()
            for suite in ([root] if root.tag == "testsuite" else root.findall("testsuite")):
                suites.append({"group": group, "name": suite.get("name", report.stem),
                               "time": float(suite.get("time", 0)),
                               **{key: int(suite.get(key, 0)) for key in ("tests", "failures", "errors", "skipped")}})

    if not suites:
        parser.exit(1, "No JUnit reports found. Run mvn test or mvn verify first.\n")
    print("Group        Classes  Tests  Failures  Errors  Skipped  Suite seconds")
    for group in ("unit", "integration"):
        rows = [suite for suite in suites if suite["group"] == group]
        if rows:
            counts = [sum(row[key] for row in rows) for key in ("tests", "failures", "errors", "skipped", "time")]
            print(f"{group:<12} {len(rows):>7} {counts[0]:>6} {counts[1]:>9} {counts[2]:>7} {counts[3]:>8} {counts[4]:>14.3f}")
    print("\nSlowest classes (includes class setup and Spring context startup):")
    for suite in sorted(suites, key=lambda row: row["time"], reverse=True)[:args.limit]:
        print(f"{suite['time']:>8.3f}s  {suite['tests']:>4} tests  {suite['name']}")
    print("\nSuite seconds exclude Maven compilation, packaging and report generation.")


if __name__ == "__main__":
    main()
