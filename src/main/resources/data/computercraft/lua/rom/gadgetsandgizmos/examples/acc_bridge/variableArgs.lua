local acc = require("gng.acc").find()

local tArgs = {...}

if #tArgs <= 1 or tArgs[1] == "help" then 
    print("Usage:")
    print("[Variable Name] [get/set] [value:optional]")
    print("Examples: variableArgs.lua variable get | variableArgs.lua variable set 10")
    return false
end

if #tArgs > 1 then
    if tArgs[2] == "get" then 
        print(acc:variable(tArgs[1]):get())
        return
    end
    if tArgs[2] == "set" then
        if tArgs[3] == "" or tArgs[3] == nil or not tArgs[3] then 
                print("Missing Argument!")
                print("Usage:")
                print("[Variable Name] [get/set] [value:optional]")
                print("Examples: variableArgs.lua variable get | variableArgs.lua variable set 10")
                return
        end
        return acc:variable(tArgs[1]):set(tArgs[3])
    end
end
