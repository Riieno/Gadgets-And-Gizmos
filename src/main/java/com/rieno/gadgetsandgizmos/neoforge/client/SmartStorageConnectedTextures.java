package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.fluids.tank.FluidTankCTBehaviour;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.IdentityHashMap;
import java.util.Map;

// Register connected textures for the local smart storage assets
public final class SmartStorageConnectedTextures {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<Block, ConnectedTextureBehaviour> BEHAVIOURS = new IdentityHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the smart storage connected textures
    private SmartStorageConnectedTextures() {
    }

    // Register each smart storage connected texture behaviour
    public static void register() {
        if (!BEHAVIOURS.isEmpty() || CTBlocks.SMART_VAULT == null) return;
        BEHAVIOURS.put(CTBlocks.SMART_VAULT.get(), new VaultBehaviour("smart_vault"));
        BEHAVIOURS.put(CTBlocks.SMART_BATTERY.get(), new VaultBehaviour("smart_battery"));
        BEHAVIOURS.put(CTBlocks.SMART_TANK.get(), new FluidTankCTBehaviour(
                shift(AllCTTypes.RECTANGLE, "smart_tank/fluid_tank", "smart_tank/fluid_tank_connected"),
                shift(AllCTTypes.RECTANGLE, "smart_tank/fluid_tank_top", "smart_tank/fluid_tank_top_connected"),
                shift(AllCTTypes.RECTANGLE, "smart_tank/fluid_tank_inner", "smart_tank/fluid_tank_inner_connected")));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Wrap a smart storage model with its connected texture behaviour
    public static BakedModel wrap(Block block, BakedModel model) {
        ConnectedTextureBehaviour behaviour = BEHAVIOURS.get(block);
        return behaviour == null || model instanceof CTModel ? model : new CTModel(model, behaviour);
    }

    // Create a connected texture sprite shift
    private static CTSpriteShiftEntry shift(AllCTTypes type, String source, String connected) {
        return CTSpriteShifter.getCT(type,
                ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "block/" + source),
                ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "block/" + connected));
    }

    // Connect the small vault source texture to its two multiblock atlases
    private static final class VaultBehaviour extends ConnectedTextureBehaviour.Base {
        private final CTSpriteShiftEntry mediumTop;
        private final CTSpriteShiftEntry largeTop;
        private final CTSpriteShiftEntry mediumFront;
        private final CTSpriteShiftEntry largeFront;
        private final CTSpriteShiftEntry mediumSide;
        private final CTSpriteShiftEntry largeSide;
        private final CTSpriteShiftEntry mediumBottom;
        private final CTSpriteShiftEntry largeBottom;

        // Initialize the vault behaviour
        private VaultBehaviour(String folder) {
            mediumTop = vaultShift(folder, "top", "medium");
            largeTop = vaultShift(folder, "top", "large");
            mediumFront = vaultShift(folder, "front", "medium");
            largeFront = vaultShift(folder, "front", "large");
            mediumSide = vaultShift(folder, "side", "medium");
            largeSide = vaultShift(folder, "side", "large");
            mediumBottom = vaultShift(folder, "bottom", "medium");
            largeBottom = vaultShift(folder, "bottom", "large");
        }

        // Create a vault sprite shift
        private static CTSpriteShiftEntry vaultShift(String folder, String face, String size) {
            return shift(AllCTTypes.RECTANGLE,
                    folder + "/vault_" + face + "_small",
                    folder + "/vault_" + face + "_" + size);
        }

        // Select the connected atlas for one vault face
        @Override
        public CTSpriteShiftEntry getShift(BlockState state, Direction face, TextureAtlasSprite sprite) {
            Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            boolean large = state.getValue(ItemVaultBlock.LARGE);
            CTSpriteShiftEntry selected = face.getAxis() == axis
                    ? (large ? largeFront : mediumFront)
                    : face == Direction.UP
                    ? (large ? largeTop : mediumTop)
                    : face == Direction.DOWN
                    ? (large ? largeBottom : mediumBottom)
                    : (large ? largeSide : mediumSide);
            return selected;
        }

        // Check whether two vault blocks belong to the same formed multiblock
        @Override
        public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader,
                                  BlockPos pos, BlockPos otherPos, Direction face) {
            return state == other && ConnectivityHandler.isConnected(reader, pos, otherPos);
        }

        // Get the upward texture direction
        @Override
        protected Direction getUpDirection(BlockAndTintGetter reader, BlockPos pos,
                                           BlockState state, Direction face) {
            Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            boolean alongX = axis == Direction.Axis.X;
            if (face.getAxis().isVertical() && alongX) {
                return super.getUpDirection(reader, pos, state, face).getClockWise();
            }
            if (face.getAxis() == axis || face.getAxis().isVertical()) {
                return super.getUpDirection(reader, pos, state, face);
            }
            return Direction.fromAxisAndDirection(axis,
                    alongX ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        }

        // Get the rightward texture direction
        @Override
        protected Direction getRightDirection(BlockAndTintGetter reader, BlockPos pos,
                                              BlockState state, Direction face) {
            Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            if (face.getAxis().isVertical() && axis == Direction.Axis.X) {
                return super.getRightDirection(reader, pos, state, face).getClockWise();
            }
            if (face.getAxis() == axis || face.getAxis().isVertical()) {
                return super.getRightDirection(reader, pos, state, face);
            }
            return Direction.fromAxisAndDirection(Direction.Axis.Y, face.getAxisDirection());
        }
    }
}
