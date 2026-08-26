-- Browse generated Gadgets & Gizmos peripheral documentation
local loaded, catalog = pcall(require, "gadgetsandgizmos.docs_catalog")
if not loaded then
    error("Gadgets & Gizmos documentation is missing; regenerate docs_catalog.lua", 0)
end
if type(catalog) ~= "table" then
    error("gadgetsandgizmos.docs_catalog did not return a table", 0)
end

local args = { ... }
if args[1] == "--help" or args[1] == "-h" then
    print("Usage: ctdocs [search words]")
    print("Browse generated peripheral documentation and attached devices.")
    print("Inside the browser: arrows navigate, / searches, and Q quits.")
    return
end

local isAdvanced = term.isColor and term.isColor()
local oldText = term.getTextColor and term.getTextColor() or colors.white
local oldBackground = term.getBackgroundColor
        and term.getBackgroundColor() or colors.black
local oldBlink = term.getCursorBlink and term.getCursorBlink() or false
local topics = {}

local function sortedKeys(value)
    local keysOut = {}
    for key in pairs(value or {}) do
        if type(key) == "string" then
            keysOut[#keysOut + 1] = key
        end
    end
    table.sort(keysOut)
    return keysOut
end

local function add(title, body)
    topics[#topics + 1] = {
        title = tostring(title),
        body = tostring(body),
    }
end

local function findCatalogType(typeName)
    if catalog[typeName] then return typeName, catalog[typeName] end
    local bestKey, bestMethods, bestLength = nil, nil, -1
    for key, methods in pairs(catalog) do
        if type(key) == "string" and key:sub(-1) == "*" then
            local prefix = key:sub(1, -2)
            if typeName:sub(1, #prefix) == prefix and #prefix > bestLength then
                bestKey, bestMethods, bestLength = key, methods, #prefix
            end
        end
    end
    return bestKey, bestMethods
end

local function entryBody(typeName, methodName, entry)
    local lines = {
        entry.signature or methodName .. "()",
        "",
        entry.description or "No description supplied.",
        "",
        "Peripheral type: " .. typeName,
        "Since API: " .. tostring(entry.since or "unknown"),
    }
    if entry.deprecatedBy and entry.deprecatedBy ~= "" then
        lines[#lines + 1] = "Deprecated; use: " .. entry.deprecatedBy
    end
    if type(entry.examples) == "table" and #entry.examples > 0 then
        lines[#lines + 1] = ""
        lines[#lines + 1] = "Examples"
        for _, example in ipairs(entry.examples) do
            lines[#lines + 1] = "  " .. tostring(example)
        end
    end
    return table.concat(lines, "\n")
end

add("Welcome", table.concat({
    "Gadgets & Gizmos CC:Tweaked Reference",
    "",
    "This browser is generated from the Java methods which implement the Lua API.",
    "It also shows peripherals currently attached to this computer.",
    "",
    "Controls",
    "  Left/Right: previous or next topic",
    "  Up/Down: scroll one line",
    "  Page Up/Page Down: scroll one page",
    "  Home/End: first or last topic",
    "  /: search titles and bodies",
    "  Q: quit",
}, "\n"))

for _, typeName in ipairs(sortedKeys(catalog)) do
    local methods = catalog[typeName]
    local names = sortedKeys(methods)
    local overview = {
        "Peripheral type: " .. typeName,
        "",
        "Documented methods (" .. #names .. ")",
    }
    for _, methodName in ipairs(names) do
        local entry = methods[methodName]
        overview[#overview + 1] = "  " .. (entry.signature or methodName .. "()")
    end
    if typeName == "monitor" then
        overview[#overview + 1] = ""
        overview[#overview + 1] = "Standard CC:Tweaked monitor and terminal methods:"
        overview[#overview + 1] = "  https://tweaked.cc/peripheral/monitor.html"
        overview[#overview + 1] = "  https://tweaked.cc/module/term.html"
    end
    add(typeName, table.concat(overview, "\n"))
    for _, methodName in ipairs(names) do
        add(typeName .. "." .. methodName,
                entryBody(typeName, methodName, methods[methodName]))
    end
end

local function liveBody()
    local lines = { "Peripherals attached to this computer", "" }
    local names = peripheral.getNames()
    table.sort(names)
    if #names == 0 then
        lines[#lines + 1] = "No peripherals are attached."
        return table.concat(lines, "\n")
    end

    for _, name in ipairs(names) do
        local typeName = peripheral.getType(name) or "unknown"
        lines[#lines + 1] = name .. " [" .. typeName .. "]"
        local methods = peripheral.getMethods(name) or {}
        table.sort(methods)
        lines[#lines + 1] = "  " .. table.concat(methods, ", ")

        local catalogKey = findCatalogType(typeName)
        if catalogKey then
            lines[#lines + 1] = "  Catalogue: " .. catalogKey
        else
            lines[#lines + 1] = "  Catalogue: no generated entry"
        end

        local ok, liveHelp = pcall(peripheral.call, name, "help")
        if ok and type(liveHelp) == "table" then
            lines[#lines + 1] = "  Live help() entries:"
            for _, methodName in ipairs(sortedKeys(liveHelp)) do
                lines[#lines + 1] = "    " .. methodName .. ": "
                        .. tostring(liveHelp[methodName])
            end
        elseif ok and type(liveHelp) == "string" then
            lines[#lines + 1] = "  Live help: " .. liveHelp
        else
            lines[#lines + 1] = "  Live help() is unavailable"
        end
        lines[#lines + 1] = ""
    end
    lines[#lines + 1] = "Reopen ctdocs to rescan attached peripherals."
    return table.concat(lines, "\n")
end

add("Live peripherals", liveBody())

local allTopics = topics
local visibleTopics = allTopics
local selected, scroll = 1, 0
local searchText = ""

local function wrapText(text, width)
    width = math.max(1, width)
    local out = {}
    for line in (text .. "\n"):gmatch("(.-)\n") do
        if line == "" then
            out[#out + 1] = ""
        else
            local indent = line:match("^(%s*)") or ""
            local rest = line:sub(#indent + 1)
            while #indent + #rest > width do
                local limit = math.max(1, width - #indent)
                local cut = rest:sub(1, limit):match("^.*()%s+")
                if not cut or cut < 2 then cut = limit end
                out[#out + 1] = indent
                        .. rest:sub(1, cut):gsub("%s+$", "")
                rest = rest:sub(cut + 1):gsub("^%s+", "")
            end
            out[#out + 1] = indent .. rest
        end
    end
    return out
end

local function setColors(foreground, background)
    if term.setTextColor then term.setTextColor(foreground) end
    if term.setBackgroundColor then term.setBackgroundColor(background) end
end

local function bodyColors()
    setColors(colors.white, colors.black)
end

local function headerColors()
    if isAdvanced then
        setColors(colors.black, colors.orange)
    else
        bodyColors()
    end
end

local function footerColors()
    if isAdvanced then
        setColors(colors.black, colors.lightGray)
    else
        bodyColors()
    end
end

local function writeAt(x, y, text)
    term.setCursorPos(x, y)
    term.write(text)
end

local function fit(text, width)
    if width <= 0 then return "" end
    if #text > width then
        if width == 1 then return ">" end
        return text:sub(1, width - 1) .. ">"
    end
    return text .. string.rep(" ", width - #text)
end

local function render()
    local width, height = term.getSize()
    local topic = visibleTopics[selected]
    local lines = wrapText(topic.body, width)
    local bodyHeight = math.max(1, height - 2)
    local maxScroll = math.max(0, #lines - bodyHeight)
    scroll = math.max(0, math.min(scroll, maxScroll))

    bodyColors()
    term.clear()
    headerColors()
    writeAt(1, 1, fit((" CT Docs %d/%d: %s"):format(
            selected, #visibleTopics, topic.title), width))
    bodyColors()
    for row = 1, bodyHeight do
        writeAt(1, row + 1, fit(lines[scroll + row] or "", width))
    end
    footerColors()
    local footer = isAdvanced
            and " < Prev   / Search   Next >   Q quit "
            or " Left/Right  Up/Down  / Search  Q quit "
    writeAt(1, height, fit(footer, width))
    bodyColors()
    return #lines, bodyHeight
end

local function changeTopic(delta)
    selected = ((selected - 1 + delta) % #visibleTopics) + 1
    scroll = 0
end

local function filteredTopics(query)
    local normalized = query:lower()
    if normalized == "" then return allTopics end
    local matches = {}
    for _, topic in ipairs(allTopics) do
        if topic.title:lower():find(normalized, 1, true)
                or topic.body:lower():find(normalized, 1, true) then
            matches[#matches + 1] = topic
        end
    end
    if #matches == 0 then
        return {{
            title = "No results",
            body = "No documentation topics matched: " .. query
                    .. "\n\nPress / to search again.",
        }}
    end
    return matches
end

local function applySearch(query)
    searchText = query
    visibleTopics = filteredTopics(query)
    selected, scroll = 1, 0
end

local function promptSearch()
    local _, height = term.getSize()
    bodyColors()
    term.setCursorPos(1, height)
    term.clearLine()
    term.write("Search: ")
    applySearch(read())
end

local function restoreTerminal()
    if term.setCursorBlink then term.setCursorBlink(oldBlink) end
    setColors(oldText, oldBackground)
    term.clear()
    term.setCursorPos(1, 1)
end

local function run()
    if #args > 0 then applySearch(table.concat(args, " ")) end
    if term.setCursorBlink then term.setCursorBlink(false) end
    while true do
        local lineCount, bodyHeight = render()
        local event, first, x, y = os.pullEvent()
        if event == "key" then
            if first == keys.q then break
            elseif first == keys.left then changeTopic(-1)
            elseif first == keys.right then changeTopic(1)
            elseif first == keys.up then scroll = math.max(0, scroll - 1)
            elseif first == keys.down then
                scroll = math.min(math.max(0, lineCount - bodyHeight), scroll + 1)
            elseif first == keys.pageUp then
                scroll = math.max(0, scroll - bodyHeight)
            elseif first == keys.pageDown then
                scroll = math.min(math.max(0, lineCount - bodyHeight),
                        scroll + bodyHeight)
            elseif first == keys.home then selected, scroll = 1, 0
            elseif first == keys["end"] then selected, scroll = #visibleTopics, 0
            end
        elseif event == "char" and first == "/" then
            promptSearch()
        elseif event == "mouse_scroll" and isAdvanced then
            scroll = math.max(0, math.min(
                    math.max(0, lineCount - bodyHeight), scroll + first * 3))
        elseif event == "mouse_click" and isAdvanced then
            local width, height = term.getSize()
            if y == height then
                if x <= 9 then changeTopic(-1)
                elseif x >= width - 8 then changeTopic(1)
                else promptSearch() end
            end
        end
    end
end

local ok, failure = pcall(run)
restoreTerminal()
if not ok then error(failure, 0) end
