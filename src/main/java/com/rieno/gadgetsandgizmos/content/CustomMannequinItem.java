package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.item.ProfileHeadItem;
import com.rieno.gadgetsandgizmos.lib.profile.PlayerSkinLookup;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

// Place a named player mannequin from a command-only head item
public final class CustomMannequinItem extends ProfileHeadItem{
    // Initialize the command-only player head
    public CustomMannequinItem(Properties props){
        super(props, "customName");
    }

    // Create a recoverable custom mannequin item with its original player name
    public static ItemStack createStack(String name){
        ItemStack stack = new ItemStack(CTItems.CUSTOM_MANNEQUIN.get());
        CompoundTag data = new CompoundTag();
        data.putString("customName", name);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    // Place the normal supporter entity with the requested remote skin and name
    @Override
    protected InteractionResult useProfileOn(UseOnContext ctx, PlayerSkinLookup.LookupResult skin){
        if(!CTFeatureToggles.isItemEnabled("player_mannequin")
                || !CTFeatureToggles.isEntityEnabled("player_mannequin")) return InteractionResult.FAIL;
        return SupporterMannequinPlacement.useOn(ctx, entity -> {
            entity.setVariant(PlayerMannequinVariants.DEFAULT_ID);
            entity.setOriginalSupporterVariant(entity.getVariant());
            entity.setRemoteSkinUrl(skin.skinUrl());
            entity.setSteveSkin(false);
            entity.setSlimSkin(skin.slim());
            entity.setCustomMannequinName(skin.playerName());
            entity.setCustomName(Component.literal(skin.playerName()));
            entity.setCustomNameVisible(false);
            entity.applyMannequinDefaults();
        });
    }

    // Leave the item intact when the player name or skin service is unavailable
    @Override
    protected void onProfileUnavailable(UseOnContext ctx, PlayerSkinLookup.Status status){
        if(ctx.getPlayer() == null) return;
        String key = status == PlayerSkinLookup.Status.PLAYER_DOES_NOT_EXIST
                ? "invalid_player" : "unavailable";
        ctx.getPlayer().displayClientMessage(Component.translatable(
                "item.createthrusters.custom_mannequin." + key), true);
    }
}
