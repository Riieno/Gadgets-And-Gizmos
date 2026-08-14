package com.rieno.gadgetsandgizmos.compat.simulated;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.service.SimConfigService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Keep rope creation and attachment working across current and legacy Simulated APIs
public final class SimulatedRopeCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger CT_LOGGER = LogUtils.getLogger();
    private static final Method CREATE_ROPE_CURRENT = findMethod("createRope",
            RopeStrandHolderBehavior.class, boolean.class);
    private static final Method CREATE_ROPE_LEGACY = findMethod("createRope",
            RopeStrandHolderBehavior.class);
    private static final Method DESTROY_ROPE_CURRENT = findMethod("destroyRope",
            ServerPlayer.class, Vec3.class, boolean.class);
    private static final Method DESTROY_ROPE_LEGACY = findMethod("destroyRope",
            ServerPlayer.class, Vec3.class);
    private static final Field ATTACHED_ROPE_ID = findField("attachedRopeID");
    private static final Field STRAND_OWNER = findField("strandOwner");
    private static final Field OWNED_SERVER_STRAND = findField("ownedServerStrand");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the simulated rope compat
    private SimulatedRopeCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the rope
    public static boolean createRope(RopeStrandHolderBehavior src, RopeStrandHolderBehavior target, boolean dropItem) {
        try {
            if (CREATE_ROPE_CURRENT != null) {
                return (Boolean) CREATE_ROPE_CURRENT.invoke(src, target, dropItem);
            }
            if (CREATE_ROPE_LEGACY != null) {
                return (Boolean) CREATE_ROPE_LEGACY.invoke(src, target);
            }
        } catch (ReflectiveOperationException e) {
            CT_LOGGER.error("[CT][Compat] Failed to invoke Simulated createRope compatibility shim", e);
        }
        return false;
    }

    // Create the rope with attachments
    public static boolean createRopeWithAttachments(RopeStrandHolderBehavior source,
                                                    RopeStrandHolderBehavior target,
                                                    ServerLevel level,
                                                    Vec3 sourceWorldPoint,
                                                    Vec3 targetWorldPoint,
                                                    @Nullable UUID sourceSubLevelId,
                                                    BlockPos sourceAttachment,
                                                    @Nullable UUID targetSubLevelId,
                                                    BlockPos targetAttachment,
                                                    boolean dropItem) {
        if (source == null || target == null || source == target || level == null
                || sourceWorldPoint == null || targetWorldPoint == null
                || sourceAttachment == null || targetAttachment == null) {
            return false;
        }
        if (target.isAttached()) {
            return false;
        }
        double distance = sourceWorldPoint.distanceTo(targetWorldPoint);
        if (!Double.isFinite(distance)) {
            return false;
        }
        double maxRopeRange = (Double) SimConfigService.INSTANCE.server().blocks.maxRopeRange.get();
        if (distance > maxRopeRange) {
            return false;
        }

        if (source.isAttached()) {
            destroyRope(source, null, sourceWorldPoint, dropItem);
        }
        if (source.isAttached() || target.isAttached()) {
            return false;
        }

        ServerRopeStrand strand = new ServerRopeStrand(UUID.randomUUID(), buildInitialPoints(sourceWorldPoint, targetWorldPoint));
        int wholeSegments = Mth.floor(distance);
        strand.updateFirstSegmentExtension(distance - wholeSegments);
        strand.addAttachment(level, RopeAttachmentPoint.START,
                new RopeAttachment(RopeAttachmentPoint.START, sourceSubLevelId, sourceAttachment.immutable()));
        strand.addAttachment(level, RopeAttachmentPoint.END,
                new RopeAttachment(RopeAttachmentPoint.END, targetSubLevelId, targetAttachment.immutable()));

        try {
            setHolderState(source, strand.getUUID(), true, strand);
            setHolderState(target, strand.getUUID(), false, null);
            ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(level);
            if (manager == null) {
                setHolderState(source, null, false, null);
                setHolderState(target, null, false, null);
                return false;
            }
            manager.addStrand(strand);
            source.blockEntity.notifyUpdate();
            target.blockEntity.notifyUpdate();
            return true;
        } catch (ReflectiveOperationException e) {
            clearHolderState(source);
            clearHolderState(target);
            CT_LOGGER.error("[CT][Compat] Failed to create Simulated rope with explicit attachments", e);
            return false;
        }
    }

    // Destroy the rope
    public static void destroyRope(RopeStrandHolderBehavior holder, @Nullable ServerPlayer player,
                                   @Nullable Vec3 dropPosition, boolean returnItem) {
        try {
            if (DESTROY_ROPE_CURRENT != null) {
                DESTROY_ROPE_CURRENT.invoke(holder, player, dropPosition, returnItem);
                return;
            }
            if (DESTROY_ROPE_LEGACY != null) {
                DESTROY_ROPE_LEGACY.invoke(holder, player, dropPosition);
                return;
            }
        } catch (ReflectiveOperationException e) {
            CT_LOGGER.error("[CT][Compat] Failed to invoke Simulated destroyRope compatibility shim", e);
        }
    }

    // Build the initial points
    private static List<Vector3d> buildInitialPoints(Vec3 start, Vec3 end) {
        double distance = start.distanceTo(end);
        int wholeSegments = Mth.floor(distance);
        int pointCount = Math.max(1, wholeSegments + 1);
        Vec3 dir = distance <= 1.0E-6D ? Vec3.ZERO : end.subtract(start).normalize();
        double firstSegmentLength = distance - wholeSegments;
        List<Vector3d> points = new ArrayList<>(pointCount + 1);
        points.add(toVector3d(start));
        for (int i = 0; i < pointCount; i++) {
            points.add(toVector3d(start.add(dir.scale(i + firstSegmentLength))));
        }
        return points;
    }

    // Convert the simulated rope compat to vector3d
    private static Vector3d toVector3d(Vec3 vec) {
        return new Vector3d(vec.x, vec.y, vec.z);
    }

    // Set the holder state
    private static void setHolderState(RopeStrandHolderBehavior holder, @Nullable UUID ropeId,
                                       boolean ownsRope, @Nullable ServerRopeStrand ownedStrand)
            throws ReflectiveOperationException {
        if (ATTACHED_ROPE_ID == null || STRAND_OWNER == null || OWNED_SERVER_STRAND == null) {
            throw new NoSuchFieldException("RopeStrandHolderBehavior rope state fields");
        }
        ATTACHED_ROPE_ID.set(holder, ropeId);
        STRAND_OWNER.setBoolean(holder, ownsRope);
        OWNED_SERVER_STRAND.set(holder, ownedStrand);
    }

    // Clear the holder state
    private static void clearHolderState(RopeStrandHolderBehavior holder) {
        try {
            setHolderState(holder, null, false, null);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    // Find the method
    private static @Nullable Method findMethod(String name, Class<?>... parameterTypes) {
        try {
            Method method = RopeStrandHolderBehavior.class.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Find the field
    private static @Nullable Field findField(String name) {
        try {
            Field field = RopeStrandHolderBehavior.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
