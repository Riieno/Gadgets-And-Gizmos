package com.rieno.gadgetsandgizmos.compat.simulated;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SableTeleportMomentumTest {
    @Test
    void teleportToStaticWorldClearsEveryVelocityComponent() {
        SableTeleportMomentum.Destination world = new SableTeleportMomentum.Destination(
                new Vec3(20, 70, 20), Vec3.ZERO, false);

        assertEquals(Vec3.ZERO, SableTeleportMomentum.matchedVelocity(new Vec3(0.5, -1.0, 2.0), world));
    }

    @Test
    void teleportToMovingSubLevelKeepsRelativeMotionAndAddsDestinationMotion() {
        SableTeleportMomentum.Destination ship = new SableTeleportMomentum.Destination(
                new Vec3(20, 70, 20), new Vec3(0.25, 0.5, -0.25), true);

        assertEquals(new Vec3(0.75, -0.5, 1.75),
                SableTeleportMomentum.matchedVelocity(new Vec3(0.5, -1.0, 2.0), ship));
    }
}
