--[[--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                         DEFAULTS
                                                    #################
                                                         Variables
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------]]--

local isAdvanced = term.isColor and term.isColor()
local topics = {}

--[[--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                         Functions
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------]]--

-- Add one page to the documentation browser
local function add(title, body)
    topics[#topics + 1] = { title = title, body = body }
end

--[[--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                      DOCUMENTATION
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------]]--

add("Welcome", [[
Create Gadgets & Gizmos CC:Tweaked Reference

This browser documents the ComputerCraft peripherals supplied by Create Gadgets & Gizmos and its compatibility layer.

Connect a computer directly to a block, or use a wired modem. Find a peripheral with:

  peripheral.find("analogue_joystick")
  peripheral.find("thruster")

Every method shown here is called with colon syntax on a wrapped peripheral:

  local joystick = peripheral.find("analogue_joystick")
  local status = joystick.getStatus()

Controls
  Left/Right: previous or next topic
  Up/Down: scroll one line
  Page Up/Page Down: scroll one page
  Home/End: first or last topic
  Q: quit

Advanced computers also support mouse navigation, mouse-wheel scrolling, and search with / or the Search button.
]])

add("Common patterns", [[
Finding peripherals

  local device = peripheral.find("thruster")
  assert(device, "Attach a thruster")

  for _, name in ipairs(peripheral.getNames()) do
    print(name, peripheral.getType(name))
  end

Return values

Simple telemetry methods return numbers, booleans, or strings. Status methods return tables. Inspect a table with:

  textutils.pretty_print(device.getStatus())

Methods which accept an id also commonly accept "all". A single id returns one value; "all" returns a table keyed by id.

Errors

Invalid ranges, modes, directions, ids, and unavailable optional integrations raise Lua errors. Use pcall when handling player-provided values.

Live help

Many peripherals expose methods() and help(). The Live peripherals topic shows methods currently attached to this computer.
]])

add("Analogue joystick", [[
Peripheral type: analogue_joystick

The joystick is read-only from CC except for its custom name. Tilt uses joystick-local axes. x is left/right and z is forward/backward, both from -1 to 1 after deadzone processing.

Methods
  getName() -> string
  setName(name:string)
  getTilt() -> {x, z, magnitude, held, active}
  getTiltDegrees() -> {x, z, max}
  getX() -> number
  getZ() -> number
  getRedstone() -> {forward, backward, left, right, max}
  getRedstoneOutput(channel:string) -> number
  isHeld() -> boolean
  isActive() -> boolean
  getDeadzone() -> number
  getMaxTiltDegrees() -> number
  getReleaseMode() -> "latched"|"momentary"
  getStatus() -> combined status table
  methods() -> method signature list
  help(method?:string) -> string|table

Directional redstone values are integers from 0 to 15. channel is forward, backward, left, or right.

Example
  local j = assert(peripheral.find("analogue_joystick"))
  local tilt = j.getTilt()
  local redstone = j.getRedstone()
  print(("x %.2f z %.2f"):format(tilt.x, tilt.z))
  print("forward", redstone.forward)

Runnable example: /rom/thrusters/examples/joystick_monitor.lua
]])

add("Thruster", [[
Peripheral type: thruster

Methods
  setThrottle(throttle:number)
  getThrottle() -> number
  setEnabled(enabled:boolean)
  isEnabled() -> boolean
  getFuel() -> number in mB
  getFuelCapacity() -> number in mB
  getFuelType() -> fluid id|string
  getBurnTimeSeconds() -> number
  getName() -> string
  setName(name:string)
  getControlMode() -> "redstone"|"computer"
  setControlMode("auto"|"redstone"|"computer")
  getThrust() -> number
  getRealThrust() -> number
  getLiftCapacity() -> number
  getAirflow() -> number
  isActive() -> boolean
  isSoulMode() -> boolean
  setSoulMode(enabled:boolean)
  clearThrottleOverride()
  getRedstoneSignal() -> integer 0..15
  getStatus() -> telemetry table
  methods() -> method signature list
  help(method?:string) -> string|table

setThrottle accepts 0..1 and switches the thruster to computer control. clearThrottleOverride returns control to redstone.

Status includes throttle, enabled, fuel, fuelCapacity, fuelType, burnTimeSeconds, controlMode, thrust, realThrust, liftCapacity, airflow, active, soulMode, and redstoneSignal.

Runnable example: /rom/thrusters/examples/thruster_control.lua
]])

add("Thruster bearing", [[
Peripheral type: thruster_bearing

Bearing control
  getName(), setName(name)
  getForwardSignal(), getBackwardSignal()
  getPivotAngle(), setPivotAngle(angleDeg)
  getBearingControlMode()
  setBearingControlMode("auto"|"redstone"|"computer"|"servo")
  getServoInputAngle()
  getMinAngle(), setMinAngle(angleDeg)
  getMaxAngle(), setMaxAngle(angleDeg)
  clearPivotOverride()
  getFacing(), getWorldFacing(), setFacing(direction)

Attached thrusters
  listThrusters() -> id-keyed quick telemetry table
  getThrusterCount() -> number
  getOwnedThrusters() -> string array
  ids() -> alias array
  getNetworkInfo() -> ownership and telemetry table
  thrusterAlias(idOrAlias, alias)
  setThrottle(idOrAll, throttle)
  getThrottle(idOrAll) -> value|table
  getThrottleMap(idOrAll) -> table
  setEnabled(idOrAll, enabled)
  isEnabled(idOrAll) -> value|table
  getFuel(idOrAll) -> value|table
  getFuelCapacity(idOrAll) -> value|table
  getFuelType(idOrAll) -> value|table
  getBurnTimeSeconds(idOrAll) -> value|table
  getControlMode(idOrAll) -> value|table
  setControlMode(idOrAll, mode)
  getThrust(idOrAll) -> value|table
  getRealThrust(idOrAll) -> value|table
  getLiftCapacity(idOrAll) -> value|table
  getTotalRealThrust() -> number
  getTotalLiftCapacity() -> number
  getAirflow(idOrAll) -> value|table
  isActive(idOrAll) -> value|table
  isSoulMode(idOrAll) -> value|table
  setSoulMode(idOrAll, enabled)
  clearThrottleOverride(idOrAll)
  getRedstoneSignal(idOrAll) -> value|table
  getThrusterStatus(idOrAll) -> table
  getStatus() -> combined bearing status
  methods(), help(method?)

Use "all" to address every attached thruster. Throttle accepts 0..1 or 0..100.
Each listThrusters entry includes world-space pos, a structured position table,
and the original localPos within its assembly.

Runnable example: /rom/thrusters/examples/bearing_control.lua
]])

add("Analogue controller", [[
Peripheral type: analogue_contraption_controller

The controller exposes configurable player-input channels to CC. An input can be selected by persistent id or alias.

Discovery and naming
  getName(), setName(name)
  listInputs(), listChannels(), listCustomEntries() -> array of config/state tables
  listInputIds() -> id array
  listAxes() -> array
  getInput(id), getChannel(id), getCustomEntry(id) -> table
  getInputByKey(key), getInputByKeyCode(code) -> table
  getBoundKeyState(key), getBoundKeyCodeState(code) -> table
  getInputConfig(id), getChannelConfig(id) -> table
  getAlias(id), getInputAlias(id)
  setAlias(id, alias), setInputAlias(id, alias), setChannelAlias(id, alias)

Creating and configuring
  addInput(label) -> id
  addCustomEntry() -> id
  removeInput(id), removeCustomEntry(id)
  getChannelMode(id) -> string
  setChannelMode(id, mode)
  setInputConfig(id, config)
  setChannelConfig(id, config)
  setCustomEntryConfig(id, config)
  setLocalOutputSide(id, side)
  setChannelFrequency(id, firstItemId, secondItemId)
  setInputFrequency(id, firstItemId, secondItemId)
  setChannelDirectTarget(id, targetTable)
  setChannelInputTarget(id, targetTable)

Driving inputs
  setInput(id, value), setChannel(id, value)
  setInputByKey(key, value) -> dispatched target count
  setInputByKeyCode(code, value) -> dispatched target count
  getInputValue(id), getCustomEntryValue(id) -> number
  press(id), pressInput(id), pressCustomEntry(id)
  pressKey(key), pressKeyCode(code) -> dispatched target count
  pressInputStepDown(id), pressCustomEntryStepDown(id)
  reset(id), resetInput(id), resetAll()
  isInputActive(id), isCustomEntryActive(id) -> boolean
  getLocalOutput(side) -> integer 0..15
  getAllSignals() -> table
  getAxis(name) -> table

Input values are finite numbers from 0 to 1. Configuration tables may include mode, riseRate, fallRate, stepAmount, stepDownAmount, deadzone, smoothing, localOutputSide, frequencies, key bindings, and direct/input targets.

Runnable example: /rom/thrusters/examples/controller_inputs.lua
]])

add("Navigation and data link", [[
Peripheral type: navigation_table

Slots are 1-based.
  getName(), setName(name)
  getSlotCount(), getSelectedSlot(), setSelectedSlot(slot)
  nextSlot(), previousSlot()
  getSlot(slot), listSlots()
  hasMap(slot), getMapName(slot), getSlotTarget(slot)
  getFilledSlotCount(), clearSlot(slot), clearAllSlots()
  getState(), setState("idle"|"running"|"paused")
  isRunning(), isPaused(), isIdle(), start(), pause(), stop()
  getTablePosition(), getBlockPos() -> projected world position
  getCurrentAngle() -> degrees
  getVector() -> directional analogue table
  hasTarget(), hasTargetInSlot(slot)
  getTargetLabel(), getTargetLabelInSlot(slot)
  getTarget(), getTargetPosition(), getTargetPositionInSlot(slot)
  getTargetDistance(), getTargetDistanceInSlot(slot)
  getSelectedMapInfo(), getStatus(), methods(), help(method?)

Peripheral type: advanced_data_link
  isLinked() -> boolean
  getTarget() -> live projected target position
  getAngles() -> linked-source radians/degrees table
  getDirection() -> {x,y,z}
  getStatus() -> combined table
  getMode() -> "live"|"static"
  setMode(mode)
  setTarget(x,y,z,dimension?)
  clearTarget(), methods(), help(method?)

Peripheral type: virtual_orientation_source
  setAngles(xRadians,zRadians)
  setAnglesDegrees(xDegrees,zDegrees)
  setDirection(x,y,z)
  clear(), isActive()
  getState() -> {active, angles, direction, lastUpdateTick}
  methods()

The legacy peripheral type gyroscope_link remains available so existing scripts continue to work.

Position tables use world-space x/y/z and include blockX/blockY/blockZ and
dimension. Block-backed positions also include centerX/centerY/centerZ,
localX/localY/localZ, and subLevelId. An empty subLevelId identifies the
ordinary world. projected is false, and x/y/z are omitted, if a referenced
sub-level is temporarily unavailable.

Runnable examples: navigation.lua, advanced_data_link.lua, orientation_source.lua
]])

add("Gearboxes and wheels", [[
Peripheral type: bidirectional_gearbox
  isGyroMode(), isServoMode(), hasGyroSource()
  getMode(), setMode(mode)
  getLaneMode(axis), setLaneMode(axis, mode)
  isReverseMode(), getSpeed()
  getSignal(face) -> integer 0..15
  getFaceAngle(face), setFaceAngle(face, angle)
  getFaceMaxAngle(face), setFaceMaxAngle(face, angle)
  clearFaceAngle(face?), clearFaceMaxAngle(face?)
  getLaneSpeed(axis)
  getStatus(), methods(), help(method?)

Faces are north, south, east, or west. Axes are x or z. Modes include auto, passthrough, passthrough_split, servo, and servo_locked; legacy aliases remain accepted.

Peripheral type: directional_gearshift
  getName(), setName(name)
  isLeftPowered(), isRightPowered()
  setLeft(powered), setRight(powered)
  setOutputs(leftPowered,rightPowered), clear()
  getRotationModifier(face)
  getStatus(), getFacing(), getWorldFacing(), getPosition(), getClassName(), help()

getFacing() is the local cardinal facing. getWorldFacing() is a projected
world-space direction vector.

Peripheral type: wheel_mount
  setLeft(value), setRight(value), setBrake(value)
  setControls(left,right,brake), clearControls()
  getStatus() -> {left,right,brake,steeringSignal,angle,extension}

Wheel control values are 0..1.

Runnable example: /rom/thrusters/examples/wheel_mount.lua
]])

add("Claw and rope winch", [[
Peripheral type: claw
  setSignal(signal), clearSignalOverride()
  open(), close(), release()
  getSignal(), getComputerSignal()
  isHolding()
  getHeldConnectorPos(), getSelectedConnectorPos()
  getNearestConnector(), getNearestConnectorInRange(range)
  getConnectorsInRange(range)
  getConnectorsInRangeLimited(range,limit)
  isConnectorInRange(x,y,z)
  isConnectorInRangeWithRadius(x,y,z,range)
  selectConnector(x,y,z), clearSelectedConnector()
  isConnectorReferenceInRange(localX,localY,localZ,subLevelId,range?)
  selectConnectorReference(localX,localY,localZ,subLevelId)
  setReceiverFrequency(firstItemId,secondItemId)
  clearReceiverFrequency(), getReceiverFrequency()
  getStatus(), methods()

Position methods return projected world x/y/z plus localX/localY/localZ and
subLevelId, or nil. Use the reference methods when passing a reported moving
connector back to the peripheral. The original x/y/z selectors remain
available for world and legacy local-coordinate scripts. Signal is 0..15.

Peripheral type: rope_winch_cable
  isConnected(), getRemoteType(), methods()
  Proxies the attached claw methods:
  setSignal, clearSignalOverride, open, close, release, getSignal,
  isHolding, connector position/search/select methods, and getStatus.

Runnable example: /rom/thrusters/examples/claw_control.lua
]])

add("External machines", [[
Create Gadgets & Gizmos supplies compatibility peripherals for selected Simulated, Aeronautics, and Offroad blocks.

Peripheral types may include:
  simulated_throttle_lever, laser_pointer, laser_sensor, analogue_transmission,
  redstone_accumulator, redstone_inductor, redstone_magnet,
  optical_sensor, docking_connector, altitude_sensor,
  hot_air_burner, steam_vent, mounted_potato_cannon, wheel_mount

The generic external-machine interface exposes methods when supported by the target:
  getName(), setName(name)
  getSignal(), setSignal(signal), isPowered()
  getRange(), setRange(range)
  hasHit(), getDistance()
  getColor(), setColor(rgb)
  isRainbow(), setRainbow(enabled)
  getAirPressure(), getWorldHeight(), getGasOutput()
  getState(), isBlocked(), getBlockedLength()
  getFacing(), getWorldFacing(), getPosition(), getClassName()
  getStatus(), methods(), help(method?)

Unsupported target features return neutral values or raise an availability error for writes. Use getStatus() and methods() to inspect the concrete block.
getFacing() is local to the block. getWorldFacing() returns a projected vector,
and getPosition() returns the same sub-level-aware position shape documented
for the navigation table.
]])

add("Runnable examples", [[
Bundled examples

  /rom/thrusters/examples/joystick_monitor.lua
  /rom/thrusters/examples/thruster_control.lua
  /rom/thrusters/examples/bearing_control.lua
  /rom/thrusters/examples/controller_inputs.lua
  /rom/thrusters/examples/navigation.lua
  /rom/thrusters/examples/advanced_data_link.lua
  /rom/thrusters/examples/orientation_source.lua
  /rom/thrusters/examples/wheel_mount.lua
  /rom/thrusters/examples/claw_control.lua
  /rom/thrusters/examples/peripheral_probe.lua

Run one directly:

  /rom/thrusters/examples/joystick_monitor.lua

Or copy one to the computer before editing:

  copy /rom/thrusters/examples/thruster_control.lua startup.lua

Examples fail with a clear message when their required peripheral is not attached. Control examples restore or clear their temporary control state before exiting where appropriate.
]])

--[[--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                  LIVE DOCUMENTATION
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------]]--

-- Build the live peripheral page when the browser opens
local function liveBody()
    local lines = { "Peripherals attached to this computer", "" }
    local names = peripheral.getNames()
    table.sort(names)
    if #names == 0 then
        lines[#lines + 1] = "No peripherals are attached."
        return table.concat(lines, "\n")
    end
    for _, name in ipairs(names) do
        lines[#lines + 1] = name .. " [" .. peripheral.getType(name) .. "]"
        local methods = peripheral.getMethods(name) or {}
        table.sort(methods)
        lines[#lines + 1] = "  " .. table.concat(methods, ", ")
        local ok, help = pcall(peripheral.call, name, "help")
        if ok and type(help) == "table" then
            lines[#lines + 1] = "  This peripheral provides live help()."
        end
        lines[#lines + 1] = ""
    end
    lines[#lines + 1] = "Reopen docs.lua to refresh this list."
    return table.concat(lines, "\n")
end

add("Live peripherals", liveBody())

--[[--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                     PRELOAD / SETUP
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------]]--

local allTopics = topics
local selected, scroll = 1, 0
local searchText = ""

--[[--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                   BROWSER FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------]]--

-- Wrap text while keeping the indentation used by method lists and examples
local function wrapText(text, width)
    local out = {}
    for line in (text .. "\n"):gmatch("(.-)\n") do
        if line == "" then
            out[#out + 1] = ""
        else
            local indent = line:match("^(%s*)") or ""
            local rest = line:sub(#indent + 1)
            while #indent + #rest > width do
                local limit = math.max(1, width - #indent)
                local cut = rest:sub(1, limit):match("^.*()%s+")
                if not cut or cut < 2 then cut = limit end
                out[#out + 1] = indent .. rest:sub(1, cut):gsub("%s+$", "")
                rest = rest:sub(cut + 1):gsub("^%s+", "")
            end
            out[#out + 1] = indent .. rest
        end
    end
    return out
end

-- Update terminal colours when supported
local function setColors(fg, bg)
    if isAdvanced then
        term.setTextColor(fg)
        term.setBackgroundColor(bg)
    end
end

-- Write text at a terminal position
local function writeAt(x, y, text)
    term.setCursorPos(x, y)
    term.write(text)
end

-- Fit text into the available width
local function fit(text, width)
    if #text > width then return text:sub(1, math.max(0, width - 1)) .. ">" end
    return text .. string.rep(" ", width - #text)
end

-- Draw the selected page and return the current scroll limits
local function render()
    local w, h = term.getSize()
    local topic = topics[selected]
    local lines = wrapText(topic.body, w)
    local bodyHeight = math.max(1, h - 2)
    local maxScroll = math.max(0, #lines - bodyHeight)
    scroll = math.max(0, math.min(scroll, maxScroll))

    setColors(colors.white, colors.black)
    term.clear()
    setColors(colors.black, isAdvanced and colors.orange or colors.white)
    writeAt(1, 1, fit((" Thrusters Docs %d/%d: %s"):format(selected, #topics, topic.title), w))
    setColors(colors.white, colors.black)
    for row = 1, bodyHeight do
        writeAt(1, row + 1, fit(lines[scroll + row] or "", w))
    end
    setColors(colors.black, isAdvanced and colors.lightGray or colors.white)
    local footer = isAdvanced and " < Prev   Search   Next >   / search   Q quit "
        or " Left/Right topic  Up/Down scroll  Q quit "
    writeAt(1, h, fit(footer, w))
    setColors(colors.white, colors.black)
    return #lines, bodyHeight
end

-- Move to another documentation topic
local function changeTopic(delta)
    selected = ((selected - 1 + delta) % #topics) + 1
    scroll = 0
end

-- Filter the topic list using the search entered on an advanced computer
local function search()
    if not isAdvanced then return end
    local w, h = term.getSize()
    setColors(colors.white, colors.black)
    term.setCursorPos(1, h)
    term.clearLine()
    term.write("Search: ")
    searchText = read()
    local query = searchText:lower()
    if query == "" then
        topics = allTopics
    else
        topics = {}
        for _, topic in ipairs(allTopics) do
            if topic.title:lower():find(query, 1, true) or topic.body:lower():find(query, 1, true) then
                topics[#topics + 1] = topic
            end
        end
        if #topics == 0 then
            topics = {{ title = "No results", body = "No documentation topics matched: " .. searchText .. "\n\nPress / to search again." }}
        end
    end
    selected, scroll = 1, 0
end

--[[--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           MAIN
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------]]--

-- Main documentation input loop
while true do
    local lineCount, bodyHeight = render()
    local evt, a, b, c = os.pullEvent()
    if evt == "key" then
        if a == keys.q then break
        elseif a == keys.left then changeTopic(-1)
        elseif a == keys.right then changeTopic(1)
        elseif a == keys.up then scroll = math.max(0, scroll - 1)
        elseif a == keys.down then scroll = math.min(math.max(0, lineCount - bodyHeight), scroll + 1)
        elseif a == keys.pageUp then scroll = math.max(0, scroll - bodyHeight)
        elseif a == keys.pageDown then scroll = math.min(math.max(0, lineCount - bodyHeight), scroll + bodyHeight)
        elseif a == keys.home then selected, scroll = 1, 0
        elseif a == keys["end"] then selected, scroll = #topics, 0
        end
    elseif evt == "char" and isAdvanced and a == "/" then
        search()
    elseif evt == "mouse_scroll" and isAdvanced then
        scroll = math.max(0, math.min(math.max(0, lineCount - bodyHeight), scroll + a * 3))
    elseif evt == "mouse_click" and isAdvanced then
        local w, h = term.getSize()
        if c == h then
            if b <= 9 then changeTopic(-1)
            elseif b >= w - 8 then changeTopic(1)
            else search()
            end
        end
    end
end

setColors(colors.white, colors.black)
term.clear()
term.setCursorPos(1, 1)
