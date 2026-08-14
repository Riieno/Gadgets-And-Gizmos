package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

// Sync short-lived thruster overrides without saving them as block state
public final class TransientThrusterControlSync {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String SHIP_CHANNEL_TAG = "ShipControlClientChannel";
    private static final String SHIP_MINIMUM_TAG = "ShipControlClientMinimum";
    private static final String SHIP_MAXIMUM_TAG = "ShipControlClientMaximum";
    private static final String SHIP_THROTTLE_TAG = "ShipControlClientThrottle";
    private static final String DIRECT_ACTIVE_TAG = "CreateThrustersDirectThrottleActive";
    private static final String DIRECT_THROTTLE_TAG = "CreateThrustersDirectThrottle";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the transient thruster control sync
    private TransientThrusterControlSync() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Write the ship envelope
    public static void writeShipEnvelope(
            CompoundTag tag,
            boolean clientPacket,
            @Nullable ShipEnvelope envelope
    ) {
        if (!clientPacket || envelope == null) {
            return;
        }
        tag.putString(SHIP_CHANNEL_TAG, envelope.channelId());
        tag.putFloat(SHIP_MINIMUM_TAG, envelope.minimum());
        tag.putFloat(SHIP_MAXIMUM_TAG, envelope.maximum());
        tag.putFloat(SHIP_THROTTLE_TAG, envelope.throttle());
    }

    // Read the ship envelope
    public static @Nullable ShipEnvelope readShipEnvelope(
            CompoundTag tag,
            boolean clientPacket
    ) {
        if (!clientPacket || !tag.contains(SHIP_THROTTLE_TAG)) {
            return null;
        }
        return new ShipEnvelope(
                tag.getString(SHIP_CHANNEL_TAG),
                tag.getFloat(SHIP_MINIMUM_TAG),
                tag.getFloat(SHIP_MAXIMUM_TAG),
                tag.getFloat(SHIP_THROTTLE_TAG));
    }

    // Write the direct throttle
    public static void writeDirectThrottle(
            CompoundTag tag,
            boolean clientPacket,
            boolean active,
            float throttle
    ) {
        if (!clientPacket || !active) {
            return;
        }
        tag.putBoolean(DIRECT_ACTIVE_TAG, true);
        tag.putFloat(DIRECT_THROTTLE_TAG, Mth.clamp(throttle, 0.0F, 1.0F));
    }

    // Read the direct throttle
    public static DirectThrottle readDirectThrottle(
            CompoundTag tag,
            boolean clientPacket
    ) {
        if (!clientPacket || !tag.getBoolean(DIRECT_ACTIVE_TAG)) {
            return new DirectThrottle(false, 0.0F);
        }
        return new DirectThrottle(true, tag.getFloat(DIRECT_THROTTLE_TAG));
    }

    // Store the ship envelope
    public record ShipEnvelope(
            String channelId,
            float minimum,
            float maximum,
            float throttle
    ) {
        // Initialize the ship envelope
        public ShipEnvelope {
            channelId = channelId == null ? "" : channelId;
            minimum = Mth.clamp(minimum, 0.0F, 1.0F);
            maximum = Mth.clamp(Math.max(minimum, maximum), 0.0F, 1.0F);
            throttle = Mth.clamp(throttle, 0.0F, 1.0F);
        }
    }

    // Store the direct throttle
    public record DirectThrottle(boolean active, float throttle) {
        // Initialize the direct throttle
        public DirectThrottle {
            throttle = active ? Mth.clamp(throttle, 0.0F, 1.0F) : 0.0F;
        }
    }
}
