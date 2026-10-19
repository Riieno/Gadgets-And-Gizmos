-- Controller-owned shipping schedule bridge. This module deliberately mirrors
-- the ACC graph bridge's revisioned editor workflow while exposing Create
-- schedule instructions, wait conditions and Scratch flow blocks directly.
local M = {}
local Bridge = {}
local Graph = {}
local Node = {}
Bridge.__index = Bridge
Graph.__index = Graph
Node.__index = Node

local function as_id(value)
    if getmetatable(value) == Node then return value.id end
    assert(type(value) == "string" and value ~= "", "schedule block must be a Node handle or id")
    return value
end

local function editable(node)
    assert(node.graph ~= nil, "read-only schedule node; use schedule:edit()")
    return node.graph
end

local function finite(value, name)
    assert(type(value) == "number" and value == value
            and value ~= math.huge and value ~= -math.huge,
            name .. " must be a finite number")
    return value
end

local function schedule_type(value, category)
    assert(type(value) == "string" and value ~= "", "schedule block type is required")
    local prefix = "shipping_schedule:" .. category .. ":"
    if value:sub(1, #prefix) == prefix then return value:sub(#prefix + 1) end
    return value
end

function M.wrap(device, name)
    if type(device) == "string" then
        name = device
        device = peripheral.wrap(device)
    end
    assert(type(device) == "table", "ACC peripheral or peripheral name is required")
    assert(type(device.getScheduleGraph) == "function",
            "peripheral does not expose the SCM schedule bridge")
    return setmetatable({ raw = device, name = name }, Bridge)
end

function M.find()
    local name
    local device
    peripheral.find("advanced_contraption_controller", function(foundName, foundDevice)
        if device ~= nil then return false end
        if type(foundDevice.getScheduleGraph) == "function" then
            name = foundName
            device = foundDevice
            return true
        end
        return false
    end)
    assert(device, "Attach an Advanced Contraption Controller with an SCM schedule workspace")
    return M.wrap(device, name)
end

function Bridge:edit(view)
    view = view or "draft"
    assert(view == "draft", "only the controller-owned schedule draft can be edited")
    local snapshot = self.raw.getScheduleGraph(view)
    return setmetatable({ bridge = self, snapshot = snapshot, revision = snapshot.revision,
        operations = {}, handles = {}, nextTemporaryId = 1 }, Graph)
end

function Bridge:get(view)
    return self.raw.getScheduleGraph(view or "draft")
end

function Bridge:status()
    return self.raw.getScheduleStatus()
end

function Bridge:version()
    return self.raw.getScheduleApiVersion()
end

function Bridge:blockTypes()
    return self.raw.listScheduleBlockTypes()
end

function Bridge:help(method)
    if method == nil then return self.raw.getScheduleApiHelp() end
    return self.raw.getScheduleApiHelp(method)
end

function Bridge:validate()
    return self.raw.validateScheduleGraph()
end

function Bridge:apply(expectedRevision)
    if expectedRevision == nil then expectedRevision = self.raw.getScheduleGraph("draft").revision end
    return self.raw.applyScheduleGraph(expectedRevision)
end

function Bridge:start() return self.raw.startSchedule() end
function Bridge:pause() return self.raw.pauseSchedule() end
function Bridge:resume() return self.raw.resumeSchedule() end
function Bridge:stop() return self.raw.stopSchedule() end
function Bridge:restart() return self.raw.restartSchedule() end
function Bridge:skip() return self.raw.skipSchedule() end
function Bridge:readItem() return self.raw.readScheduleItem() end
function Bridge:writeItem() return self.raw.writeScheduleItem() end

function Graph:_temporary(prefix)
    local id = "$" .. prefix .. self.nextTemporaryId
    self.nextTemporaryId = self.nextTemporaryId + 1
    return id
end

function Graph:_queue(operation)
    self.operations[#self.operations + 1] = operation
    return self
end

function Graph:_newNode(temporary, x, y)
    local node = setmetatable({ graph = self, id = temporary, x = x or 80, y = y or 80 }, Node)
    self.handles[temporary] = node
    return node
end

function Graph:node(id)
    id = as_id(id)
    for _, row in ipairs(self.snapshot.nodes or {}) do
        if row.id == id then
            local node = setmetatable({ graph = self, id = row.id,
                x = row.x, y = row.y, type = row.type, label = row.label }, Node)
            self.handles[row.id] = node
            return node
        end
    end
    error("unknown schedule block '" .. id .. "'", 2)
end

function Graph:addInstruction(instruction, x, y)
    local temporary = self:_temporary("instruction")
    local operation = { op = "append_instruction", id = temporary,
        instruction = schedule_type(instruction, "instruction") }
    if x ~= nil or y ~= nil then
        operation.x, operation.y = finite(x, "x"), finite(y, "y")
    end
    self:_queue(operation)
    return self:_newNode(temporary, x, y)
end

function Graph:addCondition(instruction, condition)
    local temporary = self:_temporary("condition")
    self:_queue({ op = "append_condition", id = temporary,
        instructionId = as_id(instruction), condition = schedule_type(condition, "condition") })
    return self:_newNode(temporary)
end

function Graph:addDetachedCondition(condition, x, y)
    local temporary = self:_temporary("condition")
    self:_queue({ op = "append_detached_condition", id = temporary,
        condition = schedule_type(condition, "condition"), x = finite(x, "x"), y = finite(y, "y") })
    return self:_newNode(temporary, x, y)
end

function Graph:attachCondition(condition, instruction)
    local temporary = self:_temporary("condition")
    self:_queue({ op = "attach_condition", id = temporary,
        conditionId = as_id(condition), instructionId = as_id(instruction) })
    return self:_newNode(temporary)
end

function Graph:moveCondition(condition, instruction)
    local temporary = self:_temporary("condition")
    self:_queue({ op = "move_condition", id = temporary,
        conditionId = as_id(condition), instructionId = as_id(instruction) })
    return self:_newNode(temporary)
end

function Graph:detachCondition(condition, x, y)
    return self:_queue({ op = "detach_condition", nodeId = as_id(condition),
        x = finite(x, "x"), y = finite(y, "y") })
end

function Graph:addFlow(kind, x, y)
    assert(type(kind) == "string" and kind ~= "", "flow kind is required")
    local temporary = self:_temporary("flow")
    self:_queue({ op = "add_flow", id = temporary, kind = kind,
        x = finite(x, "x"), y = finite(y, "y") })
    return self:_newNode(temporary, x, y)
end

function Graph:remove(node)
    return self:_queue({ op = "remove", nodeId = as_id(node) })
end

function Graph:move(node, x, y)
    return self:_queue({ op = "move", nodeId = as_id(node),
        x = finite(x, "x"), y = finite(y, "y") })
end

function Graph:reorderInstruction(node, index)
    assert(type(index) == "number" and index >= 1 and index == math.floor(index),
            "schedule index must be a positive integer")
    return self:_queue({ op = "reorder_instruction", nodeId = as_id(node), index = index })
end

function Graph:setParent(child, parent)
    return self:_queue({ op = "set_parent", childId = as_id(child), parentId = as_id(parent) })
end

function Graph:clearParent(child)
    return self:_queue({ op = "clear_parent", childId = as_id(child) })
end

function Graph:placeChild(child, parent, index)
    assert(type(index) == "number" and index >= 1 and index == math.floor(index),
            "C-block index must be a positive integer")
    return self:_queue({ op = "place_child", childId = as_id(child),
        parentId = as_id(parent), index = index })
end

function Graph:connect(from, to)
    return self:_queue({ op = "connect", fromId = as_id(from), toId = as_id(to) })
end

function Graph:insertBefore(node, before)
    return self:_queue({ op = "insert_before", nodeId = as_id(node), beforeId = as_id(before) })
end

function Graph:setProperty(node, key, value)
    assert(type(key) == "string" and key ~= "", "property key is required")
    assert(value ~= nil, "property value is required")
    return self:_queue({ op = "set_property", nodeId = as_id(node), key = key, value = tostring(value) })
end

function Graph:setInput(node, slot, item, count)
    assert(type(slot) == "number" and slot >= 1 and slot <= 2 and slot == math.floor(slot),
            "input slot must be 1 or 2")
    assert(item == nil or type(item) == "string", "item must be a resource ID or nil")
    count = count == nil and (item == nil and 0 or 1) or count
    assert(type(count) == "number" and (count == 0 or count == 1),
            "a schedule input count must be 0 or 1")
    assert((item == nil and count == 0) or (item ~= nil and count == 1),
            "a schedule input is either empty or exactly one item")
    return self:_queue({ op = "set_input", nodeId = as_id(node), slot = slot,
        item = item or "", count = count })
end

function Graph:setCyclic(value)
    assert(type(value) == "boolean", "cyclic value must be a boolean")
    return self:_queue({ op = "set_cyclic", value = value })
end

function Graph:reload()
    self.snapshot = self.bridge.raw.getScheduleGraph("draft")
    self.revision = self.snapshot.revision
    self.operations = {}
    self.handles = {}
    return self
end

function Graph:commit()
    local result = self.bridge.raw.mutateScheduleGraph(self.revision, self.operations)
    if result.saved then
        for temporary, handle in pairs(self.handles) do
            handle.id = result.resolvedIds[temporary] or handle.id
        end
        self.revision = result.revision
        self.operations = {}
        self.handles = {}
        self.snapshot = self.bridge.raw.getScheduleGraph("draft")
    end
    return result
end

function Node:getId() return self.id end

function Node:move(x, y)
    editable(self):move(self, x, y)
    self.x, self.y = x, y
    return self
end

function Node:remove()
    local graph = editable(self)
    graph:remove(self)
    return graph
end

function Node:setProperty(key, value)
    editable(self):setProperty(self, key, value)
    return self
end

function Node:setInput(slot, item, count)
    editable(self):setInput(self, slot, item, count)
    return self
end

function Node:detach(x, y)
    editable(self):detachCondition(self, x, y)
    self.x, self.y = x, y
    return self
end

function Node:nest(parent, index)
    local graph = editable(self)
    if index == nil then graph:setParent(self, parent) else graph:placeChild(self, parent, index) end
    return self
end

function Node:unnest()
    editable(self):clearParent(self)
    return self
end

function Node:properties()
    local bridge = self.bridge or self.graph.bridge
    return bridge.raw.getScheduleBlockProperties(self.id)
end

function Node:inputs()
    local bridge = self.bridge or self.graph.bridge
    return bridge.raw.getScheduleBlockInputs(self.id)
end

return M
