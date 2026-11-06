package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.text.LocalizedText;
import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientSurface;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppIcons;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppPanel;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletLayout;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiScrollbar;
import com.rieno.gadgetsandgizmos.lib.client.ui.ListScrollState;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Browse app cards and their detailed listings inside the painted store screens
public final class AppStore implements TabletAppClientRenderer{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation HOME_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "textures/gui/apps/screens/app_store_home.png");
    private static final ResourceLocation SELECTED_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "textures/gui/apps/screens/app_store_selected.png");
    private static final TabletLayout CANVAS = new TabletLayout(320, 180);
    private static final TabletLayout.Rect CATALOG = new TabletLayout.Rect(8, 38, 304, 128);
    private static final TabletLayout.Rect EXPLORE = new TabletLayout.Rect(10, 121, 300, 49);
    private static final TabletLayout.Rect DESCRIPTION = new TabletLayout.Rect(17, 94, 286, 10);
    private static final GuiTextureRegion HOME = region(HOME_TEXTURE, 0, 0, 320, 180);
    private static final GuiTextureRegion SELECTED = region(SELECTED_TEXTURE, 0, 0, 320, 180);
    private static final GuiTextureRegion CARD = region(HOME_TEXTURE, 0, 254, 93, 46);
    private static final GuiTextureRegion DETAILS = region(SELECTED_TEXTURE, 0, 185, 306, 67);
    private static final GuiTextureRegion TAB = region(HOME_TEXTURE, 277, 264, 43, 11);
    private static final GuiTextureRegion INSTALL = region(SELECTED_TEXTURE, 147, 262, 43, 11);
    private static final int ROW_HEIGHT = 51;
    private static final float DESCRIPTION_SCALE = .55F;
    private static final int TEXT = 0xFFF0F0F0;
    private static final int MUTED = 0xFF929292;
    private static final int GREEN = 0xFF53C867;
    private static final String SEARCH = "app_search";

    private static final class State implements TabletAppClientState{
        private final TabletAppPanel panel = new TabletAppPanel();
        private final ListScrollState discover = new ListScrollState();
        private final ListScrollState installed = new ListScrollState();
        private final ListScrollState explore = new ListScrollState();
        private final ListScrollState description = new ListScrollState();
        private final GuiScrollbar scrollbar = new GuiScrollbar(0x55333333, 0xFF888888);
        private final Map<String, Long> pending = new HashMap<>();
        private boolean installedOnly;
        private String selectedId = "";
        private String query = "";
    }

    @Override public TabletAppClientState createScreenState(){ return new State(); }
    @Override public boolean ownsTabletSurface(){ return true; }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        RENDERING
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override
    public void render(TabletAppClientContext ctx, TabletAppClientState raw){
        State state = state(raw);
        prepare(ctx, state);
        TabletLayout.Bounds bounds = bounds(ctx);
        TabletAppClientContext local = local(ctx, bounds);
        CompoundTag selected = selected(ctx, state);
        ctx.graphics().pose().pushPose();
        try{
            ctx.graphics().pose().translate(bounds.left(), bounds.top(), 0);
            ctx.graphics().pose().scale((float)bounds.scaleX(), (float)bounds.scaleY(), 1);
            (selected.isEmpty() ? HOME : SELECTED).draw(ctx.graphics(), 0, 0);
            state.panel.begin(local, false);
            renderHeader(local, state);
            if(selected.isEmpty()) renderGrid(local, state, visibleApps(ctx, state), false);
            else{
                renderSelection(local, state, selected);
                text(local, Component.translatable("createthrusters.text.explore_applications").withStyle(ChatFormatting.BOLD),
                        10, 108, 294, 11, .8F, TEXT, false);
                renderGrid(local, state, recommendations(ctx, state), true);
            }
            if(selected.isEmpty() && ctx.data().contains("Error")){
                ctx.graphics().fill(12, 154, 308, 165, 0xEE292929);
                text(local, Component.literal(LocalizedText.read(ctx.data(), "Error").getString()), 14, 154, 292, 10, .75F, 0xFFFFAAAA, false);
            }
        }finally{ ctx.graphics().pose().popPose(); }
    }

    // Keep search and store filters available on both screens
    private static void renderHeader(TabletAppClientContext ctx, State state){
        state.panel.paintedButton(ctx, TAB, 9, 22, 42, 11, LocalizedText.get("createthrusters.text.discover"), true, () -> browse(state, false));
        state.panel.paintedButton(ctx, TAB, 59, 22, 42, 11, LocalizedText.get("createthrusters.text.installed"), true, () -> browse(state, true));
        state.panel.paintedInput(ctx, SEARCH, 241, 17, 71, 15, LocalizedText.get("createthrusters.text.search"));
    }

    // Use the same card geometry for painting and selection, including partial rows
    private static void renderGrid(TabletAppClientContext ctx, State state, List<CompoundTag> apps, boolean explore){
        TabletLayout.Rect viewport = explore ? EXPLORE : CATALOG;
        ListScrollState scroll = gridScroll(state, explore);
        configureGrid(scroll, apps.size(), explore);
        try(var clip = GuiPoseScissor.push(ctx.graphics(), viewport.left(), viewport.top(),
                viewport.left() + viewport.width(), viewport.top() + viewport.height())){
            for(int idx = scroll.firstVisibleIndex() * 3; idx < apps.size(); idx++){
                TabletLayout.Rect card = card(scroll, idx, explore);
                if(card.top() >= viewport.top() + viewport.height()) break;
                CompoundTag app = apps.get(idx);
                CARD.draw(ctx.graphics(), card.left(), card.top(), card.width(), card.height());
                drawIcon(ctx, app, card.left() + 7, card.top() + 9, 25);
                text(ctx, Component.literal(LocalizedText.read(app, "Name").getString()).withStyle(ChatFormatting.BOLD),
                        card.left() + 40, card.top() + 8, 47, 13, .85F, TEXT, true);
                ctx.graphics().fill(card.left() + 40, card.top() + 23, card.left() + 82, card.top() + 24, TEXT);
                text(ctx, Component.translatable(app.getBoolean("Installed") ? "createthrusters.text.installed" : "createthrusters.text.install"),
                        card.left() + 40, card.top() + 25, 47, 12, .85F, app.getBoolean("Installed") ? MUTED : GREEN, true);
            }
            if(apps.isEmpty()) text(ctx, (explore ? Component.translatable("createthrusters.text.no_other_applications") : Component.translatable(state.installedOnly ? "createthrusters.text.no_installed_applications_match_your_search" : "createthrusters.text.no_applications_match_your_search")),
                    viewport.left() + 5, viewport.top() + 5, viewport.width() - 10, 20, .85F, MUTED, true);
        }
        configureScrollbar(state, scroll, viewport, explore);
        if(scroll.maximumOffset() > 0) state.scrollbar.render(ctx.graphics());
    }

    // Show the summary, full description and current server-owned installation state
    private static void renderSelection(TabletAppClientContext ctx, State state, CompoundTag app){
        DETAILS.draw(ctx.graphics(), 7, 41);
        drawIcon(ctx, app, 18, 49, 27);
        text(ctx, Component.literal(LocalizedText.read(app, "Name").getString()).withStyle(ChatFormatting.BOLD),
                51, 46, 204, 14, 1.05F, TEXT, false);
        text(ctx, Component.literal(LocalizedText.read(app, "Description").getString()), 51, 61, 201, 10, .65F, 0xFFBDBDBD, false);
        text(ctx, Component.literal(priceLabel(app)), 51, 72, 200, 9, .65F,
                app.getInt("PriceCount") <= 0 ? GREEN : 0xFFE3BA63, false);
        if(app.getBoolean("Installed")) text(ctx, Component.translatable("createthrusters.text.installed"), 264, 67, 42, 11, .85F, TEXT, true);
        else{
            String id = app.getString("Id");
            boolean pending = state.pending.containsKey(id);
            String action = pending ? LocalizedText.get("createthrusters.text.installing") : app.getBoolean("Owned") || app.getInt("PriceCount") <= 0 ? LocalizedText.get("createthrusters.text.install") : LocalizedText.get("createthrusters.text.purchase");
            state.panel.paintedButton(ctx, INSTALL, 264, 67, 42, 11, action, !pending, () -> {
                if(state.pending.putIfAbsent(id, System.nanoTime()) == null) ctx.actions().send("purchase_app", id);
            });
        }
        if(ctx.data().contains("Error")){
            text(ctx, Component.literal(LocalizedText.read(ctx.data(), "Error").getString()), DESCRIPTION.left(), DESCRIPTION.top(),
                    DESCRIPTION.width(), DESCRIPTION.height(), .75F, 0xFFFFAAAA, false);
            return;
        }
        String details = app.getString("Details");
        if(details.isBlank()) details = LocalizedText.read(app, "Description").getString();
        var lines = ctx.font().split(Component.literal(details), (int)(DESCRIPTION.width() / DESCRIPTION_SCALE));
        state.description.configure(lines.size(), ctx.font().lineHeight, (int)(DESCRIPTION.height() / DESCRIPTION_SCALE));
        try(var clip = GuiPoseScissor.push(ctx.graphics(), DESCRIPTION.left(), DESCRIPTION.top(),
                DESCRIPTION.left() + DESCRIPTION.width(), DESCRIPTION.top() + DESCRIPTION.height())){
            ctx.graphics().pose().pushPose();
            try{
                ctx.graphics().pose().translate(DESCRIPTION.left(), DESCRIPTION.top(), 1);
                ctx.graphics().pose().scale(DESCRIPTION_SCALE, DESCRIPTION_SCALE, 1);
                for(int idx = state.description.firstVisibleIndex(); idx < lines.size(); idx++){
                    int y = state.description.rowOffset(idx);
                    if(y >= DESCRIPTION.height() / DESCRIPTION_SCALE) break;
                    ctx.graphics().drawString(ctx.font(), lines.get(idx), 0, y, TEXT, false);
                }
            }finally{ ctx.graphics().pose().popPose(); }
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        INPUT
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override
    public boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState raw, double mouseX, double mouseY, int btn){
        State state = state(raw);
        TabletLayout.Bounds bounds = bounds(ctx);
        double x = (mouseX - bounds.left()) / bounds.scaleX();
        double y = (mouseY - bounds.top()) / bounds.scaleY();
        if(state.panel.click(x, y, btn)) return true;
        if(btn != 0 && !(btn == 1 && ctx.ui().projected())) return false;
        prepare(ctx, state);
        boolean explore = !state.selectedId.isEmpty();
        TabletLayout.Rect viewport = explore ? EXPLORE : CATALOG;
        if(!viewport.contains(x, y)) return false;
        List<CompoundTag> apps = explore ? recommendations(ctx, state) : visibleApps(ctx, state);
        ListScrollState scroll = gridScroll(state, explore);
        configureGrid(scroll, apps.size(), explore);
        configureScrollbar(state, scroll, viewport, explore);
        if(state.scrollbar.mouseClicked(x, y, 0)){
            scroll.scrollToFraction(state.scrollbar.fraction()); return true;
        }
        for(int idx = scroll.firstVisibleIndex() * 3; idx < apps.size(); idx++){
            TabletLayout.Rect card = card(scroll, idx, explore);
            if(card.top() >= viewport.top() + viewport.height()) break;
            if(!card.contains(x, y)) continue;
            CompoundTag app = apps.get(idx);
            if(explore){
                state.panel.setValue(SEARCH, ""); state.query = "";
                if(!app.getBoolean("Installed")) state.installedOnly = false;
            }
            state.selectedId = app.getString("Id");
            state.explore.scrollToFraction(0); state.description.scrollToFraction(0); state.scrollbar.reset();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(TabletAppClientContext ctx, TabletAppClientState raw, double mouseX, double mouseY, double sx, double sy){
        State state = state(raw);
        TabletLayout.Bounds bounds = bounds(ctx);
        double x = (mouseX - bounds.left()) / bounds.scaleX();
        double y = (mouseY - bounds.top()) / bounds.scaleY();
        boolean explore = !state.selectedId.isEmpty();
        if(explore && DESCRIPTION.contains(x, y)){
            state.description.scrollBy((int)Math.round(-sy * ctx.font().lineHeight)); return true;
        }
        TabletLayout.Rect viewport = explore ? EXPLORE : CATALOG;
        if(!viewport.contains(x, y)) return false;
        ListScrollState scroll = gridScroll(state, explore);
        configureGrid(scroll, (explore ? recommendations(ctx, state) : visibleApps(ctx, state)).size(), explore);
        scroll.scrollBy(explore ? -(int)Math.signum(sy) * ROW_HEIGHT : (int)Math.round(-sy * 24));
        return true;
    }

    @Override
    public boolean mouseDragged(TabletAppClientContext ctx, TabletAppClientState raw, double mouseX, double mouseY, int btn, double dx, double dy){
        State state = state(raw);
        if(!state.scrollbar.mouseDragged((mouseY - bounds(ctx).top()) / bounds(ctx).scaleY(), btn == 1 && ctx.ui().projected() ? 0 : btn)) return false;
        gridScroll(state, !state.selectedId.isEmpty()).scrollToFraction(state.scrollbar.fraction()); return true;
    }

    @Override public boolean mouseReleased(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn){ return state(raw).scrollbar.mouseReleased(); }
    @Override
    public boolean keyPressed(TabletAppClientContext ctx, TabletAppClientState raw, int key, int scan, int mods){
        State state = state(raw);
        if(state.panel.keyPressed(key, mods)) return true;
        if(key != 256 || state.selectedId.isEmpty()) return false;
        browse(state, state.installedOnly);
        return true;
    }
    @Override public boolean chatTyped(TabletAppClientContext ctx, TabletAppClientState raw, char ch, int mods){ return state(raw).panel.charTyped(ch); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static void prepare(TabletAppClientContext ctx, State state){
        String query = state.panel.value(SEARCH).strip().toLowerCase(Locale.ROOT);
        if(!query.equals(state.query)){
            state.query = query; state.selectedId = "";
            state.discover.scrollToFraction(0); state.installed.scrollToFraction(0); state.explore.scrollToFraction(0); state.scrollbar.reset();
        }
        if(!state.selectedId.isEmpty() && selected(ctx, state).isEmpty()) state.selectedId = "";
        long now = System.nanoTime();
        state.pending.entrySet().removeIf(entry -> now - entry.getValue() > 5_000_000_000L
                || apps(ctx).stream().anyMatch(app -> app.getString("Id").equals(entry.getKey()) && app.getBoolean("Installed")));
    }

    private static void browse(State state, boolean installed){
        state.installedOnly = installed; state.selectedId = ""; state.scrollbar.reset();
    }

    private static List<CompoundTag> apps(TabletAppClientContext ctx){
        return ctx.data().getList("Apps", Tag.TAG_COMPOUND).stream().map(tag -> (CompoundTag)tag)
                .filter(app -> ResourceLocation.tryParse(app.getString("Id")) != null).toList();
    }

    private static List<CompoundTag> visibleApps(TabletAppClientContext ctx, State state){
        return apps(ctx).stream().filter(app -> !state.installedOnly || app.getBoolean("Installed"))
                .filter(app -> (LocalizedText.read(app, "Name").getString() + " " + LocalizedText.read(app, "Description").getString()).toLowerCase(Locale.ROOT).contains(state.query)).toList();
    }

    private static List<CompoundTag> recommendations(TabletAppClientContext ctx, State state){
        return apps(ctx).stream().filter(app -> !app.getString("Id").equals(state.selectedId)).toList();
    }

    private static CompoundTag selected(TabletAppClientContext ctx, State state){
        return apps(ctx).stream().filter(app -> app.getString("Id").equals(state.selectedId)).findFirst().orElseGet(CompoundTag::new);
    }

    private static ListScrollState gridScroll(State state, boolean explore){ return explore ? state.explore : state.installedOnly ? state.installed : state.discover; }
    private static void configureGrid(ListScrollState scroll, int count, boolean explore){ scroll.configure((count + 2) / 3, ROW_HEIGHT, explore ? ROW_HEIGHT : 123); }
    private static TabletLayout.Rect card(ListScrollState scroll, int idx, boolean explore){ return new TabletLayout.Rect(12 + idx % 3 * 103, (explore ? 122 : 41) + scroll.rowOffset(idx / 3), 92, 45); }

    private static void configureScrollbar(State state, ListScrollState scroll, TabletLayout.Rect viewport, boolean explore){
        state.scrollbar.configure(explore ? 310 : 311, viewport.top() + 2, 1, viewport.height() - 4, scroll.maximumOffset(), scroll.offset(),
                Math.max(6, (viewport.height() - 4) * (viewport.height() - 4) / (viewport.height() - 4 + scroll.maximumOffset())));
    }

    private static String priceLabel(CompoundTag app){
        int count = app.getInt("PriceCount");
        if(count <= 0) return "FREE";
        if(app.getBoolean("Owned")) return "OWNED";
        ResourceLocation id = ResourceLocation.tryParse(app.getString("PriceItem"));
        String name = id == null ? "items" : BuiltInRegistries.ITEM.getOptional(id)
                .map(item -> item.getDescription().getString()).orElse(id.getPath().replace('_', ' '));
        if(count != 1 && !name.toLowerCase(Locale.ROOT).endsWith("s")) name += "s";
        return count + " " + name;
    }

    private static void drawIcon(TabletAppClientContext ctx, CompoundTag app, int x, int y, int size){
        ResourceLocation id = ResourceLocation.tryParse(app.getString("Id"));
        TabletAppDefinition definition = id == null ? null : TabletAppRegistry.definition(id);
        TabletAppIcons.render(definition == null ? ctx.app() : definition, ctx.graphics(), x, y, size);
    }

    private static void text(TabletAppClientContext ctx, Component label, int x, int y, int width, int height, float scale, int color, boolean centered){
        GuiControlPreviews.drawFittedText(ctx.graphics(), ctx.font(), label, x, y, width, height, scale, color, centered);
    }
    private static State state(TabletAppClientState raw){ if(raw instanceof State state) return state; throw new IllegalArgumentException(LocalizedText.get("createthrusters.text.app_store_received_another_application_s_state")); }
    private static GuiTextureRegion region(ResourceLocation texture, int x, int y, int width, int height){ return new GuiTextureRegion(texture, 320, 300, x, y, width, height); }
    private static TabletLayout.Bounds bounds(TabletAppClientContext ctx){ return CANVAS.stretch(ctx.surface().left(), ctx.surface().top(), ctx.surface().width(), ctx.surface().height()); }
    private static TabletAppClientContext local(TabletAppClientContext ctx, TabletLayout.Bounds bounds){
        return new TabletAppClientContext(ctx.app(), ctx.tab(), ctx.data(), ctx.graphics(), ctx.font(), 0, 0, 320, 180,
                new TabletAppClientSurface(0, 0, 320, 180), (int)((ctx.mouseX() - bounds.left()) / bounds.scaleX()),
                (int)((ctx.mouseY() - bounds.top()) / bounds.scaleY()), ctx.actions(), ctx.refresh(), ctx.ui());
    }
}
