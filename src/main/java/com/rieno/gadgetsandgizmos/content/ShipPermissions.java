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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

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

    // Claim a loaded ship only when the same player placed its ACC and SCM
    public static void refresh(AdvancedContraptionControllerBlockEntity controller){
        if(controller.getLevel() == null || controller.getLevel().isClientSide
                || controller.getLevel().getServer() == null || !controller.isMountedOnShipControlModule()) return;
        UUID shipId = SableLevelApi.containingId(controller);
        UUID placerId = controller.shipPermissionPlacerId();
        if(shipId == null || placerId == null) return;
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
        ShipPermissionManager manager = ShipPermissionManager.get(controller.getLevel().getServer());
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
        if(!isOwner(player, controller) || targetId == null || permission == null) return false;
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

    // Send online players and saved members while keeping the owner out of the list
    public static CompoundTag snapshot(ServerPlayer player, AdvancedContraptionControllerBlockEntity controller){
        CompoundTag tag = new CompoundTag();
        UUID shipId = SableLevelApi.containingId(controller);
        if(shipId == null) return tag;
        ShipPermissionManager manager = ShipPermissionManager.get(player.server);
        UUID ownerId = manager.owner(shipId);
        tag.putBoolean("Claimed", ownerId != null);
        tag.putBoolean("CanEdit", ownerId != null && ownerId.equals(player.getUUID()));
        tag.putBoolean("StoreEnabled", storeEnabled());
        Map<UUID, Row> rows = new LinkedHashMap<>();
        for(ShipPermissionManager.MemberView member : manager.members(shipId)){
            rows.put(member.playerId(), new Row(member.name(), member.permissions()));
        }
        for(ServerPlayer online : player.server.getPlayerList().getPlayers()){
            if(online.getUUID().equals(ownerId)) continue;
            Row existing = rows.get(online.getUUID());
            rows.put(online.getUUID(), new Row(online.getGameProfile().getName(),
                    existing == null ? Set.of() : existing.permissions()));
        }
        ListTag players = new ListTag();
        rows.forEach((id, row) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            entry.putString("Name", row.name());
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
