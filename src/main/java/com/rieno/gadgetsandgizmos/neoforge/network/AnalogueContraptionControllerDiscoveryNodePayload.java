package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuBackedBlockEntityResolver;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Analogue Contraption Controller Discovery Node
public record AnalogueContraptionControllerDiscoveryNodePayload(
        MenuConfigTarget target,
        CompoundTag nodeTag
) implements CustomPacketPayload {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AnalogueContraptionControllerDiscoveryNodePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "analogue_contraption_controller_discovery_node"));

    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, AnalogueContraptionControllerDiscoveryNodePayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(
                    AnalogueContraptionControllerDiscoveryNodePayload::encode,
                    AnalogueContraptionControllerDiscoveryNodePayload::decode);

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

    // Handle the analogue contraption controller discovery node
    public static void handle(AnalogueContraptionControllerDiscoveryNodePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            AnalogueContraptionControllerBlockEntity controller = MenuBackedBlockEntityResolver.resolve(
                    ctx,
                    payload.target(),
                    AnalogueContraptionControllerMenu.class,
                    AnalogueContraptionControllerBlockEntity.class);
            if (controller == null) {
                return;
            }

            ControllerDiscoveryNode node = ControllerDiscoveryNode.fromTag(payload.nodeTag());
            if (node != null && node.isValid()) {
                controller.upsertStoredTarget(node);
            }
        });
    }

    // Encode the analogue contraption controller discovery node
    private static void encode(RegistryFriendlyByteBuf buffer, AnalogueContraptionControllerDiscoveryNodePayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeNbt(payload.nodeTag() == null ? new CompoundTag() : payload.nodeTag());
    }

    // Decode the analogue contraption controller discovery node
    private static AnalogueContraptionControllerDiscoveryNodePayload decode(RegistryFriendlyByteBuf buffer) {
        MenuConfigTarget target = MenuConfigTarget.STREAM_CODEC.decode(buffer);
        CompoundTag nodeTag = buffer.readNbt();
        return new AnalogueContraptionControllerDiscoveryNodePayload(target, nodeTag == null ? new CompoundTag() : nodeTag);
    }
}
