package com.rieno.gadgetsandgizmos.compat.aeroclaims;

import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffInteractionGuard;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerHooks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.UUID;

// Retain powered staff protection when AeroClaims is installed
public final class PhysicsStaffAeroClaimsCompat{
    private PhysicsStaffAeroClaimsCompat(){}

    // Install the optional addon policy through the library authorization API
    public static void register(){
        if(!ModList.get().isLoaded("aeroclaims")) return;
        PhysicsStaffInteractionGuard.registerTargetAuthorization(
                ResourceLocation.fromNamespaceAndPath("createthrusters", "aeroclaims_physics_staff"), Access::canAccess);
    }

    // Keep optional implementation classes out of common initialization
    private static final class Access{
        private static boolean canAccess(ServerPlayer player, UUID subLevelId){
            if(!PhysicsStaffPowerHooks.isHoldingPoweredPhysicsStaff(player)) return true;
            var claim = com.mapter.aeroclaims.claim.ClaimManager.getClaimByShipId(player.serverLevel(), subLevelId.toString());
            return claim == null || !claim.isActive()
                    || com.mapter.aeroclaims.claim.ClaimManager.getPermissionResolver().canAccess(player, claim);
        }
    }
}
