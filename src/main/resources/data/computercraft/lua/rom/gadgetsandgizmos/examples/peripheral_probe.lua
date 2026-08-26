-- List every attached peripheral and print its live help when available

--[[--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           MAIN
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------]]--

local names = peripheral.getNames()
table.sort(names)

for _, name in ipairs(names) do
    print(("%s [%s]"):format(name, peripheral.getType(name)))
    local methods = peripheral.getMethods(name)
    table.sort(methods)
    print("  " .. table.concat(methods, ", "))
    local hasHelp = false
    for _, method in ipairs(methods) do
        if method == "help" then hasHelp = true break end
    end
    if hasHelp then
        local ok, res = pcall(peripheral.call, name, "help")
        if ok then textutils.pretty_print(res) end
    end
end
