import test from "node:test";
import assert from "node:assert/strict";
import { caseRows, caseTable } from "./case-results.mjs";

const xml = `<testsuite><testcase name="case_resolves[b.json]" classname="X" time="0.1"/>
<testcase name="case_resolves[a.json]" classname="X"><failure message="m">t</failure></testcase></testsuite>`;

test("a report becomes one sorted row per case, failed ones marked", () => {
  assert.deepEqual(caseRows(xml), [{ name: "a.json", passed: false }, { name: "b.json", passed: true }]);
  assert.match(caseTable(caseRows(xml)), /\| a\.json \| FAIL \|\n\| b\.json \| pass \|/);
});
