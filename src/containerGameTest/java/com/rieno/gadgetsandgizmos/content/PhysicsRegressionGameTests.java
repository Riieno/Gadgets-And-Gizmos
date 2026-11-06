package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.lib.physics.SableEntityQueryApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableFrameConstraint;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelAssemblyApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SablePhysicalConnectionApi;
import com.rieno.gadgetsandgizmos.lib.view.ViewPose;
import com.rieno.gadgetsandgizmos.lib.view.ViewRaycast;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Exercise camera coordinate spaces and native bearing lifetime in an isolated server
@GameTestHolder("createthrusters_physics_regressions")
@PrefixGameTestTemplate(false)
public final class PhysicsRegressionGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Local entity queries must not repeat Sable's expansion into overlapping plots
    @GameTest(template = "empty", batch = "camera_queries", timeoutTicks = 100)
    public static void cameraQueriesStayInsideEachSelectedSpace(GameTestHelper helper){
        List<ServerSubLevel> bodies = new ArrayList<>();
        var level = helper.getLevel();
        Vec3 target = helper.absolutePos(new BlockPos(2, 12, 2)).getCenter();
        try{
            for(int idx = 0; idx < 48; idx++){
                var body = ShipDoorGameTests.newBody(helper, target);
                bodies.add(body);
                body.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), 3);
                body.updateBoundingBox();
            }
            var body = bodies.getFirst();
            var entity = EntityType.PIG.create(level);
            Vec3 local = body.getPlot().getCenterBlock().getCenter().add(0, 1, 0);
            entity.setPos(local);
            level.addFreshEntity(entity);
            ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)entity).sable$setTrackingSubLevel(body);
            try{
                var world = SableTransformApi.toWorldPosition(body, local);
                helper.assertTrue(SableEntityQueryApi.localEntities(level, new AABB(world, world).inflate(2), val -> val == entity).size() == 1,
                        "The world query lost its tracked entity at " + entity.position() + "; expected " + world);
                helper.assertTrue(SableEntityQueryApi.localEntities(level, new AABB(local, local).inflate(2), val -> val == entity).isEmpty(),
                        "The plot query expanded into world entities");
                var pose = new ViewPose(world.add(0, 0, 5), new Quaterniond(), 70);
                var hit = ViewRaycast.trace(level, pose, 20, new ViewRaycast.Filter(Set.of("minecraft:pig"), true), null, 0, 1);
                helper.assertTrue(hit.hit() && hit.entityId().equals(entity.getUUID().toString()),
                        "The camera lost a filtered entity inside an overlapping plot");
                helper.assertTrue(body.getUniqueId().equals(hit.subLevelId()), "The camera returned the wrong plot identity");
                var blockCentre = SableTransformApi.toWorldPosition(body, body.getPlot().getCenterBlock().getCenter());
                var blockHit = ViewRaycast.traceBlocks(level, new ViewPose(blockCentre.add(0, 0, 5), new Quaterniond(), 70),
                        1024, null, 0, 1);
                helper.assertTrue(blockHit.hit() && blockHit.subLevelId() != null && Math.abs(blockHit.distance() - 4.5D) < 1.0E-6D,
                        "Clipping a long ray lost the nearest plot block: " + blockHit);
                long started = System.nanoTime();
                for(int repeat = 0; repeat < 16; repeat++) for(var candidate : bodies){
                    var centre = candidate.getPlot().getCenterBlock().getCenter();
                    level.getEntities((net.minecraft.world.entity.Entity)null, new AABB(centre, centre.add(0, 0, 20)).inflate(1),
                            val -> val.isAlive() && val.isPickable());
                }
                long expanded = System.nanoTime() - started;
                started = System.nanoTime();
                for(int repeat = 0; repeat < 16; repeat++) for(var candidate : bodies){
                    var centre = candidate.getPlot().getCenterBlock().getCenter();
                    SableEntityQueryApi.localEntities(level, new AABB(centre, centre.add(0, 0, 20)).inflate(1),
                            val -> val.isAlive() && val.isPickable());
                }
                com.mojang.logging.LogUtils.getLogger().info("Camera query benchmark with {} overlapping plots: expanded={} ms, local={} ms",
                        bodies.size(), expanded / 1.0E6D, (System.nanoTime() - started) / 1.0E6D);
            }finally{ entity.discard(); }
        }finally{ SableSubLevelLifecycleApi.remove(level, bodies); }
        helper.succeed();
    }

    // Preserve a multipart target even when its parent is outside the ray's spatial query
    @GameTest(template = "empty", batch = "multipart_queries")
    public static void cameraQueriesRetainPartsOutsideTheirParentBounds(GameTestHelper helper){
        var level = helper.getLevel();
        Vec3 target = helper.absolutePos(new BlockPos(2, 12, 2)).getCenter();
        var parent = EntityType.ENDER_DRAGON.create(level);
        parent.setPos(target.add(16, 0, 0));
        helper.assertTrue(level.addFreshEntity(parent), "The multipart fixture failed to spawn");
        helper.runAfterDelay(1, () -> {
            try{
                var part = parent.getSubEntities()[0];
                part.setPos(target);
                var bounds = new AABB(target, target).inflate(2);
                helper.assertTrue(!parent.getBoundingBox().intersects(bounds), "The parent overlapped the part query fixture");
                var selected = SableEntityQueryApi.localEntities(level, bounds, val -> val == part);
                helper.assertTrue(selected.size() == 1 && selected.getFirst() == part,
                        "A multipart target was lost when its parent was outside the selected bounds; registered=" + level.getPartEntities().size());
                var hit = ViewRaycast.trace(level, new ViewPose(target.add(0, 0, 5), new Quaterniond(), 70), 20,
                        new ViewRaycast.Filter(Set.of(part.getUUID().toString()), true), null, 0, 1);
                helper.assertTrue(hit.hit() && hit.entityId().equals(part.getUUID().toString()),
                        "The camera missed a multipart target outside its parent's bounds");
            }finally{ parent.discard(); }
            helper.succeed();
        });
    }

    // Native body removal must retire the joint before its handle can outlive the scene body
    @GameTest(template = "empty", batch = "joint_lifetime")
    public static void bodyRemovalRetiresManagedJoints(GameTestHelper helper){
        var body = ShipDoorGameTests.newBody(helper);
        body.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), 3);
        var pipeline = SubLevelContainer.getContainer(helper.getLevel()).physicsSystem().getPipeline();
        try(var joint = new SableFrameConstraint()){
            var centre = body.getPlot().getCenterBlock().getCenter();
            helper.assertTrue(joint.update(pipeline, null, body, new Vector3d(),
                    new Vector3d(centre.x, centre.y, centre.z), new Quaterniond()),
                    "The native frame joint was not created");
            SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body));
            helper.assertTrue(!joint.isValid(), "The joint retained a removed native body");
            joint.close();
        }finally{
            if(!body.isRemoved()) SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body));
        }
        helper.succeed();
    }

    // Wrench every initial bearing direction without deleting its head or misplacing its payload
    @GameTest(template = "empty", batch = "bearing_wrench", timeoutTicks = 100)
    public static void wrenchRotationRetainsHeadAndDisassemblyDirection(GameTestHelper helper){
        var sequence = helper.startSequence();
        for(Direction facing : Direction.values()){
            sequence.thenIdle(1).thenExecute(() -> verifyWrenchRotation(helper, facing));
        }
        sequence.thenSucceed();
    }

    // Let the normal chunk cache advance before reusing a released native plot
    private static void verifyWrenchRotation(GameTestHelper helper, Direction facing){
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 3, 2));
        for(Direction dir : Direction.values()) level.removeBlock(pos.relative(dir), false);
        level.setBlock(pos, CTBlocks.VECTOR_BEARING.get().defaultBlockState().setValue(VectorBearingBlock.FACING, facing), 3);
        level.setBlock(pos.relative(facing), Blocks.STONE.defaultBlockState(), 3);
        var bearing = (VectorBearingBlockEntity)level.getBlockEntity(pos);
        bearing.toggleMountedBlock();
        UUID id = bearing.getMountedSubLevelId();
        helper.assertTrue(id != null, "Failed to assemble the " + facing + " fixture");
        try{
            Direction clicked = facing.getAxis() == Direction.Axis.X ? Direction.UP : Direction.EAST;
            var state = bearing.getBlockState();
            var hit = new BlockHitResult(pos.getCenter(), clicked, pos, false);
            var ctx = new UseOnContext(level, null, InteractionHand.MAIN_HAND, ItemStack.EMPTY, hit);
            CTBlocks.VECTOR_BEARING.get().onWrenched(state, ctx);
            bearing.tick();
            helper.assertTrue(id.equals(bearing.getMountedSubLevelId()), "Wrenching replaced or deleted the head plot");
            Direction rotated = bearing.getBearingFacing();
            helper.assertTrue(rotated != facing, "The wrench did not rotate the bearing");
            bearing.disassembleMountedBlock();
            helper.assertTrue(level.getBlockState(pos.relative(rotated)).is(Blocks.STONE),
                    "Disassembly placement failed from " + facing + " to " + rotated + "; destination=" + java.util.Arrays.stream(Direction.values())
                            .map(dir -> dir + "=" + level.getBlockState(pos.relative(dir)).getBlock()).toList());
            level.removeBlock(pos.relative(rotated), false);
        }finally{
            var body = SubLevelContainer.getContainer(level).getSubLevel(id);
            if(body instanceof ServerSubLevel child) SableSubLevelLifecycleApi.remove(level, List.of(child));
            level.removeBlock(pos, false);
        }
    }

    // Recreate the block entity through its unload and saved-data paths while retaining the same head
    @GameTest(template = "empty", batch = "bearing_reload")
    public static void bearingReloadRetainsItsHead(GameTestHelper helper){
        helper.runAfterDelay(1, () -> reloadBearing(helper));
    }

    // Reload after the preceding batch has released its native plot and chunk cache
    private static void reloadBearing(GameTestHelper helper){
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 3, 2));
        var state = CTBlocks.VECTOR_BEARING.get().defaultBlockState();
        level.setBlock(pos, state, 3);
        var bearing = (VectorBearingBlockEntity)level.getBlockEntity(pos);
        bearing.toggleMountedBlock();
        UUID id = bearing.getMountedSubLevelId();
        helper.assertTrue(id != null, "The bearing head fixture failed to assemble");
        boolean scheduled = false;
        try{
            var saved = bearing.saveWithoutMetadata(level.registryAccess());
            bearing.onChunkUnloaded();
            level.removeBlockEntity(pos);
            var restored = new VectorBearingBlockEntity(pos, state);
            restored.loadWithComponents(saved, level.registryAccess());
            level.setBlockEntity(restored);
            restored.tick();
            helper.assertTrue(id.equals(restored.getMountedSubLevelId()) && restored.isMountedAssemblyPresent(),
                    "Reloading lost the saved head or failed to recreate its joint");
            helper.runAfterDelay(5, () -> {
                try{
                    helper.assertTrue(id.equals(restored.getMountedSubLevelId()) && restored.isMountedAssemblyPresent(),
                            "The reloaded head failed after native physics resumed");
                    restored.disassembleMountedBlock();
                }finally{ clearBearing(helper, pos, id); }
                helper.succeed();
            });
            scheduled = true;
        }finally{
            if(!scheduled) clearBearing(helper, pos, id);
        }
    }

    // Release a test head and base after delayed native-physics assertions
    private static void clearBearing(GameTestHelper helper, BlockPos pos, UUID id){
        var level = helper.getLevel();
        var body = SubLevelContainer.getContainer(level).getSubLevel(id);
        if(body instanceof ServerSubLevel child) SableSubLevelLifecycleApi.remove(level, List.of(child));
        level.removeBlock(pos, false);
    }

    // Keep the same loaded head when its base moves from terrain into a plot
    @GameTest(template = "empty", batch = "bearing_transfer", timeoutTicks = 160)
    public static void assemblingBearingRetainsItsHead(GameTestHelper helper){
        verifyBearingTransfer(helper, false);
    }

    // Keep nested heads when their carrier is assembled into another plot
    @GameTest(template = "empty", batch = "bearing_nested_transfer", timeoutTicks = 160)
    public static void assemblingCarrierRetainsItsBearingHead(GameTestHelper helper){
        verifyBearingTransfer(helper, true);
    }

    // Move a loaded base twice and check the saved head and its payload after physics resumes
    private static void verifyBearingTransfer(GameTestHelper helper, boolean nested){
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 5, 2));
        List<ServerSubLevel> bodies = new ArrayList<>();
        level.setBlock(pos, CTBlocks.VECTOR_BEARING.get().defaultBlockState(), 3);
        level.setBlock(pos.above(), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        var bearing = (VectorBearingBlockEntity)level.getBlockEntity(pos);
        bearing.toggleMountedBlock();
        UUID headId = bearing.getMountedSubLevelId();
        helper.assertTrue(headId != null, "The transfer fixture did not assemble its head");
        var head = (ServerSubLevel)SubLevelContainer.getContainer(level).getSubLevel(headId);
        bodies.add(head);
        BlockPos headPos = bearing.getMountedLocalPos();
        BlockPos payload = headPos.above();
        head.updateBoundingBox();
        try{
            var res = SubLevelAssemblyApi.assembleBlock(level, pos, pos, true, true);
            var carrier = res == null ? null : (ServerSubLevel)res.subLevel();
            helper.assertTrue(carrier != null, "The transfer fixture did not assemble its carrier");
            bodies.add(carrier);
            BlockPos moved = carrier.getPlot().getCenterBlock();
            if(nested){
                var nextRes = SubLevelAssemblyApi.assembleBlock(level, moved, moved, true, true);
                var next = nextRes == null ? null : (ServerSubLevel)nextRes.subLevel();
                helper.assertTrue(next != null, "The transfer fixture did not reassemble its carrier");
                bodies.add(next);
                moved = next.getPlot().getCenterBlock();
            }
            var transferred = (VectorBearingBlockEntity)level.getBlockEntity(moved);
            verifyTransferredHead(helper, transferred, headId, headPos, payload);
            helper.runAfterDelay(10, () -> {
                try{
                    verifyTransferredHead(helper, transferred, headId, headPos, payload);
                    var link = (VectorBearingLinkBlockEntity)level.getBlockEntity(headPos);
                    helper.assertTrue(link.isOwnedBy(transferred), "The head retained its old parent after transfer");
                    transferred.disassembleMountedBlock();
                    helper.assertTrue(level.getBlockState(transferred.getBlockPos().above()).is(Blocks.GOLD_BLOCK),
                            "Explicit disassembly lost the transferred payload");
                }finally{ clearTransferredBodies(helper, bodies); }
                helper.succeed();
            });
        }catch(Exception err){
            clearTransferredBodies(helper, bodies);
            throw new RuntimeException(err);
        }finally{ level.removeBlock(pos, false); }
    }

    // Check that transfer preserved both the head identity and blocks in its plot
    private static void verifyTransferredHead(GameTestHelper helper, VectorBearingBlockEntity bearing,
            UUID headId, BlockPos headPos, BlockPos payload){
        helper.assertTrue(bearing != null && headId.equals(bearing.getMountedSubLevelId())
                        && headPos.equals(bearing.getMountedLocalPos()) && bearing.isMountedAssemblyPresent(),
                "Assembly detached the bearing head");
        helper.assertTrue(helper.getLevel().getBlockState(headPos).getBlock() instanceof VectorBearingLinkBlock
                        && helper.getLevel().getBlockState(payload).is(Blocks.GOLD_BLOCK),
                "Assembly dropped the head or its payload");
        var carrier = SableLevelApi.containing(bearing);
        helper.assertTrue(carrier != null && SablePhysicalConnectionApi.connectedBodyIds(helper.getLevel(),
                        List.of(carrier.getUniqueId())).contains(headId),
                "Assembly left the bearing head without a physical joint");
    }

    // Skip plots already removed by explicit disassembly or an empty carrier transfer
    private static void clearTransferredBodies(GameTestHelper helper, List<ServerSubLevel> bodies){
        var level = helper.getLevel();
        var container = SubLevelContainer.getContainer(level);
        SableSubLevelLifecycleApi.remove(level, bodies.stream()
                .filter(body -> container.getSubLevel(body.getUniqueId()) == body).toList());
    }

    // Empty hands must mine every case face while the front remains an interaction surface
    @GameTest(template = "empty", batch = "display_mining")
    public static void onlyDisplayFrontRejectsEmptyHandMining(GameTestHelper helper){
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 4, 2));
        var block = CTBlocks.ACC_DISPLAY_BLOCK.get();
        var state = block.defaultBlockState().setValue(AccDisplayBlock.FACING, Direction.NORTH);
        level.setBlock(pos, state, 3);
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "DisplayMining"));
        for(Direction face : Direction.values()){
            Vec3 dir = Vec3.atLowerCornerOf(face.getNormal());
            Vec3 eye = pos.getCenter().add(dir.scale(2));
            player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
            Vec3 look = dir.scale(-1);
            player.setYRot((float)Math.toDegrees(Math.atan2(-look.x, look.z)));
            player.setXRot((float)-Math.toDegrees(Math.asin(look.y)));
            float progress = state.getDestroyProgress(player, level, pos);
            helper.assertTrue(face == Direction.NORTH ? progress == 0 : progress > 0,
                    "Incorrect empty-hand mining protection on " + face + ": " + progress);
        }
        helper.succeed();
    }
}
