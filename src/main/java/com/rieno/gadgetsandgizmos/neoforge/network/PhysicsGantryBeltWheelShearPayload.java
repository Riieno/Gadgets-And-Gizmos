package com.rieno.gadgetsandgizmos.neoforge.network;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryBeltWheelBlockEntity;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryBeltWheelLink;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

// Select one rendered belt, then validate the shears hit and unlink it on the server.
public record PhysicsGantryBeltWheelShearPayload(BlockPos first, @Nullable UUID firstLevel,
                                                 BlockPos second, @Nullable UUID secondLevel,
                                                 InteractionHand hand) implements CustomPacketPayload {
    public static final Type<PhysicsGantryBeltWheelShearPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "physics_gantry_belt_wheel_shear"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhysicsGantryBeltWheelShearPayload> STREAM_CODEC =
            StreamCodec.of(PhysicsGantryBeltWheelShearPayload::encode, PhysicsGantryBeltWheelShearPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PhysicsGantryBeltWheelShearPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var stack = player.getItemInHand(payload.hand());
            if (!(stack.getItem() instanceof ShearsItem) && !stack.is(Tags.Items.TOOLS_SHEAR)) return;
            PhysicsGantryBeltWheelBlockEntity first = SimulatedHelper.findBlockEntity(player.level(),
                    payload.firstLevel(), payload.first(), PhysicsGantryBeltWheelBlockEntity.class);
            PhysicsGantryBeltWheelBlockEntity second = SimulatedHelper.findBlockEntity(player.level(),
                    payload.secondLevel(), payload.second(), PhysicsGantryBeltWheelBlockEntity.class);
            if (first == null || second == null || first.isRemoved() || second.isRemoved()
                    || !Objects.equals(payload.firstLevel(), SimulatedHelper.getContainingSubLevelId(first))
                    || !Objects.equals(payload.secondLevel(), SimulatedHelper.getContainingSubLevelId(second))
                    || !first.references(payload.second(), payload.secondLevel())
                    || !second.references(payload.first(), payload.firstLevel())) return;

            var hit = PhysicsGantryBeltWheelLink.pickBelt(player.getEyePosition(), player.getViewVector(1.0F),
                    player.blockInteractionRange(), first.getWorldAnchorPosition(), second.getWorldAnchorPosition());
            if (hit == null) return;
            HitResult obstruction = player.pick(player.blockInteractionRange(), 0.0F, false);
            if (obstruction.getType() != HitResult.Type.MISS
                    && player.getEyePosition().distanceTo(obstruction.getLocation()) + 0.2D < hit.rayDistance()) return;
            if (!first.shearLink(payload.second(), payload.secondLevel())) return;
            stack.hurtAndBreak(1, player, payload.hand() == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        });
    }

    private static void encode(RegistryFriendlyByteBuf buffer, PhysicsGantryBeltWheelShearPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.first());
        buffer.writeBoolean(payload.firstLevel() != null);
        if (payload.firstLevel() != null) buffer.writeUUID(payload.firstLevel());
        BlockPos.STREAM_CODEC.encode(buffer, payload.second());
        buffer.writeBoolean(payload.secondLevel() != null);
        if (payload.secondLevel() != null) buffer.writeUUID(payload.secondLevel());
        buffer.writeBoolean(payload.hand() == InteractionHand.OFF_HAND);
    }

    private static PhysicsGantryBeltWheelShearPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos first = BlockPos.STREAM_CODEC.decode(buffer);
        UUID firstLevel = buffer.readBoolean() ? buffer.readUUID() : null;
        BlockPos second = BlockPos.STREAM_CODEC.decode(buffer);
        UUID secondLevel = buffer.readBoolean() ? buffer.readUUID() : null;
        InteractionHand hand = buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return new PhysicsGantryBeltWheelShearPayload(first, firstLevel, second, secondLevel, hand);
    }
}
