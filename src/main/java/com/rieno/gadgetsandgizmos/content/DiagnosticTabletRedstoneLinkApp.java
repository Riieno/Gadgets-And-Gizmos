package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.text.LocalizedText;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.item.RegisteredItemCatalog;
import com.rieno.gadgetsandgizmos.lib.tablet.ControlCardStyle;
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
import net.minecraft.nbt.TagParser;
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
        if (tabletId == null) return failure(Component.translatable("createthrusters.text.the_tablet_identity_is_not_available"));
        List<DiagnosticTabletData.RedstoneLinkChannel> channels = new ArrayList<>(
                channels(ctx.player().server, tabletId));
        String actionId = action.actionId();
        String val = action.arguments().getOrDefault("value", "").strip();
        UUID pulseChannel = null;
        TabletActionHandler.Result res = quietSuccess();

        if("card_reorder".equals(actionId)){
            var ordered = new ArrayList<DiagnosticTabletData.RedstoneLinkChannel>();
            var ids = new java.util.HashSet<UUID>();
            for(String part : val.split("\\|")){
                UUID id = parseChannelId(part);
                int idx = indexOf(channels, id);
                if(idx < 0 || !ids.add(id)) return failure(Component.translatable("createthrusters.text.invalid_card_order"));
                ordered.add(channels.get(idx));
            }
            if(ordered.size() != channels.size()) return failure(Component.translatable("createthrusters.text.include_every_card_in_the_new_order"));
            saveChannels(ctx.player().server, tabletId, ordered);
            sendSnapshot(ctx, ordered);
            return quietSuccess();
        }

        if(java.util.Set.of("media_previous", "media_pause", "media_next").contains(actionId)){
            if(!mediaInstalled(ctx.player(), tabletId)) return failure(Component.translatable("createthrusters.text.install_media_to_use_the_music_controls"));
            var media = DiagnosticTabletMediaApp.execute(ctx, new TabletAction(DiagnosticTabletMediaApp.ID, "player",
                    actionId.substring("media_".length()), action.arguments()));
            sendSnapshot(ctx, channels);
            return media;
        }

        if("card_save".equals(actionId)){
            var saved = saveCard(ctx, val, channels);
            if(!saved.success()){
                var data = snapshot(ctx.player(), tabletId, channels);
                LocalizedText.put(data, "Error", saved.message());
                PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletAppSnapshotPayload(APP_ID, ctx, data));
            }
            return saved;
        }

        if ("refresh".equals(actionId) || "select".equals(actionId)) {
            sendSnapshot(ctx, channels);
            return res;
        }
        // -----------------------------------------------------LINK SCAN-----------------------------------------------------
        if ("bind_channel".equals(actionId)) {
            if (channels.size() >= DiagnosticTabletData.MAX_REDSTONE_LINK_CHANNELS) {
                return failure(Component.translatable("createthrusters.text.a_tablet_can_store_up_to_1_s_channels", DiagnosticTabletData.MAX_REDSTONE_LINK_CHANNELS));
            }
            List<DiagnosticTabletData.Binding> selections = DiagnosticTabletAppStorage.selections(
                    ctx.player().server, tabletId, APP_ID);
            DiagnosticTabletData.Binding selected = selections.isEmpty() ? null : selections.getLast();
            BlockEntity target = selected == null ? null : SimulatedHelper.findLoadedBlockEntityExact(
                    ctx.player().level(), selected.subLevelId(), selected.pos());
            LinkBehaviour link = BlockEntityBehaviour.get(target, LinkBehaviour.TYPE);
            if (link == null) return failure(Component.translatable("createthrusters.text.reader_mode_must_target_a_loaded_redstone_link"));
            CompoundTag frequencies = new CompoundTag();
            link.write(frequencies, ctx.player().registryAccess(), false);
            ItemStack first = ItemStack.parseOptional(ctx.player().registryAccess(),
                    frequencies.getCompound("FrequencyFirst"));
            ItemStack second = ItemStack.parseOptional(ctx.player().registryAccess(),
                    frequencies.getCompound("FrequencyLast"));
            DiagnosticTabletData.RedstoneLinkChannel channel = channelFrom(selected.label(), first, second);
            if (channels.stream().anyMatch(existing -> sameFrequency(existing, channel))) {
                return failure(Component.translatable("createthrusters.text.that_frequency_pair_already_exists_on_this_tablet"));
            }
            channels.add(channel);
            res = success(Component.translatable("createthrusters.text.added_1_s", channel.label()));
        // ------------------------------------CHANNEL CREATION------------------------------------
        } else if ("channel_create".equals(actionId)) {
            if (channels.size() >= DiagnosticTabletData.MAX_REDSTONE_LINK_CHANNELS) {
                return failure(Component.translatable("createthrusters.text.the_channel_limit_has_been_reached"));
            }
            String[] parts = val.split("\\|", 7);
            if (parts.length < 7) return failure(Component.translatable("createthrusters.text.choose_two_frequencies_their_colors_a_mode_and_signal_strength"));
            ResourceLocation first = frequencyId(parts[0]);
            int firstColor = parseColor(parts[1]);
            ResourceLocation second = frequencyId(parts[2]);
            int secondColor = parseColor(parts[3]);
            if (!validFrequency(first) || !validFrequency(second)) {
                return failure(Component.translatable("createthrusters.text.choose_two_registered_frequency_items"));
            }
            int strength = parseStrength(parts[5]);
            if (strength < 0) return failure(Component.translatable("createthrusters.text.signal_strength_must_be_between_0_and_15"));
            DiagnosticTabletData.RedstoneLinkControlMode mode =
                    DiagnosticTabletData.RedstoneLinkControlMode.fromId(parts[4]);
            DiagnosticTabletData.RedstoneLinkChannel channel = new DiagnosticTabletData.RedstoneLinkChannel(
                    UUID.randomUUID(), parts[6], first, randomColor(firstColor),
                    second, randomColor(secondColor), mode, strength, false, 0);
            if (channels.stream().anyMatch(existing -> sameFrequency(existing, channel))) {
                return failure(Component.translatable("createthrusters.text.that_frequency_pair_already_exists_on_this_tablet"));
            }
            channels.add(channel);
            res = success(Component.translatable("createthrusters.text.smart_home_control_created"));
        } else {
            // ------------------------------------CHANNEL EDITING------------------------------------
            UUID channelId = parseChannelId(val);
            int idx = indexOf(channels, channelId);
            if (idx < 0) return failure(Component.translatable("createthrusters.text.the_channel_no_longer_exists"));
            DiagnosticTabletData.RedstoneLinkChannel channel = channels.get(idx);
            String parameter = parameter(val);
            switch (actionId) {
                case "channel_update" -> {
                    String[] parts = val.split("\\|", 8);
                    if (parts.length < 8) return failure(Component.translatable("createthrusters.text.complete_every_channel_field"));
                    ResourceLocation first = frequencyId(parts[1]);
                    int firstColor = parseColor(parts[2]);
                    ResourceLocation second = frequencyId(parts[3]);
                    int secondColor = parseColor(parts[4]);
                    int strength = parseStrength(parts[6]);
                    if (!validFrequency(first) || !validFrequency(second) || strength < 0) {
                        return failure(Component.translatable("createthrusters.text.choose_valid_frequency_items_and_signal_strength"));
                    }
                    DiagnosticTabletData.RedstoneLinkControlMode mode =
                            DiagnosticTabletData.RedstoneLinkControlMode.fromId(parts[5]);
                    channels.set(idx, new DiagnosticTabletData.RedstoneLinkChannel(channel.id(),
                            parts[7], first, firstColor, second, secondColor, mode, strength,
                            mode == DiagnosticTabletData.RedstoneLinkControlMode.TOGGLE && channel.active(), channel.sliderValue()));
                }
                case "channel_mode" -> channels.set(idx, channel.withControlMode(
                        DiagnosticTabletData.RedstoneLinkControlMode.fromId(parameter)));
                case "channel_strength" -> {
                    if(channel.controlMode() == DiagnosticTabletData.RedstoneLinkControlMode.SLIDER) return failure(Component.translatable("createthrusters.text.use_the_slider_to_change_its_output"));
                    int strength = parseStrength(parameter);
                    if (strength < 0) return failure(Component.translatable("createthrusters.text.signal_strength_must_be_between_0_and_15"));
                    channels.set(idx, channel.withStrength(strength));
                }
                case "channel_rename" -> {
                    if (parameter.isBlank()) return failure(Component.translatable("createthrusters.text.enter_a_channel_name"));
                    channels.set(idx, channel.withLabel(parameter));
                }
                case "channel_remove" -> channels.remove(idx);
                case "channel_toggle" -> {
                    if (channel.controlMode() != DiagnosticTabletData.RedstoneLinkControlMode.TOGGLE) {
                        return failure(Component.translatable("createthrusters.text.set_this_control_to_toggle_mode_first"));
                    }
                    channels.set(idx, channel.withActive(!channel.active()));
                }
                case "channel_slider" -> {
                    if (channel.controlMode() != DiagnosticTabletData.RedstoneLinkControlMode.SLIDER) {
                        return failure(Component.translatable("createthrusters.text.set_this_control_to_slider_mode_first"));
                    }
                    int strength = parseStrength(parameter);
                    if (strength < 0) return failure(Component.translatable("createthrusters.text.slider_output_must_be_between_0_and_15"));
                    channels.set(idx, channel.withSliderValue(strength));
                }
                case "channel_button" -> {
                    if (channel.controlMode() != DiagnosticTabletData.RedstoneLinkControlMode.BUTTON) {
                        return failure(Component.translatable("createthrusters.text.set_this_control_to_button_mode_first"));
                    }
                    pulseChannel = channel.id();
                }
                default -> {
                    return failure(Component.translatable("createthrusters.text.unknown_redstone_link_action"));
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
        saveChannels(server, tabletId, channels, null, null);
    }

    // Commit channel data and appearance together while removing deleted cards
    private static void saveChannels(net.minecraft.server.MinecraftServer server, UUID tabletId,
                                     List<DiagnosticTabletData.RedstoneLinkChannel> channels, UUID cardId, ControlCardStyle style){
        TabletStorageApi.storage().updateApp(tabletId, APP_ID, data -> {
            ListTag tags = new ListTag();
            channels.stream().limit(DiagnosticTabletData.MAX_REDSTONE_LINK_CHANNELS)
                    .forEach(channel -> tags.add(channel.toTag()));
            data.put("Channels", tags);
            var cards = data.getCompound("Cards").copy();
            for(String key : new ArrayList<>(cards.getAllKeys())){
                if(channels.stream().noneMatch(channel -> channel.id().toString().equals(key))) cards.remove(key);
            }
            if(cardId != null && style != null) cards.put(cardId.toString(), style.toTag());
            data.put("Cards", cards);
            return data;
        });
    }

    // Validate the painted editor's complete draft before changing any channel or presentation data
    private static TabletActionHandler.Result saveCard(TabletActionContext ctx, String val,
                                                       List<DiagnosticTabletData.RedstoneLinkChannel> channels){
        CompoundTag draft;
        try{ draft = TagParser.parseTag(val); }
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException err){ return failure(Component.translatable("createthrusters.text.invalid_control_card")); }
        UUID id = draft.hasUUID("Id") ? draft.getUUID("Id") : UUID.randomUUID();
        int idx = indexOf(channels, id);
        if(draft.hasUUID("Id") && idx < 0) return failure(Component.translatable("createthrusters.text.the_channel_no_longer_exists"));
        if(idx < 0 && channels.size() >= DiagnosticTabletData.MAX_REDSTONE_LINK_CHANNELS) return failure(Component.translatable("createthrusters.text.the_channel_limit_has_been_reached"));
        var first = frequencyId(draft.getString("FirstItem"));
        var second = frequencyId(draft.getString("SecondItem"));
        var style = ControlCardStyle.fromTag(draft.getCompound("Card"));
        if(!validFrequency(first) || !validFrequency(second) || !RegisteredItemCatalog.contains(style.icon())){
            return failure(Component.translatable("createthrusters.text.choose_registered_frequency_items_and_a_card_icon"));
        }
        int strength = draft.getInt("Strength");
        String modeId = draft.getString("Mode");
        if(strength < 0 || strength > 15 || !java.util.Set.of("button", "toggle", "slider", "media", "clock").contains(modeId)) return failure(Component.translatable("createthrusters.text.invalid_control_type_or_strength"));
        var mode = DiagnosticTabletData.RedstoneLinkControlMode.fromId(modeId);
        var prev = idx >= 0 ? channels.get(idx) : null;
        // Appearance edits must not overwrite a newer live slider value
        if(mode == DiagnosticTabletData.RedstoneLinkControlMode.SLIDER) strength = prev == null ? 15 : prev.strength();
        var channel = new DiagnosticTabletData.RedstoneLinkChannel(id, draft.getString("Label"), first,
                parseColor(Integer.toString(draft.getInt("FirstColor"))), second, parseColor(Integer.toString(draft.getInt("SecondColor"))),
                mode, strength, prev != null && mode == DiagnosticTabletData.RedstoneLinkControlMode.TOGGLE && prev.active(),
                prev == null ? 0 : prev.sliderValue());
        if(channels.stream().anyMatch(existing -> !existing.id().equals(id) && sameFrequency(existing, channel))) return failure(Component.translatable("createthrusters.text.that_frequency_pair_already_exists_on_this_tablet"));
        if(idx < 0) channels.add(channel); else channels.set(idx, channel);
        saveChannels(ctx.player().server, ctx.sourceTabletId(), channels, id, style);
        syncRuntime(ctx, channels);
        var snapshot = snapshot(ctx.player(), ctx.sourceTabletId(), channels);
        snapshot.putUUID("SavedCard", id);
        PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletAppSnapshotPayload(APP_ID, ctx, snapshot));
        return quietSuccess();
    }

    // Get the snapshot
    public static CompoundTag snapshot(ServerPlayer player, UUID tabletId,
                                       List<DiagnosticTabletData.RedstoneLinkChannel> src) {
        CompoundTag root = new CompoundTag();
        root.putLong("UpdatedAt", System.currentTimeMillis());
        root.putInt("MaxChannels", DiagnosticTabletData.MAX_REDSTONE_LINK_CHANNELS);
        root.putBoolean("MediaInstalled", mediaInstalled(player, tabletId));
        if(root.getBoolean("MediaInstalled")) root.put("Media", DiagnosticTabletDatabase.forServer(player.server)
                .app(tabletId, DiagnosticTabletMediaApp.ID).getCompound("Playback").copy());
        var cards = DiagnosticTabletAppStorage.data(player.server, tabletId, APP_ID).getCompound("Cards");
        ListTag channels = new ListTag();
        for (DiagnosticTabletData.RedstoneLinkChannel channel : src) {
            CompoundTag tag = channel.toTag();
            tag.putString("Mode", channel.controlMode().id());
            tag.putBoolean("Active", channel.active());
            if(channel.controlMode() == DiagnosticTabletData.RedstoneLinkControlMode.SLIDER) tag.putInt("Strength", channel.sliderValue());
            tag.putInt("Output", channel.outputStrength());
            tag.putString("FirstName", frequencyName(channel.firstItem(), channel.firstColor()));
            tag.putString("SecondName", frequencyName(channel.secondItem(), channel.secondColor()));
            var card = cards.contains(channel.id().toString()) ? ControlCardStyle.fromTag(cards.getCompound(channel.id().toString()))
                    : new ControlCardStyle(ControlCardStyle.Size.SINGLE, 0x303030, channel.firstItem());
            tag.put("Card", card.toTag());
            channels.add(tag);
        }
        root.put("Channels", channels);
        root.put("FrequencyItems", inventoryFrequencies(player));
        CompoundTag settings = DiagnosticTabletAppStorage.data(player.server, tabletId, APP_ID)
                .getCompound("Settings");
        root.put("Settings", settings.copy());
        return root;
    }

    // Gate the embedded player using the installed Media app and its existing entitlement rules
    private static boolean mediaInstalled(ServerPlayer player, UUID tabletId){
        return tabletId != null && DiagnosticTabletDatabase.forServer(player.server).installedApps(tabletId).contains(DiagnosticTabletMediaApp.ID)
                && com.rieno.gadgetsandgizmos.lib.tablet.TabletAppAccess.canUse(
                com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry.definition(DiagnosticTabletMediaApp.ID),
                com.rieno.gadgetsandgizmos.lib.tablet.TabletAppPurchaseScope.PLAYER, player.getUUID(), tabletId);
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
        if(first.controlMode() == DiagnosticTabletData.RedstoneLinkControlMode.MEDIA || second.controlMode() == DiagnosticTabletData.RedstoneLinkControlMode.MEDIA
                || first.controlMode() == DiagnosticTabletData.RedstoneLinkControlMode.CLOCK || second.controlMode() == DiagnosticTabletData.RedstoneLinkControlMode.CLOCK) return false;
        if(first.firstItem().equals(ResourceLocation.withDefaultNamespace("air")) && first.secondItem().equals(ResourceLocation.withDefaultNamespace("air"))) return false;
        return first.firstItem().equals(second.firstItem()) && first.firstColor() == second.firstColor()
                && first.secondItem().equals(second.secondItem())
                && first.secondColor() == second.secondColor();
    }

    private static ResourceLocation frequencyId(String val){ return val.isBlank() ? ResourceLocation.withDefaultNamespace("air") : ResourceLocation.tryParse(val); }
    static boolean validFrequency(ResourceLocation id){ return id != null && (id.equals(ResourceLocation.withDefaultNamespace("air")) || RegisteredItemCatalog.contains(id)); }

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
    private static TabletActionHandler.Result success(Component msg) {
        return TabletActionHandler.Result.success(msg);
    }

    // Create a failed diagnostic tablet redstone link app
    private static TabletActionHandler.Result failure(Component msg) {
        return TabletActionHandler.Result.failure(msg);
    }
}
