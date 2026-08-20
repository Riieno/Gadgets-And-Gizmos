package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           IMPORTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletLayout;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                             MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

public final class AppStore implements TabletAppClientRenderer{

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int PAGE_SIZE = 5;
    private static final TabletLayout CANVAS = new TabletLayout(128, 75);
    private static final ResourceLocation STYLE = ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "textures/gui/apps/styles/app_store/app_store.png");
    private static final TabletLayout.Rect PARENT_INFO = new TabletLayout.Rect(1, 1, 42, 9);
    private static final TabletLayout.Rect APP_LIST = new TabletLayout.Rect(1, 11, 42, 54);
    private static final TabletLayout.Rect PREV = new TabletLayout.Rect(2, 65, 8, 8);
    private static final TabletLayout.Rect PAGE_LABEL = new TabletLayout.Rect(10, 65, 24, 8);
    private static final TabletLayout.Rect NEXT = new TabletLayout.Rect(34, 65, 8, 8);
    private static final TabletLayout.Rect SELECTED_APP_ICON = new TabletLayout.Rect(47, 2, 26, 25);
    private static final TabletLayout.Rect TITLE = new TabletLayout.Rect(73, 2, 52, 5);
    private static final TabletLayout.Rect PRICE = new TabletLayout.Rect(73, 7, 25, 8);
    private static final TabletLayout.Rect ACTION = new TabletLayout.Rect(98, 7, 27, 8);
    private static final TabletLayout.Rect DESCRIPTION = new TabletLayout.Rect(47, 32, 78, 42);

    private static final class State implements TabletAppClientState{
        private int page;
        private String selectedId = "";
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           OVERRIDES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override
    public TabletAppClientState createScreenState(){
        return new State();
    }

    @Override
    public boolean ownsAppSurface(){
        return true;
    }

    @Override
    public void render(TabletAppClientContext ctx, TabletAppClientState rawState){
        State state = state(rawState);
        ListTag apps = ctx.data().getList("Apps", Tag.TAG_COMPOUND);
        int pageCount = Math.max(1, (apps.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        state.page = Mth.clamp(state.page, 0, pageCount - 1);
        CompoundTag selected = selected(apps, state);
        TabletLayout.Bounds canvas = CANVAS.stretch(ctx.surface().left(), ctx.surface().top(), ctx.surface().width(), ctx.surface().height());

        ctx.graphics().blit(STYLE, canvas.left(), canvas.top(), canvas.width(), canvas.height(), 0.0f, 0.0f, 128, 75, 128, 75);
        drawParentInfo(ctx, CANVAS.project(canvas, PARENT_INFO));
        drawList(ctx, state, apps, canvas, pageCount);
        if(selected.isEmpty()){
            drawDescription(ctx, Component.literal("No Applications are currently available."), CANVAS.project(canvas, DESCRIPTION));
            return;
        }
        drawSelection(ctx, selected, canvas);
    }

    @Override
    public boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState rawState, double mouseX, double mouseY, int button){
        if(button != 0) return false;

        State state = state(rawState);
        ListTag apps = ctx.data().getList("Apps", Tag.TAG_COMPOUND);
        int pageCount = Math.max(1, (apps.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        TabletLayout.Bounds canvas = CANVAS.stretch(ctx.surface().left(), ctx.surface().top(), ctx.surface().width(), ctx.surface().height());
        if(CANVAS.project(canvas, PREV).contains(mouseX, mouseY) && state.page > 0){
            state.page--;
            selectPage(apps, state);
            return true;
        }
        if(CANVAS.project(canvas, NEXT).contains(mouseX, mouseY) && state.page < pageCount - 1){
            state.page++;
            selectPage(apps, state);
            return true;
        }
        TabletLayout.Rect list = CANVAS.project(canvas, APP_LIST);
        if(list.contains(mouseX, mouseY)){
            int row = (int) ((mouseY - list.top()) * PAGE_SIZE / list.height());
            int idx = state.page * PAGE_SIZE + row;
            if(idx < apps.size()) state.selectedId = apps.getCompound(idx).getString("Id");
            return true;
        }
        CompoundTag selected = selected(apps, state);
        if(!selected.isEmpty() && !selected.getBoolean("Installed") && CANVAS.project(canvas, ACTION).contains(mouseX, mouseY)){
            ctx.actions().send("purchase_app", selected.getString("Id"));
            return true;
        }
        return false;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           METHODS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static State state(TabletAppClientState rawState){
        if(rawState instanceof State state) return state;
        throw new IllegalArgumentException("[G&G][Tablet] - App Store recieved another apps state.");
    }

    private static CompoundTag selected(ListTag apps, State state){
        for(int index = 0; index < apps.size(); index++){
            CompoundTag row = apps.getCompound(index);
            if(row.getString("Id").equals(state.selectedId)) return row;
        }
        if(apps.isEmpty()) return new CompoundTag();
        CompoundTag first = apps.getCompound(0);
        state.selectedId = first.getString("Id");
        return first;
    }

    private static void selectPage(ListTag apps, State state){
        int idx = state.page * PAGE_SIZE;
        if(idx < apps.size()) state.selectedId = apps.getCompound(idx).getString("Id");
    }

    // Draw Parent Info
    private static void drawParentInfo(TabletAppClientContext ctx, TabletLayout.Rect area){
        int inset = Math.max(2, area.height() / 8);
        int iconSize = Math.max(1, area.height() - inset * 2);
        ctx.graphics().blit(ctx.app().icon(), area.left() + inset, area.top() + inset, iconSize, iconSize, 0.0f, 0.0f, 64, 64, 64, 64);
        String title = trim(ctx, ctx.app().title().getString().toUpperCase(Locale.ROOT), area.width() - iconSize - inset * 3);
        ctx.graphics().drawCenteredString(ctx.font(), title, area.left() + iconSize + inset * 2 + (area.width() - iconSize - inset * 3) / 2, area.top() + (area.height() - ctx.font().lineHeight) / 2, 0xFF101114);
    }

    // Draw App List
    private static void drawList(TabletAppClientContext ctx, State state, ListTag apps, TabletLayout.Bounds bounds, int pageCount){
        TabletLayout.Rect area = CANVAS.project(bounds, APP_LIST);
        int first = state.page * PAGE_SIZE;
        int rowHeight = Math.max(1, area.height() / PAGE_SIZE);
        for(int row = 0; row < PAGE_SIZE && first + row < apps.size(); row++){
            CompoundTag app = apps.getCompound(first + row);
            int y = area.top() + row * rowHeight;
            boolean selected = app.getString("Id").equals(state.selectedId);
            int inset = Math.max(2, rowHeight / 9);
            ctx.graphics().fill(area.left() + inset, y + inset, area.left() + area.width() - inset, y + rowHeight - inset, selected ? 0xFF303033 : 0xFFE0E0E0);
            int iconSize = Math.max(1, rowHeight - inset * 4);
            ResourceLocation iconLoc = icon(ctx, app);
            if(iconLoc != null) ctx.graphics().blit(iconLoc, area.left() + inset * 3, y + inset * 2, iconSize, iconSize, 0.0f, 0.0f, 64, 64, 64, 64);
            int textLeft = area.left() + iconSize + inset * 5;
            String name = trim(ctx, app.getString("Name").toUpperCase(Locale.ROOT), area.left() + area.width() - inset * 2 - textLeft);
            ctx.graphics().drawCenteredString(ctx.font(), name, textLeft + (area.left() + area.width() - inset * 2 - textLeft) / 2, y + (rowHeight - ctx.font().lineHeight) / 2, selected ? 0xFFF5F5F5 : 0xFF151518);
        }
        TabletLayout.Rect prev = CANVAS.project(bounds, PREV);
        TabletLayout.Rect label = CANVAS.project(bounds, PAGE_LABEL);
        TabletLayout.Rect next = CANVAS.project(bounds, NEXT);
        ctx.graphics().drawCenteredString(ctx.font(), state.page == 0 ? "" : "<", prev.left() + prev.width() / 2, prev.top() + 1, 0xFF363639);
        ctx.graphics().drawCenteredString(ctx.font(), "PAGE " + (state.page + 1) + " OF " + pageCount, label.left() + label.width() / 2, label.top() + (label.height() - ctx.font().lineHeight) / 2, 0xFF151518);
        ctx.graphics().drawCenteredString(ctx.font(), state.page == pageCount - 1 ? "" : ">", next.left() + next.width() / 2, next.top() + 1, 0xFF363639);
    }

    // Draw Selected App Info
    private static void drawSelection(TabletAppClientContext ctx, CompoundTag app, TabletLayout.Bounds bounds){
        TabletLayout.Rect icon = CANVAS.project(bounds, SELECTED_APP_ICON);
        TabletLayout.Rect title = CANVAS.project(bounds, TITLE);
        TabletLayout.Rect price = CANVAS.project(bounds, PRICE);
        TabletLayout.Rect action = CANVAS.project(bounds, ACTION);
        ResourceLocation iconLoc = icon(ctx, app);

        if(iconLoc != null) ctx.graphics().blit(iconLoc, icon.left(), icon.top(), icon.width(), icon.height(), 0.0f, 0.0f, 64, 64, 64, 64);
        else ctx.graphics().drawCenteredString(ctx.font(), "APP", icon.left() + icon.width() / 2, icon.top() + icon.height() / 2 - 4, 0xDD363639);
        ctx.graphics().drawCenteredString(ctx.font(), trim(ctx, app.getString("Name").toUpperCase(Locale.ROOT), title.width() - 2), title.left() + title.width() / 2, title.top() + (title.height() - ctx.font().lineHeight) / 2, 0xFFF8F8F8);
        ctx.graphics().drawCenteredString(ctx.font(), trim(ctx, priceLabel(app), price.width() - 2), price.left() + price.width() / 2, price.top() + (price.height() - ctx.font().lineHeight) / 2, 0xFFF8F8F8);
        drawActionBackground(ctx, action, app.getBoolean("Installed") ? 0xFF8A8A8A : 0xFF72C56C);
        ctx.graphics().drawCenteredString(ctx.font(), actionLabel(app).toUpperCase(Locale.ROOT), action.left() + action.width() / 2, action.top() + (action.height() - ctx.font().lineHeight) / 2, 0xFFFFFFFF);
        drawDescription(ctx, Component.literal(app.getString("Description").toUpperCase(Locale.ROOT)), CANVAS.project(bounds, DESCRIPTION));
    }

    private static ResourceLocation icon(TabletAppClientContext ctx, CompoundTag app){
        ResourceLocation id = ResourceLocation.tryParse(app.getString("Id"));
        TabletAppDefinition definition = id == null ? null : TabletAppRegistry.definition(id);
        return definition == null ? ctx.app().icon() : definition.icon();
    }

    private static String actionLabel(CompoundTag app){
        if(app.getBoolean("Installed")) return "Installed";
        if(app.getBoolean("Owned")) return "Install";
        return app.getInt("PriceCount") <= 0 ? "Get" : "Purchase";
    }

    private static String priceLabel(CompoundTag app){
        int count = app.getInt("PriceCount");
        if(count <= 0) return "FREE";
        ResourceLocation item = ResourceLocation.tryParse(app.getString("PriceItem"));
        String name = item == null ? "ITEM" : item.getPath().replace('_', ' ').toUpperCase(Locale.ROOT);
        if(count != 1 && !name.endsWith("S")) name += "S";
        return count + " " + name;
    }

    private static void drawActionBackground(TabletAppClientContext ctx, TabletLayout.Rect area, int color){
        int radius = Math.max(2, Math.min(area.width(), area.height()) / 4);
        ctx.graphics().fill(area.left() + radius, area.top(), area.left() + area.width() - radius, area.top() + area.height(), color);
        ctx.graphics().fill(area.left(), area.top() + radius, area.left() + area.width(), area.top() + area.height() - radius, color);
        ctx.graphics().fill(area.left() + 1, area.top() + radius / 2, area.left() + area.width() - 1, area.top() + area.height() - radius / 2, color);
    }

    private static String trim(TabletAppClientContext ctx, String text, int width){
        return ctx.font().plainSubstrByWidth(text, Math.max(1, width));
    }

    // Draw Description
    private static void drawDescription(TabletAppClientContext ctx, Component text, TabletLayout.Rect area){
        List<FormattedCharSequence> lines = ctx.font().split(text, Math.max(1, area.width() - 2));
        int y = area.top() + 1;
        for(FormattedCharSequence line : lines){
            if(y + ctx.font().lineHeight > area.top() + area.height()) break;
            ctx.graphics().drawString(ctx.font(), line, area.left() + 1, y, 0xFFFFFFFF, false);
            y += ctx.font().lineHeight;
        }
    }
}
