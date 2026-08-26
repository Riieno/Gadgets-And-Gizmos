local openedWireless = false

peripheral.find("modem", function(name, modem)
    if modem.isWireless() then
        rednet.open(name)
        openedWireless = true
    end
    return false
end)

assert(openedWireless, "Attach a wireless modem to this computer")

local namedEvent = "telemetry:update"

rednet.broadcast({ request = "status" }, namedEvent)

local sender, message = rednet.receive(namedEvent, 5)
if sender == nil then
    printError("No ACC Named Event received")
else
    print("Received " .. namedEvent .. " from " .. sender)
    textutils.pretty_print(message)
end
