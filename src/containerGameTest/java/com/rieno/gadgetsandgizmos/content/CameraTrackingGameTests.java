package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.view.ViewPose;
import com.rieno.gadgetsandgizmos.lib.view.ViewRaycast;
import com.rieno.gadgetsandgizmos.lib.view.ViewTargetLock;
import com.rieno.gadgetsandgizmos.lib.view.ViewTrackingTargets;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

import java.util.List;
import java.util.Set;

// Exercise bearing support filtering with loaded native plots and the camera tracking API
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class CameraTrackingGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Reject every supplied bearing link regardless of the sublevel's name
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void bearingLinksCannotBecomeTrackingTargets(GameTestHelper helper){
        List<BlockState> links = List.of(CTBlocks.AILERON_BEARING_LINK.get().defaultBlockState(),
                CTBlocks.THRUSTER_BEARING_LINK.get().defaultBlockState(),
                CTBlocks.VECTOR_BEARING_LINK.get().defaultBlockState(),
                BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("simulated",
                        "swivel_bearing_link_block")).defaultBlockState());
        for(BlockState state : links){
            helper.assertTrue(!state.isAir() && state.is(ViewTrackingTargets.IGNORED_BLOCKS),
                    "A bearing plate is missing from the tracking tag: " + state);
            ServerSubLevel body = createBody(helper, state);
            try{
                body.setName("Renamed bearing assembly");
                var filter = new ViewRaycast.Filter(Set.of(body.getUniqueId().toString()), true);
                var hit = hit(body);
                Vec3 origin = hit.position().add(0, 0, 4);
                var pose = new ViewPose(origin, new Quaterniond(), 70);
                helper.assertTrue(!ViewTrackingTargets.isTrackable(body), "A bare bearing plate remained trackable");
                helper.assertTrue(ViewTrackingTargets.hits(helper.getLevel(), List.of(hit), filter, null).isEmpty(),
                        "A direct bearing plate hit became a tracking target");
                helper.assertTrue(ViewTrackingTargets.acquire(helper.getLevel(), pose, 8, filter, null,
                        List.of(hit)).isEmpty(), "Acquisition selected a bare bearing plate");
                var ref = new ViewTrackingTargets.Reference(null, body.getUniqueId(), hit.localBlockPos());
                helper.assertTrue(ViewTrackingTargets.resolve(helper.getLevel(), ref, filter, null) == null,
                        "A retained bearing plate reference remained valid");
            }finally{
                SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body));
            }
        }
        helper.succeed();
    }

    // Retain a mounted contraption until only its support plate remains
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void mountedBlocksRemainTrackableAndReleaseTheirLockWhenRemoved(GameTestHelper helper){
        ServerSubLevel body = createBody(helper, CTBlocks.AILERON_BEARING_LINK.get().defaultBlockState());
        try{
            body.setName("Aileron Bearing Link");
            var plot = body.getPlot();
            plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO.east(), Blocks.STONE.defaultBlockState(), 3);
            var filter = new ViewRaycast.Filter(Set.of(body.getUniqueId().toString()), true);
            var hit = hit(body);
            Vec3 origin = hit.position().add(0, 0, 4);
            var targets = ViewTrackingTargets.hits(helper.getLevel(), List.of(hit), filter, null);
            helper.assertTrue(ViewTrackingTargets.isTrackable(body) && targets.size() == 1,
                    "A populated bearing contraption was excluded");
            ViewTargetLock<ViewTrackingTargets.Reference> lock = new ViewTargetLock<>();
            helper.assertTrue(lock.update(origin, 8, targets,
                    ref -> ViewTrackingTargets.resolve(helper.getLevel(), ref, filter, null)) != null,
                    "The mounted contraption could not be locked");
            plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO.east(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(!ViewTrackingTargets.isTrackable(body), "A removed mounted block remained eligible");
            helper.assertTrue(lock.update(origin, 8, List.of(),
                    ref -> ViewTrackingTargets.resolve(helper.getLevel(), ref, filter, null)) == null
                    && lock.key() == null, "Target lock retained a bare support plate");
            plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO.east(), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(ViewTrackingTargets.isTrackable(body), "A new mounted block did not restore eligibility");
        }finally{
            SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body));
        }
        helper.succeed();
    }

    // Allocate an isolated native plot without waiting for bearing assembly ticks
    private static ServerSubLevel createBody(GameTestHelper helper, BlockState state){
        var container = SubLevelContainer.getContainer(helper.getLevel());
        ServerSubLevel body = (ServerSubLevel) container.allocateNewSubLevel(new Pose3d());
        var plot = body.getPlot();
        plot.newEmptyChunk(plot.getCenterChunk());
        Vec3 center = plot.getCenterBlock().getCenter();
        Vec3 world = helper.absolutePos(new BlockPos(1, 4, 1)).getCenter();
        body.logicalPose().rotationPoint().set(center.x, center.y, center.z);
        body.logicalPose().position().set(world.x, world.y, world.z);
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, state, 3);
        body.updateBoundingBox();
        return body;
    }

    // Supply the same native hit identity used by the camera's ray samples
    private static ViewRaycast.Result hit(ServerSubLevel body){
        BlockPos pos = body.getPlot().getCenterBlock();
        return new ViewRaycast.Result("block", SableTransformApi.toWorldPosition(body, pos.getCenter()), 4, pos,
                body.getLevel().getBlockState(pos), body.getUniqueId(), "", "", "south");
    }
}
