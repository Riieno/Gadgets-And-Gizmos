package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeJournal;
import com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeNbt;
import com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeProjection;
import com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeReplica;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagDelta;
import com.rieno.gadgetsandgizmos.lib.nbt.UuidStringTags;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerRuntimeEventsPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

// Exercise the real registry-aware transport without reducing pulses to timestamps
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class GraphRuntimeTransportGameTests{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void orderedRuntimeCodecKeepsRepeatedPulsesAndMetadataRemovals(GameTestHelper helper){
        var journal = new GraphRuntimeJournal<AdvancedGraphDocument.Value>(10);
        var projection = new GraphRuntimeProjection<AdvancedGraphDocument.Value>();
        var replica = new GraphRuntimeReplica<AdvancedGraphDocument.Value>();
        var pulses = new ArrayList<GraphRuntimeJournal.Event<AdvancedGraphDocument.Value>>();
        UUID actor = UUID.randomUUID();
        UUID scope = UUID.randomUUID();
        UUID pair = UUID.randomUUID();
        CompoundTag metadata = new CompoundTag();
        metadata.put("Pairs", UuidStringTags.write(Map.of(pair, "Tracker")));
        var initial = projection.project(journal.read(null, 0), Map.of(), (channel, key) -> null, true);
        var baseline = roundTrip(helper, new AdvancedControllerRuntimeEventsPayload(MenuConfigTarget.of(BlockPos.ZERO, scope),
                -1, 0, metadata.getCompound("Pairs"), GraphRuntimeNbt.write(initial, AdvancedGraphDocument.Value::toTag), metadata));
        helper.assertTrue(replica.accept(baseline.base(), GraphRuntimeNbt.read(baseline.changes(), AdvancedGraphDocument.Value::fromTag),
                pulses::add), "The initial runtime baseline was rejected");
        journal.pulse("edge", 7, 3, actor);
        journal.pulse("edge", 7, 3, actor);
        journal.value(GraphRuntimeJournal.Channel.OUTPUT, "result:value", AdvancedGraphDocument.Value.number(42), 7, 3, actor);
        journal.value(GraphRuntimeJournal.Channel.STATUS, "dock", AdvancedGraphDocument.Value.string("Inactive until applied"), 7, 3, null);
        long base = projection.sequence();
        var next = projection.project(journal.read(projection.sourceStream(), projection.sourceSequence()), Map.of(),
                (channel, key) -> null, true);
        CompoundTag empty = new CompoundTag();
        var packet = roundTrip(helper, new AdvancedControllerRuntimeEventsPayload(MenuConfigTarget.of(BlockPos.ZERO, scope),
                -1, base, new CompoundTag(), GraphRuntimeNbt.write(next, AdvancedGraphDocument.Value::toTag),
                CompoundTagDelta.between(metadata, empty)));
        helper.assertTrue(scope.equals(packet.target().subLevelId()), "Runtime transport changed its sublevel scope");
        var update = GraphRuntimeNbt.read(packet.changes(), AdvancedGraphDocument.Value::fromTag);
        helper.assertTrue(replica.accept(packet.base(), update, pulses::add), "An ordered runtime delta was rejected");
        helper.assertTrue(!replica.accept(packet.base(), update, pulses::add), "A duplicate runtime update was replayed");
        helper.assertTrue(pulses.size() == 2 && pulses.stream().allMatch(evt -> actor.equals(evt.playerId()) && evt.tick() == 7),
                "Repeated pulses or their interaction ownership were lost");
        helper.assertTrue(replica.values(GraphRuntimeJournal.Channel.OUTPUT).get("result:value").asNumber() == 42,
                "Runtime value changed during transport");
        helper.assertTrue(replica.values(GraphRuntimeJournal.Channel.STATUS).get("dock").asString().equals("Inactive until applied"),
                "Runtime transport lost its action status");
        CompoundTagDelta.apply(metadata, packet.metadata());
        helper.assertTrue(metadata.isEmpty(), "Removed observer metadata survived a delta");
        helper.succeed();
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void scmStateCodecKeepsConfigurationWhenOnlyTelemetryAndPermissionsChange(GameTestHelper helper){
        var source = new com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagPublication();
        var reader = new com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagReplica();
        CompoundTag document = new CompoundTag();
        CompoundTag candidates = new CompoundTag();
        net.minecraft.nbt.ListTag units = new net.minecraft.nbt.ListTag();
        CompoundTag unit = new CompoundTag(); unit.putString("UnitId", "existing_thruster"); units.add(unit);
        candidates.put("Units", units);
        CompoundTag telemetry = new CompoundTag(); telemetry.putDouble("Speed", 1);
        candidates.put("Telemetry", telemetry);
        CompoundTag permissions = new CompoundTag(); permissions.putBoolean("Open", false);
        candidates.put("ShipPermissions", permissions);
        document.put("Candidates", candidates);
        helper.assertTrue(reader.accept(source.publish(document)), "The initial SCM baseline was rejected");
        telemetry.putDouble("Speed", 2);
        permissions.putBoolean("Open", true);
        var change = source.publish(document);
        var packet = new com.rieno.gadgetsandgizmos.neoforge.network.ScmConfigurationDeltaPayload(
                MenuConfigTarget.of(BlockPos.ZERO, UUID.randomUUID()), 42, change.toTag());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try{
            com.rieno.gadgetsandgizmos.neoforge.network.ScmConfigurationDeltaPayload.STREAM_CODEC.encode(buffer, packet);
            var decoded = com.rieno.gadgetsandgizmos.neoforge.network.ScmConfigurationDeltaPayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(buffer.readableBytes() == 0 && decoded.containerId() == 42
                    && decoded.target().equals(packet.target()), "SCM delta transport changed its owning menu");
            var update = com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagPublication.Update.fromTag(decoded.update());
            helper.assertTrue(!update.full() && !update.patch().toString().contains("existing_thruster"),
                    "A telemetry change resent unchanged SCM units");
            helper.assertTrue(reader.accept(update) && reader.document().equals(document),
                    "SCM delta transport lost configuration, telemetry or permissions");
        }finally{ buffer.release(); }
        helper.succeed();
    }

    private static AdvancedControllerRuntimeEventsPayload roundTrip(GameTestHelper helper, AdvancedControllerRuntimeEventsPayload payload){
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try{
            AdvancedControllerRuntimeEventsPayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = AdvancedControllerRuntimeEventsPayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(buffer.readableBytes() == 0, "Runtime transport left unread bytes");
            return decoded;
        }finally{ buffer.release(); }
    }
}
