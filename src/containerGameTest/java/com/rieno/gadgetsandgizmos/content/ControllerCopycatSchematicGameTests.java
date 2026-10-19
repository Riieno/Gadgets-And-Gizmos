package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SchematicMaterials;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematic;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematicBuild;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematicFiles;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.joml.Quaterniond;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

// Round-trip partial controller backings through Digisable's sanitizer, material scan and staged builder
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class ControllerCopycatSchematicGameTests{
    private ControllerCopycatSchematicGameTests(){}

    @GameTest(template = "empty_container", timeoutTicks = 120)
    public static void legacyCopycatSlabKeepsItsMaterialsAndGraph(GameTestHelper ctx){
        roundTrip(ctx, copycat().setValue(BlockStateProperties.AXIS, Direction.Axis.Y)
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM), true, Direction.UP);
    }

    @GameTest(template = "empty_container", timeoutTicks = 120)
    public static void verticalCopycatSlabKeepsItsOrientationAndMaterials(GameTestHelper ctx){
        roundTrip(ctx, copycat().setValue(BlockStateProperties.AXIS, Direction.Axis.X)
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), false, Direction.WEST);
    }

    @GameTest(template = "empty_container", timeoutTicks = 120)
    public static void vanillaSlabKeepsItsShapeAndMount(GameTestHelper ctx){
        roundTrip(ctx, Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP),
                false, Direction.DOWN);
    }

    @GameTest(template = "empty_container", timeoutTicks = 120)
    public static void embeddedModuleNeedsNoCopycatPreview(GameTestHelper ctx){
        roundTrip(ctx, CTBlocks.SHIP_CONTROL_MODULE.get().defaultBlockState(), false, Direction.UP);
    }

    private static BlockState copycat(){
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("copycats", "copycat_slab"));
        if(block == null || block == Blocks.AIR) throw new IllegalStateException("Copycats must be loaded for this regression");
        return block.defaultBlockState();
    }

    private static void roundTrip(GameTestHelper ctx, BlockState backing, boolean legacy, Direction face){
        var level = ctx.getLevel();
        var registry = level.registryAccess();
        boolean copycat = backing.is(copycat().getBlock());
        var physical = new CompoundTag();
        CompoundTag encoded = NbtUtils.writeBlockState(backing);
        if(legacy) encoded.putString("Name", "sim_copycats:normal_copycat_slab");
        physical.put(ControllerPhysicalState.EMBEDDED_BLOCK, encoded);
        if(copycat){
            physical.put(ControllerPhysicalState.EMBEDDED_MATERIAL, named("create:copycat_base"));
            physical.put(ControllerPhysicalState.EMBEDDED_BLOCK_ENTITY, copycatData());
        }
        var graph = graph();
        var program = physical.copy();
        program.put("AdvancedDraftGraph", graph.toTag()); program.put("AdvancedActiveGraph", graph.toTag());
        var payload = new CompoundTag(); payload.put(ControllerSchematicPayload.CONTROLLER_DATA_TAG, program);
        var saved = new CompoundTag(); saved.put(ControllerPhysicalState.TAG_KEY, physical.copy());
        ctx.assertTrue(ControllerSchematicPayload.write(saved, payload), "Portable graph could not be encoded");
        var state = CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()
                .setValue(AnalogueContraptionControllerBlock.EMBEDDED_SLAB, true)
                .setValue(AnalogueContraptionControllerBlock.FACING, face)
                .setValue(AnalogueContraptionControllerBlock.MOUNT_OFFSET, 8);
        var schematic = new SubLevelSchematic(List.of(new SubLevelSchematic.Body(UUID.randomUUID(), Vec3.ZERO,
                new Quaterniond(), new BlockPos(1, 1, 1),
                List.of(new SubLevelSchematic.Block(BlockPos.ZERO, state, saved)))), List.of());
        schematic = SubLevelSchematicFiles.sanitize(level, schematic);
        var requirements = SchematicMaterials.requirements(level, schematic);
        ctx.assertTrue(requirements.stream().anyMatch(req -> req.stack().is(backing.getBlock().asItem()) && req.amount() == 1),
                "Digisable did not charge for the backing slab");
        if(copycat) ctx.assertTrue(requirements.stream().anyMatch(req -> req.stack().is(Items.BLACK_WOOL)
                && req.amount() == 1), "Digisable did not charge for the painted copycat material");
        build(ctx, schematic, body -> {
            BlockPos pos = body.getPlot().getCenterBlock();
            try{
                var controller = (AdvancedContraptionControllerBlockEntity)level.getBlockEntity(pos);
                ctx.assertTrue(controller != null, "Placed ACC is missing");
                ctx.assertTrue(backing.equals(controller.getEmbeddedBlockState()), "Backing slab properties were lost");
                ctx.assertTrue(controller.getBlockState().getValue(AnalogueContraptionControllerBlock.MOUNT_OFFSET) == 8
                        && controller.getBlockState().getValue(AnalogueContraptionControllerBlock.FACING) == face,
                        "The flush mount position was lost");
                ctx.assertTrue(controller.getDraftGraph().nodes().size() == 3, "Schematic lost its graph");
                var persisted = controller.saveWithoutMetadata(registry);
                ctx.assertTrue(!persisted.contains(ControllerSchematicPayload.BLOCK_ENTITY_TAG), "Schematic payload was retained after migration");
                ctx.assertTrue(!persisted.contains("AdvancedDraftGraph") && !persisted.contains("AdvancedActiveGraph"),
                        "Graph was written back into block NBT");
                ctx.assertTrue(persisted.contains(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_ID), "Graph has no database identity");
                var preview = controller.getEmbeddedBlockEntityPreview();
                if(copycat){
                    ctx.assertTrue(preview != null, "Copycat preview was not recreated");
                    for(int frame = 0; frame < 100; frame++) ctx.assertTrue(
                            preview == controller.getEmbeddedBlockEntityPreview(), "Preview was recreated per frame");
                    var samePhysical = new CompoundTag(); controller.writeControllerPhysicalState(samePhysical, registry);
                    controller.readControllerBody(samePhysical.copy(), registry);
                    ctx.assertTrue(preview == controller.getEmbeddedBlockEntityPreview(), "An unchanged update rebuilt the preview");
                }
                ctx.assertTrue(controller.restoreEmbeddedBlock(), "The backing could not be restored after removing the ACC");
                ctx.assertTrue(backing.equals(level.getBlockState(pos)), "Restored slab state changed");
                if(copycat){
                    var restored = level.getBlockEntity(pos).saveWithoutMetadata(registry);
                    ctx.assertTrue(restored.getCompound("material_data").equals(copycatData().getCompound("material_data")),
                            "Copycat materials or consumed items changed");
                }
            }finally{
                SableSubLevelLifecycleApi.remove(level, List.of(body));
            }
            ctx.runAfterDelay(4, ctx::succeed);
        });
    }

    // Yield to the next server tick whenever the shared construction allowance is spent
    private static void build(GameTestHelper ctx, SubLevelSchematic schematic, Consumer<ServerSubLevel> placed){
        var level = ctx.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[CopycatTest]"));
        Map<UUID, ServerPlayer> players = players(level.getServer().getPlayerList());
        players.put(player.getUUID(), player);
        Vec3 target = ctx.absolutePos(new BlockPos(1, 24, 1)).getCenter();
        level.getChunk(BlockPos.containing(target));
        var build = SubLevelSchematicBuild.prepare(player, schematic, target, new Quaterniond(), 2);
        ctx.runAfterDelay(1, new Runnable(){
            @Override public void run(){
                try{
                    build.tick(1);
                    if(!build.complete()){ ctx.runAfterDelay(1, this::run); return; }
                    var body = (ServerSubLevel)SubLevelContainer.getContainer(level).getSubLevel(build.bodyIds().getFirst());
                    build.close();
                    players.remove(player.getUUID());
                    ctx.runAfterDelay(12, () -> placed.accept(body));
                }catch(RuntimeException | Error err){
                    build.close();
                    players.remove(player.getUUID());
                    throw err;
                }
            }
        });
    }

    // Give each parallel fixture its own registered fake player without a network login
    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> players(PlayerList list){
        try{
            var field = PlayerList.class.getDeclaredField("playersByUUID");
            field.setAccessible(true);
            return (Map<UUID, ServerPlayer>)field.get(list);
        }catch(ReflectiveOperationException err){ throw new IllegalStateException(err); }
    }

    @GameTest(template = "empty_container", timeoutTicks = 40)
    public static void invalidSnapshotsStayCheapAndMissingBackingsStayRecoverable(GameTestHelper ctx){
        var level = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()
                .setValue(AnalogueContraptionControllerBlock.EMBEDDED_SLAB, true), 3);
        var controller = (AdvancedContraptionControllerBlockEntity)level.getBlockEntity(pos);
        controller.setEmbeddedBlockState(Blocks.AIR.defaultBlockState(), null, copycatData());
        for(int frame = 0; frame < 1000; frame++) ctx.assertTrue(controller.getEmbeddedBlockEntityPreview() == null,
                "Air was used as a copycat block entity");
        var missing = new CompoundTag();
        missing.put(ControllerPhysicalState.EMBEDDED_BLOCK, named("missing_test:non_full_cover"));
        missing.put(ControllerPhysicalState.EMBEDDED_BLOCK_ENTITY, copycatData());
        controller.readControllerBody(missing, level.registryAccess());
        var retained = new CompoundTag(); controller.writeControllerPhysicalState(retained, level.registryAccess());
        ctx.assertTrue(retained.getCompound(ControllerPhysicalState.EMBEDDED_BLOCK).equals(
                missing.getCompound(ControllerPhysicalState.EMBEDDED_BLOCK)), "An unavailable backing became air on save");
        boolean rejected = false;
        try{ controller.schematicMaterialData(retained); }catch(IllegalArgumentException expected){ rejected = true; }
        ctx.assertTrue(rejected, "A missing backing silently passed the Digisable material scan");
        level.removeBlock(pos, false);
        ctx.runAfterDelay(4, ctx::succeed);
    }

    private static CompoundTag named(String name){
        var data = new CompoundTag(); data.putString("Name", name); return data;
    }

    private static CompoundTag copycatData(){
        var data = new CompoundTag(); data.putString("id", "copycats:multistate_copycat");
        var materials = new CompoundTag();
        var top = new CompoundTag(); top.putBoolean("enableCT", true); top.put("material", named("create:copycat_base"));
        top.put("consumedItem", new CompoundTag()); materials.put("top", top);
        var bottom = new CompoundTag(); bottom.putBoolean("enableCT", true); bottom.put("material", named("minecraft:black_wool"));
        var item = new CompoundTag(); item.putString("id", "minecraft:black_wool"); item.putInt("count", 1);
        bottom.put("consumedItem", item); materials.put("bottom", bottom);
        data.put("material_data", materials); return data;
    }

    private static AdvancedGraphDocument graph(){
        var graph = new AdvancedGraphDocument();
        graph.variables().put("TestValue", AdvancedGraphDocument.Value.number(0));
        var number = new CompoundTag(); number.putDouble("Value", 1);
        var variable = new CompoundTag(); variable.putString("Variable", "TestValue");
        graph.nodes().add(new AdvancedGraphDocument.Node("tick", "event_tick", "", 0, 0, new CompoundTag()));
        graph.nodes().add(new AdvancedGraphDocument.Node("value", "constant_number", "", 0, 0, number));
        graph.nodes().add(new AdvancedGraphDocument.Node("set", "variable_set", "", 0, 0, variable));
        graph.edges().add(new AdvancedGraphDocument.Edge("exec", "tick", "exec", "set", "exec"));
        graph.edges().add(new AdvancedGraphDocument.Edge("value", "value", "value", "set", "value"));
        return graph;
    }
}
