package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.compat.computercraft.RopeWinchPeripheralBridge;
import com.rieno.gadgetsandgizmos.content.ClawBlockEntity;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Expose Simulated rope winches through the addon's stable ComputerCraft bridge
@Mixin(RopeWinchBlockEntity.class)
public abstract class RopeWinchPeripheralBridgeMixin extends SmartBlockEntity implements RopeWinchPeripheralBridge {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger CT_LOGGER = LogUtils.getLogger();
    private static final boolean CT_DEBUG_CLAW_BRIDGE = Boolean.getBoolean("createthrusters.debug.clawBridge");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the rope holder
    @Shadow
    public abstract RopeStrandHolderBehavior getRopeHolder();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the rope winch peripheral bridge
    protected RopeWinchPeripheralBridgeMixin(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos,
                                             net.minecraft.world.level.block.state.BlockState state) {
        super(type, pos, state);
    }

    // Get the attached claw
    @Override
    public @Nullable ClawBlockEntity ct$getAttachedClaw() {
        Level level = getLevel();
        if (level == null) {
            return null;
        }

        RopeStrandHolderBehavior holder = getRopeHolder();
        if (holder == null || !holder.isAttached()) {
            return null;
        }

        Object strand = holder.getAttachedStrand();
        if (strand == null && level.isClientSide()) {
            strand = getClientStrand(holder);
        }
        if (strand == null) {
            return null;
        }

        Set<AttachmentCandidate> candidates = new LinkedHashSet<>();
        if (strand instanceof ServerRopeStrand serverStrand) {
            RopeAttachment startAttachment = serverStrand.getAttachment(RopeAttachmentPoint.START);
            RopeAttachment endAttachment = serverStrand.getAttachment(RopeAttachmentPoint.END);
            collectAttachmentCandidates(startAttachment, candidates);
            collectAttachmentCandidates(endAttachment, candidates);
        }
        collectReflectiveAttachmentCandidates(strand, candidates);

        if (candidates.isEmpty()) {
            return null;
        }

        BlockPos selfPos = getBlockPos();
        if (CT_DEBUG_CLAW_BRIDGE) {
            CT_LOGGER.info("[CT][ClawBridge] winch={} attached candidates={}", selfPos, candidates.size());
        }

        for (AttachmentCandidate candidate : candidates.stream()
                .filter(candidate -> candidate != null && candidate.pos() != null && !candidate.pos().equals(selfPos))
                .sorted(Comparator.comparingDouble(candidate -> selfPos.distSqr(candidate.pos())))
                .toList()) {
            BlockPos candidatePos = candidate.pos();
            ClawBlockEntity attachedSubLevelEntity = SimulatedHelper.findBlockEntity(level, candidate.subLevelId(), candidatePos, ClawBlockEntity.class);
            if (attachedSubLevelEntity != null) {
                if (CT_DEBUG_CLAW_BRIDGE) {
                    CT_LOGGER.info("[CT][ClawBridge] selected sublevel={} claw at {}", candidate.subLevelId(), candidatePos);
                }
                return attachedSubLevelEntity;
            }

            BlockEntity worldEntity = level.getBlockEntity(candidatePos);
            if (worldEntity instanceof ClawBlockEntity claw) {
                if (CT_DEBUG_CLAW_BRIDGE) {
                    CT_LOGGER.info("[CT][ClawBridge] selected world claw at {}", candidatePos);
                }
                return claw;
            }
            ClawBlockEntity sublevelEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, candidatePos, ClawBlockEntity.class);
            if (sublevelEntity != null) {
                if (CT_DEBUG_CLAW_BRIDGE) {
                    CT_LOGGER.info("[CT][ClawBridge] selected fallback claw at {}", candidatePos);
                }
                return sublevelEntity;
            }
        }
        if (CT_DEBUG_CLAW_BRIDGE) {
            CT_LOGGER.info("[CT][ClawBridge] no claw detected for winch={}", selfPos);
        }
        return null;
    }

    // Get the client strand
    private static @Nullable Object getClientStrand(RopeStrandHolderBehavior holder) {
        try {
            return RopeStrandHolderBehavior.class.getMethod("getClientStrand").invoke(holder);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    // Collect the attachment candidates
    private static void collectAttachmentCandidates(@Nullable RopeAttachment attachment, Set<AttachmentCandidate> out) {
        if (attachment == null) {
            return;
        }
        BlockPos pos = attachment.blockAttachment();
        if (pos != null) {
            out.add(new AttachmentCandidate(pos.immutable(), attachment.subLevelID()));
        }
    }

    // Collect the reflective attachment candidates
    private static void collectReflectiveAttachmentCandidates(Object src, Set<AttachmentCandidate> out) {
        if (src == null) {
            return;
        }
        Set<Object> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Method method : src.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            String name = method.getName();
            if (!(name.contains("Attachment") || name.contains("attachment") || name.contains("Segment") || name.contains("segment"))) {
                continue;
            }
            Object val;
            try {
                val = method.invoke(src);
            } catch (ReflectiveOperationException ignored) {
                continue;
            }
            collectPositionsFromValue(val, out, visited);
        }
    }

    // Collect the positions from value
    private static void collectPositionsFromValue(@Nullable Object val, Set<AttachmentCandidate> out, Set<Object> visited) {
        if (val == null) {
            return;
        }
        if (!isScalarLike(val) && !visited.add(val)) {
            return;
        }
        if (val instanceof BlockPos blockPos) {
            out.add(new AttachmentCandidate(blockPos.immutable(), null));
            return;
        }
        if (val instanceof Vec3i vec3i) {
            out.add(new AttachmentCandidate(new BlockPos(vec3i), null));
            return;
        }
        if (val instanceof Vec3 vec3) {
            out.add(new AttachmentCandidate(BlockPos.containing(vec3), null));
            return;
        }
        if (val instanceof RopeAttachment attachment) {
            collectAttachmentCandidates(attachment, out);
            return;
        }
        if (val instanceof Map<?, ?> map) {
            for (Object entryValue : map.values()) {
                collectPositionsFromValue(entryValue, out, visited);
            }
            return;
        }
        if (val instanceof Iterable<?> iterable) {
            for (Object entryValue : iterable) {
                collectPositionsFromValue(entryValue, out, visited);
            }
            return;
        }
        if (val.getClass().isArray()) {
            int length = Array.getLength(val);
            for (int i = 0; i < length; i++) {
                collectPositionsFromValue(Array.get(val, i), out, visited);
            }
            return;
        }

        for (String accessor : new String[]{"blockAttachment", "getBlockPos", "blockPos", "getPos", "pos", "getStart", "start", "getEnd", "end"}) {
            try {
                Method method = val.getClass().getMethod(accessor);
                if (method.getParameterCount() != 0) {
                    continue;
                }
                Object nested = method.invoke(val);
                collectPositionsFromValue(nested, out, visited);
            } catch (ReflectiveOperationException ignored) {

            }
        }

        try {
            Method blockAttachment = val.getClass().getMethod("blockAttachment");
            Object maybePos = blockAttachment.invoke(val);
            if (maybePos instanceof BlockPos blockPos) {
                UUID subLevelId = null;
                try {
                    Method subLevelAccessor = val.getClass().getMethod("subLevelID");
                    Object maybeSubLevelId = subLevelAccessor.invoke(val);
                    if (maybeSubLevelId instanceof UUID uuid) {
                        subLevelId = uuid;
                    }
                } catch (ReflectiveOperationException ignored) {

                }
                out.add(new AttachmentCandidate(blockPos.immutable(), subLevelId));
            }
        } catch (ReflectiveOperationException ignored) {

        }
    }

    // Check if this is scalar like
    private static boolean isScalarLike(Object val) {
        return val instanceof String
                || val instanceof Number
                || val instanceof Boolean
                || val instanceof Character
                || val.getClass().isEnum();
    }

    // Store the attachment candidate
    private record AttachmentCandidate(BlockPos pos, @Nullable UUID subLevelId) {
    }
}
