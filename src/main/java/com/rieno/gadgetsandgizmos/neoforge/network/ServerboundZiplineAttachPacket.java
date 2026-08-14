package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlock;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Handle Zipline Attach
public record ServerboundZiplineAttachPacket(TargetType targetType, Vec3 targetPosition, BlockPos chainPos,
                                             @Nullable BlockPos chainConnection, @Nullable UUID ropeUuid,
                                             float pathPosition) implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ServerboundZiplineAttachPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "zipline_attach"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundZiplineAttachPacket> STREAM_CODEC =
            StreamCodec.of(ServerboundZiplineAttachPacket::encode, ServerboundZiplineAttachPacket::decode);

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

    // Handle the serverbound zipline attach
    public static void handle(ServerboundZiplineAttachPacket payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            if (CTBlocks.POWERED_ZIPLINE == null || CTItems.POWERED_ZIPLINE == null) {
                return;
            }

            ItemStack ziplineStack = findZiplineStack(player);
            if (ziplineStack.isEmpty()) {
                return;
            }

            double maxDistance = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 3.0D;
            if (payload.targetPosition().distanceToSqr(player.getEyePosition()) > maxDistance * maxDistance) {
                return;
            }

            BlockPos placementPos = findPlacementPos(level, payload.targetPosition());
            if (placementPos == null) {
                return;
            }

            BlockState state = CTBlocks.POWERED_ZIPLINE.get().defaultBlockState()
                    .setValue(PoweredZiplineBlock.FACING, Direction.DOWN);
            if (!level.setBlock(placementPos, state, 3)) {
                return;
            }

            if (!(level.getBlockEntity(placementPos) instanceof PoweredZiplineBlockEntity zipline)) {
                level.removeBlock(placementPos, false);
                return;
            }

            boolean attached = payload.targetType() == TargetType.ROPE
                    && payload.ropeUuid() != null
                    && zipline.attachToRope(payload.ropeUuid(), payload.pathPosition());
            if (!attached) {
                level.removeBlock(placementPos, false);
                return;
            }

            zipline.ensureAssembled();
            if (!player.getAbilities().instabuild) {
                ziplineStack.shrink(1);
            }
        });
    }

    // Find the zipline stack
    private static ItemStack findZiplineStack(ServerPlayer player) {
        if (CTItems.POWERED_ZIPLINE == null) {
            return ItemStack.EMPTY;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(CTItems.POWERED_ZIPLINE.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // Find the placement pos
    private static @Nullable BlockPos findPlacementPos(ServerLevel level, Vec3 target) {
        BlockPos origin = BlockPos.containing(target);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(1, 1, 1))) {
            BlockPos immutable = candidate.immutable();
            if (!level.getWorldBorder().isWithinBounds(immutable)) {
                continue;
            }
            BlockState candidateState = level.getBlockState(immutable);
            if (!candidateState.canBeReplaced()) {
                continue;
            }
            double distance = immutable.getCenter().distanceToSqr(target);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = immutable;
            }
        }
        return best;
    }

    // Encode the serverbound zipline attach
    private static void encode(RegistryFriendlyByteBuf buffer, ServerboundZiplineAttachPacket payload) {
        buffer.writeByte(payload.targetType().ordinal());
        buffer.writeDouble(payload.targetPosition().x);
        buffer.writeDouble(payload.targetPosition().y);
        buffer.writeDouble(payload.targetPosition().z);
        BlockPos.STREAM_CODEC.encode(buffer, payload.chainPos());
        buffer.writeBoolean(payload.chainConnection() != null);
        if (payload.chainConnection() != null) {
            BlockPos.STREAM_CODEC.encode(buffer, payload.chainConnection());
        }
        buffer.writeBoolean(payload.ropeUuid() != null);
        if (payload.ropeUuid() != null) {
            buffer.writeUUID(payload.ropeUuid());
        }
        buffer.writeFloat(payload.pathPosition());
    }

    // Decode the serverbound zipline attach
    private static ServerboundZiplineAttachPacket decode(RegistryFriendlyByteBuf buffer) {
        TargetType type = TargetType.values()[Mth.clamp(buffer.readByte(), 0, TargetType.values().length - 1)];
        Vec3 targetPosition = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        BlockPos chainPos = BlockPos.STREAM_CODEC.decode(buffer);
        BlockPos chainConnection = buffer.readBoolean() ? BlockPos.STREAM_CODEC.decode(buffer) : null;
        UUID ropeUuid = buffer.readBoolean() ? buffer.readUUID() : null;
        return new ServerboundZiplineAttachPacket(type, targetPosition, chainPos, chainConnection, ropeUuid, buffer.readFloat());
    }

    // Define the target type values
    public enum TargetType {
        CHAIN,
        ROPE
    }
}
