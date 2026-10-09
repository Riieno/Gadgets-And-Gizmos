package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppPanel;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewControlPanel;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewFeedGrid;
import com.rieno.gadgetsandgizmos.lib.view.ControlledViewSource;
import com.rieno.gadgetsandgizmos.lib.view.ViewControlState;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

// Present camera bindings and keep individual controls available beside the live grid
public final class Cctv implements TabletAppClientRenderer{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final class State implements TabletAppClientState, AutoCloseable{
        private final TabletAppPanel panel = new TabletAppPanel();
        private final ViewControlPanel controls = new ViewControlPanel();
        private final ViewFeedGrid feeds = new ViewFeedGrid();
        private boolean grid;
        private int page;
        private int scroll;
        private String selected = "";
        private boolean controllable;
        @Override public CompoundTag saveDraft(){
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Grid", grid);
            tag.putString("Selected", selected);
            tag.putInt("Page", page);
            tag.putInt("Scroll", scroll);
            return tag;
        }
        @Override public void loadDraft(CompoundTag tag){
            grid = tag.getBoolean("Grid");
            selected = tag.getString("Selected");
            page = Math.max(0, tag.getInt("Page"));
            scroll = Math.max(0, tag.getInt("Scroll"));
        }
        @Override public void close(){ controls.mouseReleased(); }
    }
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override public TabletAppClientState createScreenState(){ return new State(); }
    @Override public boolean ownsAppSurface(){ return true; }

    // Draw the server-authorized list while feeding only visible views to the capture budget
    @Override public void render(TabletAppClientContext content, TabletAppClientState raw){
        TabletAppClientContext ctx = content.forSurface();
        State state = (State) raw;
        state.panel.begin(ctx);
        List<CompoundTag> cameras = new ArrayList<>();
        for(Tag tag : ctx.data().getList("Cameras", Tag.TAG_COMPOUND)) cameras.add((CompoundTag) tag);
        if(cameras.stream().noneMatch(tag -> tag.getString("Key").equals(state.selected))){
            state.controls.mouseReleased();
            state.selected = ctx.data().getString("SelectedCamera");
        }
        int listWidth = Math.min(126, Math.max(85, ctx.width() / 4));
        int viewLeft = listWidth + 6;
        int viewWidth = Math.max(1, ctx.width() - viewLeft - 4);
        int viewTop = 32;
        int viewHeight = Math.max(1, ctx.height() - viewTop - 4);
        state.panel.button(ctx, 4, 5, listWidth - 8, 20, "Bind camera", true,
                () -> ctx.actions().send("begin_reader", "pair"));
        state.panel.button(ctx, viewLeft, 5, 62, 20, state.grid ? "Single" : "Grid 2x2", !cameras.isEmpty(),
                () -> { state.grid = !state.grid; state.controls.mouseReleased(); });
        int pages = Math.max(1, (cameras.size() + 3) / 4);
        state.page = Math.clamp(state.page, 0, pages - 1);
        state.panel.button(ctx, viewLeft + 65, 5, 23, 20, "<", state.grid && state.page > 0,
                () -> select(ctx, state, cameras.get((state.page - 1) * 4).getString("Key"), state.page - 1));
        state.panel.button(ctx, viewLeft + 91, 5, 23, 20, ">", state.grid && state.page + 1 < pages,
                () -> select(ctx, state, cameras.get((state.page + 1) * 4).getString("Key"), state.page + 1));
        state.panel.button(ctx, viewLeft + 120, 5, 62, 20, "Rename", !state.selected.isEmpty(),
                () -> {
                    CompoundTag selected = cameras.stream().filter(tag -> tag.getString("Key").equals(state.selected)).findFirst().orElse(new CompoundTag());
                    String key = state.selected;
                    ctx.ui().editText("Camera name", selected.getString("Name"), 64,
                            val -> ctx.actions().send("rename", key + "|" + val));
                });
        state.panel.button(ctx, viewLeft + 185, 5, 54, 20, "Forget", !state.selected.isEmpty(),
                () -> ctx.actions().send("forget", state.selected));
        int visibleRows = Math.max(1, (ctx.height() - 36) / 26);
        state.scroll = Math.clamp(state.scroll, 0, Math.max(0, cameras.size() - visibleRows));
        for(int idx = state.scroll; idx < Math.min(cameras.size(), state.scroll + visibleRows); idx++){
            CompoundTag tag = cameras.get(idx);
            int cameraIdx = idx;
            state.panel.button(ctx, 4, 34 + (idx - state.scroll) * 26, listWidth - 8, 23,
                    (tag.getString("Key").equals(state.selected) ? "> " : "") + tag.getString("Name"), true,
                    () -> select(ctx, state, tag.getString("Key"), cameraIdx / 4));
        }
        List<ViewFeedGrid.Entry> visible = new ArrayList<>();
        CompoundTag selected = null;
        for(int idx = 0; idx < cameras.size(); idx++){
            CompoundTag tag = cameras.get(idx);
            String key = tag.getString("Key");
            if(key.equals(state.selected)) selected = tag;
            if(state.grid ? idx / 4 != state.page : !key.equals(state.selected)) continue;
            ViewReference ref = ViewReference.fromTag(tag);
            if(ref != null) visible.add(new ViewFeedGrid.Entry(key, tag.getString("Name"), ref,
                    tag.getBoolean("Available") && ref.resolve(Minecraft.getInstance().level) != null));
        }
        state.feeds.render(ctx.graphics(), ctx.font(), ctx.left() + viewLeft, ctx.top() + viewTop,
                viewWidth, Math.max(1, viewHeight - 76), visible, state.selected);
        state.controllable = selected != null && selected.getBoolean("Available");
        if(state.controllable){
            ViewReference ref = ViewReference.fromTag(selected);
            var src = ref == null ? null : ref.resolve(Minecraft.getInstance().level);
            ViewControlState controls = src instanceof ControlledViewSource camera ? camera.viewControlState()
                    : ViewControlState.fromTag(selected.getCompound("Controls"));
            String key = state.selected;
            state.controls.render(ctx.graphics(), ctx.font(), ctx.left() + viewLeft, ctx.top() + viewTop,
                    viewWidth, viewHeight, controls, input -> ctx.actions().send("control", key + "|" + input.toTag()));
        }else{
            state.controls.mouseReleased();
            if(cameras.isEmpty()) state.panel.wrap(ctx, ctx.data().getString("Help"), viewLeft + 8, 45, viewWidth - 16, 60);
        }
        state.panel.error(ctx);
    }
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static void select(TabletAppClientContext ctx, State state, String key, int page){
        state.controls.mouseReleased();
        state.selected = key;
        state.page = page;
        ctx.actions().send("select_camera", key);
    }
    @Override public boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int button){
        State state = (State) raw;
        if(state.controllable && state.controls.mouseClicked(x, y, button)) return true;
        if(state.panel.click(x, y, button)) return true;
        String key = state.feeds.selection(x, y);
        if(key.isEmpty()) return false;
        select(ctx, state, key, state.page);
        return true;
    }
    @Override public boolean mouseDragged(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int button, double dx, double dy){
        return ((State) raw).controls.mouseDragged(x, y);
    }
    @Override public boolean mouseReleased(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int button){
        return ((State) raw).controls.mouseReleased();
    }
    @Override public boolean mouseScrolled(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, double dx, double dy){
        State state = (State) raw;
        state.scroll = Math.max(0, state.scroll - (int) Math.signum(dy));
        return true;
    }
}
