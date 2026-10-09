package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelPreviewRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelSnapshotBlocks;
import com.rieno.gadgetsandgizmos.lib.client.schematic.ClientSchematicFiles;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppPanel;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Browse sublevels and archived assemblies in a dedicated interactive 3D view
public final class Digisable implements TabletAppClientRenderer{
    private static final int ROWS = 6;

    private static final class State implements TabletAppClientState, AutoCloseable{
        private final TabletAppPanel panel = new TabletAppPanel();
        private final SubLevelPreviewRenderer preview = new SubLevelPreviewRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "digisable_preview"), 2048, null);
        private UUID selected;
        private String tab = "";
        private UUID confirmDelete;
        private long confirmUntil;
        private int scroll;
        private boolean dragging;
        // Keep the material list scroll separate from the assembly browser
        private boolean materials;
        private int materialScroll;
        private ListTag lastPreview = new ListTag();
        private TabletLayout.Rect view = new TabletLayout.Rect(0, 0, 1, 1);
        @Override public void close(){ preview.close(); }
    }

    @Override public TabletAppClientState createScreenState(){ return new State(); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override
    public void render(TabletAppClientContext ctx, TabletAppClientState raw){
        State state = (State) raw;
        boolean archives = "archives".equals(ctx.tab().id());
        boolean schematics = "schematics".equals(ctx.tab().id());
        ListTag rows = schematics ? schematicRows(ctx) : ctx.data().getList(archives ? "Archives" : "Sublevels", Tag.TAG_COMPOUND);
        if(!state.tab.equals(ctx.tab().id())){
            state.tab = ctx.tab().id(); state.selected = null; state.scroll = 0; state.confirmDelete = null;
            state.materials = false;
        }
        boolean present = false;
        for(int idx = 0; idx < rows.size(); idx++) if(rows.getCompound(idx).getUUID("Id").equals(state.selected)) present = true;
        if(state.selected != null && !present){ state.selected = null; state.lastPreview = new ListTag(); state.preview.setBodies(null, List.of()); }
        state.panel.begin(ctx);
        if(schematics && state.materials){ renderMaterials(ctx, state); return; }
        int listWidth = Math.max(120, ctx.width() / 3);
        int viewX = listWidth + 7;
        int viewWidth = ctx.width() - viewX - 4;
        int viewHeight = Math.max(60, ctx.height() - 54);
        state.view = new TabletLayout.Rect(ctx.left() + viewX, ctx.top(), viewWidth, viewHeight);
        state.scroll = Mth.clamp(state.scroll, 0, Math.max(0, rows.size() - ROWS));
        if(state.selected == null && !rows.isEmpty()){
            // Keep the validated selection when reopening placement or returning from a cancelled preview
            if(schematics && ctx.data().hasUUID("SchematicSelected")){
                UUID selected = ctx.data().getUUID("SchematicSelected");
                for(int idx = 0; idx < rows.size(); idx++)
                    if(rows.getCompound(idx).getUUID("Id").equals(selected)) state.selected = selected;
            }
            if(state.selected == null) select(ctx, state, rows.getCompound(0).getUUID("Id"));
        }
        for(int idx = 0; idx < ROWS && state.scroll + idx < rows.size(); idx++){
            CompoundTag row = rows.getCompound(state.scroll + idx);
            UUID id = row.getUUID("Id");
            int y = idx * 20;
            String name = row.getString("Name");
            if(name.isBlank()) name = id.toString().substring(0, 8);
            if(schematics) name = (row.getBoolean("Client") ? "Client: " : "Server: ") + name;
            if(!archives && !schematics && !row.getBoolean("Loaded")) name += " (unloaded)";
            state.panel.button(ctx, 3, y, listWidth - 6, 18, (id.equals(state.selected) ? "> " : "") + name, true, () -> select(ctx, state, id));
        }
        if(rows.isEmpty()) state.panel.wrap(ctx, schematics ? ClientSchematicFiles.status().isBlank()
                ? "No .nbt or .excraft files. Client: game/schematics. Shared: " + ctx.data().getString("SharedSchematicFolder")
                : ClientSchematicFiles.status()
                : archives ? "No stored assemblies" : ctx.data().getBoolean("LocateAllowed") ? "No known accessible sublevels within range" : "Locating is disabled by the server", 5, 25, listWidth - 10, 64);
        state.panel.text(ctx, rows.size() + (schematics ? " schematics" : archives ? " stored / " + ctx.data().getInt("ArchiveLimit") : " located"), 4, ctx.height() - 48, listWidth - 8, 0xFF9DB4CC);
        updatePreview(ctx, state);
        if(!state.preview.render(ctx.graphics(), state.view.left(), state.view.top(), state.view.width(), state.view.height(), 1, List.of())){
            state.panel.wrap(ctx, "Select an assembly to view its blocks in 3D", viewX + 16, 28, viewWidth - 32, 46);
        }
        state.panel.text(ctx, "Drag to rotate / scroll to zoom", viewX + 4, ctx.height() - 48, viewWidth - 8, 0xFF9DB4CC);
        CompoundTag selected = new CompoundTag();
        for(int idx = 0; idx < rows.size(); idx++) if(rows.getCompound(idx).getUUID("Id").equals(state.selected)) selected = rows.getCompound(idx);
        if(!selected.isEmpty() && !archives && !schematics){
            String point = "%d, %d, %d  (%dm)".formatted((int) selected.getDouble("X"), (int) selected.getDouble("Y"), (int) selected.getDouble("Z"), selected.getInt("Distance")) + (selected.getBoolean("Loaded") ? "" : " last known");
            state.panel.text(ctx, point, viewX + 4, ctx.height() - 36, viewWidth - 8, 0xFFE2ECF5);
        }
        if(schematics) state.panel.text(ctx, !ClientSchematicFiles.status().isBlank() ? ClientSchematicFiles.status() : ctx.data().getBoolean("Building")
                ? "Building " + ctx.data().getInt("BuildPlaced") + "/" + ctx.data().getInt("BuildTotal")
                : ctx.data().getBoolean("Creative") ? "Creative: no items or network required"
                : ready(ctx, state) ? "Materials ready" : "Open Materials to view missing items",
                viewX + 4, ctx.height() - 36, viewWidth - 8, 0xFFE2ECF5);
        boolean picked = state.selected != null && !selected.isEmpty();
        int y = ctx.height() - 23;
        int width = (ctx.width() - 16) / 4;
        state.panel.button(ctx, 3, y, width, 20, "Refresh", true, () -> {
            if(schematics) ClientSchematicFiles.refresh();
            ctx.refresh().run();
        });
        state.panel.button(ctx, width + 6, y, width, 20, schematics ? "Pair network" : "Store from world", schematics ? ctx.data().getBoolean("SchematicsAllowed") : ctx.data().getBoolean("StoreAllowed"),
                () -> ctx.actions().send("begin_reader", schematics ? "pair_network" : "store"));
        state.panel.button(ctx, width * 2 + 9, y, width, 20, schematics ? "Build" : archives ? "Extract" : "Teleport",
                picked && (schematics ? ready(ctx, state) : archives ? ctx.data().getBoolean("StoreAllowed") : ctx.data().getBoolean("TeleportAllowed")),
                () -> ctx.actions().send(schematics ? "prepare_build" : archives ? "prepare_extract" : "teleport", state.selected.toString()));
        if(schematics) state.panel.button(ctx, width * 3 + 12, y, width, 20, "Materials", validated(ctx, state) || ctx.data().getBoolean("Building"),
                () -> {
                    state.materials = true; state.materialScroll = 0;
                    if(state.selected != null) ctx.actions().send("materials", state.selected.toString());
                });
        if(archives) state.panel.button(ctx, width * 3 + 12, y, width, 20, "Save schematic", picked && ctx.data().getBoolean("SchematicsAllowed"),
                () -> ctx.ui().editText("Schematic filename", "assembly", 64,
                        name -> ctx.actions().send("export", state.selected + "|" + name)));
        boolean confirm = state.selected != null && state.selected.equals(state.confirmDelete) && System.currentTimeMillis() < state.confirmUntil;
        if(!archives && !schematics) state.panel.button(ctx, width * 3 + 12, y, width, 20, confirm ? "Confirm delete" : "Delete", picked && selected.getBoolean("Loaded") && selected.getBoolean("Unclaimed") && ctx.data().getBoolean("DeleteAllowed"), () -> {
            if(state.selected.equals(state.confirmDelete) && System.currentTimeMillis() < state.confirmUntil){ ctx.actions().send("delete", state.selected.toString()); state.confirmDelete = null; }
            else{ state.confirmDelete = state.selected; state.confirmUntil = System.currentTimeMillis() + 5000; }
        });
        state.panel.error(ctx);
    }

    // Show all remaining items, exact available counts and current construction progress
    private static void renderMaterials(TabletAppClientContext ctx, State state){
        state.panel.text(ctx, "Schematic materials", 5, 4, ctx.width() - 10, 0xFFE2ECF5);
        String help = ctx.data().getBoolean("Creative") ? "Creative: no network or items required"
                : ctx.data().getBoolean("NetworkBound") ? "Paired Worker storage: " + ctx.data().getInt("NetworkStorage") + " endpoints"
                : ctx.data().getString("NetworkHelp");
        state.panel.wrap(ctx, help, 5, 19, ctx.width() - 10, 27);
        ListTag rows = ctx.data().getList("Materials", Tag.TAG_COMPOUND);
        int visible = Math.max(1, (ctx.height() - 95) / 21);
        state.materialScroll = Mth.clamp(state.materialScroll, 0, Math.max(0, rows.size() - visible));
        for(int idx = 0; idx < visible && idx + state.materialScroll < rows.size(); idx++){
            CompoundTag row = rows.getCompound(idx + state.materialScroll);
            int y = 49 + idx * 21;
            if(Minecraft.getInstance().level != null){
                ItemStack stack = ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), row.getCompound("Stack"));
                ctx.graphics().renderItem(stack, ctx.left() + 5, ctx.top() + y - 3);
            }
            String counts = "Need " + row.getLong("Missing") + "   " + row.getLong("Available") + "/" + row.getInt("Required")
                    + (row.getBoolean("Tool") ? " tool" : "");
            int countWidth = Math.min(ctx.width() / 2, ctx.font().width(counts) + 8);
            state.panel.text(ctx, row.getString("Name"), 26, y, ctx.width() - countWidth - 31, 0xFFE2ECF5);
            state.panel.text(ctx, counts, ctx.width() - countWidth, y, countWidth - 5,
                    row.getLong("Missing") > 0 ? 0xFFFFAAAA : 0xFF91D89B);
        }
        if(ctx.data().contains("MaterialError")) state.panel.wrap(ctx, ctx.data().getString("MaterialError"), 5, 49, ctx.width() - 10, 45);
        if(ctx.data().getBoolean("Building")){
            int total = ctx.data().getInt("BuildTotal");
            state.panel.progress(ctx, 5, ctx.height() - 44, ctx.width() - 10, 5,
                    total == 0 ? 0 : (double) ctx.data().getInt("BuildPlaced") / total);
        }else state.panel.text(ctx, ctx.data().getString("BuildStatus"), 5, ctx.height() - 44, ctx.width() - 10, 0xFF91D89B);
        int width = (ctx.width() - 16) / 4;
        int y = ctx.height() - 23;
        state.panel.button(ctx, 3, y, width, 20, "Back", true, () -> state.materials = false);
        state.panel.button(ctx, width + 6, y, width, 20, "Refresh", state.selected != null,
                () -> ctx.actions().send("materials", state.selected.toString()));
        state.panel.button(ctx, width * 2 + 9, y, width, 20, "Pair network", true,
                () -> ctx.actions().send("begin_reader", "pair_network"));
        state.panel.button(ctx, width * 3 + 12, y, width, 20, ctx.data().getBoolean("Building") ? "Cancel build" : "Build",
                ctx.data().getBoolean("Building") || ready(ctx, state),
                () -> ctx.actions().send(ctx.data().getBoolean("Building") ? "cancel_build" : "prepare_build",
                        ctx.data().getBoolean("Building") ? "" : state.selected.toString()));
        state.panel.error(ctx);
    }

    private static void select(TabletAppClientContext ctx, State state, UUID id){
        state.selected = id; state.confirmDelete = null;
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
        if(!ctx.data().getBoolean("SchematicsAllowed")) return rows;
        for(var file : ClientSchematicFiles.catalogue()){
            CompoundTag row = new CompoundTag();
            row.putUUID("Id", file.id()); row.putString("Name", file.name()); row.putBoolean("Client", true);
            rows.add(row);
        }
        return rows;
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

    @Override public boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int button){
        State state = (State) raw;
        if(state.panel.click(x, y, button)) return true;
        if(state.materials) return false;
        if(state.view.contains(x, y)){
            state.dragging = true; state.preview.mousePressed(x, y, button); return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int button, double dx, double dy){
        State state = (State) raw;
        if(!state.dragging) return false;
        state.preview.mouseDragged(dx, dy); return true;
    }

    @Override public boolean mouseReleased(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int button){
        State state = (State) raw;
        if(!state.dragging) return false;
        state.dragging = false;
        state.preview.mouseReleased(x, y, button, state.view.left(), state.view.top(), state.view.width(), state.view.height(), 1, List.of());
        return true;
    }

    @Override public boolean mouseScrolled(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, double sx, double sy){
        State state = (State) raw;
        if(state.materials) state.materialScroll = Math.max(0, state.materialScroll - (int) Math.signum(sy));
        else if(state.view.contains(x, y)) state.preview.mouseScrolled(sy);
        else state.scroll = Math.max(0, state.scroll - (int) Math.signum(sy));
        return true;
    }
}
