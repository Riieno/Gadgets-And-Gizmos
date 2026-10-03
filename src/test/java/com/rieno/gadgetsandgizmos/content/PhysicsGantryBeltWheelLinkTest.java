package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PhysicsGantryBeltWheelLinkTest {
    @BeforeAll
    static void bootstrap() {
        ControllerTestBootstrap.bootstrap();
    }

    @Test
    void breakingEitherSurvivalBeltEndpointReturnsOneConnector() throws Exception {
        Level level = mock(Level.class);
        BlockPos leftPos = new BlockPos(0, 64, 0);
        BlockPos rightPos = new BlockPos(8, 64, 0);
        PhysicsGantryBeltWheelBlockEntity left = wheel(level, leftPos);
        PhysicsGantryBeltWheelBlockEntity right = wheel(level, rightPos);

        try (var simulated = mockStatic(SimulatedHelper.class)) {
            configurePair(simulated, level, left, leftPos, right, rightPos);
            left.setLinkedTarget(rightPos, null, true);
            right.setLinkedTarget(leftPos, null, true);

            left.breakLink(true, true);

            assertFalse(left.hasLinkedTarget());
            assertFalse(right.hasLinkedTarget());
            verify(left).dropBelt();
            verify(right, never()).dropBelt();

            // Destruction can remove both endpoints in the same tick. The second removal cannot duplicate the belt.
            right.breakLink(true, true);
            verify(left, times(1)).dropBelt();
            verify(right, never()).dropBelt();
        }
    }

    @Test
    void relinkingAndCreativeBeltsDoNotCreateConnectorItems() throws Exception {
        Level level = mock(Level.class);
        BlockPos leftPos = new BlockPos(0, 64, 0);
        BlockPos rightPos = new BlockPos(8, 64, 0);
        PhysicsGantryBeltWheelBlockEntity left = wheel(level, leftPos);
        PhysicsGantryBeltWheelBlockEntity right = wheel(level, rightPos);

        try (var simulated = mockStatic(SimulatedHelper.class)) {
            configurePair(simulated, level, left, leftPos, right, rightPos);
            left.setLinkedTarget(rightPos, null, true);
            right.setLinkedTarget(leftPos, null, true);
            left.breakLink(true);

            left.setLinkedTarget(rightPos, null, false);
            right.setLinkedTarget(leftPos, null, false);
            right.breakLink(true, true);

            verify(left, never()).dropBelt();
            verify(right, never()).dropBelt();
        }
    }

    @Test
    void eightBeltsShareOneWheelWithoutDuplicatingCapacityOrRefunds() throws Exception {
        Level level = mock(Level.class);
        BlockPos hubPos = new BlockPos(0, 64, 0);
        PhysicsGantryBeltWheelBlockEntity hub = wheel(level, hubPos);
        PhysicsGantryBeltWheelBlockEntity[] spokes = new PhysicsGantryBeltWheelBlockEntity[9];
        try (var simulated = mockStatic(SimulatedHelper.class)) {
            simulated.when(() -> SimulatedHelper.findBlockEntity(level, null, hubPos,
                    PhysicsGantryBeltWheelBlockEntity.class)).thenReturn(hub);
            for (int index = 0; index < spokes.length; index++) {
                BlockPos pos = new BlockPos(index + 2, 64, 0);
                PhysicsGantryBeltWheelBlockEntity spoke = wheel(level, pos);
                spokes[index] = spoke;
                simulated.when(() -> SimulatedHelper.findBlockEntity(level, null, pos,
                        PhysicsGantryBeltWheelBlockEntity.class)).thenReturn(spoke);
                if (index < 8) {
                    assertTrue(hub.addLinkedTarget(pos, null, true));
                    assertTrue(spoke.addLinkedTarget(hubPos, null, true));
                }
            }
            assertEquals(8, hub.linkCount());
            assertFalse(hub.canAddLink(spokes[8].getBlockPos(), null));
            assertFalse(hub.addLinkedTarget(spokes[0].getBlockPos(), null, true));
            assertEquals(0.0f, hub.calculateAddedStressCapacity());
            assertEquals(0.0f, hub.calculateStressApplied());

            spokes[0].breakLink(true, true);
            assertEquals(7, hub.linkCount());
            verify(spokes[0], times(1)).dropBelt();
            verify(hub, never()).dropBelt();
            assertTrue(hub.addLinkedTarget(spokes[8].getBlockPos(), null, true));
            assertTrue(spokes[8].addLinkedTarget(hubPos, null, true));
            assertEquals(8, hub.linkCount());

            hub.breakLink(true, true);
            assertEquals(0, hub.linkCount());
            for (int index = 1; index < spokes.length; index++) {
                assertFalse(spokes[index].hasLinkedTarget());
                verify(spokes[index], never()).dropBelt();
            }
            verify(hub, times(8)).dropBelt();
        }
    }

    @Test
    void shearsRemoveOnlyTheSelectedBeltAndRefundItOnce() throws Exception {
        Level level = mock(Level.class);
        PhysicsGantryBeltWheelBlockEntity hub = wheel(level, BlockPos.ZERO);
        PhysicsGantryBeltWheelBlockEntity a = wheel(level, new BlockPos(8, 0, 0));
        PhysicsGantryBeltWheelBlockEntity b = wheel(level, new BlockPos(0, 0, 8));
        try (var simulated = mockStatic(SimulatedHelper.class)) {
            configurePair(simulated, level, hub, BlockPos.ZERO, a, a.getBlockPos());
            simulated.when(() -> SimulatedHelper.findBlockEntity(level, null, b.getBlockPos(),
                    PhysicsGantryBeltWheelBlockEntity.class)).thenReturn(b);
            hub.addLinkedTarget(a.getBlockPos(), null, true);
            a.addLinkedTarget(hub.getBlockPos(), null, true);
            hub.addLinkedTarget(b.getBlockPos(), null, true);
            b.addLinkedTarget(hub.getBlockPos(), null, true);

            assertTrue(hub.shearLink(a.getBlockPos(), null));
            assertEquals(1, hub.linkCount());
            assertFalse(a.hasLinkedTarget());
            assertTrue(b.references(hub.getBlockPos(), null));
            assertTrue(hub.references(b.getBlockPos(), null));
            assertFalse(hub.shearLink(a.getBlockPos(), null));
            verify(hub, times(1)).dropBelt();
            verify(a, never()).dropBelt();
            verify(b, never()).dropBelt();
        }
    }

    @Test
    void beltPickRespectsReachAndWheelEnds() {
        Vec3 start = new Vec3(0, 0, 0);
        Vec3 end = new Vec3(8, 0, 0);
        assertTrue(PhysicsGantryBeltWheelLink.pickBelt(new Vec3(4, 0, 3),
                new Vec3(0, 0, -1), 5, start, end) != null);
        assertTrue(PhysicsGantryBeltWheelLink.pickBelt(new Vec3(4, 0, 3),
                new Vec3(0, 0, -1), 2, start, end) == null);
        assertTrue(PhysicsGantryBeltWheelLink.pickBelt(new Vec3(4, 1, 3),
                new Vec3(0, 0, -1), 5, start, end) == null);
        assertTrue(PhysicsGantryBeltWheelLink.pickBelt(new Vec3(0, 0, 3),
                new Vec3(0, 0, -1), 5, start, end) == null);
    }

    private static PhysicsGantryBeltWheelBlockEntity wheel(Level level, BlockPos pos) throws Exception {
        PhysicsGantryBeltWheelBlockEntity wheel = mock(PhysicsGantryBeltWheelBlockEntity.class, CALLS_REAL_METHODS);
        var links = PhysicsGantryBeltWheelBlockEntity.class.getDeclaredField("additionalLinks");
        links.setAccessible(true);
        links.set(wheel, new java.util.ArrayList<>());
        var position = BlockEntity.class.getDeclaredField("worldPosition");
        position.setAccessible(true);
        position.set(wheel, pos);
        wheel.setLevel(level);
        doNothing().when(wheel).refreshKineticLink();
        doNothing().when(wheel).setChanged();
        doNothing().when(wheel).sendData();
        doNothing().when(wheel).dropBelt();
        return wheel;
    }

    private static void configurePair(org.mockito.MockedStatic<SimulatedHelper> simulated, Level level,
                                      PhysicsGantryBeltWheelBlockEntity left, BlockPos leftPos,
                                      PhysicsGantryBeltWheelBlockEntity right, BlockPos rightPos) {
        simulated.when(() -> SimulatedHelper.getContainingSubLevelId(left)).thenReturn(null);
        simulated.when(() -> SimulatedHelper.getContainingSubLevelId(right)).thenReturn(null);
        simulated.when(() -> SimulatedHelper.findBlockEntity(level, null, leftPos,
                PhysicsGantryBeltWheelBlockEntity.class)).thenReturn(left);
        simulated.when(() -> SimulatedHelper.findBlockEntity(level, null, rightPos,
                PhysicsGantryBeltWheelBlockEntity.class)).thenReturn(right);
    }
}
