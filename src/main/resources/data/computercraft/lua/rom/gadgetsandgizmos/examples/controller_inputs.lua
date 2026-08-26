-- Pulse the first configured controller input then reset it

--[[--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           MAIN
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------]]--

local controller = assert(peripheral.find("analogue_contraption_controller"),
    "Attach an analogue contraption controller")

local ids = controller.listInputIds()
assert(#ids > 0, "Configure at least one controller input")
local id = ids[1]
print("Driving input:", id)
controller.setInput(id, 1)
sleep(1)
controller.resetInput(id)
textutils.pretty_print(controller.getInput(id))
