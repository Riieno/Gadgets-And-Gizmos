package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Place, link and control an entity launcher without losing its saved anchor address
public class EntityLauncherItem extends Item {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double MAX_CLAW_RANGE = 50.0;
    private static final double MIN_CLAW_RANGE = 6.0;
    private static final double MIN_THROW_POWER = 0.35;
    private static final double ENTITY_REEL_FULL_SPEED_AMOUNT = 50.0;
    private static final double ENTITY_REEL_MANUAL_AMOUNT = 0.35;
    private static final double BLOCK_GRAPPLE_MANUAL_AMOUNT = 0.12;
    private static final double BLOCK_GRAPPLE_REEL_AMOUNT = 0.18;
    private static final int MAX_CHARGE_TICKS = 60;
    private static final int CLAW_HOLD_DEBOUNCE_TICKS = 6;
    private static final String HELD_NO_PHYSICS_PREV_TAG = "CTLauncherHeldPrevNoPhysics";
    private static final String HELD_SCALE_PREV_TAG = "CTLauncherHeldPrevScale";
    private static final String HELD_SCALE_ACTIVE_TAG = "CTLauncherHeldScaleApplied";
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> GRAPPLE_FALL_GUARDS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> ACTIVE_CLAWS_BY_OWNER = new ConcurrentHashMap<>();
    private static final Map<UUID, EntityLauncherClawEntity> ACTIVE_CLAW_ENTITIES = new ConcurrentHashMap<>();
    private static final Map<UUID, LauncherInput> LAUNCHER_INPUTS = new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the entity launcher item
    public EntityLauncherItem(Properties properties) {
        super(properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is an instant mode
    public static boolean isInstantMode(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getOrDefault(CTDataComponents.ENTITY_LAUNCHER_INSTANT, false);
    }

    // Toggle the launcher power mode
    public static void togglePowerMode(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof EntityLauncherItem)) {
            return;
        }
        boolean instant = !isInstantMode(stack);
        stack.set(CTDataComponents.ENTITY_LAUNCHER_INSTANT, instant);
        player.displayClientMessage(Component.translatable(
                "item.createthrusters.entity_launcher.power_mode_set",
                Component.translatable("item.createthrusters.entity_launcher.power_mode."
                        + (instant ? "instant" : "charge"))), true);
    }

