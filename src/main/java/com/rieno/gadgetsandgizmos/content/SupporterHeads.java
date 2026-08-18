package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.item.BundledSupporterHeadStack;
import com.rieno.gadgetsandgizmos.registry.CTDataComponents;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// Create vanilla player heads for supporter mannequin variants
public final class SupporterHeads {
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           Constants
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final String TOOLTIP_PREFIX = "item.createthrusters.player_mannequin.tooltip.";

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        PRELOAD / SETUP
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the supporter heads
    private SupporterHeads() {
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                           Functions
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Create the player head for a supporter variant
    public static ItemStack createStack(PlayerMannequinVariant variant) {
        PlayerMannequinVariant resolved = variant == null
                ? PlayerMannequinVariants.byIdOrDefault(PlayerMannequinVariants.DEFAULT_ID)
                : variant;
        if (resolved == null || resolved.skinTexture() == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = BundledSupporterHeadStack.create(resolved.skinTexture(), resolved.displayName());
        stack.set(CTDataComponents.PLAYER_MANNEQUIN_VARIANT, resolved.id());
        return stack;
    }

    // Check if this is a locally textured supporter mannequin head
    public static boolean isSupporterHead(ItemStack stack) {
        if (stack == null || !stack.has(CTDataComponents.PLAYER_MANNEQUIN_VARIANT)) {
            return false;
        }
        PlayerMannequinVariant variant = PlayerMannequinVariants.byId(
                stack.get(CTDataComponents.PLAYER_MANNEQUIN_VARIANT));
        return variant != null
                && variant.skinTexture() != null
                && variant.skinTexture().equals(BundledSupporterHeadStack.texture(stack));
    }

    // Get the enabled mannequin variant from a marked supporter head
    public static PlayerMannequinVariant getVariant(ItemStack stack) {
        if (!CTFeatureToggles.isItemEnabled("player_mannequin") || !isSupporterHead(stack)) {
            return null;
        }
        return PlayerMannequinVariants.byId(stack.get(CTDataComponents.PLAYER_MANNEQUIN_VARIANT));
    }

    // Add the supporter mannequin tooltip to a marked player head
    public static void appendTooltip(ItemStack stack, List<Component> tooltip, boolean showSummary) {
        PlayerMannequinVariant variant = getVariant(stack);
        if (variant == null) {
            return;
        }
        tooltip.add(Component.translatable(TOOLTIP_PREFIX + "title").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(
                variant.thanksTranslationKey(), variant.displayName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                TOOLTIP_PREFIX + "reason",
                Component.translatable(variant.reasonTranslationKey())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(TOOLTIP_PREFIX + "usage").withStyle(ChatFormatting.GRAY));
        if (showSummary) {
            tooltip.add(Component.translatable(TOOLTIP_PREFIX + "summary.crafting").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(TOOLTIP_PREFIX + "summary.anvil").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(TOOLTIP_PREFIX + "summary.loot").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(TOOLTIP_PREFIX + "hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
