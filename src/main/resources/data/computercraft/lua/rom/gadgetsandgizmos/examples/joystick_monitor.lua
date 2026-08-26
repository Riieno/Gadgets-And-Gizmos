-- Live joystick monitor | Hold Q to close it

--[[--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           MAIN
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------]]--

local joystick = assert(peripheral.find("analogue_joystick"), "Attach an analogue joystick")

while true do
    local tilt = joystick.getTilt()
    local redstone = joystick.getRedstone()
    term.clear()
    term.setCursorPos(1, 1)
    print("Analogue Joystick")
    print(("X: %6.2f  Z: %6.2f"):format(tilt.x, tilt.z))
    print(("Magnitude: %.2f"):format(tilt.magnitude))
    print("Held:", tilt.held, "Active:", tilt.active)
    print(("F:%2d B:%2d L:%2d R:%2d"):format(
        redstone.forward, redstone.backward, redstone.left, redstone.right))
    print("Hold Q to exit")
    local timer = os.startTimer(0.1)
    while true do
        local evt, val = os.pullEvent()
        if evt == "key" and val == keys.q then return end
        if evt == "timer" and val == timer then break end
    end
end
