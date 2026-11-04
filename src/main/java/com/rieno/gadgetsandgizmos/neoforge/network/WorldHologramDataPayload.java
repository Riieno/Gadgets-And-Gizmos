package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramChunk;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramEntity;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramSlice;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramRegion;
import net.minecraft.world.level.ChunkPos;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Stream one masked chunk at a time and separate entity updates from terrain refreshes
public record WorldHologramDataPayload(MenuConfigTarget target, int revision, double surfaceY, HologramSlice slice, HologramRegion region,
                                       @Nullable HologramChunk chunk, @Nullable List<HologramEntity> entities) implements CustomPacketPayload{
    public static final Type<WorldHologramDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "world_hologram_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldHologramDataPayload> STREAM_CODEC = StreamCodec.of(
            WorldHologramDataPayload::encode, WorldHologramDataPayload::decode);
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }

    private static java.util.function.Consumer<WorldHologramDataPayload> clientHandler = payload -> {};

    // Install the sided consumer without linking client rendering into dedicated server classes
    public static void installClientHandler(java.util.function.Consumer<WorldHologramDataPayload> handler){ clientHandler = handler; }
    public static void handle(WorldHologramDataPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx){
        ctx.enqueueWork(() -> clientHandler.accept(payload));
    }

    private static void encode(RegistryFriendlyByteBuf buf, WorldHologramDataPayload val){
        MenuConfigTarget.STREAM_CODEC.encode(buf, val.target);
        buf.writeVarInt(val.revision);
        buf.writeDouble(val.surfaceY);
        buf.writeInt(val.slice.minY()); buf.writeInt(val.slice.maxY()); buf.writeBoolean(val.slice.surface());
        buf.writeChunkPos(val.region.center()); buf.writeVarInt(val.region.width());
        buf.writeBoolean(val.chunk != null);
        if(val.chunk != null) val.chunk.encode(buf);
        buf.writeBoolean(val.entities != null);
        if(val.entities != null){
            buf.writeVarInt(val.entities.size());
            val.entities.forEach(entity -> entity.encode(buf));
        }
    }

    private static WorldHologramDataPayload decode(RegistryFriendlyByteBuf buf){
        var target = MenuConfigTarget.STREAM_CODEC.decode(buf);
        int revision = buf.readVarInt();
        double y = buf.readDouble();
        if(!Double.isFinite(y)) throw new IllegalArgumentException("Invalid surface height");
        var slice = new HologramSlice(buf.readInt(), buf.readInt(), buf.readBoolean());
        var region = new HologramRegion(buf.readChunkPos(), buf.readVarInt());
        HologramChunk chunk = buf.readBoolean() ? HologramChunk.decode(buf) : null;
        List<HologramEntity> entities = null;
        if(buf.readBoolean()){
            int count = buf.readVarInt();
            if(count < 0 || count > 256) throw new IllegalArgumentException("Invalid hologram entity count");
            entities = new ArrayList<>(count);
            for(int idx = 0; idx < count; idx++) entities.add(HologramEntity.decode(buf));
        }
        return new WorldHologramDataPayload(target, revision, y, slice, region, chunk, entities);
    }
}
