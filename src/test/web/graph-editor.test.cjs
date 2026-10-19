"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const web = path.resolve(__dirname, "../../main/resources/assets/createthrusters/web/graph-editor");
const catalog = JSON.parse(fs.readFileSync(path.join(web, "nodes.json"), "utf8"));
require(path.join(web, "graph-nodes.js"));
const api = globalThis.GadgetsGraphNodes;
const get = (id) => catalog.nodes.find((node) => node.id === id);

test("the experimental catalog contains every new node family and resolves all defaults and selector variants", () => {
  assert.equal(catalog.sourceBuild, "experimental");
  assert.equal(catalog.graphVersion, 12);
  assert.equal(new Set(catalog.nodes.map((node) => node.id)).size, catalog.nodes.length);
  for (const id of ["pulse_if", "flip_flop", "bounded_loop", "exec_combine", "worker_routine", "worker_move_items",
    "camera", "createthrusters:collection_push", "createthrusters:collection_pop", "createthrusters:collection_contains", "createthrusters:collection_index_of"]) assert.ok(get(id), id);
  for (const def of catalog.nodes) {
    const data = api.defaults(def);
    for (const side of ["inputs", "outputs"]) {
      assert.deepEqual(api.ports(def, data, side), def[side], `${def.id} ${side}`);
      for (const variant of def.variants || []) {
        assert.deepEqual(api.ports(def, { ...data, [variant.selector]: variant.value }, side), variant[side], `${def.id} ${variant.value} ${side}`);
      }
    }
  }
});

test("dynamic selectors, operand types, port ordering, collapsed maps and full precision are retained", () => {
  const flip = get("flip_flop");
  assert.ok(!api.ports(flip, api.defaults(flip), "inputs").skip_pulses);
  assert.equal(api.ports(flip, { ...api.defaults(flip), flip_on: "Every Nth Pulse" }, "inputs").skip_pulses, "number");
  assert.equal(api.ports(get("pulse_if"), { a: 1.25 }, "inputs").b, "number");
  assert.equal(api.ports(get("pulse_if"), { a: false }, "inputs").b, "boolean");
  assert.deepEqual(Object.keys(api.ports(get("math_atan2"), {}, "inputs")), ["Y", "X"]);
  const fields = { field_2: "number", field_1: "string" };
  const data = { DynamicConstructor: true, DynamicInputs: fields, InputOrder: ["field_1", "field_2"], CollapseInputsToMap: true };
  assert.deepEqual(Object.keys(api.ports(get("map_create"), data, "inputs")), ["field_1", "field_2", "input_map"]);
  assert.deepEqual(api.ports(get("map_create"), data, "inputs", true), { input_map: "map" });
  assert.deepEqual(api.decode({Type:"map",Payload:{Value:{Type:"number",Payload:{Value:3}}}}),{Value:3});
  assert.deepEqual(Object.keys(api.ports(get("string_concat"), {TextInputsConfigured:true,DynamicInputs:{b:"string",text_3:"string"}}, "inputs")), ["b","text_3"]);
  assert.equal(api.format(Math.PI, true), String(Math.PI));
  assert.equal(api.format(Math.PI, false), "3.14");
});

test("target mode visibility follows bound selectors and keeps configured values", () => {
  const data = { mode: "servo", power: 42,
    DataTargetBindings: { mode: [{Target:"gear",Port:"mode"}], power: [{Target:"gear",Port:"power"}] },
    TargetInputConditions: { gear: { power: {Selector:"mode",Fallback:"passthrough",Values:["passthrough"]} } } };
  const def = { inputs: { mode:"string", power:"number" } };
  assert.deepEqual(api.ports(def, data, "inputs"), {mode:"string"});
  data.mode="passthrough";
  assert.deepEqual(api.ports(def, data, "inputs"), {mode:"string",power:"number"});
  assert.equal(data.power,42);
});

