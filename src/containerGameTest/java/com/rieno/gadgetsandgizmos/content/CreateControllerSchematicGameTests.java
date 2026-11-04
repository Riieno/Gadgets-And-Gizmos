package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.lib.nbt.ChunkedTagPayload;
import com.rieno.gadgetsandgizmos.lib.schematic.SchematicSnapshotPackets;
import com.rieno.gadgetsandgizmos.lib.schematic.SchematicSnapshotScope;
import com.rieno.gadgetsandgizmos.lib.schematic.SchematicSnapshotTransfer;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.SchematicExport;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.foundation.utility.BlockHelper;
import com.simibubi.create.foundation.utility.CreatePaths;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

// Use Create's exporter, printer, safe NBT conversion and cannon placement path
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class CreateControllerSchematicGameTests{
    private static final BlockPos SOURCE = new BlockPos(1, 2, 1);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void cannonImportsPortableGraphsAndLinker(GameTestHelper helper) throws Exception{
        verifyCannon(helper, Rotation.NONE, Mirror.NONE);
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void rotatedCannonImportsTargetPositionsAndFaces(GameTestHelper helper) throws Exception{
        verifyCannon(helper, Rotation.CLOCKWISE_90, Mirror.NONE);
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void mirroredCannonImportsTargetPositionsAndFaces(GameTestHelper helper) throws Exception{
        verifyCannon(helper, Rotation.CLOCKWISE_180, Mirror.FRONT_BACK);
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void unreadableChunksRemainRecoverable(GameTestHelper helper){
        helper.setBlock(SOURCE, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get());
        var controller = (AdvancedContraptionControllerBlockEntity) helper.getBlockEntity(SOURCE);
        CompoundTag envelope = new CompoundTag();
        envelope.putInt("Version", 99);
        CompoundTag data = new CompoundTag();
        data.put(ControllerSchematicPayload.BLOCK_ENTITY_TAG, envelope.copy());
        controller.loadWithComponents(data, helper.getLevel().registryAccess());
        controller.initialize();
        CompoundTag saved = controller.saveWithId(helper.getLevel().registryAccess());
        helper.assertTrue(saved.getCompound(ControllerSchematicPayload.BLOCK_ENTITY_TAG).equals(envelope),
                "An unreadable import lost its original chunks");
        helper.assertTrue(!ControllerManifestStore.hasControllerMetadata(saved),
                "An unreadable import was replaced by an empty database graph");
        helper.assertTrue(data.contains(ControllerSchematicPayload.BLOCK_ENTITY_TAG), "Reading consumed the caller's template");
        helper.succeed();
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void chunkCodecRejectsMissingAndOversizedData(GameTestHelper helper){
        CompoundTag payload = new CompoundTag();
        byte[] bytes = new byte[100000];
        new Random(19).nextBytes(bytes);
        payload.putByteArray("Data", bytes);
        CompoundTag envelope = ChunkedTagPayload.encode(payload);
        helper.assertTrue(envelope.getList("Chunks", Tag.TAG_COMPOUND).size() > 1, "The test did not cross a chunk boundary");
        helper.assertTrue(ChunkedTagPayload.decode(envelope).equals(payload), "Chunked NBT did not round-trip");
        envelope.getList("Chunks", Tag.TAG_COMPOUND).removeLast();
        boolean rejected = false;
        try{ ChunkedTagPayload.decode(envelope); }catch(IllegalArgumentException err){ rejected = true; }
        helper.assertTrue(rejected, "An incomplete chunk list was accepted");
        envelope.putInt("CompressedBytes", ChunkedTagPayload.MAX_COMPRESSED_BYTES + 1);
        rejected = false;
        try{ ChunkedTagPayload.decode(envelope); }catch(IllegalArgumentException err){ rejected = true; }
        helper.assertTrue(rejected, "An oversized payload was accepted");
        helper.succeed();
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void snapshotPacketsPreserveBoundedSegments(GameTestHelper helper){
        UUID id = UUID.randomUUID();
        var request = new SchematicSnapshotPackets.Request(id, List.of(helper.absolutePos(SOURCE)));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try{
            SchematicSnapshotPackets.Request.STREAM_CODEC.encode(buffer, request);
            helper.assertTrue(SchematicSnapshotPackets.Request.STREAM_CODEC.decode(buffer).equals(request), "Snapshot request codec changed the selection");
            buffer.clear();
            byte[] bytes = new byte[ChunkedTagPayload.CHUNK_BYTES];
            new Random(31).nextBytes(bytes);
            var segment = new SchematicSnapshotPackets.Segment(id, SOURCE, 2, bytes.length * 3, bytes, true);
            SchematicSnapshotPackets.Segment.STREAM_CODEC.encode(buffer, segment);
            var decoded = SchematicSnapshotPackets.Segment.STREAM_CODEC.decode(buffer);
            helper.assertTrue(decoded.id().equals(id) && decoded.idx() == 2 && decoded.size() == bytes.length * 3
                    && java.util.Arrays.equals(decoded.bytes(), bytes) && buffer.readableBytes() == 0,
                    "Snapshot segment codec changed its bounded contents");
        }finally{ buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void snapshotTransferRequiresEveryChunkAndTerminator(GameTestHelper helper){
        UUID id = UUID.randomUUID();
        CompoundTag data = new CompoundTag();
        byte[] bytes = new byte[100000];
        new Random(73).nextBytes(bytes);
        data.putByteArray("Graph", bytes);
        CompoundTag envelope = ChunkedTagPayload.encode(data);
        var transfer = new SchematicSnapshotTransfer(id, List.of(SOURCE));
        var chunks = envelope.getList("Chunks", Tag.TAG_COMPOUND);
        for(int idx = 0; idx < chunks.size(); idx++){
            transfer.accept(new SchematicSnapshotPackets.Segment(id, SOURCE, idx,
                    envelope.getInt("CompressedBytes"), chunks.getCompound(idx).getByteArray("Data"), true));
            helper.assertTrue(!transfer.isComplete(), "A transfer completed without its terminator");
        }
        transfer.accept(new SchematicSnapshotPackets.Segment(id, BlockPos.ZERO, -1, 0, new byte[0], true));
        helper.assertTrue(transfer.isComplete() && transfer.snapshots().get(SOURCE).equals(data),
                "A complete transfer lost its authoritative snapshot");
        boolean rejected = false;
        try{ transfer.accept(new SchematicSnapshotPackets.Segment(id, SOURCE, 0, 1, new byte[]{1}, true)); }
        catch(IllegalArgumentException err){ rejected = true; }
        helper.assertTrue(rejected, "A completed transfer accepted trailing data");
        var incomplete = new SchematicSnapshotTransfer(id, List.of(SOURCE));
        rejected = false;
        try{ incomplete.accept(new SchematicSnapshotPackets.Segment(id, BlockPos.ZERO, -1, 0, new byte[0], true)); }
        catch(IllegalArgumentException err){ rejected = true; }
        helper.assertTrue(rejected && !incomplete.isComplete(), "An incomplete transfer permitted the export to continue");
        helper.succeed();
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void snapshotCaptureRejectsUnloadedSelections(GameTestHelper helper){
        var level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "schematic-bounds"), ClientInformation.createDefault());
        player.setPos(Vec3.atCenterOf(helper.absolutePos(SOURCE)));
        BlockPos distant = player.blockPosition().offset(1000000, 0, 1000000);
        boolean loaded = level.hasChunkAt(distant);
        boolean rejected = false;
        try{ SchematicSnapshotPackets.capture(player, List.of(distant)); }
        catch(IllegalArgumentException err){ rejected = true; }
        helper.assertTrue(rejected && level.hasChunkAt(distant) == loaded,
                "Snapshot capture accepted or loaded an unavailable block");
        helper.succeed();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static void verifyCannon(GameTestHelper helper, Rotation rotation, Mirror mirror) throws Exception{
        helper.setBlock(SOURCE, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get());
        helper.setBlock(SOURCE.east(), Blocks.STONE);
        var source = (AdvancedContraptionControllerBlockEntity) helper.getBlockEntity(SOURCE);
        ItemStack linker = new ItemStack(CTItems.CONTRAPTION_NETWORK_LINKER.get());
        var target = new ContraptionNetworkLinkerData.LinkedTarget(source.getBlockPos().east(), null,
                "minecraft:stone", "North output", ContraptionNetworkLinkerData.LinkMode.OUTPUT,
                ContraptionNetworkLinkerData.TargetScope.FACE,
                List.of(new ContraptionNetworkLinkerData.LinkedFace(Direction.NORTH, "Front", "redstone")));
        ContraptionNetworkLinkerData.writeTargets(linker, List.of(target), ContraptionNetworkLinkerData.LinkMode.OUTPUT);
        source.getLinkerSlotHandler().setStackInSlot(0, linker);
        UUID originalLinker = ContraptionNetworkLinkerData.getLinkerId(source.getStoredLinker());
        String originalNodeId = ContraptionNetworkLinkerData.nodeIdForTarget(target);
        var graph = source.getDraftGraph();
        CompoundTag nodeData = AdvancedGraphNodeFactory.createDefaultData("add", AdvancedGraphNodeFactory.Context.EMPTY);
        byte[] noise = new byte[100000];
        new Random(27).nextBytes(noise);
        nodeData.putByteArray("SchematicNoise", noise);
        CompoundTag ref = new CompoundTag();
        ref.putString("NodeId", originalNodeId);
        ref.putLong("BlockPos", target.blockPos().asLong());
        ref.putString("Face", "north");
        ListTag targets = new ListTag();
        targets.add(ref);
        nodeData.put("DataTargets", targets);
        graph.nodes().add(new AdvancedGraphDocument.Node("applied", "add", "Applied", 0, 0, nodeData));
        helper.assertTrue(source.saveDraft(graph, graph.revision()) && source.applyDraft(), "Could not prepare the applied graph");
        graph = source.getDraftGraph();
        graph.nodes().add(new AdvancedGraphDocument.Node("draft_only", "add", "Draft", 10, 10,
                AdvancedGraphNodeFactory.createDefaultData("add", AdvancedGraphNodeFactory.Context.EMPTY)));
        helper.assertTrue(source.saveDraft(graph, graph.revision()), "Could not prepare the unapplied graph");
        CompoundTag sourceSave = source.saveWithId(helper.getLevel().registryAccess());
        String sourceId = ControllerManifestStore.controllerManifestId(sourceSave);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "schematic-test"), ClientInformation.createDefault());
        player.setPos(Vec3.atCenterOf(source.getBlockPos()));
        Map<BlockPos, CompoundTag> envelopes = SchematicSnapshotPackets.capture(player, List.of(source.getBlockPos()));
        CompoundTag snapshot = ChunkedTagPayload.decode(envelopes.get(source.getBlockPos()));
        helper.assertTrue(snapshot.getCompound(ControllerSchematicPayload.BLOCK_ENTITY_TAG)
                .getList("Chunks", Tag.TAG_COMPOUND).size() > 1, "The controller did not use multiple safe NBT chunks");
        helper.assertTrue(!ControllerManifestStore.hasControllerMetadata(snapshot), "The export retained the source database identity");
        String owner = "controller-regression";
        SchematicExport.SchematicExportResult exported;
        try(var scope = SchematicSnapshotScope.enter(Map.of(source.getBlockPos(), snapshot))){
            exported = SchematicExport.saveSchematic(CreatePaths.UPLOADED_SCHEMATICS_DIR.resolve(owner),
                    UUID.randomUUID().toString(), true, helper.getLevel(), source.getBlockPos(), source.getBlockPos().east());
        }
        helper.assertTrue(exported != null, "Create could not export the schematic");
        CompoundTag file = NbtIo.readCompressed(exported.file(), NbtAccounter.create(128L * 1024 * 1024));
        helper.assertTrue(file.getList("blocks", Tag.TAG_COMPOUND).stream().anyMatch(raw ->
                ((CompoundTag) raw).getCompound("nbt").contains(ControllerSchematicPayload.BLOCK_ENTITY_TAG)),
                "Create exported the client's reference instead of the portable server snapshot");
        helper.assertTrue(ControllerManifestStore.deleteController(sourceId, helper.getLevel()), "Could not isolate the portable schematic");
        source.cancelDestructiveRemoval();
        ItemStack schematic = SchematicItem.create(helper.getLevel(), exported.fileName(), owner);
        schematic.set(AllDataComponents.SCHEMATIC_DEPLOYED, true);
        schematic.set(AllDataComponents.SCHEMATIC_ANCHOR, helper.absolutePos(new BlockPos(4, 2, 3)));
        schematic.set(AllDataComponents.SCHEMATIC_ROTATION, rotation);
        schematic.set(AllDataComponents.SCHEMATIC_MIRROR, mirror);
        SchematicPrinter printer = new SchematicPrinter();
        printer.loadSchematic(schematic, helper.getLevel(), true);
        helper.assertTrue(printer.isLoaded() && !printer.isErrored(), "Create could not load the exported schematic");
        var placed = new java.util.ArrayList<AdvancedContraptionControllerBlockEntity>();
        int steps = 0;
        while(printer.advanceCurrentPos() && steps++ < 50){
            printer.handleCurrentTarget((pos, state, blockEntity) -> {
                CompoundTag data = BlockHelper.prepareBlockEntityData(helper.getLevel(), state, blockEntity);
                if(!(blockEntity instanceof AdvancedContraptionControllerBlockEntity)) return;
                helper.assertTrue(data != null && data.contains(ControllerSchematicPayload.BLOCK_ENTITY_TAG),
                        "Create's safe NBT conversion discarded the controller graph");
                BlockHelper.placeSchematicBlock(helper.getLevel(), state, pos, new ItemStack(state.getBlock()), data);
                var restored = (AdvancedContraptionControllerBlockEntity) helper.getLevel().getBlockEntity(pos);
                restored.initialize();
                placed.add(restored);
            }, (pos, entity) -> {});
        }
        helper.assertTrue(placed.size() == 1, "The printer did not place exactly one controller");
        var restored = placed.getFirst();
        CompoundTag saved = restored.saveWithId(helper.getLevel().registryAccess());
        String restoredId = ControllerManifestStore.controllerManifestId(saved);
        helper.assertTrue(!restoredId.isBlank() && !sourceId.equals(restoredId), "Placement reused or omitted the source database identity");
        helper.assertTrue(!saved.contains(ControllerSchematicPayload.BLOCK_ENTITY_TAG), "Successful placement retained the temporary chunks");
        helper.assertTrue(ControllerManifestStore.loadController(restoredId, helper.getLevel()) != null, "Placement did not persist the imported graph");
        helper.assertTrue(restored.getDraftGraph().nodes().stream().anyMatch(node -> node.id().equals("draft_only")), "The cannon lost the draft graph");
        helper.assertTrue(restored.getActiveGraph().nodes().stream().anyMatch(node -> node.id().equals("applied"))
                && restored.getActiveGraph().nodes().stream().noneMatch(node -> node.id().equals("draft_only")), "The cannon changed the applied graph");
        var copiedTargets = ContraptionNetworkLinkerData.readTargets(restored.getStoredLinker());
        helper.assertTrue(copiedTargets.size() == 1 && !originalLinker.equals(ContraptionNetworkLinkerData.getLinkerId(restored.getStoredLinker())),
                "The cannon lost linker targets or reused the source linker identity");
        BlockPos expected = restored.getBlockPos().relative(rotation.rotate(mirror.mirror(Direction.EAST)));
        Direction expectedFace = rotation.rotate(mirror.mirror(Direction.NORTH));
        helper.assertTrue(copiedTargets.getFirst().blockPos().equals(expected)
                && copiedTargets.getFirst().faces().getFirst().face() == expectedFace, "The linker did not follow the schematic transform");
        var applied = restored.getActiveGraph().nodes().stream().filter(node -> node.id().equals("applied")).findFirst().orElseThrow();
        CompoundTag copiedRef = applied.data().getList("DataTargets", Tag.TAG_COMPOUND).getCompound(0);
        helper.assertTrue(BlockPos.of(copiedRef.getLong("BlockPos")).equals(expected)
                && copiedRef.getString("NodeId").equals(ContraptionNetworkLinkerData.nodeIdForTarget(copiedTargets.getFirst())),
                "The graph's target lookup no longer matches its linker");
        helper.assertTrue(java.util.Arrays.equals(applied.data().getByteArray("SchematicNoise"), noise), "Large node data was truncated");
        helper.succeed();
    }

}
