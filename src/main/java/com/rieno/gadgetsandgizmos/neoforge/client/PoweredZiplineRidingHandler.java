package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundZiplineInputPacket;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundZiplineMountPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Handle Powered Zipline Riding input and keep the affected state synchronized
public final class PoweredZiplineRidingHandler {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double RIDER_SIDE_OFFSET = 0.0D;
    private static final double RIDER_DOWN_OFFSET = 0.35D;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared riding zipline pos
    private static BlockPos ridingZiplinePos;
    // Keep alive tick count
    private static int keepAliveTicks;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the powered zipline riding handler
    private PoweredZiplineRidingHandler() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Try to mount
    public static void tryMount(Player player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        ridingZiplinePos = pos.immutable();
        keepAliveTicks = 0;
        PacketDistributor.sendToServer(new ServerboundZiplineMountPacket(ridingZiplinePos, false));
    }

    // Update the client
    public static void clientTick() {
        if (ridingZiplinePos == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused() || minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.player.isShiftKeyDown()) {
            dismount();
            return;
        }

        PoweredZiplineBlockEntity zipline = SimulatedHelper.findBlockEntityIncludingSubLevels(
                minecraft.level, ridingZiplinePos, PoweredZiplineBlockEntity.class);
        if (zipline == null) {
            dismount();
            return;
        }

        Vec3 tangent = PoweredZiplinePlacementHandler.sampleZiplineWorldTangent(zipline);
        boolean pathForwardMatchesCamera = tangent == null || tangent.dot(minecraft.player.getLookAngle()) >= 0.0D;
        if (minecraft.options.keyUp.isDown() && !minecraft.options.keyDown.isDown()) {
            PacketDistributor.sendToServer(new ServerboundZiplineInputPacket(ridingZiplinePos, !pathForwardMatchesCamera));
        }
        if (minecraft.options.keyDown.isDown() && !minecraft.options.keyUp.isDown()) {
            PacketDistributor.sendToServer(new ServerboundZiplineInputPacket(ridingZiplinePos, pathForwardMatchesCamera));
        }

        Vec3 target = getRiderGripTarget(zipline, tangent);
        Vec3 playerPos = minecraft.player.position().add(0.0, minecraft.player.getBoundingBox().getYsize() + 0.5f * minecraft.player.getScale(), 0.0);
        Vec3 diff = target.subtract(playerPos);
        if (diff.lengthSqr() > 64.0) {
            dismount();
            return;
        }
        minecraft.player.setDeltaMovement(minecraft.player.getDeltaMovement().scale(0.7).add(diff.scale(0.3)));
        minecraft.player.fallDistance = 0.0f;

        keepAliveTicks++;
        if (keepAliveTicks >= 10) {
            keepAliveTicks = 0;
            PacketDistributor.sendToServer(new ServerboundZiplineMountPacket(ridingZiplinePos, false));
        }
    }

    // Check if this should cull first person connector
    public static boolean shouldCullFirstPersonConnector() {
        Minecraft minecraft = Minecraft.getInstance();
        return ridingZiplinePos != null && minecraft.options.getCameraType().isFirstPerson();
    }

    // Get the rider grip target
    private static Vec3 getRiderGripTarget(PoweredZiplineBlockEntity zipline, Vec3 tangent) {
        Vec3 target = PoweredZiplinePlacementHandler.sampleZiplineWorldPosition(zipline, 1.0f);
        if (target == null) {
            target = new Vec3(zipline.getWorldPosition(1.0).x(), zipline.getWorldPosition(1.0).y(), zipline.getWorldPosition(1.0).z());
        }
        Vec3 side = getHorizontalSide(tangent);
        return target.add(side.scale(RIDER_SIDE_OFFSET)).add(0.0D, -RIDER_DOWN_OFFSET, 0.0D);
    }

    // Get the horizontal side
    private static Vec3 getHorizontalSide(Vec3 tangent) {
        if (tangent == null) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 horizontal = new Vec3(tangent.x, 0.0D, tangent.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }
        horizontal = horizontal.normalize();
        return new Vec3(-horizontal.z, 0.0D, horizontal.x);
    }

    // Dismount from the zipline
    private static void dismount() {
        if (ridingZiplinePos != null) {
            PacketDistributor.sendToServer(new ServerboundZiplineMountPacket(ridingZiplinePos, true));
        }
        ridingZiplinePos = null;
        keepAliveTicks = 0;
    }
}
