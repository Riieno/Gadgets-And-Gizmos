package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.control.FrequencyBinding;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuOpenHeader;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.infrastructure.config.AllConfigs;
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
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Add configurable redstone direction changes to the bidirectional gearbox control path
public class BiDirectionalGearshiftBlockEntity extends BiDirectionalGearboxBlockEntity implements MenuProvider {
    private static final int AXIS_ROLE_COLOR_MAPPING_VERSION = 1;
    private static final String PRIMARY_CW_PORT = "primary_orange_cw";
    private static final String PRIMARY_CCW_PORT = "primary_orange_ccw";
    private static final String SECONDARY_CW_PORT = "secondary_cyan_cw";
    private static final String SECONDARY_CCW_PORT = "secondary_cyan_ccw";
    private static final String PRIMARY_MODE_PORT = "primary_orange_operation_mode";
    private static final String SECONDARY_MODE_PORT = "secondary_cyan_operation_mode";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current primary mode
    private AxisControlMode primaryMode = AxisControlMode.PASSTHROUGH;
    // Current secondary mode
    private AxisControlMode secondaryMode = AxisControlMode.PASSTHROUGH;
    // Local mode
    private LocalControlMode localMode = LocalControlMode.BOTH;
    // Persistent ACC direction controls
    private boolean graphPrimaryClockwise;
    private boolean graphPrimaryCounterClockwise;
    private boolean graphSecondaryClockwise;
    private boolean graphSecondaryCounterClockwise;
    // Tracked frequency bindings
    private final EnumMap<AxisRole, EnumMap<RotationChannel, FrequencyBinding>> frequencyBindings = createFrequencyBindings();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the bi directional gearshift
    public BiDirectionalGearshiftBlockEntity(BlockPos pos, BlockState blockState) {
        super(CTBlockEntities.BI_DIRECTIONAL_GEARSHIFT.get(), pos, blockState);
        setOperationMode(OperationMode.PASSTHROUGH);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the primary lane axis
    @Override
    public Direction.Axis getPrimaryLaneAxis() {
        return BiDirectionalGearshiftBlock.getCyanLaneAxis(getBlockState());
    }

    // Get the secondary lane axis
    @Override
    public Direction.Axis getSecondaryLaneAxis() {
        return BiDirectionalGearshiftBlock.getOrangeLaneAxis(getBlockState());
    }

    // Check if this should add operation mode scroll behaviour
    @Override
    protected boolean shouldAddOperationModeScrollBehaviour() {
        return false;
    }

    // Check if this should use redstone reverse
    @Override
    protected boolean shouldUseRedstoneReverse() {
        return false;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the bi directional gearshift
    @Override
    public void tick() {
        if (getOperationMode() != OperationMode.PASSTHROUGH) {
            setOperationMode(OperationMode.PASSTHROUGH);
        }
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        updateDirectionalLane(AxisRole.PRIMARY);
        updateDirectionalLane(AxisRole.SECONDARY);
    }

    // Update the directional lane
    private void updateDirectionalLane(AxisRole role) {
        Direction.Axis axis = getLaneAxis(role);
        if (getAxisMode(role) == AxisControlMode.PASSTHROUGH) {
            if (getLaneMode(axis) == LaneMode.DISABLED) {
                setLaneMode(axis, LaneMode.STRAIGHT);
            }
            return;
        }

        int forwardSignal = Math.max(queryWirelessSignal(getFrequencyBinding(role, RotationChannel.CLOCKWISE)),
                getLocalSignal(role, RotationChannel.CLOCKWISE));
        int reverseSignal = Math.max(queryWirelessSignal(getFrequencyBinding(role, RotationChannel.COUNTER_CLOCKWISE)),
                getLocalSignal(role, RotationChannel.COUNTER_CLOCKWISE));
        if (isGraphChannelActive(role, RotationChannel.CLOCKWISE)) {
            forwardSignal = 15;
        }
        if (isGraphChannelActive(role, RotationChannel.COUNTER_CLOCKWISE)) {
            reverseSignal = 15;
        }
        LaneMode targetMode;
        if (forwardSignal <= 0 && reverseSignal <= 0) {
            targetMode = LaneMode.DISABLED;
        } else {
            targetMode = reverseSignal > forwardSignal ? LaneMode.REVERSED : LaneMode.STRAIGHT;
        }
        if (getLaneMode(axis) != targetMode) {
            setLaneMode(axis, targetMode);
        }
    }

    // Get the local signal
    private int getLocalSignal(AxisRole role, RotationChannel channel) {
        if (level == null || !localMode.controls(role)) {
            return 0;
        }
        Direction.Axis controlAxis = getNonShaftAxis();
        Direction.AxisDirection dir = channel == RotationChannel.CLOCKWISE
                ? Direction.AxisDirection.POSITIVE
                : Direction.AxisDirection.NEGATIVE;
        return getSignalFromFace(Direction.fromAxisAndDirection(controlAxis, dir));
    }

    // Get the signal from face
    private int getSignalFromFace(Direction face) {
        if (level == null) {
            return 0;
        }
        BlockPos neighborPos = worldPosition.relative(face);
        return Math.max(level.getSignal(neighborPos, face), level.getDirectSignal(neighborPos, face));
    }

    // Get the non shaft axis
    private Direction.Axis getNonShaftAxis() {
        Direction.Axis primary = getPrimaryLaneAxis();
        Direction.Axis secondary = getSecondaryLaneAxis();
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != primary && axis != secondary) {
                return axis;
            }
        }
        return Direction.Axis.Y;
    }

