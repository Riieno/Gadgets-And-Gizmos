package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.discovery.SableSubLevelResidency;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelAssemblyApi;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class ControllerAndBeltRegressionGameTests{
    private ControllerAndBeltRegressionGameTests(){}

    @GameTest(template = "empty_container", timeoutTicks = 100)
    public static void chunkLoadingMigration(GameTestHelper ctx){
        var level = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(new BlockPos(4, 3, 4));
        level.setBlock(pos.below(), CTBlocks.SHIP_CONTROL_MODULE.get().defaultBlockState(), 3);
        level.setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        var controller = (AdvancedContraptionControllerBlockEntity)level.getBlockEntity(pos);
        ctx.assertTrue(controller.isMountedOnShipControlModule(), "SCM is not mounted");
        ctx.assertTrue(!controller.isScmChunkLoading(), "New SCM must default to chunk loading off");
        CompoundTag legacy = new CompoundTag();
        controller.writeAdditionalControllerManifestData(legacy, level.registryAccess());
        removePreference(legacy);
        controller.loadWithComponents(legacy, level.registryAccess());
        ctx.assertTrue(controller.isScmChunkLoading(), "Existing SCM must preserve chunk loading");
        ctx.assertTrue(controller.setScmChunkLoading(false), "Mounted SCM rejected toggle");
        CompoundTag saved = controller.saveWithoutMetadata(level.registryAccess());
        controller.loadWithComponents(saved, level.registryAccess());
        ctx.assertTrue(!controller.isScmChunkLoading(), "Disabled preference did not survive reload");
        ctx.succeed();
    }

    @GameTest(template = "empty_container", timeoutTicks = 100)
    public static void blankDisplayGrowsAfterItsGeometryWasCached(GameTestHelper ctx){
        var level = ctx.getLevel();
        var state = CTBlocks.ACC_DISPLAY_PANEL.get().defaultBlockState();
        BlockPos pos = ctx.absolutePos(new BlockPos(4, 4, 4));
        level.setBlock(pos, state, 3);
        var root = (AccDisplayBlockEntity)level.getBlockEntity(pos);
        ctx.assertTrue(root.networkWidth() == 1 && root.networkHeight() == 1, "Initial geometry is invalid");
        var right = root.screenRight();
        for(int x = 2; x >= 0; x--){
            level.setBlock(pos.below().relative(right, x), state, 3);
            level.setBlock(pos.relative(right, x), state, 3);
        }
        ctx.runAtTickTime(3, () -> {
            ctx.assertTrue(root.networkWidth() == 3 && root.networkHeight() == 2, "Blank display retained partial geometry");
            level.removeBlock(pos.relative(right, 2), false);
            ctx.assertTrue(root.networkWidth() == 2 && root.networkHeight() == 2, "Removed panel retained stale geometry");
            ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", batch = "belt_sequential", timeoutTicks = 140)
    public static void sequentialBeltAssemblyKeepsBothEndpoints(GameTestHelper ctx){
        var level = ctx.getLevel();
        var state = CTBlocks.PHYSICS_GANTRY_BELT_WHEEL.get().defaultBlockState();
        BlockPos a = ctx.absolutePos(new BlockPos(2, 3, 3));
        BlockPos b = a.offset(4, 0, 0);
        BlockPos c = a.offset(8, 0, 0);
        for(BlockPos pos : List.of(a, b, c)) level.setBlock(pos, state, 3);
        var first = (PhysicsGantryBeltWheelBlockEntity)level.getBlockEntity(a);
        var middle = (PhysicsGantryBeltWheelBlockEntity)level.getBlockEntity(b);
        var last = (PhysicsGantryBeltWheelBlockEntity)level.getBlockEntity(c);
        first.addLinkedTarget(b, null, true);
        middle.addLinkedTarget(a, null, false);
        middle.addLinkedTarget(c, null, true);
        last.addLinkedTarget(b, null, false);
        List<ServerSubLevel> bodies = new ArrayList<>();
        var firstBody = SubLevelAssemblyApi.assembleSingle(level, a);
        ctx.assertTrue(firstBody != null, "First wheel did not assemble");
        bodies.add(firstBody);
        var residency = SableSubLevelResidency.lease("belt_assembly_test/" + firstBody.getUniqueId());
        residency.retain(firstBody);
        var movedFirst = SubLevelBlockEntityCollector.getBlockEntities(firstBody, PhysicsGantryBeltWheelBlockEntity.class).getFirst();
        ctx.assertTrue(movedFirst.resolveLinkedWheel() == middle, "First assembly lost its world endpoint");
        ctx.assertTrue(middle.resolveLinkedWheel() == movedFirst, "Stationary endpoint did not follow first assembly");
        ctx.runAtTickTime(50, () -> {
            try{
                ctx.assertTrue(dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getSubLevel(firstBody.getUniqueId()) == firstBody, "First ship unloaded during assembly test");
                var middleBody = SubLevelAssemblyApi.assembleSingle(level, b);
                ctx.assertTrue(middleBody != null, "Second wheel did not assemble");
                bodies.add(middleBody);
                residency.retain(middleBody);
                var movedMiddle = SubLevelBlockEntityCollector.getBlockEntities(middleBody, PhysicsGantryBeltWheelBlockEntity.class).getFirst();
                ctx.assertTrue(movedMiddle.linkCount() == 2, "Second assembly discarded a belt");
                ctx.assertTrue(movedFirst.saveWithoutMetadata(level.registryAccess()).getBoolean("ReturnsBeltOnBreak"), "Assembly lost the original belt refund owner");
                CompoundTag middleTag = movedMiddle.saveWithoutMetadata(level.registryAccess());
                ctx.assertTrue(!middleTag.getBoolean("ReturnsBeltOnBreak") && middleTag.getList("AdditionalLinks", Tag.TAG_COMPOUND).getCompound(0).getBoolean("Refundable"), "Assembly duplicated or lost belt refund ownership");
                ctx.assertTrue(movedMiddle.resolveLinkedWheel() == movedFirst, "Second assembly lost its older sub-level endpoint");
                ctx.assertTrue(movedFirst.resolveLinkedWheel() == movedMiddle, "Older sub-level endpoint did not follow second assembly");
                ctx.assertTrue(last.resolveLinkedWheel() == movedMiddle, "World endpoint did not follow second assembly");
                ctx.assertTrue(!movedMiddle.isAssemblyTransferInProgress(), "Moved wheel cannot be broken normally");
                movedMiddle.breakLink(true, false);
                ctx.assertTrue(!movedFirst.hasLinkedTarget() && !last.hasLinkedTarget(), "Normal belt removal stopped cleaning opposite endpoints");
                ctx.succeed();
            }finally{
                residency.close();
                SableSubLevelLifecycleApi.remove(level, bodies.stream().filter(body -> dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getSubLevel(body.getUniqueId()) == body).toList());
            }
        });
    }

    @GameTest(template = "empty_container", batch = "belt_multiple", timeoutTicks = 100)
    public static void singleAssemblyKeepsAllBeltLinks(GameTestHelper ctx){
        var level = ctx.getLevel();
        var state = CTBlocks.PHYSICS_GANTRY_BELT_WHEEL.get().defaultBlockState();
        BlockPos a = ctx.absolutePos(new BlockPos(2, 3, 3));
        BlockPos b = a.offset(4, 0, 0);
        BlockPos c = a.offset(8, 0, 0);
        for(BlockPos pos : List.of(a, b, c)) level.setBlock(pos, state, 3);
        var first = (PhysicsGantryBeltWheelBlockEntity)level.getBlockEntity(a);
        var middle = (PhysicsGantryBeltWheelBlockEntity)level.getBlockEntity(b);
        var last = (PhysicsGantryBeltWheelBlockEntity)level.getBlockEntity(c);
        first.addLinkedTarget(b, null, true);
        middle.addLinkedTarget(a, null, false);
        middle.addLinkedTarget(c, null, true);
        last.addLinkedTarget(b, null, false);
        var body = SubLevelAssemblyApi.assemble(level, a, List.of(a, b, c));
        ctx.assertTrue(body != null, "Wheel set did not assemble");
        try{
            var wheels = SubLevelBlockEntityCollector.getBlockEntities(body, PhysicsGantryBeltWheelBlockEntity.class);
            ctx.assertTrue(wheels.size() == 3, "Assembled wheel set is incomplete");
            for(var wheel : wheels){
                ctx.assertTrue(wheel.resolveLinkedWheel() != null, "Assembled wheel lost an endpoint");
                ctx.assertTrue(!wheel.isAssemblyTransferInProgress(), "New endpoint suppressed ordinary breaking");
            }
            var movedMiddle = wheels.stream().filter(wheel -> wheel.linkCount() == 2).findFirst().orElseThrow();
            movedMiddle.breakLink(true, false);
            for(var wheel : wheels) ctx.assertTrue(!wheel.hasLinkedTarget(), "Breaking moved belts did not clear both sides");
            ctx.succeed();
        }finally{
            SableSubLevelLifecycleApi.remove(level, List.of(body));
        }
    }

    @GameTest(template = "empty_container", batch = "scm_chunk_loading", timeoutTicks = 100)
    public static void chunkLoadingToggleReleasesOnlyItsOwnShipTickets(GameTestHelper ctx){
        var level = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(new BlockPos(4, 3, 4));
        level.setBlock(pos.below(), CTBlocks.SHIP_CONTROL_MODULE.get().defaultBlockState(), 3);
        level.setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        var body = SubLevelAssemblyApi.assemble(level, pos.below(), List.of(pos.below(), pos));
        ctx.assertTrue(body != null, "SCM ship did not assemble");
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        var otherOwner = SableSubLevelResidency.lease("other_chunk_owner/" + body.getUniqueId());
        try{
            var controller = SubLevelBlockEntityCollector.getBlockEntities(body, AdvancedContraptionControllerBlockEntity.class).getFirst();
            ctx.assertTrue(!controller.isScmChunkLoading(), "New ship inherited legacy chunk loading");
            ctx.assertTrue(controller.setScmChunkLoading(true), "SCM loading could not be enabled");
            ctx.assertTrue(container.collectForceLoadedSubLevels().contains(body), "Enabled SCM did not retain its ship");
            otherOwner.retain(body);
            ctx.assertTrue(controller.setScmChunkLoading(false), "SCM loading could not be disabled");
            ctx.assertTrue(container.collectForceLoadedSubLevels().contains(body), "Disabling SCM removed another owner's tickets");
            otherOwner.close();
            ctx.assertTrue(!container.collectForceLoadedSubLevels().contains(body), "Disabled SCM retained ship tickets");
            ctx.succeed();
        }finally{
            otherOwner.close();
            SableSubLevelLifecycleApi.remove(level, List.of(body));
        }
    }

    private static void removePreference(CompoundTag tag){
        tag.remove("ScmChunkLoading");
        for(String key : tag.getAllKeys()){
            if(tag.get(key) instanceof CompoundTag nested) removePreference(nested);
            else if(tag.get(key) instanceof net.minecraft.nbt.ListTag list){
                for(Tag entry : list) if(entry instanceof CompoundTag nested) removePreference(nested);
            }
        }
    }
}
