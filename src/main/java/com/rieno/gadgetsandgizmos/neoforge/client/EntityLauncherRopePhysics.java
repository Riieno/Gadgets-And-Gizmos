package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.EntityLauncherClawEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Simulate launcher rope length, tension and endpoint motion on the client
final class EntityLauncherRopePhysics {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double TARGET_SEGMENT_LENGTH = 1.0;
    private static final double GRAVITY_PER_TICK = 0.035;
    private static final int MAX_POINTS = 96;
    private static final int CONSTRAINT_ITERATIONS = 7;
    private static final Map<UUID, RopeState> ROPES = new HashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the entity launcher rope physics
    private EntityLauncherRopePhysics() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the path
    static List<Vec3> getPath(EntityLauncherClawEntity entity, Vec3 muzzleWorld, Vec3 clawWorld) {
        if (entity.isRemoved()) {
            ROPES.remove(entity.getUUID());
            return List.of(muzzleWorld, clawWorld);
        }

        return getPath(entity.getUUID(), entity.level(), muzzleWorld, clawWorld, entity.isRetracting(), entity.getRenderRopeLength());
    }

    // Get the path
    static List<Vec3> getPath(UUID ropeId, Level level, Vec3 startWorld, Vec3 endWorld, boolean retracting) {
        return getPath(ropeId, level, startWorld, endWorld, retracting, -1.0);
    }

    // Get the path
    static List<Vec3> getPath(UUID ropeId, Level level, Vec3 startWorld, Vec3 endWorld, boolean retracting, double ropeLength) {
        RopeState state = ROPES.computeIfAbsent(ropeId, id -> new RopeState());
        return state.update(startWorld, endWorld, level.getGameTime(), retracting, ropeLength);
    }

    // Store rope state
    private static final class RopeState {
        // Tracked points
        private final List<Vec3> points = new ArrayList<>();
        // Previous points
        private final List<Vec3> previousPoints = new ArrayList<>();
        // Last game time
        private long lastGameTime = Long.MIN_VALUE;

        // Update the rope state
        private List<Vec3> update(Vec3 start, Vec3 end, long gameTime, boolean retracting, double ropeLength) {
            double endpointDistance = start.distanceTo(end);
            double targetLength = ropeLength > 0.0 ? ropeLength : endpointDistance;
            boolean taut = retracting || endpointDistance + 0.05 >= targetLength;
            int pointCount = desiredPointCount(taut ? endpointDistance : targetLength);
            if (taut) {
                resetStraight(start, end, pointCount);
                lastGameTime = gameTime;
                return List.copyOf(points);
            }
            if (points.size() != pointCount || shouldReset(start, end)) {
                reset(start, end, pointCount, targetLength);
                lastGameTime = gameTime;
                return List.copyOf(points);
            }

            int steps = (int) Math.max(0L, Math.min(3L, gameTime - lastGameTime));
            lastGameTime = gameTime;
            pin(start, end);
            for (int step = 0; step < steps; step++) {
                integrate(retracting);
                satisfyConstraints(start, end, retracting, targetLength);
            }
            pin(start, end);
            return List.copyOf(points);
        }

        // Check if this should reset
        private boolean shouldReset(Vec3 start, Vec3 end) {
            if (points.size() < 2) {
                return true;
            }
            return points.get(0).distanceToSqr(start) > 16.0 || points.get(points.size() - 1).distanceToSqr(end) > 16.0;
        }

        // Reset the rope state
        private void reset(Vec3 start, Vec3 end, int pointCount, double targetLength) {
            points.clear();
            previousPoints.clear();
            Vec3 delta = end.subtract(start);
            for (int i = 0; i < pointCount; i++) {
                double t = i / (double) (pointCount - 1);
                double sag = Math.sin(t * Math.PI) * Math.min(1.25, targetLength * 0.05);
                Vec3 point = start.add(delta.scale(t)).add(0.0, -sag, 0.0);
                points.add(point);
                previousPoints.add(point);
            }
        }

        // Reset the straight
        private void resetStraight(Vec3 start, Vec3 end, int pointCount) {
            points.clear();
            previousPoints.clear();
            Vec3 delta = end.subtract(start);
            for (int i = 0; i < pointCount; i++) {
                double t = pointCount == 1 ? 0.0 : i / (double) (pointCount - 1);
                Vec3 point = start.add(delta.scale(t));
                points.add(point);
                previousPoints.add(point);
            }
        }

        // Integrate the rope state
        private void integrate(boolean retracting) {
            double damping = retracting ? 0.72 : 0.88;
            double gravity = retracting ? GRAVITY_PER_TICK * 0.35 : GRAVITY_PER_TICK;
            for (int i = 1; i < points.size() - 1; i++) {
                Vec3 current = points.get(i);
                Vec3 prev = previousPoints.get(i);
                Vec3 vel = current.subtract(prev).scale(damping);
                previousPoints.set(i, current);
                points.set(i, current.add(vel).add(0.0, -gravity, 0.0));
            }
        }

        // Satisfy the constraints
        private void satisfyConstraints(Vec3 start, Vec3 end, boolean retracting, double targetLength) {
            double slack = retracting ? 1.01 : 1.10;
            double endpointDistance = start.distanceTo(end);
            double constrainedLength = Math.max(targetLength, endpointDistance);
            double segmentLength = Math.max(0.15, constrainedLength * slack / (points.size() - 1));
            for (int iteration = 0; iteration < CONSTRAINT_ITERATIONS; iteration++) {
                pin(start, end);
                for (int i = 0; i < points.size() - 1; i++) {
                    Vec3 a = points.get(i);
                    Vec3 b = points.get(i + 1);
                    Vec3 delta = b.subtract(a);
                    double length = delta.length();
                    if (length < 1.0E-5) {
                        continue;
                    }
                    Vec3 correction = delta.scale((length - segmentLength) / length);
                    if (i == 0) {
                        points.set(i + 1, b.subtract(correction));
                    } else if (i + 1 == points.size() - 1) {
                        points.set(i, a.add(correction));
                    } else {
                        points.set(i, a.add(correction.scale(0.5)));
                        points.set(i + 1, b.subtract(correction.scale(0.5)));
                    }
                }
            }
        }

        // Pin the rope state
        private void pin(Vec3 start, Vec3 end) {
            points.set(0, start);
            points.set(points.size() - 1, end);
            previousPoints.set(0, start);
            previousPoints.set(previousPoints.size() - 1, end);
        }

        // Get the desired point count
        private static int desiredPointCount(double distance) {
            return Mth.clamp((int) Math.ceil(distance / TARGET_SEGMENT_LENGTH) + 1, 2, MAX_POINTS);
        }
    }
}
