package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.theme.Color;
import net.minecraft.core.Direction;

// Register the Flywheel visuals
final class CTFlywheelVisuals {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WHITE = 0xFFFFFF;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT flywheel visuals
    private CTFlywheelVisuals() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Force the white
    static RotatingInstance forceWhite(RotatingInstance instance) {
        if (instance != null) {
            instance.colorRgb(WHITE);
        }
        return instance;
    }

    // Get the kinetic rotation transform white
    static SuperByteBuffer kineticRotationTransformWhite(SuperByteBuffer buffer, KineticBlockEntity be,
                                                          Direction.Axis axis, float angle, int light) {
        buffer.light(light);
        buffer.rotateCentered(angle, Direction.get(Direction.AxisDirection.POSITIVE, axis));
        buffer.color(Color.WHITE);
        return buffer;
    }

    // Get the standard kinetic rotation transform white
    static SuperByteBuffer standardKineticRotationTransformWhite(SuperByteBuffer buffer, KineticBlockEntity be,
                                                                 int light) {
        Direction.Axis axis = KineticBlockEntityRenderer.getRotationAxisOf(be);
        return kineticRotationTransformWhite(buffer, be, axis,
                KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), axis), light);
    }

    // Draw the rotating buffer white
    static void renderRotatingBufferWhite(KineticBlockEntity be, SuperByteBuffer superBuffer, PoseStack ms,
                                          VertexConsumer buffer, int light) {
        standardKineticRotationTransformWhite(superBuffer, be, light).renderInto(ms, buffer);
    }
}
