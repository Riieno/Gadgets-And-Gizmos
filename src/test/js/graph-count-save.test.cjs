const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const test = require("node:test");

const root = path.resolve(__dirname, "../../main/resources/assets/createthrusters/web/graph-editor");
const definitions = JSON.parse(fs.readFileSync(path.join(root, "nodes.json"), "utf8")).nodes;
const app = fs.readFileSync(path.join(root, "app.js"), "utf8").replace(/\r\n/g, "\n");
const helper = app.match(/  function graphNodeData\(node, def\) \{[\s\S]*?\n  \}\n/);
assert.ok(helper, "The web editor must migrate Count defaults before constructing studio nodes");
assert.ok(app.includes("const data = graphNodeData(node, def);"));
const context = vm.createContext({ hasOwn: (obj, key) => Object.prototype.hasOwnProperty.call(obj, key) });
vm.runInContext(fs.readFileSync(path.join(root, "graph-nodes.js"), "utf8"), context);
vm.runInContext(helper[0], context);
const definition = (id) => definitions.find((node) => node.id === id);
const bool = (value) => ({ Type: "boolean", Payload: { Value: value ? 1 : 0 } });
const number = (value) => ({ Type: "number", Payload: { Value: value } });
const load = (data) => context.graphNodeData({ Type: "count", Data: data }, definition("count"));

test("Count exposes boolean inputs instead of the old Direction property", () => {
  const def = definition("count");
  assert.equal(def.inputs.decrement, "boolean");
  assert.equal(def.inputs.remember_count, "boolean");
  assert.equal(Object.hasOwn(def.properties, "Direction"), false);
  const data = load({});
  assert.equal(context.GadgetsGraphNodes.value(data, "decrement"), false);
  assert.equal(context.GadgetsGraphNodes.value(data, "remember_count"), false);
});

test("old selector directions survive raw graph import without changing the stored document", () => {
  for (const direction of ["increment", "decrement", "DECREMENT"]) {
    const saved = { Direction: direction, Defaults: { step: number(2.5), reset: bool(false) } };
    const before = JSON.stringify(saved);
    const data = load(saved);
    assert.equal(context.GadgetsGraphNodes.value(data, "decrement"), direction.toLowerCase() === "decrement");
    assert.equal(context.GadgetsGraphNodes.value(data, "step"), 2.5);
    assert.equal(context.GadgetsGraphNodes.value(data, "reset"), false);
    assert.equal(context.GadgetsGraphNodes.value(data, "remember_count"), false);
    assert.equal(JSON.stringify(saved), before);
  }
});

test("explicit new boolean values take priority over an old selector", () => {
  for (const decrement of [false, true]) {
    const data = load({ Direction: decrement ? "increment" : "decrement",
      Defaults: { decrement: bool(decrement), remember_count: bool(true) } });
    assert.equal(context.GadgetsGraphNodes.value(data, "decrement"), decrement);
    assert.equal(context.GadgetsGraphNodes.value(data, "remember_count"), true);
    assert.equal(context.GadgetsGraphNodes.value(load({ Direction: "decrement", decrement }), "decrement"), decrement);
  }
});

test("Percentage keeps numeric A and B values when loaded into the studio", () => {
  const def = definition("createthrusters:percentage");
  assert.equal(def.inputs.a, "number");assert.equal(def.inputs.b, "number");
  assert.equal(def.outputs.value, "number");assert.equal(def.outputs.valid, "boolean");
  const data = context.graphNodeData({ Type: def.id, Data: { Defaults: { a: number(50), b: number(200) } } }, def);
  assert.equal(context.GadgetsGraphNodes.value(data, "a"), 50);
  assert.equal(context.GadgetsGraphNodes.value(data, "b"), 200);
});
