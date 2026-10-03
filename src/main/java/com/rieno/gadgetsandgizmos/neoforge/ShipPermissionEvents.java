package com.rieno.gadgetsandgizmos.neoforge;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlock;
import com.rieno.gadgetsandgizmos.content.PhysicsStaffItem;
import com.rieno.gadgetsandgizmos.content.ShipPermissions;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermission;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermissionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.common.util.TriState;

// Deny only player actions on controlled sublevels, leaving environmental damage intact
@EventBusSubscriber
public final class ShipPermissionEvents {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private ShipPermissionEvents(){}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void rightClick(PlayerInteractEvent.RightClickBlock evt){
        if(!(evt.getEntity() instanceof ServerPlayer player)) return;
        var shipId = SableLevelApi.containingId(evt.getLevel(), evt.getPos());
        if(!ShipPermissionManager.get(player.server).isClaimed(shipId)) return;
        if(evt.getItemStack().getItem() instanceof WrenchItem){
            ShipPermission permission = player.isShiftKeyDown()
                    ? ShipPermission.DESTROY : ShipPermission.INTERACT;
            if(ShipPermissionManager.get(player.server).allows(shipId, player.getUUID(), permission)){
                return;
            }
        }else if(evt.getEntity().isShiftKeyDown()
                && evt.getLevel().getBlockState(evt.getPos()).getBlock() instanceof AdvancedContraptionControllerBlock){
            if(ShipPermissions.allows(player, evt.getLevel(), evt.getPos(), ShipPermission.ACC_GRAPH)
                    && (evt.getItemStack().isEmpty()
                    || ShipPermissions.allows(player, evt.getLevel(), evt.getPos(), ShipPermission.INTERACT))) return;
        }else if(evt.getItemStack().getItem() instanceof PhysicsStaffItem){
            if(ShipPermissions.allows(player, evt.getLevel(), evt.getPos(), ShipPermission.PHYSICS_STAFF)){
                evt.setUseBlock(TriState.FALSE);
                return;
            }
        }else if(evt.getItemStack().getItem() instanceof BlockItem){
            boolean interact = ShipPermissions.allows(player, evt.getLevel(), evt.getPos(), ShipPermission.INTERACT);
            boolean place = ShipPermissions.allows(player, evt.getLevel(), evt.getPos(), ShipPermission.PLACE);
            if(interact && place) return;
            if(interact){
                evt.setUseItem(TriState.FALSE);
                return;
            }
            if(place){
                evt.setUseBlock(TriState.FALSE);
                return;
            }
        }else if(ShipPermissions.allows(player, evt.getLevel(), evt.getPos(), ShipPermission.INTERACT)){
            if(!evt.getItemStack().isEmpty()) evt.setUseItem(TriState.FALSE);
            return;
        }
        evt.setCancellationResult(InteractionResult.FAIL);
        evt.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void leftClick(PlayerInteractEvent.LeftClickBlock evt){
        if(evt.getEntity() instanceof ServerPlayer player
                && !ShipPermissions.allows(player, evt.getLevel(), evt.getPos(), ShipPermission.DESTROY)){
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interactEntity(PlayerInteractEvent.EntityInteract evt){
        if(evt.getEntity() instanceof ServerPlayer player && !allowsEntity(player, evt.getTarget())){
            evt.setCancellationResult(InteractionResult.FAIL);
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interactEntitySpecific(PlayerInteractEvent.EntityInteractSpecific evt){
        if(evt.getEntity() instanceof ServerPlayer player && !allowsEntity(player, evt.getTarget())){
            evt.setCancellationResult(InteractionResult.FAIL);
            evt.setCanceled(true);
        }
    }

    private static boolean allowsEntity(ServerPlayer player, net.minecraft.world.entity.Entity target){
        var body = SableLevelApi.containing(target);
        if(body == null) body = SableLevelApi.tracking(target);
        return body == null || ShipPermissionManager.get(player.server).allows(
                body.getUniqueId(), player.getUUID(), ShipPermission.INTERACT);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void breakBlock(BlockEvent.BreakEvent evt){
        if(evt.getPlayer() instanceof ServerPlayer player && evt.getLevel() instanceof Level level
                && !ShipPermissions.allows(player, level, evt.getPos(), ShipPermission.DESTROY)){
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void placeBlock(BlockEvent.EntityPlaceEvent evt){
        if(evt.getEntity() instanceof ServerPlayer player && evt.getLevel() instanceof Level level
                && !ShipPermissions.allows(player, level, evt.getPos(), ShipPermission.PLACE)){
            evt.setCanceled(true);
        }
    }
}
