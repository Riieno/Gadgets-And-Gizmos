package com.rieno.gadgetsandgizmos.registry;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.seat.MountedSeatRegistry;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.List;

// Register Create-compatible and convention-tagged mounted seats
public final class CTMountedSeats {
    private static final List<TagKey<Block>> SEAT_TAGS = List.of(
            seatTag("c", "seats"),
            seatTag("neoforge", "seats"),
            seatTag("gadgetsngizmos", "seats"),
            seatTag(CreateThrusters.MOD_ID, "seats"));
    private static boolean registered;

    private CTMountedSeats() {
    }

    // Register the addon seat provider once
    public static synchronized void register() {
        if (registered) return;
        MountedSeatRegistry.register(
                ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "mounted_seats"),
                (level, position, state, blockEntity) -> state.getBlock() instanceof SeatBlock
                        || SEAT_TAGS.stream().anyMatch(state::is));
        registered = true;
    }

    // Create one conventional seat block tag
    private static TagKey<Block> seatTag(String namespace, String path) {
        return TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
