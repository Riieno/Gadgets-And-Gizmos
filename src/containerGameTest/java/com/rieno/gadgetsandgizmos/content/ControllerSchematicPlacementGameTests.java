package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.foundation.utility.BlockHelper;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Random;

// Exercise real controller loading and Create placement with a multi-chunk compressed graph
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class ControllerSchematicPlacementGameTests{
    private ControllerSchematicPlacementGameTests(){}

    @GameTest(template = "empty_container", batch = "controller_schematics", timeoutTicks = 200)
    public static void largeGraphRetainsItsModuleThroughDetachedConversionAndPlacement(GameTestHelper ctx){
        var level = ctx.getLevel();
        var registry = level.registryAccess();
        var graph = new AdvancedGraphDocument();
        var random = new Random(42);
        int nodeCount = Math.min(1024, AdvancedGraphDocument.maxNodes());
        for(int idx = 0; idx < nodeCount; idx++){
            byte[] bytes = new byte[64]; random.nextBytes(bytes);
            var data = new CompoundTag(); data.putDouble("Value", idx); data.putByteArray("Fixture", bytes);
            graph.nodes().add(new AdvancedGraphDocument.Node("fixture_" + idx, "constant_number", "Number", idx, 0, data));
        }
        var module = CTBlocks.SHIP_CONTROL_MODULE.get().defaultBlockState();
        var program = new CompoundTag();
        program.put(ControllerPhysicalState.EMBEDDED_BLOCK, NbtUtils.writeBlockState(module));
        program.put("AdvancedDraftGraph", graph.toTag());
        program.put("AdvancedActiveGraph", graph.toTag());
        var payload = new CompoundTag(); payload.put(ControllerSchematicPayload.CONTROLLER_DATA_TAG, program);
        var saved = new CompoundTag(); saved.put(ControllerPhysicalState.TAG_KEY, new CompoundTag());
        ctx.assertTrue(ControllerSchematicPayload.write(saved, payload), "Graph payload was rejected");
        ctx.assertTrue(saved.getCompound(ControllerSchematicPayload.BLOCK_ENTITY_TAG).getInt("CompressedBytes") > 32768,
                "Fixture did not exercise multiple payload chunks");

        var state = CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()
                .setValue(AnalogueContraptionControllerBlock.EMBEDDED_SLAB, true);
        var detached = new AdvancedContraptionControllerBlockEntity(BlockPos.ZERO, state);
        detached.markVirtual(); detached.setLevel(level); detached.loadWithComponents(saved.copy(), registry);
        ctx.assertTrue(module.equals(detached.getEmbeddedBlockState()), "Detached conversion removed the SCM");
        ctx.assertTrue(detached.getDraftGraph().nodes().isEmpty(), "Detached conversion hydrated the entire graph");
        var safe = new CompoundTag(); detached.writeSafe(safe, registry);
        var copy = ControllerSchematicPayload.take(safe.copy());
        ctx.assertTrue(copy != null && copy.getCompound(ControllerSchematicPayload.CONTROLLER_DATA_TAG)
                .getCompound("AdvancedDraftGraph").equals(graph.toTag()), "Safe conversion lost graph data");

        var previous = SubLevelSchematicSerializationContext.getCurrentContext();
        try{
            SubLevelSchematicSerializationContext.setCurrentContext(
                    new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.PLACE, null));
            BlockPos pos = ctx.absolutePos(new BlockPos(1, 2, 1));
            BlockHelper.placeSchematicBlock(level, state, pos, ItemStack.EMPTY, safe);
            var placed = level.getBlockEntity(pos);
            ctx.assertTrue(placed instanceof AdvancedContraptionControllerBlockEntity, "ACC was not placed");
            var controller = (AdvancedContraptionControllerBlockEntity)placed;
            ctx.assertTrue(module.equals(controller.getEmbeddedBlockState()), "Placed graph lost its SCM");
            ctx.assertTrue(controller.getDraftGraph().nodes().size() == nodeCount, "Placed graph lost nodes");
        }finally{ SubLevelSchematicSerializationContext.setCurrentContext(previous); }
        ctx.succeed();
    }
}
