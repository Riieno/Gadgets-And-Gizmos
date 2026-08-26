-- Test every thruster owned by one bearing then restore its normal throttle

--[[--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           MAIN
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------]]--

local bearing = assert(peripheral.find("thruster_bearing"), "Attach a thruster bearing")

print("Attached thrusters:", bearing.getThrusterCount())
bearing.setThrottle("all", 0.35)
sleep(2)
textutils.pretty_print(bearing.getThrusterStatus("all"))
bearing.clearThrottleOverride("all")
