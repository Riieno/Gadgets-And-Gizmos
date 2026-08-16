package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogMath;
import com.rieno.gadgetsandgizmos.lib.discovery.INamedBlockEntity;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSnapshot;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSource;
import com.rieno.gadgetsandgizmos.lib.control.LinkedOrientationSource;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueSignalPacket;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueTransmissionTarget;
import com.rieno.gadgetsandgizmos.lib.control.FrequencyBinding;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Couple;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// Convert local player movement into stable analogue axes for normal and Sable-mounted controls
public class AnalogueJoystickBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation,
    LinkedOrientationSource, DirectionalAnalogSource, MenuProvider, INamedBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double DEFAULT_DEADZONE = 0.12D;
    private static final double DEFAULT_MAX_TILT = 28.0D;
    private static final double DEFAULT_DRAG_SENSITIVITY = 1.0D;
    private static final long PLAYER_DRAG_TIMEOUT_TICKS = 40L;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked bindings
    private final EnumMap<JoystickChannel, FrequencyBinding> bindings = new EnumMap<>(JoystickChannel.class);
    // Tracked transmitters
    private final EnumMap<JoystickChannel, JoystickTransmitter> transmitters = new EnumMap<>(JoystickChannel.class);
    // Current snapshot
    private DirectionalAnalogSnapshot snapshot = DirectionalAnalogSnapshot.ZERO;
    // Local x
    private double localX;
    // Local z
    private double localZ;
    // Current visual local x
    private double visualLocalX;
    // Current visual local z
    private double visualLocalZ;
    // Previous visual local x
    private double previousVisualLocalX;
    // Previous visual local z
    private double previousVisualLocalZ;
    // Current drag sensitivity
    private double dragSensitivity = DEFAULT_DRAG_SENSITIVITY;
    // Current deadzone
    private double deadzone = DEFAULT_DEADZONE;
    // Max tilt in degrees
    private double maxTiltDegrees = DEFAULT_MAX_TILT;
    // Current release mode
    private ReleaseMode releaseMode = ReleaseMode.LATCHED;
    // Selected player input mode
    private InputMode inputMode = InputMode.MOUSE;
    // Tracks whether analogue joystick is held
    private boolean held;
    // Active drag player
    @Nullable
    private UUID activeDragPlayer;
    // Last player drag tick
    private long lastPlayerDragTick = Long.MIN_VALUE;
    // Tracks whether this is registered with link network
    private boolean registeredWithLinkNetwork;
    // Current custom name
    @Nullable
    private String customName;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue joystick
    public AnalogueJoystickBlockEntity(BlockPos pos, BlockState blockState) {
        super(CTBlockEntities.ANALOGUE_JOYSTICK.get(), pos, blockState);
        for (JoystickChannel channel : JoystickChannel.values()) {
            FrequencyBinding binding = new FrequencyBinding(channel.name().toLowerCase(Locale.ROOT));
            bindings.put(channel, binding);
            transmitters.put(channel, new JoystickTransmitter(channel));
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the custom name
    @Override
    public @Nullable String getCustomName() {
        return customName;
    }

    // Set the custom name
    @Override
    public void setCustomName(@Nullable String name) {
        this.customName = (name != null && !name.isBlank()) ? name.strip() : null;
        setChanged();
        sendData();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the analogue joystick
    public static void tick(Level level, BlockPos pos, BlockState state, AnalogueJoystickBlockEntity be) {
        be.tick();
    }

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // Initialize the analogue joystick
    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            registerTransmitters();
            updateAllTransmitterStrengths(true);
            notifyOutputNeighbors();
        }
    }

    // Update the analogue joystick
    @Override
    public void tick() {
        super.tick();

        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (state.getBlock() instanceof AnalogueJoystickBlock
                    && state.hasProperty(AnalogueJoystickBlock.FACE)
                    && state.getValue(AnalogueJoystickBlock.FACE) != AttachFace.FLOOR) {

                BlockState corrected = state.setValue(AnalogueJoystickBlock.FACE, AttachFace.FLOOR);
                if (corrected.canSurvive(level, worldPosition)) {
                    level.setBlock(worldPosition, corrected, 3);
                } else {
                    level.destroyBlock(worldPosition, true);
                }
                return;
            }
            if (held && activeDragPlayer == null) {
                releaseTimedOutDrag();
            } else if (activeDragPlayer != null
                    && level.getGameTime() - lastPlayerDragTick > PLAYER_DRAG_TIMEOUT_TICKS) {
                releaseTimedOutDrag();
            }
        }

        previousVisualLocalX = visualLocalX;
        previousVisualLocalZ = visualLocalZ;
        visualLocalX += (localX - visualLocalX) * 0.35D;
        visualLocalZ += (localZ - visualLocalZ) * 0.35D;
    }

    // Apply the drag input
    public void applyDragInput(float localX, float localZ, boolean held) {
        double clampedX = Mth.clamp(localX, -1.0D, 1.0D);
        double clampedZ = Mth.clamp(localZ, -1.0D, 1.0D);
        DirectionalAnalogSnapshot nextSnapshot = DirectionalAnalogMath.fromSquareLocal(clampedX, clampedZ, deadzone);

        boolean changed = Math.abs(this.localX - nextSnapshot.localX()) > 1.0E-4D
                || Math.abs(this.localZ - nextSnapshot.localZ()) > 1.0E-4D
                || this.held != held
            || !snapshot.equals(nextSnapshot);
        if (!changed) {
            return;
        }

        this.localX = nextSnapshot.localX();
        this.localZ = nextSnapshot.localZ();
        this.snapshot = nextSnapshot;
        this.held = held;
        if (level != null && !level.isClientSide) {
            updateAllTransmitterStrengths(false);
            notifyOutputNeighbors();
        }
        setChanged();
        sendData();
    }

    // Apply the player drag input
    public boolean applyPlayerDragInput(UUID playerId, float localX, float localZ, boolean held) {
        if (playerId == null || level == null || level.isClientSide) {
            return false;
        }
        long now = level.getGameTime();
        boolean expired = activeDragPlayer != null && now - lastPlayerDragTick > PLAYER_DRAG_TIMEOUT_TICKS;
        if (held) {
            if (activeDragPlayer != null && !activeDragPlayer.equals(playerId) && !expired) {
                return false;
            }
            activeDragPlayer = playerId;
            lastPlayerDragTick = now;
            applyDragInput(localX, localZ, true);
            return true;
        }
        if (!playerId.equals(activeDragPlayer)) {
            return false;
        }
        activeDragPlayer = null;
        lastPlayerDragTick = Long.MIN_VALUE;
        applyDragInput(localX, localZ, false);
        return true;
    }

    // Release the timed out drag
    private void releaseTimedOutDrag() {
        activeDragPlayer = null;
        lastPlayerDragTick = Long.MIN_VALUE;
        float releasedX = isMomentaryMode() ? 0.0F : (float) localX;
        float releasedZ = isMomentaryMode() ? 0.0F : (float) localZ;
        applyDragInput(releasedX, releasedZ, false);
    }

    // Reset the input
    public void resetInput() {

        applyDragInput(0.0f, 0.0f, false);
    }

    // Apply the client preview
    public void applyClientPreview(float localX, float localZ, boolean held) {
        this.localX = Mth.clamp(localX, -1.0D, 1.0D);
        this.localZ = Mth.clamp(localZ, -1.0D, 1.0D);
        this.snapshot = DirectionalAnalogMath.fromSquareLocal(this.localX, this.localZ, deadzone);
        this.held = held;
    }

    // Clear the client preview
    public void clearClientPreview() {
        applyClientPreview(0.0f, 0.0f, false);
    }

    // Set the channel binding
    public void setChannelBinding(JoystickChannel channel, ItemStack first, ItemStack second) {
        FrequencyBinding binding = bindings.get(channel);
        if (binding == null) {
            return;
        }

        ItemStack previousFirst = binding.first();
        ItemStack previousSecond = binding.second();
        binding.set(first, second);
        boolean changed = !ItemStack.isSameItemSameComponents(previousFirst, binding.first())
                || !ItemStack.isSameItemSameComponents(previousSecond, binding.second());
        if (!changed) {
            return;
        }

        JoystickTransmitter transmitter = transmitters.get(channel);
        if (level != null && !level.isClientSide && registeredWithLinkNetwork && transmitter != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(resolveLinkLevel(level), transmitter);
        }
        if (level != null && !level.isClientSide && registeredWithLinkNetwork && transmitter != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(resolveLinkLevel(level), transmitter);
        }

        setChanged();
        sendData();
    }

    // Set the deadzone
    public void setDeadzone(float deadzone) {
        double clamped = Mth.clamp(deadzone, 0.0f, 0.95f);
        if (Math.abs(this.deadzone - clamped) < 1.0E-4D) {
            return;
        }
        DirectionalAnalogSnapshot previousSnapshot = snapshot;
        this.deadzone = clamped;
        if (previousSnapshot.equals(DirectionalAnalogMath.fromSquareLocal(localX, localZ, this.deadzone))) {
            syncPersistentConfigChange();
            return;
        }
        applyDragInput((float) localX, (float) localZ, held);
        syncPersistentConfigChange();
    }

    // Set the drag sensitivity
    public void setDragSensitivity(float dragSensitivity) {
        double clamped = Mth.clamp(dragSensitivity, 0.05f, 3.0f);
        if (Math.abs(this.dragSensitivity - clamped) < 1.0E-4D) {
            return;
        }
        this.dragSensitivity = clamped;
        syncPersistentConfigChange();
    }

    // Set the max tilt degrees
    public void setMaxTiltDegrees(float maxTiltDegrees) {
        double clamped = Mth.clamp(maxTiltDegrees, 5.0f, 60.0f);
        if (Math.abs(this.maxTiltDegrees - clamped) < 1.0E-4D) {
            return;
        }
        this.maxTiltDegrees = clamped;
        syncPersistentConfigChange();
    }

    // Get the release mode
    public ReleaseMode getReleaseMode() {
        return releaseMode == null ? ReleaseMode.LATCHED : releaseMode;
    }

    // Check if this is a momentary mode
    public boolean isMomentaryMode() {
        return getReleaseMode() == ReleaseMode.MOMENTARY;
    }

    // Get the selected player input mode
    public InputMode getInputMode() {
        return inputMode == null ? InputMode.MOUSE : inputMode;
    }

    // Check if mouse dragging is enabled
    public boolean acceptsMouseInput() {
        return getInputMode() == InputMode.MOUSE;
    }

    // Check if gamepad dragging is enabled
    public boolean acceptsGamepadInput() {
        return getInputMode() == InputMode.GAMEPAD;
    }

    // Set the selected player input mode
    public void setInputMode(InputMode inputMode) {
        InputMode nextMode = inputMode == null ? InputMode.MOUSE : inputMode;
        if (this.inputMode == nextMode) {
            return;
        }
        this.inputMode = nextMode;
        syncPersistentConfigChange();
    }

    // Set the release mode
    public void setReleaseMode(ReleaseMode releaseMode) {
        ReleaseMode nextMode = releaseMode == null ? ReleaseMode.LATCHED : releaseMode;
        if (this.releaseMode == nextMode) {
            return;
        }
        this.releaseMode = nextMode;

        if (nextMode == ReleaseMode.MOMENTARY && !held) {
            resetInput();
            syncPersistentConfigChange();
            return;
        }
        syncPersistentConfigChange();
    }

    // Sync the persistent config change
    private void syncPersistentConfigChange() {
        setChanged();
        sendData();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Get the frequency first
    public ItemStack getFrequencyFirst(JoystickChannel channel) {
        FrequencyBinding binding = bindings.get(channel);
        return binding == null ? ItemStack.EMPTY : binding.first();
    }

    // Get the frequency second
    public ItemStack getFrequencySecond(JoystickChannel channel) {
        FrequencyBinding binding = bindings.get(channel);
        return binding == null ? ItemStack.EMPTY : binding.second();
    }

    // Get the deadzone
    public float getDeadzone() {
        return (float) deadzone;
    }

    // Get the drag sensitivity
    public float getDragSensitivity() {
        return (float) dragSensitivity;
    }

    // Get the max tilt degrees
    public float getMaxTiltDegrees() {
        return (float) maxTiltDegrees;
    }

    // Get the visual local x
    public double getVisualLocalX(float partialTicks) {
        return Mth.lerp(partialTicks, previousVisualLocalX, visualLocalX);
    }

    // Get the visual local z
    public double getVisualLocalZ(float partialTicks) {
        return Mth.lerp(partialTicks, previousVisualLocalZ, visualLocalZ);
    }

    // Check if this is held
    public boolean isHeld() {
        return held;
    }

    // Get the signal
    public int getSignal(Direction worldDirection) {
        if (worldDirection == null || !worldDirection.getAxis().isHorizontal()) {
            return 0;
        }

        Direction facing = AnalogueJoystickBlock.getLogicalFacing(getBlockState());
        if (worldDirection == facing) {
            return snapshot.forwardRedstone();
        }
        if (worldDirection == facing.getOpposite()) {
            return snapshot.backwardRedstone();
        }
        if (worldDirection == facing.getCounterClockWise()) {
            return snapshot.leftRedstone();
        }
        if (worldDirection == facing.getClockWise()) {
            return snapshot.rightRedstone();
        }
        return 0;
    }

    // Get the directional analog snapshot
    @Override
    public DirectionalAnalogSnapshot getDirectionalAnalogSnapshot() {
        return snapshot;
    }

    // Check if the directional analog is active
    @Override
    public boolean isDirectionalAnalogActive() {
        return held || snapshot.magnitude() > 0.0D;
    }

    // Get the linked direction
    @Override
    public Vec3 getLinkedDirection() {
        double[] angles = getLinkedAnglesRadians();
        if (angles == null) {
            return null;
        }

        Vec3 localSensorDirection = directionFromAngles(angles[0], angles[1]);
        Direction facing = AnalogueJoystickBlock.getLogicalFacing(getBlockState());
        Vec3 forward = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 right = Vec3.atLowerCornerOf(facing.getClockWise().getNormal());

        return right.scale(localSensorDirection.x)
            .add(0.0D, localSensorDirection.y, 0.0D)
                .add(forward.scale(localSensorDirection.z))
                .normalize();
    }

    // Get the linked angles radians
    @Override
    public double[] getLinkedAnglesRadians() {
        if (snapshot.magnitude() <= 1.0E-6D) {
            return null;
        }

        double maxTiltRadians = Math.toRadians(maxTiltDegrees);

        return new double[]{
                -snapshot.localZ() * maxTiltRadians,
                -snapshot.localX() * maxTiltRadians
        };
    }

    // Check if the orientation source is active
    @Override
    public boolean isOrientationSourceActive() {
        return true;
    }

    // Add the goggle tooltip
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean showDetails = CTTooltipHelper.showGoggleDetails(isPlayerSneaking);
        tooltip.add(CTTooltipHelper.title(Component.translatable("block.createthrusters.analogue_joystick")));

        tooltip.add(CTTooltipHelper.line("Release Mode",
                Component.translatable(getReleaseMode().translationKey()).withStyle(ChatFormatting.GREEN)));
        tooltip.add(CTTooltipHelper.line("Output",
                CTTooltipHelper.value(String.format(Locale.ROOT, "%.2f, %.2f", snapshot.localX(), snapshot.localZ()),
                        ChatFormatting.AQUA)));
        if (showDetails) {
            tooltip.add(CTTooltipHelper.line("Forward Output",
                    CTTooltipHelper.value(Integer.toString(JoystickChannel.FORWARD.resolveStrength(snapshot)),
                            ChatFormatting.AQUA)));
            tooltip.add(CTTooltipHelper.line("Backward Output",
                    CTTooltipHelper.value(Integer.toString(JoystickChannel.BACKWARD.resolveStrength(snapshot)),
                            ChatFormatting.AQUA)));
            tooltip.add(CTTooltipHelper.line("Left Output",
                    CTTooltipHelper.value(Integer.toString(JoystickChannel.LEFT.resolveStrength(snapshot)), ChatFormatting.AQUA)));
            tooltip.add(CTTooltipHelper.line("Right Output",
                    CTTooltipHelper.value(Integer.toString(JoystickChannel.RIGHT.resolveStrength(snapshot)),
                            ChatFormatting.AQUA)));
            tooltip.add(CTTooltipHelper.line("Deadzone",
                    CTTooltipHelper.value(CTTooltipHelper.percent(deadzone), ChatFormatting.YELLOW)));
                tooltip.add(CTTooltipHelper.line("Sensitivity",
                    CTTooltipHelper.value(String.format(Locale.ROOT, "%.2fx", dragSensitivity), ChatFormatting.YELLOW)));
            tooltip.add(CTTooltipHelper.line("Max Tilt",
                    CTTooltipHelper.value(CTTooltipHelper.degrees(maxTiltDegrees), ChatFormatting.YELLOW)));
        }
        return true;
    }

    // Get the direction from angles
    private static Vec3 directionFromAngles(double xAngle, double zAngle) {
        return new Vec3(Math.sin(zAngle), -Math.cos(xAngle) - Math.cos(zAngle) + 1.0D, Math.sin(xAngle)).normalize();
    }

    // Get the render bounding box
    @Override
    public AABB getRenderBoundingBox() {
        return AABB.ofSize(worldPosition.getCenter(), 1.5D, 1.5D, 1.5D);
    }

    // Handle the destroyed event
    public void onDestroyed() {
        unregisterTransmitters();
    }

    // Create the menu
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new AnalogueJoystickMenu(containerId, playerInventory, this);
    }

    // Get the display name
    @Override
    public Component getDisplayName() {
        return Component.translatable("createthrusters.analogue_joystick.config.title");
    }

    // Check if the player can use this
    @Override
    public boolean canPlayerUse(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64.0D;
    }

    // Send the menu data
    public void sendToMenu(RegistryFriendlyByteBuf buffer) {

        com.rieno.gadgetsandgizmos.lib.menuconfig.MenuOpenHeader.encode(
                buffer, worldPosition,
                com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper.getContainingSubLevelId(this));
        buffer.writeFloat(getDragSensitivity());
        buffer.writeFloat(getDeadzone());
        buffer.writeFloat(getMaxTiltDegrees());
        buffer.writeUtf(getReleaseMode().name());
        buffer.writeUtf(getInputMode().name());
    }

    // Write the analogue joystick safely
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        tag.putFloat("DragSensitivity", (float) dragSensitivity);
        tag.putFloat("Deadzone", (float) deadzone);
        tag.putFloat("MaxTiltDegrees", (float) maxTiltDegrees);
        tag.putString("ReleaseMode", getReleaseMode().name());
        tag.putString("InputMode", getInputMode().name());
        if (customName != null) {
            tag.putString("CustomName", customName);
        }
        for (JoystickChannel channel : JoystickChannel.values()) {
            FrequencyBinding binding = bindings.get(channel);
            if (binding != null) {
                tag.put(channel.name() + "Binding", binding.toTag(provider));
            }
        }
    }

    // Write the analogue joystick
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putDouble("LocalX", localX);
        tag.putDouble("LocalZ", localZ);
        tag.putBoolean("Held", held);
        tag.putFloat("DragSensitivity", (float) dragSensitivity);
        tag.putFloat("Deadzone", (float) deadzone);
        tag.putFloat("MaxTiltDegrees", (float) maxTiltDegrees);
        tag.putString("ReleaseMode", getReleaseMode().name());
        tag.putString("InputMode", getInputMode().name());
        if (customName != null) {
            tag.putString("CustomName", customName);
        }
        for (JoystickChannel channel : JoystickChannel.values()) {
            FrequencyBinding binding = bindings.get(channel);
            if (binding == null) {
                continue;
            }
            tag.put(channel.name() + "Binding", binding.toTag(provider));
        }
    }

    // Read the analogue joystick
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        localX = tag.getDouble("LocalX");
        localZ = tag.getDouble("LocalZ");
        held = tag.getBoolean("Held");
        dragSensitivity = tag.contains("DragSensitivity") ? tag.getFloat("DragSensitivity") : DEFAULT_DRAG_SENSITIVITY;
        deadzone = tag.contains("Deadzone") ? tag.getFloat("Deadzone") : DEFAULT_DEADZONE;
        maxTiltDegrees = tag.contains("MaxTiltDegrees") ? tag.getFloat("MaxTiltDegrees") : DEFAULT_MAX_TILT;
        releaseMode = tag.contains("ReleaseMode") ? ReleaseMode.read(tag.getString("ReleaseMode")) : ReleaseMode.LATCHED;
        inputMode = tag.contains("InputMode") ? InputMode.read(tag.getString("InputMode")) : InputMode.MOUSE;
        customName = tag.contains("CustomName") ? tag.getString("CustomName") : null;

        snapshot = DirectionalAnalogMath.fromSquareLocal(localX, localZ, deadzone);
        for (JoystickChannel channel : JoystickChannel.values()) {
            FrequencyBinding binding = bindings.get(channel);
            if (binding == null) {
                continue;
            }
            if (tag.contains(channel.name() + "Binding")) {
                binding.read(tag.getCompound(channel.name() + "Binding"), provider);
            } else {
                binding.set(
                        tag.contains(channel.name() + "First") ? ItemStack.parseOptional(provider, tag.getCompound(channel.name() + "First")) : ItemStack.EMPTY,
                        tag.contains(channel.name() + "Second") ? ItemStack.parseOptional(provider, tag.getCompound(channel.name() + "Second")) : ItemStack.EMPTY);
            }
        }
    }

    // Register the transmitters
    private void registerTransmitters() {
        if (registeredWithLinkNetwork || level == null || level.isClientSide) {
            return;
        }
        Level linkLevel = resolveLinkLevel(level);
        for (JoystickTransmitter transmitter : transmitters.values()) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(linkLevel, transmitter);
        }
        registeredWithLinkNetwork = true;
    }

    // Remove the transmitters
    private void unregisterTransmitters() {
        if (!registeredWithLinkNetwork || level == null || level.isClientSide) {
            return;
        }
        Level linkLevel = resolveLinkLevel(level);
        for (JoystickTransmitter transmitter : transmitters.values()) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(linkLevel, transmitter);
        }
        registeredWithLinkNetwork = false;
    }

    // Update all transmitter strengths
    private void updateAllTransmitterStrengths(boolean force) {
        if (level == null || level.isClientSide) {
            return;
        }
        Level linkLevel = resolveLinkLevel(level);
        for (JoystickChannel channel : JoystickChannel.values()) {
            JoystickTransmitter transmitter = transmitters.get(channel);
            if (transmitter == null) {
                continue;
            }
            int nextStrength = channel.resolveStrength(snapshot);
            if (!force && transmitter.getTransmittedStrength() == nextStrength) {
                continue;
            }
            transmitter.accept(new AnalogueSignalPacket(channel.name().toLowerCase(Locale.ROOT), nextStrength / 15.0F,
                    level.getGameTime(), worldPosition.toShortString(), "analogue_joystick"));
            Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(linkLevel, transmitter);
        }
    }

    // Resolve the link level
    private Level resolveLinkLevel(Level currentLevel) {
        if (currentLevel == null) {
            return level;
        }
        try {
            Object viaGetter = currentLevel.getClass().getMethod("getLevel").invoke(currentLevel);
            if (viaGetter instanceof Level worldLevel) {
                return worldLevel;
            }
        } catch (Exception ignored) {
        }
        try {
            if (currentLevel.getServer() != null) {
                net.minecraft.server.level.ServerLevel byDimension = currentLevel.getServer().getLevel(currentLevel.dimension());
                if (byDimension != null) {
                    return byDimension;
                }
                net.minecraft.server.level.ServerLevel overworld = currentLevel.getServer().overworld();
                if (overworld != null) {
                    return overworld;
                }
            }
        } catch (Exception ignored) {
        }
        return currentLevel;
    }

    // Notify the output neighbors
    private void notifyOutputNeighbors() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        level.updateNeighborsAt(worldPosition, state.getBlock());
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            level.updateNeighborsAt(worldPosition.relative(dir), state.getBlock());
        }
    }

    // Define the joystick channel values
    public enum JoystickChannel {
        FORWARD("createthrusters.analogue_joystick.channel.forward") {
            // Resolve the strength
            @Override
            public int resolveStrength(DirectionalAnalogSnapshot snapshot) {
                return snapshot.forwardRedstone();
            }
        },
        BACKWARD("createthrusters.analogue_joystick.channel.backward") {
            // Resolve the strength
            @Override
            public int resolveStrength(DirectionalAnalogSnapshot snapshot) {
                return snapshot.backwardRedstone();
            }
        },
        LEFT("createthrusters.analogue_joystick.channel.left") {
            // Resolve the strength
            @Override
            public int resolveStrength(DirectionalAnalogSnapshot snapshot) {
                return snapshot.leftRedstone();
            }
        },
        RIGHT("createthrusters.analogue_joystick.channel.right") {
            // Resolve the strength
            @Override
            public int resolveStrength(DirectionalAnalogSnapshot snapshot) {
                return snapshot.rightRedstone();
            }
        };

        // Translation key
        private final String translationKey;

        // Initialize the joystick channel
        JoystickChannel(String translationKey) {
            this.translationKey = translationKey;
        }

        // Get the translation key
        public String getTranslationKey() {
            return translationKey;
        }

        // Resolve the strength
        public abstract int resolveStrength(DirectionalAnalogSnapshot snapshot);
    }

    // Define the release mode values
    public enum ReleaseMode {
        MOMENTARY("createthrusters.analogue_joystick.release_mode.momentary"),
        LATCHED("createthrusters.analogue_joystick.release_mode.latched");

        // Translation key
        private final String translationKey;

        // Initialize the release mode
        ReleaseMode(String translationKey) {
            this.translationKey = translationKey;
        }

        // Get the translation key
        public String translationKey() {
            return translationKey;
        }

        // Read the release mode
        public static ReleaseMode read(String name) {
            if (name == null || name.isBlank()) {
                return LATCHED;
            }
            try {
                return ReleaseMode.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return LATCHED;
            }
        }
    }

    // Define the player input mode values
    public enum InputMode {
        MOUSE("createthrusters.analogue_joystick.input_mode.mouse"),
        GAMEPAD("createthrusters.analogue_joystick.input_mode.gamepad");

        // Translation key
        private final String translationKey;

        // Initialize the player input mode
        InputMode(String translationKey) {
            this.translationKey = translationKey;
        }

        // Get the translation key
        public String translationKey() {
            return translationKey;
        }

        // Read the player input mode
        public static InputMode read(String name) {
            if (name == null || name.isBlank()) {
                return MOUSE;
            }
            try {
                return InputMode.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return MOUSE;
            }
        }
    }

    // Handle the joystick transmitter
    private class JoystickTransmitter implements IRedstoneLinkable, AnalogueTransmissionTarget {
        // Channel
        private final JoystickChannel channel;
        // Transmitted strength
        private int transmittedStrength;

        // Initialize the joystick transmitter
        private JoystickTransmitter(JoystickChannel channel) {
            this.channel = channel;
        }

        // Get the channel id
        @Override
        public String channelId() {
            return channel.name().toLowerCase(Locale.ROOT);
        }

        // Accept the joystick transmitter
        @Override
        public void accept(AnalogueSignalPacket packet) {
            transmittedStrength = packet.redstoneStrength();
        }

        // Get the transmitted strength
        @Override
        public int getTransmittedStrength() {
            return transmittedStrength;
        }

        // Set the received strength
        @Override
        public void setReceivedStrength(int networkPower) {
        }

        // Check if this is listening
        @Override
        public boolean isListening() {
            return false;
        }

        // Check if this is alive
        @Override
        public boolean isAlive() {

            return level != null && !isRemoved();
        }

        // Get the network key
        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            FrequencyBinding binding = bindings.get(channel);
            if (binding == null) {
                return Couple.create(RedstoneLinkNetworkHandler.Frequency.EMPTY, RedstoneLinkNetworkHandler.Frequency.EMPTY);
            }
            return Couple.create(
                    RedstoneLinkNetworkHandler.Frequency.of(binding.first()),
                    RedstoneLinkNetworkHandler.Frequency.of(binding.second()));
        }

        // Get the location
        @Override
        public BlockPos getLocation() {

            Vec3 projected = SimulatedHelper.toContainingWorldPosition(AnalogueJoystickBlockEntity.this,
                    Vec3.atCenterOf(worldPosition));
            if (projected == null) {
                return worldPosition;
            }
            return BlockPos.containing(projected);
        }
    }
}
