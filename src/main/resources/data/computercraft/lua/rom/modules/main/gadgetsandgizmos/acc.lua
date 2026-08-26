local M = {}
local Bridge = {}
local Graph = {}
local Node = {}
local FunctionGraph = {}
local Variable = {}
Bridge.__index = Bridge
Graph.__index = Graph
Node.__index = Node
FunctionGraph.__index = FunctionGraph
Variable.__index = Variable

local function as_id(value)
    if getmetatable(value) == Node then return value.id end
    assert(type(value) == "string", "node must be a Node handle or id")
    return value
end

local function editable_node(node)
    assert(node.graph ~= nil,
            "read-only node handle; use acc:edit() to change the draft graph")
    return node.graph
end

local function port_name(value)
    if type(value) == "table" then value = value.port or value.name or value[1] end
    assert(type(value) == "string" and value ~= "", "port name is required")
    return value
end

local function finite_number(value, name)
    assert(type(value) == "number" and value == value
            and value ~= math.huge and value ~= -math.huge,
            name .. " must be a finite number")
    return value
end

local function function_id(scope)
    if type(scope) == "table" and getmetatable(scope) == FunctionGraph then
        return scope.id
    end
    return scope or ""
end

function M.wrap(device, name)
    if type(device) == "string" then
        name = device
        device = peripheral.wrap(device)
    end
    assert(type(device) == "table", "ACC peripheral or peripheral name is required")
    assert(type(device.getGraph) == "function", "peripheral does not expose the ACC graph bridge")
    return setmetatable({ raw = device, name = name }, Bridge)
end

function M.find()
    local name
    local device
    peripheral.find("advanced_contraption_controller",
            function(foundName, foundDevice)
                if device ~= nil then return false end
                name = foundName
                device = foundDevice
                return true
            end)
    assert(device, "Attach an Advanced Contraption Controller")
    return M.wrap(device, name)
end

function Bridge:edit(view)
    view = view or "draft"
    assert(view == "draft", "only the draft can be edited")
    local snapshot = self.raw.getGraph(view)
    return setmetatable({ bridge = self, revision = snapshot.revision,
        snapshot = snapshot, operations = {}, handles = {}, nextTemporaryId = 1 }, Graph)
end

function Bridge:node(idOrAlias)
    assert(type(idOrAlias) == "string" and idOrAlias ~= "",
            "node ID or alias is required")
    local snapshot = self.raw.getGraph("active")
    local aliasMatch
    for _, row in ipairs(snapshot.nodes or {}) do
        if row.id == idOrAlias then
            return setmetatable({ bridge = self, id = row.id,
                alias = row.alias or "", x = row.x, y = row.y,
                readonly = true }, Node)
        end
        if row.alias == idOrAlias then
            assert(aliasMatch == nil,
                    "ambiguous active graph node alias '" .. idOrAlias .. "'")
            aliasMatch = row
        end
    end
    assert(aliasMatch ~= nil,
            "unknown active graph node ID or alias '" .. idOrAlias .. "'")
    return setmetatable({ bridge = self, id = aliasMatch.id,
        alias = aliasMatch.alias or "", x = aliasMatch.x, y = aliasMatch.y,
        readonly = true }, Node)
end

function Bridge:get(view)
    return self.raw.getGraph(view or "draft")
end

function Bridge:status()
    return self.raw.getGraphStatus()
end

function Bridge:version()
    return self.raw.getGraphApiVersion()
end

function Bridge:nodeTypes()
    return self.raw.listGraphNodeTypes()
end

function Bridge:help(method)
    if method == nil then return self.raw.getGraphApiHelp() end
    return self.raw.getGraphApiHelp(method)
end

function Bridge:validate()
    return self.raw.validateGraph()
end

function Bridge:apply(expectedRevision)
    if expectedRevision == nil then
        expectedRevision = self.raw.getGraph("draft").revision
    end
    return self.raw.applyGraph(expectedRevision)
end

function Bridge:trigger(eventId)
    assert(type(eventId) == "string" and eventId ~= "", "event id is required")
    return self.raw.tryTriggerGraphEvent(eventId)
