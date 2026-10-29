package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphPortState;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematic;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematicFiles;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapterRegistry;
import com.rieno.gadgetsandgizmos.lib.schematic.SchematicPlacementScope;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.utility.BlockHelper;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

import java.util.List;
import java.util.UUID;

// Check reference-only schematic copies independently of ordinary item restoration
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class ControllerSchematicCopyGameTests{
    // Retain source graphs and aliases when Create copies an older reference-only schematic
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void createPlacementForksReferenceOnlyController(GameTestHelper helper){
        var source = source(helper);
        CompoundTag saved = source.saveWithFullMetadata(helper.getLevel().registryAccess());
        var pos = helper.absolutePos(new BlockPos(4, 2, 1));
        BlockHelper.placeSchematicBlock(helper.getLevel(), source.getBlockState(), pos, ItemStack.EMPTY, saved.copy());
        var copy = (AdvancedContraptionControllerBlockEntity) helper.getLevel().getBlockEntity(pos);
        assertIndependent(helper, source, copy);
        helper.assertTrue(!SchematicPlacementScope.isActive(), "Create leaked its placement context");
        helper.succeed();
    }

    // Cover vanilla structures which load the same saved NBT without Create's safe writer
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void vanillaStructureForksReferenceOnlyController(GameTestHelper helper){
        var source = source(helper);
        var template = new StructureTemplate();
        template.fillFromWorld(helper.getLevel(), source.getBlockPos(), new BlockPos(1, 1, 1), false, Blocks.AIR);
        var pos = helper.absolutePos(new BlockPos(4, 2, 1));
        helper.assertTrue(template.placeInWorld(helper.getLevel(), pos, pos,
                new StructurePlaceSettings().setIgnoreEntities(true), helper.getLevel().random, 3), "Structure placement failed");
        assertIndependent(helper, source, (AdvancedContraptionControllerBlockEntity) helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(!SchematicPlacementScope.isActive(), "Structure placement leaked its copy context");
        helper.succeed();
    }

    // Expand database references before Digisable loads its detached material and placement model
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void digisableSanitizationDetachesTheSourceManifest(GameTestHelper helper){
        var source = source(helper);
        CompoundTag saved = source.saveWithFullMetadata(helper.getLevel().registryAccess());
        String id = source.controllerManifestId();
        var template = new SubLevelSchematic(List.of(new SubLevelSchematic.Body(UUID.randomUUID(), Vec3.ZERO,
                new Quaterniond(), new BlockPos(1, 1, 1), List.of(new SubLevelSchematic.Block(BlockPos.ZERO, source.getBlockState(), saved)))));
        var safe = SubLevelSchematicFiles.sanitize(helper.getLevel(), template).bodies().getFirst().blocks().getFirst().data();
        helper.assertTrue(safe.contains(ControllerSchematicPayload.BLOCK_ENTITY_TAG)
                && !ControllerManifestStore.hasControllerMetadata(safe), "Digisable retained a live database identity");
        var pos = helper.absolutePos(new BlockPos(4, 2, 1));
        BlockHelper.placeSchematicBlock(helper.getLevel(), source.getBlockState(), pos, ItemStack.EMPTY, safe);
        assertIndependent(helper, source, (AdvancedContraptionControllerBlockEntity) helper.getLevel().getBlockEntity(pos));
        helper.assertTrue(source.controllerManifestId().equals(id)
                && ControllerManifestStore.controllerManifestId(saved).equals(id), "Sanitizing changed the source or caller's tag");
        helper.succeed();
    }

    // Detach old manifest references when native Sable restoration supplies placement context
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void nativePlacementForksReferenceOnlyController(GameTestHelper helper){
        var source = source(helper);
        var saved = source.saveWithFullMetadata(helper.getLevel().registryAccess());
        var pos = helper.absolutePos(new BlockPos(4, 2, 1));
        helper.getLevel().setBlock(pos, source.getBlockState(), 3);
        var copy = (AdvancedContraptionControllerBlockEntity) helper.getLevel().getBlockEntity(pos);
        var prev = SubLevelSchematicSerializationContext.getCurrentContext();
        var ctx = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.PLACE, null);
        ctx.setPlaceTransform(val -> (BlockPos) val);
        ctx.setSetupTransform(val -> (BlockPos) val);
        try{
            SubLevelSchematicSerializationContext.setCurrentContext(ctx);
            copy.loadWithComponents(saved, helper.getLevel().registryAccess());
        }finally{ SubLevelSchematicSerializationContext.setCurrentContext(prev); }
        assertIndependent(helper, source, copy);
        helper.succeed();
    }

    // Expose the cuckoo clock's solar time through the Get Data adapter registry
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void cuckooClockDataFollowsItsWorldClock(GameTestHelper helper){
        helper.setBlock(new BlockPos(1, 2, 1), AllBlocks.CUCKOO_CLOCK.get());
        var clock = helper.getBlockEntity(new BlockPos(1, 2, 1));
        long prev = helper.getLevel().getDayTime();
        try{
            helper.getLevel().setDayTime(6000);
            helper.assertTrue("string".equals(BlockEntityDataAdapterRegistry.readableData(clock).get("time"))
                    && "12:00:00".equals(BlockEntityDataAdapterRegistry.read(clock, "time").asString()), "Get Data did not expose noon");
            helper.getLevel().setDayTime(18000);
            helper.assertTrue("00:00:00".equals(BlockEntityDataAdapterRegistry.read(clock, "time").asString()), "Clock data did not update");
            helper.assertTrue(!BlockEntityDataAdapterRegistry.writableData(clock).containsKey("time"), "Clock time became writable");
        }finally{ helper.getLevel().setDayTime(prev); }
        helper.succeed();
    }

    // Populate zone settings from the supplied server and execute the node through the ACC host
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void timeNodeUsesServerDefaultsAndWorldTime(GameTestHelper helper){
        helper.setBlock(new BlockPos(1, 2, 1), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get());
        var controller = (AdvancedContraptionControllerBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 1));
        var data = AdvancedGraphNodeFactory.createDefaultData("createthrusters:time",
                new AdvancedGraphNodeFactory.Context("", "variable", "variable", "", "Pacific/Auckland"));
        helper.assertTrue("Pacific/Auckland".equals(data.getCompound("Defaults").getCompound("timezone")
                .getCompound("Payload").getString("Value")), "The editor used its own timezone");
        var graph = new AdvancedGraphDocument();
        var node = new AdvancedGraphDocument.Node("clock", "createthrusters:time", "Time", 0, 0, data);
        graph.nodes().add(node);
        long prev = helper.getLevel().getDayTime();
        try{
            helper.getLevel().setDayTime(6000);
            helper.assertTrue("12:00:00".equals(new GraphRuntime(controller, true).previewOutput(graph, node, "time").asString()),
                    "The Time node did not read its host world's day clock");
        }finally{ helper.getLevel().setDayTime(prev); }
        helper.succeed();
    }

    // Retire saved offsets in the main graph and functions without changing other wires or zones
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void timeNodeRetiresOffsetSettingsAndWires(GameTestHelper helper){
        var graph = new AdvancedGraphDocument();
        var data = AdvancedGraphNodeFactory.createDefaultData("createthrusters:time",
                new AdvancedGraphNodeFactory.Context("", "variable", "variable", "", "Europe/London"));
        data.getCompound("Defaults").put("mode", AdvancedGraphDocument.Value.string("Real").toTag());
        data.getCompound("Defaults").put("offset", AdvancedGraphDocument.Value.number(1).toTag());
        data.getList("InputOrder", 8).add(StringTag.valueOf("offset"));
        data.getCompound("PortSections").getCompound("Inputs").getCompound("time").getList("Ports", 8)
                .add(StringTag.valueOf("offset"));
        var dynamic = new CompoundTag(); dynamic.putString("offset", "number"); data.put("DynamicInputs", dynamic);
        var options = new ListTag(); options.add(StringTag.valueOf("1")); data.getCompound("InputOptions").put("offset", options);
        var persisted = new CompoundTag(); persisted.putBoolean("input:offset", true);
        data.put(AdvancedGraphPortState.PERSISTENT_PORTS_TAG, persisted);
        var values = new CompoundTag(); values.put("input:offset", AdvancedGraphDocument.Value.number(1).toTag());
        data.put(AdvancedGraphPortState.PERSISTENT_VALUES_TAG, values);
        graph.nodes().add(new AdvancedGraphDocument.Node("time", "createthrusters:time", "Time", 0, 0, data));
        graph.edges().add(new AdvancedGraphDocument.Edge("offset", "source", "value", "time", "offset"));
        graph.edges().add(new AdvancedGraphDocument.Edge("zone", "source", "value", "time", "timezone"));
        var function = new AdvancedGraphDocument.FunctionGraph("function", "Time");
        function.nodes().addAll(graph.nodes()); function.edges().addAll(graph.edges()); graph.functions().add(function);
        var migrated = AdvancedGraphDocument.fromTag(graph.toTag());
        for(var nodes : List.of(migrated.nodes(), migrated.functions().getFirst().nodes())){
            var node = nodes.getFirst();
            helper.assertTrue(!node.data().getCompound("Defaults").contains("offset")
                    && !node.data().getCompound("DynamicInputs").contains("offset")
                    && !node.data().getCompound("InputOptions").contains("offset")
                    && !node.data().getList("InputOrder", 8).contains(StringTag.valueOf("offset"))
                    && !node.data().getCompound("PortSections").getCompound("Inputs").getCompound("time")
                    .getList("Ports", 8).contains(StringTag.valueOf("offset"))
                    && !node.data().getCompound(AdvancedGraphPortState.PERSISTENT_PORTS_TAG).contains("input:offset")
                    && !node.data().getCompound(AdvancedGraphPortState.PERSISTENT_VALUES_TAG).contains("input:offset"),
                    "The saved offset metadata survived migration");
            helper.assertTrue(AdvancedGraphCatalog.inputs(node).keySet().equals(java.util.Set.of("mode", "timezone"))
                    && "Europe/London".equals(node.data().getCompound("Defaults").getCompound("timezone")
                    .getCompound("Payload").getString("Value")), "Time settings changed during offset retirement");
        }
        for(var edges : List.of(migrated.edges(), migrated.functions().getFirst().edges()))
            helper.assertTrue(edges.size() == 1 && "zone".equals(edges.getFirst().id()), "Migration removed a current wire or kept an offset wire");
        helper.assertTrue(data.getCompound("Defaults").contains("offset"), "Reading the graph changed the caller's data");
        helper.succeed();
    }

    // Retain a named source graph and a linker whose identity must not be reused by copies
    private static AdvancedContraptionControllerBlockEntity source(GameTestHelper helper){
        helper.setBlock(new BlockPos(1, 2, 1), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get());
        var source = (AdvancedContraptionControllerBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 1));
        source.getLinkerSlotHandler().setStackInSlot(0, new ItemStack(CTItems.CONTRAPTION_NETWORK_LINKER.get()));
        var graph = new AdvancedGraphDocument();
        var data = new CompoundTag(); data.putDouble("Value", 123);
        graph.nodes().add(new AdvancedGraphDocument.Node("source", "constant_number", "Source", 0, 0, data));
        helper.assertTrue(source.saveDraft(graph, source.getDraftGraph().revision()) && source.applyDraft(), "Could not prepare source graph");
        helper.assertTrue(source.renameGraphAlias("copy-test-" + UUID.randomUUID()), "Could not prepare the source alias");
        return source;
    }

    // Verify source and copy remain independent after an edit and a durable save
    private static void assertIndependent(GameTestHelper helper, AdvancedContraptionControllerBlockEntity source,
            AdvancedContraptionControllerBlockEntity copy){
        helper.assertTrue(copy != null, "Copied controller is missing");
        copy.initialize();
        helper.assertTrue(!copy.controllerManifestId().isBlank() && !source.controllerManifestId().equals(copy.controllerManifestId()),
                "The copied controller retained its source manifest");
        helper.assertTrue(copy.getActiveGraph().nodes().stream().anyMatch(node -> "source".equals(node.id())), "Copy lost the applied graph");
        var sourceLinker = ContraptionNetworkLinkerData.getLinkerId(source.getStoredLinker());
        var copyLinker = ContraptionNetworkLinkerData.getLinkerId(copy.getStoredLinker());
        helper.assertTrue(sourceLinker != null && copyLinker != null && !sourceLinker.equals(copyLinker), "Copy retained its source linker identity");
        helper.assertTrue(ControllerSqliteStore.controllerAliasGraph(helper.getLevel(), copy.controllerManifestId()) == null,
                "Copy stayed subscribed to the source's shared graph alias");
        var graph = copy.getDraftGraph();
        graph.nodes().add(new AdvancedGraphDocument.Node("copy_only", "constant_number", "Copy", 10, 10, new CompoundTag()));
        helper.assertTrue(copy.saveDraft(graph, graph.revision()), "Could not save the copied graph");
        var original = ControllerManifestStore.loadController(source.controllerManifestId(), helper.getLevel());
        helper.assertTrue(AdvancedGraphDocument.fromTag(original.draftGraph()).nodes().stream().noneMatch(node -> "copy_only".equals(node.id())),
                "Editing the copy changed the source graph");
    }
}
