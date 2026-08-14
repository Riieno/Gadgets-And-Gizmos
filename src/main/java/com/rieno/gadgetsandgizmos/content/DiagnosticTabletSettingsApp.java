package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletNotifications;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorage;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Set;
import java.util.UUID;
import net.neoforged.fml.ModList;

// Implement the tablet settings page and its stored preferences
public final class DiagnosticTabletSettingsApp {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static final ResourceLocation APP_ID = DiagnosticTabletData.appId("settings");
    private static final Set<String> WALLPAPERS = Set.of(
            "aurora", "midnight", "sunset", "meadow", "graphite");
    private static final Set<ResourceLocation> REQUIRED_APPS = Set.of(APP_ID);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet settings app
    private DiagnosticTabletSettingsApp() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the diagnostic tablet settings app
    static TabletActionHandler.Result execute(TabletActionContext ctx, TabletAction action) {
        UUID tabletId = ctx.sourceTabletId();
        if (tabletId == null) return failure("The tablet identity is not available");
        TabletStorage database = TabletStorageApi.storage();
        String val = action.arguments().getOrDefault("value", "").strip();
        TabletActionHandler.Result res = switch (action.actionId()) {
            case "refresh", "select" -> quietSuccess();
            case "tablet_rename" -> {
                if (val.isBlank()) yield failure("Enter a tablet name");
                String name = val.substring(0, Math.min(48, val.length()));
                database.updateTablet(tabletId, settings -> {
                    settings.putString("Name", name);
                    return settings;
                });
                yield success("Tablet renamed");
            }
            case "wallpaper_set" -> {
                if (!WALLPAPERS.contains(val)) yield failure("Unknown wallpaper");
                database.updateTablet(tabletId, settings -> {
                    settings.putString("Wallpaper", val);
                    return settings;
                });
                yield quietSuccess();
            }
            case "app_install", "app_uninstall" -> {
                ResourceLocation appId = ResourceLocation.tryParse(val);
                if (appId == null || TabletAppRegistry.definition(appId) == null) {
                    yield failure("That application is not available");
                }
                if (REQUIRED_APPS.contains(appId) && "app_uninstall".equals(action.actionId())) {
                    yield failure("Built-in applications cannot be removed");
                }
                boolean installed = "app_install".equals(action.actionId());
                yield database.setInstalled(tabletId, appId, installed)
                        ? success(installed ? "Application installed" : "Application removed")
                        : failure("Could not update the application");
            }
            default -> failure("Unknown Settings action");
        };
        sendSnapshot(ctx);
        return res;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Send the snapshot
    static void sendSnapshot(TabletActionContext ctx) {
        UUID tabletId = ctx.sourceTabletId();
        if (tabletId == null) return;
        CompoundTag snapshot = snapshot(ctx.player().server, tabletId);
        snapshot.putBoolean("GgAutoAvailable", DiagnosticTabletAutoApp.available(ctx));
        PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletAppSnapshotPayload(
                APP_ID, ctx, snapshot));
    }

    // Get the snapshot
    public static CompoundTag snapshot(net.minecraft.server.MinecraftServer server, UUID tabletId) {
        TabletStorage database = TabletStorageApi.storage();
        CompoundTag root = database.tablet(tabletId);
        Set<ResourceLocation> installed = database.installedApps(tabletId);
        ListTag apps = new ListTag();
        for (TabletAppDefinition definition : TabletAppRegistry.apps()) {
            CompoundTag app = new CompoundTag();
            app.putString("Id", definition.id().toString());
            app.putString("Name", definition.title().getString());
            app.putString("Description", definition.description().getString());
            app.putBoolean("Installed", installed.contains(definition.id()));
            app.putBoolean("Required", REQUIRED_APPS.contains(definition.id()));
            apps.add(app);
        }
        root.put("Apps", apps);
        ListTag notificationCounts = new ListTag();
        for (ResourceLocation appId : installed) {
            int count = TabletNotifications.count(tabletId, appId);
            if (count <= 0) continue;
            CompoundTag notification = new CompoundTag();
            notification.putString("Id", appId.toString());
            notification.putInt("Count", count);
            notificationCounts.add(notification);
        }
        root.put("NotificationCounts", notificationCounts);
        ListTag wallpapers = new ListTag();
        WALLPAPERS.stream().sorted().forEach(id -> {
            CompoundTag wallpaper = new CompoundTag();
            wallpaper.putString("Id", id);
            wallpapers.add(wallpaper);
        });
        root.put("Wallpapers", wallpapers);
        root.putString("ModVersion", version("createthrusters"));
        root.putString("LibraryVersion", version(GadgetsNGizmosLibrary.MOD_ID));
        root.putString("Wiki", "https://gadgetsngizmos-hub.com/wiki");
        root.putString("Discord", "https://discord.gg/zhvuEMEpZR");
        return root;
    }

    // Get the version
    private static String version(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("Unavailable");
    }

    // Get the quiet success
    private static TabletActionHandler.Result quietSuccess() {
        return TabletActionHandler.Result.success(Component.empty());
    }

    // Create a successful diagnostic tablet settings app
    private static TabletActionHandler.Result success(String msg) {
        return TabletActionHandler.Result.success(Component.literal(msg));
    }

    // Create a failed diagnostic tablet settings app
    private static TabletActionHandler.Result failure(String msg) {
        return TabletActionHandler.Result.failure(Component.literal(msg));
    }
}
