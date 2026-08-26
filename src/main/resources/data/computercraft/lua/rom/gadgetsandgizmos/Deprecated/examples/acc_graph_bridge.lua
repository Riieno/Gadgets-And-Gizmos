local acc = require("gadgetsandgizmos.acc").find()

print("ACC graph API v" .. acc:version())

local graph = acc:edit()
graph:setVariable("answer", 0)
local ready = graph:addNode("event_graph_ready", 80, 80)
local value = graph:addNode("constant_number", 80, 220, { Value = 42 })
local variable = graph:addNode("variable_set", 320, 80, { Variable = "answer" })

ready:wire("exec", variable, "exec")
value:wire("value", variable, "value")

local result = graph:commit()
if not result.saved then
    error(result.code .. ": " .. result.message, 0)
end

local validation = acc:validate()
if not validation.valid then
    textutils.pretty_print(validation.diagnostics)
    error("The graph draft is invalid", 0)
end

local applied = acc:apply(result.revision)
if not applied.applied then
    error(applied.code .. ": " .. applied.message, 0)
end

print("Graph saved and applied at revision " .. applied.revision)

local answer = graph:variable("answer")
print("Active answer: " .. answer:get())
answer:set(84)
print("Updated answer: " .. answer:get())
