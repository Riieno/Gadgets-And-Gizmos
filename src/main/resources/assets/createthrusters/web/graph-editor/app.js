(() => {
  "use strict";

  /*--------------------------------------------------------##---------------------------------------------------------

  =======================================================================================================================
                                                           Constants
  =======================================================================================================================

  ------------------------------------------------------------##-----------------------------------------------------*/

  // Get an editor element
  const byId = (id) => document.getElementById(id);
  const isPublicSite = document.body.dataset.publicSite === "true";
  const initialGraphId = String(document.body.dataset.graphId || "").trim().toLowerCase();
  const endpoints = isPublicSite ? {
    status: "/api/status.php",
    nodes: "/nodes.json",
    manifest: (id) => `/api/manifest.php?id=${encodeURIComponent(id)}`,
    share: "/api/upload.php"
  } : {
    status: "/api/status",
    nodes: "/api/nodes",
    manifest: (id) => `/api/manifests/${encodeURIComponent(id)}`,
    share: "/api/share"
  };
  // Copy saved data without shared references
  const deepCopy = (value) => value == null ? value : JSON.parse(JSON.stringify(value));
  // Check if an object owns a key
  const hasOwn = (value, key) => Object.prototype.hasOwnProperty.call(value || {}, key);
  const STUDIO_POSITION_SCALE = 2;
  const STUDIO_FLOW_INPUT = "__FLOW_IN__";
  const STUDIO_VARIABLE_VALUE = "Current Value";
  const STUDIO_VARIABLE_SET_VALUE = "Set Value";
  const STUDIO_VARIABLE_FLOW_OUTPUT = "Out";
  // Convert graph positions for the studio
  const toStudioPos = (value) => Number(value || 0) * STUDIO_POSITION_SCALE;
  // Convert studio positions for the graph
  const toGraphPos = (value) => Number(value || 0) / STUDIO_POSITION_SCALE;
  // Wait for studio updates
  const wait = (ms) => new Promise((resolve) => window.setTimeout(resolve, ms));
  // Create a unique graph id
  const uniqueId = (prefix) => {
    const random = globalThis.crypto?.randomUUID?.()
      || `${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}`;
    return `${prefix}_${random.replaceAll("-", "_")}`;
  };

  /*--------------------------------------------------------##---------------------------------------------------------

  =======================================================================================================================
                                                         DEFAULTS
                                                    #################
                                                         Variables
  =======================================================================================================================

  ------------------------------------------------------------##-----------------------------------------------------*/

  const el = {
    addFunction: byId("addFunctionButton"),
    download: byId("downloadButton"),
    editor: byId("graphEditor"),
    fileInput: byId("fileInput"),
    filter: byId("manifestFilter"),
    functionTabs: byId("functionTabs"),
    graphName: byId("graphName"),
    graphSelect: byId("graphSelect"),
    graphStats: byId("graphStats"),
    manifestList: byId("manifestList"),
    manifestName: byId("manifestName"),
    manifestOrigin: byId("manifestOrigin"),
    newManifest: byId("newButton"),
    open: byId("openButton"),
    refresh: byId("refreshButton"),
    saveServer: byId("saveServerButton"),
    serverLamp: byId("serverLamp"),
    serverState: byId("serverState"),
    share: byId("shareButton"),
    installGame: byId("installGameButton"),
    status: byId("statusText"),
    upload: byId("uploadButton")
  };

  const state = {
    activeGraphKey: "main",
    catalog: [],
    definitions: new Map(),
    isDirty: false,
    fileIntent: "open",
    latestPlugin: null,
    isLoadingPlugin: false,
    manifest: null,
    manifests: [],
    isGameGraphAvailable: false,
    currentGameFingerprint: null,
    isGameGraph: false,
    currentPoll: 0,
    publicGraphId: initialGraphId,
    serverId: null,
    storedGraphId: null,
    studio: null,
    studioSerial: 0,
    isSwitching: false
  };

  /*--------------------------------------------------------##---------------------------------------------------------

  =======================================================================================================================
                                                          Functions
  =======================================================================================================================

  ------------------------------------------------------------##-----------------------------------------------------*/

  // Update the editor status
  function setStatus(message, tone = "") {
    el.status.textContent = message;
    el.status.className = tone;
  }

  // Update the server status
  function setServerState(isOnline, label) {
    el.serverLamp.className = `signal-lamp ${isOnline ? "online" : "offline"}`;
    el.serverState.textContent = label;
  }

  // Handle JSON requests
  async function requestJSON(url, opts) {
    const res = await fetch(url, opts);
    const text = await res.text();
    let body = {};
    if (text) {
      try {
        body = JSON.parse(text);
      } catch {
        throw new Error(`The server returned invalid JSON (${res.status})`);
      }
    }
    if (!res.ok) {
      throw new Error(body.error || body.status || `Request failed (${res.status})`);
    }
    return body;
  }

  /*--------------------------------------------------------##---------------------------------------------------------

  =======================================================================================================================
                                                 GRAPH DEFAULTS / MIGRATION
  =======================================================================================================================

  ------------------------------------------------------------##-----------------------------------------------------*/

  // Create an empty graph
  function blankGraph() {
    return {
      Version: 9,
      Revision: 0,
      Template: "",
      ViewportX: 150,
      ViewportY: 80,
      ViewportZoom: 1,
      Nodes: [],
      Edges: [],
      Variables: {},
      Groups: [],
      Notes: [],
      Functions: []
    };
  }

  // Create an empty manifest
  function blankManifest(name = "New Contraption Graph") {
    const graphId = uniqueId("graph");
    const storedGraphs = [{
      GraphId: graphId,
      Name: name,
      Graph: blankGraph()
    }];
    return {
      schemaVersion: 1,
      kind: "contraption_network_linker",
      linkerManifestId: "",
      linkerId: "",
      displayName: name,
      targets: null,
      channelBindings: null,
      customEntryBindings: null,
      storedControllerManifests: null,
      storedGraphs: deepCopy(storedGraphs),
      graphImages: [],
      linkerData: {
        StoredGraphs: storedGraphs
      }
    };
  }

  // Check the saved manifest shape
  function ensureManifest(manifest) {
    if (!manifest || typeof manifest !== "object" || Array.isArray(manifest)) {
      throw new Error("Manifest root must be a JSON object");
    }
    if (manifest.kind && manifest.kind !== "contraption_network_linker") {
      throw new Error("This is not a Contraption Network Linker manifest");
    }
    manifest.kind = "contraption_network_linker";
    manifest.schemaVersion = Number(manifest.schemaVersion || 1);
    if (!manifest.linkerData || typeof manifest.linkerData !== "object"
        || Array.isArray(manifest.linkerData)) {
      manifest.linkerData = {};
    }
    if (!Array.isArray(manifest.linkerData.StoredGraphs)) {
      manifest.linkerData.StoredGraphs = Array.isArray(manifest.storedGraphs)
        ? deepCopy(manifest.storedGraphs) : [];
    }
    if (!manifest.linkerData.StoredGraphs.length) {
      const graphId = uniqueId("graph");
      manifest.linkerData.StoredGraphs.push({
        GraphId: graphId,
        Name: manifest.displayName || "Contraption Graph",
        Graph: blankGraph()
      });
    }
    for (const entry of manifest.linkerData.StoredGraphs) {
      entry.GraphId ||= uniqueId("graph");
      entry.Name ||= "Contraption Graph";
      if (!entry.Graph || typeof entry.Graph !== "object") entry.Graph = blankGraph();
      normalizeGraph(entry.Graph);
    }
    return manifest;
  }

  // Update old deadzone ports
  function migrateDeadzonePorts(nodes, edges) {
    for (const node of nodes) {
      if (node?.Type !== "deadzone") continue;
      const data = node.Data && typeof node.Data === "object" && !Array.isArray(node.Data)
        ? node.Data : (node.Data = {});
      const defaults = data.Defaults && typeof data.Defaults === "object" && !Array.isArray(data.Defaults)
        ? data.Defaults : (data.Defaults = {});
      if (!hasOwn(defaults, "resist") && hasOwn(defaults, "amount")) {
        defaults.resist = defaults.amount;
      }
      delete defaults.amount;
      if (!hasOwn(defaults, "start")) {
        defaults.start = { Type: "number", Payload: { Value: 0 } };
      }
      if (hasOwn(data, "amount") && !hasOwn(data, "resist")) data.resist = data.amount;
      delete data.amount;
      const dynamicInputs = data.DynamicInputs;
      if (dynamicInputs && typeof dynamicInputs === "object" && !Array.isArray(dynamicInputs)) {
        if (!hasOwn(dynamicInputs, "resist") && hasOwn(dynamicInputs, "amount")) {
          dynamicInputs.resist = dynamicInputs.amount;
        }
        delete dynamicInputs.amount;
        dynamicInputs.start ||= "number";
      }
    }
    for (const edge of edges) {
      const target = nodes.find((node) => String(node?.Id || "") === String(edge?.ToNode || ""));
      if (target?.Type === "deadzone" && edge.ToPort === "amount") edge.ToPort = "resist";
    }
  }

  // Update old curve defaults
  function migrateCurveDefaults(nodes) {
    for (const node of nodes) {
      if (node?.Type !== "curve") continue;
      const data = node.Data && typeof node.Data === "object" && !Array.isArray(node.Data)
        ? node.Data : (node.Data = {});
      const defaults = data.Defaults && typeof data.Defaults === "object" && !Array.isArray(data.Defaults)
        ? data.Defaults : (data.Defaults = {});
      defaults.min ||= { Type: "number", Payload: { Value: 0 } };
      defaults.max ||= { Type: "number", Payload: { Value: 1 } };
      defaults.speed ||= { Type: "number", Payload: { Value: 0.1 } };
      defaults.value ||= { Type: "number", Payload: { Value: 1 } };
    }
  }

  // Update old switch ports
  function migrateSwitchDataInputs(nodes, edges) {
    const dataSwitches = new Set();
    for (const node of nodes) {
      if (node?.Type !== "switch" || node?.Data?.SwitchType !== "data") continue;
      dataSwitches.add(String(node.Id || ""));
      const data = node.Data;
      const dynamicOutputs = data.DynamicOutputs && typeof data.DynamicOutputs === "object"
        ? data.DynamicOutputs : {};
      const dynamicInputs = data.DynamicInputs && typeof data.DynamicInputs === "object"
        ? { ...data.DynamicInputs } : {};
      const legacyTypes = data.SwitchOutputTypes && typeof data.SwitchOutputTypes === "object"
        ? data.SwitchOutputTypes : {};
      const caseTypes = data.SwitchCaseTypes && typeof data.SwitchCaseTypes === "object"
        ? { ...data.SwitchCaseTypes } : {};
      const outputDefaults = data.OutputDefaults && typeof data.OutputDefaults === "object"
        ? data.OutputDefaults : {};
      const defaults = data.Defaults && typeof data.Defaults === "object"
        ? { ...data.Defaults } : {};
      const ports = new Set(["default",
        ...Object.keys(dynamicOutputs).filter((port) => /^case_\d+$/.test(port)),
        ...Object.keys(dynamicInputs).filter((port) => /^case_\d+$/.test(port))]);
      for (const port of ports) {
        const requested = caseTypes[port] || legacyTypes[port] || dynamicOutputs[port];
        const type = ["any", "boolean", "string", "number", "direction", "frequency", "target"]
          .includes(requested) ? requested : "any";
        caseTypes[port] = type;
        if (/^case_\d+$/.test(port)) dynamicInputs[port] = type;
        defaults[port] ||= outputDefaults[port] || (["frequency", "target"].includes(type)
          ? { Type: type, Payload: {} }
          : { Type: type === "any" ? "string" : type, Payload: { Value: type === "boolean" ? false : type === "number" ? 0 : "" } });
      }
      delete dynamicInputs.exec;
      delete dynamicInputs.value;
      const persistentPorts = data.PersistentPorts && typeof data.PersistentPorts === "object"
        ? { ...data.PersistentPorts } : {};
      const persistentValues = data.PersistentPortValues && typeof data.PersistentPortValues === "object"
        ? { ...data.PersistentPortValues } : {};
      for (const port of ports) {
        const oldKey = `output:${port}`;
        const newKey = `input:${port}`;
        if (persistentPorts[oldKey]) persistentPorts[newKey] = true;
        delete persistentPorts[oldKey];
        if (persistentValues[oldKey]) {
          persistentValues[newKey] = persistentValues[oldKey];
          defaults[port] = persistentValues[oldKey];
        }
        delete persistentValues[oldKey];
      }
      data.DynamicInputs = dynamicInputs;
      data.SwitchCaseTypes = caseTypes;
      data.Defaults = defaults;
      data.PersistentPorts = persistentPorts;
      data.PersistentPortValues = persistentValues;
      delete data.DynamicOutputs;
      delete data.SwitchOutputTypes;
      delete data.OutputDefaults;
    }
    for (const edge of edges) {
      if (dataSwitches.has(String(edge?.FromNode || ""))
        && (edge.FromPort === "default" || /^case_\d+$/.test(edge.FromPort))) {
        edge.FromPort = "value";
      }
    }
  }

  // Update old graph data
  function normalizeGraph(graph) {
    const storedVersion = Math.max(1, Number(graph.Version || 1));
    graph.Version = Math.max(9, storedVersion);
    graph.Revision = Math.max(0, Number(graph.Revision || 0));
    graph.Template ||= "";
    graph.ViewportX = Number.isFinite(Number(graph.ViewportX)) ? Number(graph.ViewportX) : 150;
    graph.ViewportY = Number.isFinite(Number(graph.ViewportY)) ? Number(graph.ViewportY) : 80;
    graph.ViewportZoom = Number.isFinite(Number(graph.ViewportZoom))
      ? Math.min(1.75, Math.max(0.05, Number(graph.ViewportZoom))) : 1;
    if (!Array.isArray(graph.Nodes)) graph.Nodes = [];
    if (!Array.isArray(graph.Edges)) graph.Edges = [];
    if (!graph.Variables || typeof graph.Variables !== "object") graph.Variables = {};
    if (!Array.isArray(graph.Groups)) graph.Groups = [];
    if (!Array.isArray(graph.Notes)) graph.Notes = [];
    if (!Array.isArray(graph.Functions)) graph.Functions = [];
    for (const fn of graph.Functions) {
      fn.Id ||= uniqueId("function");
      fn.Name ||= "Function";
      fn.ViewportX = Number.isFinite(Number(fn.ViewportX)) ? Number(fn.ViewportX) : 150;
      fn.ViewportY = Number.isFinite(Number(fn.ViewportY)) ? Number(fn.ViewportY) : 80;
      fn.ViewportZoom = Number.isFinite(Number(fn.ViewportZoom))
        ? Math.min(1.75, Math.max(0.05, Number(fn.ViewportZoom))) : 1;
      if (!Array.isArray(fn.Nodes)) fn.Nodes = [];
      if (!Array.isArray(fn.Edges)) fn.Edges = [];
      if (!Array.isArray(fn.Groups)) fn.Groups = [];
    }
    if (storedVersion < 7) {
      migrateDeadzonePorts(graph.Nodes, graph.Edges);
      for (const fn of graph.Functions) migrateDeadzonePorts(fn.Nodes, fn.Edges);
    }
    if (storedVersion < 8) {
      migrateCurveDefaults(graph.Nodes);
      for (const fn of graph.Functions) migrateCurveDefaults(fn.Nodes);
    }
    if (storedVersion < 9) {
      migrateSwitchDataInputs(graph.Nodes, graph.Edges);
      for (const fn of graph.Functions) migrateSwitchDataInputs(fn.Nodes, fn.Edges);
    }
  }

  /*--------------------------------------------------------##---------------------------------------------------------

  =======================================================================================================================
                                                       GRAPH HELPERS
  =======================================================================================================================

  ------------------------------------------------------------##-----------------------------------------------------*/

  // Resolve the root graph or the function currently open in the editor
  function storedGraphs() {
    return state.manifest?.linkerData?.StoredGraphs || [];
  }

  // Get the selected graph
  function selectedGraph() {
    const entries = storedGraphs();
    return entries.find((entry) => entry.GraphId === state.storedGraphId) || entries[0] || null;
  }

  // Get the root graph
  function rootGraph() {
    return selectedGraph()?.Graph || null;
  }

  // Get the graph being edited
  function activeGraph() {
    const graph = rootGraph();
    if (!graph || state.activeGraphKey === "main") return graph;
    return graph.Functions.find((fn) => fn.Id === state.activeGraphKey) || graph;
  }

  // Get dynamic node ports
  function dynamicPorts(data, key) {
    const ports = data?.[key];
    return ports && typeof ports === "object" && !Array.isArray(ports) ? ports : {};
  }

  /*--------------------------------------------------------##---------------------------------------------------------

  =======================================================================================================================
                                                     GRAPH CONVERSION
  =======================================================================================================================

  ------------------------------------------------------------##-----------------------------------------------------*/

  // Convert a packed colour to hex
  function colorToHex(value, fallback = "#7D8A99") {
    if (typeof value === "string" && /^#[0-9a-f]{6}/i.test(value)) return value.slice(0, 7);
    if (!Number.isFinite(Number(value))) return fallback;
    return `#${(Number(value) >>> 0 & 0xFFFFFF).toString(16).padStart(6, "0")}`;
  }

  // Convert a hex colour to NBT
  function colorToNBT(value) {
    const match = String(value || "").match(/^#([0-9a-f]{6})/i);
    if (!match) return 0xFF5D9FE3 | 0;
    return (0xFF000000 | Number.parseInt(match[1], 16)) | 0;
  }

  // Convert a group for the studio
  function groupToStudio(group) {
    return {
      id: group.Id || uniqueId("group"),
      name: group.Title || "Comment Group",
      x: toStudioPos(group.X),
      y: toStudioPos(group.Y),
      width: toStudioPos(group.Width || 320),
      height: toStudioPos(group.Height || 180),
      color: `${colorToHex(group.Color, "#8A7424")}1a`,
      nodeIds: Array.isArray(group.NodeIds) ? deepCopy(group.NodeIds) : [],
      __ct: deepCopy(group)
    };
  }

  // Convert a studio group for the graph
  function groupFromStudio(group) {
    return {
      ...(group.__ct && typeof group.__ct === "object" ? deepCopy(group.__ct) : {}),
      Id: String(group.id || uniqueId("group")),
      Title: String(group.name || "Comment Group"),
      X: toGraphPos(group.x),
      Y: toGraphPos(group.y),
      Width: Math.max(40, toGraphPos(group.width || 320)),
      Height: Math.max(40, toGraphPos(group.height || 180)),
      Color: colorToNBT(group.color),
      NodeIds: Array.isArray(group.nodeIds) ? deepCopy(group.nodeIds) : []
    };
  }

  // Translate typed defaults between Minecraft graph data and the browser studio
  function defaultToStudio(entry) {
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) return undefined;
    const type = String(entry.Type || "").trim().toLowerCase();
    const payload = entry.Payload;
    if (!payload || typeof payload !== "object" || Array.isArray(payload)) return undefined;
    if (type === "list") {
      const values = hasOwn(payload, "Value") ? payload.Value : payload;
      if (Array.isArray(values)) {
        return values.map((value) => defaultToStudio(value) ?? deepCopy(value));
      }
      if (!values || typeof values !== "object") return [];
      return Object.keys(values)
        .sort((first, second) => Number(first) - Number(second))
        .map((key) => defaultToStudio(values[key]) ?? deepCopy(values[key]));
    }
    if (type === "map") {
      const values = hasOwn(payload, "Value") ? payload.Value : payload;
      if (!values || typeof values !== "object" || Array.isArray(values)) return {};
      return Object.fromEntries(Object.entries(values)
        .map(([key, value]) => [key, defaultToStudio(value) ?? deepCopy(value)]));
    }
    if (hasOwn(payload, "Value")) return deepCopy(payload.Value);
    return deepCopy(payload);
  }

  // Convert studio defaults for the graph
  function defaultToGraph(value, declaredType, prevEntry) {
    const previous = prevEntry && typeof prevEntry === "object" && !Array.isArray(prevEntry)
      ? prevEntry : {};
    const prevType = String(previous.Type || "").trim().toLowerCase();
    let type = String(declaredType || prevType || "any").trim().toLowerCase();
    if (!type || type === "exec") type = prevType || "any";
    if (type === "any") {
      if (typeof value === "boolean") type = "boolean";
      else if (typeof value === "number") type = "number";
      else if (Array.isArray(value)) type = "list";
      else if (value && typeof value === "object") type = "map";
      else if (prevType && prevType !== "any") type = prevType;
      else type = "string";
    }
    if (type === "list") {
      const values = Array.isArray(value) ? value : [];
      const prevPayload = previous.Payload && typeof previous.Payload === "object"
        ? previous.Payload : {};
      return {
        Type: "list",
        Payload: Object.fromEntries(values.map((child, index) => {
          const key = String(index);
          return [key, defaultToGraph(child, "any", prevPayload[key])];
        }))
      };
    }
    if (type === "map") {
      const values = value && typeof value === "object" && !Array.isArray(value) ? value : {};
      const prevPayload = previous.Payload && typeof previous.Payload === "object"
        ? previous.Payload : {};
      return {
        Type: "map",
        Payload: Object.fromEntries(Object.entries(values)
          .map(([key, child]) => [key,
            defaultToGraph(child, "any", prevPayload[key])]))
      };
    }
    let normalized = value;
    if (type === "boolean") {
      normalized = typeof value === "string" ? value.toLowerCase() === "true" : Boolean(value);
    } else if (type === "number") {
      const number = Number(value);
      normalized = Number.isFinite(number) ? number : 0;
    } else if (["string", "direction"].includes(type)) {
      normalized = value == null ? "" : String(value);
    }
    const payload = normalized && typeof normalized === "object" && !Array.isArray(normalized)
      ? deepCopy(normalized)
      : { Value: normalized };
    return { Type: type, Payload: payload };
  }

  /*--------------------------------------------------------##---------------------------------------------------------

  =======================================================================================================================
                                                        VARIABLES
  =======================================================================================================================

  ------------------------------------------------------------##-----------------------------------------------------*/

  // Keep graph variables and their dynamic ports in sync with studio nodes
  function toStudioVarType(type) {
    return switchVarType(type, {
      integer: "number",
      float: "number",
      direction: "string",
      frequency: "map",
      target: "map",
      array: "list",
      json: "map"
    });
  }

  // Apply variable type aliases
  function switchVarType(type, aliases = {}) {
    const normalized = String(type || "string").trim().toLowerCase();
    return aliases[normalized] || normalized || "string";
  }

  // Get a graph variable type
  function graphVarType(data, varEntry) {
    return String(data?.VariableType || varEntry?.Type || "string").trim().toLowerCase();
  }

  // Get a studio variable default
  function studioVarDefault(type) {
    if (type === "boolean") return false;
    if (type === "number") return 0;
    if (type === "list") return [];
    if (type === "map") return {};
    return "";
  }

  // Convert a variable node for the studio
  function varNodeToStudio(node, graph) {
    const def = state.definitions.get(node.Type);
    const srcData = node.Data && typeof node.Data === "object" ? deepCopy(node.Data) : {};
    const varName = String(srcData.Variable || "variable");
    const varEntry = rootGraph()?.Variables?.[varName]
      || graph?.Variables?.[varName];
    const origType = graphVarType(srcData, varEntry);
    const studioType = toStudioVarType(origType);
    const storedVal = defaultToStudio(varEntry)
      ?? defaultToStudio(srcData.Defaults?.default)
      ?? studioVarDefault(studioType);
    const isSetter = node.Type === "variable_set";
    const studioData = {
      varName: varName,
      value: storedVal,
      mode: isSetter ? "set" : "get",
      dataType: studioType,
      isCollapsed: Boolean(srcData.EditorCollapsed),
      __ctGraphVariableData: srcData,
      __ctGraphVariableType: origType,
      __ctStudioVariableType: studioType
    };
    if (isSetter) {
      studioData[STUDIO_VARIABLE_SET_VALUE] =
        defaultToStudio(srcData.Defaults?.value) ?? storedVal;
    }
    return {
      id: String(node.Id || uniqueId("node")),
      type: "core_var",
      name: String(node.Label || varName),
      x: toStudioPos(node.X),
      y: toStudioPos(node.Y),
      data: studioData,
      inputs: isSetter ? [STUDIO_FLOW_INPUT, STUDIO_VARIABLE_SET_VALUE] : [],
      outputs: isSetter
        ? [STUDIO_VARIABLE_FLOW_OUTPUT, STUDIO_VARIABLE_VALUE]
        : [STUDIO_VARIABLE_VALUE],
      color: def?.color || "#B987E8"
    };
  }

  // Convert a variable port for the studio
  function graphVarPortToStudio(nodeType, port, direction) {
    if (nodeType === "variable_get") {
      return direction === "output" && port === "value" ? STUDIO_VARIABLE_VALUE : port;
    }
    if (nodeType !== "variable_set") return port;
    if (direction === "input") {
      if (port === "exec") return STUDIO_FLOW_INPUT;
      if (port === "value") return STUDIO_VARIABLE_SET_VALUE;
    } else {
      if (port === "exec") return STUDIO_VARIABLE_FLOW_OUTPUT;
      if (port === "value") return STUDIO_VARIABLE_VALUE;
    }
    return port;
  }

  // Convert a studio variable port
  function studioVarPortToGraph(mode, port, direction) {
    if (mode !== "get" && mode !== "set") return port;
    if (mode === "get") {
      return direction === "output" && port === STUDIO_VARIABLE_VALUE ? "value" : port;
    }
    if (direction === "input") {
      if (port === STUDIO_FLOW_INPUT) return "exec";
      if (port === STUDIO_VARIABLE_SET_VALUE) return "value";
    } else {
      if (port === STUDIO_VARIABLE_FLOW_OUTPUT) return "exec";
      if (port === STUDIO_VARIABLE_VALUE) return "value";
    }
    return port;
  }

  // Get a studio variable type
  function studioNodeVarType(studioData) {
    const studioType = switchVarType(studioData?.dataType || "string");
    const originalStudioType = switchVarType(studioData?.__ctStudioVariableType || "");
    return studioType === originalStudioType
      ? switchVarType(studioData?.__ctGraphVariableType || studioType)
      : studioType;
  }

  // Convert a studio variable node
  function studioVarNodeToGraph(node) {
    const studioData = node.data && typeof node.data === "object" ? node.data : {};
    const srcData = studioData.__ctGraphVariableData
      && typeof studioData.__ctGraphVariableData === "object"
      ? deepCopy(studioData.__ctGraphVariableData) : {};
    const graphType = studioNodeVarType(studioData);
    const isSetter = studioData.mode === "set";
    srcData.Variable = String(studioData.varName || node.name || "variable");
    if (studioData.isCollapsed) srcData.EditorCollapsed = true;
    else delete srcData.EditorCollapsed;
    if (isSetter) {
      const variableOption = graphType === "number" ? "float" : graphType;
      srcData.VariableType = variableOption;
      srcData.DynamicInputs = { default: graphType, value: graphType };
      srcData.DynamicOutputs = { value: graphType };
      const defaults = srcData.Defaults && typeof srcData.Defaults === "object"
        ? srcData.Defaults : {};
      defaults.type = defaultToGraph(variableOption, "string", defaults.type);
      defaults.default = defaultToGraph(studioData.value, graphType, defaults.default);
      defaults.value = defaultToGraph(
        studioData[STUDIO_VARIABLE_SET_VALUE], graphType, defaults.value);
      srcData.Defaults = defaults;
    } else {
      srcData.DynamicOutputs = { value: graphType };
    }
    return {
      Id: String(node.id || uniqueId("node")),
      Type: isSetter ? "variable_set" : "variable_get",
      Label: String(node.name || "") === srcData.Variable ? "" : String(node.name || ""),
      X: toGraphPos(node.x),
      Y: toGraphPos(node.y),
      Data: srcData
    };
  }

  // Update graph variables from the studio
  function syncGraphVars(plugin) {
    const root = rootGraph();
    if (!root) return;
    const previous = root.Variables && typeof root.Variables === "object"
      ? root.Variables : {};
    const updated = new Map();
    for (const node of plugin.nodes || []) {
      if (node.type !== "core_var" || !String(node.data?.varName || "").trim()) continue;
      const name = String(node.data.varName).trim();
      const type = studioNodeVarType(node.data);
      updated.set(name, defaultToGraph(node.data.value, type, previous[name]));
    }

    const referenced = new Set();
    // Collect the variables still referenced by graph nodes
    const collect = (nodes) => {
      for (const node of nodes || []) {
        if (!["variable_get", "variable_set", "event_variable_change"].includes(node?.Type)) continue;
        const name = String(node?.Data?.Variable || "").trim();
        if (name) referenced.add(name);
      }
    };
    collect(root.Nodes);
    for (const fn of root.Functions || []) collect(fn.Nodes);

    const next = {};
    for (const name of referenced) {
      next[name] = updated.get(name) || previous[name]
        || defaultToGraph("", "string");
    }
    root.Variables = next;
  }

  // Convert notes, groups and image references without losing their saved metadata
  function noteToStudio(node) {
    const data = node.Data && typeof node.Data === "object" ? node.Data : {};
    return {
      id: String(node.Id || uniqueId("note")),
      content: String(data.Text ?? ""),
      locked: false,
      x: toStudioPos(node.X),
      y: toStudioPos(node.Y),
      width: Math.max(160, toStudioPos(data.Width || 180)),
      height: Math.max(100, toStudioPos(data.Height || 110)),
      createdAt: Number(data.CreatedAt || Date.now()),
      updatedAt: Number(data.UpdatedAt || Date.now()),
      __ct: deepCopy(node)
    };
  }

  // Convert a studio note for the graph
  function noteFromStudio(note) {
    const source = note.__ct && typeof note.__ct === "object" ? deepCopy(note.__ct) : {};
    const data = source.Data && typeof source.Data === "object" ? source.Data : {};
    data.Text = String(note.content ?? "");
    data.Width = Math.max(80, Math.round(toGraphPos(note.width || 300)));
    data.Height = Math.max(50, Math.round(toGraphPos(note.height || 200)));
    delete data.CreatedAt;
    delete data.UpdatedAt;
    return {
      ...source,
      Id: String(note.id || source.Id || uniqueId("note")),
      Type: "sticky_note",
      Label: String(source.Label || ""),
      X: toGraphPos(note.x),
      Y: toGraphPos(note.y),
      Data: data
    };
  }

  // Get the image preview source
  function imagePreviewSrc(data) {
    const source = String(data?.Source || "");
    if (!source.startsWith("graph-image:")) return source;
    const assetId = String(data?.ImageAssetId || source.slice("graph-image:".length));
    const graphId = String(selectedGraph()?.GraphId || "");
    const asset = (state.manifest?.graphImages || []).find((candidate) =>
      String(candidate?.assetId || "") === assetId
      && (!candidate?.graphId || String(candidate.graphId) === graphId));
    if (!asset?.base64) return "";
    return `data:${asset.mediaType || "image/png"};base64,${asset.base64}`;
  }

  // Merge fixed catalog ports with the dynamic ports stored on each node
  function nodePorts(def, data, direction) {
    if (def?.id === "switch" && data?.SwitchType === "data") {
      if (direction === "outputs") return { value: "any" };
      const caseTypes = data.SwitchCaseTypes && typeof data.SwitchCaseTypes === "object"
        ? data.SwitchCaseTypes : {};
      const dynamicInputs = dynamicPorts(data, "DynamicInputs");
      const inputs = {
        selector: "number",
        default: caseTypes.default || "any"
      };
      for (const [port, type] of Object.entries(dynamicInputs)) {
        if (/^case_\d+$/.test(port)) inputs[port] = caseTypes[port] || type || "any";
      }
      return inputs;
    }
    const dynamicConstructorInputs = direction === "inputs"
      && Boolean(data?.DynamicConstructor)
      && (def?.id === "list_create" || def?.id === "map_create");
    const ports = {
      ...(dynamicConstructorInputs ? {} : (def?.[direction] || {})),
      ...dynamicPorts(data, direction === "inputs" ? "DynamicInputs" : "DynamicOutputs")
    };
    if (def?.id === "pid" && direction === "inputs") {
      if (Boolean(data?.PreventIntegralWindup)) {
        ports.integral_min = "number";
        ports.integral_max = "number";
      } else {
        delete ports.integral_min;
        delete ports.integral_max;
      }
    }
    return ports;
  }

  // Convert complete graphs in both directions while preserving data the studio does not understand
  function graphToPlugin(graph) {
    const stored = selectedGraph();
    const scopeName = state.activeGraphKey === "main"
      ? stored?.Name || "Main Graph"
      : graph.Name || "Function";
    const id = `ct_${++state.studioSerial}_${String(stored?.GraphId || "graph").replace(/\W/g, "_")}`;
    const graphNodes = Array.isArray(graph.Nodes) ? graph.Nodes : [];
    const controllerNodes = graphNodes.filter((node) => {
      const type = String(node?.Type || "").trim();
      return type !== "sticky_note" && type && state.definitions.has(type);
    });
    const passthroughNodes = graphNodes.filter((node) => {
      const type = String(node?.Type || "").trim();
      return type !== "sticky_note" && (!type || !state.definitions.has(type));
    });
    const nodes = controllerNodes.map((node) => {
      if (node.Type === "variable_get" || node.Type === "variable_set") {
        return varNodeToStudio(node, graph);
      }
      const def = state.definitions.get(node.Type);
      const data = node.Data && typeof node.Data === "object" ? deepCopy(node.Data) : {};
      data.isCollapsed = Boolean(data.EditorCollapsed);
      const inputs = nodePorts(def, data, "inputs");
      const outputs = nodePorts(def, data, "outputs");
      const defaults = data.Defaults && typeof data.Defaults === "object" && !Array.isArray(data.Defaults)
        ? data.Defaults : {};
      for (const [port, type] of Object.entries(inputs)) {
        if (type !== "exec" && hasOwn(defaults, port)) {
          data[port] = defaultToStudio(defaults[port]);
        }
      }
      if (node.Type === "image_reference") {
        data.__ctImagePreview = imagePreviewSrc(data);
      }
      return {
        id: String(node.Id || uniqueId("node")),
        type: String(node.Type || ""),
        name: String(node.Label || def?.name || node.Type || "Node"),
        x: toStudioPos(node.X),
        y: toStudioPos(node.Y),
        data,
        inputs: Object.keys(inputs),
        outputs: Object.keys(outputs),
        color: def?.color || "#7D8A99"
      };
    });
    const editorNodeIds = new Set(controllerNodes.map((node) => String(node.Id || "")));
    const editorEdges = graph.Edges.filter((edge) => editorNodeIds.has(String(edge?.FromNode || ""))
      && editorNodeIds.has(String(edge?.ToNode || "")));
    const passthroughEdges = graph.Edges.filter((edge) => !editorNodeIds.has(String(edge?.FromNode || ""))
      || !editorNodeIds.has(String(edge?.ToNode || "")));
    const controllerNodeTypes = new Map(controllerNodes.map((node) => [String(node.Id || ""), node.Type]));
    const connections = editorEdges.map((edge) => ({
      id: String(edge.Id || uniqueId("edge")),
      fromNode: String(edge.FromNode || ""),
      fromPort: graphVarPortToStudio(
        controllerNodeTypes.get(String(edge.FromNode || "")),
        String(edge.FromPort || ""), "output"),
      toNode: String(edge.ToNode || ""),
      toPort: graphVarPortToStudio(
        controllerNodeTypes.get(String(edge.ToNode || "")),
        String(edge.ToPort || ""), "input")
    }));
    const exposedVariables = Object.entries(rootGraph()?.Variables || graph.Variables || {})
      .map(([name, entry]) => ({
        name,
        type: toStudioVarType(entry?.Type),
        defaultValue: defaultToStudio(entry) ?? studioVarDefault(toStudioVarType(entry?.Type))
      }));
    return {
      id,
      name: scopeName,
      description: "Advanced Contraption Controller graph",
      version: "1",
      enabled: true,
      type: "nodegraph",
      nodes,
      connections,
      groups: graph.Groups.map(groupToStudio),
      notes: graph.Nodes.filter((node) => node?.Type === "sticky_note").map(noteToStudio),
      exposedVariables,
      __ctViewport: {
        x: Number(graph.ViewportX || 0),
        y: Number(graph.ViewportY || 0),
        zoom: Number(graph.ViewportZoom || 1)
      },
      __ctPassthroughNodes: deepCopy(passthroughNodes),
      __ctPassthroughConnections: deepCopy(passthroughEdges)
    };
  }

  // Convert studio data back to a graph
  function pluginToGraph(plugin, graph) {
    if (!plugin || !graph) return;
    const nodes = (plugin.nodes || []).map((node) => {
      if (node.type === "core_var") return studioVarNodeToGraph(node);
      const def = state.definitions.get(node.type);
      const displayedName = String(node.name || "");
      const data = node.data && typeof node.data === "object" ? deepCopy(node.data) : {};
      const isCollapsed = Boolean(data.EditorCollapsed || data.isCollapsed);
      delete data.isCollapsed;
      delete data.__ctImagePreview;
      if (isCollapsed) data.EditorCollapsed = true;
      else delete data.EditorCollapsed;
      const inputs = nodePorts(def, data, "inputs");
      const defaults = data.Defaults && typeof data.Defaults === "object" && !Array.isArray(data.Defaults)
        ? data.Defaults : {};
      for (const [port, type] of Object.entries(inputs)) {
        if (type === "exec" || !hasOwn(data, port)) continue;
        defaults[port] = defaultToGraph(data[port], type, defaults[port]);
        delete data[port];
      }
      if (Object.keys(defaults).length) data.Defaults = defaults;
      else delete data.Defaults;
      return {
        Id: String(node.id || uniqueId("node")),
        Type: String(node.type || ""),
        Label: displayedName && displayedName !== def?.name ? displayedName : "",
        X: toGraphPos(node.x),
        Y: toGraphPos(node.y),
        Data: data
      };
    });
    const notes = (plugin.notes || []).map(noteFromStudio);
    const passthroughNodes = Array.isArray(plugin.__ctPassthroughNodes)
      ? deepCopy(plugin.__ctPassthroughNodes)
      : (graph.Nodes || []).filter((node) => {
        const type = String(node?.Type || "").trim();
        return type !== "sticky_note" && (!type || !state.definitions.has(type));
      }).map(deepCopy);
    graph.Nodes = [...nodes, ...notes, ...passthroughNodes];
    const variableModes = new Map((plugin.nodes || [])
      .filter((node) => node.type === "core_var")
      .map((node) => [String(node.id || ""), node.data?.mode === "set" ? "set" : "get"]));
    const editorEdges = (plugin.connections || []).map((edge) => ({
      Id: String(edge.id || uniqueId("edge")),
      FromNode: String(edge.fromNode || ""),
      FromPort: studioVarPortToGraph(
        variableModes.get(String(edge.fromNode || "")),
        String(edge.fromPort || ""), "output"),
      ToNode: String(edge.toNode || ""),
      ToPort: studioVarPortToGraph(
        variableModes.get(String(edge.toNode || "")),
        String(edge.toPort || ""), "input")
    }));
    const validNodeIds = new Set(graph.Nodes.map((node) => String(node?.Id || "")));
    const passthroughNodeIds = new Set(passthroughNodes.map((node) => String(node?.Id || "")));
    const passthroughEdges = Array.isArray(plugin.__ctPassthroughConnections)
      ? deepCopy(plugin.__ctPassthroughConnections)
      : (graph.Edges || []).filter((edge) => passthroughNodeIds.has(String(edge?.FromNode || ""))
        || passthroughNodeIds.has(String(edge?.ToNode || ""))).map(deepCopy);
    graph.Edges = [...editorEdges, ...passthroughEdges].filter((edge) =>
      validNodeIds.has(String(edge?.FromNode || ""))
      && validNodeIds.has(String(edge?.ToNode || "")));
    graph.Groups = (plugin.groups || []).map(groupFromStudio);
    if (plugin.__ctViewport && typeof plugin.__ctViewport === "object") {
      graph.ViewportX = Number(plugin.__ctViewport.x || 0);
      graph.ViewportY = Number(plugin.__ctViewport.y || 0);
      graph.ViewportZoom = Math.min(1.75,
        Math.max(0.05, Number(plugin.__ctViewport.zoom || 1)));
    }
    syncGraphVars(plugin);
    state.isDirty = true;
    updateStats();
  }

  /*--------------------------------------------------------##---------------------------------------------------------

  =======================================================================================================================
                                                          EDITOR
  =======================================================================================================================

  ------------------------------------------------------------##-----------------------------------------------------*/

  // Commit studio changes back into the manifest before any save or graph switch
  function syncManifest() {
    if (!state.manifest) return;
    const displayName = el.manifestName.value.trim()
      || state.manifest.displayName || state.serverId || "Contraption Graph";
    state.manifest.displayName = displayName;
    state.manifest.storedGraphs = deepCopy(storedGraphs());
    delete state.manifest.contentHash;
  }

  // Save studio changes to the graph
  function saveStudioGraph() {
    if (!state.latestPlugin || state.isLoadingPlugin) return;
    pluginToGraph(state.latestPlugin, activeGraph());
    syncManifest();
  }

  // Wait for the studio before saving
  async function settleAndSave() {
    await wait(140);
    saveStudioGraph();
  }

  // Update graph totals
  function updateStats() {
    const graph = activeGraph();
    const plugin = state.latestPlugin;
    const nodes = plugin
      ? (plugin.nodes?.length || 0) + (plugin.notes?.length || 0)
      : graph?.Nodes?.length || 0;
    const edges = plugin?.connections?.length ?? graph?.Edges?.length ?? 0;
    const selected = selectedGraph();
    const functionGraph = state.activeGraphKey === "main"
      ? null : rootGraph()?.Functions.find((fn) => fn.Id === state.activeGraphKey);
    el.graphName.textContent = functionGraph?.Name || selected?.Name || "Main graph";
    el.graphStats.textContent = `${nodes} nodes · ${edges} wires${state.isDirty ? " · modified" : ""}`;
  }

  // Keep the graph selector, function tabs and document counters on the active graph
  function drawGraphSelect() {
    el.graphSelect.replaceChildren();
    for (const entry of storedGraphs()) {
      const option = document.createElement("option");
      option.value = entry.GraphId;
      option.textContent = entry.Name || entry.GraphId;
      option.selected = entry.GraphId === state.storedGraphId;
      el.graphSelect.append(option);
    }
  }

  // Draw the function tabs
  function drawFunctionTabs() {
    el.functionTabs.replaceChildren();
    const graph = rootGraph();
    const tabs = [{ id: "main", name: "Main" },
      ...(graph?.Functions || []).map((fn) => ({ id: fn.Id, name: fn.Name }))];
    for (const tab of tabs) {
      const button = document.createElement("button");
      button.className = `function-tab${tab.id === state.activeGraphKey ? " active" : ""}`;
      button.textContent = tab.name;
      button.title = tab.id === "main" ? "Main graph" : "Double-click to rename this function";
      button.addEventListener("click", () => switchFunction(tab.id));
      if (tab.id !== "main") {
        button.addEventListener("dblclick", async (event) => {
          event.preventDefault();
          const fn = rootGraph()?.Functions.find((candidate) => candidate.Id === tab.id);
          if (!fn) return;
          const next = window.prompt("Function name", fn.Name);
          if (!next?.trim()) return;
          fn.Name = next.trim().slice(0, 64);
          state.isDirty = true;
          drawFunctionTabs();
          if (state.activeGraphKey === fn.Id) loadActiveGraph();
        });
      }
      el.functionTabs.append(button);
    }
  }

  // Load the active graph in the studio
  function loadActiveGraph() {
    const graph = activeGraph();
    if (!state.studio || !graph) return;
    state.isLoadingPlugin = true;
    const plugin = graphToPlugin(graph);
    state.latestPlugin = plugin;
    state.studio.setPlugins([plugin]);
    state.studio.openPlugin(plugin.id);
    updateStats();
    window.setTimeout(() => {
      state.isLoadingPlugin = false;
    }, 220);
  }

  // Open another graph function
  async function switchFunction(functionId) {
    if (state.isSwitching || functionId === state.activeGraphKey) return;
    state.isSwitching = true;
    try {
      await settleAndSave();
      state.activeGraphKey = functionId;
      drawFunctionTabs();
      loadActiveGraph();
    } finally {
      state.isSwitching = false;
    }
  }

  // Create an empty graph function
  async function addFunction() {
    if (!rootGraph() || state.isSwitching) return;
    const requestedName = window.prompt("Function name", "New Function");
    if (!requestedName?.trim()) return;
    await settleAndSave();
    const fn = {
      Id: uniqueId("function"),
      Name: requestedName.trim().slice(0, 64),
      ViewportX: 150,
      ViewportY: 80,
      ViewportZoom: 1,
      Nodes: [],
      Edges: [],
      Groups: []
    };
    rootGraph().Functions.push(fn);
    state.isDirty = true;
    state.activeGraphKey = fn.Id;
    drawFunctionTabs();
    loadActiveGraph();
    setStatus(`Created function “${fn.Name}”`, "success");
  }

  /*--------------------------------------------------------##---------------------------------------------------------

  =======================================================================================================================
                                                    GRAPH FUNCTIONS
  =======================================================================================================================

  ------------------------------------------------------------##-----------------------------------------------------*/

  // Turn selected nodes into a reusable function with stable input and output ports
  function nextFnName() {
    const names = new Set((rootGraph()?.Functions || [])
      .map((fn) => String(fn.Name || "").toLowerCase()));
    if (!names.has("function")) return "Function";
    let suffix = 2;
    while (names.has(`function ${suffix}`)) suffix++;
    return `Function ${suffix}`;
  }

  // Create a unique function port
  function uniqueFnPort(requested, used) {
    const base = String(requested || "").trim().toLowerCase()
      .replace(/[^a-z0-9_]+/g, "_").replace(/^_+|_+$/g, "") || "value";
    let candidate = base;
    let suffix = 2;
    while (used.has(candidate)) candidate = `${base}_${suffix++}`;
    used.add(candidate);
    return candidate;
  }

  // Check for a standalone execution source
  function isStandaloneExecSource(node) {
    const def = state.definitions.get(node?.Type);
    if (!def) return false;
    return !Object.values(def.inputs || {}).includes("exec")
      && Object.values(def.outputs || {}).includes("exec");
  }

  // Get a graph port type
  function graphPortType(node, direction, port) {
    const def = state.definitions.get(node?.Type);
    return nodePorts(def, node?.Data, direction)[port] || "any";
  }

  // Convert selected nodes to a function
  async function selectionToFunction(event) {
    if (state.isSwitching || !rootGraph()) return;
    const requestedIds = Array.isArray(event?.detail?.nodeIds) ? event.detail.nodeIds : [];
    if (requestedIds.length < 2) {
      setStatus("Select at least two nodes to convert them to a function", "error");
      return;
    }

    state.isSwitching = true;
    try {
      await settleAndSave();
      const source = activeGraph();
      if (!source) return;
      const requested = new Set(requestedIds.map(String));
      const selected = new Set(source.Nodes
        .filter((node) => requested.has(String(node.Id)) && !isStandaloneExecSource(node))
        .map((node) => String(node.Id)));
      const movedNodes = source.Nodes.filter((node) => selected.has(String(node.Id)));
      if (!movedNodes.length) {
        setStatus("The selected execution source nodes cannot be converted", "error");
        return;
      }

      const functionId = uniqueId("function");
      const functionName = nextFnName();
      const minX = Math.min(...movedNodes.map((node) => Number(node.X || 0)));
      const minY = Math.min(...movedNodes.map((node) => Number(node.Y || 0)));
      const maxX = Math.max(...movedNodes.map((node) =>
        Number(node.X || 0) + Math.max(80, Number(node.Data?.Width || 166))));
      const maxY = Math.max(...movedNodes.map((node) =>
        Number(node.Y || 0) + Math.max(54, Number(node.Data?.Height || 80))));
      const inputPorts = {};
      const outputPorts = {};
      const functionInput = {
        Id: uniqueId("node"),
        Type: "function_input",
        Label: "Inputs",
        X: minX - 230,
        Y: minY,
        Data: { DynamicOutputs: inputPorts }
      };
      const functionOutput = {
        Id: uniqueId("node"),
        Type: "function_output",
        Label: "Outputs",
        X: maxX + 80,
        Y: minY,
        Data: { DynamicInputs: outputPorts }
      };
      const fn = {
        Id: functionId,
        Name: functionName,
        ViewportX: Number(source.ViewportX || 150),
        ViewportY: Number(source.ViewportY || 80),
        ViewportZoom: Number(source.ViewportZoom || 1),
        Nodes: [...movedNodes, functionInput, functionOutput],
        Edges: [],
        Groups: []
      };

      const usedInputs = new Set();
      const usedOutputs = new Set();
      const outerEdges = [];
      for (const edge of source.Edges) {
        const fromSelected = selected.has(String(edge.FromNode));
        const toSelected = selected.has(String(edge.ToNode));
        if (fromSelected && toSelected) {
          fn.Edges.push(edge);
        } else if (!fromSelected && toSelected) {
          const target = movedNodes.find((node) => String(node.Id) === String(edge.ToNode));
          const port = uniqueFnPort(edge.ToPort, usedInputs);
          inputPorts[port] = graphPortType(target, "inputs", edge.ToPort);
          fn.Edges.push({
            Id: uniqueId("edge"),
            FromNode: functionInput.Id,
            FromPort: port,
            ToNode: edge.ToNode,
            ToPort: edge.ToPort
          });
          outerEdges.push({
            Id: uniqueId("edge"),
            FromNode: edge.FromNode,
            FromPort: edge.FromPort,
            ToNode: "",
            ToPort: port
          });
        } else if (fromSelected && !toSelected) {
          const sourceNode = movedNodes.find((node) => String(node.Id) === String(edge.FromNode));
          const port = uniqueFnPort(edge.FromPort, usedOutputs);
          outputPorts[port] = graphPortType(sourceNode, "outputs", edge.FromPort);
          fn.Edges.push({
            Id: uniqueId("edge"),
            FromNode: edge.FromNode,
            FromPort: edge.FromPort,
            ToNode: functionOutput.Id,
            ToPort: port
          });
          outerEdges.push({
            Id: uniqueId("edge"),
            FromNode: "",
            FromPort: port,
            ToNode: edge.ToNode,
            ToPort: edge.ToPort
          });
        }
      }

      const remainingGroups = [];
      for (const group of source.Groups) {
        const members = Array.isArray(group.NodeIds) ? group.NodeIds.map(String) : [];
        const movedMembers = members.filter((id) => selected.has(id));
        if (movedMembers.length) {
          fn.Groups.push({ ...deepCopy(group), NodeIds: movedMembers });
        }
        const remainingMembers = members.filter((id) => !selected.has(id));
        if (remainingMembers.length) {
          remainingGroups.push({ ...group, NodeIds: remainingMembers });
        }
      }

      const call = {
        Id: uniqueId("node"),
        Type: "function_call",
        Label: functionName,
        X: (minX + maxX - 166) * 0.5,
        Y: (minY + maxY - 54) * 0.5,
        Data: {
          FunctionId: functionId,
          FunctionName: functionName,
          DynamicInputs: deepCopy(inputPorts),
          DynamicOutputs: deepCopy(outputPorts)
        }
      };
      source.Nodes = source.Nodes.filter((node) => !selected.has(String(node.Id)));
      source.Nodes.push(call);
      source.Edges = source.Edges.filter((edge) =>
        !selected.has(String(edge.FromNode)) && !selected.has(String(edge.ToNode)));
      source.Edges.push(...outerEdges.map((edge) => edge.FromNode
        ? { ...edge, ToNode: call.Id }
        : { ...edge, FromNode: call.Id }));
      source.Groups = remainingGroups;
      rootGraph().Functions.push(fn);

      state.isDirty = true;
      state.activeGraphKey = functionId;
      state.latestPlugin = null;
      drawFunctionTabs();
      loadActiveGraph();
      setStatus(`Converted ${movedNodes.length} node(s) to ${functionName}`, "success");
    } finally {
      state.isSwitching = false;
    }
  }

  /*--------------------------------------------------------##---------------------------------------------------------

  =======================================================================================================================
                                                        MANIFESTS
  =======================================================================================================================

  ------------------------------------------------------------##-----------------------------------------------------*/

  // Load and save manifests exposed by the local graph server
  function drawManifestList() {
    const query = el.filter.value.trim().toLowerCase();
    const visible = state.manifests.filter((manifest) =>
      !query || manifest.name.toLowerCase().includes(query) || manifest.id.toLowerCase().includes(query));
    el.manifestList.replaceChildren();
    if (!isPublicSite) {
      const current = document.createElement("button");
      current.className = `manifest-entry current-graph-entry${state.isGameGraph ? " active" : ""}`;
      current.disabled = !state.isGameGraphAvailable;
      const currentName = document.createElement("strong");
      currentName.textContent = "Currently Open Graph";
      const currentState = document.createElement("small");
      currentState.textContent = state.isGameGraphAvailable ? "LIVE FROM GAME" : "NO CONTROLLER GRAPH OPEN";
      current.append(currentName, currentState);
      current.addEventListener("click", loadGameGraph);
      el.manifestList.append(current);
    }
    if (!visible.length) {
      const empty = document.createElement("p");
      empty.className = "empty-message";
      empty.textContent = query ? "No shared graphs match this filter." : "No shared graphs are stored in this instance.";
      el.manifestList.append(empty);
    }
    for (const manifest of visible) {
      const button = document.createElement("button");
      button.className = `manifest-entry${manifest.id === state.serverId ? " active" : ""}`;
      const name = document.createElement("strong");
      name.textContent = manifest.name;
      const id = document.createElement("small");
      id.textContent = manifest.id;
      button.append(name, id);
      button.addEventListener("click", () => loadServerGraph(manifest.id));
      el.manifestList.append(button);
    }
  }

  // Refresh manifests from the server
  async function refreshManifests(isQuiet = false) {
    if (isPublicSite) return;
    try {
      const result = await requestJSON("/api/manifests");
      state.manifests = Array.isArray(result.manifests) ? result.manifests : [];
      drawManifestList();
      if (!isQuiet) setStatus(`Found ${state.manifests.length} shared graph manifest(s)`, "success");
    } catch (error) {
      setStatus(error.message, "error");
    }
  }

  // Load a manifest into the editor
  function loadManifest(manifest, origin, serverId = null, isGameGraph = false) {
    state.manifest = ensureManifest(deepCopy(manifest));
    state.serverId = serverId;
    state.isGameGraph = isGameGraph;
    state.storedGraphId = storedGraphs()[0].GraphId;
    state.activeGraphKey = "main";
    state.latestPlugin = null;
    state.isDirty = false;
    el.manifestName.value = state.manifest.displayName
      || selectedGraph()?.Name || serverId || "Contraption Graph";
    el.manifestOrigin.textContent = origin;
    drawGraphSelect();
    drawFunctionTabs();
    drawManifestList();
    loadActiveGraph();
    setStatus(`Loaded ${el.manifestName.value}`, "success");
  }

  // Pull the controller currently open in-game into the browser without overwriting local edits
  function applyRuntime(snapshot) {
    if (!state.isGameGraph || !state.studio || !state.latestPlugin) return;
    const runtime = snapshot?.runtime && typeof snapshot.runtime === "object"
      ? snapshot.runtime : {};
    state.studio.setPluginState(state.latestPlugin.id, runtime);
  }

  // Get the graph opened in game
  async function requestGameGraph(shouldLoadGraph = false) {
    if (isPublicSite || state.currentPoll) return null;
    state.currentPoll = 1;
    try {
      const snapshot = await requestJSON("/api/current-graph");
      const available = Boolean(snapshot.available && snapshot.manifest);
      if (available !== state.isGameGraphAvailable) {
        state.isGameGraphAvailable = available;
        drawManifestList();
      }
      if (!available) return snapshot;
      const fingerprint = Number(snapshot.fingerprint);
      if (shouldLoadGraph || state.isGameGraph
          && state.currentGameFingerprint !== fingerprint && !state.isDirty) {
        state.currentGameFingerprint = fingerprint;
        loadManifest(snapshot.manifest, "Currently Open Graph · live from game", null, true);
      }
      applyRuntime(snapshot);
      return snapshot;
    } catch {
      if (state.isGameGraphAvailable) {
        state.isGameGraphAvailable = false;
        drawManifestList();
      }
      return null;
    } finally {
      state.currentPoll = 0;
    }
  }

  // Load the graph opened in game
  async function loadGameGraph() {
    await settleAndSave();
    setStatus("Loading the graph currently open in game…");
    const snapshot = await requestGameGraph(true);
    if (!snapshot?.available) setStatus("No controller graph is currently open", "error");
  }

  // Load a graph from the server
  async function loadServerGraph(id) {
    try {
      await settleAndSave();
      setStatus(`Loading ${id}…`);
      const manifest = await requestJSON(endpoints.manifest(id));
      loadManifest(manifest, `Server manifest · ${id}`, id);
    } catch (error) {
      setStatus(error.message, "error");
    }
  }

  // Save the graph to the server
  async function saveServer() {
    if (!state.manifest) return;
    try {
      await settleAndSave();
      syncManifest();
      const name = el.manifestName.value.trim() || "Contraption Graph";
      const opts = {
        method: state.serverId ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(state.manifest)
      };
      const url = state.serverId
        ? `/api/manifests/${encodeURIComponent(state.serverId)}`
        : `/api/manifests?name=${encodeURIComponent(name)}&mode=increment`;
      setStatus("Writing manifest to shared_graphs…");
      const result = await requestJSON(url, opts);
      state.serverId = result.id;
      state.isDirty = false;
      el.manifestOrigin.textContent = `Server manifest · ${result.id}`;
      await refreshManifests(true);
      drawManifestList();
      updateStats();
      setStatus(`Saved ${result.id} to shared_graphs`, "success");
    } catch (error) {
      setStatus(error.message, "error");
    }
  }

  /*--------------------------------------------------------##---------------------------------------------------------

  =======================================================================================================================
                                                   IMPORT / EXPORT
  =======================================================================================================================

  ------------------------------------------------------------##-----------------------------------------------------*/

  // Import and export standalone manifest files
  async function uploadManifest(file) {
    try {
      const text = await file.text();
      const manifest = ensureManifest(JSON.parse(text));
      const name = manifest.displayName || file.name.replace(/\.json$/i, "");
      const result = await requestJSON(
        `/api/manifests?name=${encodeURIComponent(name)}&mode=increment`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(manifest)
        });
      await refreshManifests(true);
      await loadServerGraph(result.id);
      setStatus(`Uploaded ${file.name} as ${result.id}`, "success");
    } catch (error) {
      setStatus(error.message, "error");
    }
  }

  // Open a local manifest file
  async function openManifest(file) {
    try {
      await settleAndSave();
      const text = await file.text();
      loadManifest(JSON.parse(text), `Local file · ${file.name}`);
    } catch (error) {
      setStatus(error.message, "error");
    }
  }

  // Download the current manifest
  async function downloadManifest() {
    if (!state.manifest) return;
    await settleAndSave();
    syncManifest();
    const blob = new Blob([JSON.stringify(state.manifest, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    const safeName = (el.manifestName.value || "contraption_graph")
      .trim().replace(/[^a-z0-9._-]+/gi, "_").replace(/^_+|_+$/g, "") || "contraption_graph";
    link.href = url;
    link.download = `${safeName}.json`;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    setStatus(`Downloaded ${link.download}`, "success");
  }

  // Publish a graph when the public sharing endpoint is available
  async function checkShare() {
    try {
      const status = await requestJSON(isPublicSite ? endpoints.status : "/api/share/status");
      el.share.hidden = !status.available;
      return Boolean(status.available);
    } catch {
      el.share.hidden = true;
      return false;
    }
  }

  // Share the current graph
  async function shareGraph() {
    if (!state.manifest) return;
    el.share.disabled = true;
    try {
      await settleAndSave();
      syncManifest();
      setStatus("Uploading graph for public sharing…");
      const result = await requestJSON(endpoints.share, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(state.manifest)
      });
      state.publicGraphId = String(result.id || "");
      if (isPublicSite && state.publicGraphId) {
        el.installGame.hidden = false;
      }
      const url = String(result.url || "");
      if (url) {
        let copied = false;
        if (navigator.clipboard?.writeText) {
          try {
            await navigator.clipboard.writeText(url);
            copied = true;
          } catch {
          }
        }
        if (!copied) window.prompt("Copy this graph link", url);
      }
      setStatus(url ? `Share link copied · ${url}` : "Graph uploaded", "success");
    } catch (error) {
      setStatus(error.message, "error");
      if (!isPublicSite) el.share.hidden = true;
    } finally {
      el.share.disabled = false;
    }
  }

  // Install the shared graph in game
  function installGraph() {
    if (!/^[a-f0-9]{16}$/.test(state.publicGraphId)) return;
    window.location.assign(
      `http://127.0.0.1:48574/api/import-remote/${encodeURIComponent(state.publicGraphId)}`);
  }

  // Create a new local manifest
  function newManifest() {
    const name = "New Contraption Graph";
    loadManifest(blankManifest(name), "Unsaved local document");
  }

  /*--------------------------------------------------------##---------------------------------------------------------

  =======================================================================================================================
                                                     PRELOAD / SETUP
  =======================================================================================================================

  ------------------------------------------------------------##-----------------------------------------------------*/

  // Wire toolbar controls after every required element has been found
  function bindUI() {
    el.addFunction.addEventListener("click", addFunction);
    el.download.addEventListener("click", downloadManifest);
    el.filter.addEventListener("input", drawManifestList);
    el.graphSelect.addEventListener("change", async () => {
      if (state.isSwitching) return;
      await settleAndSave();
      state.storedGraphId = el.graphSelect.value;
      state.activeGraphKey = "main";
      drawFunctionTabs();
      loadActiveGraph();
    });
    el.manifestName.addEventListener("input", () => {
      if (!state.manifest) return;
      state.isDirty = true;
      syncManifest();
      updateStats();
    });
    el.newManifest.addEventListener("click", async () => {
      await settleAndSave();
      newManifest();
    });
    el.open.addEventListener("click", () => {
      state.fileIntent = "open";
      el.fileInput.value = "";
      el.fileInput.click();
    });
    el.refresh.addEventListener("click", () => refreshManifests());
    el.saveServer.addEventListener("click", saveServer);
    el.share.addEventListener("click", shareGraph);
    el.installGame.addEventListener("click", installGraph);
    el.upload.addEventListener("click", () => {
      state.fileIntent = "upload";
      el.fileInput.value = "";
      el.fileInput.click();
    });
    el.fileInput.addEventListener("change", () => {
      const file = el.fileInput.files?.[0];
      if (!file) return;
      if (state.fileIntent === "upload") uploadManifest(file);
      else openManifest(file);
    });
    document.addEventListener(
      "createthrusters-convert-selection-to-function",
      (event) => void selectionToFunction(event));
    document.addEventListener("createthrusters-open-function", (event) => {
      const functionId = String(event.detail?.functionId || "").trim();
      if (functionId && rootGraph()?.Functions.some((fn) => fn.Id === functionId)) {
        void switchFunction(functionId);
      }
    });
  }

  // Configure the studio once, then keep all saved changes flowing through this page
  function setupStudio() {
    const api = globalThis.RieStatCdnPluginStudio;
    if (!api?.initPluginStudioModule || !api?.configureCreateThrustersNodes) {
      throw new Error("The packaged RieStat graph editor did not load");
    }
    api.configureCreateThrustersNodes(state.catalog);
    const host = {
      __hostVersion: 1,
      logging: {},
      plugins: {},
      telemetry: {},
      customNodes: {}
    };
    state.studio = api.initPluginStudioModule({
      target: el.editor,
      host,
      auto_theme: false,
      style: {
        rootFontFamily: '"Pixelify Sans", "Segoe UI", sans-serif',
        tokens: {
          "--bg-app": "#0d1215",
          "--bg-primary": "#141b1f",
          "--bg-secondary": "#1b2429",
          "--bg-surface": "#182126",
          "--bg-card": "#202a2f",
          "--bg-sidebar": "#11181b",
          "--bg-header": "#192126",
          "--bg-hover": "#2b353a",
          "--text-primary": "#e8e0cd",
          "--text-secondary": "#c7c0b2",
          "--text-muted": "#918c82",
          "--border-color": "#39464c",
          "--border-strong": "#59666b",
          "--accent-color": "#c6873f",
          "--accent-glow": "rgba(198, 135, 63, 0.16)",
          "--accent-glow-strong": "rgba(198, 135, 63, 0.36)",
          "--glass-blur": "0px",
          "--card-shadow": "0 10px 24px rgba(0, 0, 0, 0.32)",
          "--ui-radius-lg": "4px",
          "--ui-radius-md": "3px",
          "--ui-radius-sm": "2px",
          "--nav-indicator-glow": "none"
        }
      },
      config: {
        plugins: [],
        pluginId: null,
        sensors: [],
        accentColor: "#c6873f",
        nativeIdeEnabled: false,
        nativeIdeSuggestPopupEnabled: false,
        uiState: {
          viewMode: "nodes",
          isProfiling: false,
          showConfigPanel: true,
          sidebarTab: "nodes"
        },
        onPluginsChange: (plugins) => {
          const next = Array.isArray(plugins) ? plugins[0] : null;
          if (!next) return;
          state.latestPlugin = next;
          if (!state.isLoadingPlugin) {
            state.isDirty = true;
            updateStats();
          }
        }
      }
    });
  }

  /*--------------------------------------------------------##---------------------------------------------------------

  =======================================================================================================================
                                                            MAIN
  =======================================================================================================================

  ------------------------------------------------------------##-----------------------------------------------------*/

  // Load the catalog and initial manifest before handing control to the player
  async function start() {
    bindUI();
    try {
      const [status, catalog] = await Promise.all([
        requestJSON(endpoints.status),
        requestJSON(endpoints.nodes)
      ]);
      state.catalog = Array.isArray(catalog.nodes) ? catalog.nodes : [];
      state.definitions = new Map(state.catalog.map((def) => [def.id, def]));
      setServerState(true, isPublicSite ? "PUBLIC SHARE" : `PORT ${status.port}`);
      setupStudio();
      if (isPublicSite && /^[a-f0-9]{16}$/.test(initialGraphId)) {
        const manifest = await requestJSON(endpoints.manifest(initialGraphId));
        loadManifest(manifest, `Public graph · ${initialGraphId}`);
        el.installGame.hidden = false;
      } else {
        newManifest();
      }
      if (!isPublicSite) {
        await refreshManifests(true);
        await requestGameGraph(false);
        window.setInterval(() => void requestGameGraph(false), 500);
      }
      void checkShare();
      setStatus(`Workshop ready · ${state.catalog.length} controller nodes`, "success");
    } catch (error) {
      setServerState(false, "OFFLINE");
      setStatus(error.message, "error");
      const failure = document.createElement("div");
      const message = document.createElement("span");
      failure.className = "editor-loading";
      message.textContent = error.message;
      failure.append(message);
      el.editor.replaceChildren(failure);
    }
  }

  start();
})();
