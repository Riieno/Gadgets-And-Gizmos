package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueChannelMode;
import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuBackedBlockEntityResolver;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Send Analogue Contraption Controller Config
public record AnalogueContraptionControllerConfigPayload(MenuConfigTarget target, String channelId, String mode,
                                                         float riseRate, float fallRate, float stepAmount,
                                                         float deadzone, float smoothing, String localSide,
                                                         ItemStack first, ItemStack second,

                                                         ItemStack inputFirst, ItemStack inputSecond,
                                                         int keyCode, CompoundTag directTarget,
                                                         CompoundTag inputTarget, String bindingMode,
                                                         String bindingPreset) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AnalogueContraptionControllerConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "analogue_contraption_controller_config"));

    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, AnalogueContraptionControllerConfigPayload> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of(
            AnalogueContraptionControllerConfigPayload::encode,
            AnalogueContraptionControllerConfigPayload::decode);

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

    // Handle the analogue contraption controller config
    public static void handle(AnalogueContraptionControllerConfigPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            AnalogueContraptionControllerBlockEntity controller = MenuBackedBlockEntityResolver.resolve(
                    ctx,
                    payload.target(),
                    AnalogueContraptionControllerMenu.class,
                    AnalogueContraptionControllerBlockEntity.class);
            if (controller == null) {
                return;
            }

            AnalogueChannelMode parsedMode;
            try {
                parsedMode = AnalogueChannelMode.valueOf(payload.mode().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException err) {
                parsedMode = AnalogueChannelMode.RAMP;
            }

            Direction localOutputSide = payload.localSide().isBlank() ? null : Direction.byName(payload.localSide());
            ControllerDirectTargetReference directTarget = ControllerDirectTargetReference.fromTag(payload.directTarget());
            ControllerDirectTargetReference inputTarget = ControllerDirectTargetReference.fromTag(payload.inputTarget());
            controller.applyChannelConfig(payload.channelId(), parsedMode, payload.riseRate(), payload.fallRate(),
                    payload.stepAmount(), payload.deadzone(), payload.smoothing(), localOutputSide,
                    payload.first(), payload.second(), payload.inputFirst(), payload.inputSecond(),
                    payload.keyCode(), directTarget, inputTarget,
                    payload.bindingMode(), payload.bindingPreset());
        });
    }

    // Encode the analogue contraption controller config
    private static void encode(RegistryFriendlyByteBuf buffer, AnalogueContraptionControllerConfigPayload payload) {
        MenuConfigTarget.STREAM_CODEC.encode(buffer, payload.target());
        buffer.writeUtf(payload.channelId());
        buffer.writeUtf(payload.mode());
        buffer.writeFloat(payload.riseRate());
        buffer.writeFloat(payload.fallRate());
        buffer.writeFloat(payload.stepAmount());
        buffer.writeFloat(payload.deadzone());
        buffer.writeFloat(payload.smoothing());
        buffer.writeUtf(payload.localSide());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.first());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.second());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.inputFirst());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.inputSecond());
        buffer.writeInt(payload.keyCode());
        buffer.writeNbt(payload.directTarget());
        buffer.writeNbt(payload.inputTarget());
        buffer.writeUtf(payload.bindingMode());
        buffer.writeUtf(payload.bindingPreset());
    }

    // Decode the analogue contraption controller config
    private static AnalogueContraptionControllerConfigPayload decode(RegistryFriendlyByteBuf buffer) {
        return new AnalogueContraptionControllerConfigPayload(
                MenuConfigTarget.STREAM_CODEC.decode(buffer),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readUtf(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                buffer.readInt(),
                emptyIfNull(buffer.readNbt()),
                emptyIfNull(buffer.readNbt()),
                buffer.readUtf(),
                buffer.readUtf());
    }

    // Get the empty if null
    private static CompoundTag emptyIfNull(CompoundTag tag) {
        return tag == null ? new CompoundTag() : tag;
    }
}
