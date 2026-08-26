local acc = require("gng.acc").find()

local graphVariable = acc:variable("variable")

function printVariable()
    while true do
        term.setCursorPos(1,1)
        print(graphVariable:get())
        sleep(0)
    end
end

function writeVariable()
    while true do
    local e = {os.pullEvent()}
    if e[1] == "key" then
        if e[2] == keys.up then
            graphVariable:set(graphVariable:get() + 1)
        end
        if e[2] == keys.down then
            graphVariable:set(graphVariable:get() - 1)
        end
    end
    sleep(0)
    end
end

parallel.waitForAll(printVariable,  writeVariable)
