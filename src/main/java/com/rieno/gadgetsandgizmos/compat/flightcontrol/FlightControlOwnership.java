package com.rieno.gadgetsandgizmos.compat.flightcontrol;

import com.rieno.gadgetsandgizmos.lib.control.ControllerOwnership;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.List;

// Display the active G&G coordinator while retaining native engine diagnostics
public final class FlightControlOwnership{
    private FlightControlOwnership(){}

    public static void updateTooltip(List<Component> tooltip, ControllerOwnership owner){
        if(!owner.present()) return;
        String prefix = "goggle.create_flight_control.smart_vector_thruster.";
        for(int idx = 0; idx < tooltip.size(); idx++){
            if(!(tooltip.get(idx).getContents() instanceof TranslatableContents val)) continue;
            String key = val.getKey();
            if(key.equals(prefix + "not_linked") || key.equals(prefix + "linked") || key.equals(prefix + "linked_indexed")
                    || key.equals(prefix + "linked_mount") || key.equals(prefix + "linked_mount_indexed")){
                tooltip.set(idx, Component.translatable(owner.displayKey()).withStyle(ChatFormatting.GREEN));
                return;
            }
        }
        tooltip.add(Component.translatable(owner.displayKey()).withStyle(ChatFormatting.GREEN));
    }
}
