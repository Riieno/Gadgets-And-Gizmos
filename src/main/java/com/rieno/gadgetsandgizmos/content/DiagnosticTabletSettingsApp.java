package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.text.LocalizedText;
import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppAccess;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppPurchaseScope;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletLauncherThemes;
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
    public static final ResourceLocation DEFAULT_LAUNCHER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "textures/gui/apps/screens/launcher_streaks.png");

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
        if (tabletId == null) return failure(Component.translatable("createthrusters.text.the_tablet_identity_is_not_available"));
        TabletStorage database = TabletStorageApi.storage();
        String val = action.arguments().getOrDefault("value", "").strip();
        TabletActionHandler.Result res = switch (action.actionId()) {
            case "refresh", "select" -> quietSuccess();
            case "tablet_rename" -> {
                if (val.isBlank()) yield failure(Component.translatable("createthrusters.text.enter_a_tablet_name"));
                String name = val.substring(0, Math.min(48, val.length()));
                database.updateTablet(tabletId, settings -> {
                    settings.putString("Name", name);
                    return settings;
                });
                yield success(Component.translatable("createthrusters.text.tablet_renamed"));
            }
            case "wallpaper_set" -> {
                ResourceLocation texture = ResourceLocation.tryParse(val);
                if(!TabletLauncherThemes.isLauncherTexture(texture)) yield failure(Component.translatable("createthrusters.text.unknown_launcher_theme"));
                database.updateTablet(tabletId, settings -> {
                    settings.putString("Wallpaper", texture.toString());
                    return settings;
                });
                yield quietSuccess();
            }
            case "app_install", "app_uninstall" -> {
                ResourceLocation appId = ResourceLocation.tryParse(val);
                TabletAppDefinition app = appId == null ? null : TabletAppRegistry.definition(appId);
                if (app == null) {
                    yield failure(Component.translatable("createthrusters.text.that_application_is_not_available"));
                }
                if (app.builtIn() && "app_uninstall".equals(action.actionId())) {
                    yield failure(Component.translatable("createthrusters.text.built_in_applications_cannot_be_removed"));
                }
                boolean installed = "app_install".equals(action.actionId());
                if (installed && !app.builtIn() && !TabletAppAccess.canUse(app,
                        TabletAppPurchaseScope.PLAYER, ctx.player().getUUID(), tabletId)) {
                    yield failure(Component.translatable("createthrusters.text.purchase_this_application_from_the_app_store_first"));
                }
                yield database.setInstalled(tabletId, appId, installed)
                        ? success(Component.translatable(installed ? "createthrusters.text.application_installed" : "createthrusters.text.application_removed"))
                        : failure(Component.translatable("createthrusters.text.could_not_update_the_application"));
            }
            default -> failure(Component.translatable("createthrusters.text.unknown_settings_action"));
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
        root.putUUID("TabletId", tabletId);
        Set<ResourceLocation> installed = database.installedApps(tabletId);
        ListTag apps = new ListTag();
        for (TabletAppDefinition definition : TabletAppRegistry.apps()) {
            CompoundTag app = new CompoundTag();
            app.putString("Id", definition.id().toString());
            LocalizedText.put(app, "Name", definition.title());
            LocalizedText.put(app, "Description", definition.description());
            app.putBoolean("Installed", installed.contains(definition.id()));
            app.putBoolean("Required", definition.builtIn());
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
        TabletLauncherThemes.dataPackThemes().forEach(theme -> wallpapers.add(theme.toTag()));
        root.put("Wallpapers", wallpapers);
        root.putString("ModVersion", version("createthrusters"));
        root.putString("LibraryVersion", version(GadgetsNGizmosLibrary.MOD_ID));
        root.putString("Wiki", "https://github.com/Riieno/Gadgets-And-Gizmos/wiki");
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
    private static TabletActionHandler.Result success(Component msg) {
        return TabletActionHandler.Result.success(msg);
    }

    // Create a failed diagnostic tablet settings app
    private static TabletActionHandler.Result failure(Component msg) {
        return TabletActionHandler.Result.failure(msg);
    }
}
