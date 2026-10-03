package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Check Physics Gantry Belt Wheel link distance
public final class PhysicsGantryBeltWheelLink {
    private static final double PICK_RADIUS_SQUARED = 0.45D * 0.45D;
    private static final double END_MARGIN = 0.55D;

    public record BeltHit(Vec3 position, double rayDistance, double separationSquared) {}
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics gantry belt wheel link
    private PhysicsGantryBeltWheelLink() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if the distance is valid
    static boolean isDistanceValid(@Nullable Vec3 first, @Nullable Vec3 second, int maxDistance) {
        if (first == null || second == null || maxDistance < 1) {
            return false;
        }
        double distance = first.distanceTo(second);
        return distance > 0.01D && distance <= maxDistance;
    }

    // The belt is a rendered mesh, so select its centre line without requiring a block hitbox.
    // Leave the wheel ends to the ordinary block interaction.
    @Nullable
    public static BeltHit pickBelt(Vec3 eye, Vec3 look, double reach, Vec3 first, Vec3 second) {
        if (reach <= 0 || first == null || second == null || look.lengthSqr() < 0.0001D) return null;
        Vec3 axis = second.subtract(first);
        double length = axis.length();
        if (length <= END_MARGIN * 2) return null;
        Vec3 along = axis.scale(1.0D / length);
        Vec3 start = first.add(along.scale(END_MARGIN));
        Vec3 end = second.subtract(along.scale(END_MARGIN));
        Vec3 belt = end.subtract(start);
        Vec3 ray = look.normalize();
        Vec3 offset = start.subtract(eye);
        double c = belt.lengthSqr();
        double b = ray.dot(belt);
        double d = ray.dot(offset);
        double e = belt.dot(offset);
        double bestRay = 0;
        double bestBelt = 0;
        double bestDistance = Double.POSITIVE_INFINITY;

        double denominator = c - b * b;
        if (denominator > 0.000001D) {
            double beltPart = (b * d - e) / denominator;
            double rayPart = d + b * beltPart;
            if (beltPart >= 0 && beltPart <= 1 && rayPart >= 0 && rayPart <= reach) {
                bestBelt = beltPart;
                bestRay = rayPart;
                bestDistance = eye.add(ray.scale(rayPart)).distanceToSqr(start.add(belt.scale(beltPart)));
            }
        }

        double[][] edges = {
                {0, Math.clamp(d, 0, reach)},
                {1, Math.clamp(d + b, 0, reach)},
                {Math.clamp(-e / c, 0, 1), 0},
                {Math.clamp((b * reach - e) / c, 0, 1), reach}
        };
        for (double[] edge : edges) {
            double distance = eye.add(ray.scale(edge[1])).distanceToSqr(start.add(belt.scale(edge[0])));
            if (distance < bestDistance) {
                bestDistance = distance;
                bestBelt = edge[0];
                bestRay = edge[1];
            }
        }
        return bestDistance <= PICK_RADIUS_SQUARED
                ? new BeltHit(start.add(belt.scale(bestBelt)), bestRay, bestDistance) : null;
    }
}
