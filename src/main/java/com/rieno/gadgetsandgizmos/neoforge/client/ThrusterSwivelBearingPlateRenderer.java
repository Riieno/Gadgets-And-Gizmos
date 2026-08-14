package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.mixin.SwivelBearingPlateParentAccessor;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockRenderer;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

// Draw the Thruster Swivel Bearing Plate
public class ThrusterSwivelBearingPlateRenderer extends SwivelBearingPlateBlockRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster swivel bearing plate
    public ThrusterSwivelBearingPlateRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the thruster swivel bearing plate
    @Override
    protected void renderSafe(SwivelBearingPlateBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        if (isOwnedByThrusterBearing(be)) {
            return;
        }
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the rotated model
    @Override
    protected SuperByteBuffer getRotatedModel(SwivelBearingPlateBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing((PartialModel) SimPartialModels.SHAFT_SIXTEENTH, state, state.getValue(SwivelBearingBlock.FACING));
    }

    // Check if this belongs to the parent
    private static boolean isOwnedByThrusterBearing(SwivelBearingPlateBlockEntity be) {
        SwivelBearingPlateParentAccessor accessor = (SwivelBearingPlateParentAccessor) be;
        BlockPos parentPos = accessor.createthrusters$getParent();
        if (parentPos == null || be.getLevel() == null) {
            return false;
        }
        UUID parentSubLevelId = accessor.createthrusters$getParentSubLevelId();
        ThrusterBearingBlockEntity parent = parentSubLevelId != null
                ? SimulatedHelper.findBlockEntity(be.getLevel(), parentSubLevelId, parentPos, ThrusterBearingBlockEntity.class)
                : SimulatedHelper.findBlockEntityIncludingSubLevels(be.getLevel(), parentPos, ThrusterBearingBlockEntity.class);
        return parent != null;
    }
}