test("collection operations use insertion order and compare values without mutating sources", () => {
  const list=[1,{z:2,a:3}];
  assert.deepEqual(api.collection("push",list,4).result,[1,{z:2,a:3},4]);
  assert.deepEqual(api.collection("pop",list,null,null,1),{result:[1],value:{z:2,a:3},removed:true});
  assert.equal(api.collection("contains",list,{a:3,z:2}).result,true);
  assert.equal(api.collection("index_of",{z:"first",a:"second"},"second").index,1);
  assert.equal(api.collection("index_of",list,9).index,-1);
  assert.equal(api.collection("pop",list,null,null,-1).removed,false);
  assert.equal(api.collection("pop",list,null,null,-0.2).removed,false);
  assert.equal(api.collection("pop",list,null,null,99).value,null);
  assert.deepEqual(list,[1,{z:2,a:3}]);
});

function adapter() {
  const element = () => ({textContent:"",value:"",className:"",style:{},replaceChildren(){},append(){},addEventListener(){}});
  const elements = new Map();
  const sandbox = { console, GadgetsGraphNodes:api, crypto:require("node:crypto").webcrypto,
    document:{body:{dataset:{}},getElementById(id){if(!elements.has(id))elements.set(id,element());return elements.get(id);}},
    window:{setTimeout(){}},Map,Set };
  let source = fs.readFileSync(path.join(web,"app.js"),"utf8");
  assert.ok(source.includes("  start();"));
  source = source.replace("  start();", "  globalThis.workshop = {state,blankManifest,graphToPlugin,pluginToGraph,normalizeGraph,isStandaloneExecSource};");
  vm.runInNewContext(source,sandbox);
  const app=sandbox.workshop;
  app.state.catalog=catalog.nodes;app.state.definitions=new Map(catalog.nodes.map((node)=>[node.id,node]));
  app.state.manifest=app.blankManifest("Round trip");
  return app;
}

test("every experimental node round trips through the real website adapter with its settings", () => {
  const app=adapter();
  const graph=app.state.manifest.linkerData.StoredGraphs[0].Graph;
  graph.Nodes=catalog.nodes.map((def,idx)=>({Id:`node_${idx}`,Type:def.id,Label:"",X:idx,Y:0,Data:JSON.parse(JSON.stringify(def.defaultData || {}))}));
  const precise=graph.Nodes.find((node)=>node.Type==="constant_number");
  precise.Data.Value=Math.PI;precise.Data.EditorShowPreciseValues=true;
  const flip=graph.Nodes.find((node)=>node.Type==="flip_flop");
  flip.Data.Defaults.flip_on={Type:"string",Payload:{Value:"Every Nth Pulse"}};
  flip.Data.Defaults.skip_pulses={Type:"number",Payload:{Value:7}};
  const pulse=graph.Nodes.find((node)=>node.Type==="pulse_if");
  pulse.Data.Defaults.operator={Type:"string",Payload:{Value:">="}};
  graph.Nodes.push({Id:"future",Type:"external:future_node",X:0,Y:0,Data:{untouched:{value:1.2345678901234567}}});
  const original=JSON.parse(JSON.stringify(graph.Nodes));
  const plugin=app.graphToPlugin(graph);
  app.pluginToGraph(plugin,graph);
  assert.equal(graph.Nodes.length,original.length);
  const restored=new Map(graph.Nodes.map((node)=>[node.Id,node]));
  for(const node of original) {
    assert.equal(restored.get(node.Id)?.Type,node.Type,node.Id);
    for(const [port,entry] of Object.entries(node.Data?.Defaults || {})) {
      assert.deepEqual(api.decode(restored.get(node.Id).Data.Defaults[port]),api.decode(entry),`${node.Type} ${port}`);
    }
  }
  assert.equal(restored.get(precise.Id).Data.Value,Math.PI);
  assert.equal(restored.get(precise.Id).Data.EditorShowPreciseValues,true);
  assert.equal(JSON.stringify(restored.get("future").Data),JSON.stringify(original.at(-1).Data));
  assert.equal(app.isStandaloneExecSource({Type:"exec_combine",Data:get("exec_combine").defaultData}),false);
});