end

function Bridge:exportLua(view, options)
    return self.raw.exportGraphLua(view or "active", options or {})
end

function Bridge:variable(name)
    assert(type(name) == "string" and name ~= "", "variable name is required")
    return setmetatable({ bridge = self, name = name }, Variable)
end

function Bridge:publish(name, data, maximumDistance)
    assert(type(name) == "string" and name ~= "", "named event name is required")
    if data == nil then data = 0 end
    return self.raw.publishNamedEvent(name, data, maximumDistance)
end

function Bridge:receive(name, data)
    assert(type(name) == "string" and name ~= "", "named event name is required")
    if data == nil then data = 0 end
    return self.raw.receiveNamedEvent(name, data)
end

function Graph:addNode(nodeType, x, y, data, alias)
    return self:_addNode("", nodeType, x, y, data, alias)
end

function Graph:node(id)
    return self:_node("", id)
end

function Graph:_node(scope, id)
    assert(type(id) == "string" and id ~= "", "node ID or alias is required")
    local scopeId = function_id(scope)
    local rows = self.snapshot.nodes
    if scopeId ~= "" then
        rows = nil
        for _, fn in ipairs(self.snapshot.functions or {}) do
            if fn.id == scopeId then rows = fn.nodes break end
        end
        assert(rows, "unknown function graph '" .. scopeId .. "'")
    end
    local aliasMatch
    for _, row in ipairs(rows or {}) do
        if row.id == id then
            local node = setmetatable({ graph = self, scope = scope,
                id = row.id, alias = row.alias or "", x = row.x, y = row.y }, Node)
            self.handles[row.id] = node
            return node
        end
        if row.alias == id then
            assert(aliasMatch == nil, "ambiguous node alias '" .. id .. "'")
            aliasMatch = row
        end
    end
    if aliasMatch ~= nil then
        local node = setmetatable({ graph = self, scope = scope,
            id = aliasMatch.id, alias = aliasMatch.alias or "",
            x = aliasMatch.x, y = aliasMatch.y }, Node)
        self.handles[aliasMatch.id] = node
        return node
    end
    error("unknown node ID or alias '" .. id .. "'", 2)
end

function Graph:_addNode(scope, nodeType, x, y, data, alias)
    assert(type(nodeType) == "string" and nodeType ~= "", "node type is required")
    assert(alias == nil or type(alias) == "string", "node alias must be a string")
    finite_number(x, "x")
    finite_number(y, "y")
    local temporaryId = "$" .. self.nextTemporaryId
    self.nextTemporaryId = self.nextTemporaryId + 1
    self.operations[#self.operations + 1] = {
        op = "add_node", id = temporaryId, type = nodeType,
        functionId = function_id(scope), x = x, y = y,
        data = data or {}, alias = alias or ""
    }
    local node = setmetatable({ graph = self, scope = scope,
        id = temporaryId, alias = alias or "", x = x, y = y }, Node)
    self.handles[temporaryId] = node
    return node
end

function Graph:wire(fromNode, fromPort, toNode, toPort)
    return self:_wire("", fromNode, fromPort, toNode, toPort)
end

