package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.rieno.gadgetsandgizmos.lib.network.BlockEntitySnapshotChannel;
import com.rieno.gadgetsandgizmos.lib.network.CompressedTagCodec;
import com.rieno.gadgetsandgizmos.lib.network.PayloadTransferBuffer;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

// Verify bulk graph and preview data against real registered content
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class LargePayloadGameTests{
    // Leave room for authenticated request metadata inside a serverbound custom packet
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void schematicUploadsFitServerboundPacketLimit(GameTestHelper helper){
        var request = new com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletActionPayload(false,
                net.minecraft.world.InteractionHand.MAIN_HAND, BlockPos.ZERO, null, UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("createthrusters", "digisable"), "schematics", "schematic_preview", UUID.randomUUID().toString());
        var payload = new com.rieno.gadgetsandgizmos.neoforge.network.DigisableSchematicUploadPayload(request,
                64 * 1024 * 1024, 0, new byte[com.rieno.gadgetsandgizmos.lib.physics.archive.SchematicClientFiles.TRANSFER_CHUNK_BYTES]);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try{
            com.rieno.gadgetsandgizmos.neoforge.network.DigisableSchematicUploadPayload.STREAM_CODEC.encode(buf, payload);
            helper.assertTrue(buf.readableBytes() < 32767, "An upload slice exceeds the serverbound custom packet limit");
        }finally{ buf.release(); }
        helper.succeed();
    }

    // Keep large display frames out of both initial chunk tags and block update packets
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void largeDisplayFramesUseDeferredSnapshots(GameTestHelper helper){
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, CTBlocks.ACC_DISPLAY.get().defaultBlockState());
        var display = (AccDisplayBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        CompoundTag frame = new CompoundTag();
        frame.putString("State", "graph");
        frame.putInt("GraphRevision", 42);
        CompoundTag graph = new CompoundTag();
        graph.putByteArray("LargeGraph", new byte[3 * 1024 * 1024]);
        frame.put("Graph", graph);
        CompoundTag data = new CompoundTag();
        data.put("AccDisplayFrame", frame);
        display.readClient(data, helper.getLevel().registryAccess());
        for(var tag : new CompoundTag[]{display.getUpdateTag(helper.getLevel().registryAccess()), display.getUpdatePacket().getTag()}){
            helper.assertTrue(BlockEntitySnapshotChannel.isDeferred(tag), "A large display frame remained inline");
            helper.assertTrue(tag.sizeInBytes() < BlockEntitySnapshotChannel.INLINE_BYTES, "A chunk still contains bulk display NBT");
        }
        CompoundTag snapshot = display.clientSnapshot(helper.getLevel().registryAccess());
        helper.assertTrue(snapshot.getCompound("AccDisplayFrame").equals(frame), "Deferring a display discarded its full baseline");
        helper.succeed();
    }

    // Round-trip a large Digisable preview through its production codec
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void tabletPreviewsExceedVanillaNbtLimitSafely(GameTestHelper helper){
        byte[] bytes = new byte[3 * 1024 * 1024];
        new Random(42).nextBytes(bytes);
        CompoundTag data = new CompoundTag(); data.putByteArray("Preview", bytes);
        var payload = new DiagnosticTabletAppSnapshotPayload(ResourceLocation.fromNamespaceAndPath("createthrusters", "digisable"), data);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try{
            DiagnosticTabletAppSnapshotPayload.STREAM_CODEC.encode(buf, payload);
            var decoded = DiagnosticTabletAppSnapshotPayload.STREAM_CODEC.decode(buf);
            helper.assertTrue(decoded.data().equals(data) && !buf.isReadable(), "The large preview codec lost or left unread bytes");
        }finally{ buf.release(); }
        helper.succeed();
    }

    // Preserve the supplied real graph document through compression and chunk assembly
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void suppliedGraphRoundTripsAcrossBoundedChunks(GameTestHelper helper) throws Exception{
        String path = System.getProperty("acc.presentation.graph", "");
        if(path.isBlank()){ helper.succeed(); return; }
        var root = JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();
        CompoundTag graph = (CompoundTag) JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE,
                root.getAsJsonArray("storedGraphs").get(0).getAsJsonObject().get("Graph"));
        var runtime = new com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime(null, true);
        runtime.compile(com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.fromTag(graph));
        CompoundTag layout = runtime.hudPresentationLayout();
        helper.assertTrue(!layout.contains("Nodes") && !layout.contains("Edges") && !layout.contains("Functions"),
                "A HUD layout contains executable graph data");
        helper.assertTrue(layout.getList("Elements", 10).size() == 4, "The supplied HUD definitions were lost");
        byte[] bytes = CompressedTagCodec.encode(graph);
        helper.assertTrue(bytes.length > PayloadTransferBuffer.CHUNK_BYTES, "The fixture did not exercise multiple packets");
        var buffer = new PayloadTransferBuffer();
        UUID id = UUID.randomUUID();
        byte[] decoded = null;
        for(int offset = 0; offset < bytes.length; offset += PayloadTransferBuffer.CHUNK_BYTES){
            decoded = buffer.receive(id, bytes.length, offset,
                    Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + PayloadTransferBuffer.CHUNK_BYTES)), offset);
        }
        helper.assertTrue(CompressedTagCodec.decode(decoded).equals(graph), "The supplied graph changed during transport");
        helper.succeed();
    }
}
