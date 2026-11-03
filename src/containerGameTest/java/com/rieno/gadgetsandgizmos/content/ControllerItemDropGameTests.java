package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.lib.item.BlockEntityItemDrops;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

// Exercise controller removal, ordinary loot and item placement on the server
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class ControllerItemDropGameTests{
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void survivalRetainsAnalogueBindingsAndLinker(GameTestHelper helper){
        verifyDropAndPlacement(helper, false, false, false, true);
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void creativeRetainsAnalogueBindingsAndLinker(GameTestHelper helper){
        verifyDropAndPlacement(helper, false, true, false, false);
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void survivalRetainsAdvancedGraphsAndLinker(GameTestHelper helper){
        verifyDropAndPlacement(helper, true, false, false, true);
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void creativeRetainsAdvancedGraphsAndLinker(GameTestHelper helper){
        verifyDropAndPlacement(helper, true, true, false, false);
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void embeddedSurvivalRetainsSetupAndBacking(GameTestHelper helper){
        verifyDropAndPlacement(helper, true, false, true, true);
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void embeddedCreativeRetainsSetupAndBacking(GameTestHelper helper){
        verifyDropAndPlacement(helper, true, true, true, false);
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void incorrectToolRetainsConfiguredController(GameTestHelper helper){
        verifyDropAndPlacement(helper, false, false, false, false);
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void creativeRetainsDraftWithoutBindingsOrLinker(GameTestHelper helper){
        verifyGraphOnlyDrop(helper, false);
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void creativeRetainsAppliedGraphWithoutBindingsOrLinker(GameTestHelper helper){
        verifyGraphOnlyDrop(helper, true);
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void emptyCreativeControllerDoesNotDrop(GameTestHelper helper){
        helper.setBlock(POS, CTBlocks.ANALOGUE_CONTRAPTION_CONTROLLER.get());
        var controller = (AnalogueContraptionControllerBlockEntity) helper.getBlockEntity(POS);
        remove(helper, controller, true, false);
        helper.assertTrue(helper.getEntities(EntityType.ITEM, POS, 1).isEmpty(),
                "An empty creative controller produced an item");
        helper.succeed();
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void assemblyDoesNotProduceControllerDrops(GameTestHelper helper){
        helper.setBlock(POS, CTBlocks.ANALOGUE_CONTRAPTION_CONTROLLER.get());
        var controller = (AnalogueContraptionControllerBlockEntity) helper.getBlockEntity(POS);
        controller.setChannelKeyBinding("pitch_up", 87);
        controller.beginAssemblyTransfer(controller.getBlockPos(), controller.getBlockPos().above());
        helper.assertTrue(controller.prepareControllerDrop().isEmpty(), "Assembly captured a controller item");
        helper.assertTrue(Block.getDrops(controller.getBlockState(), helper.getLevel(),
                controller.getBlockPos(), controller).isEmpty(), "Assembly emitted duplicate loot");
        helper.succeed();
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void portableDataCopiesAndLootCountsRemainIndependent(GameTestHelper helper){
        helper.setBlock(POS, CTBlocks.ANALOGUE_CONTRAPTION_CONTROLLER.get());
        var controller = (AnalogueContraptionControllerBlockEntity) helper.getBlockEntity(POS);
        CompoundTag data = new CompoundTag();
        data.putInt("x", 123);
        data.putString("Program", "original");
        ItemStack retained = BlockEntityItemDrops.create(controller, data);
        data.putString("Program", "changed");
        CompoundTag copied = retained.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        helper.assertTrue(!copied.contains("x") && copied.getString("Program").equals("original"),
                "Portable data retained coordinates or borrowed its source tag");
        ItemStack unrelated = new ItemStack(Items.STONE);
        var drops = BlockEntityItemDrops.replace(List.of(new ItemStack(retained.getItem(), 2), unrelated), retained);
        helper.assertTrue(drops.getFirst().getCount() == 2 && drops.get(1) == unrelated,
                "Replacing loot changed its count or unrelated items");
        drops.getFirst().setCount(7);
        helper.assertTrue(retained.getCount() == 1, "Replacing loot borrowed the retained stack");
        helper.succeed();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // A graph alone must trigger the creative preservation path
    private static void verifyGraphOnlyDrop(GameTestHelper helper, boolean applied){
        helper.setBlock(POS, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get());
        var controller = (AdvancedContraptionControllerBlockEntity) helper.getBlockEntity(POS);
        var graph = controller.getDraftGraph();
        graph.nodes().add(new AdvancedGraphDocument.Node("graph_only", "add", "Saved", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData("add", AdvancedGraphNodeFactory.Context.EMPTY)));
        helper.assertTrue(controller.saveDraft(graph, graph.revision()), "Could not save the standalone graph");
        if(applied) helper.assertTrue(controller.applyDraft(), "Could not apply the standalone graph");
        remove(helper, controller, true, false);
        var drops = helper.getEntities(EntityType.ITEM, POS, 1);
        helper.assertTrue(drops.size() == 1, "A graph without binds or a linker was lost in creative");
        CompoundTag data = PortableContraptionControllerItem.readControllerData(drops.getFirst().getItem(), helper.getLevel());
        String graphKey = applied ? "AdvancedActiveGraph" : "AdvancedDraftGraph";
        helper.assertTrue(!AdvancedGraphDocument.fromTag(data.getCompound(graphKey)).nodes().isEmpty(),
                "The referenced standalone graph was lost");
        helper.succeed();
    }

    // Break the real block, then place its dropped item through the ordinary item path
    private static void verifyDropAndPlacement(GameTestHelper helper, boolean advanced, boolean creative,
                                               boolean embedded, boolean harvest){
        helper.setBlock(POS, advanced ? CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get()
                : CTBlocks.ANALOGUE_CONTRAPTION_CONTROLLER.get());
        var controller = (AnalogueContraptionControllerBlockEntity) helper.getBlockEntity(POS);
        controller.getLinkerSlotHandler().setStackInSlot(0, new ItemStack(CTItems.CONTRAPTION_NETWORK_LINKER.get()));
        controller.setChannelKeyBinding("pitch_up", 87);
        controller.setChannelFrequency("pitch_up", new ItemStack(Items.REDSTONE), new ItemStack(Items.GOLD_INGOT));
        controller.setCustomName("Retained controller");
        if(advanced){
            var acc = (AdvancedContraptionControllerBlockEntity) controller;
            AdvancedGraphDocument graph = new AdvancedGraphDocument();
            graph.nodes().add(new AdvancedGraphDocument.Node("retained_constant", "add", "Saved", 0, 0,
                    AdvancedGraphNodeFactory.createDefaultData("add", AdvancedGraphNodeFactory.Context.EMPTY)));
            helper.assertTrue(acc.saveDraft(graph, acc.getDraftGraph().revision()), "Could not save graph");
            helper.assertTrue(acc.applyDraft(), "Could not apply graph");
            graph = acc.getDraftGraph();
            graph.nodes().add(new AdvancedGraphDocument.Node("draft_only", "add", "Draft", 10, 10,
                    AdvancedGraphNodeFactory.createDefaultData("add", AdvancedGraphNodeFactory.Context.EMPTY)));
            helper.assertTrue(acc.saveDraft(graph, graph.revision()), "Could not save the unapplied graph");
        }
        BlockState backing = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        if(embedded) controller.setEmbeddedSlabState(backing);
        var linkerId = ContraptionNetworkLinkerData.getLinkerId(controller.getStoredLinker());
        remove(helper, controller, creative, harvest);
        if(embedded) helper.assertTrue(helper.getBlockState(POS).equals(backing), "Breaking removed the backing slab");
        var entities = helper.getEntities(EntityType.ITEM, POS, 1);
        helper.assertTrue(entities.size() == 1, "Expected one controller item with no loose linker or slab");
        ItemStack stack = entities.getFirst().getItem().copy();
        entities.getFirst().discard();
        CompoundTag metadata = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        String manifestId = ControllerManifestStore.controllerManifestId(metadata);
        helper.assertTrue(!manifestId.isBlank() && !metadata.contains("AdvancedDraftGraph"),
                "The item did not retain a compact controller reference");
        helper.assertTrue(ControllerManifestStore.loadController(manifestId, helper.getLevel()) != null,
                "Breaking deleted the referenced setup");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos target = helper.absolutePos(POS.east(2));
        helper.getLevel().setBlockAndUpdate(target.below(), Blocks.STONE.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var ctx = new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atBottomCenterOf(target), net.minecraft.core.Direction.UP, target.below(), false));
        helper.assertTrue(((BlockItem) stack.getItem()).place(ctx).consumesAction(), "The dropped controller could not be placed");
        var restored = (AnalogueContraptionControllerBlockEntity) helper.getLevel().getBlockEntity(target);
        helper.assertTrue(restored != null && restored.getKeyBinding("pitch_up") == 87, "Placement lost the key binding");
        helper.assertTrue(restored.getFrequencyFirst("pitch_up").is(Items.REDSTONE), "Placement lost the frequency binding");
        helper.assertTrue("Retained controller".equals(restored.getCustomName()), "Placement lost the name");
        helper.assertTrue(!restored.getStoredLinker().isEmpty()
                && linkerId != null && linkerId.equals(ContraptionNetworkLinkerData.getLinkerId(restored.getStoredLinker())),
                "Placement lost or replaced the linker in its slot");
        helper.assertTrue(restored.getEmbeddedBlockState() == null, "Placement duplicated the old backing slab");
        helper.assertTrue(manifestId.equals(restored.controllerManifestId()), "Placement replaced the setup reference");
        if(advanced){
            var acc = (AdvancedContraptionControllerBlockEntity) restored;
            helper.assertTrue(acc.getDraftGraph().nodes().stream().anyMatch(node -> node.id().equals("draft_only")),
                    "Placement lost the unapplied graph");
            helper.assertTrue(acc.getActiveGraph().nodes().stream().anyMatch(node -> node.id().equals("retained_constant")),
                    "Placement lost the applied graph");
            helper.assertTrue(acc.getActiveGraph().nodes().stream().noneMatch(node -> node.id().equals("draft_only")),
                    "Placement applied the draft graph");
        }
        helper.succeed();
    }

    // Match the server's removal and survival loot sequence
    private static void remove(GameTestHelper helper, AnalogueContraptionControllerBlockEntity controller,
                               boolean creative, boolean harvest){
        Player player = helper.makeMockPlayer(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        BlockState state = controller.getBlockState();
        BlockPos pos = controller.getBlockPos();
        state.getBlock().playerWillDestroy(helper.getLevel(), pos, state, player);
        helper.assertTrue(state.onDestroyedByPlayer(helper.getLevel(), pos, player, harvest,
                helper.getLevel().getFluidState(pos)), "The controller could not be removed");
        state.getBlock().destroy(helper.getLevel(), pos, state);
        if(!creative && harvest)
            state.getBlock().playerDestroy(helper.getLevel(), player, pos, state, controller, ItemStack.EMPTY);
    }
}
