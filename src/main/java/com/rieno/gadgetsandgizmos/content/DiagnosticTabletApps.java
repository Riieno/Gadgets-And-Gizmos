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
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletTabDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

// Register tablet pages and route their actions to the correct runtime
public final class DiagnosticTabletApps {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Set<String> KEYBOARD_ACTIONS = Set.of(
            "rename_dock", "add_friend", "accept_friend", "remove_friend",
            "request_items", "route", "schedule", "navigate", "follow", "climb", "channel_rename",
            "tablet_rename", "app_setting", "journey_search",
            "rename_logistics_run", "rename_controller");
    private static final TabletAppDefinition RDP = definition("rdp", "Remote Desktop",
            "Secure remote sessions for Advanced Controllers", 0xFF00B8D4,
            List.of(tab("devices", "Devices", "refresh", "remote_control",
                    "open_graph", "open_plotter", "rename_controller",
                    "remove_controller")));
    private static final TabletAppDefinition SCM = definition("scm", "Ship Control",
            "Ship, dock, logistics and landing-zone control", 0xFFFFB74D,
            List.of(
                    tab("ships", "Ships", "refresh", "select_target", "manual_control",
                            "navigate", "follow", "climb", "hover", "initialize", "dock"),
                    tab("dashboard", "Overview", "refresh", "select_target", "initialize", "hover", "stop"),
                    tab("landing", "Landing Zones", "landing_zone", "select_landing_zone",
                            "remove_landing_zone"),
                    tab("logistics", "Logistics", "connected_inventories", "request_items",
                            "schedule", "begin_logistics_run", "select_logistics_run",
                            "edit_logistics_run", "rename_logistics_run",
                            "delete_logistics_run", "configure_run"),
                    tab("manage", "Manage", "rename_dock", "remove_target")));
    private static final TabletAppDefinition BLOCK360 = definition("block360", "block360",
            "Private circles and mutual location sharing", 0xFF7AC943,
            List.of(tab("circle", "Circle", "refresh", "add_friend", "accept_friend", "remove_friend",
                            "navigate_friend"),
                    tab("places", "Places")), Set.of("friend_location"));
    private static final TabletAppDefinition JOURNEY = definition("journey", "Journey",
            "Live train and ship journey planning", 0xFF00A88F,
            List.of(tab("search", "Search", "journey_search", "refresh"),
                    tab("live", "Live", "refresh"),
                    tab("saved", "Saved", "journey_save", "journey_remove")));
    private static final TabletAppDefinition REDSTONE_LINK = definition("redstone_link", "Redstone Link",
            "Smart-home controls for wireless redstone", 0xFF7C4DFF,
            List.of(tab("home", "Home", "refresh", "channel_button", "channel_toggle", "channel_slider"),
                    tab("devices", "Devices", "channel_create", "bind_channel", "channel_mode",
                            "channel_strength", "channel_rename", "channel_remove")));
    private static final TabletAppDefinition SETTINGS = definition("settings", "Settings",
            "Tablet identity, appearance and applications", 0xFF90A4AE,
            List.of(tab("tablet", "Tablet", "tablet_rename", "wallpaper_set"),
                    tab("apps", "Apps", "app_install", "app_uninstall"),
                    tab("about", "About", "refresh")));
    private static final TabletAppDefinition GG_AUTO = definition("gg_auto", "G&G Auto",
            "On-ship navigation and telemetry", 0xFF5AC8FA,
            List.of(tab("drive", "Drive", "refresh", "navigate", "hover", "dock"),
                    tab("stats", "Stats", "refresh")));
    private static final TabletAppDefinition NFC = definition("nfc", "NFC",
            "Inspect and directly control nearby blocks", 0xFF90A4AE,
            List.of(tab("inspect", "Inspect", "refresh", "nfc_scan", "nfc_signal")));
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether diagnostic tablet apps are registered
    private static boolean registered;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet apps
    private DiagnosticTabletApps() {
    }

    // Register the diagnostic tablet apps
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        TabletAppRegistry.registerIfAbsent(RDP, DiagnosticTabletApps::execute);
        TabletAppRegistry.registerIfAbsent(SCM, DiagnosticTabletApps::execute);
        TabletAppRegistry.registerIfAbsent(BLOCK360, DiagnosticTabletApps::execute);
        TabletAppRegistry.registerIfAbsent(JOURNEY, DiagnosticTabletApps::execute);
        TabletAppRegistry.registerIfAbsent(REDSTONE_LINK, DiagnosticTabletApps::execute);
        TabletAppRegistry.registerIfAbsent(SETTINGS, DiagnosticTabletApps::execute);
        TabletAppRegistry.registerIfAbsent(GG_AUTO, DiagnosticTabletApps::execute);
        TabletAppRegistry.registerIfAbsent(NFC, DiagnosticTabletApps::execute);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is canonical definition
    public static boolean isCanonicalDefinition(TabletAppDefinition definition, String id) {
        if (definition == null || id == null) return false;
        return switch (id) {
            case "rdp" -> definition == RDP;
            case "scm" -> definition == SCM;
            case "block360" -> definition == BLOCK360;
            case "journey" -> definition == JOURNEY;
            case "redstone_link" -> definition == REDSTONE_LINK;
            case "settings" -> definition == SETTINGS;
            case "gg_auto" -> definition == GG_AUTO;
            case "nfc" -> definition == NFC;
            default -> false;
        };
    }

    // Get the definition
    private static TabletAppDefinition definition(String id, String title, String description,
                                                  int accent, List<TabletTabDefinition> tabs) {
        return definition(id, title, description, accent, tabs, Set.of());
    }

