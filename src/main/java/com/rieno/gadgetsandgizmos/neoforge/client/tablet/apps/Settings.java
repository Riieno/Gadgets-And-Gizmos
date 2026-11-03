package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletSettingsApp;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppIcons;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletLauncherResources;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletLayout;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiScrollbar;
import com.rieno.gadgetsandgizmos.lib.client.ui.ListScrollState;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedGuiStyle;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletLauncherThemes.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;

// Paint the settings sidebar and its themes, application list and tablet identity
public final class Settings implements TabletAppClientRenderer{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "textures/gui/apps/screens/settings.png");
    private static final TabletLayout CANVAS = new TabletLayout(320, 180);
    private static final TabletLayout.Rect CONTENT = new TabletLayout.Rect(97, 37, 216, 129);
    private static final TabletLayout.Rect EDIT_NAME = new TabletLayout.Rect(282, 132, 9, 9);
    private static final TabletLayout.Rect WIKI = new TabletLayout.Rect(108, 156, 90, 10);
    private static final TabletLayout.Rect DISCORD = new TabletLayout.Rect(204, 156, 94, 10);
    private static final GuiTextureRegion BACKGROUND = region(0, 0, 320, 180);
    private static final GuiTextureRegion SELECTED_TAB = region(49, 185, 2, 8);
    private static final GuiTextureRegion SCROLL_TRACK = region(79, 42, 2, 8);
    private static final GuiTextureRegion THEME_CARD = region(0, 240, 75, 60);
    private static final GuiTextureRegion SELECTED_BUTTON = region(0, 225, 77, 11);
    private static final GuiTextureRegion BUTTON = region(0, 195, 43, 11);
    private static final GuiTextureRegion RUN_BUTTON = region(94, 240, 19, 11);
    private static final GuiTextureRegion REMOVE_BUTTON = region(118, 240, 19, 11);
    private static final GuiTextureRegion APP_ROW = region(94, 255, 200, 45);
    private static final GuiTextureRegion EDIT = region(159, 241, 9, 9);
    private static final GuiTextureRegion EDIT_HOVER = region(143, 241, 9, 9);
    private static final GuiNineSlice IDENTITY = PaintedGuiStyle.slice(region(94, 190, 190, 46), 5, 5, 5, 5);
    private static final int THEME_ROW_HEIGHT = 63;
    private static final int APP_ROW_HEIGHT = 49;
    private static final int TEXT = 0xFFE3E3E3;
    private static final int MUTED = 0xFF9A9A9A;

    private static final class State implements TabletAppClientState{
        private final ListScrollState themes = new ListScrollState();
        private final ListScrollState apps = new ListScrollState();
        private final GuiScrollbar scrollbar = new GuiScrollbar(SCROLL_TRACK, SELECTED_TAB);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override
    public TabletAppClientState createScreenState(){
        return new State();
    }

    @Override
    public boolean ownsTabletSurface(){
        return true;
    }

    @Override
    public void render(TabletAppClientContext ctx, TabletAppClientState rawState){
        State state = state(rawState);
        TabletLayout.Bounds bounds = bounds(ctx);
        GuiGraphics graphics = ctx.graphics();
        double mouseX = (ctx.mouseX() - bounds.left()) / bounds.scaleX();
        double mouseY = (ctx.mouseY() - bounds.top()) / bounds.scaleY();
        ListScrollState scroll = configure(ctx, state);
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(bounds.left(), bounds.top(), 0);
            graphics.pose().scale((float)bounds.scaleX(), (float)bounds.scaleY(), 1);
            BACKGROUND.draw(graphics, 0, 0);
            TabletAppIcons.render(ctx.app(), graphics, 13, 18, 12);
            text(ctx, "Settings", new TabletLayout.Rect(27, 18, 60, 12), .9F, false, TEXT);
            renderSidebar(ctx);
            try(var clip = GuiPoseScissor.push(graphics, CONTENT.left(), CONTENT.top(),
                    CONTENT.left() + CONTENT.width(), CONTENT.top() + CONTENT.height())){
                switch(ctx.tab().id()){
                    case "apps" -> renderApps(ctx, state, mouseX, mouseY);
                    case "about" -> renderAbout(ctx, mouseX, mouseY);
                    default -> renderThemes(ctx, state, mouseX, mouseY);
                }
            }
            if(scroll != null && scroll.maximumOffset() > 0) state.scrollbar.render(graphics);
        }finally{ graphics.pose().popPose(); }
    }

    @Override
    public boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState rawState,
                                double mouseX, double mouseY, int btn){
        if(btn != 0 && !(btn == 1 && ctx.ui().projected())) return false;
        State state = state(rawState);
        TabletLayout.Bounds bounds = bounds(ctx);
        double x = (mouseX - bounds.left()) / bounds.scaleX();
        double y = (mouseY - bounds.top()) / bounds.scaleY();
        var tabs = ctx.app().tabs();
        for(int idx = 0; idx < Math.min(3, tabs.size()); idx++){
            if(sidebarTab(idx).contains(x, y)){
                state.scrollbar.reset();
                ctx.ui().selectTab(tabs.get(idx).id());
                return true;
            }
        }
        if(!CONTENT.contains(x, y)) return false;
        ListScrollState scroll = configure(ctx, state);
        if(scroll != null && state.scrollbar.mouseClicked(x, y, 0)){
            scroll.scrollToFraction(state.scrollbar.fraction());
            return true;
        }
        switch(ctx.tab().id()){
            case "apps" -> {
                ListTag apps = ctx.data().getList("Apps", Tag.TAG_COMPOUND);
                for(int idx = state.apps.firstVisibleIndex(); idx < apps.size(); idx++){
                    int rowY = 40 + state.apps.rowOffset(idx);
                    if(rowY >= CONTENT.top() + CONTENT.height()) break;
                    CompoundTag row = apps.getCompound(idx);
                    if(row.getBoolean("Required")) continue;
                    if(row.getBoolean("Installed") && new TabletLayout.Rect(257, rowY + 26, 19, 11).contains(x, y)){
                        ResourceLocation appId = ResourceLocation.tryParse(row.getString("Id"));
                        TabletAppDefinition app = appId == null ? null : TabletAppRegistry.definition(appId);
                        if(app != null) ctx.ui().openApp(app.id(), app.tabs().isEmpty() ? "" : app.tabs().getFirst().id());
                        return true;
                    }
                    if(new TabletLayout.Rect(280, rowY + 26, 19, 11).contains(x, y)){
                        if(row.getBoolean("Installed")) ctx.actions().send("app_uninstall", row.getString("Id"));
                        else ctx.ui().openApp(DiagnosticTabletData.appId("app_store"), "store");
                        return true;
                    }
                }
            }
            case "about" -> {
                if(EDIT_NAME.contains(x, y)){
                    ctx.ui().editText("Tablet name", ctx.data().getString("Name"), 48,
                            val -> ctx.actions().send("tablet_rename", val));
                    return true;
                }
                if(WIKI.contains(x, y)) return openLink(ctx.data().getString("Wiki"));
                if(DISCORD.contains(x, y)) return openLink(ctx.data().getString("Discord"));
            }
            default -> {
                List<Theme> themes = themes(ctx);
                for(int idx = state.themes.firstVisibleIndex() * 3; idx < themes.size(); idx++){
                    TabletLayout.Rect card = themeCard(state, idx);
                    if(card.top() >= CONTENT.top() + CONTENT.height()) break;
                    if(card.contains(x, y)){
                        ctx.actions().send("wallpaper_set", themes.get(idx).texture().toString());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(TabletAppClientContext ctx, TabletAppClientState rawState,
                                 double mouseX, double mouseY, double scrollX, double scrollY){
        TabletLayout.Bounds bounds = bounds(ctx);
        if(!CANVAS.project(bounds, CONTENT).contains(mouseX, mouseY) || scrollY == 0) return false;
        ListScrollState scroll = configure(ctx, state(rawState));
        if(scroll == null || scroll.maximumOffset() == 0) return false;
        scroll.scrollBy((int)Math.round(-scrollY * 18));
        return true;
    }

    @Override
    public boolean mouseDragged(TabletAppClientContext ctx, TabletAppClientState rawState,
                                double mouseX, double mouseY, int btn, double deltaX, double deltaY){
        State state = state(rawState);
        TabletLayout.Bounds bounds = bounds(ctx);
        ListScrollState scroll = configure(ctx, state);
        if(scroll == null || !state.scrollbar.mouseDragged((mouseY - bounds.top()) / bounds.scaleY(), btn)) return false;
        scroll.scrollToFraction(state.scrollbar.fraction());
        return true;
    }

    @Override
    public boolean mouseReleased(TabletAppClientContext ctx, TabletAppClientState rawState,
                                 double mouseX, double mouseY, int btn){
        return state(rawState).scrollbar.mouseReleased();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add labels and the active rail to the baked sidebar buttons
    private static void renderSidebar(TabletAppClientContext ctx){
        var tabs = ctx.app().tabs();
        for(int idx = 0; idx < Math.min(3, tabs.size()); idx++){
            TabletLayout.Rect tab = sidebarTab(idx);
            text(ctx, tabs.get(idx).title().getString(), new TabletLayout.Rect(tab.left() + 5, tab.top(), 62, tab.height()),
                    1.25F, false, TEXT);
            if(tabs.get(idx).id().equals(ctx.tab().id())) SELECTED_TAB.draw(ctx.graphics(), 79, tab.top() + 5);
        }
    }

    // Keep complete and partially visible cards inside the content panel
    private static void renderThemes(TabletAppClientContext ctx, State state, double mouseX, double mouseY){
        List<Theme> themes = themes(ctx);
        ResourceLocation selected = TabletLauncherResources.resolve(ctx.data().getString("Wallpaper"),
                DiagnosticTabletSettingsApp.DEFAULT_LAUNCHER_TEXTURE);
        for(int idx = state.themes.firstVisibleIndex() * 3; idx < themes.size(); idx++){
            TabletLayout.Rect card = themeCard(state, idx);
            if(card.top() >= CONTENT.top() + CONTENT.height()) break;
            Theme theme = themes.get(idx);
            THEME_CARD.draw(ctx.graphics(), card.left(), card.top(), card.width(), card.height());
            TabletLauncherResources.render(ctx.graphics(), theme.texture(), card.left() + 4, card.top() + 5, 53, 28);
            if(theme.texture().equals(selected)) SELECTED_BUTTON.draw(ctx.graphics(), card.left() + 13, card.top() + 37, 35, 9);
            if(card.contains(mouseX, mouseY)) ctx.graphics().fill(card.left() + 2, card.top() + 2,
                    card.left() + card.width() - 2, card.top() + card.height() - 2, 0x11FFFFFF);
            text(ctx, theme.title(), new TabletLayout.Rect(card.left() + 14, card.top() + 37, 33, 9), .75F, true, TEXT);
        }
    }

    // Draw the supplied row partial with application icons and management actions
    private static void renderApps(TabletAppClientContext ctx, State state, double mouseX, double mouseY){
        ListTag apps = ctx.data().getList("Apps", Tag.TAG_COMPOUND);
        for(int idx = state.apps.firstVisibleIndex(); idx < apps.size(); idx++){
            int y = 40 + state.apps.rowOffset(idx);
            if(y >= CONTENT.top() + CONTENT.height()) break;
            CompoundTag row = apps.getCompound(idx);
            APP_ROW.draw(ctx.graphics(), 104, y);
            ResourceLocation appId = ResourceLocation.tryParse(row.getString("Id"));
            TabletAppDefinition app = appId == null ? null : TabletAppRegistry.definition(appId);
            if(app != null) TabletAppIcons.render(app, ctx.graphics(), 109, y + 6, 32);
            GuiControlPreviews.drawFittedText(ctx.graphics(), ctx.font(),
                    Component.literal(row.getString("Name")).withStyle(ChatFormatting.BOLD),
                    160, y + 4, 137, 13, 1.1F, TEXT, false);
            String status = row.getBoolean("Required") ? "Built-in" : row.getBoolean("Installed") ? "Installed" : "Available";
            text(ctx, status, new TabletLayout.Rect(160, y + 24, 92, 13), 1, false, TEXT);
            if(row.getBoolean("Required")) continue;
            if(row.getBoolean("Installed")) button(ctx, RUN_BUTTON, new TabletLayout.Rect(257, y + 26, 19, 11),
                    "Run", mouseX, mouseY);
            button(ctx, row.getBoolean("Installed") ? REMOVE_BUTTON : RUN_BUTTON,
                    new TabletLayout.Rect(280, y + 26, 19, 11), row.getBoolean("Installed") ? "Remove" : "Store", mouseX, mouseY);
        }
    }

    // Keep device identity and renaming beside version information
    private static void renderAbout(TabletAppClientContext ctx, double mouseX, double mouseY){
        text(ctx, "Create: Gadgets & Gizmos", new TabletLayout.Rect(107, 41, 195, 13), 1, false, TEXT);
        text(ctx, "Version " + ctx.data().getString("ModVersion") + " | Library " + ctx.data().getString("LibraryVersion"),
                new TabletLayout.Rect(109, 58, 192, 11), .9F, false, TEXT);
        ctx.graphics().drawWordWrap(ctx.font(), Component.literal(
                "Native ship, automation and logistics tools for Create and Sable."), 109, 72, 194, TEXT);
        text(ctx, "Tablet Identity", new TabletLayout.Rect(108, 98, 190, 12), 1, false, TEXT);
        IDENTITY.draw(ctx.graphics(), 107, 111, 190, 43);
        text(ctx, ctx.data().getString("Name"), new TabletLayout.Rect(113, 115, 176, 12), .95F, false, TEXT);
        String id = ctx.data().hasUUID("TabletId") ? ctx.data().getUUID("TabletId").toString() : "Unavailable";
        text(ctx, id, new TabletLayout.Rect(113, 131, 163, 12), .75F, false, MUTED);
        (EDIT_NAME.contains(mouseX, mouseY) ? EDIT_HOVER : EDIT).draw(ctx.graphics(), EDIT_NAME.left(), EDIT_NAME.top());
        button(ctx, BUTTON, WIKI, "Open G&G Wiki", mouseX, mouseY);
        button(ctx, BUTTON, DISCORD, "Join Discord", mouseX, mouseY);
    }

    // Preserve independent scroll offsets when changing sidebar tabs
    private static ListScrollState configure(TabletAppClientContext ctx, State state){
        ListScrollState scroll;
        if("about".equals(ctx.tab().id())) return null;
        if("apps".equals(ctx.tab().id())){
            scroll = state.apps;
            scroll.configure(ctx.data().getList("Apps", Tag.TAG_COMPOUND).size(), APP_ROW_HEIGHT, 123);
        }else{
            scroll = state.themes;
            scroll.configure((themes(ctx).size() + 2) / 3, THEME_ROW_HEIGHT, 121);
        }
        int thumb = Math.max(14, 123 * 123 / (123 + scroll.maximumOffset()));
        state.scrollbar.configure(309, 40, 2, 123, scroll.maximumOffset(), scroll.offset(), thumb);
        return scroll;
    }

    private static List<Theme> themes(TabletAppClientContext ctx){
        return TabletLauncherResources.themes(ctx.data().getList("Wallpapers", Tag.TAG_COMPOUND)).stream()
                .sorted(Comparator.comparing((Theme theme) -> !theme.texture().equals(DiagnosticTabletSettingsApp.DEFAULT_LAUNCHER_TEXTURE))
                        .thenComparing(theme -> theme.texture().toString())).toList();
    }

    private static TabletLayout.Rect themeCard(State state, int idx){
        return new TabletLayout.Rect(103 + idx % 3 * 67, 41 + state.themes.rowOffset(idx / 3), 61, 49);
    }

    private static TabletLayout.Rect sidebarTab(int idx){
        return new TabletLayout.Rect(14, 36 + idx * 22, 72, 19);
    }

    private static TabletLayout.Bounds bounds(TabletAppClientContext ctx){
        return CANVAS.stretch(ctx.surface().left(), ctx.surface().top(), ctx.surface().width(), ctx.surface().height());
    }

    private static State state(TabletAppClientState rawState){
        if(rawState instanceof State state) return state;
        throw new IllegalArgumentException("Settings received another application's state");
    }

    private static GuiTextureRegion region(int u, int v, int width, int height){
        return new GuiTextureRegion(TEXTURE, 320, 300, u, v, width, height);
    }

    private static void text(TabletAppClientContext ctx, String text, TabletLayout.Rect rect,
                              float scale, boolean centered, int color){
        GuiControlPreviews.drawFittedText(ctx.graphics(), ctx.font(), Component.literal(text),
                rect.left(), rect.top(), rect.width(), rect.height(), scale, color, centered);
    }

    private static void button(TabletAppClientContext ctx, GuiTextureRegion texture, TabletLayout.Rect rect,
                                String title, double mouseX, double mouseY){
        texture.draw(ctx.graphics(), rect.left(), rect.top(), rect.width(), rect.height());
        if(rect.contains(mouseX, mouseY)) ctx.graphics().fill(rect.left() + 1, rect.top() + 1,
                rect.left() + rect.width() - 1, rect.top() + rect.height() - 1, 0x22FFFFFF);
        text(ctx, title, new TabletLayout.Rect(rect.left() + 2, rect.top() + 1, rect.width() - 4, rect.height() - 2),
                .75F, true, TEXT);
    }

    private static boolean openLink(String uri){
        if(uri.isBlank()) return false;
        Util.getPlatform().openUri(uri);
        return true;
    }
}
