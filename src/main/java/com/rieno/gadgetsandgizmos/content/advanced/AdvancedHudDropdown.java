package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.lib.display.DropdownOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.List;
import java.util.function.Function;

// Adapt bound graph collections to the shared dropdown option model
public final class AdvancedHudDropdown{
    public static final String TYPE = "dropdown";
    public static final int MAX_KEY_LENGTH = 1024;
    private AdvancedHudDropdown(){}

    public static List<DropdownOptions.Option> options(CompoundTag elm, Function<String, AdvancedGraphDocument.Value> values){
        String port = AdvancedHudElementBinding.port(elm, DropdownOptions.PROPERTY);
        if(port.isBlank()) return List.of();
        var source = values == null ? null : values.apply(port);
        if(source == null && elm.contains("DropdownOptions")) source = AdvancedGraphDocument.Value.fromTag(elm.getCompound("DropdownOptions"));
        return DropdownOptions.entries(GraphRuntime.toLibraryValue(source));
    }

    public static String selectedKey(CompoundTag elm, List<DropdownOptions.Option> options, AdvancedGraphDocument.Value value){
        if(elm.contains("SelectedKey") && DropdownOptions.find(options, elm.getString("SelectedKey")) != null) return elm.getString("SelectedKey");
        return DropdownOptions.selectedKey(options, GraphRuntime.toLibraryValue(value));
    }

    // Send option identities and labels while keeping selection values authoritative on the server
    public static CompoundTag popup(CompoundTag elm, Function<String, AdvancedGraphDocument.Value> values){
        var res = AdvancedHudElementBinding.resolvedCopy(elm, values);
        var options = options(elm, values);
        ListTag entries = new ListTag();
        for(var option : options){
            if(option.key().length() > MAX_KEY_LENGTH) continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("Key", option.key()); entry.putString("Label", option.label()); entries.add(entry);
        }
        res.remove("DropdownOptions");
        res.put("DropdownEntries", entries);
        String key = selectedKey(elm, options, values.apply(elm.getString("ValuePort")));
        if(key != null) res.putString("SelectedKey", key);
        return res;
    }
}
