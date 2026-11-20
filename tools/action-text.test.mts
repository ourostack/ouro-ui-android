import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const dir = new URL("../samples/shop/src/main/kotlin/bot/ouro/shop/actions/", import.meta.url);
const doors = JSON.parse(readFileSync(new URL("doors.json", dir), "utf8")).actions as Record<string, { text: { label: string; description: string; agent: string; confirm?: string } }>;
const kotlin = readFileSync(new URL("OuroActions.kt", dir), "utf8");

test("each generated action carries the label, description and agent text core's doors.json gives it", () => {
  const ids = Object.keys(doors);
  assert.ok(ids.length > 0);
  for (const id of ids) {
    const block = kotlin.slice(kotlin.indexOf(`id = "${id}"`));
    const field = (name: string) => JSON.parse(`"${block.match(new RegExp(`\\n    ${name} = "((?:[^"\\\\]|\\\\.)*)"`))![1]}"`);
    assert.equal(field("label"), doors[id].text.label);
    assert.equal(field("description"), doors[id].text.description);
    assert.equal(field("agent"), doors[id].text.agent);
  }
});

test("the built-in confirmation takes the declared question by action id, and falls back to the description", () => {
  const source = readFileSync(new URL("../ouro-actions/src/main/kotlin/bot/ouro/ui/actions/OuroConfirm.kt", import.meta.url), "utf8");
  assert.match(source, /confirmTexts\[action\.id\] \?: action\.description/);
  assert.match(source, /confirmText\(typed, confirmTexts\)/);
});
