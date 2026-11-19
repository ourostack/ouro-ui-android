import { readFileSync } from "node:fs";

/** Reads a JUnit XML report of the parameterized case test and returns one row per core case. */
export function caseRows(xml) {
  const rows = [];
  for (const m of xml.matchAll(/<testcase\b[^>]*\bname="([^"]*)"[^>]*?(\/>|>([\s\S]*?)<\/testcase>)/g)) {
    const name = /\[(.+)\]$/.exec(m[1])?.[1] ?? m[1];
    const failed = m[3] !== undefined && /<(failure|error)\b/.test(m[3]);
    rows.push({ name, passed: !failed });
  }
  return rows.sort((a, b) => a.name.localeCompare(b.name));
}

export function caseTable(rows) {
  return ["### Core's conformance cases, per case", "", "| Case | Result |", "| --- | --- |",
    ...rows.map((r) => `| ${r.name} | ${r.passed ? "pass" : "FAIL"} |`)].join("\n") + "\n";
}

if (process.argv[1] === new URL(import.meta.url).pathname) {
  const rows = caseRows(process.argv.slice(2).map((f) => readFileSync(f, "utf8")).join("\n"));
  process.stdout.write(caseTable(rows));
  if (rows.some((r) => !r.passed)) process.exitCode = 1;
}
