local sides = {"left", "right", "front", "back", "top", "bottom"}

function getModem()
    for i,v in pairs(sides) do
        if peripheral.hasType(v, "modem") then 
            return v
        end
    end
end

rednet.open(getModem())
-- RECIEVE A REDNET NAMED EVENT MESSAGE
local function receiveEvent()
    local msg = ""

    while true do
        local id, message = rednet.receive()
        if msg ~= message then
            msg = message
            term.clear()
            term.setCursorPos(1,1)
            print(msg)
        end
        sleep(0)
    end
end

-- SEND A NAMED EVENT OVER REDNET
function sendEvent(event, msg)
    rednet.broadcast(msg, event)
end

local tArgs = {...}
if tArgs[1] == "receive" or tArgs[1] == "rec" then
    receiveEvent()
end

if tArgs[1] == "send" or tArgs[1] == "broadcast" then
    if not tArgs[2] or not tArgs[3] then
        print("Missing Argument")
        print("Usage:")
        print("[recieve/send] [event_name] [data]")
        print("Examples: rednet_events.lua send event hello world!")
        return
    end

    sendEvent(tArgs[2], tArgs[3])
end


rednet.close(getModem())