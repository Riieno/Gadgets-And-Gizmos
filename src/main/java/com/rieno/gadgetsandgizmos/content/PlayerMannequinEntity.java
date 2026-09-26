package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;

import java.net.URI;

// Keep a mannequin's player skin, pose and equipment state synchronized
public class PlayerMannequinEntity extends ArmorStand {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String VARIANT_TAG = "PlayerMannequinVariant";
    private static final String ORIGINAL_VARIANT_TAG = "OriginalSupporterVariant";
    private static final String REMOTE_SKIN_URL_TAG = "RemoteSkinUrl";
    private static final String STEVE_SKIN_TAG = "SteveSkin";
    private static final String SLIM_SKIN_TAG = "SlimSkin";
    private static final String KINETIC_CURRENCY_REWARD_POSE_TAG = "KineticCurrencyRewardPose";
    private static final EquipmentSlot[] DROPPED_EQUIPMENT_SLOTS = {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND,
            EquipmentSlot.FEET,
            EquipmentSlot.LEGS,
            EquipmentSlot.CHEST,
            EquipmentSlot.HEAD
    };
    private static final Rotations DEFAULT_HEAD_POSE = new Rotations(0.0F, 0.0F, 0.0F);
    private static final Rotations DEFAULT_BODY_POSE = new Rotations(0.0F, 0.0F, 0.0F);
    private static final Rotations DEFAULT_LEFT_ARM_POSE = new Rotations(-10.0F, 0.0F, -10.0F);
    private static final Rotations DEFAULT_RIGHT_ARM_POSE = new Rotations(-15.0F, 0.0F, 10.0F);
    private static final Rotations DEFAULT_LEFT_LEG_POSE = new Rotations(-1.0F, 0.0F, -1.0F);
    private static final Rotations DEFAULT_RIGHT_LEG_POSE = new Rotations(1.0F, 0.0F, 1.0F);
    private static final EntityDataAccessor<String> DATA_VARIANT =
            SynchedEntityData.defineId(PlayerMannequinEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_REMOTE_SKIN_URL =
            SynchedEntityData.defineId(PlayerMannequinEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_STEVE_SKIN =
            SynchedEntityData.defineId(PlayerMannequinEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SLIM_SKIN =
            SynchedEntityData.defineId(PlayerMannequinEntity.class, EntityDataSerializers.BOOLEAN);
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether kinetic currency reward pose is set
    private boolean kineticCurrencyRewardPose;
    // The supporter variant this mannequin was originally placed as
    private String originalSupporterVariantId = PlayerMannequinVariants.DEFAULT_ID;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the player mannequin entity
    public PlayerMannequinEntity(EntityType<? extends ArmorStand> entityType, Level level) {
        super(entityType, level);
        applyMannequinDefaults();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the attributes
    public static AttributeSupplier.Builder createAttributes() {
        return ArmorStand.createAttributes();
    }

    // Define the synched data
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, PlayerMannequinVariants.DEFAULT_ID);
        builder.define(DATA_REMOTE_SKIN_URL, "");
        builder.define(DATA_STEVE_SKIN, false);
        builder.define(DATA_SLIM_SKIN, false);
    }

    // Add the additional save data
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(VARIANT_TAG, getVariant().id());
        tag.putString(ORIGINAL_VARIANT_TAG, getOriginalSupporterVariant().id());
        if (!remoteSkinUrl().isBlank()) tag.putString(REMOTE_SKIN_URL_TAG, remoteSkinUrl());
        if (usesSteveSkin()) tag.putBoolean(STEVE_SKIN_TAG, true);
        if (usesSlimSkin()) tag.putBoolean(SLIM_SKIN_TAG, true);
        if (kineticCurrencyRewardPose) {
            tag.putBoolean(KINETIC_CURRENCY_REWARD_POSE_TAG, true);
        }
    }

    // Read the additional save data
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getString(VARIANT_TAG));
        originalSupporterVariantId = tag.contains(ORIGINAL_VARIANT_TAG, Tag.TAG_STRING)
                ? PlayerMannequinVariants.byIdOrDefault(tag.getString(ORIGINAL_VARIANT_TAG)).id()
                : getVariant().id();
        setRemoteSkinUrl(tag.getString(REMOTE_SKIN_URL_TAG));
        setSteveSkin(tag.getBoolean(STEVE_SKIN_TAG));
        setSlimSkin(tag.getBoolean(SLIM_SKIN_TAG));
        kineticCurrencyRewardPose = tag.getBoolean(KINETIC_CURRENCY_REWARD_POSE_TAG);
    }

    // Set the item slot
    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        ItemStack prev = getItemBySlot(slot).copy();
        super.setItemSlot(slot, stack);
        if (shouldResetKineticCurrencyRewardPose(slot, prev, stack)) {
            kineticCurrencyRewardPose = false;
            resetMannequinPose();
        }
    }

    // Apply damage to the mannequin
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRemoved()) {
            return false;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!(source.getEntity() instanceof Player)) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            kill();
            return false;
        }
        if (isInvulnerableTo(source) || isInvisible() || isMarker()) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            breakWithoutMannequinItem(serverLevel, source);
            kill();
            return false;
        }
        if (source.is(DamageTypeTags.IGNITES_ARMOR_STANDS)) {
            if (isOnFire()) {
                causeMannequinDamage(serverLevel, source, 0.15F);
            } else {
                igniteForSeconds(5.0F);
            }
            return false;
        }
        if (source.is(DamageTypeTags.BURNS_ARMOR_STANDS) && getHealth() > 0.5F) {
            causeMannequinDamage(serverLevel, source, 4.0F);
            return false;
        }

        boolean canBreak = source.is(DamageTypeTags.CAN_BREAK_ARMOR_STAND);
        boolean alwaysKills = source.is(DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS);
        if (!canBreak && !alwaysKills) {
            return false;
        }
        if (source.getEntity() instanceof Player player && !player.getAbilities().mayBuild) {
            return false;
        }
        if (source.isCreativePlayer()) {
            playMannequinBrokenSound();
            showMannequinBreakingParticles();
            kill();
            return true;
        }

        long gameTime = serverLevel.getGameTime();
        if (gameTime - lastHit > 5L && !alwaysKills) {
            serverLevel.broadcastEntityEvent(this, (byte) 32);
            gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
            lastHit = gameTime;
        } else {
            breakByPlayer(serverLevel, source);
            showMannequinBreakingParticles();
            kill();
        }
        return true;
    }

    // Apply the mannequin defaults
    public void applyMannequinDefaults() {
        setShowArms(true);
        setNoBasePlate(true);
    }

    // Mark the kinetic currency reward pose
    public void markKineticCurrencyRewardPose() {
        kineticCurrencyRewardPose = true;
    }

    // Reset the mannequin pose
    public void resetMannequinPose() {
        setHeadPose(DEFAULT_HEAD_POSE);
        setBodyPose(DEFAULT_BODY_POSE);
        setLeftArmPose(DEFAULT_LEFT_ARM_POSE);
        setRightArmPose(DEFAULT_RIGHT_ARM_POSE);
        setLeftLegPose(DEFAULT_LEFT_LEG_POSE);
        setRightLegPose(DEFAULT_RIGHT_LEG_POSE);
        applyMannequinDefaults();
    }

    // Get the variant
    public PlayerMannequinVariant getVariant() {
        return PlayerMannequinVariants.byIdOrDefault(this.entityData.get(DATA_VARIANT));
    }

    // Set the variant
    public void setVariant(String variantId) {
        PlayerMannequinVariant variant = PlayerMannequinVariants.byIdOrDefault(variantId);
        this.entityData.set(DATA_VARIANT, variant.id());
        setSlimSkin(variant.slim());
    }

    // Set the variant
    public void setVariant(PlayerMannequinVariant variant) {
        setVariant(variant == null ? PlayerMannequinVariants.DEFAULT_ID : variant.id());
    }

    // Get the supporter variant this mannequin should return when broken
    public PlayerMannequinVariant getOriginalSupporterVariant() {
        return PlayerMannequinVariants.byIdOrDefault(originalSupporterVariantId);
    }

    // Set the supporter variant this mannequin should return when broken
    public void setOriginalSupporterVariant(PlayerMannequinVariant variant) {
        originalSupporterVariantId = (variant == null ? getVariant() : variant).id();
    }

    // Get the verified Mojang skin texture URL assigned to this mannequin
    public String remoteSkinUrl() {
        return entityData.get(DATA_REMOTE_SKIN_URL);
    }

    // Set the verified Mojang skin texture URL assigned to this mannequin
    public void setRemoteSkinUrl(String skinUrl) {
        String normalized = skinUrl == null ? "" : skinUrl.strip();
        if (!verifiedSkinUrl(normalized)) normalized = "";
        entityData.set(DATA_REMOTE_SKIN_URL, normalized);
    }

    // Check whether this mannequin should render the Steve fallback skin
    public boolean usesSteveSkin() {
        return entityData.get(DATA_STEVE_SKIN);
    }

    // Set whether this mannequin should render the Steve fallback skin
    public void setSteveSkin(boolean steveSkin) {
        entityData.set(DATA_STEVE_SKIN, steveSkin);
        if (steveSkin) setSlimSkin(false);
    }

    // Check whether this mannequin uses the three-pixel slim player arm model
    public boolean usesSlimSkin() {
        return entityData.get(DATA_SLIM_SKIN);
    }

    // Set whether this mannequin uses the three-pixel slim player arm model
    public void setSlimSkin(boolean slimSkin) {
        entityData.set(DATA_SLIM_SKIN, slimSkin);
    }

    // Get the pick result
    @Override
    public ItemStack getPickResult() {
        return SupporterHeads.createStack(getOriginalSupporterVariant());
    }

    // Damage the mannequin
    private void causeMannequinDamage(ServerLevel level, DamageSource src, float damage) {
        float health = getHealth() - damage;
        if (health <= 0.5F) {
            breakWithoutMannequinItem(level, src);
            kill();
        } else {
            setHealth(health);
            gameEvent(GameEvent.ENTITY_DAMAGE, src.getEntity());
        }
    }

    // Handle the break by player
    private void breakByPlayer(ServerLevel level, DamageSource src) {
        Block.popResource(level, blockPosition(), createBreakStack());
        breakWithoutMannequinItem(level, src);
    }

    // Create the break stack
    private ItemStack createBreakStack() {
        PlayerMannequinVariant originalVariant = getOriginalSupporterVariant();
        ItemStack stack = SupporterHeads.createStack(originalVariant);
        Component customName = getCustomName();
        if (customName != null && !customName.getString().equals(originalVariant.displayName().getString())) {
            stack.set(DataComponents.CUSTOM_NAME, customName);
        }
        return stack;
    }

    // Handle the break without mannequin item
    private void breakWithoutMannequinItem(ServerLevel level, DamageSource src) {
        playMannequinBrokenSound();
        dropAllDeathLoot(level, src);
        dropEquipmentSlots();
    }

    // Handle the drop equipment slots
    private void dropEquipmentSlots() {
        BlockPos dropPos = blockPosition().above();
        for (EquipmentSlot slot : DROPPED_EQUIPMENT_SLOTS) {
            ItemStack stack = getItemBySlot(slot);
            if (!stack.isEmpty()) {
                Block.popResource(level(), dropPos, stack);
                super.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    // Play the mannequin broken sound
    private void playMannequinBrokenSound() {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_STAND_BREAK, getSoundSource(), 1.0F, 1.0F);
    }

    // Show the mannequin breaking particles
    private void showMannequinBreakingParticles() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()),
                    getX(),
                    getY(0.6666666666666666),
                    getZ(),
                    10,
                    getBbWidth() / 4.0F,
                    getBbHeight() / 4.0F,
                    getBbWidth() / 4.0F,
                    0.05);
        }
    }

    // Check if this should reset kinetic currency reward pose
    private boolean shouldResetKineticCurrencyRewardPose(EquipmentSlot slot, ItemStack prev, ItemStack current) {
        return kineticCurrencyRewardPose
                && slot == EquipmentSlot.MAINHAND
                && isKineticCurrencyDisc(prev)
                && !isKineticCurrencyDisc(current);
    }

    // Check if this is a kinetic currency disc
    private static boolean isKineticCurrencyDisc(ItemStack stack) {
        return CTItems.MUSIC_DISC_KINETIC_CURRENCY != null
                && !stack.isEmpty()
                && stack.is(CTItems.MUSIC_DISC_KINETIC_CURRENCY.get());
    }

    // Check that a remote skin URL remains on Mojang's dedicated texture host
    private static boolean verifiedSkinUrl(String skinUrl) {
        if (skinUrl == null || skinUrl.isBlank() || skinUrl.length() > 2048) return false;
        try {
            URI uri = URI.create(skinUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "textures.minecraft.net".equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null && uri.getPort() == -1;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
