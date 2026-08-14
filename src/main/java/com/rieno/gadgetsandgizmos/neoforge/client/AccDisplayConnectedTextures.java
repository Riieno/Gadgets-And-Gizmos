package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlock;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

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

        register(CTBlocks.ACC_DISPLAY.get(), new FullDisplayBehaviour(fullScreen, fullBorder));
        register(CTBlocks.ACC_DISPLAY_BLOCK.get(), new FullDisplayBehaviour(fullScreen, fullBorder));
        register(CTBlocks.ACC_DISPLAY_PANEL.get(), new FullDisplayBehaviour(fullScreen, fullBorder));
        register(CTBlocks.ACC_DISPLAY_HALF_PANEL.get(), new SmallDisplayBehaviour(smallScreen, smallBorder));
        register(CTBlocks.ACC_DISPLAY_SLAB.get(), new SmallDisplayBehaviour(smallScreen, smallBorder));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the shift
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
        BakedModel wrapped = model;
        for (ConnectedTextureBehaviour behaviour : BEHAVIOURS.getOrDefault(block, List.of())) {
            wrapped = new CTModel(wrapped, behaviour);
        }
        return wrapped;
    }

    // Handle the full display behaviour
    private static class FullDisplayBehaviour extends ConnectedTextureBehaviour.Base {
        // Shifts
        protected final CTSpriteShiftEntry[] shifts;

        // Initialize the full display behaviour
        private FullDisplayBehaviour(CTSpriteShiftEntry... shifts) {
            this.shifts = shifts;
        }

        // Get the shift
        @Override
        public CTSpriteShiftEntry getShift(BlockState state, Direction dir, TextureAtlasSprite sprite) {
            for (CTSpriteShiftEntry shift : shifts) {
                if (sprite == null || sprite == shift.getOriginal()) {
                    return shift;
                }
            }
            return null;
        }

        // Check if the display connects to its neighbour
        @Override
        public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader,
                                  BlockPos pos, BlockPos otherPos, Direction face,
                                  Direction primaryOffset, Direction secondaryOffset) {
            return compatible(state, other);
        }
    }

    // Handle the small display behaviour
    private static final class SmallDisplayBehaviour extends FullDisplayBehaviour {
        // Initialize the small display behaviour
        private SmallDisplayBehaviour(CTSpriteShiftEntry... shifts) {
            super(shifts);
        }

        // Check if the being is blocked
        @Override
        protected boolean isBeingBlocked(BlockState state, BlockAndTintGetter reader,
                                         BlockPos pos, BlockPos otherPos, Direction face) {
            if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
                return super.isBeingBlocked(state, reader, pos, otherPos, face);
            }
            return state.getValue(HorizontalDirectionalBlock.FACING) == face
                    && super.isBeingBlocked(state, reader, pos, otherPos, face);
        }

        // Check if the UVs should be reversed
        @Override
        protected boolean reverseUVs(BlockState state, Direction face) {
            Direction.Axis axis = state.getValue(HorizontalDirectionalBlock.FACING).getAxis();
            if (axis == Direction.Axis.X) {
                return face.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                        && face.getAxis() != Direction.Axis.X;
            }
            return face != Direction.NORTH
                    && face.getAxisDirection() != Direction.AxisDirection.POSITIVE;
        }

        // Check if the UVs should be reversed vertically
        @Override
        protected boolean reverseUVsVertically(BlockState state, Direction face) {
            Direction.Axis axis = state.getValue(HorizontalDirectionalBlock.FACING).getAxis();
            if (axis == Direction.Axis.X && face == Direction.NORTH
                    || axis == Direction.Axis.Z && face == Direction.WEST) {
                return false;
            }
            return super.reverseUVsVertically(state, face);
        }

        // Get the upward texture direction
        @Override
        protected Direction getUpDirection(BlockAndTintGetter reader, BlockPos pos,
                                           BlockState state, Direction face) {
            Direction.Axis axis = state.getValue(HorizontalDirectionalBlock.FACING).getAxis();
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

        // Get the right direction
        @Override
        protected Direction getRightDirection(BlockAndTintGetter reader, BlockPos pos,
                                              BlockState state, Direction face) {
            Direction.Axis axis = state.getValue(HorizontalDirectionalBlock.FACING).getAxis();
            if (face.getAxis().isVertical() && axis == Direction.Axis.X) {
                return super.getRightDirection(reader, pos, state, face).getClockWise();
            }
            if (face.getAxis() == axis || face.getAxis().isVertical()) {
                return super.getRightDirection(reader, pos, state, face);
            }
            return Direction.fromAxisAndDirection(Direction.Axis.Y, face.getAxisDirection());
        }
    }

    // Check if the values are compatible
    private static boolean compatible(BlockState state, BlockState other) {
        if (!(state.getBlock() instanceof AccDisplayBlock)
                || state.getBlock() != other.getBlock()) {
            return false;
        }
        for (Property<?> property : state.getProperties()) {
            if (other.hasProperty(property) && !state.getValue(property).equals(other.getValue(property))) {
                return false;
            }
        }
        return true;
    }
}
