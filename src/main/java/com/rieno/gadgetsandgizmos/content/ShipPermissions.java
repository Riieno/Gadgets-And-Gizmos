package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermission;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermissionManager;
import com.rieno.gadgetsandgizmos.lib.util.PlayerNames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Connect SCM control blocks and player actions to the reusable ship permission manager
public final class ShipPermissions {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private ShipPermissions(){}

    // Bind existing claims and claim unowned ships when the ACC and SCM share a placer
    public static void refresh(AdvancedContraptionControllerBlockEntity controller){
        if(controller.getLevel() == null || controller.getLevel().isClientSide
                || !ShipPermissionManager.isEnabled(controller.getLevel().getServer())
                || !controller.isMountedOnShipControlModule()) return;
        UUID shipId = SableLevelApi.containingId(controller);
        if(shipId == null) return;
        ShipPermissionManager manager = ShipPermissionManager.get(controller.getLevel().getServer());
        if(manager.isClaimed(shipId)){
            if(shipId.equals(manager.root(shipId))) manager.syncBindings(shipId, controller.getMappedShipSubLevelIds());
            return;
        }
        UUID placerId = controller.shipPermissionPlacerId();
        if(placerId == null) return;
        UUID modulePlacerId = controller.shipPermissionModulePlacerId();
        for(Direction direction : Direction.values()){
            BlockPos modulePos = controller.getBlockPos().relative(direction);
            var moduleState = controller.getLevel().getBlockState(modulePos);
            if(moduleState.getBlock() instanceof ShipControlModuleBlock
                    && ShipControlModuleBlock.exposedFace(moduleState) == direction.getOpposite()
                    && controller.getLevel().getBlockEntity(modulePos) instanceof ShipControlModuleBlockEntity module){
                modulePlacerId = module.placerId();
                break;
            }
        }
        if(!placerId.equals(modulePlacerId)) return;
        if(manager.claim(shipId, placerId)){
            manager.syncBindings(shipId, controller.getMappedShipSubLevelIds());
        }
    }

    public static void unclaim(@Nullable Level level, @Nullable BlockPos pos){
        if(level == null || level.isClientSide || level.getServer() == null || pos == null) return;
        UUID id = SableLevelApi.containingId(level, pos);
        if(id != null) ShipPermissionManager.get(level.getServer()).unclaim(id);
    }

    public static boolean allows(ServerPlayer player, Level level, BlockPos pos, ShipPermission permission){
        if(player == null || level == null || pos == null || level.getServer() == null) return false;
        if(!ShipPermissionManager.isEnabled(level.getServer())) return true;
        UUID id = SableLevelApi.containingId(level, pos);
        return id == null || ShipPermissionManager.get(level.getServer()).allows(id, player.getUUID(), permission);
    }

    public static boolean allows(ServerPlayer player, AdvancedContraptionControllerBlockEntity controller,
                                 ShipPermission permission){
        return player != null && controller != null && controller.getLevel() != null
                && allows(player, controller.getLevel(), controller.getBlockPos(), permission);
    }

    public static boolean isOwner(ServerPlayer player, AdvancedContraptionControllerBlockEntity controller){
        if(player == null || controller == null || controller.getLevel() == null) return false;
        UUID id = SableLevelApi.containingId(controller);
        return id != null && ShipPermissionManager.get(player.server).isOwner(id, player.getUUID());
    }

    public static boolean set(ServerPlayer player, AdvancedContraptionControllerBlockEntity controller,
                              UUID targetId, ShipPermission permission, boolean enabled){
        if(player == null || controller == null || controller.getLevel() == null
                || targetId == null || permission == null) return false;
        if(permission == ShipPermission.STORE && !storeEnabled()) return false;
        UUID id = SableLevelApi.containingId(controller);
        if(id == null) return false;
        ServerPlayer target = player.server.getPlayerList().getPlayer(targetId);
        String name = target == null ? storedName(player, id, targetId) : target.getGameProfile().getName();
        return ShipPermissionManager.get(player.server).set(id, player.getUUID(), targetId, name, permission, enabled);
    }

