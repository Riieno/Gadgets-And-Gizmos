package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// Store one redstone-link frequency pair used by the double button
public class DoubleButtonFrequencySlot extends ValueBoxTransform {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Button
    private final DoubleButtonBlockEntity.ButtonHalf button;
    // Tracks whether this is the first frequency
    private final boolean firstFrequency;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the double button frequency slot
    public DoubleButtonFrequencySlot(DoubleButtonBlockEntity.ButtonHalf btn, boolean firstFrequency) {
        this.button = btn;
        this.firstFrequency = firstFrequency;
        this.scale = getScale();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the button
    public DoubleButtonBlockEntity.ButtonHalf button() {
        return button;
    }

    // Check if this is the first frequency
    public boolean firstFrequency() {
        return firstFrequency;
    }

    // Get the local offset
    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        return DoubleButtonBlock.frequencySlotCenter(state, button, firstFrequency);
    }

    // Rotate the double button frequency slot
    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack poseStack) {
        Rotation rotation = rotationFor(state.getValue(DoubleButtonBlock.FACING));
        if (rotation.yDegrees != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(-rotation.yDegrees));
        }
        if (rotation.xDegrees != 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-rotation.xDegrees));
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
    }

    // Get the scale
    @Override
    public float getScale() {
        return 0.4975F;
    }

    // Get the rotation
    private static Rotation rotationFor(Direction facing) {
        return switch (facing) {
            case DOWN -> new Rotation(180.0F, 180.0F);
            case EAST -> new Rotation(270.0F, 270.0F);
            case NORTH -> new Rotation(270.0F, 180.0F);
            case SOUTH -> new Rotation(270.0F, 0.0F);
            case UP -> new Rotation(0.0F, 180.0F);
            case WEST -> new Rotation(270.0F, 90.0F);
        };
    }

    // Store the rotation
    private record Rotation(float xDegrees, float yDegrees) {
    }
}
