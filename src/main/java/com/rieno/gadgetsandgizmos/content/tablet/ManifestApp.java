package com.rieno.gadgetsandgizmos.content.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStorage;
import com.rieno.gadgetsandgizmos.content.ShippingManifestBlock;
import com.rieno.gadgetsandgizmos.content.ShippingManifestBlockEntity;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerAutomation;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerAutomationStore;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerContentsSnapshot;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerStorageIdentity;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerOrchestrator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

import java.util.UUID;

// Configure attached cargo through the persistent container automation API
final class ManifestApp{
    private ManifestApp(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static String execute(TabletActionContext ctx, TabletAction action){
        if("detach".equals(action.actionId())){
            if(ctx.placedSource()) throw new IllegalArgumentException("A placed tablet inspects its attached container");
            var binding = DiagnosticTabletAppStorage.selectedBinding(ctx.player().server, ctx.sourceTabletId(), PaidTabletApps.MANIFEST.id());
            if(binding != null) DiagnosticTabletAppStorage.removeBinding(ctx.player().server, ctx.sourceTabletId(), PaidTabletApps.MANIFEST.id(), DiagnosticTabletAppStorage.key(binding));
            CompoundTag data = new CompoundTag();
            data.putString("Help", "Look at a container and press Inspect, or use the tablet on it");
            PaidTabletApps.snapshot(ctx, action.appId(), data);
            return "Stopped inspecting container";
        }
        if("inspect".equals(action.actionId())) inspectLookedAt(ctx);
        BlockEntity target = target(ctx);
        if(target == null && "refresh".equals(action.actionId())){
            CompoundTag data = new CompoundTag();
            boolean bound = !ctx.placedSource() && DiagnosticTabletAppStorage.selectedBinding(
                    ctx.player().server, ctx.sourceTabletId(), PaidTabletApps.MANIFEST.id()) != null;
            data.putBoolean("Bound", bound);
            data.putString("Help", bound ? "The inspected container is unavailable. Stop inspecting or choose another target"
                    : "Place this tablet on a container, or use the handheld tablet on one to inspect it");
            PaidTabletApps.snapshot(ctx, action.appId(), data);
            return "";
        }
        TabletAppTargets.requireAccess(ctx, target);
        if(!ctx.placedSource()) requireNearby(ctx.player(), target);
        if(!com.rieno.gadgetsandgizmos.lib.inventory.ContainerAccessRegistry.canOpen(ctx.player(), target.getLevel(), target.getBlockPos())) throw new IllegalArgumentException("This container is locked by its owner");
        var id = SimulatedHelper.getContainingSubLevelId(target);
        var store = ContainerAutomationStore.get(ctx.player().serverLevel());
        ContainerAutomation config = store.find(id, target.getBlockPos());
        if(config == null && !"refresh".equals(action.actionId())) config = store.configure(ctx.player().getUUID(), id, target.getBlockPos());
        if(config == null) config = new ContainerAutomation();
        String val = action.arguments().getOrDefault("value", "").strip();
        if(!"refresh".equals(action.actionId()) && !"inspect".equals(action.actionId())){
            if(!config.claim(ctx.player().getUUID()) && !ctx.player().hasPermissions(2)) throw new IllegalArgumentException("Only the container owner or an operator can change its settings");
            switch(action.actionId()){
                case "filter" -> {
                    if("clear".equals(val)) config.setFilter(ItemStack.EMPTY);
                    else{
                        ItemStack filter = ctx.player().getOffhandItem();
                        if(filter.isEmpty()) throw new IllegalArgumentException("Hold a Create filter or an item in your offhand");
                        config.setFilter(filter);
                    }
                }
                case "lock" -> config.setLocked(!config.locked());
                case "push" -> config.setPush(!config.push());
                case "pull" -> config.setPull(!config.pull());
                case "stock" -> {
                    String[] args = val.split("\\|", 3);
                    if(args.length < 2) throw new IllegalArgumentException("Enter an item and desired stock amount");
                    ResourceLocation itemId = ResourceLocation.tryParse(args[0]);
                    var item = itemId == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR);
                    ItemStack sample = ctx.player().getOffhandItem();
                    if(!sample.is(item)) sample = new ItemStack(item);
                    if(item == Items.AIR || !config.setTarget(sample, Integer.parseInt(args[1]), args.length == 3 ? args[2] : "transfer")) throw new IllegalArgumentException("Invalid stock target or stock target limit reached");
                }
                default -> throw new IllegalArgumentException("Unknown Manifest action");
            }
            store.setDirty();
            target.getLevel().invalidateCapabilities(target.getBlockPos());
        }
        CompoundTag data = config.toTag(target.getLevel().registryAccess());
        data.merge(ContainerContentsSnapshot.capture(target));
        data.putBoolean("CanConfigure", config.owner() == null || ctx.player().getUUID().equals(config.owner()) || ctx.player().hasPermissions(2));
        PaidTabletApps.snapshot(ctx, action.appId(), data);
        return "";
    }

