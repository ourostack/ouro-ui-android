// What comes from ouro-ui-core is vendored from a release, by SHA-256, and never edited (AGENTS.md).
// core-provenance.json names the core tag, the commit it points at and, for each file here, the path it came from and
// its hash. A path under dist/ comes from the release's dist zip; any other path is a file in core's source tree at the
// tag's commit (the composed tree's conformance cases and the app catalog they use, which the dist doesn't carry).
//
//   node scripts/provenance.mjs check            every vendored file matches its recorded hash; every dist hash matches
//                                                the release's dist-manifest.json, every source file the tag's commit,
//                                                and the tag still points at the recorded commit (fetched from GitHub)
//   node scripts/provenance.mjs update vX.Y.Z    take every file from that core release and record it
//
// update downloads the release's dist zip and lists source folders with `gh`; check needs only the network and git.
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, writeFileSync, mkdirSync, copyFileSync, readdirSync, rmSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";

const root = new URL("..", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");
const file = join(root, "core-provenance.json");
const sha = (bytes) => createHash("sha256").update(bytes).digest("hex");
const [cmd, tag] = process.argv.slice(2);
const CORE = "ourostack/ouro-ui-core";

/** Where each file here comes from in a core release: dist/ paths from its dist zip, others from its source tree. A key
 * ending in / is a folder: every file in it at the tag. */
const MAP = {
  "vendor/core/ouro-actions.mts": "dist/actions/ouro-actions.mts",
  "vendor/core/ouro-actions-android.mts": "dist/actions/ouro-actions-android.mts",
  "vendor/core/components.claims.json": "dist/hx/components.claims.json",
  "vendor/core/components/component.schema.json": "dist/hx/components/component.schema.json",
  "vendor/core/components/button.spec.json": "dist/hx/components/button.spec.json",
  "vendor/core/components/badge.spec.json": "dist/hx/components/badge.spec.json",
  "vendor/core/components/card.spec.json": "dist/hx/components/card.spec.json",
  "vendor/core/components/item.spec.json": "dist/hx/components/item.spec.json",
  "vendor/core/components/checkbox.spec.json": "dist/hx/components/checkbox.spec.json",
  "vendor/core/components/text-field.spec.json": "dist/hx/components/text-field.spec.json",
  "vendor/core/components/separator.spec.json": "dist/hx/components/separator.spec.json",
  "vendor/core/components/description-list.spec.json": "dist/hx/components/description-list.spec.json",
  "vendor/core/composed/catalog.json": "dist/hx/a2ui/catalog.json",
  "vendor/core/composed/recipe.catalog.json": "standard/hx/a2ui/conformance/recipe.catalog.json",
  "vendor/core/composed/cases/": "standard/hx/a2ui/conformance/cases/",
  "ouro-hx/src/main/kotlin/bot/ouro/ui/hx/tokens/OuroTokens.kt": "dist/android/OuroTokens.kt",
};

const fromDist = (from) => from.startsWith("dist/");
const raw = (commit, path) => `https://raw.githubusercontent.com/${CORE}/${commit}/${path}`;

/** The commit a core tag points at, peeled, from git itself (no API rate limit). */
function tagCommit(t) {
  const out = execFileSync("git", ["ls-remote", `https://github.com/${CORE}`, `refs/tags/${t}`, `refs/tags/${t}^{}`], { encoding: "utf8" });
  const lines = out.trim().split("\n").filter(Boolean).map((l) => l.split("\t"));
  return (lines.find(([, ref]) => ref.endsWith("^{}")) ?? lines[0])?.[0];
}

async function fetchBytes(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${url}: ${response.status}`);
  return Buffer.from(await response.arrayBuffer());
}

if (cmd === "update" && /^v\d+\.\d+\.\d+$/.test(tag ?? "")) {
  const commit = tagCommit(tag);
  if (!commit) throw new Error(`no tag ${tag} in ${CORE}`);
  const dir = mkdtempSync(join(tmpdir(), "ouro-core-"));
  execFileSync("gh", ["release", "download", tag, "-R", CORE, "-p", `ouro-ui-core-${tag}-dist.zip`, "-D", dir], { stdio: "inherit" });
  execFileSync("unzip", ["-q", join(dir, `ouro-ui-core-${tag}-dist.zip`), "-d", join(dir, "dist")], { stdio: "inherit" });
  const files = {};
  const take = async (here, from) => {
    mkdirSync(dirname(join(root, here)), { recursive: true });
    if (fromDist(from)) copyFileSync(join(dir, from), join(root, here));
    else writeFileSync(join(root, here), await fetchBytes(raw(commit, from)));
    files[here] = { from, sha256: sha(readFileSync(join(root, here))) };
  };
  for (const [here, from] of Object.entries(MAP)) {
    if (!here.endsWith("/")) { await take(here, from); continue; }
    // A folder: what the tag holds now, and nothing it no longer holds.
    if (existsSync(join(root, here))) for (const f of readdirSync(join(root, here))) rmSync(join(root, here, f));
    const listed = JSON.parse(execFileSync("gh", ["api", `repos/${CORE}/contents/${from.replace(/\/$/, "")}?ref=${commit}`], { encoding: "utf8" }));
    for (const entry of listed.filter((e) => e.type === "file").sort((a, b) => a.name.localeCompare(b.name))) await take(here + entry.name, from + entry.name);
  }
  rmSync(dir, { recursive: true, force: true });
  writeFileSync(file, `${JSON.stringify({ coreTag: tag, coreCommit: commit, files }, null, 2)}\n`);
  console.log(`provenance: vendored ${Object.keys(files).length} files from ouro-ui-core ${tag} (${commit}). Run the generators (README) and commit.`);
} else if (cmd === "check") {
  const recorded = JSON.parse(readFileSync(file, "utf8"));
  const fix = `Fix: node scripts/provenance.mjs update ${recorded.coreTag}`;
  const problems = [];
  for (const [here, from] of Object.entries(MAP)) {
    if (!here.endsWith("/")) {
      if (!recorded.files[here]) problems.push(`${here}: not recorded. ${fix}`);
      continue;
    }
    const inFolder = Object.keys(recorded.files).filter((k) => k.startsWith(here));
    if (!inFolder.length) problems.push(`${here}: nothing recorded from ${from}. ${fix}`);
    const present = existsSync(join(root, here)) ? readdirSync(join(root, here)).map((f) => here + f) : [];
    for (const f of present) if (!recorded.files[f]) problems.push(`${f}: not from ${recorded.coreTag}. Vendored folders hold only core's files. ${fix}`);
  }
  for (const [here, { from, sha256 }] of Object.entries(recorded.files)) {
    let bytes;
    try { bytes = readFileSync(join(root, here)); } catch { problems.push(`${here}: missing. ${fix}`); continue; }
    if (sha(bytes) !== sha256) problems.push(`${here}: differs from ${recorded.coreTag}'s ${from}. Vendored files are never edited. Fix: change core, release it, then update.`);
  }
  const url = `https://github.com/${CORE}/releases/download/${recorded.coreTag}/dist-manifest.json`;
  const response = await fetch(url);
  if (!response.ok) problems.push(`${url}: ${response.status}. Fix: is ${recorded.coreTag} released?`);
  else {
    const manifest = await response.json();
    for (const [here, { from, sha256 }] of Object.entries(recorded.files)) if (fromDist(from) && manifest.files[from] !== sha256) problems.push(`${here}: recorded hash isn't ${recorded.coreTag}'s ${from}. ${fix}`);
  }
  const source = Object.entries(recorded.files).filter(([, { from }]) => !fromDist(from));
  if (source.length) {
    const commit = tagCommit(recorded.coreTag);
    if (commit !== recorded.coreCommit) problems.push(`${recorded.coreTag} points at ${commit}, not the recorded ${recorded.coreCommit}. ${fix}`);
    else for (const [here, { from, sha256 }] of source) {
      try {
        if (sha(await fetchBytes(raw(commit, from))) !== sha256) problems.push(`${here}: recorded hash isn't ${recorded.coreTag}'s ${from}. ${fix}`);
      } catch (e) { problems.push(`${here}: ${e.message}`); }
    }
  }
  for (const p of problems) console.error(`provenance: ${p}`);
  if (problems.length) process.exit(1);
  console.log(`provenance: ${Object.keys(recorded.files).length} files match ouro-ui-core ${recorded.coreTag}.`);
} else {
  console.error("usage: node scripts/provenance.mjs check | update vX.Y.Z");
  process.exit(2);
}
