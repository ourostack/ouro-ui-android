import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const workflow = readFileSync(new URL("../.github/workflows/release.yml", import.meta.url), "utf8");

function attachedFiles(text: string): string[] {
  const create = text.match(/gh release create [^\n]*\\\n(?:[^\n]*\\\n)*\s+(.+)\n/);
  assert.ok(create, "release.yml runs gh release create with its files on the last line");
  return create[1].split(/\s+/).map((file) => file.replace(/^"|"$/g, "").replace("$GITHUB_REF_NAME", "${{ github.ref_name }}"));
}

function attestedFiles(text: string): string[] {
  const step = text.match(/uses: actions\/attest-build-provenance@[0-9a-f]{40} # v\S+\n\s+with:\n\s+subject-path: \|\n((?: {12}[^\n]+\n)+)/);
  assert.ok(step, "release.yml attests with actions/attest-build-provenance, pinned to a full commit SHA, listing subject-path one file per line");
  return step[1].trim().split(/\n\s*/);
}

test("every file the release attaches carries a build attestation, made before the release is created", () => {
  assert.deepEqual([...attestedFiles(workflow)].sort(), [...attachedFiles(workflow)].sort());
  assert.ok(workflow.indexOf("attest-build-provenance") < workflow.indexOf("gh release create"));
  assert.match(workflow, /^permissions:\n(?: {2}.*\n)*? {2}id-token: write\n/m);
  assert.match(workflow, /^permissions:\n(?: {2}.*\n)*? {2}attestations: write\n/m);
});

test("a file attached but not attested fails the check", () => {
  const missing = workflow.replace(/ {12}\.\/\*\.pom\n/, "");
  assert.notDeepEqual([...attestedFiles(missing)].sort(), [...attachedFiles(missing)].sort());
});

test("the Pages deploy is its own job after the one that uploads the site, so a failed deploy reruns alone", () => {
  assert.match(workflow, /\n {2}site:\n {4}needs: release\n/);
  assert.match(workflow, /\n {2}deploy:\n {4}needs: site\n/);
  const deploy = workflow.slice(workflow.indexOf("\n  deploy:"));
  assert.match(deploy, /actions\/deploy-pages@/);
  assert.doesNotMatch(workflow.slice(0, workflow.indexOf("\n  deploy:")), /actions\/deploy-pages@/);
  assert.match(workflow.slice(workflow.indexOf("\n  site:"), workflow.indexOf("\n  deploy:")), /actions\/upload-pages-artifact@/);
});

test("creating the release is safe to run again, and the workflow can be run by hand to rebuild the site", () => {
  assert.match(workflow, /gh release view "\$GITHUB_REF_NAME"[^\n]*\n\s+echo [^\n]*already exists/);
  assert.match(workflow, /\n {2}workflow_dispatch:\n/);
  assert.match(workflow, /\n {2}release:\n {4}if: github\.event_name == 'push'\n/);
});
