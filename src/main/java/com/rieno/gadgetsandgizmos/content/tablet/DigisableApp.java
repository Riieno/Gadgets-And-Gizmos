package com.rieno.gadgetsandgizmos.content.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.TabletAppsServerConfig;
import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelLocator;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelArchiveApi;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelArchiveStore;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSnapshots;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

import java.util.Comparator;
import java.util.UUID;

// Keep locating and every sublevel mutation authoritative on the server
final class DigisableApp{
    private DigisableApp(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static String execute(TabletActionContext ctx, TabletAction action) throws Exception{
        String val = action.arguments().getOrDefault("value", "").strip();
        UUID selected = val.isBlank() || "refresh".equals(action.actionId()) ? null
                : UUID.fromString("extract".equals(action.actionId()) ? val.split("\\|", 2)[0] : val);
        String message = "";
        UUID completed = null;
        switch(action.actionId()){
            case "refresh" -> {}
            case "preview" -> {}
            case "prepare_extract" -> {
                if(!TabletAppsServerConfig.STORE.get()) throw new IllegalArgumentException("Sublevel extraction is disabled by the server");
                var archive = accessibleArchive(ctx, selected);
                if(archive.getList("Preview", Tag.TAG_COMPOUND).isEmpty()) throw new IllegalArgumentException("This archive has no blocks to preview");
            }
            case "store" -> {
                if(!TabletAppsServerConfig.STORE.get()) throw new IllegalArgumentException("Sublevel storage is disabled by the server");
                cooldown(ctx);
                var smokeBounds = DigisableStorageEffects.capture(ctx.player().serverLevel(), selected);
                SubLevelArchiveApi.store(ctx.player(), ctx.player().serverLevel(), selected, TabletAppsServerConfig.archiveLimits());
                DigisableStorageEffects.emit(ctx.player().serverLevel(), smokeBounds);
                message = "Assembly stored";
                selected = null;
            }
            case "extract" -> {
                if(!TabletAppsServerConfig.STORE.get()) throw new IllegalArgumentException("Sublevel extraction is disabled by the server");
                String[] args = val.split("\\|", 8);
                if(args.length != 8) throw new IllegalArgumentException("Choose the extraction position in the world first");
                Vec3 point = new Vec3(Double.parseDouble(args[1]), Double.parseDouble(args[2]), Double.parseDouble(args[3]));
                Quaterniond rotation = new Quaterniond(Double.parseDouble(args[4]), Double.parseDouble(args[5]),
                        Double.parseDouble(args[6]), Double.parseDouble(args[7]));
                if(!Double.isFinite(point.x + point.y + point.z) || !Double.isFinite(rotation.lengthSquared())
                        || Math.abs(rotation.lengthSquared() - 1.0D) > 0.01D) throw new IllegalArgumentException("Invalid extraction placement");
                double range = Math.max(2, Math.min(64, TabletAppsServerConfig.STORE_RANGE.get()));
                if(ctx.player().getEyePosition().distanceToSqr(point) > range * range) throw new IllegalArgumentException("Extraction point is outside the server range");
                cooldown(ctx);
                completed = selected;
                selected = SubLevelArchiveApi.extractPlaced(ctx.player(), selected, point, rotation);
                message = "Assembly extracted";
            }
            case "teleport" -> {
                if(!TabletAppsServerConfig.TELEPORT.get() || !TabletAppsServerConfig.LOCATE.get()) throw new IllegalArgumentException("Sublevel teleportation is disabled by the server");
                if(!ctx.player().isCreative() && !ctx.player().hasPermissions(2)) throw new IllegalArgumentException("Teleportation requires creative mode or operator permissions");
                var location = locateKnown(ctx, selected);
                Vec3 point = location.position();
                double y = Math.min(ctx.player().level().getMaxBuildHeight() - 3, location.top() + 2);
                BlockPos pos = BlockPos.containing(point.x, y, point.z);
                if(!ctx.player().level().getWorldBorder().isWithinBounds(pos)) throw new IllegalArgumentException("Teleport destination is unavailable");
                ctx.player().serverLevel().getChunk(pos);
                if(SableLevelApi.subLevel(ctx.player().level(), selected) == null) throw new IllegalArgumentException("Sublevel is loading; try teleporting again");
                if(!WorldAccessPolicy.canAccess(ctx.player(), ctx.player().serverLevel(), selected, BlockPos.containing(location.position()))) throw new IllegalArgumentException("Teleport destination is protected");
                cooldown(ctx);
                ctx.player().teleportTo(ctx.player().serverLevel(), point.x, y, point.z, ctx.player().getYRot(), ctx.player().getXRot());
                ctx.player().fallDistance = 0;
                message = "Teleported to sublevel";
            }
            case "delete" -> {
                if(!TabletAppsServerConfig.DELETE.get()) throw new IllegalArgumentException("Sublevel deletion is disabled by the server");
                cooldown(ctx);
                SubLevelArchiveApi.deleteUnclaimed(ctx.player(), selected, TabletAppsServerConfig.archiveLimits());
                message = "Unclaimed assembly deleted";
                selected = null;
            }
            default -> throw new IllegalArgumentException("Unknown Digisable action");
        }
        CompoundTag data = snapshot(ctx);
        if(completed != null) data.putUUID("PlacementComplete", completed);
        if(selected != null && ("preview".equals(action.actionId()) || "prepare_extract".equals(action.actionId()))){
            var archive = SubLevelArchiveStore.forServer(ctx.player().server).entry(selected);
            if(!archive.isEmpty() && !"consumed".equals(archive.getString("State"))){
                if(!ctx.player().hasPermissions(2) && !ctx.player().getUUID().equals(archive.getUUID("Owner"))) throw new IllegalArgumentException("This archive belongs to another player");
                data.put("Preview", archive.getList("Preview", Tag.TAG_COMPOUND).copy());
                data.putUUID("PreviewRoot", archive.getUUID("Root"));
                if("prepare_extract".equals(action.actionId())) data.putUUID("PlacementId", selected);
            }else{
                if("prepare_extract".equals(action.actionId())) throw new IllegalArgumentException("Stored sublevel is unavailable");
                var body = locate(ctx, selected);
                data.put("Preview", SubLevelSnapshots.preview(SableAssemblyTopologyApi.discover(body).loadedBodies(), 2048));
                data.putUUID("PreviewRoot", body.getUniqueId());
            }
            data.putUUID("Selected", selected);
        }
        PaidTabletApps.snapshot(ctx, action.appId(), data);
        return message;
    }

    private static CompoundTag accessibleArchive(TabletActionContext ctx, UUID id) throws Exception{
        CompoundTag archive = SubLevelArchiveStore.forServer(ctx.player().server).entry(id);
        if(archive.isEmpty() || !"stored".equals(archive.getString("State"))) throw new IllegalArgumentException("Stored sublevel is unavailable");
        if(!ctx.player().hasPermissions(2) && !ctx.player().getUUID().equals(archive.getUUID("Owner"))) throw new IllegalArgumentException("This archive belongs to another player");
        if(!ctx.player().serverLevel().dimension().location().toString().equals(archive.getString("Dimension"))) throw new IllegalArgumentException("Extract this assembly in its original dimension");
        return archive;
    }

    private static ServerSubLevel locate(TabletActionContext ctx, UUID id){
        if(!TabletAppsServerConfig.LOCATE.get()) throw new IllegalArgumentException("Sublevel locating is disabled by the server");
        if(!(SableLevelApi.subLevel(ctx.player().level(), id) instanceof ServerSubLevel body)) throw new IllegalArgumentException("Sublevel is unloaded or unavailable");
        if(SubLevelArchiveApi.position(body).distanceToSqr(ctx.player().position()) > Math.pow(TabletAppsServerConfig.LOCATE_RANGE.get(), 2)) throw new IllegalArgumentException("Sublevel is outside the server's locate range");
        if(!WorldAccessPolicy.canAccess(ctx.player(), ctx.player().serverLevel(), id, BlockPos.containing(SubLevelArchiveApi.position(body)))) throw new IllegalArgumentException("Sublevel is protected");
        return body;
    }

    private static SubLevelLocator.Location locateKnown(TabletActionContext ctx, UUID id){
        if(!TabletAppsServerConfig.LOCATE.get()) throw new IllegalArgumentException("Sublevel locating is disabled by the server");
        var locator = SubLevelLocator.get(ctx.player().serverLevel());
        locator.refresh(ctx.player().serverLevel());
        var location = locator.find(id);
        if(location == null || location.position().distanceToSqr(ctx.player().position()) > Math.pow(TabletAppsServerConfig.LOCATE_RANGE.get(), 2)) throw new IllegalArgumentException("Sublevel is unavailable or outside the locate range");
        if(!WorldAccessPolicy.canAccess(ctx.player(), ctx.player().serverLevel(), id, BlockPos.containing(location.position()))) throw new IllegalArgumentException("Sublevel is protected");
        return location;
    }

    private static CompoundTag snapshot(TabletActionContext ctx) throws Exception{
        CompoundTag data = new CompoundTag();
        data.putBoolean("StoreAllowed", TabletAppsServerConfig.STORE.get());
        data.putBoolean("LocateAllowed", TabletAppsServerConfig.LOCATE.get());
        data.putBoolean("TeleportAllowed", TabletAppsServerConfig.LOCATE.get() && TabletAppsServerConfig.TELEPORT.get() && (ctx.player().isCreative() || ctx.player().hasPermissions(2)));
        data.putBoolean("DeleteAllowed", TabletAppsServerConfig.DELETE.get() && (ctx.player().server.isSingleplayerOwner(ctx.player().getGameProfile()) || ctx.player().hasPermissions(2)));
        ListTag live = new ListTag();
        if(TabletAppsServerConfig.LOCATE.get()){
            var locator = SubLevelLocator.get(ctx.player().serverLevel());
            locator.refresh(ctx.player().serverLevel());
            var locations = locator.locations().stream()
                    .filter(location -> WorldAccessPolicy.canAccess(ctx.player(), ctx.player().serverLevel(), location.id(), BlockPos.containing(location.position())))
                    .filter(location -> location.position().distanceToSqr(ctx.player().position()) <= Math.pow(TabletAppsServerConfig.LOCATE_RANGE.get(), 2))
                    .sorted(Comparator.comparingDouble(location -> location.position().distanceToSqr(ctx.player().position()))).limit(128).toList();
            for(var location : locations){
                CompoundTag row = new CompoundTag();
                Vec3 point = location.position();
                row.putUUID("Id", location.id());
                row.putString("Name", location.name());
                row.putDouble("X", point.x); row.putDouble("Y", point.y); row.putDouble("Z", point.z);
                row.putBoolean("Loaded", SableLevelApi.subLevel(ctx.player().level(), location.id()) != null);
                row.putBoolean("Unclaimed", WorldAccessPolicy.unclaimed(ctx.player().serverLevel(), location.id(), BlockPos.containing(location.position())));
                row.putInt("Distance", (int) point.distanceTo(ctx.player().position()));
                live.add(row);
            }
        }
        data.put("Sublevels", live);
        ListTag archives = new ListTag();
        SubLevelArchiveStore.forServer(ctx.player().server).catalogue(ctx.player().getUUID(), ctx.player().hasPermissions(2))
                .stream().limit(128).forEach(archives::add);
        data.put("Archives", archives);
        data.putInt("ArchiveLimit", TabletAppsServerConfig.ARCHIVES.get());
        data.putInt("ExtractionRange", Math.max(2, Math.min(64, TabletAppsServerConfig.STORE_RANGE.get())));
        return data;
    }

    private static void cooldown(TabletActionContext ctx){
        long now = ctx.player().serverLevel().getGameTime();
        CompoundTag tag = ctx.player().getPersistentData();
        long prev = tag.getLong("createthrusters:digisable_operation");
        if(tag.contains("createthrusters:digisable_operation") && now >= prev && now - prev < TabletAppsServerConfig.COOLDOWN.get()) throw new IllegalArgumentException("Wait before the next sublevel operation");
        tag.putLong("createthrusters:digisable_operation", now);
    }
}