function Graph:_wire(scope, fromNode, fromPort, toNode, toPort)
    local selectedScope = function_id(scope)
    if getmetatable(fromNode) == Node then
        assert(function_id(fromNode.scope) == selectedScope,
                "source node belongs to a different graph scope")
    end
    if getmetatable(toNode) == Node then
        assert(function_id(toNode.scope) == selectedScope,
                "target node belongs to a different graph scope")
    end
    self.operations[#self.operations + 1] = {
        op = "add_edge", id = "$edge" .. (#self.operations + 1),
        functionId = selectedScope,
        fromNode = as_id(fromNode), fromPort = port_name(fromPort),
        toNode = as_id(toNode), toPort = port_name(toPort)
    }
    return toNode
end

function Graph:removeNode(node)
    return self:_removeNode(getmetatable(node) == Node and node.scope or "", node)
end

function Graph:_removeNode(scope, node)
    self.operations[#self.operations + 1] = { op = "remove_node",
        functionId = function_id(scope),
        nodeId = as_id(node) }
    return self
end

function Graph:moveNode(node, x, y)
    return self:_moveNode(getmetatable(node) == Node and node.scope or "", node, x, y)
end

function Graph:_moveNode(scope, node, x, y)
    finite_number(x, "x")
    finite_number(y, "y")
    self.operations[#self.operations + 1] = {
        op = "move_node",
        functionId = function_id(scope),
        nodeId = as_id(node), x = x, y = y
    }
    return self
end

function Graph:renameNode(node, label)
    local scope = getmetatable(node) == Node and node.scope or ""
    return self:_renameNode(scope, node, label)
end

function Graph:_renameNode(scope, node, label)
    assert(type(label) == "string", "node label must be a string")
    self.operations[#self.operations + 1] = { op = "rename_node",
        functionId = function_id(scope), nodeId = as_id(node), label = label }
    return self
end

function Graph:setNodeAlias(node, alias)
    local scope = getmetatable(node) == Node and node.scope or ""
    return self:_setNodeAlias(scope, node, alias)
end

function Graph:_setNodeAlias(scope, node, alias)
    assert(type(alias) == "string", "node alias must be a string")
    self.operations[#self.operations + 1] = { op = "set_node_alias",
        functionId = function_id(scope), nodeId = as_id(node), alias = alias }
    if getmetatable(node) == Node then node.alias = alias end
    return self
end

function Graph:setNodeData(node, key, value)
    local scope = getmetatable(node) == Node and node.scope or ""
    return self:_setNodeData(scope, node, key, value)
end

function Graph:_setNodeData(scope, node, key, value)
    assert(type(key) == "string" and key ~= "", "node data key is required")
    assert(value ~= nil, "use removeNodeData to remove a key")
    self.operations[#self.operations + 1] = { op = "set_node_data",
        functionId = function_id(scope), nodeId = as_id(node),
        key = key, value = value }
    return self
end

function Graph:removeNodeData(node, key)
    local scope = getmetatable(node) == Node and node.scope or ""
    return self:_removeNodeData(scope, node, key)
end

function Graph:_removeNodeData(scope, node, key)
    assert(type(key) == "string" and key ~= "", "node data key is required")
    self.operations[#self.operations + 1] = { op = "remove_node_data",
        functionId = function_id(scope), nodeId = as_id(node), key = key }
    return self
end

function Graph:unwire(edgeId)
    return self:_unwire("", edgeId)
end

function Graph:_unwire(scope, edgeId)
    assert(type(edgeId) == "string" and edgeId ~= "", "edge id is required")
    self.operations[#self.operations + 1] = { op = "remove_edge",
        functionId = function_id(scope), edge = edgeId }
    return self
end

function Graph:setVariable(name, value)
    assert(type(name) == "string" and name ~= "", "variable name is required")
    assert(value ~= nil, "use removeVariable to remove a variable")
    self.operations[#self.operations + 1] = {
        op = "set_variable", name = name, value = value }
    return self
end

function Graph:variable(name)
    return self.bridge:variable(name)
end

function Graph:removeVariable(name)
    assert(type(name) == "string" and name ~= "", "variable name is required")
    self.operations[#self.operations + 1] = {
        op = "remove_variable", name = name }
    return self
end

function Graph:addFunction(name)
    assert(type(name) == "string" and name ~= "", "function name is required")
    local temporaryId = "$function" .. self.nextTemporaryId
    self.nextTemporaryId = self.nextTemporaryId + 1
    self.operations[#self.operations + 1] = {
        op = "add_function", id = temporaryId, name = name
    }
    local fn = setmetatable({ graph = self, id = temporaryId, name = name }, FunctionGraph)
    self.handles[temporaryId] = fn
    return fn
end

function Graph:functionGraph(id)
    assert(type(id) == "string" and id ~= "", "function id is required")
    return setmetatable({ graph = self, id = id }, FunctionGraph)
end

function Graph:renameFunction(fn, name)
    local id = function_id(fn)
    assert(id ~= "", "function graph is required")
    assert(type(name) == "string" and name ~= "", "function name is required")
    self.operations[#self.operations + 1] = {
        op = "rename_function", functionId = id, name = name
    }
    if getmetatable(fn) == FunctionGraph then fn.name = name end
    return self
end

function Graph:removeFunction(fn)
    local id = function_id(fn)
    assert(id ~= "", "function graph is required")
    self.operations[#self.operations + 1] = {
        op = "remove_function", functionId = id
    }
    return self
end

function Graph:reload()
    self.snapshot = self.bridge.raw.getGraph("draft")
    self.revision = self.snapshot.revision
    self.operations = {}
    self.handles = {}
    return self
end

function Graph:commit()
    local result = self.bridge.raw.mutateGraph(self.revision, self.operations)
    if result.saved then
        for temporaryId, handle in pairs(self.handles) do
            handle.id = result.resolvedIds[temporaryId] or handle.id
        end
        self.revision = result.revision
        self.operations = {}
        self.handles = {}
        self.snapshot = self.bridge.raw.getGraph("draft")
    end
    return result
end

function Node:wire(fromPort, target, toPort, x, y, data, alias)
    local graph = editable_node(self)
    if type(target) == "string" then
        target = graph:_addNode(self.scope, target,
                x or (self.x + 180), y or self.y, data, alias)
    end
    graph:_wire(self.scope, self, fromPort, target, toPort)
    return target
end

function FunctionGraph:addNode(nodeType, x, y, data, alias)
    return self.graph:_addNode(self, nodeType, x, y, data, alias)
end

function FunctionGraph:node(id)
    return self.graph:_node(self, id)
end

function FunctionGraph:wire(fromNode, fromPort, toNode, toPort)
    return self.graph:_wire(self, fromNode, fromPort, toNode, toPort)
end

function FunctionGraph:unwire(edgeId)
    return self.graph:_unwire(self, edgeId)
end

function FunctionGraph:moveNode(node, x, y)
    return self.graph:_moveNode(self, node, x, y)
end

function FunctionGraph:removeNode(node)
    return self.graph:_removeNode(self, node)
end

function FunctionGraph:renameNode(node, label)
    return self.graph:_renameNode(self, node, label)
end

function FunctionGraph:setNodeData(node, key, value)
    return self.graph:_setNodeData(self, node, key, value)
end

function FunctionGraph:setNodeAlias(node, alias)
    return self.graph:_setNodeAlias(self, node, alias)
end

function FunctionGraph:removeNodeData(node, key)
    return self.graph:_removeNodeData(self, node, key)
end

function FunctionGraph:rename(name)
    self.graph:renameFunction(self, name)
    return self
end

function FunctionGraph:remove()
    return self.graph:removeFunction(self)
end

function Node:move(x, y)
    editable_node(self):moveNode(self, x, y)
    self.x, self.y = x, y
    return self
end

function Node:remove()
    local graph = editable_node(self)
    graph:removeNode(self)
    return graph
end

function Node:rename(label)
    editable_node(self):_renameNode(self.scope, self, label)
    return self
end

function Node:setData(key, value)
    editable_node(self):_setNodeData(self.scope, self, key, value)
    return self
end

function Node:setAlias(alias)
    editable_node(self):_setNodeAlias(self.scope, self, alias)
    return self
end

function Node:getAlias()
    return self.alias or ""
end

function Node:getId()
    return self.id
end

function Node:getData()
    assert(function_id(self.scope) == "",
            "function template nodes do not have one live runtime instance")
    assert(self.id:sub(1, 1) ~= "$",
            "commit and apply the node before reading its live data")
    local bridge = self.bridge or self.graph.bridge
    return bridge.raw.getGraphNodeOutputs(self.id)
end

function Node:removeData(key)
    editable_node(self):_removeNodeData(self.scope, self, key)
    return self
end

function Variable:get()
    return self.bridge.raw.getGraphVariable(self.name)
end

function Variable:set(value)
    assert(value ~= nil, "graph variable value is required")
    self.bridge.raw.setGraphVariable(self.name, value)
    return self
end

return M
