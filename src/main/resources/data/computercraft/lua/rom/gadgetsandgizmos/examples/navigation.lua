-- Read the selected navigation target without changing the table

--[[--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           MAIN
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------]]--

local nav = assert(peripheral.find("navigation_table"), "Attach a navigation table")

print("State:", nav.getState())
print("Selected slot:", nav.getSelectedSlot())
textutils.pretty_print(nav.getSelectedMapInfo())
if nav.hasTarget() then
    print("Distance:", nav.getTargetDistance())
end