    // Add the hover text
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(
                "item.createthrusters.entity_launcher.power_mode",
                Component.translatable("item.createthrusters.entity_launcher.power_mode."
                        + (isInstantMode(stack) ? "instant" : "charge")))
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }

    // Handle entity launcher item use on the target
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer) || ctx.getLevel().isClientSide()) {
            return InteractionResult.PASS;
        }
        ServerLevel serverLevel = (ServerLevel) ctx.getLevel();
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
        if (session.activeClawId == null) {
            EntityLauncherClawEntity owned = findOwnedClaw(serverPlayer);
            if (owned != null) {
                session.activeClawId = owned.getUUID();
            }
        }
        EntityLauncherClawEntity claw = session.getActiveClaw(serverLevel);
        if (player.isShiftKeyDown()) {
            EntityLauncherClawEntity transferableClaw = findTransferableClaw(serverPlayer, claw);
            if (transferableClaw != null) {
                claw = transferableClaw;
                session.activeClawId = claw.getUUID();
            }
            boolean hasAttachedRope = claw != null && (claw.hasGrabbedEntity() || claw.hasBlockAnchor());
            EntityLauncherTargetSnapshot transferTarget = hasAttachedRope ? EntityLauncherTargetSnapshot.capture(claw) : null;
            if (hasAttachedRope && transferTarget == null) {
                return InteractionResult.FAIL;
            }
            BlockPos placePos = ctx.getClickedPos().relative(ctx.getClickedFace());
            if (!ctx.getLevel().getBlockState(placePos).canBeReplaced()) {
                return InteractionResult.FAIL;
            }
            BlockState anchorState = CTBlocks.ENTITY_LAUNCHER_ANCHOR.get().defaultBlockState()
                    .setValue(EntityLauncherAnchorBlock.FACING, ctx.getClickedFace());
            if (!ctx.getLevel().setBlock(placePos, anchorState, 3)) {
                return InteractionResult.FAIL;
            }
            if (hasAttachedRope) {
                EntityLauncherAnchorBlockEntity anchor = findPlacedAnchor(ctx.getLevel(), ctx.getClickedPos(),
                        ctx.getClickedFace(), placePos);
                if (anchor == null || !transferTarget.bindTo(anchor)) {
                    removePlacedAnchor(ctx.getLevel(), ctx.getClickedPos(), ctx.getClickedFace(), placePos);
                    return InteractionResult.FAIL;
                }
                transferTarget.prepareSourceRemoval(claw);
            }
            session.clear(serverPlayer);
            if (claw != null) {
                claw.discard();
            }
            if (!player.isCreative()) {
                ctx.getItemInHand().shrink(1);
            }
            return InteractionResult.CONSUME;
        }
        if (claw != null && (claw.hasGrabbedEntity() || claw.hasBlockAnchor())) {
            session.charging = false;
            session.beginClawUse(ctx.getHand());
            player.startUsingItem(ctx.getHand());
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    // Find the placed anchor
    @Nullable
    private static EntityLauncherAnchorBlockEntity findPlacedAnchor(Level interactionLevel, BlockPos clickedPos,
                                                                    Direction clickedFace, BlockPos localPlacedPos) {
        Level placementLevel = resolvePlacementLevel(interactionLevel);
        BlockPos expectedPos = projectPlacementPos(interactionLevel, clickedPos, clickedFace);
        EntityLauncherAnchorBlockEntity anchor = findAnchorAtExactPositions(placementLevel, expectedPos, localPlacedPos);
        if (anchor != null) {
            return anchor;
        }
        if (interactionLevel != placementLevel) {
            return findAnchorAtExactPositions(interactionLevel, expectedPos, localPlacedPos);
        }
        return null;
    }

    // Find the anchor at exact positions
    @Nullable
    private static EntityLauncherAnchorBlockEntity findAnchorAtExactPositions(Level level, BlockPos expectedPos,
                                                                              BlockPos localPlacedPos) {
        EntityLauncherAnchorBlockEntity anchor = SimulatedHelper.findBlockEntityIncludingSubLevels(level,
                expectedPos, EntityLauncherAnchorBlockEntity.class);
        if (anchor != null) {
            return anchor;
        }
        return SimulatedHelper.findBlockEntityIncludingSubLevels(level, localPlacedPos,
                EntityLauncherAnchorBlockEntity.class);
    }

    // Remove the placed anchor
    private static void removePlacedAnchor(Level interactionLevel, BlockPos clickedPos, Direction clickedFace,
                                           BlockPos localPlacedPos) {
        Level placementLevel = resolvePlacementLevel(interactionLevel);
        BlockPos expectedPos = projectPlacementPos(interactionLevel, clickedPos, clickedFace);
        removeAnchorAtExactPositions(interactionLevel, expectedPos, localPlacedPos);
        if (placementLevel != interactionLevel) {
            removeAnchorAtExactPositions(placementLevel, expectedPos, localPlacedPos);
        }
    }

    // Remove the anchor at exact positions
    private static void removeAnchorAtExactPositions(Level level, BlockPos expectedPos, BlockPos localPlacedPos) {
        removeAnchorAt(level, expectedPos);
        if (!localPlacedPos.equals(expectedPos)) {
            removeAnchorAt(level, localPlacedPos);
        }
    }

    // Remove the anchor
    private static void removeAnchorAt(Level level, BlockPos pos) {
        EntityLauncherAnchorBlockEntity anchor = SimulatedHelper.findBlockEntityIncludingSubLevels(level, pos,
                EntityLauncherAnchorBlockEntity.class);
        if (anchor == null) {
            return;
        }
        Level anchorLevel = anchor.getLevel();
        if (anchorLevel != null) {
            anchorLevel.removeBlock(anchor.getBlockPos(), false);
        } else {
            level.removeBlock(pos, false);
        }
    }

    // Resolve the placement level
    private static Level resolvePlacementLevel(Level level) {
        try {
            Object rootLevel = level.getClass().getMethod("getLevel").invoke(level);
            if (rootLevel instanceof Level resolved) {
                return resolved;
            }
        } catch (Exception ignored) {
        }
        return level;
    }

    // Get the project placement pos
    private static BlockPos projectPlacementPos(Level level, BlockPos clickedPos, Direction clickedFace) {
        Vec3 projected = SimulatedHelper.projectOutOfSubLevels(level, Vec3.atCenterOf(clickedPos.relative(clickedFace)));
        return BlockPos.containing(projected);
    }

    // Find the transferable claw
    @Nullable
    private static EntityLauncherClawEntity findTransferableClaw(ServerPlayer player,
                                                                 @Nullable EntityLauncherClawEntity preferred) {
        if (isTransferableClaw(player, preferred)) {
            return preferred;
        }

        UUID registeredId = ACTIVE_CLAWS_BY_OWNER.get(player.getUUID());
        EntityLauncherClawEntity registered = findClawById(player, registeredId);
        if (isTransferableClaw(player, registered)) {
            return registered;
        }

        for (EntityLauncherClawEntity claw : ACTIVE_CLAW_ENTITIES.values()) {
            if (isTransferableClaw(player, claw)) {
                return claw;
            }
        }

        EntityLauncherClawEntity owned = findOwnedClaw(player);
        return isTransferableClaw(player, owned) ? owned : null;
    }

    // Find the claw by id
    @Nullable
    private static EntityLauncherClawEntity findClawById(ServerPlayer player, @Nullable UUID clawId) {
        Entity registered = clawId == null ? null : getEntity(player.getServer(), clawId);
        if (registered instanceof EntityLauncherClawEntity claw && !claw.isRemoved()) {
            return claw;
        }
        EntityLauncherClawEntity cached = clawId == null ? null : ACTIVE_CLAW_ENTITIES.get(clawId);
        return cached != null && !cached.isRemoved() ? cached : null;
    }

    // Check if this is transferable claw
    private static boolean isTransferableClaw(ServerPlayer player, @Nullable EntityLauncherClawEntity claw) {
        return claw != null
                && !claw.isRemoved()
                && claw.getOwner() != null
                && player.getUUID().equals(claw.getOwner().getUUID())
                && (claw.hasGrabbedEntity() || claw.hasBlockAnchor());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle entity launcher item use
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            if (!isInstantMode(stack)) {
                player.startUsingItem(hand);
            }
            return InteractionResultHolder.consume(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        Session session = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
        retractDuplicateOwnedClaws(serverPlayer, session.activeClawId);
        if (session.activeClawId == null) {
            EntityLauncherClawEntity owned = findOwnedClaw(serverPlayer);
            if (owned != null) {
                session.activeClawId = owned.getUUID();
            }
        }
        EntityLauncherClawEntity activeClaw = session.getActiveClaw((ServerLevel) level);
        if (activeClaw != null) {
            session.charging = false;
            if (activeClaw.hasGrabbedEntity() || activeClaw.hasBlockAnchor()) {
                session.beginClawUse(hand);
                player.startUsingItem(hand);
                return InteractionResultHolder.consume(stack);
            }
            session.endClawUse();
            activeClaw.beginRetracting(player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.7f, 1.1f);
            return InteractionResultHolder.consume(stack);
        }

        if (player.isShiftKeyDown()) {
            session.clear(serverPlayer);
            retractDuplicateOwnedClaws(serverPlayer, null);
            return InteractionResultHolder.success(stack);
        }

        session.hand = hand;
        if (isInstantMode(stack)) {
            session.charging = false;
            if (session.heldEntityId != null) {
                session.launchHeld(serverPlayer, 1.0D);
            } else {
                session.fire(serverPlayer, 1.0D);
            }
            return InteractionResultHolder.success(stack);
        }
        session.charging = true;
        session.chargeStartedAt = ((ServerLevel) level).getGameTime();
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    // Release the using
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        int usedTicks = getUseDuration(stack, entity) - timeLeft;
        if (session.clawUseMode == ClawUseMode.PENDING_TAP && usedTicks < CLAW_HOLD_DEBOUNCE_TICKS) {
            EntityLauncherClawEntity claw = session.getActiveClaw(player.serverLevel());
            if (claw != null) {
                if (claw.hasGrabbedEntity()) {
                    claw.startEntityRetractToOwner(player);
                } else {
                    claw.beginRetracting(player);
                }
            }
            session.endClawUse();
            return;
        }
        if (session.isClawUseActive()) {
            EntityLauncherClawEntity claw = session.getActiveClaw(player.serverLevel());
            if (claw != null) {
                claw.stopOwnerGrapple();
            }
            session.endClawUse();
            return;
        }
        float scalar = session.getChargeScalar(player.serverLevel());
        session.charging = false;
        if (session.heldEntityId != null) {
            session.launchHeld(player, scalar);
        } else {
            session.fire(player, scalar);
        }
    }

    // Update the inventory
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (session.charging && player.isUsingItem() && player.getUsedItemHand() == session.hand && (session.activeClawId == null || session.heldEntityId != null)) {
            int used = session.getChargeTicks(player.serverLevel());
            if (used % 5 == 0) {
                int percent = Mth.floor(Mth.clamp(used / (float) MAX_CHARGE_TICKS, 0.0f, 1.0f) * 100.0f);
                player.displayClientMessage(Component.literal("Entity Launcher Power: " + percent + "%"), true);
            }
        }
        EntityLauncherClawEntity claw = session.getActiveClaw(player.serverLevel());
        if (claw == null) {
            session.activeClawId = null;
            session.endClawUse();
        } else if (session.isClawUseActive() && player.isUsingItem() && player.getUsedItemHand() == session.hand) {
            long heldTicks = player.getTicksUsingItem();
            if (heldTicks >= CLAW_HOLD_DEBOUNCE_TICKS) {
                if (claw.hasGrabbedEntity()) {
                    if (player.isShiftKeyDown()) {
                        session.clawUseMode = ClawUseMode.ENTITY_LENGTHEN;
                        claw.extendHeldRope(player, ENTITY_REEL_MANUAL_AMOUNT);
                    } else if (player.isSprinting()) {
                        session.clawUseMode = ClawUseMode.ENTITY_SHORTEN;
                        claw.shortenHeldRope(player, ENTITY_REEL_MANUAL_AMOUNT);
                    } else {
                        session.clawUseMode = ClawUseMode.ENTITY_REEL_CHARGE;
                        UUID grabbedEntityId = claw.getGrabbedEntityId();
                        if (grabbedEntityId != null && claw.reelGrabbedEntityTowardOwner(player, ENTITY_REEL_FULL_SPEED_AMOUNT)) {
                            session.beginChargingHeldEntity(player, grabbedEntityId);
                            claw.discard();
                        }
                    }
                } else if (claw.hasBlockAnchor()) {
                    if (player.isSprinting()) {
                        session.clawUseMode = ClawUseMode.BLOCK_LENGTHEN;
                        claw.extendHeldRope(player, BLOCK_GRAPPLE_MANUAL_AMOUNT);
                    } else if (isJumpHeld(player)) {
                        session.clawUseMode = ClawUseMode.BLOCK_SHORTEN;
                        claw.shortenHeldRope(player, BLOCK_GRAPPLE_MANUAL_AMOUNT);
                    } else {
                        session.clawUseMode = ClawUseMode.BLOCK_GRAPPLE;
                        claw.reelOwnerTowardAnchor(player, BLOCK_GRAPPLE_REEL_AMOUNT);
                    }
                }
            }
        } else if (session.isClawUseActive()) {
            claw.stopOwnerGrapple();
            session.endClawUse();
        }

        if (session.heldEntityId != null) {
            session.tickHeldEntity(player);
        }
    }

    // Handle the post-server tick
    public static void postServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post evt) {
        tickGrappleFallGuards(evt.getServer());
        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            UUID playerId = entry.getKey();
            Session session = entry.getValue();
            ServerPlayer player = evt.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                cleanupOwnerClaw(evt.getServer(), playerId, session);
                LAUNCHER_INPUTS.remove(playerId);
                iterator.remove();
                continue;
            }
            if (!isHoldingLauncher(player) && !EntityLauncherAnchorBlockEntity.isMountedLauncherRider(player)) {
                cleanupOwnerClaw(evt.getServer(), playerId, session);
                LAUNCHER_INPUTS.remove(playerId);
                iterator.remove();
                continue;
            }
            if (!session.charging && session.activeClawId == null && session.heldEntityId == null) {
                iterator.remove();
            }
        }
        Iterator<Map.Entry<UUID, UUID>> clawIterator = ACTIVE_CLAWS_BY_OWNER.entrySet().iterator();
        while (clawIterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = clawIterator.next();
            ServerPlayer player = evt.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || (!isHoldingLauncher(player) && !EntityLauncherAnchorBlockEntity.isMountedLauncherRider(player))) {
                EntityLauncherClawEntity claw = ACTIVE_CLAW_ENTITIES.get(entry.getValue());
                if (claw != null && !claw.isRemoved()) {
                    claw.discard();
                }
                ACTIVE_CLAW_ENTITIES.remove(entry.getValue());
                LAUNCHER_INPUTS.remove(entry.getKey());
                clawIterator.remove();
            }
        }
    }

    // Handle the launcher input
    public static void handleLauncherInput(ServerPlayer player, boolean jumpHeld, boolean jumpPressed) {
        if (!isHoldingLauncher(player)) {
            LAUNCHER_INPUTS.remove(player.getUUID());
            return;
        }
        LAUNCHER_INPUTS.put(player.getUUID(), new LauncherInput(jumpHeld));
        if (!jumpPressed || player.isUsingItem()) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.releaseBlockGrappleForTraversal(player)) {
            protectGrappleFall(player);
        }
    }

    // Register the active claw
    public static void registerActiveClaw(EntityLauncherClawEntity claw) {
        Entity owner = claw.getOwner();
        if (owner != null) {
            ACTIVE_CLAWS_BY_OWNER.put(owner.getUUID(), claw.getUUID());
        }
        ACTIVE_CLAW_ENTITIES.put(claw.getUUID(), claw);
    }

    // Remove the active claw
    public static void unregisterActiveClaw(EntityLauncherClawEntity claw) {
        Entity owner = claw.getOwner();
        if (owner != null) {
            ACTIVE_CLAWS_BY_OWNER.remove(owner.getUUID(), claw.getUUID());
        }
        ACTIVE_CLAW_ENTITIES.remove(claw.getUUID(), claw);
    }

    // Protect the grapple fall
    public static void protectGrappleFall(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            player.fallDistance = 0.0f;
            GRAPPLE_FALL_GUARDS.put(player.getUUID(), false);
        }
    }

    // Protect the held entity fall
    public static void protectHeldEntityFall(Entity entity) {
        if (entity != null) {
            entity.fallDistance = 0.0f;
        }
    }

    // Update the grapple fall guards
    private static void tickGrappleFallGuards(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Boolean>> iterator = GRAPPLE_FALL_GUARDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Boolean> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }

            player.fallDistance = 0.0f;
            if (!player.onGround()) {
                entry.setValue(false);
                continue;
            }

            if (entry.getValue()) {
                iterator.remove();
            } else {
                entry.setValue(true);
            }
        }
    }

    // Get the use duration
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    // Get the use animation
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    // Get the charge scalar
    public float getChargeScalar(ItemStack stack, LivingEntity entity, int timeLeft) {
        int used = getUseDuration(stack, entity) - timeLeft;
        return Mth.clamp(used / (float) MAX_CHARGE_TICKS, 0.0f, 1.0f);
    }

    // Handle the claw returned with entity event
    public static void onClawReturnedWithEntity(ServerPlayer player, UUID entityId) {
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
        session.activeClawId = null;
        session.heldEntityId = entityId;
        session.endClawUse();
        Entity entity = getEntity(player, entityId);
        if (entity != null) {
            protectHeldEntityFall(entity);
            holdEntityAtMuzzle(player, entity, session.hand);
        }
    }

    // Define the claw use mode values
    private enum ClawUseMode {
        NONE,
        PENDING_TAP,
        ENTITY_SHORTEN,
        ENTITY_LENGTHEN,
        ENTITY_REEL_CHARGE,
        BLOCK_SHORTEN,
        BLOCK_LENGTHEN,
        BLOCK_GRAPPLE
    }

    // Track the active session
    private static final class Session {
        // Current hand
        private InteractionHand hand = InteractionHand.MAIN_HAND;
        // Active claw id
        @Nullable private UUID activeClawId;
        // Current held entity id
        @Nullable private UUID heldEntityId;
        // Current claw use mode
        private ClawUseMode clawUseMode = ClawUseMode.NONE;
        // Tracks whether grapple is requested
        private boolean grappleRequested;
        // Tracks whether session is charging
        private boolean charging;
        // Charge start time
        private long chargeStartedAt;

        // Fire the entity launcher
        private void fire(ServerPlayer player, double scalar) {
            Vec3 start = muzzleWorldPos(player, hand);
            EntityLauncherClawEntity claw = new EntityLauncherClawEntity(player.level(), player);
            claw.setPos(start);
            claw.setLaunchedFromMainHand(hand == InteractionHand.MAIN_HAND);
            claw.setMaxTravelDistance(Mth.lerp(scalar, MIN_CLAW_RANGE, MAX_CLAW_RANGE));
            claw.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, (float) Mth.lerp(scalar, 0.75, 2.25), 0.0f);
            claw.setDeltaMovement(player.getLookAngle().normalize().scale(Mth.lerp(scalar, 0.75, 2.25)));
            claw.setOldPosAndRot();
            if (player.level().addFreshEntity(claw)) {
                activeClawId = claw.getUUID();
                player.level().playSound(null, start.x, start.y, start.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.85f, 1.25f);
            }
        }

        // Launch the held entity
        private void launchHeld(ServerPlayer player, double scalar) {
            Entity entity = getEntity(player, heldEntityId);
            heldEntityId = null;
            if (entity != null) {
                holdEntityAtMuzzle(player, entity, hand);
                applyHeldVisualState(entity, false);
                entity.fallDistance = 0.0f;
                double maxKnockback = CTConfigs.COMMON.entityLauncherMaxKnockback.get();
                Vec3 launch = player.getLookAngle().normalize().scale(Mth.lerp(scalar, MIN_THROW_POWER, maxKnockback));
                entity.setDeltaMovement(launch);
                entity.hurtMarked = true;
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.45f, 1.65f);
            }
        }

        // Begin the charging held entity
        private void beginChargingHeldEntity(ServerPlayer player, UUID entityId) {
            activeClawId = null;
            heldEntityId = entityId;
            endClawUse();
            hand = player.getUsedItemHand();
            charging = true;
            chargeStartedAt = player.serverLevel().getGameTime();
            Entity entity = getEntity(player, entityId);
            if (entity != null) {
                protectHeldEntityFall(entity);
                holdEntityAtMuzzle(player, entity, hand);
            }
        }

        // Update the held entity
        private void tickHeldEntity(ServerPlayer player) {
            Entity held = getEntity(player, heldEntityId);
            if (held == null || held.isRemoved()) {
                heldEntityId = null;
                return;
            }
            protectHeldEntityFall(held);
            holdEntityAtMuzzle(player, held, hand);
        }

        // Clear the session
        private void clear(ServerPlayer player) {
            Entity held = getEntity(player, heldEntityId);
            if (held != null) {
                held.fallDistance = 0.0f;
                applyHeldVisualState(held, false);
            }
            heldEntityId = null;
            activeClawId = null;
            endClawUse();
            charging = false;
        }

        // Begin the claw use
        private void beginClawUse(InteractionHand hand) {
            this.hand = hand;
            this.clawUseMode = ClawUseMode.PENDING_TAP;
            this.grappleRequested = true;
        }

        // End the claw use
        private void endClawUse() {
            this.clawUseMode = ClawUseMode.NONE;
            this.grappleRequested = false;
        }

        // Check if the claw use is active
        private boolean isClawUseActive() {
            return grappleRequested && clawUseMode != ClawUseMode.NONE;
        }

        // Release the block grapple for traversal
        private boolean releaseBlockGrappleForTraversal(ServerPlayer player) {
            EntityLauncherClawEntity claw = getActiveClaw(player.serverLevel());
            if (claw == null || !claw.hasBlockAnchor()) {
                return false;
            }
            claw.beginRetracting(player);
            activeClawId = null;
            endClawUse();
            charging = false;
            return true;
        }

        // Get the active claw
        @Nullable
        private EntityLauncherClawEntity getActiveClaw(ServerLevel level) {
            Entity entity = activeClawId == null ? null : level.getEntity(activeClawId);
            if (entity instanceof EntityLauncherClawEntity claw && !claw.isRemoved()) {
                return claw;
            }
            EntityLauncherClawEntity registered = activeClawId == null ? null : ACTIVE_CLAW_ENTITIES.get(activeClawId);
            return registered != null && !registered.isRemoved() ? registered : null;
        }

        // Get the charge ticks
        private int getChargeTicks(ServerLevel level) {
            return (int) Math.max(0L, level.getGameTime() - chargeStartedAt);
        }

        // Get the charge scalar
        private float getChargeScalar(ServerLevel level) {
            return Mth.clamp(getChargeTicks(level) / (float) MAX_CHARGE_TICKS, 0.0f, 1.0f);
        }
    }

    // Get the muzzle world pos
    public static Vec3 muzzleWorldPos(Player player, InteractionHand hand) {
        return muzzleWorldPos(player, hand, 1.0f);
    }

    // Get the muzzle world pos
    public static Vec3 muzzleWorldPos(Player player, InteractionHand hand, float partialTick) {
        Vec3 look = player.getLookAngle().normalize();
        float yawRad = (player.getYRot() + 90.0f) * ((float) Math.PI / 180.0f);
        Vec3 right = new Vec3(Math.cos(yawRad), 0.0, Math.sin(yawRad)).normalize();
        boolean rightMainArm = player.getMainArm() == HumanoidArm.RIGHT;
        double sideSign = hand == InteractionHand.MAIN_HAND
                ? (rightMainArm ? 1.0 : -1.0)
                : (rightMainArm ? -1.0 : 1.0);
        double x = Mth.lerp(partialTick, player.xOld, player.getX());
        double y = Mth.lerp(partialTick, player.yOld, player.getY());
        double z = Mth.lerp(partialTick, player.zOld, player.getZ());
        Vec3 muzzle = new Vec3(x, y + player.getBbHeight() * 0.72, z)
                .add(look.scale(0.62))
                .add(right.scale(0.54 * sideSign))
            .add(0.0, -0.1, 0.0);

        return SimulatedHelper.projectOutOfSubLevels(player.level(), muzzle);
    }

    // Get the hand rope world pos
    public static Vec3 handRopeWorldPos(Player player, InteractionHand hand, float partialTick) {
        Vec3 look = player.getLookAngle().normalize();
        float yaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float yawRad = (yaw + 90.0f) * ((float) Math.PI / 180.0f);
        Vec3 right = new Vec3(Math.cos(yawRad), 0.0, Math.sin(yawRad)).normalize();
        boolean rightMainArm = player.getMainArm() == HumanoidArm.RIGHT;
        double sideSign = hand == InteractionHand.MAIN_HAND
                ? (rightMainArm ? 1.0 : -1.0)
                : (rightMainArm ? -1.0 : 1.0);

        Vec3 handPos = muzzleWorldPos(player, hand, partialTick)
            .add(look.scale(-0.18))
            .add(right.scale(-0.08 * sideSign))
            .add(0.0, -0.04, 0.0);

        return SimulatedHelper.projectOutOfSubLevels(player.level(), handPos);
    }

    // Hold the entity at muzzle
    private static void holdEntityAtMuzzle(ServerPlayer player, Entity entity, InteractionHand hand) {
        Vec3 targetCenter = heldEntityTargetPos(player, hand);
        Vec3 currentCenter = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
        Vec3 delta = targetCenter.subtract(currentCenter);

        applyHeldVisualState(entity, true);
        protectHeldEntityFall(entity);
        entity.setDeltaMovement(entity.getDeltaMovement().scale(0.3).add(delta.scale(0.6)));
        if (delta.lengthSqr() > 2.25) {
            entity.setPos(targetCenter.x, targetCenter.y - entity.getBbHeight() * 0.5, targetCenter.z);
            entity.setDeltaMovement(Vec3.ZERO);
        }
        entity.hurtMarked = true;
    }

    // Apply the held visual state
    private static void applyHeldVisualState(Entity entity, boolean held) {
        CompoundTag data = entity.getPersistentData();
        if (held) {
            if (!data.contains(HELD_NO_PHYSICS_PREV_TAG)) {
                data.putBoolean(HELD_NO_PHYSICS_PREV_TAG, readNoPhysics(entity));
            }
            writeNoPhysics(entity, true);
            if (entity instanceof LivingEntity living) {
                AttributeInstance scale = living.getAttribute(Attributes.SCALE);
                if (scale != null) {
                    if (!data.getBoolean(HELD_SCALE_ACTIVE_TAG)) {
                        data.putDouble(HELD_SCALE_PREV_TAG, scale.getBaseValue());
                        scale.setBaseValue(Math.max(0.2, scale.getBaseValue() * 0.45));
                        living.refreshDimensions();
                        data.putBoolean(HELD_SCALE_ACTIVE_TAG, true);
                    }
                }
            }
            return;
        }

        if (data.contains(HELD_NO_PHYSICS_PREV_TAG)) {
            writeNoPhysics(entity, data.getBoolean(HELD_NO_PHYSICS_PREV_TAG));
            data.remove(HELD_NO_PHYSICS_PREV_TAG);
        } else {
            writeNoPhysics(entity, false);
        }
        if (entity instanceof LivingEntity living) {
            AttributeInstance scale = living.getAttribute(Attributes.SCALE);
            if (scale != null && data.getBoolean(HELD_SCALE_ACTIVE_TAG) && data.contains(HELD_SCALE_PREV_TAG)) {
                scale.setBaseValue(data.getDouble(HELD_SCALE_PREV_TAG));
                living.refreshDimensions();
            }
        }
        data.remove(HELD_SCALE_PREV_TAG);
        data.remove(HELD_SCALE_ACTIVE_TAG);
    }

    // Get the held entity target pos
    private static Vec3 heldEntityTargetPos(ServerPlayer player, InteractionHand hand) {
        return muzzleWorldPos(player, hand);
    }

    // Find the owned claw
    @Nullable
    private static EntityLauncherClawEntity findOwnedClaw(ServerPlayer player) {
        UUID registeredId = ACTIVE_CLAWS_BY_OWNER.get(player.getUUID());
        Entity registered = registeredId == null ? null : player.serverLevel().getEntity(registeredId);
        if (registered instanceof EntityLauncherClawEntity claw && claw.getOwner() == player && !claw.isRemoved()) {
            return claw;
        }
        EntityLauncherClawEntity registeredClaw = registeredId == null ? null : ACTIVE_CLAW_ENTITIES.get(registeredId);
        if (registeredClaw != null && registeredClaw.getOwner() == player && !registeredClaw.isRemoved()) {
            return registeredClaw;
        }
        for (EntityLauncherClawEntity claw : player.serverLevel().getEntitiesOfClass(EntityLauncherClawEntity.class,
                player.getBoundingBox().inflate(256.0), claw -> claw.getOwner() == player && !claw.isRemoved())) {
            return claw;
        }
        return null;
    }

    // Retract the duplicate owned claws
    private static void retractDuplicateOwnedClaws(ServerPlayer player, @Nullable UUID keepId) {
        UUID registeredId = ACTIVE_CLAWS_BY_OWNER.get(player.getUUID());
        Entity registered = registeredId == null ? null : player.serverLevel().getEntity(registeredId);
        if (registered instanceof EntityLauncherClawEntity claw && claw.getOwner() == player && !claw.isRemoved()
                && (keepId == null || !keepId.equals(claw.getUUID()))) {
            claw.beginRetracting(player);
        }
        EntityLauncherClawEntity registeredClaw = registeredId == null ? null : ACTIVE_CLAW_ENTITIES.get(registeredId);
        if (registeredClaw != null && registeredClaw.getOwner() == player && !registeredClaw.isRemoved()
                && (keepId == null || !keepId.equals(registeredClaw.getUUID()))) {
            registeredClaw.beginRetracting(player);
        }
        for (EntityLauncherClawEntity claw : player.serverLevel().getEntitiesOfClass(EntityLauncherClawEntity.class,
                player.getBoundingBox().inflate(256.0), claw -> claw.getOwner() == player && !claw.isRemoved())) {
            if (keepId != null && keepId.equals(claw.getUUID())) {
                continue;
            }
            claw.beginRetracting(player);
        }
    }

    // Check if the player is holding the launcher
    private static boolean isHoldingLauncher(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof EntityLauncherItem
                || player.getOffhandItem().getItem() instanceof EntityLauncherItem;
    }

    // Check if the jump is held
    private static boolean isJumpHeld(ServerPlayer player) {
        LauncherInput input = LAUNCHER_INPUTS.get(player.getUUID());
        return input != null && input.jumpHeld();
    }

    // Clean up the owner claw
    private static void cleanupOwnerClaw(MinecraftServer server, UUID playerId, Session session) {
        Entity held = getEntity(server, session.heldEntityId);
        if (held != null) {
            held.fallDistance = 0.0f;
            applyHeldVisualState(held, false);
            held.hurtMarked = true;
        }

        UUID clawId = session.activeClawId != null ? session.activeClawId : ACTIVE_CLAWS_BY_OWNER.get(playerId);
        EntityLauncherClawEntity registeredClaw = clawId == null ? null : ACTIVE_CLAW_ENTITIES.get(clawId);
        if (registeredClaw != null && !registeredClaw.isRemoved()) {
            registeredClaw.discard();
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = clawId == null ? null : level.getEntity(clawId);
            if (entity instanceof EntityLauncherClawEntity claw && !claw.isRemoved()) {
                claw.discard();
            }
        }
        ACTIVE_CLAWS_BY_OWNER.remove(playerId);
        if (clawId != null) {
            ACTIVE_CLAW_ENTITIES.remove(clawId);
        }
        session.activeClawId = null;
        session.heldEntityId = null;
        session.endClawUse();
        session.charging = false;
    }

    // Read the no-physics flag
    private static boolean readNoPhysics(Entity entity) {
        try {
            Field field = Entity.class.getDeclaredField("noPhysics");
            field.setAccessible(true);
            return field.getBoolean(entity);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    // Write the no-physics flag
    private static void writeNoPhysics(Entity entity, boolean val) {
        try {
            Field field = Entity.class.getDeclaredField("noPhysics");
            field.setAccessible(true);
            field.setBoolean(entity, val);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    // Get the entity
    @Nullable
    private static Entity getEntity(ServerPlayer player, @Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return player.serverLevel().getEntity(uuid);
    }

    // Get the entity
    @Nullable
    private static Entity getEntity(MinecraftServer server, @Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    // Store the launcher input
    private record LauncherInput(boolean jumpHeld) {
    }

}
