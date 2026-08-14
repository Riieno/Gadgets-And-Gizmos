package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rieno.gadgetsandgizmos.content.AdvancedNavigationTableBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlock;
import dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.RenderableNavigationTarget;
import dev.simulated_team.simulated.index.SimTags;
import dev.simulated_team.simulated.util.SimColors;
import dev.simulated_team.simulated.util.SimDirectionUtil;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

// Draw the Advanced Navigation Table
public class AdvancedNavigationTableRenderer extends SmartBlockEntityRenderer<AdvancedNavigationTableBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced navigation table
    public AdvancedNavigationTableRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the advanced navigation table
    @Override
    protected void renderSafe(AdvancedNavigationTableBlockEntity navBE, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(navBE, partialTicks, ms, buffer, light, overlay);

        ItemStack heldItem = navBE.getHeldItem();
        BlockState navState = navBE.getBlockState();
        Direction facing = navState.getValue(NavTableBlock.FACING);
        ((PoseTransformStack) TransformStack.of(ms).pushPose().center()).rotate((Quaternionfc) facing.getRotation());

        float arrowAngle = navBE.getClientTargetAngle(partialTicks) - (float) (Math.PI / 2.0D);
        renderRedstoneIndicators(navBE, navState, facing, ms, buffer, light);
        renderPointer(navState, arrowAngle, ms, buffer, light);
        renderHeldItem(navBE, heldItem, navState, arrowAngle, partialTicks, ms, buffer, light, overlay);

        ms.popPose();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the redstone indicators
    private static void renderRedstoneIndicators(AdvancedNavigationTableBlockEntity navBE, BlockState navState,
                                                 Direction facing, PoseStack ms, MultiBufferSource buffer, int light) {
        ms.pushPose();
        ms.translate(0.0D, -0.5D, 0.0D);
        Vector3f logicalDirectionF = new Vector3f();
        for (Direction dir : SimDirectionUtil.Y_AXIS_PLANE) {
            facing.getRotation().transform(dir.getStepX(), dir.getStepY(), dir.getStepZ(),
                    logicalDirectionF);
            Direction logicalDirection = Direction.getNearest(logicalDirectionF.x, logicalDirectionF.y,
                    logicalDirectionF.z);

            ms.pushPose();
            SuperByteBuffer indicator = CachedBuffers.partial(CTPartialModels.ADVANCED_NAVIGATION_TABLE_INDICATOR,
                    navState);
            indicator.rotateToFace(dir);
            indicator.translate(0.0D, 0.0D, 0.5D);
            float signalStrength = navBE.isPowering
                    ? Math.max(navBE.getRedstoneStrength(logicalDirection), 0) / 15.0F
                    : 0.0F;
            indicator.light(light)
                    .color(SimColors.redstone(signalStrength))
                    .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
            ms.popPose();
        }
        ms.popPose();
    }

    // Draw the pointer
    private static void renderPointer(BlockState navState, float arrowAngle, PoseStack ms,
                                      MultiBufferSource buffer, int light) {
        ms.pushPose();
        ms.translate(0.0D, 0.3D, 0.0D);
        SuperByteBuffer pointer = CachedBuffers.partial(CTPartialModels.ADVANCED_NAVIGATION_TABLE_POINTER, navState);
        pointer.rotateY(arrowAngle);
        pointer.light(light).renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        ms.popPose();
    }

    // Draw the held item
    private static void renderHeldItem(AdvancedNavigationTableBlockEntity navBE, ItemStack heldItem,
                                       BlockState navState, float arrowAngle, float partialTicks, PoseStack ms,
                                       MultiBufferSource buffer, int light, int overlay) {
        ms.pushPose();
        ms.translate(0.0D, 0.3D, 0.0D);
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        boolean blockItem = itemRenderer.getModel(heldItem, null, null, 0).isGui3d();
        ((PoseTransformStack) TransformStack.of(ms)
                .translate(0.0F, blockItem ? 0.25F : 0.15F, 0.0F)
                .rotate((float) Math.toRadians(90.0D), Direction.WEST))
                .scale(blockItem ? 0.5F : 0.375F);

        Item item = heldItem.getItem();
        if (item instanceof RenderableNavigationTarget renderable) {
            renderable.renderInNavTable(heldItem, navBE, navState, partialTicks, ms, buffer, light, overlay);
        } else {
            if (heldItem.is(SimTags.Items.ROTATE_WITH_NAV_ARROW)) {
                ms.mulPose(Axis.ZP.rotation(arrowAngle));
            }
            itemRenderer.renderStatic(heldItem, ItemDisplayContext.FIXED, light, overlay, ms, buffer,
                    navBE.getLevel(), 0);
        }
        ms.popPose();
    }
}
