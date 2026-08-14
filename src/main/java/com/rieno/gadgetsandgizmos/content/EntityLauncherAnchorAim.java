package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Convert launcher target points into clamped yaw and pitch angles
public final class EntityLauncherAnchorAim {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final double BARREL_PIVOT_LOCAL_Y = 1.25D;
    public static final double MUZZLE_DISTANCE_FROM_PIVOT = 0.94D;
    public static final double MIN_TARGET_MOUNT_CLEARANCE = 0.135D;
    private static final double MAX_INWARD_PITCH = Math.toRadians(7.0D);
    private static final double AXIAL_DEAD_CONE_SQUARED = 1.0E-6D;
    private static final double MIN_VECTOR_LENGTH_SQUARED = 1.0E-12D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the entity launcher anchor aim
    private EntityLauncherAnchorAim() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the barrel pivot local
    public static Vec3 barrelPivotLocal(Direction mountNormal) {
        Vec3 normal = directionVector(safeNormal(mountNormal));
        return new Vec3(0.5D, 0.5D, 0.5D)
                .add(normal.scale(BARREL_PIVOT_LOCAL_Y - 0.5D));
    }

    // Get the solve
    public static AimFrame solve(Direction mountNormal, @Nullable Vec3 desiredDirection) {
        Direction normalDirection = safeNormal(mountNormal);
        Vec3 normal = directionVector(normalDirection);
        Vec3 target = normalizedOrFallback(desiredDirection, directionVector(defaultAim(normalDirection)));

        double normalComponent = Math.max(-1.0D, Math.min(1.0D, target.dot(normal)));
        Vec3 planar = target.subtract(normal.scale(normalComponent));
        double planarLengthSquared = planar.lengthSqr();
        double planarLength = planarLengthSquared < AXIAL_DEAD_CONE_SQUARED
                ? 0.0D
                : Math.sqrt(planarLengthSquared);
        Vec3 tangent = planarLength == 0.0D
                ? directionVector(referenceTangent(normalDirection))
                : planar.scale(1.0D / planarLength);

        double pitch = Math.atan2(normalComponent, planarLength);
        pitch = Math.max(pitch, -MAX_INWARD_PITCH);
        Vec3 dir = tangent.scale(Math.cos(pitch)).add(normal.scale(Math.sin(pitch))).normalize();
        Vec3 right = tangent.cross(normal).normalize();
        Vec3 up = right.cross(dir).normalize();
        return new AimFrame(dir, tangent, right, up);
    }

    // Check if the target clears the mounting face
    public static boolean targetClearsMountingFace(Direction mountNormal, @Nullable Vec3 blockCenter,
                                                    @Nullable Vec3 target) {
        if (blockCenter == null || target == null) {
            return false;
        }
        Vec3 normal = directionVector(safeNormal(mountNormal));
        Vec3 mountingFace = blockCenter.add(normal.scale(0.5D));
        double clearance = target.subtract(mountingFace).dot(normal);
        return Double.isFinite(clearance) && clearance + 1.0E-9D >= MIN_TARGET_MOUNT_CLEARANCE;
    }

    // Check if the targets share a dynamic sublevel
    public static boolean sharesDynamicSubLevel(@Nullable UUID sourceSubLevelId, @Nullable UUID targetSubLevelId) {
        return sourceSubLevelId != null && sourceSubLevelId.equals(targetSubLevelId);
    }

    // Create the default aim
    static Direction defaultAim(Direction mountNormal) {
        Direction normal = safeNormal(mountNormal);
        if (normal == Direction.DOWN) {
            return Direction.SOUTH;
        }
        return normal.getAxis().isVertical() ? Direction.NORTH : normal;
    }

    // Get the reference tangent
    static Direction referenceTangent(Direction mountNormal) {
        Direction normal = safeNormal(mountNormal);
        if (normal == Direction.DOWN) {
            return Direction.SOUTH;
        }
        return normal == Direction.UP ? Direction.NORTH : Direction.UP;
    }

    // Get the normalized or fallback
    private static Vec3 normalizedOrFallback(@Nullable Vec3 dir, Vec3 fallback) {
        if (dir == null
                || !Double.isFinite(dir.x)
                || !Double.isFinite(dir.y)
                || !Double.isFinite(dir.z)) {
            return fallback;
        }
        double lengthSquared = dir.lengthSqr();
        if (!Double.isFinite(lengthSquared) || lengthSquared < MIN_VECTOR_LENGTH_SQUARED) {
            return fallback;
        }
        return dir.scale(1.0D / Math.sqrt(lengthSquared));
    }

    // Get the safe normal
    private static Direction safeNormal(@Nullable Direction mountNormal) {
        return mountNormal == null ? Direction.NORTH : mountNormal;
    }

    // Get the direction vector
    private static Vec3 directionVector(Direction dir) {
        return Vec3.atLowerCornerOf(dir.getNormal());
    }

    // Store the aim frame
    public record AimFrame(Vec3 direction, Vec3 forwardTangent, Vec3 right, Vec3 up) {
    }
}
