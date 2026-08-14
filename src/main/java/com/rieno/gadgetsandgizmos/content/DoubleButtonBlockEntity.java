package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.control.FrequencyBinding;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.Create;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Couple;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

// Keep both button halves, copycat material and pulse state synced as one block entity
public class DoubleButtonBlockEntity extends CopycatBlockEntity implements com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int OAK_RELEASE_TICKS = 30;
    private static final int STONE_RELEASE_TICKS = 20;
    private static final int HOLD_TIMEOUT_TICKS = 4;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked bindings
    private final EnumMap<ButtonHalf, FrequencyBinding> bindings = new EnumMap<>(ButtonHalf.class);
    // Tracked transmitters
    private final EnumMap<ButtonHalf, ButtonTransmitter> transmitters = new EnumMap<>(ButtonHalf.class);
    // Registered transmitters
    private final EnumSet<ButtonHalf> registeredTransmitters = EnumSet.noneOf(ButtonHalf.class);
    // Release tick count
    private final EnumMap<ButtonHalf, Long> releaseTicks = new EnumMap<>(ButtonHalf.class);
    // Hold timeout tick count
    private final EnumMap<ButtonHalf, Long> holdTimeoutTicks = new EnumMap<>(ButtonHalf.class);
    // Tracked response modes
    private final EnumMap<ButtonHalf, ResponseMode> responseModes = new EnumMap<>(ButtonHalf.class);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the double button
    public DoubleButtonBlockEntity(BlockPos pos, BlockState state) {
        this(CTBlockEntities.DOUBLE_BUTTON.get(), pos, state);
    }

    // Initialize the double button
    public DoubleButtonBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        for (ButtonHalf btn : ButtonHalf.values()) {
            bindings.put(btn, new FrequencyBinding(btn.name().toLowerCase(Locale.ROOT)));
            transmitters.put(btn, new ButtonTransmitter(btn));
            releaseTicks.put(btn, 0L);
            holdTimeoutTicks.put(btn, 0L);
            responseModes.put(btn, ResponseMode.OAK);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // Initialize the double button
    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            refreshTransmitterRegistrations();
            updateTransmitters(true);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the double button
    public static void tick(Level level, BlockPos pos, BlockState state, DoubleButtonBlockEntity blockEntity) {
        blockEntity.tick();
        if (!level.isClientSide) {
            blockEntity.serverTick();
        }
    }

    // Update the server
    private void serverTick() {
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        for (ButtonHalf btn : ButtonHalf.values()) {
            if (getResponseMode(btn) == ResponseMode.HOLD
                    && isActive(btn) && holdTimeoutTicks.getOrDefault(btn, 0L) > 0L
                    && holdTimeoutTicks.get(btn) <= now) {
                holdTimeoutTicks.put(btn, 0L);
                setActive(btn, false);
            }
        }
    }

    // Activate the double button
    public void activate(ButtonHalf btn) {
        if (level == null || level.isClientSide) {
            return;
        }

        ResponseMode mode = getResponseMode(btn);
        switch (mode) {
            case OAK, STONE -> activateTimed(btn);
            case LATCH -> {
                boolean next = !isActive(btn);
                setActive(btn, next);
                playClick(mode, next);
            }
            case HOLD -> handleHold(btn, true);
        }
    }

    // Update the held button state
    public void handleHold(ButtonHalf btn, boolean held) {
        ResponseMode mode = getResponseMode(btn);
        if (level == null || level.isClientSide || mode != ResponseMode.HOLD) {
            return;
        }

        if (held) {
            holdTimeoutTicks.put(btn, level.getGameTime() + HOLD_TIMEOUT_TICKS);
            if (!isActive(btn)) {
                setActive(btn, true);
                playClick(mode, true);
            }
            return;
        }

        holdTimeoutTicks.put(btn, 0L);
        if (isActive(btn)) {
            setActive(btn, false);
            playClick(mode, false);
        }
    }

    // Update the scheduled release
    public void scheduledReleaseTick() {
        if (level == null || level.isClientSide) {
            return;
        }
        long now = level.getGameTime();
        long nextTick = 0L;
        for (ButtonHalf btn : ButtonHalf.values()) {
            long releaseTick = releaseTicks.getOrDefault(btn, 0L);
            if (releaseTick <= 0L) {
                continue;
            }
            if (releaseTick <= now) {
                releaseTicks.put(btn, 0L);
                ResponseMode mode = getResponseMode(btn);
                if (mode == ResponseMode.OAK || mode == ResponseMode.STONE) {
                    setActive(btn, false);
                    playClick(mode, false);
                }
            } else if (nextTick == 0L || releaseTick < nextTick) {
                nextTick = releaseTick;
            }
        }
        if (nextTick > now) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), (int) Math.max(1L, nextTick - now));
        }
    }

    // Set the frequency
    public void setFrequency(ButtonHalf btn, boolean firstFrequency, ItemStack stack) {
        FrequencyBinding binding = bindings.get(btn);
        if (binding == null || level == null || level.isClientSide) {
            return;
        }

        ItemStack first = binding.first();
        ItemStack second = binding.second();
        ItemStack next = copySingle(stack);
        if (firstFrequency) {
            first = next;
        } else {
            second = next;
        }

        unregisterTransmitter(btn);
        binding.set(first, second);
        registerTransmitter(btn);
        updateTransmitter(btn, true);
        setChanged();
        sendData();
    }

    // Get the frequency
    public ItemStack getFrequency(ButtonHalf btn, boolean firstFrequency) {
        FrequencyBinding binding = bindings.get(btn);
        if (binding == null) {
            return ItemStack.EMPTY;
        }
        return firstFrequency ? binding.first() : binding.second();
    }

    // Get the response mode
    public ResponseMode getResponseMode(ButtonHalf btn) {
        return responseModes.getOrDefault(btn, ResponseMode.OAK);
    }

    // Set the response mode
    public void setResponseMode(ButtonHalf btn, ResponseMode mode) {
        if (btn == null || mode == null || getResponseMode(btn) == mode) {
            return;
        }

        responseModes.put(btn, mode);
        releaseTicks.put(btn, 0L);
        holdTimeoutTicks.put(btn, 0L);
        if (mode == ResponseMode.OAK || mode == ResponseMode.STONE || mode == ResponseMode.HOLD) {
            if (isActive(btn)) {
                setActive(btn, false);
            }
        }
        setChanged();
        sendData();
    }

    // Check if the link hardware is visible
    public boolean isLinkHardwareVisible() {
        return DoubleButtonBlock.isLinkHardwareVisible(getBlockState());
    }

    // Set the link hardware visible
    public void setLinkHardwareVisible(boolean visible) {
        if (level == null || level.isClientSide || isLinkHardwareVisible() == visible) {
            return;
        }

        BlockState state = getBlockState();
        if (!state.hasProperty(DoubleButtonBlock.SHOW_LINKS)) {
            return;
        }
        level.setBlock(worldPosition, state.setValue(DoubleButtonBlock.SHOW_LINKS, visible), 3);
        setChanged();
        sendData();
    }

    // Check if this is active
    public boolean isActive(ButtonHalf btn) {
        BlockState state = getBlockState();
        return state.hasProperty(DoubleButtonBlock.poweredProperty(btn))
                && state.getValue(DoubleButtonBlock.poweredProperty(btn));
    }

    // Get the signal
    public int getSignal(ButtonHalf btn) {
        return isActive(btn) ? 15 : 0;
    }

    // Handle the destroyed event
    public void onDestroyed() {
        unregisterTransmitters();
        for (ButtonTransmitter transmitter : transmitters.values()) {
            transmitter.transmittedStrength = 0;
        }
    }

    // Remove the double button
    @Override
    public void remove() {
        unregisterTransmitters();
        super.remove();
    }

    // Invalidate the double button
    @Override
    public void invalidate() {
        unregisterTransmitters();
        super.invalidate();
    }

    // Write the double button
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        writeButtonData(tag, provider);
    }

    // Write the double button safely
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        writeButtonData(tag, provider);
    }

    // Write the button data
    private void writeButtonData(CompoundTag tag, HolderLookup.Provider provider) {
        for (ButtonHalf btn : ButtonHalf.values()) {
            tag.putString(btn.nbtPrefix() + "ResponseMode", getResponseMode(btn).name());
            FrequencyBinding binding = bindings.get(btn);
            if (binding != null && binding.isBound()) {
                tag.put(btn.nbtPrefix() + "Binding", binding.toTag(provider));
            }
        }
    }

    // Read the double button
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        for (ButtonHalf btn : ButtonHalf.values()) {
            responseModes.put(btn, readResponseMode(tag, btn));
            FrequencyBinding binding = bindings.get(btn);
            if (binding == null) {
                continue;
            }
            String key = btn.nbtPrefix() + "Binding";
            if (tag.contains(key)) {
                binding.read(tag.getCompound(key), provider);
            } else {
                binding.clear();
            }
        }
    }

    // Read the response mode
    static ResponseMode readResponseMode(CompoundTag tag, ButtonHalf btn) {
        String modeKey = btn.nbtPrefix() + "ResponseMode";
        if (tag.contains(modeKey)) {
            return ResponseMode.read(tag.getString(modeKey));
        }
        return tag.contains("ResponseMode")
                ? ResponseMode.read(tag.getString("ResponseMode"))
                : ResponseMode.OAK;
    }

    // Add the goggle tooltip
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("createthrusters.double_button.goggle.top",
                isActive(ButtonHalf.TOP) ? 15 : 0,
                Component.translatable(getResponseMode(ButtonHalf.TOP).translationKey()))
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("createthrusters.double_button.goggle.bottom",
                isActive(ButtonHalf.BOTTOM) ? 15 : 0,
                Component.translatable(getResponseMode(ButtonHalf.BOTTOM).translationKey()))
                .withStyle(ChatFormatting.DARK_RED));
        return true;
    }

    // Activate the timed
    private void activateTimed(ButtonHalf btn) {
        if (isActive(btn)) {
            return;
        }
        ResponseMode mode = getResponseMode(btn);
        setActive(btn, true);
        playClick(mode, true);
        int delay = mode.releaseTicks();
        releaseTicks.put(btn, level.getGameTime() + delay);
        level.scheduleTick(worldPosition, getBlockState().getBlock(), delay);
    }

    // Set the active
    private void setActive(ButtonHalf btn, boolean active) {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(DoubleButtonBlock.poweredProperty(btn))
                || state.getValue(DoubleButtonBlock.poweredProperty(btn)) == active) {
            updateTransmitter(btn, false);
            return;
        }

        BlockState updated = state.setValue(DoubleButtonBlock.poweredProperty(btn), active);
        level.setBlock(worldPosition, updated, 3);
        updateTransmitter(btn, true);
        DoubleButtonBlock.notifyNeighbors(level, worldPosition, updated);
        setChanged();
        sendData();
    }

    // Play the click
    private void playClick(ResponseMode mode, boolean active) {
        if (level == null) {
            return;
        }
        SoundEvent sound = mode.clickSound(active);
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 0.3F, active ? 0.6F : 0.5F);
    }

    // Refresh the transmitter registrations
    private void refreshTransmitterRegistrations() {
        for (ButtonHalf btn : ButtonHalf.values()) {
            if (isFrequencyBound(btn)) {
                registerTransmitter(btn);
            } else {
                unregisterTransmitter(btn);
            }
        }
    }

    // Check if the frequency is bound
    private boolean isFrequencyBound(ButtonHalf btn) {
        FrequencyBinding binding = bindings.get(btn);
        return binding != null && binding.isBound();
    }

    // Register the transmitter
    private void registerTransmitter(ButtonHalf btn) {
        if (level == null || level.isClientSide || registeredTransmitters.contains(btn) || !isFrequencyBound(btn)) {
            return;
        }
        ButtonTransmitter transmitter = transmitters.get(btn);
        if (transmitter == null) {
            return;
        }
        Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(resolveLinkLevel(level), transmitter);
        registeredTransmitters.add(btn);
    }

    // Remove the transmitter
    private void unregisterTransmitter(ButtonHalf btn) {
        if (level == null || level.isClientSide || !registeredTransmitters.remove(btn)) {
            return;
        }
        ButtonTransmitter transmitter = transmitters.get(btn);
        if (transmitter != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(resolveLinkLevel(level), transmitter);
        }
    }

    // Remove the transmitters
    private void unregisterTransmitters() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (ButtonHalf btn : ButtonHalf.values()) {
            unregisterTransmitter(btn);
        }
    }

    // Update the transmitters
    private void updateTransmitters(boolean force) {
        for (ButtonHalf btn : ButtonHalf.values()) {
            updateTransmitter(btn, force);
        }
    }

    // Update the transmitter
    private void updateTransmitter(ButtonHalf btn, boolean force) {
        if (level == null || level.isClientSide) {
            return;
        }
        ButtonTransmitter transmitter = transmitters.get(btn);
        if (transmitter == null) {
            return;
        }
        int nextStrength = getSignal(btn);
        if (!force && transmitter.transmittedStrength == nextStrength) {
            return;
        }
        transmitter.transmittedStrength = nextStrength;
        if (registeredTransmitters.contains(btn)) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(resolveLinkLevel(level), transmitter);
        }
    }

    // Resolve the link level
    private Level resolveLinkLevel(Level currentLevel) {
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

    // Copy one item
    private static ItemStack copySingle(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    // Define the button half values
    public enum ButtonHalf {
        TOP,
        BOTTOM;

        // Get the NBT prefix
        public String nbtPrefix() {
            return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
        }

        // Get the translation key
        public String translationKey() {
            return "createthrusters.double_button.button." + name().toLowerCase(Locale.ROOT);
        }
    }

    // Define the response mode values
    public enum ResponseMode {
        OAK("createthrusters.double_button.mode.oak", OAK_RELEASE_TICKS),
        STONE("createthrusters.double_button.mode.stone", STONE_RELEASE_TICKS),
        LATCH("createthrusters.double_button.mode.latch", 0),
        HOLD("createthrusters.double_button.mode.hold", 0);

        // Translation key
        private final String translationKey;
        // Release tick count
        private final int releaseTicks;

        // Initialize the response mode
        ResponseMode(String translationKey, int releaseTicks) {
            this.translationKey = translationKey;
            this.releaseTicks = releaseTicks;
        }

        // Get the translation key
        public String translationKey() {
            return translationKey;
        }

        // Release the ticks
        public int releaseTicks() {
            return releaseTicks;
        }

        // Get the click sound
        public SoundEvent clickSound(boolean active) {
            if (this == STONE) {
                return active ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF;
            }
            return active ? SoundEvents.WOODEN_BUTTON_CLICK_ON : SoundEvents.WOODEN_BUTTON_CLICK_OFF;
        }

        // Read the response mode
        public static ResponseMode read(String name) {
            if (name == null || name.isBlank()) {
                return OAK;
            }
            try {
                return ResponseMode.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return OAK;
            }
        }
    }

    // Handle the button transmitter
    private class ButtonTransmitter implements IRedstoneLinkable {
        // Button
        private final ButtonHalf button;
        // Transmitted strength
        private int transmittedStrength;

        // Initialize the button transmitter
        private ButtonTransmitter(ButtonHalf btn) {
            this.button = btn;
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
            FrequencyBinding binding = bindings.get(button);
            if (binding == null) {
                return Couple.create(RedstoneLinkNetworkHandler.Frequency.EMPTY,
                        RedstoneLinkNetworkHandler.Frequency.EMPTY);
            }
            return Couple.create(
                    RedstoneLinkNetworkHandler.Frequency.of(binding.first()),
                    RedstoneLinkNetworkHandler.Frequency.of(binding.second()));
        }

        // Get the location
        @Override
        public BlockPos getLocation() {
            Vec3 projected = SimulatedHelper.toContainingWorldPosition(DoubleButtonBlockEntity.this,
                    Vec3.atCenterOf(worldPosition));
            return projected == null ? worldPosition : BlockPos.containing(projected);
        }
    }
}
