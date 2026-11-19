// The card an agent composes for the shop, as an MCP tool result carrying Ouro's tree (structuredContent.ouro): built
// with core's own helpers, so every button is exactly what shopping.catalog.json says its action's button is, and
// checked by core's validator before it is written. The sample reads it from its assets and draws it on its
// "composed" screen. A second result updates that card (core 0.19.0's mcpAppTreeUpdateResult): a new heading, and the
// quantity the agent now suggests. And the same card as A2UI messages (structuredContent.a2ui), which core's A2UI adapter
// must resolve to exactly the same tree and data; the sample draws it on its "a2ui" screen.
//   node samples/shop/agent-card.mts write    write src/main/assets/agent-card.json, agent-card-update.json and agent-card-a2ui.json
//   node samples/shop/agent-card.mts check    fail when any drifted from this file or the catalog
import { readFileSync, writeFileSync } from "node:fs";
import { isDeepStrictEqual } from "node:util";
import { A2UI_VERSION, loadCatalog, mcpAppToolResult, resolveMessages } from "../../vendor/core/ouro-a2ui.mts";
import { actionButtonProps, COMPONENTS, type ComposedNode, type Json, type Obj } from "../../vendor/core/ouro-composed.mts";
import { mcpAppTreeResult, mcpAppTreeUpdateResult } from "../../vendor/core/ouro-mcp-app.mts";

const here = new URL(".", import.meta.url);
const catalogJson = JSON.parse(readFileSync(new URL("src/main/assets/shopping.catalog.json", here), "utf8"));
const catalog = loadCatalog(catalogJson);
const out = new URL("src/main/assets/agent-card.json", here);
const outUpdate = new URL("src/main/assets/agent-card-update.json", here);
const outA2ui = new URL("src/main/assets/agent-card-a2ui.json", here);

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

const data: Obj = { item: { name: "Basil", quantity: 1 }, share: { with: "sam@example.com" } };
const result = mcpAppTreeResult({ text: "Pasta night: Spaghetti, Parmesan and Basil are missing from your list.", catalog, tree, data });
const text = `${JSON.stringify(result, null, 2)}\n`;
const heading = tree.children[0]!;
const updated = mcpAppTreeUpdateResult({
  text: "Pasta night for two: I suggest two of whatever you add.",
  catalog,
  update: { nodes: [{ ...heading, props: { ...heading.props, text: "Pasta night for two" } }], data: [{ path: "/item/quantity", value: 2 }] },
});
const updateText = `${JSON.stringify(updated, null, 2)}\n`;

// The same card as an agent writes it in A2UI: one flat list of components, each naming its children by id, a field's
// value bound to the data model with {"@path"}, and a button naming only its action, its input and its emphasis.
const component = (n: ComposedNode): Obj => {
  if (n.component === "ActionButton") return { id: n.id, component: n.component, action: n.props.action!, input: n.props.input!, emphasis: n.props.emphasis! };
  const props = COMPONENTS[n.component]!.props;
  const c: Obj = { id: n.id, component: n.component };
  for (const [key, value] of Object.entries(n.props)) c[key] = props[key]?.type.kind === "path" ? { "@path": value } : value;
  if (props.children) c.children = n.children.map((x) => x.id);
  return c;
};
const flat = (n: ComposedNode): ComposedNode[] => [n, ...n.children.flatMap(flat)];
const messages: Json[] = [
  { version: A2UI_VERSION, createSurface: { surfaceId: "pasta", catalogId: catalog.catalogId, dataModel: data } },
  { version: A2UI_VERSION, updateComponents: { surfaceId: "pasta", components: flat(tree).map(component) } },
];
const a2ui = mcpAppToolResult({ text: "Pasta night: Spaghetti, Parmesan and Basil are missing from your list.", catalog: catalogJson, messages });
const resolved = resolveMessages(catalogJson, messages);
if (!("surfaces" in resolved) || !isDeepStrictEqual(resolved.surfaces.pasta, tree)) {
  console.error("agent-card: the A2UI card doesn't resolve to the composed card's tree. Fix: change component() so it does.");
  process.exit(1);
}
const a2uiText = `${JSON.stringify(a2ui, null, 2)}\n`;

const cmd = process.argv[2];
if (cmd === "write") {
  writeFileSync(out, text);
  writeFileSync(outUpdate, updateText);
  writeFileSync(outA2ui, a2uiText);
  console.log("agent-card: wrote src/main/assets/agent-card.json, agent-card-update.json and agent-card-a2ui.json, which core's validators accept.");
} else if (cmd === "check") {
  const had = (url: URL) => { try { return readFileSync(url, "utf8"); } catch { return ""; } };
  if (had(out) !== text || had(outUpdate) !== updateText || had(outA2ui) !== a2uiText) {
    console.error("agent-card: src/main/assets/agent-card.json, agent-card-update.json or agent-card-a2ui.json drifted from samples/shop/agent-card.mts or the catalog. Fix: node samples/shop/agent-card.mts write");
    process.exit(1);
  }
  console.log("agent-card: the sample's card, its update and its A2UI form match their source, and core's validators accept them.");
} else {
  console.error("usage: node samples/shop/agent-card.mts write | check");
  process.exit(2);
}
