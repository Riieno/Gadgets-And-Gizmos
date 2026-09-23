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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Optional;
import java.util.UUID;

// Keep a mannequin's player skin, pose and equipment state synchronized
public class PlayerMannequinEntity extends ArmorStand {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String VARIANT_TAG = "PlayerMannequinVariant";
    private static final String KINETIC_CURRENCY_REWARD_POSE_TAG = "KineticCurrencyRewardPose";
    private static final String WORKER_POD_TAG = "WorkerPod";
    private static final String WORKER_INVENTORY_TAG = "WorkerInventory";
    private static final String WORKER_CURIOS_TAG = "WorkerCurios";
    private static final String WORKER_CARRY_PROP_TAG = "WorkerCarryProp";
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
    private static final EntityDataAccessor<Optional<UUID>> DATA_WORKER_POD =
            SynchedEntityData.defineId(PlayerMannequinEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Byte> DATA_WORKER_ANIMATION =
            SynchedEntityData.defineId(PlayerMannequinEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_WORKER_INTERACTION_TICKS =
            SynchedEntityData.defineId(PlayerMannequinEntity.class, EntityDataSerializers.INT);
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether kinetic currency reward pose is set
    private boolean kineticCurrencyRewardPose;
    // Persistent shulker-sized worker inventory
    private final ItemStackHandler workerInventory = new ItemStackHandler(27);
    // Persistent slots exposed when Curios is installed
    private final ItemStackHandler workerCurios = new ItemStackHandler(6);

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
        return ArmorStand.createAttributes().add(NeoForgeMod.CREATIVE_FLIGHT);
    }

    // Check whether equipped modifiers grant this worker NeoForge-standard creative flight.
    public boolean hasWorkerFlight() {
        var flight = getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        return flight != null && flight.getValue() > 0.0D;
    }

    // Define the synched data
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, PlayerMannequinVariants.DEFAULT_ID);
        builder.define(DATA_WORKER_POD, Optional.empty());
        builder.define(DATA_WORKER_ANIMATION, (byte) WorkerAnimation.IDLE.ordinal());
        builder.define(DATA_WORKER_INTERACTION_TICKS, 0);
    }

    // Add the additional save data
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(VARIANT_TAG, getVariant().id());
        if (kineticCurrencyRewardPose) {
            tag.putBoolean(KINETIC_CURRENCY_REWARD_POSE_TAG, true);
        }
        assignedWorkerPod().ifPresent(id -> tag.putUUID(WORKER_POD_TAG, id));
        tag.put(WORKER_INVENTORY_TAG, workerInventory.serializeNBT(registryAccess()));
        tag.put(WORKER_CURIOS_TAG, workerCurios.serializeNBT(registryAccess()));
    }

    // Read the additional save data
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getString(VARIANT_TAG));
        kineticCurrencyRewardPose = tag.getBoolean(KINETIC_CURRENCY_REWARD_POSE_TAG);
        setAssignedWorkerPod(tag.hasUUID(WORKER_POD_TAG) ? tag.getUUID(WORKER_POD_TAG) : null);
        if (tag.contains(WORKER_INVENTORY_TAG, Tag.TAG_COMPOUND)) {
            workerInventory.deserializeNBT(registryAccess(), tag.getCompound(WORKER_INVENTORY_TAG));
        }
        if (tag.contains(WORKER_CURIOS_TAG, Tag.TAG_COMPOUND)) {
            workerCurios.deserializeNBT(registryAccess(), tag.getCompound(WORKER_CURIOS_TAG));
        }
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

    // Tick the short worker interaction animation
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && entityData.get(DATA_WORKER_INTERACTION_TICKS) > 0) {
            entityData.set(DATA_WORKER_INTERACTION_TICKS,
                    entityData.get(DATA_WORKER_INTERACTION_TICKS) - 1);
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
            breakWithoutMannequinItem(serverLevel, source);
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
    }

    // Set the variant
    public void setVariant(PlayerMannequinVariant variant) {
        setVariant(variant == null ? PlayerMannequinVariants.DEFAULT_ID : variant.id());
    }

    // Get the visual carry prop in the worker's main hand.
    public ItemStack workerCarryProp() {
        return getItemBySlot(EquipmentSlot.MAINHAND);
    }

    // Get the persistent worker inventory used by the logistics runtime and player inventory menu.
    public ItemStackHandler workerInventory() {
        return workerInventory;
    }

    // Get the persistent Curios-compatible worker slots.
    public ItemStackHandler workerCurios() {
        return workerCurios;
    }

    // Set the non-recoverable visual prop for active worker cargo.
    public void setWorkerCarryProp(ItemStack stack) {
        ItemStack prop = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!prop.isEmpty()) {
            CompoundTag data = prop.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            data.putBoolean(WORKER_CARRY_PROP_TAG, true);
            prop.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        }
        if (!ItemStack.matches(getItemBySlot(EquipmentSlot.MAINHAND), prop)) {
            setItemSlot(EquipmentSlot.MAINHAND, prop);
        }
    }

    // Check whether the main hand is occupied by a worker-only visual prop.
    public boolean hasWorkerCarryProp() {
        ItemStack stack = workerCarryProp();
        return !stack.isEmpty() && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean(WORKER_CARRY_PROP_TAG);
    }

    // Get the Worker Pod currently responsible for this mannequin
    public Optional<UUID> assignedWorkerPod() {
        return entityData.get(DATA_WORKER_POD);
    }

    // Assign or release this mannequin from one Worker Pod
    public void setAssignedWorkerPod(UUID podId) {
        entityData.set(DATA_WORKER_POD, Optional.ofNullable(podId));
    }

    // Get the current worker animation
    public WorkerAnimation workerAnimation() {
        if (entityData.get(DATA_WORKER_INTERACTION_TICKS) > 0) return WorkerAnimation.INTERACT;
        return WorkerAnimation.byId(entityData.get(DATA_WORKER_ANIMATION));
    }

    // Set the locomotion or carrying animation
    public void setWorkerAnimation(WorkerAnimation animation) {
        WorkerAnimation resolved = animation == null ? WorkerAnimation.IDLE : animation;
        byte value = (byte) resolved.ordinal();
        if (entityData.get(DATA_WORKER_ANIMATION) != value) entityData.set(DATA_WORKER_ANIMATION, value);
    }

    // Start a visible pickup or placement animation
    public void startWorkerInteraction() {
        entityData.set(DATA_WORKER_INTERACTION_TICKS, 10);
    }

    // Clear transient worker presentation
    public void clearWorkerPresentation() {
        setWorkerAnimation(WorkerAnimation.IDLE);
    }

    // Store the worker-specific mannequin animation
    public enum WorkerAnimation {
        IDLE,
        WALK,
        CARRY_IDLE,
        CARRY_WALK,
        INTERACT;

        // Resolve a serialized animation id
        private static WorkerAnimation byId(byte id) {
            int index = Byte.toUnsignedInt(id);
            return index < values().length ? values()[index] : IDLE;
        }
    }

    // Get the pick result
    @Override
    public ItemStack getPickResult() {
        return SupporterHeads.createStack(getVariant());
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
        ItemStack stack = SupporterHeads.createStack(getVariant());
        Component customName = getCustomName();
        if (customName != null && !customName.getString().equals(getVariant().displayName().getString())) {
            stack.set(DataComponents.CUSTOM_NAME, customName);
        }
        return stack;
    }

    // Handle the break without mannequin item
    private void breakWithoutMannequinItem(ServerLevel level, DamageSource src) {
        playMannequinBrokenSound();
        if (hasWorkerCarryProp()) super.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        dropAllDeathLoot(level, src);
        dropEquipmentSlots();
    }

    // Handle the drop equipment slots
    private void dropEquipmentSlots() {
        assignedWorkerPod().ifPresent(podId -> WorkerPodBlockEntity.workerDestroyed(podId, getUUID()));
        setAssignedWorkerPod(null);
        BlockPos dropPos = blockPosition().above();
        for (EquipmentSlot slot : DROPPED_EQUIPMENT_SLOTS) {
            ItemStack stack = getItemBySlot(slot);
            if (slot == EquipmentSlot.MAINHAND && hasWorkerCarryProp()) {
                super.setItemSlot(slot, ItemStack.EMPTY);
                continue;
            }
            if (!stack.isEmpty()) {
                Block.popResource(level(), dropPos, stack);
                super.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        dropWorkerInventory(workerInventory, dropPos);
        dropWorkerInventory(workerCurios, dropPos);
    }

    // Play the mannequin broken sound
    private void playMannequinBrokenSound() {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_STAND_BREAK, getSoundSource(), 1.0F, 1.0F);
    }

    // Drop every persisted worker inventory stack when its mannequin is broken.
    private void dropWorkerInventory(ItemStackHandler inventory, BlockPos dropPos) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) Block.popResource(level(), dropPos, stack);
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
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
}
