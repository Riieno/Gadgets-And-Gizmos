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
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDescriptions;
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
            "tablet_rename", "app_setting", "journey_search", "rename_controller");
    private static final TabletAppDefinition RDP = definition("rdp", "createthrusters.text.remote_desktop",
            "createthrusters.text.secure_remote_sessions_for_advanced_controllers", 0xFF00B8D4,
            List.of(tab("devices", "createthrusters.text.devices", "refresh", "remote_control",
                    "open_graph", "open_plotter", "rename_controller",
                    "remove_controller", "controller_color")));
    private static final TabletAppDefinition SCM = definition("scm", "createthrusters.configuration.ship_control",
            "createthrusters.text.ship_dock_logistics_and_landing_zone_control", 0xFFFFB74D,
            List.of(
                    tab("ships", "createthrusters.text.ships", "refresh", "select_target", "manual_control",
                            "navigate", "follow", "climb", "hover", "initialize", "dock"),
                    tab("dashboard", "createthrusters.text.overview", "refresh", "select_target", "initialize", "hover", "stop"),
                    tab("landing", "createthrusters.text.landing_zones", "landing_zone", "select_landing_zone",
                            "remove_landing_zone"),
                    tab("logistics", "createthrusters.text.logistics", "connected_inventories", "request_items",
                            "schedule"),
                    tab("permissions", "createthrusters.text.permissions", "refresh", "permissions_set",
                            "permissions_access", "permissions_transfer"),
                    tab("manage", "createthrusters.text.manage", "rename_dock", "remove_target")));
    private static final TabletAppDefinition BLOCK360 = definition("block360", "createthrusters.text.block360",
            "createthrusters.text.private_circles_and_mutual_location_sharing", 0xFF7AC943,
            List.of(tab("circle", "createthrusters.text.circle", "refresh", "add_friend", "accept_friend", "remove_friend",
                            "navigate_friend"),
                    tab("places", "createthrusters.text.places")), Set.of("friend_location"));
    private static final TabletAppDefinition JOURNEY = definition("journey", "createthrusters.text.journey",
            "createthrusters.text.live_train_and_ship_journey_planning", 0xFF00A88F,
            List.of(tab("search", "createthrusters.text.search", "journey_search", "refresh"),
                    tab("live", "createthrusters.gyroscope_link.mode.live", "refresh"),
                    tab("saved", "createthrusters.text.saved", "journey_save", "journey_remove")));
    private static final TabletAppDefinition REDSTONE_LINK = definition("redstone_link", "createthrusters.analogue_controller.discovery.redstone_link",
            "createthrusters.text.smart_home_controls_for_wireless_redstone", 0xFF7C4DFF,
            List.of(tab("home", "createthrusters.text.home", "refresh", "channel_button", "channel_toggle", "channel_slider", "media_previous", "media_pause", "media_next"),
                    tab("devices", "createthrusters.text.devices", "card_reorder", "card_save", "channel_create", "channel_update", "bind_channel", "channel_mode",
                            "channel_strength", "channel_rename", "channel_remove", "channel_button", "channel_toggle", "channel_slider",
                            "media_previous", "media_pause", "media_next")));
    private static final TabletAppDefinition APP_STORE = definition("app_store", "createthrusters.text.app_store",
            "createthrusters.text.purchase_and_install_tablet_applications", 0xFF66BB6A,
            List.of(tab("store", "createthrusters.text.store", "refresh", "purchase_app")), Set.of(), true);
    private static final TabletAppDefinition SETTINGS = definition("settings", "createthrusters.text.settings",
            "createthrusters.text.tablet_identity_appearance_and_applications", 0xFF90A4AE,
            List.of(tab("tablet", "createthrusters.text.themes", "wallpaper_set"),
                    tab("apps", "createthrusters.text.apps", "app_install", "app_uninstall"),
                    tab("about", "createthrusters.text.about", "refresh", "tablet_rename")), Set.of(), true);
    private static final TabletAppDefinition GG_AUTO = definition("gg_auto", "createthrusters.text.g_g_auto",
            "createthrusters.text.on_ship_navigation_and_telemetry", 0xFF5AC8FA,
            List.of(tab("drive", "createthrusters.text.drive", "refresh", "navigate", "destination_save", "destination_remove", "destination_navigate",
                            "hover", "dock", "media_pause", "media_previous", "media_next"),
                    tab("stats", "createthrusters.text.stats", "refresh"), tab("manifest", "createthrusters.text.manifest", "refresh")));
    private static final TabletAppDefinition MEDIA = definition("media", "createthrusters.text.media",
            "createthrusters.text.browse_music_discs_and_create_looping_playlists", 0xFF49B9A2,
            List.of(tab("player", "createthrusters.text.player", "play", "pause", "next", "previous", "playlist_create",
                    "playlist_select", "playlist_delete", "playlist_rename", "playlist_toggle")));

    private static final TabletAppDefinition NFC = definition("nfc", "createthrusters.text.nfc",
            "createthrusters.text.inspect_and_directly_control_nearby_blocks", 0xFF90A4AE,
            List.of(tab("inspect", "createthrusters.text.inspect", "refresh", "nfc_scan", "nfc_signal")));
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
        TabletAppRegistry.registerIfAbsent(APP_STORE, DiagnosticTabletApps::execute);
        TabletAppRegistry.registerIfAbsent(SETTINGS, DiagnosticTabletApps::execute);
        TabletAppRegistry.registerIfAbsent(GG_AUTO, DiagnosticTabletApps::execute);
        TabletAppRegistry.registerIfAbsent(NFC, DiagnosticTabletApps::execute);
        TabletAppRegistry.registerIfAbsent(MEDIA, DiagnosticTabletMediaApp::execute);
        TabletAppDescriptions.register(MEDIA.id(), Component.translatable("createthrusters.text.play_every_registered_music_disc_search_tracks_and_save_looping_playlists_placed_tablets_play_i"));
        com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps.register();
        registerDescriptions();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Supply detailed store listings for the bundled applications
    private static void registerDescriptions(){
        describe(RDP, "createthrusters.text.connect_securely_to_linked_advanced_controllers_operate_their_controls_and_open_their_graph_or");
        describe(SCM, "createthrusters.text.inspect_ships_and_docks_manage_logistics_and_control_navigation_and_landing_zones_from_one_plac");
        describe(BLOCK360, "createthrusters.text.stay_connected_with_other_players_create_private_circles_and_share_your_location");
        describe(JOURNEY, "createthrusters.text.search_stations_and_docks_plan_journeys_and_keep_saved_routes_together_with_live_departure_info");
        describe(REDSTONE_LINK, "createthrusters.text.create_buttons_toggles_and_sliders_for_wireless_redstone_controls_then_choose_the_channels_and");
        describe(GG_AUTO, "createthrusters.text.view_onboard_telemetry_and_guide_a_ship_through_navigation_hovering_and_docking_controls");
        describe(NFC, "createthrusters.text.use_reader_mode_to_inspect_a_nearby_block_and_operate_its_exposed_controls_directly_from_the_ta");
        var paid = com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps.DIGISABLE;
        describe(paid, "createthrusters.text.browse_sublevels_and_stored_assemblies_with_a_3d_preview_store_or_extract_assemblies_inspect_sc");
        describe(com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps.MANIFEST,
                "createthrusters.text.inspect_detailed_container_cargo_and_browse_connected_items_fluids_and_energy_through_the_table");
        describe(com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps.BLOCKMATES,
                "createthrusters.text.manage_controller_workers_and_request_item_or_fluid_deliveries_from_connected_storage_follow_re");
        describe(com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps.CCTV,
                "createthrusters.text.browse_bound_cameras_watch_their_live_feeds_and_operate_supported_camera_controls");
    }

    private static void describe(TabletAppDefinition app, String details){
        TabletAppDescriptions.register(app.id(), Component.translatable(details));
    }

    // Check if this is canonical definition
    public static boolean isCanonicalDefinition(TabletAppDefinition definition, String id) {
        if (definition == null || id == null) return false;
        if(com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps.canonical(definition)){
            return definition.id().getPath().equals(id);
        }
        return switch (id) {
            case "rdp" -> definition == RDP;
            case "scm" -> definition == SCM;
            case "block360" -> definition == BLOCK360;
            case "journey" -> definition == JOURNEY;
            case "redstone_link" -> definition == REDSTONE_LINK;
            case "app_store" -> definition == APP_STORE;
            case "settings" -> definition == SETTINGS;
            case "gg_auto" -> definition == GG_AUTO;
            case "nfc" -> definition == NFC;
            case "media" -> definition == MEDIA;
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
        return definition(id, title, description, accent, tabs, sharedKeys, false);
    }

    // Get the definition
    private static TabletAppDefinition definition(String id, String title, String description,
                                                   int accent, List<TabletTabDefinition> tabs,
                                                   Set<String> sharedKeys, boolean builtIn) {
        ResourceLocation appId = DiagnosticTabletData.appId(id);
        return new TabletAppDefinition(appId, Component.translatable(title),
                Component.translatable(description), accent, tabs, null, sharedKeys, builtIn);
    }

    // Get the tab
    private static TabletTabDefinition tab(String id, String title, String... actions) {
        Set<String> keyboardActions = Arrays.stream(actions)
                .filter(KEYBOARD_ACTIONS::contains).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new TabletTabDefinition(id, Component.translatable(title), List.of(actions), keyboardActions);
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
            if (setting.length != 2 || setting[0].isBlank()) return failure(Component.translatable("createthrusters.text.invalid_app_setting"));
            TabletStorageApi.storage().updateApp(
                    ctx.sourceTabletId(), action.appId(), data -> {
                        CompoundTag settings = data.getCompound("Settings").copy();
                        settings.putString(setting[0].strip(), setting[1].strip());
                        data.put("Settings", settings);
                        return data;
                    });
            return success(Component.translatable("createthrusters.text.app_setting_updated"));
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
        if (APP_STORE.id().equals(app)) {
            return DiagnosticTabletAppStore.execute(ctx, action);
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
        return failure(Component.translatable("createthrusters.text.unknown_tablet_app"));
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
            return failure(Component.translatable("createthrusters.text.unknown_tablet_app"));
        }
        UUID tabletId = state.tabletId() == null ? sourceTabletId : state.tabletId();
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
    private static TabletActionHandler.Result success(Component msg) {
        return TabletActionHandler.Result.success(msg);
    }

    // Create a failed diagnostic tablet apps
    private static TabletActionHandler.Result failure(Component msg) {
        return TabletActionHandler.Result.failure(msg);
    }
}
