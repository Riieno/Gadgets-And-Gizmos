package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ShipDockApproachService;
import com.rieno.gadgetsandgizmos.lib.navigation.SplinePathNetwork;
import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachPath;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Exchange one dock's approach editing draft and its confirmation
public record ShipDockApproachPayload(int action, BlockPos pos, UUID subLevelId, CompoundTag data) implements CustomPacketPayload{
    public static final int CONFIGURE = 0;
    public static final int CONFIRM = 1;
    public static final int SNAPSHOT = 2;
    public static final int HIDE = 3;
    public static final int RESET = 4;
    public static final Type<ShipDockApproachPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "ship_dock_approach"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShipDockApproachPayload> STREAM_CODEC =
            StreamCodec.of(ShipDockApproachPayload::encode, ShipDockApproachPayload::decode);

    // Confirm only the player's edits; connector poses remain authoritative in the server session
    private static void encode(RegistryFriendlyByteBuf buf, ShipDockApproachPayload val){
        buf.writeVarInt(val.action()); buf.writeBlockPos(val.pos());
        buf.writeBoolean(val.subLevelId() != null);
        if(val.subLevelId() != null) buf.writeUUID(val.subLevelId());
        if(val.action() != CONFIRM){ buf.writeNbt(val.data()); return; }
        buf.writeUUID(val.data().getUUID("Token"));
        ListTag connectors = val.data().getList("Connectors", Tag.TAG_COMPOUND);
        if(connectors.size() > 64) throw new IllegalArgumentException("Too many docking connectors");
        buf.writeVarInt(connectors.size());
        int links = 0, points = 0;
        for(int idx = 0; idx < connectors.size(); idx++){
            CompoundTag connector = connectors.getCompound(idx);
            buf.writeDouble(connector.getDouble("Distance"));
            var connections = DockingApproachPath.readConnections(connector.getList("Connections", Tag.TAG_COMPOUND));
            links += connections.size();
            buf.writeVarInt(connections.size());
            for(var connection : connections){
                points += connection.controls().size();
                if(links > 256 || points > 256) throw new IllegalArgumentException("Too many connector spline controls");
                buf.writeUUID(connection.junction());
                buf.writeVarInt(connection.controls().size());
                for(var point : connection.controls()){
                    buf.writeDouble(point.x); buf.writeDouble(point.y); buf.writeDouble(point.z);
                }
            }
        }
        SplinePathNetwork.fromTag(val.data().getCompound("Network")).write(buf);
    }

    private static ShipDockApproachPayload decode(RegistryFriendlyByteBuf buf){
        int action = buf.readVarInt();
        BlockPos pos = buf.readBlockPos();
        UUID subLevel = buf.readBoolean() ? buf.readUUID() : null;
        if(action != CONFIRM) return new ShipDockApproachPayload(action, pos, subLevel, buf.readNbt());
        CompoundTag data = new CompoundTag();
        data.putUUID("Token", buf.readUUID());
        int count = buf.readVarInt();
        if(count < 0 || count > 64) throw new IllegalArgumentException("Invalid docking connector count");
        ListTag connectors = new ListTag();
        int links = 0, points = 0;
        for(int idx = 0; idx < count; idx++){
            CompoundTag val = new CompoundTag(); val.putDouble("Distance", buf.readDouble());
            int size = buf.readVarInt();
            links += size;
            if(size < 0 || size > SplinePathNetwork.MAX_PATHS || links > 256) throw new IllegalArgumentException("Invalid connector spline count");
            var connections = new java.util.ArrayList<DockingApproachPath.Connection>();
            for(int link = 0; link < size; link++){
                UUID junction = buf.readUUID();
                int controlCount = buf.readVarInt();
                points += controlCount;
                if(controlCount < 0 || points > 256) throw new IllegalArgumentException("Invalid connector control count");
                var controls = new java.util.ArrayList<net.minecraft.world.phys.Vec3>();
                for(int point = 0; point < controlCount; point++) controls.add(new net.minecraft.world.phys.Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
                connections.add(new DockingApproachPath.Connection(junction, controls));
            }
            val.put("Connections", DockingApproachPath.connectionsTag(connections));
            connectors.add(val);
        }
        data.put("Connectors", connectors);
        data.put("Network", SplinePathNetwork.read(buf).toTag());
        return new ShipDockApproachPayload(action, pos, subLevel, data);
    }

    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    public static void handle(ShipDockApproachPayload payload, IPayloadContext ctx){
        ctx.enqueueWork(() -> {
            if(ctx.player() instanceof ServerPlayer player){
                ShipDockApproachService.handle(player, payload);
            }else if(payload.action() == SNAPSHOT || payload.action() == HIDE){
                try{
                    Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.ShipDockApproachClient")
                            .getMethod("accept", ShipDockApproachPayload.class).invoke(null, payload);
                }catch(ReflectiveOperationException ex){ throw new IllegalStateException("Cannot apply dock approach draft", ex); }
            }
        });
    }
}
