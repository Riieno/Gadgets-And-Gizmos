package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableExtensionAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

// Carry one navigation table slot action to its root or Sable level target
public record NavigationTableActionPayload(BlockPos pos, UUID subLevelId, Action action, int slot) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<NavigationTableActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "navigation_table_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NavigationTableActionPayload> STREAM_CODEC = StreamCodec.of(
            NavigationTableActionPayload::encode,
            NavigationTableActionPayload::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the navigation table action
    public static void handle(NavigationTableActionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var blockEntity = SimulatedHelper.findBlockEntity(
                    ctx.player().level(), payload.subLevelId(), payload.pos());
            if (!(blockEntity instanceof NavigationTableExtensionAccess table)) {
                return;
            }

            switch (payload.action()) {
                case SELECT_SLOT -> table.ct$setSelectedSlot(payload.slot());
                case START -> table.ct$startNavigation();
                case PAUSE -> table.ct$pauseNavigation();
                case STOP -> table.ct$stopNavigation();
            }
        });
    }

    // Encode the navigation table action
    private static void encode(RegistryFriendlyByteBuf buffer, NavigationTableActionPayload payload) {
        buffer.writeBlockPos(payload.pos());
        buffer.writeBoolean(payload.subLevelId() != null);
        if (payload.subLevelId() != null) {
            buffer.writeUUID(payload.subLevelId());
        }
        buffer.writeEnum(payload.action());
        buffer.writeInt(payload.slot());
    }

    // Decode the navigation table action
    private static NavigationTableActionPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        UUID subLevelId = buffer.readBoolean() ? buffer.readUUID() : null;
        Action action = buffer.readEnum(Action.class);
        int slot = buffer.readInt();
        return new NavigationTableActionPayload(pos, subLevelId, action, slot);
    }

    // Define the action values
    public enum Action {
        SELECT_SLOT,
        START,
        PAUSE,
        STOP
    }
}