    // Query the wireless signal
    private int queryWirelessSignal(FrequencyBinding binding) {
        if (level == null || binding == null || !binding.isBound()) {
            return 0;
        }
        return queryWirelessSignalForLevel(binding, level);
    }

    // Query the wireless signal for level
    private int queryWirelessSignalForLevel(FrequencyBinding binding, Level queryLevel) {
        Couple<RedstoneLinkNetworkHandler.Frequency> key = Couple.create(
                RedstoneLinkNetworkHandler.Frequency.of(binding.first()),
                RedstoneLinkNetworkHandler.Frequency.of(binding.second()));
        Map<Couple<RedstoneLinkNetworkHandler.Frequency>, Set<IRedstoneLinkable>> networks =
                Create.REDSTONE_LINK_NETWORK_HANDLER.networksIn(queryLevel);
        Set<IRedstoneLinkable> network = networks.get(key);
        if (network == null || network.isEmpty()) {
            return 0;
        }

        Vec3 currentPosition = SimulatedHelper.toGlobalWorldPosition(this, Vec3.atCenterOf(worldPosition));
        int linkRange = AllConfigs.server().logistics.linkRange.get();
        double linkRangeSq = linkRange * (double) linkRange;
        int maxSignal = 0;
        for (IRedstoneLinkable candidate : network) {
            if (candidate == null || !candidate.isAlive()) {
                continue;
            }
            int transmitted = Mth.clamp(candidate.getTransmittedStrength(), 0, 15);
            if (transmitted <= 0) {
                continue;
            }
            BlockPos candidateLocation = candidate.getLocation();
            if (candidateLocation == null) {
                continue;
            }
            Vec3 candidatePosition = SimulatedHelper.projectOutOfSubLevels(queryLevel, Vec3.atCenterOf(candidateLocation));
            if (SimulatedHelper.distanceSquaredWithSubLevels(queryLevel, currentPosition, candidatePosition) > linkRangeSq) {
                continue;
            }
            maxSignal = Math.max(maxSignal, transmitted);
            if (maxSignal >= 15) {
                break;
            }
        }
        return maxSignal;
    }

    // Check whether one ACC direction control is active
    private boolean isGraphChannelActive(AxisRole role, RotationChannel channel) {
        if (role == AxisRole.PRIMARY) {
            return channel == RotationChannel.CLOCKWISE
                    ? graphPrimaryClockwise
                    : graphPrimaryCounterClockwise;
        }
        return channel == RotationChannel.CLOCKWISE
                ? graphSecondaryClockwise
                : graphSecondaryCounterClockwise;
    }

    // Set one ACC direction control
    private void setGraphChannelActive(AxisRole role, RotationChannel channel, boolean active) {
        if (role == AxisRole.PRIMARY) {
            if (channel == RotationChannel.CLOCKWISE) {
                graphPrimaryClockwise = active;
            } else {
                graphPrimaryCounterClockwise = active;
            }
        } else if (channel == RotationChannel.CLOCKWISE) {
            graphSecondaryClockwise = active;
        } else {
            graphSecondaryCounterClockwise = active;
        }
        setChanged();
        sendData();
    }

    // Get the lane axis
    public Direction.Axis getLaneAxis(AxisRole role) {
        return role == AxisRole.PRIMARY ? getSecondaryLaneAxis() : getPrimaryLaneAxis();
    }

    // Get the axis mode
    public AxisControlMode getAxisMode(AxisRole role) {
        return role == AxisRole.SECONDARY ? secondaryMode : primaryMode;
    }

