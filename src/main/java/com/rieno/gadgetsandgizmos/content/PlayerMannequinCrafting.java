package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

import java.util.List;

// Copy player skin and pose data onto mannequin crafting results
public final class PlayerMannequinCrafting {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final List<Item> SAMPLE_SKULLS = List.of(
            Items.PLAYER_HEAD,
            Items.ZOMBIE_HEAD,
            Items.SKELETON_SKULL,
            Items.WITHER_SKELETON_SKULL,
            Items.CREEPER_HEAD,
            Items.PIGLIN_HEAD,
            Items.DRAGON_HEAD);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the player mannequin crafting
    private PlayerMannequinCrafting() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is mob head
    public static boolean isMobHead(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.SKULLS);
    }

    // Get the variant from name tag
    public static PlayerMannequinVariant variantFromNameTag(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.NAME_TAG)) {
            return null;
        }
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        return customName == null ? null : variantFromName(customName.getString());
    }

    // Get the variant from name
    public static PlayerMannequinVariant variantFromName(String name) {
        return PlayerMannequinVariants.byNameTag(name);
    }

    // Get the named name tag
    public static ItemStack namedNameTag(PlayerMannequinVariant variant) {
        ItemStack stack = new ItemStack(Items.NAME_TAG);
        stack.set(DataComponents.CUSTOM_NAME, variant.displayName());
        return stack;
    }

    // Sample the skulls
    public static List<ItemStack> sampleSkulls() {
        return SAMPLE_SKULLS.stream()
                .map(ItemStack::new)
                .toList();
    }

    // Get the named sample skulls
    public static List<ItemStack> namedSampleSkulls(PlayerMannequinVariant variant) {
        return sampleSkulls().stream()
                .map(stack -> {
                    ItemStack named = stack.copy();
                    named.set(DataComponents.CUSTOM_NAME, variant.displayName());
                    return named;
                })
                .toList();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the anvil update event
    public static void onAnvilUpdate(AnvilUpdateEvent evt) {
        if (!CTFeatureToggles.isItemEnabled("player_mannequin")
                || !evt.getRight().isEmpty()
                || !isMobHead(evt.getLeft())) {
            return;
        }

        PlayerMannequinVariant variant = variantFromName(evt.getName());
        if (variant == null) {
            return;
        }

        evt.setOutput(SupporterHeads.createStack(variant));
        evt.setCost(1);
        evt.setMaterialCost(0);
    }
}