    private static BlockEntity target(TabletActionContext ctx){
        BlockEntity be = TabletAppTargets.support(ctx);
        if(be == null && !ctx.placedSource()){
            var binding = DiagnosticTabletAppStorage.selectedBinding(ctx.player().server, ctx.sourceTabletId(), PaidTabletApps.MANIFEST.id());
            be = binding == null ? null : SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(), binding.subLevelId(), binding.pos());
        }
        if(be instanceof ShippingManifestBlockEntity manifest) be = manifest.getLevel().getBlockEntity(ShippingManifestBlock.attachedTargetPos(manifest.getBlockPos(), manifest.getBlockState()));
        if(be != null && be.getLevel() != null) be = be.getLevel().getBlockEntity(ContainerStorageIdentity.position(be.getLevel(), be.getBlockPos()));
        return readable(be) ? be : null;
    }

    static boolean inspect(ServerPlayer player, UUID tabletId, com.rieno.gadgetsandgizmos.content.DiagnosticTabletData.Binding binding){
        if(tabletId == null) return false;
        BlockEntity be = SimulatedHelper.findLoadedBlockEntityExact(player.level(), binding.subLevelId(), binding.pos());
        if(be instanceof ShippingManifestBlockEntity manifest) be = manifest.getLevel().getBlockEntity(ShippingManifestBlock.attachedTargetPos(manifest.getBlockPos(), manifest.getBlockState()));
        if(be == null) return false;
        be = be.getLevel().getBlockEntity(ContainerStorageIdentity.position(be.getLevel(), be.getBlockPos()));
        if(!readable(be)) return false;
        requireNearby(player, be);
        if(!com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy.canAccessLocal(player, player.serverLevel(), SimulatedHelper.getContainingSubLevelId(be), be.getBlockPos())) return false;
        if(!com.rieno.gadgetsandgizmos.lib.inventory.ContainerAccessRegistry.canOpen(player, be.getLevel(), be.getBlockPos())) throw new IllegalArgumentException("This container is locked by its owner");
        DiagnosticTabletAppStorage.addBinding(player.server, tabletId, PaidTabletApps.MANIFEST.id(), binding, true);
        return true;
    }

    private static boolean readable(BlockEntity be){
        if(be == null || be instanceof WorkerOrchestrator || be.getLevel() == null) return false;
        var level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        return ContainerContentsSnapshot.canCapture(be);
    }

    private static void inspectLookedAt(TabletActionContext ctx){
        if(ctx.placedSource()) throw new IllegalArgumentException("A placed tablet inspects the container behind it");
        if(!(ctx.player().pick(16, 0, false) instanceof BlockHitResult hit)) throw new IllegalArgumentException("Look at a container within 16 blocks");
        BlockEntity be = SimulatedHelper.findBlockEntityIncludingSubLevels(ctx.player().level(), hit.getBlockPos());
        if(be == null) throw new IllegalArgumentException("Look at a container within 16 blocks");
        var binding = new com.rieno.gadgetsandgizmos.content.DiagnosticTabletData.Binding("block",
                SimulatedHelper.getContainingSubLevelId(be), be.getBlockPos(), be.getBlockState().getBlock().getName().getString());
        if(!inspect(ctx.player(), ctx.sourceTabletId(), binding)) throw new IllegalArgumentException("This block is not a readable container");
    }

    private static void requireNearby(ServerPlayer player, BlockEntity target){
        UUID id = SimulatedHelper.getContainingSubLevelId(target);
        var body = SableLevelApi.subLevel(player.level(), id);
        var point = SableTransformApi.toWorldPosition(body, target.getBlockPos().getCenter());
        if(point == null || player.distanceToSqr(point) > 256) throw new IllegalArgumentException("Inspect the container from within 16 blocks");
    }

}
