package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.neoforge.network.PhysicsGantryBeltWheelSelectionPayload;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Pair two kinetic belt wheels across the physical gantry cable
public class PhysicsGantryBeltWheelBlock extends RotatedPillarKineticBlock
        implements IBE<PhysicsGantryBeltWheelBlockEntity>, ICogWheel, IWrenchable, BlockSubLevelAssemblyListener {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long LINK_SELECTION_TIMEOUT_TICKS = 20L * 30L;
    private static final int HARD_MAX_LINK_DISTANCE = 64;
    private static final Map<UUID, BlockPos> PENDING_LINK_POSITIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> PENDING_LINK_SUBLEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PENDING_LINK_EXPIRY = new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics gantry belt wheel block
    public PhysicsGantryBeltWheelBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle physics gantry belt wheel block use on the target
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof PhysicsGantryBeltWheelBlockEntity local)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide && isShears(stack)) {
            if (local.hasLinkedTarget()) {
                local.breakLink(true);
                if (player instanceof ServerPlayer serverPlayer) {
                    stack.hurtAndBreak(1, serverPlayer, hand == InteractionHand.MAIN_HAND
                            ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                            : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
                }
                notify(player, "createthrusters.physics_gantry_belt_wheel.link_broken", ChatFormatting.YELLOW);
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!(stack.getItem() instanceof BeltConnectorItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        return handleBeltConnectorUse(level, player, stack, local);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the belt connector use
    private static ItemInteractionResult handleBeltConnectorUse(Level level, Player player, ItemStack stack,
                                                                PhysicsGantryBeltWheelBlockEntity clickedWheel) {
        UUID playerId = player.getUUID();
        long now = level.getGameTime();

        Long expiresAt = PENDING_LINK_EXPIRY.get(playerId);
        if (expiresAt != null && expiresAt < now) {
            clearPendingSelection(player);
        }

        BlockPos pendingPos = PENDING_LINK_POSITIONS.get(playerId);
        UUID pendingSubLevel = PENDING_LINK_SUBLEVELS.get(playerId);

        UUID clickedSubLevel = SimulatedHelper.getContainingSubLevelId(clickedWheel);
        if (pendingPos == null) {
            setPendingSelection(player, clickedWheel, clickedSubLevel, now + LINK_SELECTION_TIMEOUT_TICKS);
            notify(player, "createthrusters.physics_gantry_belt_wheel.link_first", ChatFormatting.GRAY);
            return ItemInteractionResult.SUCCESS;
        }

        if (pendingPos.equals(clickedWheel.getBlockPos()) && java.util.Objects.equals(pendingSubLevel, clickedSubLevel)) {
            clearPendingSelection(player);
            notify(player, "createthrusters.physics_gantry_belt_wheel.link_cleared", ChatFormatting.YELLOW);
            return ItemInteractionResult.SUCCESS;
        }

        PhysicsGantryBeltWheelBlockEntity firstWheel = SimulatedHelper.findBlockEntity(level, pendingSubLevel, pendingPos,
                PhysicsGantryBeltWheelBlockEntity.class);
        if (firstWheel == null || firstWheel.isRemoved()) {
            setPendingSelection(player, clickedWheel, clickedSubLevel, now + LINK_SELECTION_TIMEOUT_TICKS);
            notify(player, "createthrusters.physics_gantry_belt_wheel.link_missing", ChatFormatting.RED);
            return ItemInteractionResult.SUCCESS;
        }

        if (firstWheel.getBlockPos().equals(clickedWheel.getBlockPos()) &&
                java.util.Objects.equals(SimulatedHelper.getContainingSubLevelId(firstWheel), clickedSubLevel)) {
            notify(player, "createthrusters.physics_gantry_belt_wheel.link_self", ChatFormatting.RED);
            return ItemInteractionResult.FAIL;
        }

        Vec3 firstAnchor = firstWheel.getWorldAnchorPosition();
        Vec3 secondAnchor = clickedWheel.getWorldAnchorPosition();
        if (firstAnchor == null || secondAnchor == null) {
            notify(player, "createthrusters.physics_gantry_belt_wheel.link_missing", ChatFormatting.RED);
            return ItemInteractionResult.FAIL;
        }
        int maxDistance = getConfiguredMaxDistance();
        if (!PhysicsGantryBeltWheelLink.isDistanceValid(firstAnchor, secondAnchor, maxDistance)) {
            notify(player, "createthrusters.physics_gantry_belt_wheel.distance_invalid", ChatFormatting.RED);
            return ItemInteractionResult.FAIL;
        }

        UUID firstSubLevel = SimulatedHelper.getContainingSubLevelId(firstWheel);
        firstWheel.breakLink(true);
        clickedWheel.breakLink(true);
        firstWheel.setLinkedTarget(clickedWheel.getBlockPos(), clickedSubLevel);
        clickedWheel.setLinkedTarget(firstWheel.getBlockPos(), firstSubLevel);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        clearPendingSelection(player);
        notify(player, "createthrusters.physics_gantry_belt_wheel.link_success", ChatFormatting.GREEN);
        return ItemInteractionResult.SUCCESS;
    }

    // Set the pending selection
    private static void setPendingSelection(Player player, PhysicsGantryBeltWheelBlockEntity wheel,
                                            @org.jetbrains.annotations.Nullable UUID subLevelId, long expiresAtTick) {
        UUID playerId = player.getUUID();
        BlockPos pos = wheel.getBlockPos();
        PENDING_LINK_POSITIONS.put(playerId, pos.immutable());
        if (subLevelId == null) {
            PENDING_LINK_SUBLEVELS.remove(playerId);
        } else {
            PENDING_LINK_SUBLEVELS.put(playerId, subLevelId);
        }
        PENDING_LINK_EXPIRY.put(playerId, expiresAtTick);
        syncPendingSelection(player, true, pos, subLevelId, wheel.getWorldAnchorPosition());
    }

    // Clear the pending selection
    private static void clearPendingSelection(Player player) {
        UUID playerId = player.getUUID();
        PENDING_LINK_POSITIONS.remove(playerId);
        PENDING_LINK_SUBLEVELS.remove(playerId);
        PENDING_LINK_EXPIRY.remove(playerId);
        syncPendingSelection(player, false, BlockPos.ZERO, null, Vec3.ZERO);
    }

    // Synchronize the selected endpoint with its owner for Create-style connection particles.
    private static void syncPendingSelection(Player player, boolean active, BlockPos pos,
                                             @org.jetbrains.annotations.Nullable UUID subLevelId, Vec3 worldAnchor) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        PacketDistributor.sendToPlayer(serverPlayer, new PhysicsGantryBeltWheelSelectionPayload(
                active, pos.asLong(), subLevelId, worldAnchor.x, worldAnchor.y, worldAnchor.z,
                getConfiguredMaxDistance()));
    }

    // Handle wrench use
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx) {
        Direction clickedFace = ctx.getClickedFace();
        if (clickedFace.getAxis() == state.getValue(AXIS)) {
            if (ctx.getLevel().isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            Player player = ctx.getPlayer();
            if (player != null) {
                PhysicsGantryBeltWheelBlockEntity be = SimulatedHelper.findBlockEntityIncludingSubLevels(
                        ctx.getLevel(), ctx.getClickedPos(), PhysicsGantryBeltWheelBlockEntity.class);
                if (be != null) {
                    player.displayClientMessage(be.getWrenchStatusComponent(), true);
                }
            }
            return InteractionResult.SUCCESS;
        }

        InteractionResult res = super.onWrenched(state, ctx);
        if (res.consumesAction() && !ctx.getLevel().isClientSide()) {
            Player player = ctx.getPlayer();
            if (player != null) {
                PhysicsGantryBeltWheelBlockEntity be = SimulatedHelper.findBlockEntityIncludingSubLevels(
                        ctx.getLevel(), ctx.getClickedPos(), PhysicsGantryBeltWheelBlockEntity.class);
                if (be != null) {
                    player.displayClientMessage(be.getWrenchStatusComponent(), true);
                }
            }
        }
        return res;
    }

    // Notify the physics gantry belt wheel block
    private static void notify(Player player, String key, ChatFormatting formatting) {
        Component msg = Component.translatable(key).withStyle(formatting);
        player.displayClientMessage(msg, true);
    }

    // Get the configured max distance
    private static int getConfiguredMaxDistance() {
        int configured = CTConfigs.SERVER.physicsGantryBeltWheelMaxDistance.get();
        return Math.max(1, Math.min(configured, HARD_MAX_LINK_DISTANCE));
    }

    // Check if this is shears
    private static boolean isShears(ItemStack stack) {
        return stack.getItem() instanceof ShearsItem || stack.is(Tags.Items.TOOLS_SHEAR);
    }

    // Handle the remove event
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && !state.is(newState.getBlock())) {
            withBlockEntityDo(level, pos, be -> be.breakLink(true));
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // Preserve both ends of a belt when Sable moves them into or out of a sub-level.
    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                           BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(originLevel, oldPos,
                be -> be.beginAssemblyTransfer(originLevel, oldPos, newPos));
    }

    // Complete the pending endpoint remap after Sable has recreated the block entity.
    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        withBlockEntityDo(resultingLevel, newPos,
                be -> be.finishAssemblyTransfer(originLevel, oldPos, newPos));
    }

    // Check if this has a shaft on the side
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {

        return face.getAxis() == state.getValue(AXIS);
    }

    // Get the rotation axis
    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    // Check if this is a small cog
    @Override
    public boolean isSmallCog() {

        return true;
    }

    // Check if this is a large cog
    @Override
    public boolean isLargeCog() {
        return false;
    }

    // Check if this is a dedicated cog wheel
    @Override
    public boolean isDedicatedCogWheel() {
        return true;
    }

    // Get the render shape
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // Get the block entity class
    @Override
    public Class<PhysicsGantryBeltWheelBlockEntity> getBlockEntityClass() {
        return PhysicsGantryBeltWheelBlockEntity.class;
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends PhysicsGantryBeltWheelBlockEntity> getBlockEntityType() {
        return CTBlockEntities.PHYSICS_GANTRY_BELT_WHEEL.get();
    }
}
