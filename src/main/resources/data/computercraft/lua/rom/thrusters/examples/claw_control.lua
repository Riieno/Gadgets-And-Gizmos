-- Read the attached claw then release anything it is holding

--[[--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           MAIN
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------]]--

local claw = peripheral.find("claw") or peripheral.find("rope_winch_cable")
assert(claw, "Attach a claw or connected rope winch cable")

print("Holding:", claw.isHolding())
textutils.pretty_print(claw.getStatus())
claw.release()
