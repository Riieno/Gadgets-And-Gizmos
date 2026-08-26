package com.rieno.gadgetsandgizmos.compat.computercraft.api;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// Add generated version and help methods without consuming a class inheritance slot
public interface DocumentedPeripheral extends IPeripheral {
    @LuaFunction
    @PeripheralDoc(name = "getApiVersion", signature = "getApiVersion(): number",
            description = "Returns the stable Gadgets & Gizmos CC API version")
    default int getApiVersion() {
        return 2;
    }

    @LuaFunction
    @PeripheralDoc(name = "methods", signature = "methods(): table",
            description = "Lists the documented Lua signatures exposed by this peripheral")
    default List<String> methods() {
        return PeripheralDocumentation.catalog(getClass()).signatures();
    }

    @LuaFunction
    @PeripheralDoc(name = "help", signature = "help([method: string]): table|string",
            description = "Returns all method help or the help text for one method")
    default Object help(Optional<String> method) throws LuaException {
        Map<String, String> docs = PeripheralDocumentation.catalog(getClass()).help();
        if (method.isEmpty()) {
            return docs;
        }
        String value = docs.get(method.get());
        if (value == null) {
            throw new LuaException("unknown method '" + method.get() + "'");
        }
        return value;
    }
}
