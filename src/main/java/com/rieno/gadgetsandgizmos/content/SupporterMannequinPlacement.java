package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.pose.ArmorStandPoseData;
import com.rieno.gadgetsandgizmos.content.pose.ArmorStandPosePreset;
import com.rieno.gadgetsandgizmos.registry.CTEntityTypes;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

// Place marked supporter heads as their matching mannequin entities
public final class SupporterMannequinPlacement {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String CHRISTEROPH_KINETIC_CURRENCY_REWARD_TAG =
            "createthrusters:christeroph_kinetic_currency_reward";
    private static final String CHRISTEROPH_KINETIC_CURRENCY_REWARD_GENERATION_TAG =
            "createthrusters:christeroph_kinetic_currency_reward_generation";
    private static final ArmorStandPosePreset ITEM_POSE = ArmorStandPosePreset.DEFAULTS.stream()
            .filter(preset -> "item".equals(preset.id()))
            .findFirst()
            .orElse(null);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the supporter mannequin placement service
    private SupporterMannequinPlacement() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Place the mannequin represented by a marked supporter head
    public static InteractionResult useOn(UseOnContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        PlayerMannequinVariant variant = SupporterHeads.getVariant(stack);
        if (variant == null) {
            return InteractionResult.PASS;
        }

        Level level = ctx.getLevel();
        BlockPos clickedPos = ctx.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        if (clickedState.getBlock() instanceof SeatBlock) {
            return placeOnSeat(ctx, variant);
        }
        if (ctx.getClickedFace() == Direction.DOWN || CTEntityTypes.PLAYER_MANNEQUIN == null) {
            return InteractionResult.FAIL;
        }

        BlockPlaceContext placeContext = new BlockPlaceContext(ctx);
        BlockPos placePos = placeContext.getClickedPos();
        Vec3 pos = Vec3.atBottomCenterOf(placePos);
        AABB bounds = CTEntityTypes.PLAYER_MANNEQUIN.get().getDimensions()
                .makeBoundingBox(pos.x(), pos.y(), pos.z());
        if (!level.noCollision(null, bounds) || !level.getEntities(null, bounds).isEmpty()) {
            return InteractionResult.FAIL;
        }

        if (level instanceof ServerLevel serverLevel) {
            Consumer<PlayerMannequinEntity> stackConfig = EntityType.createDefaultStackConfig(
                    serverLevel,
                    stack,
                    ctx.getPlayer());
            PlayerMannequinEntity mannequin = CTEntityTypes.PLAYER_MANNEQUIN.get().create(
                    serverLevel,
                    stackConfig.andThen(entity -> applyVariant(entity, variant)),
                    placePos,
                    MobSpawnType.SPAWN_EGG,
                    true,
                    true);
            if (mannequin == null) {
                return InteractionResult.FAIL;
            }

            float yaw = (float) Mth.floor(
                    (Mth.wrapDegrees(ctx.getRotation() - 180.0F) + 22.5F) / 45.0F) * 45.0F;
            mannequin.moveTo(mannequin.getX(), mannequin.getY(), mannequin.getZ(), yaw, 0.0F);
            applyFirstChristerophReward(mannequin, ctx.getPlayer());
            serverLevel.addFreshEntityWithPassengers(mannequin);
            playPlaceEffects(serverLevel, mannequin, ctx.getPlayer());
            consumeOne(stack, ctx.getPlayer());
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // Reset the kinetic currency reward claim
    public static boolean resetKineticCurrencyRewardClaim(Player player) {
        if (player == null) {
            return false;
        }
        CompoundTag rewardData = persistedPlayerData(player);
        boolean hadClaim = rewardData.getBoolean(CHRISTEROPH_KINETIC_CURRENCY_REWARD_TAG)
                || rewardData.contains(CHRISTEROPH_KINETIC_CURRENCY_REWARD_GENERATION_TAG);
        rewardData.remove(CHRISTEROPH_KINETIC_CURRENCY_REWARD_TAG);
        rewardData.remove(CHRISTEROPH_KINETIC_CURRENCY_REWARD_GENERATION_TAG);
        return hadClaim;
    }

    // Place the mannequin on a Create seat
    private static InteractionResult placeOnSeat(UseOnContext ctx, PlayerMannequinVariant variant) {
        Level level = ctx.getLevel();
        BlockPos seatPos = ctx.getClickedPos();
        if (SeatBlock.isSeatOccupied(level, seatPos) || CTEntityTypes.PLAYER_MANNEQUIN == null) {
            return InteractionResult.FAIL;
        }

        ItemStack stack = ctx.getItemInHand();
        if (level instanceof ServerLevel serverLevel) {
            Consumer<PlayerMannequinEntity> stackConfig = EntityType.createDefaultStackConfig(
                    serverLevel,
                    stack,
                    ctx.getPlayer());
            PlayerMannequinEntity mannequin = CTEntityTypes.PLAYER_MANNEQUIN.get().create(
                    serverLevel,
                    stackConfig.andThen(entity -> applyVariant(entity, variant)),
                    seatPos,
                    MobSpawnType.SPAWN_EGG,
                    false,
                    false);
            if (mannequin == null) {
                return InteractionResult.FAIL;
            }

            float yaw = (float) Mth.floor(
                    (Mth.wrapDegrees(ctx.getRotation() - 180.0F) + 22.5F) / 45.0F) * 45.0F;
            mannequin.moveTo(seatPos.getX() + 0.5, seatPos.getY(), seatPos.getZ() + 0.5, yaw, 0.0F);
            applyFirstChristerophReward(mannequin, ctx.getPlayer());
            serverLevel.addFreshEntity(mannequin);
            SeatBlock.sitDown(serverLevel, seatPos, mannequin);
            playPlaceEffects(serverLevel, mannequin, ctx.getPlayer());
            consumeOne(stack, ctx.getPlayer());
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // Apply the selected variant to a new mannequin
    private static void applyVariant(PlayerMannequinEntity mannequin, PlayerMannequinVariant variant) {
        mannequin.setVariant(variant);
        mannequin.setCustomName(variant.displayName());
        mannequin.setCustomNameVisible(false);
        mannequin.applyMannequinDefaults();
    }

    // Apply the first Christeroph reward
    private static void applyFirstChristerophReward(PlayerMannequinEntity mannequin, Player player) {
        if (player == null || CTItems.MUSIC_DISC_KINETIC_CURRENCY == null) {
            return;
        }
        if (!PlayerMannequinVariants.CHRISTEROPH.id().equals(mannequin.getVariant().id())) {
            return;
        }

        CompoundTag rewardData = persistedPlayerData(player);
        if (hasClaimedChristerophReward(rewardData)) {
            return;
        }

        markChristerophRewardClaimed(rewardData);
        applyItemPose(mannequin);
        mannequin.markKineticCurrencyRewardPose();
        mannequin.setItemSlot(
                EquipmentSlot.MAINHAND,
                new ItemStack(CTItems.MUSIC_DISC_KINETIC_CURRENCY.get()));
    }

    // Check if the Christeroph reward was claimed
    private static boolean hasClaimedChristerophReward(CompoundTag rewardData) {
        return rewardData.getBoolean(CHRISTEROPH_KINETIC_CURRENCY_REWARD_TAG);
    }

    // Mark the Christeroph reward claimed
    private static void markChristerophRewardClaimed(CompoundTag rewardData) {
        rewardData.putBoolean(CHRISTEROPH_KINETIC_CURRENCY_REWARD_TAG, true);
        rewardData.remove(CHRISTEROPH_KINETIC_CURRENCY_REWARD_GENERATION_TAG);
    }

    // Get the persisted player data
    private static CompoundTag persistedPlayerData(Player player) {
        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            persistentData.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return persistentData.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // Apply the reward item pose
    private static void applyItemPose(PlayerMannequinEntity mannequin) {
        if (ITEM_POSE == null) {
            return;
        }
        ArmorStandPoseData data = ArmorStandPoseData.fromEntity(mannequin);
        data.applyPreset(ITEM_POSE);
        ArmorStandPoseData.applyAllowedTag(mannequin, data.toTag());
    }

    // Play the mannequin placement effects
    private static void playPlaceEffects(ServerLevel level, PlayerMannequinEntity mannequin, Player player) {
        level.playSound(
                null,
                mannequin.getX(),
                mannequin.getY(),
                mannequin.getZ(),
                SoundEvents.ARMOR_STAND_PLACE,
                SoundSource.BLOCKS,
                0.75F,
                0.8F);
        mannequin.gameEvent(GameEvent.ENTITY_PLACE, player);
    }

    // Consume one supporter head outside creative mode
    private static void consumeOne(ItemStack stack, Player player) {
        if (player == null || !player.isCreative()) {
            stack.shrink(1);
        }
    }
}
