package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelAssemblyApi;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.List;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class LinkerLifetimeGameTests{
    private LinkerLifetimeGameTests(){}

    @GameTest(template = "empty_container", batch = "linker_lifetime_remote", timeoutTicks = 100)
    public static void controllerUnloadingAndDeletionDoNotOwnOtherReferencedSubLevels(GameTestHelper ctx){
        var level = ctx.getLevel();
        BlockPos targetPos = ctx.absolutePos(new BlockPos(7, 3, 7));
        level.setBlock(targetPos, Blocks.STONE.defaultBlockState(), 3);
        var targetBody = SubLevelAssemblyApi.assembleSingle(level, targetPos);
        ctx.assertTrue(targetBody != null, "Target did not assemble");
        var target = targetBody.getPlot().getCenterBlock();
        var container = SubLevelContainer.getContainer(level);
        var plane = target.east();
        level.setBlock(plane, CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.get().defaultBlockState(), 3);
        ((ContraptionNetworkLinkerPlaneBlockEntity)level.getBlockEntity(plane)).setPlane(Direction.EAST, ContraptionNetworkLinkerData.LinkMode.OUTPUT);
        try{
            for(boolean face : new boolean[]{false, true}) for(var reason : SubLevelRemovalReason.values()){
                BlockPos pos = ctx.absolutePos(new BlockPos(2, 3, 2));
                level.setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
                var body = SubLevelAssemblyApi.assembleSingle(level, pos);
                ctx.assertTrue(body != null, "Controller did not assemble");
                var acc = SubLevelBlockEntityCollector.getBlockEntities(body, AdvancedContraptionControllerBlockEntity.class).getFirst();
                ItemStack linker = new ItemStack(CTItems.CONTRAPTION_NETWORK_LINKER.get());
                var reference = new ContraptionNetworkLinkerData.LinkedTarget(face ? plane : target, targetBody.getUniqueId(),
                        face ? "createthrusters:contraption_network_linker_plane" : "minecraft:stone", "Remote block",
                        ContraptionNetworkLinkerData.LinkMode.OUTPUT, face ? ContraptionNetworkLinkerData.TargetScope.FACE : ContraptionNetworkLinkerData.TargetScope.BLOCK,
                        face ? List.of(new ContraptionNetworkLinkerData.LinkedFace(Direction.EAST, "East", "")) : List.of());
                ContraptionNetworkLinkerData.writeTargets(linker, List.of(reference), ContraptionNetworkLinkerData.LinkMode.OUTPUT);
                acc.getLinkerSlotHandler().setStackInSlot(0, linker);acc.initialize();acc.tick();acc.syncTrackedLinkerRegistry();
                var context = new SubLevelSchematicSerializationContext(
                        SubLevelSchematicSerializationContext.Type.SAVE,
                        new BoundingBox3i(0, 0, 0, 1, 1, 1));
                var previous = SubLevelSchematicSerializationContext.getCurrentContext();
                try{
                    SubLevelSchematicSerializationContext.setCurrentContext(context);
                    ctx.assertTrue(StreamSupport.stream(acc.sable$getLoadingDependencies().spliterator(), false)
                            .anyMatch(dependency -> dependency == targetBody), "Schematic save lost its target references");
                }finally{ SubLevelSchematicSerializationContext.setCurrentContext(previous); }
                ctx.assertTrue(!acc.sable$getLoadingDependencies().iterator().hasNext(), "Runtime control references were retained");
                targetBody.getPlot().updateBoundingBox();targetBody.updateBoundingBox();targetBody.forceUpdateGlobalBounds();
                body.getPlot().updateBoundingBox();body.updateBoundingBox();body.forceUpdateGlobalBounds();
                ctx.assertTrue(SubLevelHelper.getLoadingDependencyChain(body).size() == 1,
                        "A control reference became a runtime loading dependency");
                if(reason == SubLevelRemovalReason.UNLOADED) ((ServerSubLevelContainer)container).getHoldingChunkMap()
                        .moveToUnloaded(body, new ChunkPos(100000, 100000));
                else SableSubLevelLifecycleApi.remove(level, SubLevelHelper.getLoadingDependencyChain(body));
                ctx.assertTrue(container.getSubLevel(targetBody.getUniqueId()) == targetBody && !targetBody.isRemoved(), "Source removal deleted remote sub-level");
                ctx.assertTrue(level.getBlockState(target).is(Blocks.STONE), "Source removal deleted remote block");
            }
        }finally{
            if(container.getSubLevel(targetBody.getUniqueId()) == targetBody) SableSubLevelLifecycleApi.remove(level, List.of(targetBody));
        }
        ctx.succeed();
    }

    @GameTest(template = "empty_container", batch = "linker_lifetime", timeoutTicks = 100)
    public static void controllerUnloadingAndDeletionNeverRemoveReferencedWorldBlocks(GameTestHelper ctx){
        var level = ctx.getLevel();
        for(var reason : SubLevelRemovalReason.values()){
            BlockPos pos = ctx.absolutePos(new BlockPos(2, 3, 2));
            BlockPos target = ctx.absolutePos(new BlockPos(6, 3, 6));
            BlockPos plane = target.east();
            level.setBlock(target, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(plane, CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.get().defaultBlockState(), 3);
            ((ContraptionNetworkLinkerPlaneBlockEntity)level.getBlockEntity(plane)).setPlane(Direction.EAST, ContraptionNetworkLinkerData.LinkMode.OUTPUT);
            ItemStack linker = new ItemStack(CTItems.CONTRAPTION_NETWORK_LINKER.get());
            var blockTarget = new ContraptionNetworkLinkerData.LinkedTarget(target, null, "minecraft:stone", "World block",
                    ContraptionNetworkLinkerData.LinkMode.OUTPUT, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
            var faceTarget = new ContraptionNetworkLinkerData.LinkedTarget(plane, null, "createthrusters:contraption_network_linker_plane", "World face",
                    ContraptionNetworkLinkerData.LinkMode.OUTPUT, ContraptionNetworkLinkerData.TargetScope.FACE,
                    List.of(new ContraptionNetworkLinkerData.LinkedFace(Direction.EAST, "East", "")));
            ContraptionNetworkLinkerData.writeTargets(linker, List.of(blockTarget, faceTarget), ContraptionNetworkLinkerData.LinkMode.OUTPUT);
            ContraptionNetworkLinkerTracker.get(level.getServer()).observeLinker(level, linker);
            level.setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
            var body = SubLevelAssemblyApi.assembleSingle(level, pos);
            ctx.assertTrue(body != null, "Controller did not assemble");
            var acc = SubLevelBlockEntityCollector.getBlockEntities(body, AdvancedContraptionControllerBlockEntity.class).getFirst();
            acc.getLinkerSlotHandler().setStackInSlot(0, linker);
            acc.initialize();acc.tick();acc.syncTrackedLinkerRegistry();
            ctx.assertTrue(level.getBlockState(target).is(Blocks.STONE), "Insertion deleted target");
            if(reason == SubLevelRemovalReason.UNLOADED) SubLevelContainer.getContainer(level).removeSubLevel(body, reason);
            else SableSubLevelLifecycleApi.remove(level, List.of(body));
            ContraptionNetworkLinkerTracker.get(level.getServer()).requestReconciliation();
            ContraptionNetworkLinkerTracker.get(level.getServer()).tick(level.getServer());
            ctx.assertTrue(level.getBlockState(target).is(Blocks.STONE), "Source " + reason + " removed world target");
            ctx.assertTrue(level.getBlockState(plane).is(CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.get()), "Source " + reason + " removed world plate");
        }
        ctx.succeed();
    }
}
