package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.RcsThrusterBlock;
import com.rieno.gadgetsandgizmos.content.ThrusterBlock;
import com.rieno.gadgetsandgizmos.lib.tooltip.TooltipComponents;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Set;

// Identify supported thruster items without loading optional mod implementation classes
public final class ThrusterDyeTooltips{
    private static final Set<String> FLIGHT_CONTROL = Set.of("smart_vector_thruster", "smart_vector_thruster_small",
            "smart_vector_ion_thruster", "smart_vector_ion_thruster_small",
            "creative_smart_vector_thruster", "creative_smart_vector_thruster_small");
    private static final Set<String> PROPULSION = Set.of("thruster", "solid_fuel_thruster", "creative_thruster",
            "ion_thruster", "vector_thruster", "creative_vector_thruster", "liquid_vector_thruster");

    private ThrusterDyeTooltips(){}

    public static void onItemTooltip(ItemTooltipEvent evt){
        if(CTItems.THRUSTER_DYE == null || !compatible(evt.getItemStack())) return;
        TooltipComponents.appendOnce(evt.getToolTip(), Component.translatable("createthrusters.thruster_dye.compatible")
                .withStyle(ChatFormatting.YELLOW));
    }

    private static boolean compatible(ItemStack stack){
        if(stack.isEmpty()) return false;
        var item = stack.getItem();
        if(item instanceof BlockItem block && (block.getBlock() instanceof ThrusterBlock || block.getBlock() instanceof RcsThrusterBlock)) return true;
        var id = BuiltInRegistries.ITEM.getKey(item);
        return switch(id.getNamespace()){
            case "create_flight_control" -> FLIGHT_CONTROL.contains(id.getPath()) && CTConfigs.COMMON.dyeApplicatorFlightControl.get();
            case "createpropulsion" -> PROPULSION.contains(id.getPath()) && CTConfigs.COMMON.dyeApplicatorPropulsionSimulated.get();
            default -> false;
        };
    }
}
