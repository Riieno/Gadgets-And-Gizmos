package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import com.rieno.gadgetsandgizmos.lib.inventory.PaletteDyeStorage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// Keep the confirmed preset and real dye counts on the reusable item
public final class ThrusterDyeData{
    private ThrusterDyeData(){}
    public static CompoundTag read(ItemStack stack){
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("ThrusterDye");
    }
    public static ColorEffectPreset preset(ItemStack stack){ return ColorEffectPreset.read(read(stack).getCompound("Preset")); }
    public static PaletteDyeStorage storage(ItemStack stack){
        var storage = new PaletteDyeStorage();
        storage.read(read(stack));
        return storage;
    }
    public static void write(ItemStack stack, PaletteDyeStorage storage, ColorEffectPreset preset){
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var tag = storage.write();
        tag.put("Preset", preset.write());
        data.put("ThrusterDye", tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }
}
