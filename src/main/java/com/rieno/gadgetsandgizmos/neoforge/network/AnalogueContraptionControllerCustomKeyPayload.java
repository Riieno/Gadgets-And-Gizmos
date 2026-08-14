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

// Send Analogue Contraption Controller Custom Key
public record AnalogueContraptionControllerCustomKeyPayload(
        MenuConfigTarget target,
        String op,
        String entryId,
        int keyCode,
        int stepDownKeyCode,
        String label,
        String mode,
        float riseRate,
        float fallRate,
        float stepAmount,
        float stepDownAmount,
        float deadzone,
        float smoothing,
        String localSide,
        ItemStack first,
        ItemStack second,
        ItemStack inputFirst,
        ItemStack inputSecond,
        CompoundTag directTarget,
        CompoundTag inputTarget,
        String bindingPreset
) implements CustomPacketPayload {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<AnalogueContraptionControllerCustomKeyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "analogue_controller_custom_key"));

    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, AnalogueContraptionControllerCustomKeyPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(
                    AnalogueContraptionControllerCustomKeyPayload::encode,
                    AnalogueContraptionControllerCustomKeyPayload::decode);

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

    // Handle the analogue contraption controller custom key
    public static void handle(AnalogueContraptionControllerCustomKeyPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            AnalogueContraptionControllerBlockEntity controller = MenuBackedBlockEntityResolver.resolve(
                    ctx,
                    payload.target(),
                    AnalogueContraptionControllerMenu.class,
                    AnalogueContraptionControllerBlockEntity.class);
            if (controller == null) return;

            switch (payload.op()) {
                case "add" -> controller.addCustomKeyEntryWithId(payload.entryId());
                case "remove" -> controller.removeCustomKeyEntry(payload.entryId());
                case "update" -> {
                    AnalogueChannelMode parsedMode;
                    try {
                        parsedMode = AnalogueChannelMode.valueOf(payload.mode().toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        parsedMode = AnalogueChannelMode.RAMP;
                    }
                    Direction side = payload.localSide().isBlank() ? null : Direction.byName(payload.localSide());
                    ControllerDirectTargetReference directTarget = ControllerDirectTargetReference.fromTag(payload.directTarget());
                        ControllerDirectTargetReference inputTarget = ControllerDirectTargetReference.fromTag(payload.inputTarget());
                    controller.applyCustomKeyEntry(
                            payload.entryId(), payload.keyCode(), payload.stepDownKeyCode(), payload.label(),
                            parsedMode, payload.riseRate(), payload.fallRate(),
                            payload.stepAmount(), payload.stepDownAmount(), payload.deadzone(), payload.smoothing(),
                            side, payload.first(), payload.second(), payload.inputFirst(), payload.inputSecond(),
                            directTarget, inputTarget, payload.bindingPreset());
                }
            }
        });
    }

    // Encode the analogue contraption controller custom key
    private static void encode(RegistryFriendlyByteBuf buf, AnalogueContraptionControllerCustomKeyPayload p) {
        MenuConfigTarget.STREAM_CODEC.encode(buf, p.target());
        buf.writeUtf(p.op());
        buf.writeUtf(p.entryId());
        buf.writeInt(p.keyCode());
        buf.writeInt(p.stepDownKeyCode());
        buf.writeUtf(p.label());
        buf.writeUtf(p.mode());
        buf.writeFloat(p.riseRate());
        buf.writeFloat(p.fallRate());
        buf.writeFloat(p.stepAmount());
        buf.writeFloat(p.stepDownAmount());
        buf.writeFloat(p.deadzone());
        buf.writeFloat(p.smoothing());
        buf.writeUtf(p.localSide());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.first());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.second());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.inputFirst());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.inputSecond());
        buf.writeNbt(p.directTarget());
        buf.writeNbt(p.inputTarget());
        buf.writeUtf(p.bindingPreset());
    }

    // Decode the analogue contraption controller custom key
    private static AnalogueContraptionControllerCustomKeyPayload decode(RegistryFriendlyByteBuf buf) {
        MenuConfigTarget target = MenuConfigTarget.STREAM_CODEC.decode(buf);
        String op = buf.readUtf();
        String entryId = buf.readUtf();
        int keyCode = buf.readInt();
        int stepDownKeyCode = buf.readInt();
        String label = buf.readUtf();
        String mode = buf.readUtf();
        float riseRate = buf.readFloat();
        float fallRate = buf.readFloat();
        float stepAmount = buf.readFloat();
        float stepDownAmount = buf.readFloat();
        float deadzone = buf.readFloat();
        float smoothing = buf.readFloat();
        String localSide = buf.readUtf();
        ItemStack first = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        ItemStack second = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        ItemStack inputFirst = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        ItemStack inputSecond = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        CompoundTag directTarget = buf.readNbt();
        CompoundTag inputTarget = buf.readNbt();
        String bindingPreset = buf.readUtf();
        return new AnalogueContraptionControllerCustomKeyPayload(
            target, op, entryId, keyCode, stepDownKeyCode, label, mode,
            riseRate, fallRate, stepAmount, stepDownAmount, deadzone, smoothing,
            localSide, first, second, inputFirst, inputSecond,
            directTarget != null ? directTarget : new CompoundTag(),
            inputTarget != null ? inputTarget : new CompoundTag(),
            bindingPreset == null || bindingPreset.isBlank() ? "none" : bindingPreset);
    }
}
