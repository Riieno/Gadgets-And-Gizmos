Welcome to the Gadgets & Gizmos CC:Tweaked wiki!
# Gadgets & Gizmos CC:Tweaked API

The Gadgets & Gizmos CC:Tweaked integration exposes blocks as ordinary CC peripherals and supplies a high-level Lua bridge for reading, editing, validating, applying and exporting Advanced Contraption Controller graphs.

This page specifies the supported CC:Tweaked surface for Gadgets & Gizmos `1.2.x`. Use the documented Lua methods and bundled modules instead of depending on Java implementation classes or CC:Tweaked internals.

## Contents

- [Supported versions](#supported-versions)
- [Starting with CC:Tweaked](#starting-with-cctweaked)
- [Built-in documentation](#built-in-documentation)
- [API rules](#api-rules)
- [Peripheral types](#peripheral-types)
- [Advanced Contraption Controller](#advanced-contraption-controller)
- [SCM shipping schedule bridge](#scm-shipping-schedule-bridge)
- [SCM navigation and brain data](#scm-navigation-and-brain-data)
- [Graph snapshots](#graph-snapshots)
- [Editing a graph](#editing-a-graph)
- [Graph handles](#graph-handles)
- [Graph variables](#graph-variables)
- [Validation and revisions](#validation-and-revisions)
- [Lua graph export](#lua-graph-export)
- [Named Events](#named-events)
- [Rednet Named Event bus](#rednet-named-event-bus)
- [ACC Displays and remote terminals](#acc-displays-and-remote-terminals)
- [Other peripheral families](#other-peripheral-families)
- [Complete examples](#complete-examples)
- [Compatibility and failure behaviour](#compatibility-and-failure-behaviour)
- [Bundled files](#bundled-files)

## Supported versions

| Component | Supported version |
| --- | --- |
| Gadgets & Gizmos | `1.2.x` |
| Graph bridge API | `4` |
| SCM schedule bridge API | `1` |
| Minecraft | `1.21.1` |
| Java | `21` |
| NeoForge | `21.1.225` or newer compatible version |
| CC:Tweaked | `1.118.x` |
| Create | `6.0.10` or newer compatible version |
| Synaxis | Optional, `1.5.0` with LDLib2 `2.2.17` |

The addon checks optional blocks and integrations at runtime. A peripheral backed by an optional mod is available only when that mod and its target block entity are present.

## Starting with CC:Tweaked

Attach a wired modem, place a computer beside the block, or otherwise expose the block through CC:Tweaked's peripheral network. Use `peripheral.getNames()` and `peripheral.getType(...)` when the attachment name is not known.

```lua
for _, name in ipairs(peripheral.getNames()) do
    print(name, peripheral.getType(name))
end
```

Find the first peripheral of a known type:

```lua
local thruster = peripheral.find("thruster")
assert(thruster, "Attach a thruster")

thruster.setControlMode("computer")
thruster.setThrottle(0.5)
textutils.pretty_print(thruster.getStatus())
```

Use the attachment name when more than one matching peripheral is present:

```lua
local left = peripheral.wrap("left_thruster_0")
local right = peripheral.wrap("right_thruster_0")

left.setThrottle(0.35)
right.setThrottle(0.35)
```

CC:Tweaked wrapped peripheral functions are already bound to their target. Call `device.method(...)`; do not pass `device` as the first argument.

## Built-in documentation

Run the bundled documentation browser from a CC computer:

```text
ctdocs
```

Search immediately by passing words:

```text
ctdocs graph variable
```

The browser is generated from the annotations beside the Java methods which implement the Lua API. The build fails when that checked-in catalogue is stale or an addon Lua method has no documentation entry. It also lists peripherals currently attached to the computer.

Most Gadgets & Gizmos peripherals expose the following common methods:

| Method | Contract |
| --- | --- |
| `methods()` | Returns stable documented method signatures for that peripheral. |
| `help()` | Returns a method-name to help-text table. |
| `help(method)` | Returns help for one method and errors when the method is unknown. |

Use `peripheral.getMethods(name)` for the actual methods exposed by the installed CC:Tweaked runtime. Use `ctdocs` for their documented arguments, results and failure behaviour.

## API rules

### Logical side and authority

- Peripheral calls which mutate a block run on the owning Minecraft server thread.
- The ACC active graph, graph variables and Named Events are server-authoritative.
- A Lua graph editor changes the draft first. It does not replace the active graph until validation and apply succeed.
- ACC Display input is returned to the server before it is forwarded to a CC computer.
- Do not treat a client screen, tablet or monitor render as control authority.

### Values and ranges

- Angles are degrees unless a method name explicitly says `Radians`.
- Normalized control values normally use `0.0` through `1.0`.
- Redstone signals use `0` through `15`.
- Positions use `{ x = number, y = number, z = number }`.
- Resource IDs and item frequencies use namespaced strings such as `"minecraft:redstone"`.
- Graph numbers must be finite. NaN and positive or negative infinity are rejected.
- Passing an unavailable target, unknown identifier, invalid range or malformed graph value raises a Lua error unless the method is explicitly a `try...` operation.

### Tables crossing the graph boundary

The graph bridge bounds incoming tables to a maximum depth of 16 and a total of 1,024 entries. A single atomic mutation request can contain at most 512 operations. Cyclic tables, sparse arrays and mixed unsafe values are rejected.

Those limits apply before the graph is mutated. A rejected request leaves the draft unchanged.

## Peripheral types

The current integration documents these type strings. Prefix entries match every type beginning with that prefix.

| Type | Purpose |
| --- | --- |
| `advanced_contraption_controller` | ACC controller controls, live graph runtime and graph editing bridge |
| `analogue_contraption_controller` | Named controller inputs, bindings and direct targets |
| `monitor` | ACC Display or Universal Display Adapter terminal surface |
| `thruster` | Single thruster control and telemetry |
| `rcs_thruster` | RCS thruster control and telemetry |
| `thruster_bearing` | Bearing and attached-thruster fleet control |
| `vector_bearing` | Vector bearing head control and telemetry |
| `aileron_bearing` | Aileron bearing head control and telemetry |
| `servo_bearing` | Optional Aeroworks servo bearing bridge |
| `bidirectional_gearbox` | Bidirectional gearbox control |
| `analogue_joystick` | Analogue joystick state |
| `advanced_data_link` / `gyroscope_link` | Advanced data and gyroscope link state |
| `virtual_orientation_source` | Virtual orientation source control |
| `claw` | Claw signal and connector selection |
| `rope_winch_cable` | Claw access through a Simulated rope winch cable |
| `navigation_table` | Simulated navigation table slots and target telemetry |
| `wheel_mount` | Optional Offroad wheel control |
| `simulated_throttle_lever` | Simulated throttle lever bridge |
| `directional_gearshift` | Simulated directional gearshift control |
| `vector_thruster` / `liquid_vector_thruster` | Optional propulsion vector thruster bridge |
| `create_connected_*` | Create Connected block family bridge |
| External machine types | Optional Simulated and Aeronautics machine bridges |

External machine types currently include `laser_pointer`, `laser_sensor`, `analogue_transmission`, `redstone_accumulator`, `redstone_inductor`, `redstone_magnet`, `optical_sensor`, `docking_connector`, `altitude_sensor`, `hot_air_burner`, `steam_vent` and `mounted_potato_cannon`.

The complete method list is intentionally generated rather than duplicated by hand. Run `ctdocs <type or method>` inside CC:Tweaked to inspect every installed method.

### Vector Bearing stabilization

ACC Set Data accepts `stabilize_axis` as `X Axis`, `Y Axis`, `Z Axis`, `XZ Axis`, `XY Axis` or `ZY Axis`; `keep_stable` enables the selected world-space axis or plane. The `Max Tilt` setting limits the combined stabilization correction and manual head offset, so a bearing follows its parent once the required correction exceeds that angle. The `vector_bearing` peripheral exposes the same values through `getStabilizeAxis()` and `setStabilizeAxis(...)`. Existing saved single-axis selections continue to load as their matching single-axis option.

## Advanced Contraption Controller

The `advanced_contraption_controller` peripheral inherits the normal analogue controller surface and adds the graph bridge. Existing controller scripts can continue to use input, channel, alias, key, binding and direct-target methods.

Find and wrap the ACC with the bundled module:

```lua
local acc = require("gadgetsandgizmos.acc").find()

print("Graph API version: " .. acc:version())
textutils.pretty_print(acc:status())
```

Wrap a specific attachment when several ACCs are available:

```lua
local accModule = require("gadgetsandgizmos.acc")
local acc = accModule.wrap("advanced_contraption_controller_3")
```

`gadgetsandgizmos.acc` is the canonical module name. The bundled aliases all return the same module table:

```lua
local canonical = require("gadgetsandgizmos.acc")
local short = require("gng.acc")
local gadgets = require("gadgets.acc")
local gizmos = require("gizmos.acc")
```

Lua and CC:Tweaked use a dot as the module path separator. Use `require("gng.acc")`, not `require("gng:.acc")`; a colon is not a valid bundled resource-module path. The legacy `createthrusters.acc` name remains as a compatibility loader for existing computers, but new programs should use the canonical name or one of the four aliases above.

The module returns high-level handles. The raw CC peripheral remains available as `acc.raw`.

### Raw graph methods

| Method | Contract |
| --- | --- |
| `getGraphApiVersion()` | Returns the integer graph bridge version. |
| `listGraphApiMethods()` | Returns the stable graph method signatures. |
| `getGraphApiHelp([method])` | Returns all graph help or one method's help. |
| `getGraph([view])` | Returns the `draft` or `active` graph snapshot; default is `draft`. |
| `getGraphNodeOutputs(nodeIdOrAlias)` | Returns one active root node's current live data outputs, keyed by port name. |
| `listGraphNodeTypes()` | Returns registered node IDs, categories, ports and stateful flags. |
| `mutateGraph(expectedRevision, operations)` | Applies one bounded atomic mutation batch to the draft. |
| `validateGraph()` | Validates the current draft and returns a structured edit result. |
| `applyGraph(expectedRevision)` | Applies a valid draft only when its revision matches. |
| `exportGraphLua(view, [options])` | Exports `draft` or `active` as bounded pure Lua source. |
| `getGraphStatus()` | Returns draft revision, active revision, API version, template and validity. |
| `listGraphVariables()` | Returns active graph variable names. |
| `getGraphVariable(name)` | Reads one active runtime variable. |
| `setGraphVariable(name, value)` | Updates one active runtime variable immediately. |
| `publishNamedEvent(name, data, [maximumDistance])` | Publishes through the ACC Named Event bus, installed shared transports and optional CC rednet integration. |
| `receiveNamedEvent(name, data)` | Delivers an external event to this ACC without retransmission. |
| `triggerGraphEvent(eventId)` | Queues a graph trigger without reporting whether a listener exists. |
| `tryTriggerGraphEvent(eventId)` | Queues a graph trigger and reports whether it was accepted. |
| `getGraphDiagnostics()` | Returns active graph runtime diagnostics. |
| `validateDraft()` | Compatibility boolean validation method. |
| `applyDraft()` | Compatibility boolean apply method. |

The graph bridge version is `4`. Scripts can reject an unsupported version before changing a graph:

```lua
local acc = require("gadgetsandgizmos.acc").find()
assert(acc:version() >= 4, "This program requires ACC graph API 4")
```

### High-level bridge methods

| Method | Contract |
| --- | --- |
| `acc:edit([view])` | Creates a draft `Graph` editor; only `draft` is editable. |
| `acc:node(idOrAlias)` | Returns a read-only active root-node handle for live output reads. |
| `acc:get([view])` | Returns a raw `draft` or `active` snapshot. |
| `acc:status()` | Returns graph revision, API and validity status. |
| `acc:version()` | Returns the graph API version. |
| `acc:nodeTypes()` | Returns the registered node definitions. |
| `acc:help([method])` | Returns graph API help. |
| `acc:validate()` | Returns the structured draft validation result. |
| `acc:apply([expectedRevision])` | Applies the current draft revision or the supplied revision. |
| `acc:trigger(eventId)` | Attempts to trigger one graph event and returns a boolean. |
| `acc:exportLua([view], [options])` | Exports one graph view; default is `active`. |
| `acc:variable(name)` | Returns a live active variable handle. |
| `acc:publish(name, [data], [maximumDistance])` | Publishes a new ACC Named Event. |
| `acc:receive(name, [data])` | Delivers a transport event to this ACC without retransmission. |

`acc.raw` is the wrapped CC peripheral. Use it when a script needs a raw method which the high-level module does not wrap.

## SCM shipping schedule bridge

The Advanced Contraption Controller also exposes its controller-owned Shipping Schedule Scratch graph.
This is a separate API from the normal ACC control graph and is available through `acc.raw` or a directly
wrapped `advanced_contraption_controller` peripheral.

| Method | Contract |
| --- | --- |
| `getScheduleApiVersion()` | Returns schedule bridge version `1`. |
| `listScheduleApiMethods()` | Returns the stable schedule method signatures. |
| `getScheduleApiHelp([method])` | Returns all schedule help or one method's help. |
| `getScheduleGraph([view])` | Returns the `draft` or `active` controller-owned schedule graph; default is `draft`. |
| `listScheduleBlockTypes()` | Lists installed Create instructions, wait conditions and Scratch flow blocks. |
| `getScheduleStatus()` | Returns workspace/pilot state, revisions and current route telemetry. |
| `getScheduleBlockProperties(nodeId)` | Returns one draft block's editable string properties. |
| `getScheduleBlockInputs(nodeId)` | Returns one draft block's one-based item/frequency input slots. |
| `mutateScheduleGraph(expectedRevision, operations)` | Applies up to 512 ordered operations atomically. |
| `validateScheduleGraph()` | Reports whether the draft contains executable schedule steps. |
| `applyScheduleGraph(expectedRevision)` | Confirms the revision; successful saves are already active. |
| `startSchedule()` | Starts or restarts the controller-owned graph without requiring a schedule item. |
| `pauseSchedule()` / `resumeSchedule()` | Pauses or resumes the installed runtime. |
| `stopSchedule()` / `restartSchedule()` | Stops and resets, or restarts, from the first step. |
| `skipSchedule()` | Skips the current step. |
| `readScheduleItem()` | Explicitly imports the adjacent pilot's held schedule. |
| `writeScheduleItem()` | Explicitly exports the controller-owned graph to the held schedule. |

A schedule snapshot contains `version`, `revision`, `template`, `cyclic`, `nodes` and `edges`. Each node
contains `id`, `type`, `label`, `x`, `y`, `parentId`, `cBlock`, `properties` and `inputs`. Each input row
contains one-based `slot`, `label`, `item` and `count` fields.

Mutation operation names are `append_instruction`, `append_condition`, `append_detached_condition`,
`attach_condition`, `move_condition`, `detach_condition`, `add_flow`, `remove`, `move`,
`reorder_instruction`, `set_parent`, `clear_parent`, `place_child`, `connect`, `insert_before`,
`set_property`, `set_input` and `set_cyclic`. Creation operations may include a unique temporary `id`
beginning with `$`; later operations in the same batch may use it. Results contain `saved`, `applied`,
`valid`, `revision`, `code`, `message`, `resolvedIds` and `diagnostics`.

```lua
local acc = require("gadgetsandgizmos.acc").find()
local schedule = acc.raw.getScheduleGraph("draft")

local result = acc.raw.mutateScheduleGraph(schedule.revision, {
    { op = "set_cyclic", value = true }
})
assert(result.saved, result.message)
assert(acc.raw.startSchedule(), "SCM schedule could not start")
```

## SCM navigation and brain data

Precalculated schedule routes are retained safe geometry, not destination ownership. The schedule chooses
the active stop. Pre-calculation resolves one concrete dock per ordered schedule stop and creates one
stop-to-stop chain (including one closing leg for cyclic schedules), rather than an all-to-all candidate
graph. A cached leg is mandatory only for the exact live terminal it was calculated for; another dock
which happens to share that schedule-entry index cannot capture the vehicle. A vehicle follows the ordered
active leg toward its current stop; a temporary live course correction does not replace, reverse or discard
that route. Docking routes end in
the terminal area near the ship dock, after which live dock provisioning and final approach take over.
Vehicles waiting for a busy dock use its provisioned holding placement away from the retained ingress route.
Normal cross-track error is corrected against a lookahead point on the active retained leg and does not
create a second rejoin route. For a command without retained geometry, the live planner's accepted safe
prefix may be followed while planning continues. A yellow dashed destination connection is debug-only,
unvalidated intent and is never passed to vehicle control as movement geometry.

Reactive collision avoidance is always active for autonomous Navigate, Follow and Dock control, including
retained-route travel, route rejoin, recovery, holding and final live approach. The legacy
`avoid_collisions` graph input remains load-compatible but cannot disable this safety layer. Every control
tick checks the selected travel corridor and current motion against root-world collision shapes and loaded
moving Sable SubLevels. A detected hazard may temporarily steer, slow, stop or recover in reverse. Ground
vehicles commit a clear escape to hull-validated bicycle curves instead of treating it as a one-tick
sideways vector. When a forward curve is not enough, the recovery owns a complete multi-point sequence of
forward and reverse phases; the selected gear is held for each phase rather than re-decided from heading
error every tick. If a bounded pose search finds only a safe partial manoeuvre, the vehicle executes that
prefix and immediately continues planning from its new pose; it does not discard the prefix or enter a
stopped retry cooldown. An incompatible off-course heading independently triggers this recovery, including
a clearance-maximising reverse turn when the complete merge has no valid first edge. A newly-arrived live
blocker can replace even that active manoeuvre immediately while
the schedule route remains suspended underneath it. Off-course ground rejoin selects a continuous point on
the current ordered leg at or ahead of its monotonic destination-progress cursor, not an authored waypoint
or a geometrically closer crossing/later leg. The pose planner approaches that point along the leg tangent,
so it cannot splice a sharp diagonal into the route or select already-travelled geometry. A blocker splitting
a leg is bypassed toward the first onward-clear interior point past it. The live planner remains active while
its longer route search is queued and may splice a temporary detour to that clear point on the retained route.
Contact with the specifically selected dock face is the sole intentional collision endpoint.

When the SCM configuration's `Acceleration` group contains any units, it is the exclusive analogue speed
channel in every travel direction. `Forward` and `Backward` are then mutually-exclusive, full-strength
direction selectors; they are never pulsed or feathered by the speed plan. `Acceleration` gains or maintains
speed, `Deceleration` reduces speed, and `Brake` performs the stop. If `Acceleration` is empty, legacy
directional groups retain their analogue drive-strength behaviour.

Traffic intent is shared between SCM vehicles through their containing server dimension, even though each
vehicle runs in a separate SubLevel `Level`. Exactly one vehicle yields an imminent overlapping, crossing or
head-on route conflict; live collision avoidance can override that hold to reverse or move clear. Ship dock
destinations are restored from the complete persisted registry before live pose refresh, copied duplicate
dock identities are repaired, and destination validity does not require the dock block entity, chunk or
SubLevel to be loaded. Saved SubLevel ownership normalizes legacy dimension records without loading the
body. A schedule waits only while the backend snapshot is unreadable; once complete, a genuinely absent
destination is skipped as invalid.

The `SCM Brain Debug` (`scm_brain_debug`) graph node is populated only for an ACC controlling an SCM
vehicle. `getGraphNodeOutputs(nodeIdOrAlias)` exposes its live outputs:

| Output | Value |
| --- | --- |
| `scm_brain_available` | Whether an SCM vehicle snapshot is available. |
| `scm_brain_state`, `scm_brain_reason` | Current high-level behavior and reason. |
| `scm_brain_vehicle_name`, `scm_brain_vehicle_id` | Stable vehicle identity. |
| `scm_brain_game_time` | Server tick for the snapshot. |
| `scm_brain_anchor_x`, `scm_brain_anchor_y`, `scm_brain_anchor_z` | Root-world nameplate anchor. |
| `scm_brain_data` | Complete nested overview, telemetry, command, guidance, route, traffic, collision, speed, control and planner sections. |

## Graph snapshots

`getGraph("draft")` and `getGraph("active")` return detached Lua tables. Changing the table does not change the controller.

```lua
{
    version = number,
    revision = number,
    template = string,
    viewport = { x = number, y = number, zoom = number },
    nodes = table[],
    edges = table[],
    functions = table[],
    variables = { [string] = graphValue }
}
```

A node row contains:

```lua
{
    id = string,
    type = string,
    label = string,
    alias = string,
    x = number,
    y = number,
    inputs = { [string] = string },
    outputs = { [string] = string },
    data = graphData
}
```

An edge row contains:

```lua
{
    id = string,
    fromNode = string,
    fromPort = string,
    toNode = string,
    toPort = string
}
```

Function graphs contain their own `id`, `name`, viewport, nodes and edges. Nodes cannot be wired across the root/function boundary or between different function graphs.

### Runtime graph values

Snapshot variables preserve their graph type:

```lua
{
    type = "number"|"boolean"|"string"|"direction"|"list"|"map"|string,
    value = any
}
```

Lists and maps use explicit collection wrappers when needed to preserve an empty list or distinguish a map from a numeric array:

```lua
local typedList = {
    type = "list",
    value = { __graph = "list", value = { 1, 2, 3 } }
}

local typedMap = {
    type = "map",
    value = { __graph = "map", value = { enabled = true, gain = 0.5 } }
}
```

Ordinary booleans, finite numbers, strings, consecutive arrays and string-keyed tables can be passed directly when an explicit custom graph type is not required.

### Persistent node data

Node configuration is stored as NBT-compatible graph data. Ordinary booleans, numbers, strings, consecutive lists and string-keyed compounds are accepted. Use a tagged wrapper when the exact NBT numeric or collection type matters:

```lua
local exactLong = { __nbt = "long", value = "9223372036854775807" }
local exactInts = { __nbt = "int_array", value = { 1, 2, 3 } }
local compound = {
    __nbt = "compound",
    value = {
        Enabled = true,
        Gain = { __nbt = "float", value = 0.5 }
    }
}
```

Supported `__nbt` kinds are `byte`, `short`, `int`, `long`, `float`, `double`, `list`, `compound`, `byte_array`, `int_array` and `long_array`.

## Editing a graph

Use `acc:edit()` to capture the current draft revision and create an empty atomic operation batch.

```lua
local acc = require("gadgetsandgizmos.acc").find()
local graph = acc:edit()

local ready = graph:addNode("event_graph_ready", 80, 80)
local value = graph:addNode("constant_number", 80, 220, { Value = 42 }, "answer_value")
local store = graph:addNode("variable_set", 320, 80, { Variable = "answer" })

ready:wire("exec", store, "exec")
value:wire("value", store, "value")
graph:setVariable("answer", 0)

local result = graph:commit()
assert(result.saved, result.code .. ": " .. result.message)
print("Created node ID: " .. value:getId())

local applied = acc:apply(result.revision)
assert(applied.applied, applied.code .. ": " .. applied.message)
```

`commit()` mutates only the draft. It resolves temporary node/function IDs, refreshes the local snapshot and clears the submitted operation list after a successful save. Every new `Node` handle is updated in place, so `node.id` and `node:getId()` return its permanent UUID after a successful commit. The same UUID is available in `result.resolvedIds[temporaryId]`. A commit does not apply the draft.

### Atomic mutation operations

The high-level module creates these raw operations. They can also be supplied directly to `mutateGraph(...)`.

```lua
local operations = {
    {
        op = "add_node",
        id = "$source",
        type = "constant_number",
        label = "Throttle",
        alias = "throttle_source",
        functionId = "",
        x = 80,
        y = 120,
        data = { Value = 0.5 }
    },
    {
        op = "add_node",
        id = "$target",
        type = "variable_set",
        functionId = "",
        x = 320,
        y = 120,
        data = { Variable = "throttle" }
    },
    {
        op = "add_edge",
        id = "$edge",
        functionId = "",
        fromNode = "$source",
        fromPort = "value",
        toNode = "$target",
        toPort = "value"
    }
}

local snapshot = acc.raw.getGraph("draft")
local result = acc.raw.mutateGraph(snapshot.revision, operations)
textutils.pretty_print(result)
```

The accepted operation shapes are:

| Operation | Required fields |
| --- | --- |
| `add_node` | `id`, `type`, `functionId`, `x`, `y`; optional `label`, `alias`, `data` |
| `remove_node` | `functionId`, `nodeId` |
| `move_node` | `functionId`, `nodeId`, `x`, `y` |
| `rename_node` | `functionId`, `nodeId`, `label` |
| `set_node_alias` | `functionId`, `nodeId`, `alias` |
| `set_node_data` | `functionId`, `nodeId`, `key`, `value` |
| `remove_node_data` | `functionId`, `nodeId`, `key` |
| `add_edge` | `id`, `functionId`, `fromNode`, `fromPort`, `toNode`, `toPort` |
| `remove_edge` | `functionId`, `edge` |
| `add_function` | `id`, `name` |
| `rename_function` | `functionId`, `name` |
| `remove_function` | `functionId` |
| `set_variable` | `name`, `value` |
| `remove_variable` | `name` |

Use an empty `functionId` for the root graph. Every node mutation identifies its target with `nodeId`; the value may be the permanent node ID, a unique alias in the same graph scope or a temporary ID created earlier in the batch. The legacy raw field name `node` remains accepted for compatibility, but new scripts should use `nodeId`. Temporary IDs begin with `$` and successful results map them to permanent IDs in `resolvedIds`.

Unknown fields are rejected. This prevents spelling mistakes from silently creating unused data.

## Graph handles

### Graph

| Method | Result |
| --- | --- |
| `graph:addNode(type, x, y, [data], [alias])` | New temporary `Node` handle; its ID becomes permanent after commit |
| `graph:node(idOrAlias)` | Existing root `Node` handle |
| `graph:wire(fromNode, fromPort, toNode, toPort)` | Target node or ID |
| `graph:unwire(edgeId)` | Graph handle |
| `graph:removeNode(node)` | Graph handle |
| `graph:moveNode(node, x, y)` | Graph handle |
| `graph:renameNode(node, label)` | Graph handle |
| `graph:setNodeAlias(node, alias)` | Graph handle |
| `graph:setNodeData(node, key, value)` | Graph handle |
| `graph:removeNodeData(node, key)` | Graph handle |
| `graph:setVariable(name, value)` | Queues a draft variable mutation |
| `graph:removeVariable(name)` | Queues a draft variable removal |
| `graph:variable(name)` | Live active `Variable` handle |
| `graph:addFunction(name)` | New temporary `FunctionGraph` handle |
| `graph:functionGraph(id)` | Existing `FunctionGraph` handle |
| `graph:renameFunction(fn, name)` | Graph handle |
| `graph:removeFunction(fn)` | Graph handle |
| `graph:reload()` | Discards pending operations and reloads the draft |
| `graph:commit()` | Submits the operation batch atomically |

### Node

Node methods keep the node's graph scope and support fluent wiring:

```lua
local source = graph:addNode("constant_number", 80, 120, { Value = 0.75 })
local target = source:wire("value", "variable_set", "value", 300, 120, {
    Variable = "throttle"
})

target:rename("Store throttle")
target:setAlias("requested_throttle_store")
target:move(320, 140)
target:setData("Variable", "requested_throttle")
```

| Method | Result |
| --- | --- |
| `node:wire(fromPort, target, toPort, [x], [y], [data], [alias])` | Target `Node` handle |
| `node:move(x, y)` | Same node |
| `node:rename(label)` | Same node |
| `node:setAlias(alias)` | Same editable node |
| `node:getAlias()` | Current alias, or an empty string |
| `node:getId()` | Permanent node ID after commit; temporary ID before commit |
| `node:setData(key, value)` | Same node |
| `node:getData()` | Detached table containing the node's current live output data, keyed by port name |
| `node:removeData(key)` | Same node |
| `node:remove()` | Owning graph |

When `target` passed to `node:wire(...)` is a node type string, the method creates the target node before adding the edge. When it is a `Node` handle, both nodes must belong to the same root or function graph.

`node:getData()` reads the active server runtime. It does not read the node's editable configuration despite its similar name to `node:setData(...)`, and it does not execute, pulse or otherwise wake the node. `node:setData(...)`, move, remove, rename and alias changes require an editable handle returned by `acc:edit()`; the handle returned by `acc:node(...)` is intentionally read-only.

```lua
local acc = require("gadgetsandgizmos.acc").find()
local node = acc:node("requested_throttle_store")
local data = node:getData()

print("Value: " .. tostring(data["value"]))

for key, value in pairs(data) do
    print(key .. " : " .. textutils.serialize(value))
end
```

The returned value is an ordinary Lua table. Numbers, booleans and strings are ordinary Lua primitives; graph lists and maps become nested Lua tables. The table is a detached snapshot, so changing it does not change the graph.

Only public data output ports are included. Execution ports and internal ports beginning with `__` are omitted. The bridge returns the current live value when the runtime has produced one; otherwise it evaluates the missing output through an isolated read-only runtime. This means unconnected constants, primitive nodes and target-data getters still return their declared outputs without executing or changing the active graph.

The node must exist in the active root graph. `acc:node(...)`, `graph:node(...)`, raw node-output reads and node mutation operations accept either a node ID or a unique alias. IDs remain authoritative and aliases must be unique within their root or function graph, must not match a node ID and may contain at most 64 non-control characters. The graph GUI shows `node_id: ...` in the Config sidebar, provides `Copy Node ID` in the node context menu and edits the alias under Node Options.

Newly added temporary nodes must be committed and the draft must be applied before their data can be read. Function template nodes do not have one unambiguous runtime value because the same template can be called several times; read the output ports on the corresponding root `function_call` node instead.

### FunctionGraph

Function graph handles provide the same node, edge, move, rename, alias and node-data operations, scoped to one function.

```lua
local fn = graph:addFunction("Clamp throttle")
local input = fn:addNode("function_input", 80, 100)
local output = fn:addNode("function_output", 360, 100)

fn:wire(input, "value", output, "value")
fn:rename("Pass throttle")
```

Removing a function removes its contained nodes and edges as one draft mutation.

## Graph variables

The bridge has two deliberately separate variable APIs.

### Draft variable definitions

`graph:setVariable(name, value)` and `graph:removeVariable(name)` queue structural mutations in an editor batch. They take effect in the draft after `commit()` and in the active graph after `apply(...)`.

```lua
local graph = acc:edit()
graph:setVariable("target_altitude", 120)
graph:setVariable("autopilot_enabled", false)

local saved = graph:commit()
assert(saved.saved, saved.message)
assert(acc:apply(saved.revision).applied, "Could not apply graph")
```

### Live variable handles

`acc:variable(name)` and `graph:variable(name)` return a handle to the active runtime variable. `get()` reads it and `set(...)` changes it immediately without rebuilding or applying the graph.

```lua
local acc = require("gadgetsandgizmos.acc").find()
local throttle = acc:variable("requested_throttle")

print("Current throttle: " .. throttle:get())
throttle:set(0.65)
```

The same API is available from an editor when the surrounding code already uses a `graph` variable:

```lua
local graph = acc:edit()
local enabled = graph:variable("autopilot_enabled")

enabled:set(not enabled:get())
```

This handle is live, not a queued draft operation. The setter wakes variable-change nodes and persists the active value. When the draft already defines the same variable, the controller keeps its value synchronized so a later unrelated apply does not restore the old value.

The variable must already exist. `get()` raises `unknown graph variable '<name>'` when it does not. Use `graph:setVariable(...)`, `commit()` and `apply(...)` to create it first.

Numbers, booleans, strings, lists, maps and explicitly typed graph values are accepted. Values are subject to the graph table limits described above.

## Validation and revisions

Every graph edit result has this shape:

```lua
{
    saved = boolean,
    applied = boolean,
    valid = boolean,
    revision = number,
    code = string,
    message = string,
    resolvedIds = { [string] = string },
    diagnostics = {
        {
            severity = string,
            code = string,
            message = string,
            nodeId = string,
            edgeId = string
        }
    }
}
```

`mutateGraph(expectedRevision, operations)` compares `expectedRevision` with the current draft revision. A stale editor cannot overwrite a newer edit. Reload the draft, reconsider the pending change and submit a new batch.

```lua
local result = graph:commit()
if not result.saved and result.code == "revision_conflict" then
    graph:reload()
    error("The draft changed; rebuild this edit from the new snapshot", 0)
end
```

Validate before apply when a script needs to display all diagnostics:

```lua
local validation = acc:validate()
if not validation.valid then
    for _, diagnostic in ipairs(validation.diagnostics) do
        printError(diagnostic.code .. ": " .. diagnostic.message)
    end
    return
end

local snapshot = acc:get("draft")
local applied = acc:apply(snapshot.revision)
assert(applied.applied, applied.code .. ": " .. applied.message)
```

Malformed node types, missing ports, incompatible wires, invalid function boundaries and invalid Named Event nodes become diagnostics. A failed apply leaves the previous active graph running.

## Lua graph export

`acc:exportLua(view, options)` creates deterministic pure Lua source for one graph view.

```lua
local acc = require("gadgetsandgizmos.acc").find()
local source = acc:exportLua("active", {})

local file = fs.open("active_acc_graph.lua", "w")
assert(file, "Could not open export file")
file.write(source)
file.close()
```

Export reads a bounded snapshot. It does not mutate the graph, apply a draft or start the resulting Lua. Unsupported graph behaviour is reported as an export error instead of emitting source which silently changes semantics.

## Named Events

Named Events use G&G's shared transport bus to connect ACC graphs, optional Computed and Synaxis graph bridges, and the optional CC:Tweaked rednet integration without exposing the graph scheduler directly. The public transport API lets another server-side mod subscribe to or publish the same immutable topic/data events without linking to ACC, Computed, Synaxis or CC:Tweaked implementation classes.

Use an `On Named Controller Event` node to receive an event. Its configured event name selects the topic and its `data` output contains the received graph value. Use `Send Named Controller Event` to publish a topic, data and optional Minecraft-distance limit.

The raw CC methods provide both directions:

```lua
local acc = peripheral.find("advanced_contraption_controller")
assert(acc, "Attach an ACC")

acc.publishNamedEvent("autopilot:engage", {
    altitude = 120,
    heading = 90
}, 64)
```

`publishNamedEvent(...)` is a new source event. It enters the normal ACC Named Event bus, reaches matching graph nodes, and is offered to every installed shared transport. When CC:Tweaked is installed, the same event is also broadcast to nearby wireless rednet computers with the event name as its rednet protocol.

External transports must use `receiveNamedEvent(...)`:

```lua
acc.receiveNamedEvent("telemetry:update", {
    speed = 18.5,
    altitude = 121.25
})
```

`receiveNamedEvent(...)` delivers only to this ACC's matching Named Event nodes. It deliberately does not publish the event again.

The high-level aliases have the same distinction:

```lua
local acc = require("gadgetsandgizmos.acc").find()

acc:publish("autopilot:engage", { altitude = 120 }, 64)
acc:receive("telemetry:update", { speed = 18.5 })
```

### Optional graph transports

When Computed is installed, its built-in `event_bus` event nodes use the same shared transport. A Computed event can therefore reach ACC Named Event nodes, Synaxis nodes and rednet; an ACC or rednet event can be delivered to matching Computed event nodes.

When Synaxis `1.5.0` and LDLib2 are installed, its Cimulink palette adds `Send Named Event` and `On Named Event`. Both nodes have a `Named Event` topic option. `Send Named Event` publishes its numeric `value` input on a rising `pulse`; `On Named Event` exposes a matching numeric payload through `value` and raises `pulse` for one Synaxis tick. The Synaxis pair maps data to its native numeric signal type, so a nonnumeric payload is read as `0`. Use ACC/Computed graph values or a custom `NamedEventBus` transport when a receiving mod needs a structured list or map.

The shared bus is server-local and is not authentication. Every transport should validate topic names and payloads before applying safety-critical actions. A positive `maximumDistance` is enforced by receiving graph transports using the source dimension and position; the normal CC wireless range remains authoritative for rednet packets.

## Rednet Named Event bus

CC:Tweaked is optional. Gadgets & Gizmos and the ACC graph runtime do not load, link or depend on CC:Tweaked classes when the mod is absent. When CC:Tweaked is installed, every loaded ACC is registered internally as a wireless rednet endpoint.

This endpoint is not a modem block or ACC peripheral attachment. Do not place a modem on the ACC, do not attach a computer to the ACC and do not run a forwarding program. Only the CC computer needs a normal wireless modem opened through the standard `rednet` API.

### Event flow

```text
CC computer
    -> rednet.broadcast(message, namedEventName)
    -> nearby ACC On Named Controller Event nodes
    -> installed shared Named Event transports

ACC, Computed or Synaxis shared Named Event publisher
    -> nearby CC rednet_message
    -> rednet.receive(namedEventName)
```

The rednet protocol is the Named Event name. There is no addon protocol, envelope, bridge object or `acc_named_event` OS event.

### Opening rednet

Attach a wireless modem to the CC computer and open it normally:

```lua
local openedWireless = false

peripheral.find("modem", function(name, modem)
    if modem.isWireless() then
        rednet.open(name)
        openedWireless = true
    end
    return false
end)

assert(openedWireless, "Attach a wireless modem")
```

The filter returns `false` because it is being used only to visit and open the modems, not to collect the wrapped peripherals returned by `peripheral.find(...)`. The ACC endpoint belongs to CC:Tweaked's wireless network, so an opened wired modem alone cannot reach it.

Open a known modem attachment when a computer has several modems:

```lua
rednet.open("wireless_modem_0")
```

### Broadcasting from CC to ACC

Pass the Named Event name as the second argument to `rednet.broadcast(...)`:

```lua
rednet.open("wireless_modem_0")

rednet.broadcast({
    altitude = 150,
    heading = 270
}, "flight:set_target")
```

Every loaded ACC within the normal CC:Tweaked wireless range receives the broadcast. An `On Named Controller Event` node configured as `flight:set_target` pulses and exposes the message table through its `data` output.

Only `rednet.broadcast(...)` maps into ACC Named Events. `rednet.send(...)` remains an addressed computer-to-computer operation and is not consumed by ACC endpoints.

### Receiving ACC events with rednet

An ACC `Send Named Controller Event` node broadcasts its `data` value as the ordinary rednet message and its configured event name as the protocol:

```lua
rednet.open("wireless_modem_0")

local sender, message, protocol = rednet.receive("telemetry:update")
print("Received " .. protocol .. " from " .. sender)
textutils.pretty_print(message)
```

The returned values follow CC:Tweaked's normal `rednet.receive(...)` contract:

| Result | Contract |
| --- | --- |
| `sender` | Synthetic numeric sender ID for the shared publisher. It is negative and stable while that publisher remains at the same dimension and block position. |
| `message` | The graph event data converted to ordinary Lua booleans, numbers, strings and tables. |
| `protocol` | The ACC Named Event name. |

Use the normal timeout argument when the program must not wait forever:

```lua
local sender, message = rednet.receive("telemetry:update", 5)
if sender == nil then
    printError("No telemetry received")
else
    textutils.pretty_print(message)
end
```

ACC-originated packets are ignored by the ACC endpoints themselves. This prevents one graph publication from returning through rednet and pulsing the same ACC graph a second time. Repeated copies of the same CC rednet broadcast are also discarded for the same period used by CC:Tweaked's rednet duplicate protection.

### Range and dimensions

The integration uses CC:Tweaked's global wireless packet network and its standard wireless range calculation. It does not make ACCs interdimensional and it does not turn wired modem networks into wireless networks.

The Named Event node's `maximumDistance` still controls the normal ACC-to-ACC graph bus. It does not replace CC:Tweaked's wireless range for rednet delivery.

### Rednet trust boundary

Rednet has no built-in application authentication. Any computer in wireless range which knows a Named Event name can broadcast data to matching ACC graph nodes.

Use private event names and validate the incoming `data` inside the graph before wiring safety-critical actions to an `On Named Controller Event` node. A rednet protocol is a filter, not an authentication secret.

## ACC Displays and remote terminals

ACC Displays and Universal Display Adapters expose CC terminal behaviour under the standard `monitor` peripheral type. Use CC:Tweaked monitor APIs for text, palette and cursor operations.

```lua
local monitor = peripheral.find("monitor")
assert(monitor, "Attach an ACC Display or monitor adapter")

monitor.setTextScale(0.5)
monitor.setBackgroundColor(colors.black)
monitor.setTextColor(colors.lime)
monitor.clear()
monitor.setCursorPos(2, 2)
monitor.write("ACC ONLINE")
```

Display interaction queues the normal monitor and mouse event families where the target supports them. Use the attachment field on monitor events to distinguish multiple display surfaces.

The Diagnostic Tablet RDP app opens supported CC computer terminals through the addon compatibility backend. RDP mirrors the computer's existing terminal and input stream; it does not create a second computer, bypass the computer owner's program or provide graph authority by itself.

## Other peripheral families

### Controllers

`analogue_contraption_controller` and `advanced_contraption_controller` expose named inputs instead of legacy axes. Inputs have stable IDs, aliases, key bindings, modes, rates, local outputs, wireless frequencies and optional direct targets.

```lua
local controller = peripheral.find("analogue_contraption_controller")
assert(controller, "Attach a controller")

for _, input in ipairs(controller.listInputs()) do
    print(input.id, input.alias, input.key, input.mode, input.value)
end

local changed = controller.pressKey("w")
print("Updated " .. changed .. " W-bound inputs")
```

Use `ctdocs analogue_contraption_controller` for the complete input/config table schemas and method list.

### Thrusters and bearings

Thruster peripherals expose computer control modes, throttle, enabled state, fuel, burn time and thrust telemetry. Bearing peripherals additionally expose assembled state, head controls and attached-device fleet methods.

Servo-capable bearings consume the shared exact kinetic angle used by Create and compatible Simulated/Aeroworks sources. For a Simulated Torsion Spring, the bearing reads the spring's existing accumulated output angle and follows its kinetic source chain; Gadgets & Gizmos does not replace or inject a second Torsion Spring angle publisher. Hand Cranks, Valve Handles, Sequenced Gearshifts, Steering Wheels, Torsion Springs, Aeroworks servos and the Smart Gearbox can therefore drive a bearing angle through an ordinary connected kinetic network. The Smart Gearbox publishes each face independently and preserves gear ratios and direction changes through intermediate shafts and gears; direct adjacency is not required.

```lua
local bearing = peripheral.find("thruster_bearing")
assert(bearing, "Attach a thruster bearing")

bearing.setBearingControlMode("servo")
bearing.setPivotAngle(15)
bearing.setControlMode("all", "computer")
bearing.setThrottle("all", 0.65)

textutils.pretty_print(bearing.getNetworkInfo())
```

### Links and orientation

`advanced_data_link`, `gyroscope_link` and `virtual_orientation_source` expose linked targets, orientation angles, direction vectors and source state. Radian and degree setters are named explicitly.

### Claws and winches

`claw` exposes signal control, holding state and connector discovery. `rope_winch_cable` forwards the same operations through a connected Simulated rope winch and errors when no compatible claw is attached.

### Optional machine bridges

Optional Simulated, Aeronautics, Offroad, Create Connected and propulsion bridges are registered only when their owning block entity exists and the compatible mod is loaded. Do not assume that a type from the table above exists in every pack.

Probe the peripheral before using it:

```lua
local servo = peripheral.find("servo_bearing")
if servo then
    textutils.pretty_print(servo.getStatus())
else
    print("No compatible Aeroworks servo bearing is attached")
end
```

## Complete examples

### Create, apply and control a variable graph

```lua
local acc = require("gadgetsandgizmos.acc").find()
assert(acc:version() >= 4, "ACC graph API 4 is required")

local graph = acc:edit()
graph:setVariable("requested_throttle", 0)

local ready = graph:addNode("event_graph_ready", 80, 80)
local initial = graph:addNode("constant_number", 80, 220, { Value = 0.25 })
local write = graph:addNode("variable_set", 320, 80, {
    Variable = "requested_throttle"
})

ready:wire("exec", write, "exec")
initial:wire("value", write, "value")

local saved = graph:commit()
if not saved.saved then
    error(saved.code .. ": " .. saved.message, 0)
end

local validation = acc:validate()
if not validation.valid then
    textutils.pretty_print(validation.diagnostics)
    error("The draft is invalid", 0)
end

local applied = acc:apply(saved.revision)
if not applied.applied then
    error(applied.code .. ": " .. applied.message, 0)
end

local throttle = graph:variable("requested_throttle")
print("Initial value: " .. throttle:get())
throttle:set(0.7)
print("Updated value: " .. throttle:get())
```

### Named Event request and response

One CC computer sends a request to nearby ACC graphs and waits for the response:

```lua
rednet.open("wireless_modem_0")

rednet.broadcast({
    replyTo = os.getComputerID()
}, "telemetry:request")

local sender, message = rednet.receive("telemetry:response", 5)
if sender == nil then
    printError("The ACC did not respond")
else
    textutils.pretty_print(message)
end
```

The nearby ACC graph receives `telemetry:request` through an `On Named Controller Event` node and sends `telemetry:response` through a `Send Named Controller Event` node. The ACC needs no modem, attached computer, Lua module or bridge process.

## Compatibility and failure behaviour

### Missing peripherals

`peripheral.find(type)` returns `nil` when no matching peripheral is attached. Always assert a required device or handle the missing case before calling methods.

### Removed targets

Ordinary block-entity peripherals reject calls after their target is removed or loses its level. A stale wrapper does not keep a removed block alive.

### Graph edits

- Snapshot tables are detached and safe to inspect, but modifying them has no effect.
- Mutation batches are all-or-nothing.
- Revision conflicts do not partially apply operations.
- Validation failure leaves the previous active graph running.
- Unknown mutation fields, unknown node IDs, invalid ports and cross-scope wires are errors or diagnostics.
- Runtime variable writes wake variable-change execution and mark the controller state dirty.
- Invalid rednet Named Event data is ignored and logged without crashing the game server.

### Event delivery

- `publishNamedEvent(...)` creates a bus event for ACC graphs and installed shared transports, and broadcasts it through rednet only when CC:Tweaked is installed.
- `receiveNamedEvent(...)` is a local transport ingress and never retransmits.
- `rednet.broadcast(message, protocol)` reaches ACC endpoints only when `protocol` is a nonblank Named Event name of at most 128 characters.
- ACC endpoints consume broadcast rednet traffic only. Addressed `rednet.send(...)` packets remain computer-to-computer traffic.
- ACC-originated rednet messages are not fed back into ACC endpoints.
- Rednet delivery does not prove a matching graph listener exists.
- Without CC:Tweaked, all ACC graph, variable, shared Named Event transport and internal Named Event behaviour remains available; only the rednet extension is absent.

### Documentation consistency

The checked-in CC catalogue is generated from the actual addon peripheral methods. Development builds run `verifyComputerCraftDocs`; a stale catalogue or an undocumented addon Lua method fails verification. `CCT.md` explains the stable concepts and workflows, while `ctdocs` is the exhaustive installed method reference.

## Bundled files

| File | Purpose |
| --- | --- |
| `data/computercraft/lua/rom/modules/main/gadgetsandgizmos/acc.lua` | High-level ACC graph and variable module |
| `data/computercraft/lua/rom/modules/main/gadgetsandgizmos/docs_catalog.lua` | Generated peripheral method catalogue |
| `data/computercraft/lua/rom/modules/main/gng/acc.lua` | Short `gng.acc` alias |
| `data/computercraft/lua/rom/modules/main/gadgets/acc.lua` | `gadgets.acc` alias |
| `data/computercraft/lua/rom/modules/main/gizmos/acc.lua` | `gizmos.acc` alias |
| `data/computercraft/lua/rom/modules/main/createthrusters/acc.lua` | Legacy compatibility alias |
| `data/computercraft/lua/rom/programs/ctdocs.lua` | Interactive in-game documentation browser |
| `data/computercraft/lua/rom/thrusters/docs.lua` | Compatibility launcher for `ctdocs` |
| `data/computercraft/lua/rom/thrusters/examples/acc_graph_bridge.lua` | Graph edit/apply example |
| `data/computercraft/lua/rom/thrusters/examples/acc_rednet_events.lua` | Direct rednet Named Event example |

Use the bundled module with `require("gadgetsandgizmos.acc")`. Do not copy its implementation into every computer unless a pack intentionally needs to pin and maintain a separate API version.
