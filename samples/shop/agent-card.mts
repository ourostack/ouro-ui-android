// The card an agent composes for the shop, as an MCP tool result carrying Ouro's tree (structuredContent.ouro): built
// with core's own helpers, so every button is exactly what shopping.catalog.json says its action's button is, and
// checked by core's validator before it is written. The sample reads it from its assets and draws it on its
// "composed" screen. A second result updates that card (core 0.19.0's mcpAppTreeUpdateResult): a new heading, and the
// quantity the agent now suggests.
//   node samples/shop/agent-card.mts write    write src/main/assets/agent-card.json and agent-card-update.json
//   node samples/shop/agent-card.mts check    fail when either drifted from this file or the catalog
import { readFileSync, writeFileSync } from "node:fs";
import { loadCatalog } from "../../vendor/core/ouro-a2ui.mts";
import { actionButtonProps, type ComposedNode, type Obj } from "../../vendor/core/ouro-composed.mts";
import { mcpAppTreeResult, mcpAppTreeUpdateResult } from "../../vendor/core/ouro-mcp-app.mts";

const here = new URL(".", import.meta.url);
const catalog = loadCatalog(JSON.parse(readFileSync(new URL("src/main/assets/shopping.catalog.json", here), "utf8")));
const out = new URL("src/main/assets/agent-card.json", here);
const outUpdate = new URL("src/main/assets/agent-card-update.json", here);

let next = 0;
const node = (component: string, props: Obj, children: ComposedNode[] = []): ComposedNode => ({ id: `${component.toLowerCase()}_${next++}`, component, props, children });
const button = (action: string, input: Obj, emphasis: "primary" | "secondary" = "secondary") => node("ActionButton", { emphasis, ...actionButtonProps(catalog, action, input) });

const tree: ComposedNode = { ...node("Card", {}, [
  node("Heading", { text: "Pasta night", level: 1 }),
  node("Stack", { direction: "horizontal", gap: "small" }, [node("Badge", { text: "Vegetarian", tone: "success" }), node("Badge", { text: "35 min", tone: "neutral" })]),
  node("Text", { text: "What Saturday's recipe needs that your list doesn't have yet.", tone: "muted" }),
  node("DescriptionList", { items: [{ term: "Serves", detail: "4" }, { term: "Missing", detail: "3 things" }] }),
  node("List", { items: ["Spaghetti", "Parmesan", "Basil"], ordered: false }),
  node("Alert", { title: "Basil sells out on Saturdays.", text: "Add it now so it's on Friday's order.", tone: "info" }),
  node("Separator", {}),
  node("TextField", { label: "Item", value: "/item/name", hint: "What to add, as you'd say it.", type: "text", multiline: false }),
  node("TextField", { label: "Quantity", value: "/item/quantity", type: "number", multiline: false }),
  node("Select", { label: "Share with", value: "/share/with", options: ["sam@example.com", "kim@example.com"] }),
  node("Stack", { direction: "horizontal", gap: "small" }, [
    button("add_shopping_list_item", { name: { "@path": "/item/name" }, quantity: { "@path": "/item/quantity" } }, "primary"),
    button("share_shopping_list", { with: { "@path": "/share/with" } }),
    button("place_grocery_order", {}),
  ]),
]), id: "root" };

const result = mcpAppTreeResult({ text: "Pasta night: Spaghetti, Parmesan and Basil are missing from your list.", catalog, tree, data: { item: { name: "Basil", quantity: 1 }, share: { with: "sam@example.com" } } });
const text = `${JSON.stringify(result, null, 2)}\n`;
const heading = tree.children[0]!;
const updated = mcpAppTreeUpdateResult({
  text: "Pasta night for two: I suggest two of whatever you add.",
  catalog,
  update: { nodes: [{ ...heading, props: { ...heading.props, text: "Pasta night for two" } }], data: [{ path: "/item/quantity", value: 2 }] },
});
const updateText = `${JSON.stringify(updated, null, 2)}\n`;

const cmd = process.argv[2];
if (cmd === "write") {
  writeFileSync(out, text);
  writeFileSync(outUpdate, updateText);
  console.log("agent-card: wrote src/main/assets/agent-card.json and agent-card-update.json, which core's validators accept.");
} else if (cmd === "check") {
  const had = (url: URL) => { try { return readFileSync(url, "utf8"); } catch { return ""; } };
  if (had(out) !== text || had(outUpdate) !== updateText) {
    console.error("agent-card: src/main/assets/agent-card.json or agent-card-update.json drifted from samples/shop/agent-card.mts or the catalog. Fix: node samples/shop/agent-card.mts write");
    process.exit(1);
  }
  console.log("agent-card: the sample's card and its update match their source, and core's validators accept them.");
} else {
  console.error("usage: node samples/shop/agent-card.mts write | check");
  process.exit(2);
}
