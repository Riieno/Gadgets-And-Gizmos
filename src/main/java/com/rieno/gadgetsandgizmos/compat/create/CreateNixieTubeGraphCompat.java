package com.rieno.gadgetsandgizmos.compat.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Locale;
import java.util.Map;

// Let graph outputs write numbers or text across a connected row of Create Nixie Tubes
public final class CreateNixieTubeGraphCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String STRING_PORT = "string";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the create nixie tube graph compat
    private CreateNixieTubeGraphCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is a target
    public static boolean isTarget(Object target) {
        return target instanceof NixieTubeBlockEntity;
    }

    // Check if this is a target
    public static boolean isTarget(Level level, BlockPos pos) {
        return level != null && pos != null && level.isLoaded(pos)
                && level.getBlockState(pos).getBlock() instanceof NixieTubeBlock;
    }

    // Get the writable data
    public static Map<String, String> writableData(Object target) {
        return isTarget(target) ? writableDataSchema() : Map.of();
    }

    // Get the writable data schema
    static Map<String, String> writableDataSchema() {
        return Map.of(STRING_PORT, "string");
    }

    // Write the number
    public static boolean writeNumber(Level level, BlockPos pos, double val) {
        return writeText(level, pos, formatNumber(val));
    }

    // Write the text
    public static boolean writeText(Level level, BlockPos pos, String text) {
        if (!isTarget(level, pos)) {
            return false;
        }
        String serialized = Component.Serializer.toJson(
                Component.literal(text == null ? "" : text), level.registryAccess());
        return NixieTubeBlock.walkNixies(level, pos, false, (currentPos, rowPosition) -> {
            BlockEntity blockEntity = level.getBlockEntity(currentPos);
            if (blockEntity instanceof NixieTubeBlockEntity nixie) {
                nixie.displayCustomText(serialized, rowPosition);
            }
        });
    }

    // Format the number
    public static String formatNumber(double val) {
        long rounded = Double.isFinite(val) ? Math.round(val) : 0L;
        int clamped = (int) Math.max(0L, Math.min(99L, rounded));
        return String.format(Locale.ROOT, "%02d", clamped);
    }
}