    // Get the definition
    private static TabletAppDefinition definition(String id, String title, String description,
                                                  int accent, List<TabletTabDefinition> tabs,
                                                  Set<String> sharedKeys) {
        ResourceLocation appId = DiagnosticTabletData.appId(id);
        return new TabletAppDefinition(appId, Component.literal(title),
                Component.literal(description), accent, tabs, null, sharedKeys);
    }

    // Get the tab
    private static TabletTabDefinition tab(String id, String title, String... actions) {
        Set<String> keyboardActions = Arrays.stream(actions)
                .filter(KEYBOARD_ACTIONS::contains).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new TabletTabDefinition(id, Component.literal(title), List.of(actions), keyboardActions);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the diagnostic tablet apps
    private static TabletActionHandler.Result execute(TabletActionContext ctx, TabletAction action) {
        if ("app_setting".equals(action.actionId()) && ctx.sourceTabletId() != null) {
            String val = action.arguments().getOrDefault("value", "");
            String[] setting = val.split("\\|", 2);
            if (setting.length != 2 || setting[0].isBlank()) return failure("Invalid app setting");
            TabletStorageApi.storage().updateApp(
                    ctx.sourceTabletId(), action.appId(), data -> {
                        CompoundTag settings = data.getCompound("Settings").copy();
                        settings.putString(setting[0].strip(), setting[1].strip());
                        data.put("Settings", settings);
                        return data;
                    });
            return success("App setting updated");
        }
        BlockEntity target = ctx.blockPos() == null ? null
                : SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(),
                ctx.subLevelId(), ctx.blockPos());
        if (target == null && SCM.id().equals(action.appId()) && ctx.blockPos() != null) {
            target = SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(),
                    ctx.subLevelId(), ctx.blockPos().below());
        }
        ResourceLocation app = action.appId();
        if (RDP.id().equals(app)) {
            return DiagnosticTabletRdpApp.execute(ctx, action,
                    target instanceof AdvancedContraptionControllerBlockEntity controller
                            ? controller : null);
        }
        if (SCM.id().equals(app)) {
            return DiagnosticTabletScmActions.execute(ctx, action, target);
        }
        if (BLOCK360.id().equals(app)) {
            return DiagnosticTabletFriendDatabase.handle(ctx, action);
        }
        if (JOURNEY.id().equals(app)) {
            return DiagnosticTabletJourneyApp.execute(ctx, action);
        }
        if (REDSTONE_LINK.id().equals(app)) {
            return DiagnosticTabletRedstoneLinkApp.execute(ctx, action);
        }
        if (SETTINGS.id().equals(app)) {
            return DiagnosticTabletSettingsApp.execute(ctx, action);
        }
        if (GG_AUTO.id().equals(app)) {
            return DiagnosticTabletAutoApp.execute(ctx, action);
        }
        if (NFC.id().equals(app)) {
            return DiagnosticTabletNfcApp.execute(ctx, action);
        }
        return failure("Unknown tablet app");
    }

    // Dispatch the diagnostic tablet apps
    public static TabletActionHandler.Result dispatch(ServerPlayer player, ItemStack tablet,
                                                       DiagnosticTabletData.State state,
                                                       TabletAction action) {
        return dispatch(player, tablet, state, action, false, state.tabletId(), null, null);
    }

    // Dispatch the diagnostic tablet apps
    public static TabletActionHandler.Result dispatch(ServerPlayer player, ItemStack tablet,
                                                       DiagnosticTabletData.State state,
                                                       TabletAction action, boolean placedSource,
                                                       java.util.UUID sourceTabletId,
                                                       java.util.UUID sourceSubLevelId,
                                                       net.minecraft.core.BlockPos sourceBlockPos) {
        TabletActionHandler handler = TabletAppRegistry.handler(action.appId());
        if (handler == null) {
            return failure("Unknown tablet app");
        }
        UUID tabletId = state.tabletId() == null ? sourceTabletId : state.tabletId();
        DiagnosticTabletDatabase database = DiagnosticTabletDatabase.forServer(player.server);
        if (tabletId != null) database.importLegacy(tabletId, state);
        DiagnosticTabletData.Binding binding = tabletId == null ? null
                : DiagnosticTabletAppStorage.selectedBinding(player.server, tabletId, action.appId());
        TabletActionContext ctx = new TabletActionContext(player, tablet,
                binding == null ? null : binding.subLevelId(),
                binding == null ? null : binding.pos(), placedSource, sourceTabletId,
                sourceSubLevelId, sourceBlockPos);
        TabletActionHandler.Result res = handler.execute(ctx, action);
        TabletAppDefinition definition = TabletAppRegistry.definition(action.appId());
        if (tabletId != null && definition != null
                && !isCanonicalDefinition(definition, action.appId().getPath())) {
            PacketDistributor.sendToPlayer(player, new DiagnosticTabletAppSnapshotPayload(
                    action.appId(), ctx, TabletStorageApi.storage().app(tabletId, action.appId())));
        }
        return res;
    }

    // Resolve the bound target
    static BlockEntity resolveBoundTarget(
            net.minecraft.world.level.Level level, DiagnosticTabletData.Binding binding) {
        if (level == null || binding == null) {
            return null;
        }
        BlockEntity target = SimulatedHelper.findLoadedBlockEntityExact(
                level, binding.subLevelId(), binding.pos());
        if (target == null && "scm".equals(binding.type())) {
            target = SimulatedHelper.findLoadedBlockEntityExact(
                    level, binding.subLevelId(), binding.pos().below());
        }
        return target;
    }

    // Create a successful diagnostic tablet apps
    private static TabletActionHandler.Result success(String msg) {
        return TabletActionHandler.Result.success(Component.literal(msg));
    }

    // Create a failed diagnostic tablet apps
    private static TabletActionHandler.Result failure(String msg) {
        return TabletActionHandler.Result.failure(Component.literal(msg));
    }
}
