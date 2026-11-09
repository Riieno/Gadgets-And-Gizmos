package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.rieno.gadgetsandgizmos.lib.client.create.ConnectedTextureModel;
import com.rieno.gadgetsandgizmos.lib.client.create.StateConnectedTextureBehaviour;
import com.rieno.gadgetsandgizmos.lib.client.create.HorizontalPanelConnectedTextureBehaviour;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// Choose display border connections so adjacent panels render as one surface
public final class AccDisplayConnectedTextures {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether ACC display connected textures are registered
    private static boolean registered;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<Block, List<ConnectedTextureBehaviour>> BEHAVIOURS = new IdentityHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ACC display connected textures
    private AccDisplayConnectedTextures() {
    }

    // Register the ACC display connected textures
    public static void register() {
        if (registered || CTBlocks.ACC_DISPLAY == null) {
            return;
        }
        registered = true;
        CTSpriteShiftEntry fullScreen = shift(AllCTTypes.OMNIDIRECTIONAL,
                "advanced_display", "advanced_display_connected");
        CTSpriteShiftEntry fullBorder = shift(AllCTTypes.OMNIDIRECTIONAL,
                "advanced_display_border", "advanced_display_border_connected");
        CTSpriteShiftEntry smallScreen = shift(AllCTTypes.HORIZONTAL_KRYPPERS,
                "advanced_display_small", "advanced_display_small_connected");
        CTSpriteShiftEntry smallBorder = shift(AllCTTypes.HORIZONTAL_KRYPPERS,
                "advanced_display_small_border", "advanced_display_small_border_connected");

        register(CTBlocks.ACC_DISPLAY.get(), new StateConnectedTextureBehaviour(fullScreen, fullBorder));
        register(CTBlocks.ACC_DISPLAY_BLOCK.get(), new StateConnectedTextureBehaviour(fullScreen, fullBorder));
        register(CTBlocks.ACC_DISPLAY_PANEL.get(), new StateConnectedTextureBehaviour(fullScreen, fullBorder));
        register(CTBlocks.ACC_DISPLAY_HALF_PANEL.get(), new HorizontalPanelConnectedTextureBehaviour(smallScreen, smallBorder));
        register(CTBlocks.ACC_DISPLAY_SLAB.get(), new HorizontalPanelConnectedTextureBehaviour(smallScreen, smallBorder));

        List<ConnectedTextureBehaviour> casing = AccDisplayCasingTextures.create();
        for(Block block : List.of(CTBlocks.ACC_DISPLAY.get(), CTBlocks.ACC_DISPLAY_BLOCK.get(),
                CTBlocks.ACC_DISPLAY_PANEL.get(), CTBlocks.ACC_DISPLAY_HALF_PANEL.get(), CTBlocks.ACC_DISPLAY_SLAB.get())){
            for(var behaviour : casing) register(block, behaviour);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve an addon sprite and its connected atlas
    private static CTSpriteShiftEntry shift(com.simibubi.create.foundation.block.connected.CTType type,
                                             String src, String connected) {
        return CTSpriteShifter.getCT(type,
                ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "block/" + src),
                ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "block/" + connected));
    }

    // Register the ACC display connected textures
    private static void register(Block block, ConnectedTextureBehaviour behaviour) {
        BEHAVIOURS.computeIfAbsent(block, ignored -> new ArrayList<>()).add(behaviour);
    }

    // Wrap the ACC display connected textures
    public static BakedModel wrap(Block block, BakedModel model) {
        List<ConnectedTextureBehaviour> behaviours = BEHAVIOURS.get(block);
        if(behaviours == null || model instanceof ConnectedTextureModel) return model;
        return new ConnectedTextureModel(model, behaviours.toArray(ConnectedTextureBehaviour[]::new));
    }

}
