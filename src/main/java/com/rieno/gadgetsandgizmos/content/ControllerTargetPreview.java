package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.display.AccDisplaySource;
import com.rieno.gadgetsandgizmos.lib.display.DisplayPreviewSources;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermission;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

// Adapt controller target selections to shared live display previews
public final class ControllerTargetPreview{
    public static final net.minecraft.resources.ResourceLocation DISPLAY_FORMAT = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("createthrusters", "acc_display");
    private ControllerTargetPreview(){}

    // Preserve the complete surface, including source tabs and connected panel dimensions
    public static CompoundTag displayFrame(AccDisplayBlockEntity display){
        var root = display.networkRoot(); if(root == null) root = display;
        var frame = new CompoundTag(); frame.putString("Format", DISPLAY_FORMAT.toString());
        frame.put("Target", com.rieno.gadgetsandgizmos.lib.view.ViewReference.of(root).toTag());
        frame.putLong("Position", root.getBlockPos().asLong());
        frame.put("BlockState", net.minecraft.nbt.NbtUtils.writeBlockState(root.getBlockState()));
        frame.putInt("BlocksWide", root.networkWidth()); frame.putInt("BlocksHigh", root.networkHeight());
        frame.putInt("Width", root.surfacePixelWidth()); frame.putInt("Height", root.surfacePixelHeight());
        frame.put("Content", root.displayFrame().copy()); return frame;
    }

    public static DisplayPreviewSources sources(AdvancedContraptionControllerBlockEntity controller, ServerPlayer player){
        var fallback = source(player);
        return new DisplayPreviewSources(target -> {
            var selection = ControllerDiscoveryNode.fromTag(target);
            if(selection == null){
                var reference = com.rieno.gadgetsandgizmos.lib.view.ViewReference.fromTag(target);
                return reference == null || reference.entityId() != null || !reference.dimension().equals(controller.getLevel().dimension().location()) ? null
                        : com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper.findLoadedBlockEntityExact(controller.getLevel(), reference.subLevelId(), reference.blockPos());
            }
            if(selection.blockPos() == null) return null;
            var data = new CompoundTag(); data.put("TargetData", target.copy());
            if(target.contains("Face")){
                var faces = new CompoundTag(); faces.putString(selection.nodeId(), target.getString("Face")); data.put(AdvancedGraphCatalog.DATA_TARGET_FACES_TAG, faces);
            }
            var node = new AdvancedGraphDocument.Node("preview", "", "", 0, 0, data);
            var resolved = AdvancedContraptionControllerBlockEntity.resolveGraphDataTarget(controller.getLevel(), node, selection);
            return resolved == null ? null : resolved.level().getBlockEntity(resolved.pos());
        }, src -> WorldAccessPolicy.canAccessLocal(player, player.serverLevel(), SableLevelApi.containingId(src), src.getBlockPos())
                && ShipPermissions.allows(player, src.getLevel(), src.getBlockPos(), ShipPermission.INTERACT), src -> fallback);
    }

    private static AccDisplaySource source(ServerPlayer player){
        return new AccDisplaySource(){
            @Override public CompoundTag frame(BlockEntity target, int width, int height){
                if(target instanceof UniversalDisplayAdapterBlockEntity adapter) return adapter.accDisplayFrame();
                if(target instanceof AccDisplayBlockEntity display) return displayFrame(display);
                return UniversalDisplayAdapterBlockEntity.terminalFrame(target);
            }
            @Override public boolean input(BlockEntity target, String action, double x, double y, int value){
                if(target instanceof UniversalDisplayAdapterBlockEntity adapter) return adapter.input(action, x, y, value);
                if(target instanceof AccDisplayBlockEntity display) return display.submitPreviewInput(player, action, x, y, value);
                return UniversalDisplayAdapterBlockEntity.inputSource(target, action, x, y, "acc_inspector", value);
            }
        };
    }
}