    // Set the axis mode
    public void setAxisMode(AxisRole role, AxisControlMode mode) {
        if (mode == null) {
            return;
        }
        if (role == AxisRole.SECONDARY) {
            if (secondaryMode == mode) {
                return;
            }
            secondaryMode = mode;
        } else {
            if (primaryMode == mode) {
                return;
            }
            primaryMode = mode;
        }
        if (level != null && !level.isClientSide && mode == AxisControlMode.PASSTHROUGH
                && getLaneMode(getLaneAxis(role)) == LaneMode.DISABLED) {
            setLaneMode(getLaneAxis(role), LaneMode.STRAIGHT);
        }
        setChanged();
        sendData();
    }

    // Get the local mode
    public LocalControlMode getLocalMode() {
        return localMode;
    }

    // Set the local mode
    public void setLocalMode(LocalControlMode localMode) {
        if (localMode == null || this.localMode == localMode) {
            return;
        }
        this.localMode = localMode;
        setChanged();
        sendData();
    }

    // Get the frequency first
    public ItemStack getFrequencyFirst(AxisRole role, RotationChannel channel) {
        return getFrequencyBinding(role, channel).first();
    }

    // Get the frequency second
    public ItemStack getFrequencySecond(AxisRole role, RotationChannel channel) {
        return getFrequencyBinding(role, channel).second();
    }

    // Set the frequency
    public void setFrequency(AxisRole role, RotationChannel channel, ItemStack first, ItemStack second) {
        getFrequencyBinding(role, channel).set(first, second);
        setChanged();
        sendData();
    }

    // Get the frequency binding
    private FrequencyBinding getFrequencyBinding(AxisRole role, RotationChannel channel) {
        return frequencyBindings.get(role).get(channel);
    }

