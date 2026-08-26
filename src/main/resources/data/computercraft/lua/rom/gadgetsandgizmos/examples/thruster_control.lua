-- Basic thruster test | Gives control back to redstone when finished

--[[--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           MAIN
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------]]--

local thruster = assert(peripheral.find("thruster"), "Attach a thruster")

thruster.setEnabled(true)
thruster.setThrottle(0.5)
sleep(2)
textutils.pretty_print(thruster.getStatus())
thruster.clearThrottleOverride()
