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
    private ControllerTargetPreview(){}

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
                if(target instanceof AccDisplayBlockEntity display) return display.displayFrame();
                return UniversalDisplayAdapterBlockEntity.terminalFrame(target);
            }
            @Override public boolean input(BlockEntity target, String action, double x, double y, int value){
                if(target instanceof UniversalDisplayAdapterBlockEntity adapter) return adapter.input(action, x, y, value);
                if(target instanceof AccDisplayBlockEntity display) return display.submitComputerCraftInput(player, action, x, y, value);
                return UniversalDisplayAdapterBlockEntity.inputSource(target, action, x, y, "acc_inspector", value);
            }
        };
    }
}
