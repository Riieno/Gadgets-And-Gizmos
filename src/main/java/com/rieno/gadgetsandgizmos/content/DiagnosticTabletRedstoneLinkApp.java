package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Store and drive tablet-owned Redstone Link channels through validated app actions
public final class DiagnosticTabletRedstoneLinkApp {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final ResourceLocation APP_ID = DiagnosticTabletData.appId("redstone_link");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet redstone link app
    private DiagnosticTabletRedstoneLinkApp() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the diagnostic tablet redstone link app
    static TabletActionHandler.Result execute(TabletActionContext ctx, TabletAction action) {
        // -----------------------------------------------------ACTION SETUP-----------------------------------------------------
        UUID tabletId = ctx.sourceTabletId();
        if (tabletId == null) return failure("The tablet identity is not available");
        List<DiagnosticTabletData.RedstoneLinkChannel> channels = new ArrayList<>(
                channels(ctx.player().server, tabletId));
        String actionId = action.actionId();
        String val = action.arguments().getOrDefault("value", "").strip();
        UUID pulseChannel = null;
        TabletActionHandler.Result res = quietSuccess();

        if ("refresh".equals(actionId) || "select".equals(actionId)) {
            sendSnapshot(ctx, channels);
            return res;
        }
        // -----------------------------------------------------LINK SCAN-----------------------------------------------------
        if ("bind_channel".equals(actionId)) {
            if (channels.size() >= DiagnosticTabletData.MAX_REDSTONE_LINK_CHANNELS) {
                return failure("A tablet can store up to "
                        + DiagnosticTabletData.MAX_REDSTONE_LINK_CHANNELS + " channels");
            }
            List<DiagnosticTabletData.Binding> selections = DiagnosticTabletAppStorage.selections(
                    ctx.player().server, tabletId, APP_ID);
            DiagnosticTabletData.Binding selected = selections.isEmpty() ? null : selections.getLast();
            BlockEntity target = selected == null ? null : SimulatedHelper.findLoadedBlockEntityExact(
                    ctx.player().level(), selected.subLevelId(), selected.pos());
            LinkBehaviour link = BlockEntityBehaviour.get(target, LinkBehaviour.TYPE);
            if (link == null) return failure("Reader mode must target a loaded Redstone Link");
            CompoundTag frequencies = new CompoundTag();
            link.write(frequencies, ctx.player().registryAccess(), false);
            ItemStack first = ItemStack.parseOptional(ctx.player().registryAccess(),
                    frequencies.getCompound("FrequencyFirst"));
            ItemStack second = ItemStack.parseOptional(ctx.player().registryAccess(),
                    frequencies.getCompound("FrequencyLast"));
            if (first.isEmpty() || second.isEmpty()) {
                return failure("Configure both Redstone Link frequencies before scanning it");
            }
            DiagnosticTabletData.RedstoneLinkChannel channel = channelFrom(selected.label(), first, second);
            if (channels.stream().anyMatch(existing -> sameFrequency(existing, channel))) {
                return failure("That frequency pair already exists on this tablet");
            }
            channels.add(channel);
            res = success("Added " + channel.label());
        // ------------------------------------CHANNEL CREATION------------------------------------
        } else if ("channel_create".equals(actionId)) {
            if (channels.size() >= DiagnosticTabletData.MAX_REDSTONE_LINK_CHANNELS) {
                return failure("The channel limit has been reached");
            }
            String[] parts = val.split("\\|", 7);
            if (parts.length < 7) return failure("Choose two frequencies, their colors, a mode and signal strength");
            ResourceLocation first = ResourceLocation.tryParse(parts[0]);
            int firstColor = parseColor(parts[1]);
            ResourceLocation second = ResourceLocation.tryParse(parts[2]);
            int secondColor = parseColor(parts[3]);
            if (first == null || second == null || !hasItem(ctx.player(), first)
                    || !hasItem(ctx.player(), second)) {
                return failure("Both frequency items must be in your inventory");
            }
            int strength = parseStrength(parts[5]);
            if (strength < 0) return failure("Signal strength must be between 0 and 15");
            DiagnosticTabletData.RedstoneLinkControlMode mode =
                    DiagnosticTabletData.RedstoneLinkControlMode.fromId(parts[4]);
            DiagnosticTabletData.RedstoneLinkChannel channel = new DiagnosticTabletData.RedstoneLinkChannel(
                    UUID.randomUUID(), parts[6], first, randomColor(firstColor),
                    second, randomColor(secondColor), mode, strength, false);
            if (channels.stream().anyMatch(existing -> sameFrequency(existing, channel))) {
                return failure("That frequency pair already exists on this tablet");
            }
            channels.add(channel);
            res = success("Smart-home control created");
        } else {
            // ------------------------------------CHANNEL EDITING------------------------------------
            UUID channelId = parseChannelId(val);
            int idx = indexOf(channels, channelId);
            if (idx < 0) return failure("The channel no longer exists");
            DiagnosticTabletData.RedstoneLinkChannel channel = channels.get(idx);
            String parameter = parameter(val);
            switch (actionId) {
                case "channel_update" -> {
                    String[] parts = val.split("\\|", 8);
                    if (parts.length < 8) return failure("Complete every channel field");
                    ResourceLocation first = ResourceLocation.tryParse(parts[1]);
                    int firstColor = parseColor(parts[2]);
                    ResourceLocation second = ResourceLocation.tryParse(parts[3]);
                    int secondColor = parseColor(parts[4]);
                    int strength = parseStrength(parts[6]);
                    if (first == null || second == null || strength < 0
                            || !hasItem(ctx.player(), first) || !hasItem(ctx.player(), second)) {
                        return failure("Choose valid frequency items and signal strength");
                    }
                    DiagnosticTabletData.RedstoneLinkControlMode mode =
                            DiagnosticTabletData.RedstoneLinkControlMode.fromId(parts[5]);
                    channels.set(idx, new DiagnosticTabletData.RedstoneLinkChannel(channel.id(),
                            parts[7], first, firstColor, second, secondColor, mode, strength,
                            mode == DiagnosticTabletData.RedstoneLinkControlMode.TOGGLE && channel.active()));
                }
                case "channel_mode" -> channels.set(idx, channel.withControlMode(
                        DiagnosticTabletData.RedstoneLinkControlMode.fromId(parameter)));
                case "channel_strength" -> {
                    int strength = parseStrength(parameter);
                    if (strength < 0) return failure("Signal strength must be between 0 and 15");
                    channels.set(idx, channel.withStrength(strength));
                }
                case "channel_rename" -> {
                    if (parameter.isBlank()) return failure("Enter a channel name");
                    channels.set(idx, channel.withLabel(parameter));
                }
                case "channel_remove" -> channels.remove(idx);
                case "channel_toggle" -> {
                    if (channel.controlMode() != DiagnosticTabletData.RedstoneLinkControlMode.TOGGLE) {
                        return failure("Set this control to Toggle mode first");
                    }
                    channels.set(idx, channel.withActive(!channel.active()));
                }
                case "channel_slider" -> {
                    if (channel.controlMode() != DiagnosticTabletData.RedstoneLinkControlMode.SLIDER) {
                        return failure("Set this control to Slider mode first");
                    }
                    int strength = parseStrength(parameter);
                    if (strength < 0) return failure("Slider output must be between 0 and 15");
                    channels.set(idx, channel.withStrength(strength));
                }
                case "channel_button" -> {
                    if (channel.controlMode() != DiagnosticTabletData.RedstoneLinkControlMode.BUTTON) {
                        return failure("Set this control to Button mode first");
                    }
                    pulseChannel = channel.id();
                }
                default -> {
                    return failure("Unknown Redstone Link action");
                }
            }
        }

        // -----------------------------------------------------STATE COMMIT-----------------------------------------------------
        saveChannels(ctx.player().server, tabletId, channels);
        syncRuntime(ctx, channels);
        if (pulseChannel != null) pulseRuntime(ctx, channels, pulseChannel);
        sendSnapshot(ctx, channels);
        return res;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the channels
    public static List<DiagnosticTabletData.RedstoneLinkChannel> channels(
            net.minecraft.server.MinecraftServer server, UUID tabletId) {
        if (server == null || tabletId == null) return List.of();
        ListTag tags = DiagnosticTabletAppStorage.data(server, tabletId, APP_ID)
                .getList("Channels", Tag.TAG_COMPOUND);
        List<DiagnosticTabletData.RedstoneLinkChannel> channels = new ArrayList<>();
        for (int idx = 0; idx < tags.size()
                && channels.size() < DiagnosticTabletData.MAX_REDSTONE_LINK_CHANNELS; idx++) {
            DiagnosticTabletData.RedstoneLinkChannel channel =
                    DiagnosticTabletData.RedstoneLinkChannel.fromTag(tags.getCompound(idx));
            if (channel != null) channels.add(channel);
        }
        return List.copyOf(channels);
    }

    // Save the channels
    private static void saveChannels(net.minecraft.server.MinecraftServer server, UUID tabletId,
                                     List<DiagnosticTabletData.RedstoneLinkChannel> channels) {
        TabletStorageApi.storage().updateApp(tabletId, APP_ID, data -> {
            ListTag tags = new ListTag();
            channels.stream().limit(DiagnosticTabletData.MAX_REDSTONE_LINK_CHANNELS)
                    .forEach(channel -> tags.add(channel.toTag()));
            data.put("Channels", tags);
            return data;
        });
    }

    // Get the snapshot
    public static CompoundTag snapshot(ServerPlayer player, UUID tabletId,
                                       List<DiagnosticTabletData.RedstoneLinkChannel> src) {
        CompoundTag root = new CompoundTag();
        root.putLong("UpdatedAt", System.currentTimeMillis());
        root.putInt("MaxChannels", DiagnosticTabletData.MAX_REDSTONE_LINK_CHANNELS);
        ListTag channels = new ListTag();
        for (DiagnosticTabletData.RedstoneLinkChannel channel : src) {
            CompoundTag tag = channel.toTag();
            tag.putString("Mode", channel.controlMode().id());
            tag.putBoolean("Active", channel.active());
            tag.putInt("Output", channel.outputStrength());
            tag.putString("FirstName", frequencyName(channel.firstItem(), channel.firstColor()));
            tag.putString("SecondName", frequencyName(channel.secondItem(), channel.secondColor()));
            channels.add(tag);
        }
        root.put("Channels", channels);
        root.put("FrequencyItems", inventoryFrequencies(player));
        CompoundTag settings = DiagnosticTabletAppStorage.data(player.server, tabletId, APP_ID)
                .getCompound("Settings");
        root.put("Settings", settings.copy());
        return root;
    }

    // Get the frequency stack
    static ItemStack frequencyStack(ResourceLocation itemId, int col) {
        ItemStack stack = BuiltInRegistries.ITEM.getOptional(itemId)
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (!stack.isEmpty() && col >= 0) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(col, false));
        }
        return stack;
    }

    // Get the inventory frequencies
    private static ListTag inventoryFrequencies(ServerPlayer player) {
        Map<String, CompoundTag> unique = new LinkedHashMap<>();
        Container inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            int col = color(stack);
            String key = id + ":" + col;
            unique.computeIfAbsent(key, ignored -> {
                CompoundTag item = new CompoundTag();
                item.putString("Id", id.toString());
                item.putString("Name", stack.getHoverName().getString());
                item.putInt("Color", col);
                return item;
            });
            if (unique.size() >= 96) break;
        }
        ListTag items = new ListTag();
        unique.values().forEach(items::add);
        return items;
    }

    // Sync the runtime
    private static void syncRuntime(TabletActionContext ctx,
                                    List<DiagnosticTabletData.RedstoneLinkChannel> channels) {
        if (ctx.placedSource()) {
            BlockEntity src = SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(),
                    ctx.sourceSubLevelId(), ctx.sourceBlockPos());
            if (src instanceof DiagnosticTabletBlockEntity tablet) {
                DiagnosticTabletRedstoneLinkRuntime.syncPlaced(ctx.player(), tablet, channels);
            }
        } else {
            DiagnosticTabletRedstoneLinkRuntime.syncItem(ctx.player(), ctx.tablet(), channels);
        }
    }

    // Handle the pulse runtime
    private static void pulseRuntime(TabletActionContext ctx,
                                     List<DiagnosticTabletData.RedstoneLinkChannel> channels,
                                     UUID channelId) {
        if (ctx.placedSource()) {
            BlockEntity src = SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(),
                    ctx.sourceSubLevelId(), ctx.sourceBlockPos());
            if (src instanceof DiagnosticTabletBlockEntity tablet) {
                DiagnosticTabletRedstoneLinkRuntime.pulsePlaced(
                        ctx.player(), tablet, channels, channelId);
            }
        } else {
            DiagnosticTabletRedstoneLinkRuntime.pulseItem(
                    ctx.player(), ctx.tablet(), channels, channelId);
        }
    }

    // Get the channel
    private static DiagnosticTabletData.RedstoneLinkChannel channelFrom(
            String label, ItemStack first, ItemStack second) {
        return new DiagnosticTabletData.RedstoneLinkChannel(UUID.randomUUID(), label,
                BuiltInRegistries.ITEM.getKey(first.getItem()), color(first),
                BuiltInRegistries.ITEM.getKey(second.getItem()), color(second),
                DiagnosticTabletData.RedstoneLinkControlMode.BUTTON, 15, false);
    }

    // Check if this has item
    private static boolean hasItem(ServerPlayer player, ResourceLocation id) {
        if (player.isCreative()) return BuiltInRegistries.ITEM.containsKey(id);
        Container inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id)) return true;
        }
        return false;
    }

    // Get the color
    private static int color(ItemStack stack) {
        DyedItemColor col = stack.get(DataComponents.DYED_COLOR);
        return col == null ? -1 : col.rgb();
    }

    // Parse the color
    private static int parseColor(String val) {
        try {
            int col = Integer.parseInt(val);
            return col < 0 ? -1 : col & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    // Get the random color
    private static int randomColor(int configured) {
        return configured >= 0 ? configured & 0xFFFFFF
                : java.util.concurrent.ThreadLocalRandom.current().nextInt(0x1000000);
    }

    // Get the frequency name
    private static String frequencyName(ResourceLocation itemId, int col) {
        ItemStack stack = frequencyStack(itemId, col);
        return stack.isEmpty() ? itemId.toString() : stack.getHoverName().getString();
    }

    // Check if this uses the same frequency
    private static boolean sameFrequency(DiagnosticTabletData.RedstoneLinkChannel first,
                                         DiagnosticTabletData.RedstoneLinkChannel second) {
        return first.firstItem().equals(second.firstItem()) && first.firstColor() == second.firstColor()
                && first.secondItem().equals(second.secondItem())
                && first.secondColor() == second.secondColor();
    }

    // Parse the channel id
    private static UUID parseChannelId(String val) {
        String id = val;
        int separator = val.indexOf('|');
        if (separator >= 0) id = val.substring(0, separator);
        try {
            return UUID.fromString(id.strip());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // Get the parameter
    private static String parameter(String val) {
        int separator = val.indexOf('|');
        return separator < 0 ? "" : val.substring(separator + 1).strip();
    }

    // Parse the strength
    private static int parseStrength(String val) {
        try {
            int strength = Integer.parseInt(val.strip());
            return strength >= 0 && strength <= 15 ? strength : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    // Get the index
    private static int indexOf(List<DiagnosticTabletData.RedstoneLinkChannel> channels, UUID id) {
        if (id == null) return -1;
        for (int idx = 0; idx < channels.size(); idx++) {
            if (id.equals(channels.get(idx).id())) return idx;
        }
        return -1;
    }

    // Send the snapshot
    private static void sendSnapshot(TabletActionContext ctx,
                                     List<DiagnosticTabletData.RedstoneLinkChannel> channels) {
        PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletAppSnapshotPayload(
                APP_ID, ctx, snapshot(ctx.player(), ctx.sourceTabletId(), channels)));
    }

    // Get the quiet success
    private static TabletActionHandler.Result quietSuccess() {
        return TabletActionHandler.Result.success(Component.empty());
    }

    // Create a successful diagnostic tablet redstone link app
    private static TabletActionHandler.Result success(String msg) {
        return TabletActionHandler.Result.success(Component.literal(msg));
    }

    // Create a failed diagnostic tablet redstone link app
    private static TabletActionHandler.Result failure(String msg) {
        return TabletActionHandler.Result.failure(Component.literal(msg));
    }
}
