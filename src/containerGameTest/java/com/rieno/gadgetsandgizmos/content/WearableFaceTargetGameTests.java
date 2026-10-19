package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.*;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetPortLayout;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetSelection;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class WearableFaceTargetGameTests{
    private WearableFaceTargetGameTests(){}

    @GameTest(template = "empty_container", batch = "wearable_faces")
    public static void pilotHatBindingsRetainTheirItemTypeWhenSaved(GameTestHelper ctx){
        BlockPos pos = ctx.absolutePos(new BlockPos(2, 3, 2));
        ctx.getLevel().setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        var controller = (AdvancedContraptionControllerBlockEntity)ctx.getLevel().getBlockEntity(pos);
        UUID hat = UUID.randomUUID(), goggles = UUID.randomUUID();
        ItemStack cap = new ItemStack(CTItems.PILOT_CAP.get());
        controller.registerGogglesTrackerPair(hat, controller.nextGogglesTrackerPairLabel(cap), cap);
        controller.registerGogglesTrackerPair(goggles, "Deck crew", new ItemStack(CTItems.PHYSICS_GOGGLES.get()));
        ctx.assertTrue(controller.getGogglesTrackerPairs().stream().anyMatch(pair -> hat.equals(pair.id())
                && pair.itemId().equals("createthrusters:pilot_cap") && pair.label().startsWith("Pilot Hat")), "Hat identity was lost");
        var metadata = controller.saveWithFullMetadata(ctx.getLevel().registryAccess());
        var copy = new AdvancedContraptionControllerBlockEntity(pos, controller.getBlockState());
        copy.setLevel(ctx.getLevel());
        copy.loadWithComponents(metadata, ctx.getLevel().registryAccess());
        ctx.assertTrue("createthrusters:pilot_cap".equals(copy.getGogglesTrackerPairItems().get(hat)), "Saved hat became goggles");
        ctx.assertTrue("createthrusters:physics_goggles".equals(copy.getGogglesTrackerPairItems().get(goggles)), "Goggles changed type");
        ctx.assertTrue(copy.unregisterGogglesTrackerPair(hat) && !copy.getGogglesTrackerPairItems().containsKey(hat), "Unbinding retained item metadata");
        ctx.succeed();
    }

    @GameTest(template = "empty_container", batch = "wearable_faces")
    public static void twoFacesKeepIndependentBindingsAndAliasesAcrossReload(GameTestHelper ctx) throws Exception{
        BlockPos host = ctx.absolutePos(new BlockPos(2, 3, 2)), block = host.east(2);
        ctx.getLevel().setBlock(host, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        ctx.getLevel().setBlock(block, Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
        var controller = (AdvancedContraptionControllerBlockEntity)ctx.getLevel().getBlockEntity(host);
        var target = new ControllerDiscoveryNode("scm_picker:world:" + block.asLong(), ControllerDiscoveryKind.MACHINE,
                "scm_picker", "minecraft:redstone_lamp", "Lamp", null, block);
        for(String type : List.of("get_block_data", "set_block_data")){
            var node = new AdvancedGraphDocument.Node("node", type, "", 0, 0, AdvancedGraphNodeFactory.createDefaultData(type, null));
            var north = GraphTargetSelection.withFace(target, Direction.NORTH);
            var south = GraphTargetSelection.withFace(target, Direction.SOUTH);
            ListTag targets = new ListTag();targets.add(north.toTag());targets.add(south.toTag());
            node.data().put(AdvancedGraphCatalog.DATA_TARGETS_TAG, targets);
            CompoundTag faces = new CompoundTag();faces.putString(north.nodeId(), "north");faces.putString(south.nodeId(), "south");
            node.data().put(AdvancedGraphCatalog.DATA_TARGET_FACES_TAG, faces);
            CompoundTag aliases = new CompoundTag();aliases.putString(north.nodeId(), "Intake");aliases.putString(south.nodeId(), "Exhaust");
            node.data().put(GraphTargetSelection.ALIASES_TAG, aliases);
            var schemas = new ArrayList<GraphTargetPortLayout.Target>();
            for(var selected : List.of(north, south)){
                var schema = AdvancedContraptionControllerBlockEntity.graphDataTargetPortLayout(ctx.getLevel(), node, selected, type.equals("set_block_data"));
                ctx.assertTrue(schema != null, "Face schema could not resolve");schemas.add(schema);
            }
            AdvancedContraptionControllerBlockEntity.applyDataTargetPortLayout(node, schemas, type.equals("set_block_data"));
            AdvancedGraphTargetSignals.upgrade(node, new ArrayList<>());
            var graph = new AdvancedGraphDocument();graph.nodes().add(node);
            node = graph.copy().nodes().getFirst();
            ctx.assertTrue(node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, 10).size() == 2, "Reload merged two faces");
            var sections = node.data().getCompound(AdvancedGraphCatalog.PORT_SECTIONS_TAG).getCompound(type.equals("set_block_data") ? "Inputs" : "Outputs");
            var labels = sections.getAllKeys().stream().map(key -> sections.getCompound(key).getString("Label")).toList();
            ctx.assertTrue(labels.containsAll(List.of("Intake", "Exhaust")), "Face aliases did not label the node groups");
            var scoped = AdvancedContraptionControllerBlockEntity.class.getDeclaredMethod("dataTargetNode", AdvancedGraphDocument.Node.class, String.class);
            scoped.setAccessible(true);
            for(var selected : List.of(north, south)){
                var resolved = (AdvancedGraphDocument.Node)scoped.invoke(null, node, selected.nodeId());
                ctx.assertTrue(AdvancedContraptionControllerBlockEntity.selectedDataTargetFace(resolved, selected)
                        == (selected == north ? Direction.NORTH : Direction.SOUTH), "Runtime bound the wrong face");
            }
        }
        ctx.succeed();
    }
}
