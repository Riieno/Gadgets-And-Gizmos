package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.util.SableBufferUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

// Exercise empty tracking bounds and sparse plot discovery with the production mixins loaded
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class SableBoundsGameTests{
    // Preserve all six empty sentinel coordinates through the native packet reader
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void boundsPacketsPreserveEmptyAndOccupiedStates(GameTestHelper helper){
        var buffer = Unpooled.buffer();
        try{
            var occupied = new BoundingBox3i(-30_000_000, -64, 29_999_990, -29_999_990, 319, 30_000_000);
            SableBufferUtils.write(buffer, BoundingBox3i.EMPTY);
            SableBufferUtils.write(buffer, occupied);
            SableBufferUtils.write(buffer, BoundingBox3i.EMPTY);
            var decoded = new BoundingBox3i();
            SableBufferUtils.read(buffer, decoded);
            assertBounds(helper, decoded, BoundingBox3i.EMPTY);
            SableBufferUtils.read(buffer, decoded);
            assertBounds(helper, decoded, occupied);
            SableBufferUtils.read(buffer, decoded);
            assertBounds(helper, decoded, BoundingBox3i.EMPTY);
            helper.assertTrue(!buffer.isReadable(), "Bounds decoding changed the packet length");
            helper.succeed();
        }finally{ buffer.release(); }
    }

    // Keep empty bounds empty when replacing either an absent or an occupied plot box
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void plotAssignmentsPreserveEmptyBounds(GameTestHelper helper){
        var body = createBody(helper);
        var plot = body.getPlot();
        var occupied = new BoundingBox3i(plot.getBoundingBox());
        try{
            plot.setBoundingBox(BoundingBox3i.EMPTY);
            assertBounds(helper, plot.getBoundingBox(), BoundingBox3i.EMPTY);
            plot.setBoundingBox(occupied);
            assertBounds(helper, plot.getBoundingBox(), occupied);
            plot.setBoundingBox(BoundingBox3i.EMPTY);
            assertBounds(helper, plot.getBoundingBox(), BoundingBox3i.EMPTY);
            helper.succeed();
        }finally{
            plot.setBoundingBox(occupied);
            SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body));
        }
    }

    // Find real beacons without walking the empty chunks inside an oversized plot box
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void beaconDiscoveryUsesLoadedPlotContents(GameTestHelper helper){
        var body = createBody(helper);
        var plot = body.getPlot();
        var occupied = new BoundingBox3i(plot.getBoundingBox());
        try{
            plot.setBoundingBox(new BoundingBox3i(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                    Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
            var beacons = SubLevelBlockEntityCollector.getBlockEntities(body, BeaconBlockEntity.class);
            helper.assertTrue(beacons.size() == 1 && beacons.getFirst().getBlockPos().equals(plot.getCenterBlock()),
                    "Loaded beacon discovery lost the plot's beacon");
            helper.succeed();
        }finally{
            plot.setBoundingBox(occupied);
            SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body));
        }
    }

    // Allocate a native beacon plot without involving another test's world blocks
    private static ServerSubLevel createBody(GameTestHelper helper){
        var container = SubLevelContainer.getContainer(helper.getLevel());
        var body = (ServerSubLevel) container.allocateNewSubLevel(new Pose3d());
        var plot = body.getPlot();
        plot.newEmptyChunk(plot.getCenterChunk());
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, Blocks.BEACON.defaultBlockState(), 3);
        return body;
    }

    // Compare corners directly so normalized empty sentinels cannot pass
    private static void assertBounds(GameTestHelper helper, BoundingBox3ic actual, BoundingBox3ic expected){
        helper.assertTrue(actual.minX() == expected.minX() && actual.minY() == expected.minY()
                && actual.minZ() == expected.minZ() && actual.maxX() == expected.maxX()
                && actual.maxY() == expected.maxY() && actual.maxZ() == expected.maxZ(),
                "Empty or occupied plot coordinates changed");
    }
}
