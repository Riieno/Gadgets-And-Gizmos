package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletInteractionMode;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

// Read and write the small identity and access payload kept directly on a tablet item
public final class DiagnosticTabletData {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String ROOT = "DiagnosticTablet";
    private static final String APP = "App";
    private static final String TAB = "Tab";
    private static final String BINDING = "Binding";
    private static final String MODE = "Mode";
    private static final String PENDING_ACTION = "PendingAction";
    private static final String SELECTIONS = "Selections";
    private static final String REDSTONE_LINK_CHANNELS = "RedstoneLinkChannels";
    private static final String TABLET_ID = "TabletId";
    public static final int MAX_REDSTONE_LINK_CHANNELS = 24;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet data
    private DiagnosticTabletData() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read the diagnostic tablet data
    public static State read(ItemStack stack) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return fromTag(custom.getCompound(ROOT));
    }

    // Write the diagnostic tablet data
    public static void write(ItemStack stack, State state) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        custom.put(ROOT, toTag(state));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }

    // Read the diagnostic tablet data
    public static State fromTag(CompoundTag tag) {
        ResourceLocation app = ResourceLocation.tryParse(tag.getString(APP));
        List<Binding> selections = new ArrayList<>();
        ListTag selectionTags = tag.getList(SELECTIONS, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < selectionTags.size() && selections.size() < 128; idx++) {
            Binding selection = Binding.fromTag(selectionTags.getCompound(idx));
            if (selection != null) selections.add(selection);
        }
        List<RedstoneLinkChannel> redstoneLinkChannels = new ArrayList<>();
        ListTag channelTags = tag.getList(REDSTONE_LINK_CHANNELS, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < channelTags.size()
                && redstoneLinkChannels.size() < MAX_REDSTONE_LINK_CHANNELS; idx++) {
            RedstoneLinkChannel channel = RedstoneLinkChannel.fromTag(channelTags.getCompound(idx));
            if (channel != null) redstoneLinkChannels.add(channel);
        }
        return new State(app == null ? appId("home") : app,
                tag.getString(TAB), Binding.fromTag(tag.getCompound(BINDING)),
                TabletInteractionMode.fromId(tag.getString(MODE)), tag.getString(PENDING_ACTION),
                selections, redstoneLinkChannels,
                tag.hasUUID(TABLET_ID) ? tag.getUUID(TABLET_ID) : null);
    }

    // Write the diagnostic tablet data
    public static CompoundTag toTag(State state) {
        State val = state == null ? State.DEFAULT : state;
        CompoundTag tag = new CompoundTag();
        tag.putString(APP, val.app().toString());
        tag.putString(TAB, val.tab());
        if (val.binding() != null) {
            tag.put(BINDING, val.binding().toTag());
        }
        tag.putString(MODE, val.mode().id());
        tag.putString(PENDING_ACTION, val.pendingAction());
        ListTag selections = new ListTag();
        val.selections().stream().limit(128).forEach(selection -> selections.add(selection.toTag()));
        tag.put(SELECTIONS, selections);
        ListTag channels = new ListTag();
        val.redstoneLinkChannels().stream().limit(MAX_REDSTONE_LINK_CHANNELS)
                .forEach(channel -> channels.add(channel.toTag()));
        tag.put(REDSTONE_LINK_CHANNELS, channels);
        if (val.tabletId() != null) tag.putUUID(TABLET_ID, val.tabletId());
        return tag;
    }

    // Ensure the tablet id
    public static UUID ensureTabletId(ItemStack stack) {
        State state = read(stack);
        if (state.tabletId() != null) return state.tabletId();
        UUID tabletId = UUID.randomUUID();
        write(stack, state.withTabletId(tabletId));
        return tabletId;
    }

    // Get the app id
    public static ResourceLocation appId(String path) {
        return ResourceLocation.fromNamespaceAndPath("createthrusters", path);
    }

    // Store the current state
    public record State(ResourceLocation app, String tab, @Nullable Binding binding,
                        TabletInteractionMode mode, String pendingAction, List<Binding> selections,
                        List<RedstoneLinkChannel> redstoneLinkChannels,
                        @Nullable UUID tabletId) {
        public static final State DEFAULT = new State(appId("home"), "", null,
                TabletInteractionMode.STANDARD, "", List.of(), List.of(), null);

        // Initialize the state
        public State {
            app = app == null ? appId("home") : app;
            tab = tab == null ? "" : tab;
            mode = mode == null ? TabletInteractionMode.STANDARD : mode;
            pendingAction = pendingAction == null ? "" : pendingAction.trim();
            selections = selections == null ? List.of() : List.copyOf(selections.stream().limit(128).toList());
            redstoneLinkChannels = redstoneLinkChannels == null ? List.of()
                    : List.copyOf(redstoneLinkChannels.stream()
                    .limit(MAX_REDSTONE_LINK_CHANNELS).toList());
        }

        // Copy the state with the app
        public State withApp(ResourceLocation nextApp, String nextTab) {
            return new State(nextApp, nextTab, binding, mode, pendingAction, selections,
                    redstoneLinkChannels, tabletId);
        }

        // Copy the state with the binding
        public State withBinding(@Nullable Binding nextBinding) {
            return new State(app, tab, nextBinding, mode, pendingAction, selections,
                    redstoneLinkChannels, tabletId);
        }

        // Copy the state with the mode
        public State withMode(TabletInteractionMode nextMode, String nextPendingAction) {
            return new State(app, tab, binding, nextMode, nextPendingAction, selections,
                    redstoneLinkChannels, tabletId);
        }

        // Copy the state with the selection
        public State withSelection(Binding selection) {
            if (selection == null) return this;
            List<Binding> next = new ArrayList<>(selections);
            next.removeIf(existing -> existing.subLevelId() == null
                    ? selection.subLevelId() == null && existing.pos().equals(selection.pos())
                    : existing.subLevelId().equals(selection.subLevelId()) && existing.pos().equals(selection.pos()));
            next.add(selection);
            if (next.size() > 128) next = new ArrayList<>(next.subList(next.size() - 128, next.size()));
            return new State(app, tab, binding, mode, pendingAction, next,
                    redstoneLinkChannels, tabletId);
        }

        // Clear the selections
        public State clearSelections() {
            return new State(app, tab, binding, mode, pendingAction, List.of(),
                    redstoneLinkChannels, tabletId);
        }

        // Copy the state with the redstone link channels
        public State withRedstoneLinkChannels(List<RedstoneLinkChannel> channels) {
            return new State(app, tab, binding, mode, pendingAction, selections, channels,
                    tabletId);
        }

        // Copy the state with the tablet id
        public State withTabletId(UUID nextTabletId) {
            return new State(app, tab, binding, mode, pendingAction, selections,
                    redstoneLinkChannels, nextTabletId);
        }

    // Copy the state without legacy app data
        public State withoutLegacyAppData() {
            return new State(app, tab, null, mode, pendingAction, List.of(), List.of(), tabletId);
        }
    }

    // Define the redstone link control mode values
    public enum RedstoneLinkControlMode {
        BUTTON("button"),
        TOGGLE("toggle"),
        SLIDER("slider");

        // Redstone link control mode id
        private final String id;

        // Initialize the redstone link control mode
        RedstoneLinkControlMode(String id) {
            this.id = id;
        }

        // Get the id
        public String id() {
            return id;
        }

        // Create the redstone link control mode from id
        public static RedstoneLinkControlMode fromId(String id) {
            if (id != null) {
                for (RedstoneLinkControlMode mode : values()) {
                    if (mode.id.equalsIgnoreCase(id.trim())) return mode;
                }
            }
            return BUTTON;
        }
    }

    // Store the redstone link channel
    public record RedstoneLinkChannel(UUID id, String label,
                                     ResourceLocation firstItem, int firstColor,
                                     ResourceLocation secondItem, int secondColor,
                                     RedstoneLinkControlMode controlMode, int strength,
                                     boolean active) {
        private static final ResourceLocation EMPTY_FREQUENCY =
                ResourceLocation.withDefaultNamespace("air");

        // Initialize the redstone link channel
        public RedstoneLinkChannel {
            id = id == null ? UUID.randomUUID() : id;
            label = label == null || label.isBlank() ? "Redstone Link" : label.strip();
            if (label.length() > 48) label = label.substring(0, 48);
            firstItem = firstItem == null ? EMPTY_FREQUENCY : firstItem;
            secondItem = secondItem == null ? EMPTY_FREQUENCY : secondItem;
            firstColor = normalizeColor(firstColor);
            secondColor = normalizeColor(secondColor);
            controlMode = controlMode == null ? RedstoneLinkControlMode.BUTTON : controlMode;
            strength = Math.max(0, Math.min(15, strength));
        }

        // Initialize the redstone link channel
        public RedstoneLinkChannel(UUID id, String label,
                                   ResourceLocation firstItem, int firstColor,
                                   ResourceLocation secondItem, int secondColor,
                                   RedstoneLinkControlMode controlMode, int strength) {
            this(id, label, firstItem, firstColor, secondItem, secondColor,
                    controlMode, strength, controlMode == RedstoneLinkControlMode.TOGGLE
                            && strength > 0);
        }

        // Copy the redstone link channel with the label
        public RedstoneLinkChannel withLabel(String nextLabel) {
            return new RedstoneLinkChannel(id, nextLabel, firstItem, firstColor,
                    secondItem, secondColor, controlMode, strength, active);
        }

        // Copy the redstone link channel with the control mode
        public RedstoneLinkChannel withControlMode(RedstoneLinkControlMode nextMode) {
            return new RedstoneLinkChannel(id, label, firstItem, firstColor,
                    secondItem, secondColor, nextMode, strength, false);
        }

        // Copy the redstone link channel with the strength
        public RedstoneLinkChannel withStrength(int nextStrength) {
            return new RedstoneLinkChannel(id, label, firstItem, firstColor,
                    secondItem, secondColor, controlMode, nextStrength, active);
        }

        // Copy the redstone link channel with the active
        public RedstoneLinkChannel withActive(boolean nextActive) {
            return new RedstoneLinkChannel(id, label, firstItem, firstColor,
                    secondItem, secondColor, controlMode, strength, nextActive);
        }

        // Get the output strength
        public int outputStrength() {
            return switch (controlMode) {
                case BUTTON -> 0;
                case TOGGLE -> active ? strength : 0;
                case SLIDER -> strength;
            };
        }

        // Write the redstone link channel data
        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("Label", label);
            tag.putString("FirstItem", firstItem.toString());
            tag.putInt("FirstColor", firstColor);
            tag.putString("SecondItem", secondItem.toString());
            tag.putInt("SecondColor", secondColor);
            tag.putString("ControlMode", controlMode.id());
            tag.putInt("Strength", strength);
            tag.putBoolean("Active", active);
            return tag;
        }

        // Read the redstone link channel data
        static @Nullable RedstoneLinkChannel fromTag(CompoundTag tag) {
            if (tag == null || !tag.hasUUID("Id")) return null;
            ResourceLocation first = ResourceLocation.tryParse(tag.getString("FirstItem"));
            ResourceLocation second = ResourceLocation.tryParse(tag.getString("SecondItem"));
            if (first == null || second == null) return null;
            RedstoneLinkControlMode mode = RedstoneLinkControlMode.fromId(tag.getString("ControlMode"));
            int strength = tag.getInt("Strength");
            boolean active = tag.contains("Active") ? tag.getBoolean("Active")
                    : mode == RedstoneLinkControlMode.TOGGLE && strength > 0;
            if (!tag.contains("Active") && mode == RedstoneLinkControlMode.TOGGLE && strength > 0) {
                strength = 15;
            }
            return new RedstoneLinkChannel(tag.getUUID("Id"), tag.getString("Label"),
                    first, tag.contains("FirstColor") ? tag.getInt("FirstColor") : -1,
                    second, tag.contains("SecondColor") ? tag.getInt("SecondColor") : -1,
                    mode, strength, active);
        }

        // Normalize the color
        private static int normalizeColor(int col) {
            return col < 0 ? -1 : col & 0xFFFFFF;
        }
    }

    // Store the binding
    public record Binding(String type, @Nullable UUID subLevelId, BlockPos pos, String label) {
        // Initialize the binding
        public Binding {
            type = type == null ? "" : type.trim();
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
            label = label == null ? "" : label.trim();
        }

        // Write the binding data
        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Type", type);
            tag.putLong("Pos", pos.asLong());
            tag.putString("Label", label);
            if (subLevelId != null) {
                tag.putUUID("SubLevel", subLevelId);
            }
            return tag;
        }

        // Read the binding data
        static @Nullable Binding fromTag(CompoundTag tag) {
            if (tag == null || !tag.contains("Pos")) {
                return null;
            }
            return new Binding(tag.getString("Type"),
                    tag.hasUUID("SubLevel") ? tag.getUUID("SubLevel") : null,
                    BlockPos.of(tag.getLong("Pos")), tag.getString("Label"));
        }
    }
}