    private static String storedName(ServerPlayer player, UUID shipId, UUID targetId){
        return ShipPermissionManager.get(player.server).members(shipId).stream()
                .filter(member -> targetId.equals(member.playerId()))
                .map(ShipPermissionManager.MemberView::name).findFirst().orElse("");
    }

    public static boolean storeEnabled(){
        return CTFeatureToggles.isBlockEnabled("diagnostic_tablet");
    }

    // Change access mode and bind newly claimed ships to their mapped bodies
    public static boolean setOpen(ServerPlayer player, AdvancedContraptionControllerBlockEntity controller,
                                  boolean open){
        if(player == null || controller == null || controller.getLevel() == null) return false;
        UUID shipId = SableLevelApi.containingId(controller);
        ShipPermissionManager manager = ShipPermissionManager.get(player.server);
        if(!manager.setOpen(shipId, player.getUUID(), open)) return false;
        if(shipId.equals(manager.root(shipId))) manager.syncBindings(shipId, controller.getMappedShipSubLevelIds());
        return true;
    }

    // Transfer ship ownership through the library's management check
    public static boolean transferOwnership(ServerPlayer player, AdvancedContraptionControllerBlockEntity controller,
                                            UUID ownerId){
        if(player == null || controller == null || controller.getLevel() == null || ownerId == null) return false;
        UUID shipId = SableLevelApi.containingId(controller);
        if(shipId == null) return false;
        ShipPermissionManager manager = ShipPermissionManager.get(player.server);
        UUID prevOwnerId = manager.owner(shipId);
        return prevOwnerId != null && manager.transferOwnership(shipId, player.getUUID(), ownerId,
                PlayerNames.resolve(player.server, prevOwnerId));
    }

    // Send the owner, online players and saved members with the active access mode
    public static CompoundTag snapshot(ServerPlayer player, AdvancedContraptionControllerBlockEntity controller){
        CompoundTag tag = new CompoundTag();
        boolean enabled = ShipPermissionManager.isEnabled(player.server);
        tag.putBoolean("Enabled", enabled);
        if(!enabled) return tag;
        UUID shipId = SableLevelApi.containingId(controller);
        if(shipId == null) return tag;
        ShipPermissionManager manager = ShipPermissionManager.get(player.server);
        UUID ownerId = manager.owner(shipId);
        tag.putBoolean("Claimed", ownerId != null);
        tag.putBoolean("Open", manager.isOpen(shipId));
        tag.putBoolean("CanEdit", ownerId == null || manager.canManage(shipId, player.getUUID()));
        tag.putBoolean("StoreEnabled", storeEnabled());
        Map<UUID, Row> rows = new LinkedHashMap<>();
        if(ownerId != null) rows.put(ownerId, new Row(PlayerNames.resolve(player.server, ownerId), EnumSet.allOf(ShipPermission.class)));
        for(ShipPermissionManager.MemberView member : manager.members(shipId)){
            rows.put(member.playerId(), new Row(member.name(), member.permissions()));
        }
        for(ServerPlayer online : player.server.getPlayerList().getPlayers()){
            Row existing = rows.get(online.getUUID());
            rows.put(online.getUUID(), new Row(online.getGameProfile().getName(),
                    existing == null ? Set.of() : existing.permissions()));
        }
        ListTag players = new ListTag();
        rows.forEach((id, row) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            entry.putString("Name", row.name());
            entry.putBoolean("Owner", id.equals(ownerId));
            int mask = 0;
            for(ShipPermission permission : row.permissions()) mask |= 1 << permission.ordinal();
            entry.putInt("Mask", mask);
            players.add(entry);
        });
        tag.put("Players", players);
        return tag;
    }

    private record Row(String name, Set<ShipPermission> permissions){}
}
