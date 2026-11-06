package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.text.LocalizedText;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppAccess;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDescriptions;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppEntitlementApi;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppPrice;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppPurchaseScope;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

// Sell non-built-in tablet applications through the library entitlement API
public final class DiagnosticTabletAppStore {
    static final ResourceLocation APP_ID = DiagnosticTabletData.appId("app_store");

    private DiagnosticTabletAppStore() {
    }

    static TabletActionHandler.Result execute(TabletActionContext ctx, TabletAction action) {
        UUID tabletId = ctx.sourceTabletId();
        if (tabletId == null) return failure(Component.translatable("createthrusters.text.the_tablet_identity_is_not_available"));

        TabletActionHandler.Result result = switch (action.actionId()) {
            case "refresh" -> quietSuccess();
            case "purchase_app" -> purchase(ctx.player(), tabletId,
                    action.arguments().getOrDefault("value", ""));
            default -> failure(Component.translatable("createthrusters.text.unknown_app_store_action"));
        };
        sendSnapshot(ctx);
        DiagnosticTabletSettingsApp.sendSnapshot(ctx);
        return result;
    }

    static void sendSnapshot(TabletActionContext ctx) {
        UUID tabletId = ctx.sourceTabletId();
        if (tabletId == null) return;
        PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletAppSnapshotPayload(
                APP_ID, ctx, snapshot(ctx.player(), tabletId)));
    }

    static CompoundTag snapshot(ServerPlayer player, UUID tabletId) {
        CompoundTag root = new CompoundTag();
        ListTag apps = new ListTag();
        var storage = TabletStorageApi.storage();
        for (TabletAppDefinition app : TabletAppRegistry.apps()) {
            if (app.builtIn()) continue;
            TabletAppPrice price = DiagnosticTabletAppStoreConfig.price(app);
            CompoundTag row = new CompoundTag();
            row.putString("Id", app.id().toString());
            LocalizedText.put(row, "Name", app.title());
            LocalizedText.put(row, "Description", app.description());
            row.putString("Details", TabletAppDescriptions.details(app).getString());
            row.putBoolean("Owned", TabletAppAccess.canUse(app, TabletAppPurchaseScope.PLAYER,
                    player.getUUID(), tabletId));
            row.putBoolean("Installed", storage.installedApps(tabletId).contains(app.id()));
            row.putString("PriceItem", price.itemId().toString());
            row.putInt("PriceCount", price.count());
            apps.add(row);
        }
        root.put("Apps", apps);
        return root;
    }

    private static TabletActionHandler.Result purchase(ServerPlayer player, UUID tabletId,
                                                       String requestedId) {
        ResourceLocation appId = ResourceLocation.tryParse(requestedId.strip());
        TabletAppDefinition app = appId == null ? null : TabletAppRegistry.definition(appId);
        if (app == null || app.builtIn()) return failure(Component.translatable("createthrusters.text.that_application_is_not_for_sale"));

        var storage = TabletStorageApi.storage();
        if (TabletAppAccess.canUse(app, TabletAppPurchaseScope.PLAYER, player.getUUID(), tabletId)) {
            storage.setInstalled(tabletId, app.id(), true);
            return success(Component.translatable("createthrusters.text.application_installed"));
        }
        if (!TabletAppEntitlementApi.available()) return failure(Component.translatable("createthrusters.text.the_app_store_is_unavailable"));

        TabletAppPrice price = DiagnosticTabletAppStoreConfig.price(app);
        Item currency = BuiltInRegistries.ITEM.getOptional(price.itemId()).orElse(null);
        if (currency == null) return failure(Component.translatable("createthrusters.text.this_application_has_an_invalid_price"));
        if (!hasItems(player, currency, price.count())) return failure(Component.translatable("createthrusters.text.you_cannot_afford_this_application"));
        if (!consumeItems(player, currency, price.count())) return failure(Component.translatable("createthrusters.text.could_not_take_payment"));
        if (!TabletAppEntitlementApi.store().grant(TabletAppPurchaseScope.PLAYER,
                player.getUUID(), tabletId, app.id())) {
            return failure(Component.translatable("createthrusters.text.could_not_grant_the_application"));
        }
        if (!storage.setInstalled(tabletId, app.id(), true)) {
            return failure(Component.translatable("createthrusters.text.application_purchased_but_could_not_be_installed"));
        }
        return success(Component.translatable(price.free() ? "createthrusters.text.application_installed" : "createthrusters.text.application_purchased_and_installed"));
    }

    private static boolean hasItems(ServerPlayer player, Item item, int count) {
        if (count == 0 || player.isCreative()) return true;
        int remaining = count;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() != item) continue;
            remaining -= stack.getCount();
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static boolean consumeItems(ServerPlayer player, Item item, int count) {
        if (!hasItems(player, item, count)) return false;
        if (count == 0 || player.isCreative()) return true;
        int remaining = count;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() != item) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        inventory.setChanged();
        return remaining == 0;
    }

    private static TabletActionHandler.Result quietSuccess() {
        return TabletActionHandler.Result.success(Component.empty());
    }

    private static TabletActionHandler.Result success(Component message) {
        return TabletActionHandler.Result.success(message);
    }

    private static TabletActionHandler.Result failure(Component message) {
        return TabletActionHandler.Result.failure(message);
    }
}
