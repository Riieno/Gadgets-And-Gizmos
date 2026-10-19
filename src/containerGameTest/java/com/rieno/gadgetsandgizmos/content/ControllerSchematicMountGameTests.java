package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematicBuild;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematicCapture;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematicFiles;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Exercise controller mounts through item placement, world capture, file decoding and staged construction
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class ControllerSchematicMountGameTests{
    // Keep the framework entry point static
    private ControllerSchematicMountGameTests(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Preserve both controller versions on slabs, every SCM face and painted copycat panels
    @GameTest(template = "empty_container", timeoutTicks = 200)
    public static void embeddedSupportsSurviveSchematicConstruction(GameTestHelper ctx) throws ReflectiveOperationException{
        var level = ctx.getLevel();
        var container = SubLevelContainer.getContainer(level);
        var player = FakePlayerFactory.getMinecraft(level);
        Map<UUID, ServerPlayer> players = players(level.getServer().getPlayerList());
        ServerPlayer prevPlayer = players.put(player.getUUID(), player);
        ItemStack prevItem = player.getMainHandItem();
        List<ServerSubLevel> retained = new ArrayList<>();
        try{
            var source = (ServerSubLevel)container.allocateNewSubLevel(new Pose3d());
            retained.add(source);
            var plot = source.getPlot();
            plot.newEmptyChunk(plot.getCenterChunk());
            BlockPos center = plot.getCenterBlock();
            BlockPos worldPos = ctx.absolutePos(new BlockPos(1, 3, 1));
            source.logicalPose().position().set(worldPos.getX(), worldPos.getY(), worldPos.getZ());
            source.logicalPose().rotationPoint().set(center.getX(), center.getY(), center.getZ());
            source.updateLastPose();
            player.setPos(worldPos.getCenter());
            List<Mount> mounts = mounts(ctx);
            List<BlockState> controllerStates = new ArrayList<>();
            Block[] controllers = { CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get(), CTBlocks.ANALOGUE_CONTRAPTION_CONTROLLER.get() };
            for(int idx = 0; idx < mounts.size(); idx++){
                Mount mount = mounts.get(idx);
                BlockPos pos = center.offset(idx % 8 * 2, idx / 8 * 2, 0);
                level.setBlock(pos, mount.support(), 2);
                if(level.getBlockEntity(pos) instanceof CopycatBlockEntity copycat){
                    copycat.setMaterial(Blocks.GOLD_BLOCK.defaultBlockState());
                    copycat.setConsumedItem(new ItemStack(Items.GOLD_BLOCK));
                }
                Block controller = controllers[idx / (mounts.size() / 2)];
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(controller));
                Vec3 hit = pos.getCenter().add(Vec3.atLowerCornerOf(mount.face().getNormal())
                        .scale(mount.offset() / 16D - 0.5));
                var res = player.getMainHandItem().getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(hit, mount.face(), pos, false)));
                ctx.assertTrue(res.consumesAction(), "Controller item refused " + mount.support());
                var be = (AnalogueContraptionControllerBlockEntity)level.getBlockEntity(pos);
                checkMount(ctx, be, mount);
                controllerStates.add(be.getBlockState());
            }
            container.physicsSystem().getPipeline().onStatsChanged(source);
            var captured = SubLevelSchematicCapture.capture(level, source.getUniqueId(), 8, 256, body -> {});
            var decoded = SubLevelSchematicFiles.decode(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                    SubLevelSchematicFiles.encode(captured), 8, 256);
            var schematic = SubLevelSchematicFiles.sanitize(level, decoded);
            ctx.assertTrue(schematic.blockCount() == mounts.size(), "Schematic lost controller positions");
            for(int idx = 0; idx < mounts.size(); idx++){
                BlockPos pos = new BlockPos(idx % 8 * 2, idx / 8 * 2, 0);
                var block = schematic.bodies().getFirst().blocks().stream().filter(val -> val.pos().equals(pos)).findFirst().orElseThrow();
                ctx.assertTrue(block.state().equals(controllerStates.get(idx)), "Capture or sanitization changed the mount state");
                ctx.assertTrue(block.data().getCompound(ControllerPhysicalState.TAG_KEY).contains(ControllerPhysicalState.EMBEDDED_BLOCK),
                        "Capture or sanitization discarded the support data");
            }
            Vec3 target = ctx.absolutePos(new BlockPos(1, 3, 24)).getCenter();
            for(int x = 0; x <= 16; x += 4) level.getChunk(BlockPos.containing(target.add(x, 0, 0)));
            try(var build = new SubLevelSchematicBuild(player, schematic, target, new org.joml.Quaterniond(), 4)){
                for(UUID id : build.bodyIds()) retained.add((ServerSubLevel)container.getSubLevel(id));
                while(!build.complete()) build.tick(1);
                var built = retained.getLast();
                for(int idx = 0; idx < mounts.size(); idx++){
                    BlockPos local = new BlockPos(idx % 8 * 2, idx / 8 * 2, 0);
                    BlockPos pos = built.getPlot().getCenterBlock().offset(local);
                    var be = (AnalogueContraptionControllerBlockEntity)SubLevelBlockEntityCollector.getBlockEntity(built, pos);
                    ctx.assertTrue(be != null && be.getBlockState().equals(controllerStates.get(idx)), "Construction changed the controller state");
                    checkMount(ctx, be, mounts.get(idx));
                    ctx.assertTrue(be.restoreEmbeddedBlock(), "Removing the controller could not restore its support");
                    ctx.assertTrue(level.getBlockState(pos).equals(mounts.get(idx).support()), "Restored support has the wrong state");
                    if(level.getBlockEntity(pos) instanceof CopycatBlockEntity copycat){
                        ctx.assertTrue(copycat.getMaterial().is(Blocks.GOLD_BLOCK) && copycat.getConsumedItem().is(Items.GOLD_BLOCK),
                                "Construction lost copycat material or its consumed item");
                    }
                }
            }
            ctx.assertTrue(!source.isRemoved(), "Schematic operations consumed the source ship");
            for(int idx = 0; idx < mounts.size(); idx++) checkMount(ctx,
                    (AnalogueContraptionControllerBlockEntity)SubLevelBlockEntityCollector.getBlockEntity(source,
                            center.offset(idx % 8 * 2, idx / 8 * 2, 0)), mounts.get(idx));
            ctx.succeed();
        }finally{
            player.setItemInHand(InteractionHand.MAIN_HAND, prevItem);
            if(prevPlayer == null) players.remove(player.getUUID());
            else players.put(player.getUUID(), prevPlayer);
            SableSubLevelLifecycleApi.remove(level, retained.stream().filter(body -> !body.isRemoved()).toList());
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Cover mirrored slabs, every module orientation and variable-depth copycat surfaces
    private static List<Mount> mounts(GameTestHelper ctx){
        List<Mount> res = new ArrayList<>();
        res.add(new Mount(Blocks.STONE_SLAB.defaultBlockState(), Direction.UP, 8));
        res.add(new Mount(Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), Direction.DOWN, 8));
        for(Direction face : Direction.values()) res.add(new Mount(CTBlocks.SHIP_CONTROL_MODULE.get().defaultBlockState()
                .setValue(ShipControlModuleBlock.MOUNT_FACE, face.getOpposite()), face, 8));
        for(Direction facing : List.of(Direction.UP, Direction.EAST)){
            BlockState panel = AllBlocks.COPYCAT_PANEL.getDefaultState().setValue(BlockStateProperties.FACING, facing);
            var shape = panel.getShape(ctx.getLevel(), ctx.absolutePos(BlockPos.ZERO));
            Direction face = shape.max(facing.getAxis()) < 1 ? facing : facing.getOpposite();
            double depth = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? shape.max(face.getAxis()) : 1 - shape.min(face.getAxis());
            res.add(new Mount(panel, face, (int)Math.round(depth * 16)));
        }
        res.addAll(List.copyOf(res));
        return List.copyOf(res);
    }

    // Verify the backing state, surface offset and copycat data at each stage
    private static void checkMount(GameTestHelper ctx, AnalogueContraptionControllerBlockEntity be, Mount mount){
        ctx.assertTrue(be != null && mount.support().equals(be.getEmbeddedBlockState()), "Embedded support was discarded");
        ctx.assertTrue(be.getBlockState().getValue(AnalogueContraptionControllerBlock.EMBEDDED_SLAB)
                && be.getBlockState().getValue(AnalogueContraptionControllerBlock.MOUNT_OFFSET) == mount.offset()
                && be.getBlockState().getValue(AnalogueContraptionControllerBlock.FACING) == mount.face(), "Embedded surface offset changed");
        if(mount.support().is(AllBlocks.COPYCAT_PANEL.get())){
            ctx.assertTrue(be.getEmbeddedBlockEntityPreview() instanceof CopycatBlockEntity, "Copycat backing entity was discarded");
            var copycat = (CopycatBlockEntity)be.getEmbeddedBlockEntityPreview();
            ctx.assertTrue(copycat.getMaterial().is(Blocks.GOLD_BLOCK) && copycat.getConsumedItem().is(Items.GOLD_BLOCK), "Copycat backing data changed");
        }
    }

    // Temporarily register the fixture player for construction without running a network login
    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> players(PlayerList list) throws ReflectiveOperationException{
        var field = PlayerList.class.getDeclaredField("playersByUUID");
        field.setAccessible(true);
        return (Map<UUID, ServerPlayer>)field.get(list);
    }

    // Keep the expected support and clicked surface together
    private record Mount(BlockState support, Direction face, int offset){}
}
