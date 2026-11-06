package com.rieno.gadgetsandgizmos.content;
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.tooltip.TooltipComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Locale;

// Build the standard tooltip rows used by addon blocks and items
public final class CTTooltipHelper {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT tooltip
    private CTTooltipHelper() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the create description
    public static void addCreateDescription(Item item, List<Component> tooltip) {
        TooltipComponents.appendCreateDescription(item, tooltip);
    }

    // Get the title
    public static Component title(Component title) {
        return title.copy().withStyle(ChatFormatting.GOLD);
    }

    // Build one display line
    public static Component line(String label, Component val) {
        return Component.literal(label).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                .append(val);
    }

    // Build one display line
    public static Component line(Component label, Component val) {
        return label.copy().withStyle(ChatFormatting.GRAY)
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                .append(val);
    }

    // Get the value
    public static MutableComponent value(String text, ChatFormatting col) {
        return Component.literal(text).withStyle(col);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Format the on/off text
    public static MutableComponent onOff(boolean active) {
        return Component.translatable(active ? "createthrusters.thruster.enabled_on" : "createthrusters.thruster.enabled_off")
                .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    // Get the ready idle
    public static MutableComponent readyIdle(boolean active) {
        return Component.translatable(active ? "createthrusters.text.ready" : "createthrusters.navigation_table.state.idle")
                .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
    }

    // Get the linked
    public static MutableComponent linked(boolean linked) {
        return Component.translatable(linked ? "createthrusters.text.linked" : "createthrusters.text.not_linked")
                .withStyle(linked ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    // Show the goggle details
    public static boolean showGoggleDetails(boolean isPlayerSneaking) {
        return isPlayerSneaking;
    }

    // Get the percent
    public static String percent(double val) {
        return Math.round(val * 100.0D) + "%";
    }

    // Get the degrees
    public static String degrees(double val) {
        return String.format(Locale.ROOT, "%.1f deg", val);
    }

    // Get the signed percent
    public static String signedPercent(double val) {
        return String.format(Locale.ROOT, "%+.0f%%", val * 100.0D);
    }

    // Get the signed decimal
    public static String signedDecimal(double val) {
        return String.format(Locale.ROOT, "%+.2f", val);
    }
}
