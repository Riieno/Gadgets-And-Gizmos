package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerArea;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerNoEntryBoundary;
import com.rieno.gadgetsandgizmos.lib.zipline.ZiplineRider;
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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Keep a mannequin's player skin, pose and equipment state synchronized
public class PlayerMannequinEntity extends ArmorStand implements ZiplineRider {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final float SCALE = 1.0F;
    public static final float POD_SCALE = 0.75F;
    public static final float WIDTH = 0.6F;
    public static final float HEIGHT = 1.8F;
    public static final float EYE_HEIGHT = 1.62F;
    private static final String VARIANT_TAG = "PlayerMannequinVariant";
    private static final String ORIGINAL_VARIANT_TAG = "OriginalSupporterVariant";
    private static final String REMOTE_SKIN_URL_TAG = "RemoteSkinUrl";
    private static final String STEVE_SKIN_TAG = "SteveSkin";
    private static final String SLIM_SKIN_TAG = "SlimSkin";
    private static final String KINETIC_CURRENCY_REWARD_POSE_TAG = "KineticCurrencyRewardPose";
    private static final String WORKER_POD_TAG = "WorkerPod";
    private static final String WORKER_HOUSED_TAG = "WorkerHoused";
    private static final String WORKER_INVENTORY_TAG = "WorkerInventory";
    private static final String WORKER_CURIOS_TAG = "WorkerCurios";
    private static final String WORKER_CARRY_PROP_TAG = "WorkerCarryProp";
    private static final String ZIPLINE_RIDING_TAG = "ZiplineRiding";
    private static final String ZIPLINE_PREVIOUS_GRAVITY_TAG = "ZiplinePreviousNoGravity";
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
    private static final EntityDataAccessor<Optional<UUID>> DATA_WORKER_POD =
            SynchedEntityData.defineId(PlayerMannequinEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_WORKER_HOUSED =
            SynchedEntityData.defineId(PlayerMannequinEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_WORKER_ANIMATION =
            SynchedEntityData.defineId(PlayerMannequinEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_WORKER_INTERACTION_TICKS =
            SynchedEntityData.defineId(PlayerMannequinEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ZIPLINE_RIDING =
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
    // Persistent shulker-sized worker inventory
    private final ItemStackHandler workerInventory = new ItemStackHandler(27);
    // Persistent worker Tools slots (legacy NBT key retained for existing worlds)
    private final ItemStackHandler workerCurios = new ItemStackHandler(6);
    private Vec3 lastWorkerPositionOutsideNoEntry;
    private boolean ziplinePreviousNoGravity;

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
        return ArmorStand.createAttributes().add(NeoForgeMod.CREATIVE_FLIGHT).add(Attributes.SCALE);
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
        builder.define(DATA_REMOTE_SKIN_URL, "");
        builder.define(DATA_STEVE_SKIN, false);
        builder.define(DATA_SLIM_SKIN, false);
        builder.define(DATA_WORKER_POD, Optional.empty());
        builder.define(DATA_WORKER_HOUSED, false);
        builder.define(DATA_WORKER_ANIMATION, (byte) WorkerAnimation.IDLE.ordinal());
        builder.define(DATA_WORKER_INTERACTION_TICKS, 0);
        builder.define(DATA_ZIPLINE_RIDING, false);
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
        assignedWorkerPod().ifPresent(id -> tag.putUUID(WORKER_POD_TAG, id));
        if (isHousedInWorkerPod()) tag.putBoolean(WORKER_HOUSED_TAG, true);
        if (isZiplineRiding()) {
            tag.putBoolean(ZIPLINE_RIDING_TAG, true);
            tag.putBoolean(ZIPLINE_PREVIOUS_GRAVITY_TAG, ziplinePreviousNoGravity);
        }
        tag.put(WORKER_INVENTORY_TAG, workerInventory.serializeNBT(registryAccess()));
        tag.put(WORKER_CURIOS_TAG, workerCurios.serializeNBT(registryAccess()));
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
        setAssignedWorkerPod(tag.hasUUID(WORKER_POD_TAG) ? tag.getUUID(WORKER_POD_TAG) : null);
        setHousedInWorkerPod(tag.getBoolean(WORKER_HOUSED_TAG));
        entityData.set(DATA_ZIPLINE_RIDING, tag.getBoolean(ZIPLINE_RIDING_TAG));
        ziplinePreviousNoGravity = tag.getBoolean(ZIPLINE_PREVIOUS_GRAVITY_TAG);
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
        enforceWorkerNoEntry();
        if (!level().isClientSide && entityData.get(DATA_WORKER_INTERACTION_TICKS) > 0) {
            entityData.set(DATA_WORKER_INTERACTION_TICKS,
                    entityData.get(DATA_WORKER_INTERACTION_TICKS) - 1);
        }
    }

    public boolean isZiplineRiding() {
        return entityData.get(DATA_ZIPLINE_RIDING);
    }

    @Override
    public void ziplineAttached() {
        if (isZiplineRiding()) return;
        ziplinePreviousNoGravity = isNoGravity();
        entityData.set(DATA_ZIPLINE_RIDING, true);
        setNoGravity(true);
    }

    @Override
    public void ziplineDetached() {
        if (!isZiplineRiding()) return;
        entityData.set(DATA_ZIPLINE_RIDING, false);
        setNoGravity(ziplinePreviousNoGravity);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void ziplineMoved(Vec3 gripPosition, Vec3 travelDirection) {
        ziplineAttached();
        Vec3 feet = gripPosition.add(0.0D, -(HEIGHT + 0.5D * getScale()), 0.0D);
        setPos(feet.x, feet.y, feet.z);
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0.0F;
        if (travelDirection != null && travelDirection.horizontalDistanceSqr() > 1.0E-6D) {
            float yaw = (float) Math.toDegrees(Math.atan2(-travelDirection.x, travelDirection.z));
            setYRot(yaw);
            setYBodyRot(yaw);
        }
    }

    // Reject direct moves and teleports into a managed worker exclusion area
    @Override
    public void setPos(double x, double y, double z){
        Vec3 requested = new Vec3(x, y, z);
        List<WorkerArea> areas = workerNoEntryAreas();
        if(areas.isEmpty()){
            super.setPos(x, y, z);
            return;
        }
        Vec3 allowed = outsideNoEntry(requested, areas);
        super.setPos(allowed.x, allowed.y, allowed.z);
        if(!allowed.equals(requested)) setDeltaMovement(Vec3.ZERO);
        if(!WorkerNoEntryBoundary.intersects(getBoundingBox(), areas))
            lastWorkerPositionOutsideNoEntry = position();
    }

    // Correct collision and gravity movement even when it bypasses scripted worker navigation
    @Override
    public void move(MoverType type, Vec3 movement){
        Vec3 before = position();
        AABB beforeBounds = getBoundingBox();
        List<WorkerArea> areas = workerNoEntryAreas();
        Vec3 permitted = movement;
        if(!areas.isEmpty() && !WorkerNoEntryBoundary.intersects(beforeBounds, areas)){
            double blocked = WorkerNoEntryBoundary.firstBlockedFraction(beforeBounds, movement, areas);
            if(blocked < 1.0D){
                double safe = Math.max(0.0D, blocked - 0.001D / Math.max(movement.length(), 0.001D));
                permitted = movement.scale(safe);
            }
        }
        super.move(type, permitted);
        if(!areas.isEmpty() && !WorkerNoEntryBoundary.intersects(beforeBounds, areas)){
            Vec3 actual = position().subtract(before);
            double blocked = WorkerNoEntryBoundary.firstBlockedFraction(beforeBounds, actual, areas);
            if(blocked < 1.0D){
                double safe = Math.max(0.0D, blocked - 0.001D / Math.max(actual.length(), 0.001D));
                Vec3 allowed = before.add(actual.scale(safe));
                super.setPos(allowed.x, allowed.y, allowed.z);
                setDeltaMovement(Vec3.ZERO);
            }
        }
        enforceWorkerNoEntry();
    }

    private void enforceWorkerNoEntry(){
        List<WorkerArea> areas = workerNoEntryAreas();
        if(areas.isEmpty()) return;
        if(!WorkerNoEntryBoundary.intersects(getBoundingBox(), areas)){
            lastWorkerPositionOutsideNoEntry = position();
            return;
        }
        Vec3 allowed = outsideNoEntry(position(), areas);
        super.setPos(allowed.x, allowed.y, allowed.z);
        setDeltaMovement(Vec3.ZERO);
        if(!WorkerNoEntryBoundary.intersects(getBoundingBox(), areas))
            lastWorkerPositionOutsideNoEntry = position();
    }

    private Vec3 outsideNoEntry(Vec3 requested, List<WorkerArea> areas){
        AABB bounds = getBoundingBox().move(requested.subtract(position()));
        if(!WorkerNoEntryBoundary.intersects(bounds, areas)) return requested;
        Vec3 current = position();
        if(requested.distanceToSqr(current) <= 4.0D
                && !WorkerNoEntryBoundary.intersects(getBoundingBox(), areas)) return current;
        Vec3 exit = WorkerNoEntryBoundary.nearestExit(requested, bounds, areas, candidate -> {
            BlockPos pos = BlockPos.containing(candidate);
            return level().isLoaded(pos) && level().getWorldBorder().isWithinBounds(pos)
                    && level().noCollision(this, bounds.move(candidate.subtract(requested)));
        });
        if(exit != null) return exit;
        if(lastWorkerPositionOutsideNoEntry != null
                && !WorkerNoEntryBoundary.intersects(getBoundingBox().move(
                lastWorkerPositionOutsideNoEntry.subtract(current)), areas))
            return lastWorkerPositionOutsideNoEntry;
        exit = WorkerNoEntryBoundary.nearestExit(requested, bounds, areas, candidate -> true);
        return exit == null ? current : exit;
    }

    private List<WorkerArea> workerNoEntryAreas(){
        if(entityData == null || !(level() instanceof ServerLevel)) return List.of();
        return assignedWorkerPod().map(podId -> WorkerPodBlockEntity.noEntryBounds(podId, getUUID()))
                .orElse(List.of());
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

    // Get the visual carry prop in the worker's main hand.
    public ItemStack workerCarryProp() {
        return getItemBySlot(EquipmentSlot.MAINHAND);
    }

    // Get the persistent worker inventory used by the logistics runtime and player inventory menu.
    public ItemStackHandler workerInventory() {
        return workerInventory;
    }

    // Get the persistent tool slots. Keep the original saved tag and accessor for existing workers.
    public ItemStackHandler workerTools() {
        return workerCurios;
    }

    // Compatibility accessor for saved workers and external callers.
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
        if (podId == null) setHousedInWorkerPod(false);
    }

    // Check whether this worker is stored inside a Worker Pod
    public boolean isHousedInWorkerPod() {
        return entityData.get(DATA_WORKER_HOUSED);
    }

    // Resize this worker only while it is physically stored inside a Worker Pod
    public void setHousedInWorkerPod(boolean housed) {
        if (entityData.get(DATA_WORKER_HOUSED) == housed) return;
        entityData.set(DATA_WORKER_HOUSED, housed);
        var scale = getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(housed ? POD_SCALE : SCALE);
        refreshDimensions();
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
