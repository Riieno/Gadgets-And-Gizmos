package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelPreviewRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientSurface;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiScrollbar;
import com.rieno.gadgetsandgizmos.lib.client.ui.ListScrollState;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletInteractionMode;
import net.minecraft.network.chat.Component;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelSnapshotBlocks;
import com.rieno.gadgetsandgizmos.lib.client.schematic.ClientSchematicFiles;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppPanel;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletLayout;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletMultiSelectDropdown;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematicFiles;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SchematicProvenance;
import com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// Browse sublevels and archived assemblies in a dedicated interactive 3D view
public final class Digisable implements TabletAppClientRenderer{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "textures/gui/apps/screens/digisable.png");
    private static final TabletLayout CANVAS = new TabletLayout(320, 180);
    private static final TabletLayout.Rect LIST = new TabletLayout.Rect(12, 60, 77, 92);
    private static final TabletLayout.Rect VIEW = new TabletLayout.Rect(96, 39, 130, 112);
    private static final TabletLayout.Rect MATERIALS = new TabletLayout.Rect(98, 56, 126, 94);
    private static final GuiTextureRegion BACKGROUND = region(0, 0, 320, 180);
    private static final GuiTextureRegion SMALL_BUTTON = region(0, 195, 43, 11);
    private static final GuiTextureRegion SELECTED_TAB = region(78, 196, 43, 11);
    private static final GuiTextureRegion BUTTON = region(0, 210, 78, 11);
    private static final GuiTextureRegion PRIMARY_BUTTON = region(0, 225, 77, 11);
    private static final GuiTextureRegion DANGER_BUTTON = region(84, 225, 78, 11);
    private static final GuiTextureRegion FIELD = region(84, 210, 75, 11);
    private static final GuiTextureRegion MODE = region(0, 185, 44, 8);
    private static final GuiTextureRegion EMPTY_CARD = region(0, 240, 76, 60);
    private static final GuiTextureRegion EMPTY_ICON = region(53, 193, 13, 14);
    private static final GuiTextureRegion SCROLL_TRACK = region(79, 42, 1, 8);
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF929292;
    private static final int GOLD = 0xFFE3BA63;
    private static final int ROW_HEIGHT = 14;

    private static final class State implements TabletAppClientState, AutoCloseable{
        private final TabletAppPanel panel = new TabletAppPanel();
        private final TabletMultiSelectDropdown filters = new TabletMultiSelectDropdown(List.of(
                new TabletMultiSelectDropdown.Option("PHOTOMANCY", "Photomancy"),
                new TabletMultiSelectDropdown.Option("TOOLGUN", "Toolgun"),
                new TabletMultiSelectDropdown.Option("NATIVE", "G&G"),
                new TabletMultiSelectDropdown.Option("UNKNOWN", "Unknown")));
        private final SubLevelPreviewRenderer preview = new SubLevelPreviewRenderer(ResourceLocation.fromNamespaceAndPath(
                CreateThrusters.MOD_ID, "digisable_preview"), 2048, null);
        private final ListScrollState list = new ListScrollState();
        private final ListScrollState materialList = new ListScrollState();
        private final GuiScrollbar scrollbar = new GuiScrollbar(SCROLL_TRACK, SELECTED_TAB);
        private UUID selected;
        private String tab = "";
        private UUID confirmDelete;
        private long confirmUntil;
        private String query = "";
        private Set<String> formats = Set.of("PHOTOMANCY", "TOOLGUN", "NATIVE", "UNKNOWN");
        private boolean dragging;
        private boolean materials;
        private ListTag lastPreview = new ListTag();
        private TabletLayout.Rect view = new TabletLayout.Rect(0, 0, 1, 1);
        @Override public void close(){ preview.close(); }
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
        State state = (State)raw;
        ListTag rows = prepare(ctx, state);
        CompoundTag selected = selected(rows, state.selected);
        TabletLayout.Bounds bounds = bounds(ctx);
        TabletAppClientContext local = local(ctx, bounds);
        state.view = CANVAS.project(bounds, VIEW);
        ctx.graphics().pose().pushPose();
        try{
            ctx.graphics().pose().translate(bounds.left(), bounds.top(), 0);
            ctx.graphics().pose().scale((float)bounds.scaleX(), (float)bounds.scaleY(), 1);
            BACKGROUND.draw(ctx.graphics(), 0, 0);
            state.panel.begin(local, false);
            renderToolbar(local, state);
            state.panel.paintedInput(local, searchKey(ctx), 14, 37, 72, 16, "Search");
            renderList(local, state, rows);
            renderDetails(local, state, selected);
            if(state.materials) renderMaterials(local, state);
            else if(!selected.isEmpty()){
                BUTTON.draw(ctx.graphics(), 96, 151, 130, 17);
                text(local, "Drag to rotate / Scroll to zoom", 99, 153, 124, 13, .8F, TEXT, true);
            }
        }finally{ ctx.graphics().pose().popPose(); }
        // Render the preview at the projected pixel size before foreground controls
        if(!state.materials && !selected.isEmpty()){
            updatePreview(ctx, state);
            ctx.graphics().flush();
            try(var clip = GuiPoseScissor.push(ctx.graphics(), state.view.left(), state.view.top(),
                    state.view.left() + state.view.width(), state.view.top() + state.view.height())){
                state.preview.render(ctx.graphics(), state.view.left(), state.view.top(), state.view.width(), state.view.height(), 1, List.of());
            }
        }
        ctx.graphics().pose().pushPose();
        try{
            ctx.graphics().pose().translate(bounds.left(), bounds.top(), 0);
            ctx.graphics().pose().scale((float)bounds.scaleX(), (float)bounds.scaleY(), 1);
            if("schematics".equals(ctx.tab().id()) && !state.materials)
                state.filters.renderPainted(local, SMALL_BUTTON, SELECTED_TAB, 149, 23, 69, 11, "Formats");
            if(ctx.data().contains("Error")){
                FIELD.draw(ctx.graphics(), 98, 40, 126, 29);
                wrap(local, ctx.data().getString("Error"), 101, 42, 120, 25, .7F, 0xFFFFAAAA);
            }
        }finally{ ctx.graphics().pose().popPose(); }
    }

    // Keep navigation and Reader mode controls in the painted top toolbar
    private static void renderToolbar(TabletAppClientContext ctx, State state){
        var tabs = ctx.app().tabs();
        for(int idx = 0; idx < tabs.size(); idx++){
            var tab = tabs.get(idx);
            if("sublevels".equals(tab.id()) && !ctx.data().getBoolean("SublevelsAllowed")) continue;
            state.panel.paintedButton(ctx, tab.id().equals(ctx.tab().id()) ? SELECTED_TAB : SMALL_BUTTON,
                    10 + idx * 45, 23, 42, 11, tab.title().getString(), true, () -> ctx.ui().selectTab(tab.id()));
        }
        state.panel.paintedButton(ctx, SMALL_BUTTON, 223, 23, 35, 11, "Refresh", true, () -> {
            if("schematics".equals(ctx.tab().id())) ClientSchematicFiles.refresh();
            ctx.refresh().run();
        });
        if("sublevels".equals(ctx.tab().id())) state.panel.paintedButton(ctx, SMALL_BUTTON, 149, 23, 69, 11,
                "Delete from world", ctx.data().getBoolean("DeleteAllowed"), () -> ctx.actions().send("begin_reader", "delete"));
        boolean reader = ctx.ui().interactionMode() == TabletInteractionMode.READER;
        state.panel.paintedButton(ctx, MODE, 271, 24, 43, 8, "", true, () -> {
            if(reader) ctx.ui().setInteractionMode(TabletInteractionMode.STANDARD);
            else ctx.actions().send("begin_reader", "schematics".equals(ctx.tab().id()) ? "pair_network" : "store");
        });
        text(ctx, reader ? "Reader" : ctx.ui().interactionMode() == TabletInteractionMode.PUSH ? "Push" : "Standard",
                273, 24, 39, 8, .85F, reader ? 0xFF31B9EF : TEXT, true);
    }

    // Clip rows to the left pane and leave hit testing to the same visible bounds
    private static void renderList(TabletAppClientContext ctx, State state, ListTag rows){
        try(var clip = GuiPoseScissor.push(ctx.graphics(), LIST.left(), LIST.top(), LIST.left() + LIST.width(), LIST.top() + LIST.height())){
            for(int idx = state.list.firstVisibleIndex(); idx < rows.size(); idx++){
                int y = LIST.top() + state.list.rowOffset(idx);
                if(y >= LIST.top() + LIST.height()) break;
                CompoundTag row = rows.getCompound(idx);
                (row.getUUID("Id").equals(state.selected) ? PRIMARY_BUTTON : BUTTON).draw(ctx.graphics(), LIST.left(), y, LIST.width(), 11);
                text(ctx, name(ctx, row), LIST.left() + 4, y + 1, LIST.width() - 8, 9, .9F, TEXT, false);
            }
        }
        if(rows.isEmpty()){
            EMPTY_CARD.draw(ctx.graphics(), 12, 57);
            EMPTY_ICON.draw(ctx.graphics(), 44, 65, 11, 12);
            String title = !state.query.isBlank() ? "No matching assemblies" : "schematics".equals(ctx.tab().id())
                    ? "No compatible schematics" : "archives".equals(ctx.tab().id()) ? "No Stored Assemblies" : "No accessible sublevels";
            text(ctx, title, 16, 81, 68, 10, .6F, MUTED, true);
            String help = "archives".equals(ctx.tab().id()) ? "Store a nearby assembly to see it here"
                    : "schematics".equals(ctx.tab().id()) ? "Save a schematic or open its directory"
                    : ctx.data().getBoolean("LocateAllowed") ? "No assemblies are within range" : "Locating is disabled by the server";
            wrap(ctx, help, 16, 94, 68, 20, .65F, MUTED);
        }
        if(state.list.maximumOffset() > 0){
            state.scrollbar.configure(88, 60, 1, 92, state.list.maximumOffset(), state.list.offset(),
                    Math.max(12, 92 * 92 / (92 + state.list.maximumOffset())));
            state.scrollbar.render(ctx.graphics());
        }
    }

    // Keep gameplay validation and placement actions behind the painted controls
    private static void renderDetails(TabletAppClientContext ctx, State state, CompoundTag selected){
        boolean archives = "archives".equals(ctx.tab().id());
        boolean schematics = "schematics".equals(ctx.tab().id());
        boolean picked = state.selected != null && !selected.isEmpty();
        text(ctx, picked ? name(ctx, selected) : "Selected Assembly", 235, 39, 77, 13, .9F, GOLD, false);
        ctx.graphics().fill(235, 55, 308, 56, 0xFFE8E8E8);
        if(archives){
            field(ctx, "Capacity", ctx.data().getList("Archives", Tag.TAG_COMPOUND).size() + "/" + ctx.data().getInt("ArchiveLimit") + " slots", 62);
            button(ctx, state, 155, "Extract", picked && ctx.data().getBoolean("StoreAllowed"),
                    () -> ctx.actions().send("prepare_extract", state.selected.toString()), false);
        }else if(schematics){
            field(ctx, "Source", picked ? selected.getBoolean("Client") ? "Client" : "Shared" : "Not selected", 62);
            field(ctx, "Network", ctx.data().getBoolean("NetworkBound") ? "Paired" : "Not Paired", 85);
            String status = !ClientSchematicFiles.status().isBlank() ? ClientSchematicFiles.status()
                    : ctx.data().getBoolean("Building") ? "Building " + ctx.data().getInt("BuildPlaced") + "/" + ctx.data().getInt("BuildTotal")
                    : ctx.data().getBoolean("Creative") ? "Creative" : ready(ctx, state) ? "Materials ready" : "Check materials";
            text(ctx, status, 236, 111, 75, 14, .7F, MUTED, false);
            button(ctx, state, 131, ctx.data().getBoolean("Building") ? "Cancel build" : "Build",
                    ctx.data().getBoolean("Building") || ready(ctx, state), () -> ctx.actions().send(
                            ctx.data().getBoolean("Building") ? "cancel_build" : "prepare_build",
                            ctx.data().getBoolean("Building") ? "" : state.selected.toString()), true);
            button(ctx, state, 143, state.materials ? "Preview" : "Materials", validated(ctx, state) || ctx.data().getBoolean("Building"), () -> {
                state.materials = !state.materials;
                state.materialList.scrollToFraction(0);
                if(state.materials && state.selected != null) ctx.actions().send("materials", state.selected.toString());
            }, false);
            button(ctx, state, 155, "Pair Network", ctx.data().getBoolean("SchematicsAllowed"),
                    () -> ctx.actions().send("begin_reader", "pair_network"), false);
        }else{
            field(ctx, "Position", picked ? "%d, %d, %d".formatted(Mth.floor(selected.getDouble("X")),
                    Mth.floor(selected.getDouble("Y")), Mth.floor(selected.getDouble("Z"))) : "Not selected", 62);
            field(ctx, "Distance", picked ? selected.getInt("Distance") + "m" : "-", 85);
            if(picked && !selected.getBoolean("Loaded")) text(ctx, "Last known location", 236, 109, 75, 12, .7F, MUTED, false);
            button(ctx, state, 131, "Store Selected", picked && selected.getBoolean("Loaded") && ctx.data().getBoolean("RemoteStoreAllowed"),
                    () -> ctx.actions().send("store_selected", state.selected.toString()), true);
            button(ctx, state, 143, "Teleport", picked && ctx.data().getBoolean("TeleportAllowed"),
                    () -> ctx.actions().send("teleport", state.selected.toString()), false);
            boolean confirm = picked && state.selected.equals(state.confirmDelete) && System.currentTimeMillis() < state.confirmUntil;
            state.panel.paintedButton(ctx, DANGER_BUTTON, 235, 155, 77, 11, confirm ? "Confirm delete" : "Delete",
                    picked && selected.getBoolean("Loaded") && selected.getBoolean("Unclaimed") && ctx.data().getBoolean("DeleteAllowed"), () -> {
                        if(state.selected != null && state.selected.equals(state.confirmDelete) && System.currentTimeMillis() < state.confirmUntil){
                            ctx.actions().send("delete", state.selected.toString()); state.confirmDelete = null;
                        }
                        else{ state.confirmDelete = state.selected; state.confirmUntil = System.currentTimeMillis() + 5000; }
                    });
        }
        if(schematics){
            state.panel.paintedButton(ctx, SMALL_BUTTON, 12, 156, 29, 10, "Save", ctx.data().getBoolean("SchematicsAllowed"),
                    () -> ctx.actions().send("begin_reader", "save_schematic"));
            state.panel.paintedButton(ctx, SMALL_BUTTON, 47, 156, 42, 10, "Open Directory", true, ClientSchematicFiles::openDirectory);
        }else state.panel.paintedButton(ctx, PRIMARY_BUTTON, 12, 155, 77, 11, "Store From World", ctx.data().getBoolean("StoreAllowed"),
                () -> ctx.actions().send("begin_reader", "store"));
    }

    // Keep materials, availability and construction progress in the center pane
    private static void renderMaterials(TabletAppClientContext ctx, State state){
        text(ctx, "Schematic materials", 99, 40, 124, 12, .85F, TEXT, false);
        ListTag rows = ctx.data().getList("Materials", Tag.TAG_COMPOUND);
        state.materialList.configure(rows.size(), 18, MATERIALS.height());
        try(var clip = GuiPoseScissor.push(ctx.graphics(), MATERIALS.left(), MATERIALS.top(), MATERIALS.left() + MATERIALS.width(), MATERIALS.top() + MATERIALS.height())){
            for(int idx = state.materialList.firstVisibleIndex(); idx < rows.size(); idx++){
                int y = MATERIALS.top() + state.materialList.rowOffset(idx);
                if(y >= MATERIALS.top() + MATERIALS.height()) break;
                CompoundTag row = rows.getCompound(idx);
                var level = Minecraft.getInstance().level;
                if(level != null){
                    ItemStack stack = ItemStack.parseOptional(level.registryAccess(), row.getCompound("Stack"));
                    ctx.graphics().pose().pushPose();
                    try{
                        ctx.graphics().pose().translate(99, y + 2, 0);
                        ctx.graphics().pose().scale(.625F, .625F, 1);
                        ctx.graphics().renderItem(stack, 0, 0);
                    }finally{ ctx.graphics().pose().popPose(); }
                }
                text(ctx, row.getString("Name"), 112, y, 110, 9, .75F, TEXT, false);
                text(ctx, "Need " + row.getLong("Missing") + "  " + row.getLong("Available") + "/" + row.getInt("Required")
                        + (row.getBoolean("Tool") ? " tool" : ""), 112, y + 9, 110, 9, .65F,
                        row.getLong("Missing") > 0 ? 0xFFFFAAAA : 0xFF91D89B, false);
            }
        }
        if(ctx.data().contains("MaterialError")) wrap(ctx, ctx.data().getString("MaterialError"), 100, 57, 122, 35, .7F, 0xFFFFAAAA);
        if(rows.isEmpty()) wrap(ctx, ctx.data().getBoolean("Creative") ? "Creative: no items required" : ctx.data().getString("NetworkHelp"), 100, 58, 122, 50, .8F, MUTED);
        if(ctx.data().getBoolean("Building")) state.panel.progress(ctx, 99, 146, 124, 3,
                ctx.data().getInt("BuildTotal") == 0 ? 0 : (double)ctx.data().getInt("BuildPlaced") / ctx.data().getInt("BuildTotal"));
        state.panel.paintedButton(ctx, BUTTON, 96, 151, 93, 17, "Back to preview", true, () -> state.materials = false);
        state.panel.paintedButton(ctx, SMALL_BUTTON, 191, 151, 35, 17, "Refresh", state.selected != null,
                () -> ctx.actions().send("materials", state.selected.toString()));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Reconcile selection after tab, search and catalogue changes
    private static ListTag prepare(TabletAppClientContext ctx, State state){
        if(!state.tab.equals(ctx.tab().id())){
            state.tab = ctx.tab().id();
            clearSelection(state);
            state.list.scrollToFraction(0);
            state.materials = false;
            state.filters.close();
            state.panel.clearFocus();
            state.scrollbar.reset();
        }
        ListTag rows = visibleRows(ctx, state);
        state.list.configure(rows.size(), ROW_HEIGHT, LIST.height());
        if(state.selected != null && selected(rows, state.selected).isEmpty()) clearSelection(state);
        if(state.selected == null && !rows.isEmpty()){
            if("schematics".equals(ctx.tab().id()) && ctx.data().hasUUID("SchematicSelected")
                    && !selected(rows, ctx.data().getUUID("SchematicSelected")).isEmpty()) state.selected = ctx.data().getUUID("SchematicSelected");
            if(state.selected == null && state.query.isBlank()) select(ctx, state, rows.getCompound(0).getUUID("Id"));
        }
        return rows;
    }

    private static void clearSelection(State state){
        state.selected = null;
        state.materials = false;
        state.confirmDelete = null;
        state.lastPreview = new ListTag();
        state.preview.setBodies(null, List.of());
        state.preview.setSnapshotBlocks(List.of());
    }

    private static CompoundTag selected(ListTag rows, UUID id){
        if(id != null) for(Tag raw : rows){
            CompoundTag row = (CompoundTag)raw;
            if(row.hasUUID("Id") && row.getUUID("Id").equals(id)) return row;
        }
        return new CompoundTag();
    }

    private static String name(TabletAppClientContext ctx, CompoundTag row){
        String name = "schematics".equals(ctx.tab().id()) ? SubLevelSchematicFiles.displayName(row.getString("Name")) : row.getString("Name");
        return name.isBlank() && row.hasUUID("Id") ? row.getUUID("Id").toString().substring(0, 8) : name;
    }

    private static String searchKey(TabletAppClientContext ctx){ return "search_" + ctx.tab().id(); }

    private static TabletLayout.Bounds bounds(TabletAppClientContext ctx){
        return CANVAS.stretch(ctx.surface().left(), ctx.surface().top(), ctx.surface().width(), ctx.surface().height());
    }

    private static TabletAppClientContext local(TabletAppClientContext ctx, TabletLayout.Bounds bounds){
        return new TabletAppClientContext(ctx.app(), ctx.tab(), ctx.data(), ctx.graphics(), ctx.font(), 0, 0, 320, 180,
                new TabletAppClientSurface(0, 0, 320, 180), (int)((ctx.mouseX() - bounds.left()) / bounds.scaleX()),
                (int)((ctx.mouseY() - bounds.top()) / bounds.scaleY()), ctx.actions(), ctx.refresh(), ctx.ui());
    }

    private static GuiTextureRegion region(int u, int v, int width, int height){ return new GuiTextureRegion(TEXTURE, 320, 300, u, v, width, height); }

    private static void text(TabletAppClientContext ctx, String value, int x, int y, int width, int height, float scale, int color, boolean centered){
        GuiControlPreviews.drawFittedText(ctx.graphics(), ctx.font(), Component.literal(value), x, y, width, height, scale, color, centered);
    }

    private static void wrap(TabletAppClientContext ctx, String value, int x, int y, int width, int height, float scale, int color){
        ctx.graphics().pose().pushPose();
        try{
            ctx.graphics().pose().translate(x, y, 1);
            ctx.graphics().pose().scale(scale, scale, 1);
            int line = 0;
            for(var text : ctx.font().split(Component.literal(value), (int)(width / scale))){
                if(line + ctx.font().lineHeight > height / scale) break;
                ctx.graphics().drawString(ctx.font(), text, 0, line, color, false);
                line += ctx.font().lineHeight;
            }
        }finally{ ctx.graphics().pose().popPose(); }
    }

    private static void field(TabletAppClientContext ctx, String label, String value, int y){
        text(ctx, label, 236, y - 1, 75, 10, .7F, MUTED, false);
        FIELD.draw(ctx.graphics(), 235, y + 9, 77, 11);
        text(ctx, value, 238, y + 10, 70, 9, .9F, TEXT, false);
    }

    private static void button(TabletAppClientContext ctx, State state, int y, String label, boolean enabled, Runnable action, boolean primary){
        state.panel.paintedButton(ctx, primary && enabled ? PRIMARY_BUTTON : BUTTON, 235, y, 77, 11, label, enabled, action);
    }

    private static void select(TabletAppClientContext ctx, State state, UUID id){
        state.selected = id; state.confirmDelete = null; state.materials = false;
        state.lastPreview = new ListTag(); state.preview.setBodies(null, List.of()); state.preview.setSnapshotBlocks(List.of());
        boolean schematics = "schematics".equals(ctx.tab().id());
        var rows = schematics ? schematicRows(ctx) : ctx.data().getList("archives".equals(ctx.tab().id()) ? "Archives" : "Sublevels", Tag.TAG_COMPOUND);
        for(int idx = 0; idx < rows.size(); idx++){
            var row = rows.getCompound(idx);
            if(row.getUUID("Id").equals(id) && (schematics || "archives".equals(ctx.tab().id()) || row.getBoolean("Loaded"))) ctx.actions().send(schematics ? "schematic_preview" : "preview", id.toString());
        }
    }

    // Combine shared server entries with private files discovered by this client
    private static ListTag schematicRows(TabletAppClientContext ctx){
        ListTag rows = ctx.data().getList("Schematics", Tag.TAG_COMPOUND).copy();
        if(!ctx.data().getBoolean("SchematicsAllowed") || ctx.data().getBoolean("SharedSchematicsLocal")) return rows;
        for(var file : ClientSchematicFiles.catalogue(ctx.data().getInt("SchematicMaximumBodies"), ctx.data().getInt("SchematicMaximumBlocks"))){
            CompoundTag row = new CompoundTag();
            row.putUUID("Id", file.id()); row.putString("Name", file.name()); row.putBoolean("Client", true);
            row.putString("Format", file.format().name());
            row.putString("Source", file.source().toString());
            rows.add(row);
        }
        return rows;
    }

    // Apply search to every tab and provenance filters to schematics
    private static ListTag visibleRows(TabletAppClientContext ctx, State state){
        String query = state.panel.value(searchKey(ctx)).strip().toLowerCase(Locale.ROOT);
        Set<String> formats = state.filters.selectedIds();
        if(!query.equals(state.query) || !formats.equals(state.formats)){
            state.query = query; state.formats = formats; state.list.scrollToFraction(0);
        }
        boolean schematics = "schematics".equals(ctx.tab().id());
        ListTag rows = schematics ? schematicRows(ctx) : ctx.data().getList("archives".equals(ctx.tab().id()) ? "Archives" : "Sublevels", Tag.TAG_COMPOUND);
        ListTag res = new ListTag();
        for(Tag raw : rows){
            CompoundTag row = (CompoundTag)raw;
            if(!row.hasUUID("Id") || !name(ctx, row).toLowerCase(Locale.ROOT).contains(query)) continue;
            if(schematics){
                String source = row.getString("Source");
                String format = PaidTabletApps.DIGISABLE.id().toString().equals(source) ? "NATIVE"
                        : SchematicProvenance.PHOTOMANCY.toString().equals(source) ? "PHOTOMANCY"
                        : SchematicProvenance.TOOLGUN.toString().equals(source) ? "TOOLGUN" : "UNKNOWN";
                if(!formats.contains(format)) continue;
            }
            res.add(row);
        }
        return res;
    }

    // Enable building only when the server has validated the file selected on this screen
    private static boolean ready(TabletAppClientContext ctx, State state){
        return validated(ctx, state) && ctx.data().getBoolean("BuildReady");
    }

    // Keep material actions tied to the file whose upload and server conversion have finished
    private static boolean validated(TabletAppClientContext ctx, State state){
        return state.selected != null && ctx.data().hasUUID("SchematicSelected")
                && ctx.data().getUUID("SchematicSelected").equals(state.selected);
    }

    private static void updatePreview(TabletAppClientContext ctx, State state){
        if(!ctx.data().hasUUID("PreviewRoot") || !ctx.data().hasUUID("Selected") || !ctx.data().getUUID("Selected").equals(state.selected)) return;
        ListTag rows = ctx.data().getList("Preview", Tag.TAG_COMPOUND);
        if(rows.equals(state.lastPreview)) return;
        state.lastPreview = rows.copy();
        var level = Minecraft.getInstance().level;
        if(level == null) return;
        List<SubLevelPreviewRenderer.SnapshotBlock> blocks = SubLevelSnapshotBlocks.decode(rows, level.registryAccess());
        List<UUID> bodies = new ArrayList<>();
        for(var block : blocks){
            UUID body = block.subLevelId();
            if(!bodies.contains(body)) bodies.add(body);
        }
        state.preview.setBodies(ctx.data().getUUID("PreviewRoot"), bodies);
        state.preview.setSnapshotBlocks(blocks);
        state.preview.setFullBright(true);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        INPUT
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override
    public boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn){
        State state = (State)raw;
        TabletLayout.Bounds bounds = bounds(ctx);
        double localX = (x - bounds.left()) / bounds.scaleX();
        double localY = (y - bounds.top()) / bounds.scaleY();
        if("schematics".equals(ctx.tab().id()) && !state.materials && state.filters.mouseClicked(localX, localY, btn)) return true;
        if(state.panel.click(localX, localY, btn)) return true;
        if(btn != 0 && !(btn == 1 && ctx.ui().projected())) return false;
        ListTag rows = prepare(ctx, state);
        if(LIST.contains(localX, localY)){
            if(state.list.maximumOffset() > 0 && state.scrollbar.mouseClicked(localX, localY, 0)){
                state.list.scrollToFraction(state.scrollbar.fraction());
                return true;
            }
            int idx = (int)((localY - LIST.top() + state.list.offset()) / ROW_HEIGHT);
            if(idx >= 0 && idx < rows.size() && localY < LIST.top() + state.list.rowOffset(idx) + 11){
                select(ctx, state, rows.getCompound(idx).getUUID("Id"));
                return true;
            }
        }
        if(!state.materials && state.selected != null && CANVAS.project(bounds, VIEW).contains(x, y)){
            state.dragging = true; state.preview.mousePressed(x, y, 0); return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn, double dx, double dy){
        State state = (State)raw;
        TabletLayout.Bounds bounds = bounds(ctx);
        if(state.scrollbar.mouseDragged((y - bounds.top()) / bounds.scaleY(), ctx.ui().projected() && btn == 1 ? 0 : btn)){
            state.list.scrollToFraction(state.scrollbar.fraction()); return true;
        }
        if(!state.dragging) return false;
        state.preview.mouseDragged(dx / bounds.scaleX(), dy / bounds.scaleY()); return true;
    }

    @Override
    public boolean mouseReleased(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn){
        State state = (State)raw;
        boolean scrolling = state.scrollbar.mouseReleased();
        if(!state.dragging) return scrolling;
        state.dragging = false;
        state.preview.mouseReleased(x, y, 0, state.view.left(), state.view.top(), state.view.width(), state.view.height(), 1, List.of());
        return true;
    }

    @Override
    public boolean mouseScrolled(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, double sx, double sy){
        State state = (State)raw;
        TabletLayout.Bounds bounds = bounds(ctx);
        if(CANVAS.project(bounds, LIST).contains(x, y)){
            state.list.scrollBy((int)Math.round(-sy * ROW_HEIGHT)); return true;
        }
        if(CANVAS.project(bounds, VIEW).contains(x, y)){
            if(state.materials) state.materialList.scrollBy((int)Math.round(-sy * 18));
            else state.preview.mouseScrolled(sy);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(TabletAppClientContext ctx, TabletAppClientState raw, int key, int scan, int modifiers){
        State state = (State)raw;
        if("schematics".equals(ctx.tab().id()) && state.filters.keyPressed(key)) return true;
        return state.panel.keyPressed(key, modifiers);
    }

    @Override
    public boolean chatTyped(TabletAppClientContext ctx, TabletAppClientState raw, char ch, int modifiers){
        return ((State)raw).panel.charTyped(ch);
    }
}
