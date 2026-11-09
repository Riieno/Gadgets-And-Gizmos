package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlock;
import com.rieno.gadgetsandgizmos.lib.client.create.AtlasConnectedTextureBehaviour;
import com.rieno.gadgetsandgizmos.lib.client.create.AtlasConnectedTextureBehaviour.Orientation;
import com.rieno.gadgetsandgizmos.lib.client.create.AtlasConnectedTextureBehaviour.Patch;
import com.rieno.gadgetsandgizmos.lib.client.create.AtlasConnectedTextureBehaviour.Region;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

// Describe the connected ACC vent strips and thin panel rails
final class AccDisplayCasingTextures{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Index strips by neighbours above and below: single, bottom, top, middle
    private static final List<Region> WIDE = List.of(side(0, 0, 16, 16), side(16, 16, 16, 16),
            side(0, 16, 16, 16), side(16, 0, 16, 16));
    private static final List<Region> NARROW = List.of(side(32, 0, 8, 16), side(48, 16, 8, 16),
            side(32, 16, 8, 16), side(48, 0, 8, 16));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep casing atlas selection separate from the working display fronts
    private AccDisplayCasingTextures(){}

    // Bind the perimeter context while leaving back plates on their original tiled UVs
    static List<ConnectedTextureBehaviour> create(){
        return List.of(new AtlasConnectedTextureBehaviour(
                CTSpriteShifter.getCT(AllCTTypes.VERTICAL, texture("advanced_display_edge_face"), texture("advanced_display_side")),
                Orientation.PERIMETER, AccDisplayCasingTextures::edgePatches));
    }

    // Select a strip by physical depth and keep its end caps out of connected middle sections
    private static List<Patch> edgePatches(BlockState state, Direction face, int mask){
        var type = ((AccDisplayBlock)state.getBlock()).displayType();
        boolean rail = type == AccDisplayBlock.DisplayType.PANEL || type == AccDisplayBlock.DisplayType.HALF_PANEL;
        boolean half = !face.getAxis().isVertical()
                && (type == AccDisplayBlock.DisplayType.SLAB || type == AccDisplayBlock.DisplayType.HALF_PANEL);
        if(rail && half){
            return List.of(new Patch(new Region(0, 0, 32F / 3, 16), side(48, 56, 2, 8)),
                    rearStrip(32F / 3, 28, 16, 1, 8));
        }
        if(rail){
            return List.of(new Patch(new Region(0, 0, 32F / 3, 8), side((mask & 1) == 0 ? 0 : 16, 48, 2, 8)),
                    new Patch(new Region(0, 8, 32F / 3, 16), side((mask & 2) == 0 ? 32 : 16, 56, 2, 8)),
                    rearStrip(32F / 3, 28, (mask & 1) == 0 ? 0 : 2, 1,
                            16 - ((mask & 1) == 0 ? 0 : 2) - ((mask & 2) == 0 ? 0 : 2)));
        }
        if(half) return List.of(new Patch(Region.FULL, side(32, 40, 16, 8)));
        boolean narrow = type == AccDisplayBlock.DisplayType.BOARD;
        if(narrow && state.getValue(AccDisplayBlock.Z_ALIGNMENT) == AccDisplayBlock.Alignment.CENTER){
            return List.of(new Patch(new Region(0, 0, 12.8F, 16), NARROW.get(mask)),
                    rearStrip(12.8F, 24, (mask & 1) == 0 ? 0 : 2, 2,
                            16 - ((mask & 1) == 0 ? 0 : 2) - ((mask & 2) == 0 ? 0 : 2)));
        }
        return List.of(new Patch(Region.FULL, (narrow ? NARROW : WIDE).get(mask)));
    }

    // Keep the native one- or two-pixel rear strip beside the panel rail or board vent
    private static Patch rearStrip(float startU, int x, int y, int width, int height){
        return new Patch(new Region(startU, 0, 16, 16), Region.pixels(x, y, width, height, 32, 32),
                texture("advanced_display_back"));
    }

    // Convert the side atlas's pixel coordinates without treating transparent cells as usable tiles
    private static Region side(int x, int y, int width, int height){
        return Region.pixels(x, y, width, height, 64, 64);
    }

    // Resolve casing sprites in the addon namespace
    private static ResourceLocation texture(String path){
        return ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "block/" + path);
    }
}