    // Write the bi directional gearshift safely
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        tag.putInt("AxisRoleColorMappingVersion", AXIS_ROLE_COLOR_MAPPING_VERSION);
        tag.putString("PrimaryAxisMode", primaryMode.name());
        tag.putString("SecondaryAxisMode", secondaryMode.name());
        tag.putString("LocalControlMode", localMode.name());
        writeGraphControls(tag);
        for (AxisRole role : AxisRole.values()) {
            for (RotationChannel channel : RotationChannel.values()) {
                tag.put(frequencyKey(role, channel), getFrequencyBinding(role, channel).toTag(provider));
            }
        }
    }

    // Write the bi directional gearshift
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putInt("AxisRoleColorMappingVersion", AXIS_ROLE_COLOR_MAPPING_VERSION);
        tag.putString("PrimaryAxisMode", primaryMode.name());
        tag.putString("SecondaryAxisMode", secondaryMode.name());
        tag.putString("LocalControlMode", localMode.name());
        writeGraphControls(tag);
        for (AxisRole role : AxisRole.values()) {
            for (RotationChannel channel : RotationChannel.values()) {
                FrequencyBinding binding = getFrequencyBinding(role, channel);
                if (binding.isBound()) {
                    tag.put(frequencyKey(role, channel), binding.toTag(provider));
                }
            }
        }
    }

    // Read the bi directional gearshift
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        boolean legacyRoleMapping = tag.getInt("AxisRoleColorMappingVersion") < AXIS_ROLE_COLOR_MAPPING_VERSION;
        AxisControlMode storedPrimaryMode = readEnum(tag, "PrimaryAxisMode", AxisControlMode.PASSTHROUGH);
        AxisControlMode storedSecondaryMode = readEnum(tag, "SecondaryAxisMode", AxisControlMode.PASSTHROUGH);
        primaryMode = legacyRoleMapping ? storedSecondaryMode : storedPrimaryMode;
        secondaryMode = legacyRoleMapping ? storedPrimaryMode : storedSecondaryMode;
        localMode = migrateLocalControlMode(
                readEnum(tag, "LocalControlMode", LocalControlMode.BOTH), legacyRoleMapping);
        readGraphControls(tag);
        for (AxisRole role : AxisRole.values()) {
            for (RotationChannel channel : RotationChannel.values()) {
                FrequencyBinding binding = getFrequencyBinding(role, channel);
                AxisRole storedRole = legacyRoleMapping ? oppositeRole(role) : role;
                String key = frequencyKey(storedRole, channel);
                if (tag.contains(key)) {
                    binding.read(tag.getCompound(key), provider);
                } else {
                    binding.clear();
                }
            }
        }
    }

    // Write the persistent ACC direction controls
    private void writeGraphControls(CompoundTag tag) {
        tag.putBoolean("GraphPrimaryClockwise", graphPrimaryClockwise);
        tag.putBoolean("GraphPrimaryCounterClockwise", graphPrimaryCounterClockwise);
        tag.putBoolean("GraphSecondaryClockwise", graphSecondaryClockwise);
        tag.putBoolean("GraphSecondaryCounterClockwise", graphSecondaryCounterClockwise);
    }

    // Read the persistent ACC direction controls
    private void readGraphControls(CompoundTag tag) {
        graphPrimaryClockwise = tag.getBoolean("GraphPrimaryClockwise");
        graphPrimaryCounterClockwise = tag.getBoolean("GraphPrimaryCounterClockwise");
        graphSecondaryClockwise = tag.getBoolean("GraphSecondaryClockwise");
        graphSecondaryCounterClockwise = tag.getBoolean("GraphSecondaryCounterClockwise");
    }

    // Migrate the old cyan-primary and orange-secondary user-facing role mapping
    private static LocalControlMode migrateLocalControlMode(LocalControlMode mode, boolean legacyRoleMapping) {
        if (!legacyRoleMapping) {
            return mode;
        }
        return switch (mode) {
            case PRIMARY_AXIS -> LocalControlMode.SECONDARY_AXIS;
            case SECONDARY_AXIS -> LocalControlMode.PRIMARY_AXIS;
            case BOTH -> LocalControlMode.BOTH;
        };
    }

    // Get the opposite user-facing role
    private static AxisRole oppositeRole(AxisRole role) {
        return role == AxisRole.PRIMARY ? AxisRole.SECONDARY : AxisRole.PRIMARY;
    }

    // Read the enum
    private static <E extends Enum<E>> E readEnum(CompoundTag tag, String key, E fallback) {
        if (!tag.contains(key)) {
            return fallback;
        }
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), tag.getString(key));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    // Create the menu
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BiDirectionalGearshiftMenu(containerId, playerInventory, this);
    }

    // Get the display name
    @Override
    public Component getDisplayName() {
        return Component.translatable("createthrusters.bi_directional_gearshift.config.title");
    }

    // Check if the player can use this
    public boolean canPlayerUse(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64.0D;
    }

    // Send the menu data
    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        MenuOpenHeader.encode(buffer, worldPosition, SimulatedHelper.getContainingSubLevelId(this));
        buffer.writeEnum(getPrimaryLaneAxis());
        buffer.writeEnum(getSecondaryLaneAxis());
        buffer.writeEnum(primaryMode);
        buffer.writeEnum(secondaryMode);
        buffer.writeEnum(localMode);
    }

    // Add the goggle tooltip
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean showDetails = CTTooltipHelper.showGoggleDetails(isPlayerSneaking);
        tooltip.add(CTTooltipHelper.title(Component.translatable("block.createthrusters.bi_directional_gearshift")));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gearshift.axis_modes"),
                CTTooltipHelper.value(modeName(primaryMode) + " / " + modeName(secondaryMode), ChatFormatting.AQUA)));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gearshift.local_control"),
                CTTooltipHelper.value(localMode.name().toLowerCase(Locale.ROOT), ChatFormatting.GOLD)));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gearbox.lane_speed"),
                CTTooltipHelper.value(String.format(Locale.ROOT, "%.2f / %.2f",
                        Mth.abs(getNorthSouthSpeed()), Mth.abs(getEastWestSpeed())), ChatFormatting.GOLD)));
        if (showDetails) {
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gearbox.lanes"),
                    CTTooltipHelper.value(getLaneMode(getLaneAxis(AxisRole.PRIMARY)).name().toLowerCase(Locale.ROOT)
                            + " / " + getLaneMode(getLaneAxis(AxisRole.SECONDARY)).name().toLowerCase(Locale.ROOT),
                            ChatFormatting.YELLOW)));
        }
        return true;
    }

    // Get the graph readable data
    @Override
    public Map<String, String> graphReadableData() {
        Map<String, String> data = new LinkedHashMap<>(super.graphReadableData());
        data.putAll(graphControlData());
        return data;
    }

    // Get the graph writable data
    @Override
    public Map<String, String> graphWritableData() {
        return graphControlData();
    }

    // Get the graph control data
    private static Map<String, String> graphControlData() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put(PRIMARY_CW_PORT, "boolean");
        data.put(PRIMARY_CCW_PORT, "boolean");
        data.put(SECONDARY_CW_PORT, "boolean");
        data.put(SECONDARY_CCW_PORT, "boolean");
        data.put(PRIMARY_MODE_PORT, "string");
        data.put(SECONDARY_MODE_PORT, "string");
        return data;
    }

    // Get the graph writable options
    @Override
    public Map<String, List<String>> graphWritableOptions() {
        List<String> modes = List.of("passthrough", "directional");
        return Map.of(PRIMARY_MODE_PORT, modes, SECONDARY_MODE_PORT, modes);
    }

    // Read the graph data
    @Override
    public com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value readGraphData(String field) {
        return switch (field) {
            case PRIMARY_CW_PORT -> com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.bool(
                    graphPrimaryClockwise);
            case PRIMARY_CCW_PORT -> com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.bool(
                    graphPrimaryCounterClockwise);
            case SECONDARY_CW_PORT -> com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.bool(
                    graphSecondaryClockwise);
            case SECONDARY_CCW_PORT -> com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.bool(
                    graphSecondaryCounterClockwise);
            case PRIMARY_MODE_PORT -> com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.string(
                    modeName(primaryMode));
            case SECONDARY_MODE_PORT -> com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.string(
                    modeName(secondaryMode));
            default -> super.readGraphData(field);
        };
    }

    // Write the graph data
    @Override
    public boolean writeGraphData(String field,
                                  com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value value) {
        if (field == null || value == null) {
            return false;
        }
        switch (field) {
            case PRIMARY_CW_PORT -> setGraphChannelActive(AxisRole.PRIMARY, RotationChannel.CLOCKWISE,
                    value.asBoolean());
            case PRIMARY_CCW_PORT -> setGraphChannelActive(AxisRole.PRIMARY, RotationChannel.COUNTER_CLOCKWISE,
                    value.asBoolean());
            case SECONDARY_CW_PORT -> setGraphChannelActive(AxisRole.SECONDARY, RotationChannel.CLOCKWISE,
                    value.asBoolean());
            case SECONDARY_CCW_PORT -> setGraphChannelActive(AxisRole.SECONDARY, RotationChannel.COUNTER_CLOCKWISE,
                    value.asBoolean());
            case PRIMARY_MODE_PORT -> {
                AxisControlMode mode = graphAxisMode(value.asString());
                if (mode == null) return false;
                setAxisMode(AxisRole.PRIMARY, mode);
            }
            case SECONDARY_MODE_PORT -> {
                AxisControlMode mode = graphAxisMode(value.asString());
                if (mode == null) return false;
                setAxisMode(AxisRole.SECONDARY, mode);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // Parse one graph axis mode
    private static AxisControlMode graphAxisMode(String mode) {
        if (mode == null) return null;
        try {
            return AxisControlMode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // Get the mode name
    private static String modeName(AxisControlMode mode) {
        return mode == null ? "passthrough" : mode.name().toLowerCase(Locale.ROOT);
    }

    // Get the frequency key
    private static String frequencyKey(AxisRole role, RotationChannel channel) {
        return "Frequency" + role.name() + channel.name();
    }

    // Create the frequency bindings
    private static EnumMap<AxisRole, EnumMap<RotationChannel, FrequencyBinding>> createFrequencyBindings() {
        EnumMap<AxisRole, EnumMap<RotationChannel, FrequencyBinding>> bindings = new EnumMap<>(AxisRole.class);
        for (AxisRole role : AxisRole.values()) {
            EnumMap<RotationChannel, FrequencyBinding> channels = new EnumMap<>(RotationChannel.class);
            for (RotationChannel channel : RotationChannel.values()) {
                channels.put(channel, new FrequencyBinding("gearshift_" + role.name().toLowerCase() + "_" + channel.name().toLowerCase()));
            }
            bindings.put(role, channels);
        }
        return bindings;
    }

    // Define the axis role values
    public enum AxisRole {
        PRIMARY,
        SECONDARY
    }

    // Define the axis control mode values
    public enum AxisControlMode {
        PASSTHROUGH,
        DIRECTIONAL
    }

    // Define the rotation channel values
    public enum RotationChannel {
        CLOCKWISE,
        COUNTER_CLOCKWISE
    }

    // Define the local control mode values
    public enum LocalControlMode {
        BOTH {
            // Check if this target controls the axis role
            @Override
            boolean controls(AxisRole role) {
                return true;
            }
        },
        PRIMARY_AXIS {
            // Check if this target controls the axis role
            @Override
            boolean controls(AxisRole role) {
                return role == AxisRole.PRIMARY;
            }
        },
        SECONDARY_AXIS {
            // Check if this target controls the axis role
            @Override
            boolean controls(AxisRole role) {
                return role == AxisRole.SECONDARY;
            }
        };

        // Check if this target controls the axis role
        abstract boolean controls(AxisRole role);
    }
}
