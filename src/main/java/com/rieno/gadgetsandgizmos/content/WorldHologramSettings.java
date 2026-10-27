package com.rieno.gadgetsandgizmos.content;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

// Store the projector's synchronized controls with bounded finite slider values
public record WorldHologramSettings(int radius, float height, float size, boolean players, boolean hostile, boolean passive, int yOffset, boolean ships){
    public static final WorldHologramSettings DEFAULT = new WorldHologramSettings(3, 2, 4, true, false, false, 0, true);

    // Retain the original constructor for integrations using the initial controls
    public WorldHologramSettings(int radius, float height, float size, boolean players, boolean hostile, boolean passive){
        this(radius, height, size, players, hostile, passive, 0, true);
    }

    public WorldHologramSettings{
        radius = Math.clamp(radius, 1, 9);
        yOffset = Math.clamp(yOffset, -512, 512);
        height = Float.isFinite(height) ? Math.clamp(height, 0, 32) : 2;
        size = Float.isFinite(size) ? Math.clamp(size, 0.5F, 16) : 4;
    }

    // Retain defaults when loading an older or incomplete block tag
    public static WorldHologramSettings read(CompoundTag tag){
        if(!tag.contains("Radius")) return DEFAULT;
        return new WorldHologramSettings(tag.getInt("Radius"), tag.getFloat("Height"), tag.getFloat("Size"),
                tag.getBoolean("ShowPlayers"), tag.getBoolean("ShowHostileMobs"), tag.getBoolean("ShowPassiveMobs"), tag.getInt("SamplingYOffset"), !tag.contains("ShowShips") || tag.getBoolean("ShowShips"));
    }

    public void write(CompoundTag tag){
        tag.putInt("Radius", radius);
        tag.putInt("SamplingYOffset", yOffset);
        tag.putBoolean("ShowShips", ships);
        tag.putFloat("Height", height);
        tag.putFloat("Size", size);
        tag.putBoolean("ShowPlayers", players);
        tag.putBoolean("ShowHostileMobs", hostile);
        tag.putBoolean("ShowPassiveMobs", passive);
    }

    public void encode(RegistryFriendlyByteBuf buf){
        buf.writeVarInt(radius);
        buf.writeVarInt(yOffset);
        buf.writeBoolean(ships);
        buf.writeFloat(height);
        buf.writeFloat(size);
        buf.writeBoolean(players);
        buf.writeBoolean(hostile);
        buf.writeBoolean(passive);
    }

    public static WorldHologramSettings decode(RegistryFriendlyByteBuf buf){
        int radius = buf.readVarInt(), offset = buf.readVarInt();
        boolean ships = buf.readBoolean();
        return new WorldHologramSettings(radius, buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), offset, ships);
    }
}
