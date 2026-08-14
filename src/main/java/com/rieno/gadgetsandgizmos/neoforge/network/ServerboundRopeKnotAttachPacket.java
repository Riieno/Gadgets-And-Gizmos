package com.rieno.gadgetsandgizmos.neoforge.network;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedRopeCompat;
import com.rieno.gadgetsandgizmos.content.RopeKnotBlockEntity;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import dev.simulated_team.simulated.index.SimDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

// Check and apply one client-selected knot position on an existing launcher rope
public record ServerboundRopeKnotAttachPacket(UUID ropeUuid, Vec3 targetPosition, float pathPosition)
        implements CustomPacketPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Type<ServerboundRopeKnotAttachPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "rope_knot_attach"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundRopeKnotAttachPacket> STREAM_CODEC =
            StreamCodec.of(ServerboundRopeKnotAttachPacket::encode, ServerboundRopeKnotAttachPacket::decode);

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

    // Handle the serverbound rope knot attach
    public static void handle(ServerboundRopeKnotAttachPacket payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            if (CTBlocks.ROPE_KNOT == null) {
                return;
            }
            if (!player.isShiftKeyDown()) {
                return;
            }
            ItemStack ropeStack = findRopeStack(player);
            if (ropeStack.isEmpty()) {
                return;
            }
            double maxDistance = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 3.0D;
            if (payload.targetPosition().distanceToSqr(player.getEyePosition()) > maxDistance * maxDistance) {
                return;
            }

            ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(level);
            ServerRopeStrand strand = manager == null ? null : manager.getStrand(payload.ropeUuid());
            if (strand == null) {
                return;
            }
            Vec3 sampledPosition = sampleRope(strand, payload.pathPosition());
            if (sampledPosition == null || sampledPosition.distanceToSqr(payload.targetPosition()) > 1.0D) {
                return;
            }
            BlockPos first = ropeStack.has(SimDataComponents.ROPE_FIRST_CONNECTION)
                    ? ropeStack.get(SimDataComponents.ROPE_FIRST_CONNECTION)
                    : null;
            RopeKnotBlockEntity knot = findOrCreateKnot(level, sampledPosition, first);
            if (knot == null) {
                return;
            }
            knot.setRopeAnchor(payload.ropeUuid(), payload.pathPosition(), sampledPosition);
            BlockPos knotPos = knot.getBlockPos();
            if (ropeStack.has(SimDataComponents.ROPE_FIRST_CONNECTION)) {
                ropeStack.remove(SimDataComponents.ROPE_FIRST_CONNECTION);
                if (first != null && attachRope(level, first, knotPos, !player.getAbilities().instabuild)) {
                    if (!player.getAbilities().instabuild) {
                        ropeStack.shrink(1);
                    }
                    return;
                }
            }

            ropeStack.set(SimDataComponents.ROPE_FIRST_CONNECTION, knotPos);
            level.playSound(null, knotPos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
        });
    }

    // Find the rope stack
    private static ItemStack findRopeStack(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof RopeItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // Attach the rope
    private static boolean attachRope(ServerLevel level, BlockPos firstPos, BlockPos secondPos, boolean dropItem) {
        RopeStrandHolderBehavior first = resolveRopeHolder(level, firstPos);
        RopeStrandHolderBehavior second = resolveRopeHolder(level, secondPos);
        if (first == null || second == null || first.isAttached() || second.isAttached()) {
            return false;
        }
        if (SimulatedRopeCompat.createRope(first, second, dropItem)) {
            level.playSound(null, firstPos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
            level.playSound(null, secondPos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
            return true;
        }
        return false;
    }

    // Find or create the knot
    private static @Nullable RopeKnotBlockEntity findOrCreateKnot(ServerLevel level, Vec3 target, @Nullable BlockPos excludedPos) {
        if (CTBlocks.ROPE_KNOT == null) {
            return null;
        }
        Object targetSubLevel = SimulatedHelper.getContainingSubLevel(level, target);
        if (targetSubLevel != null) {
            RopeKnotBlockEntity knot = findOrCreateKnotInSubLevel(targetSubLevel, target, excludedPos);
            if (knot != null) {
                return knot;
            }
        }

        BlockPos origin = BlockPos.containing(target);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(1, 1, 1))) {
            BlockPos immutable = candidate.immutable();
            if (immutable.equals(excludedPos)) {
                continue;
            }
            if (!level.getWorldBorder().isWithinBounds(immutable)) {
                continue;
            }
            BlockEntity existing = level.getBlockEntity(immutable);
            if (existing instanceof RopeKnotBlockEntity knot && knot.isFree()) {
                knot.setAttachmentPoint(target);
                return knot;
            }
            BlockState state = level.getBlockState(immutable);
            if (!state.canBeReplaced()) {
                continue;
            }
            double distance = immutable.getCenter().distanceToSqr(target);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = immutable;
            }
        }
        if (best == null || !level.setBlock(best, CTBlocks.ROPE_KNOT.get().defaultBlockState(), 3)) {
            return null;
        }
        BlockEntity placed = level.getBlockEntity(best);
        if (placed instanceof RopeKnotBlockEntity knot) {
            knot.setAttachmentPoint(target);
            return knot;
        }
        return null;
    }

    // Find or create the knot in sublevel
    private static @Nullable RopeKnotBlockEntity findOrCreateKnotInSubLevel(@Nullable Object subLevel, Vec3 target,
                                                                            @Nullable BlockPos excludedPos) {
        if (CTBlocks.ROPE_KNOT == null) {
            return null;
        }
        if (subLevel == null) {
            return null;
        }
        Object accessor = getEmbeddedLevelAccessor(subLevel);
        if (accessor == null) {
            return null;
        }
        BlockPos localTarget = BlockPos.containing(target);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(localTarget.offset(-1, -1, -1), localTarget.offset(1, 1, 1))) {
            BlockPos immutable = candidate.immutable();
            if (immutable.equals(excludedPos)) {
                continue;
            }
            BlockEntity existing = getAccessorBlockEntity(accessor, immutable);
            if (existing instanceof RopeKnotBlockEntity knot && knot.isFree()) {
                knot.setAttachmentPoint(target);
                return knot;
            }
            BlockState state = getAccessorBlockState(accessor, immutable);
            if (state == null || !state.canBeReplaced()) {
                continue;
            }
            double distance = immutable.getCenter().distanceToSqr(target);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = immutable;
            }
        }
        if (best == null || !setAccessorBlock(accessor, best, CTBlocks.ROPE_KNOT.get().defaultBlockState())) {
            return null;
        }
        BlockEntity placed = getAccessorBlockEntity(accessor, best);
        RopeKnotBlockEntity knot = placed instanceof RopeKnotBlockEntity placedKnot
                ? placedKnot
                : SimulatedHelper.findBlockEntityInSubLevel(subLevel, best, RopeKnotBlockEntity.class);
        if (knot != null) {
            knot.setAttachmentPoint(target);
        }
        return knot;
    }

    // Get the embedded level accessor
    private static @Nullable Object getEmbeddedLevelAccessor(@Nullable Object subLevel) {
        if (subLevel == null) {
            return null;
        }
        try {
            Object plot = subLevel.getClass().getMethod("getPlot").invoke(subLevel);
            return plot == null ? null : plot.getClass().getMethod("getEmbeddedLevelAccessor").invoke(plot);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Get the accessor block entity
    private static @Nullable BlockEntity getAccessorBlockEntity(@Nullable Object accessor, BlockPos pos) {
        if (accessor == null) {
            return null;
        }
        try {
            Object res = accessor.getClass().getMethod("getBlockEntity", BlockPos.class).invoke(accessor, pos);
            return res instanceof BlockEntity blockEntity ? blockEntity : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Get the accessor block state
    private static @Nullable BlockState getAccessorBlockState(@Nullable Object accessor, BlockPos pos) {
        if (accessor == null) {
            return null;
        }
        try {
            Object res = accessor.getClass().getMethod("getBlockState", BlockPos.class).invoke(accessor, pos);
            return res instanceof BlockState blockState ? blockState : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Set the accessor block
    private static boolean setAccessorBlock(@Nullable Object accessor, BlockPos pos, BlockState state) {
        if (accessor == null) {
            return false;
        }
        try {
            Object res = accessor.getClass().getMethod("setBlock", BlockPos.class, BlockState.class).invoke(accessor, pos, state);
            return res instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    // Resolve the rope holder
    private static @Nullable RopeStrandHolderBehavior resolveRopeHolder(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, pos);
        if (!(blockEntity instanceof SmartBlockEntity smartBlockEntity)) {
            return null;
        }
        return smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
    }

    // Sample the rope
    private static @Nullable Vec3 sampleRope(ServerRopeStrand strand, float pos) {
        var points = strand.getPoints();
        float cumulative = 0.0f;
        for (int i = 0; i < points.size() - 1; i++) {
            Vector3d a = new Vector3d((Vector3dc) points.get(i));
            Vector3d b = new Vector3d((Vector3dc) points.get(i + 1));
            Vector3d ab = b.sub((Vector3dc) a, new Vector3d());
            float segmentLength = (float) ab.length();
            if (pos <= cumulative + segmentLength) {
                double local = segmentLength <= 1.0E-5f ? 0.0D : (pos - cumulative) / segmentLength;
                Vector3d res = a.fma(Mth.clamp(local, 0.0D, 1.0D), ab, new Vector3d());
                return new Vec3(res.x, res.y, res.z);
            }
            cumulative += segmentLength;
        }
        if (points.isEmpty()) {
            return null;
        }
        Vector3d last = new Vector3d((Vector3dc) points.get(points.size() - 1));
        return new Vec3(last.x, last.y, last.z);
    }

    // Encode the serverbound rope knot attach
    private static void encode(RegistryFriendlyByteBuf buffer, ServerboundRopeKnotAttachPacket payload) {
        buffer.writeUUID(payload.ropeUuid());
        buffer.writeDouble(payload.targetPosition().x);
        buffer.writeDouble(payload.targetPosition().y);
        buffer.writeDouble(payload.targetPosition().z);
        buffer.writeFloat(payload.pathPosition());
    }

    // Decode the serverbound rope knot attach
    private static ServerboundRopeKnotAttachPacket decode(RegistryFriendlyByteBuf buffer) {
        UUID ropeUuid = buffer.readUUID();
        Vec3 targetPosition = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        return new ServerboundRopeKnotAttachPacket(ropeUuid, targetPosition, buffer.readFloat());
    }
}
