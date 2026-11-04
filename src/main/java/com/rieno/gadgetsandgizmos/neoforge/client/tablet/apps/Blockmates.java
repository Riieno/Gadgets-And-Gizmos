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
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletInventoryView;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import com.rieno.gadgetsandgizmos.lib.client.ui.LayeredItemRenderer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Configure owned workers and submit item requests without entering the controller graph editor
public final class Blockmates implements TabletAppClientRenderer{
    private static final class State implements TabletAppClientState{
        private final TabletAppPanel panel = new TabletAppPanel();
        private final TabletInventoryView inventory = new TabletInventoryView();
        private int scroll;
        private int missingScroll;
        private List<String> missing = List.of();
        private boolean showMissing = true;
        private UUID lastRequestResponse;
        private boolean craft;
        private boolean fluidRequest;
        private ResourceLocation fluid = ResourceLocation.withDefaultNamespace("water");
        private List<ResourceLocation> stockedFluids = List.of();
        private boolean fluidCatalogRequested;
        private int task;
        private UUID selectedWorker;
        private ResourceLocation item = ResourceLocation.withDefaultNamespace("iron_ingot");
        private final Set<UUID> expanded = new HashSet<>();

        private State(){
            panel.setValue("amount", "1");
            panel.setValue("inventory_amount", "1");
            panel.setValue("millibuckets", "1000");
        }

        @Override public CompoundTag saveDraft(){
            CompoundTag draft = new CompoundTag();
            draft.putString("Item", item.toString());
            draft.putString("Amount", panel.value("amount"));
            draft.putBoolean("Craft", craft);
            if(fluidRequest){
                draft.putBoolean("FluidRequest", true);
                draft.putString("Fluid", fluid.toString());
                draft.putString("Millibuckets", panel.value("millibuckets"));
            }
            draft.putInt("Task", task);
            if(selectedWorker != null) draft.putUUID("SelectedWorker", selectedWorker);
            draft.put("Inventory", inventory.save());
            draft.putString("InventoryAmount", panel.value("inventory_amount"));
            if(scroll > 0) draft.putInt("WorkerScroll", scroll);
            if(missingScroll > 0) draft.putInt("MissingScroll", missingScroll);
            if(!showMissing) draft.putBoolean("ShowMissing", false);
            if(!missing.isEmpty()){
                ListTag missingRows = new ListTag();
                missing.forEach(row -> missingRows.add(net.minecraft.nbt.StringTag.valueOf(row)));
                draft.put("Missing", missingRows);
            }
            if(!expanded.isEmpty()){
                ListTag opened = new ListTag();
                for(UUID id : expanded){
                    CompoundTag entry = new CompoundTag();
                    entry.putUUID("Id", id);
                    opened.add(entry);
                }
                draft.put("ExpandedWorkers", opened);
            }
            return draft;
        }

        @Override public void loadDraft(CompoundTag draft){
            ResourceLocation selected = ResourceLocation.tryParse(draft.getString("Item"));
            if(selected != null) item = selected;
            panel.setValue("amount", positiveAmount(draft.getString("Amount")) ? draft.getString("Amount") : "1");
            craft = draft.getBoolean("Craft");
            fluidRequest = draft.getBoolean("FluidRequest");
            ResourceLocation selectedFluid = ResourceLocation.tryParse(draft.getString("Fluid"));
            if(selectedFluid != null) fluid = selectedFluid;
            panel.setValue("millibuckets", positiveAmount(draft.getString("Millibuckets"))
                    ? draft.getString("Millibuckets") : "1000");
            task = Math.max(0, draft.getInt("Task"));
            selectedWorker = draft.hasUUID("SelectedWorker") ? draft.getUUID("SelectedWorker") : null;
            inventory.load(draft.getCompound("Inventory"));
            panel.setValue("inventory_amount", positiveAmount(draft.getString("InventoryAmount"))
                    ? draft.getString("InventoryAmount") : "1");
            scroll = Math.max(0, draft.getInt("WorkerScroll"));
            missingScroll = Math.max(0, draft.getInt("MissingScroll"));
            showMissing = !draft.contains("ShowMissing") || draft.getBoolean("ShowMissing");
            ListTag missingRows = draft.getList("Missing", Tag.TAG_STRING);
            missing = new ArrayList<>();
            for(int idx = 0; idx < missingRows.size(); idx++) missing.add(missingRows.getString(idx));
            expanded.clear();
            ListTag opened = draft.getList("ExpandedWorkers", Tag.TAG_COMPOUND);
            for(int idx = 0; idx < opened.size(); idx++){
                CompoundTag entry = opened.getCompound(idx);
                if(entry.hasUUID("Id")) expanded.add(entry.getUUID("Id"));
            }
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override public TabletAppClientState createScreenState(){ return new State(); }

    @Override public void refresh(TabletAppClientContext ctx, TabletAppClientState raw){
        State state = (State) raw;
        if("request".equals(ctx.tab().id()) && state.fluidRequest && !state.fluidCatalogRequested){
            state.fluidCatalogRequested = true;
            ctx.actions().send("fluids", "");
        }else ctx.actions().send("refresh", "inventory".equals(ctx.tab().id())
                ? Integer.toString(state.inventory.offset()) : "");
    }

    @Override public void render(TabletAppClientContext ctx, TabletAppClientState raw){
        State state = (State) raw;
        state.panel.begin(ctx);
        if(!ctx.data().getBoolean("Linked")){
            state.panel.wrap(ctx, ctx.data().getString("Help"), 8, 25, ctx.width() - 16, 60);
            state.panel.error(ctx);
            return;
        }
        if("inventory".equals(ctx.tab().id())){
            inventory(ctx, state);
            state.panel.error(ctx);
            return;
        }
        if("workers".equals(ctx.tab().id())){
            workers(ctx, state);
            state.panel.error(ctx);
            return;
        }
        if(state.fluidRequest && ctx.data().contains("Fluids", Tag.TAG_LIST)){
            ListTag rows = ctx.data().getList("Fluids", Tag.TAG_STRING);
            List<ResourceLocation> nextFluids = new ArrayList<>();
            for(int idx = 0; idx < rows.size(); idx++){
                ResourceLocation candidate = ResourceLocation.tryParse(rows.getString(idx));
                if(candidate != null) nextFluids.add(candidate);
            }
            state.stockedFluids = List.copyOf(nextFluids);
            state.fluidCatalogRequested = true;
        }
        if(state.fluidRequest && !state.stockedFluids.isEmpty()
                && !state.stockedFluids.contains(state.fluid)) state.fluid = state.stockedFluids.getFirst();
        state.panel.text(ctx, "Deliver to your inventory / limit " + ctx.data().getInt("RequestLimit"), 5, 4, ctx.width() - 10, 0xFFCDE7C5);
        ItemStack selected = new ItemStack(BuiltInRegistries.ITEM.get(state.item));
        if(state.fluidRequest){
            var fluid = BuiltInRegistries.FLUID.get(state.fluid);
            String fluidName = fluid.getFluidType().getDescription().getString();
            state.panel.button(ctx, 6, 23, ctx.width() - 100, 22, "     " + fluidName,
                    !state.stockedFluids.isEmpty(), () -> {
                int index = state.stockedFluids.indexOf(state.fluid);
                state.fluid = state.stockedFluids.get((index + 1) % state.stockedFluids.size());
            });
            ItemStack filledBucket = FluidUtil.getFilledBucket(new FluidStack(fluid, 1000));
            if(!filledBucket.isEmpty()) LayeredItemRenderer.renderVisible(ctx.graphics(), filledBucket,
                    ctx.left() + 9, ctx.top() + 26);
        }else{
            state.panel.button(ctx, 6, 23, ctx.width() - 100, 22,
                    "     " + selected.getHoverName().getString(), true,
                    () -> ctx.ui().pickItem(state.item, item -> state.item = item));
            LayeredItemRenderer.renderVisible(ctx.graphics(), selected, ctx.left() + 9, ctx.top() + 26);
        }
        state.panel.input(ctx, "amount", ctx.width() - 88, 23, 82, "Amount");
        state.panel.button(ctx, 6, 51, 124, 22, state.fluidRequest ? "Mode: Fluid"
                : state.craft ? "Mode: Craft" : "Mode: Deliver", true, () -> {
            if(state.fluidRequest){ state.fluidRequest = false; state.craft = false; }
            else if(state.craft){
                state.fluidRequest = true;
                state.item = ResourceLocation.withDefaultNamespace("bucket");
                state.fluidCatalogRequested = true;
                ctx.actions().send("fluids", "");
            }
            else state.craft = true;
        });
        var tasks = ctx.data().getList("Tasks", Tag.TAG_STRING);
        state.task = Mth.clamp(state.task, 0, tasks.size());
        String task = state.task == 0 ? "Automatic task" : tasks.getString(state.task - 1);
        if(state.fluidRequest){
            state.panel.button(ctx, 137, 51, ctx.width() - 232, 22,
                    "     " + selected.getHoverName().getString(), true,
                    () -> ctx.ui().pickFluidContainer(state.item, state.fluid,
                            validAmount(state.panel.value("millibuckets"), 1_000_000)
                                    ? Integer.parseInt(state.panel.value("millibuckets")) : 1000,
                            item -> state.item = item));
            LayeredItemRenderer.renderVisible(ctx.graphics(), selected, ctx.left() + 140, ctx.top() + 54);
            state.panel.input(ctx, "millibuckets", ctx.width() - 88, 51, 82, "mB each");
        }else state.panel.button(ctx, 137, 51, ctx.width() - 143, 22, task, true,
                () -> state.task = (state.task + 1) % (tasks.size() + 1));
        List<CompoundTag> available = availableItemWorkers(ctx, state.fluidRequest);
        if(state.selectedWorker != null && available.stream()
                .noneMatch(row -> state.selectedWorker.equals(row.getUUID("Worker")))){
            state.selectedWorker = null;
        }
        String workerLabel = "Worker: Automatic";
        if(state.selectedWorker != null){
            for(CompoundTag row : available){
                if(!state.selectedWorker.equals(row.getUUID("Worker"))) continue;
                workerLabel = "Worker: " + row.getString("Name");
                break;
            }
        }
        if(ctx.data().hasUUID("RequestResponseId")
                && !ctx.data().getUUID("RequestResponseId").equals(state.lastRequestResponse)){
            state.lastRequestResponse = ctx.data().getUUID("RequestResponseId");
            ListTag details = ctx.data().getList("Missing", Tag.TAG_STRING);
            List<String> rows = new ArrayList<>();
            for(int idx = 0; idx < details.size(); idx++) rows.add(details.getString(idx));
            state.missing = ctx.data().getBoolean("RequestFailed") ? rows : List.of();
            state.missingScroll = 0;
            state.showMissing = !state.missing.isEmpty();
        }
        if(!state.missing.isEmpty() && state.showMissing){
            renderMissing(ctx, state);
        }else{
            state.panel.button(ctx, 6, 79, ctx.width() - 12, 22, workerLabel, true,
                    () -> state.selectedWorker = nextItemWorker(available, state.selectedWorker));
            if(state.missing.isEmpty()) state.panel.wrap(ctx, state.fluidRequest
                    ? "Choose an empty fluid container above, stocked fluid, mB per container, and container count. Workers craft missing containers, fill, and deliver them."
                    : state.craft ? "Workers use stocked crafting ingredients and executable recipe chains from the ACC's linked endpoints."
                    : "Workers collect the requested items from the ACC's linked storage and deliver them to you.",
                    7, 108, ctx.width() - 14, 34);
            else state.panel.button(ctx, 6, 108, ctx.width() - 12, 31,
                    "Show missing machines/ingredients", true, () -> state.showMissing = true);
        }
        state.panel.button(ctx, 6, ctx.height() - 29, ctx.width() - 12, 24,
                state.fluidRequest ? "Request filled containers" : "Request items",
                validAmount(state.panel.value("amount"), ctx.data().getInt("RequestLimit"))
                        && (!state.fluidRequest || !state.stockedFluids.isEmpty()
                        && validAmount(state.panel.value("millibuckets"), 1_000_000)), () -> {
            if(state.fluidRequest){
                String payload = state.fluid + "|" + state.item + "|" + state.panel.value("amount")
                        + "|" + state.panel.value("millibuckets");
                state.missing = List.of();
                state.missingScroll = 0;
                state.showMissing = true;
                if(state.selectedWorker == null) ctx.actions().send("request_fluid", payload);
                else ctx.actions().send("request_fluid_selected", state.selectedWorker + "|" + payload);
                return;
            }
            String item = state.item.toString();
            String amount = state.panel.value("amount");
            String payload = item + "|" + amount + "|" + (state.craft ? "craft" : "transfer")
                    + "|" + (state.task == 0 ? "" : tasks.getString(state.task - 1));
            state.missing = List.of();
            state.missingScroll = 0;
            state.showMissing = true;
            if(state.selectedWorker == null) ctx.actions().send("request", payload);
            else ctx.actions().send("request_selected", state.selectedWorker + "|" + payload);
        });
        state.panel.error(ctx);
    }

    // Keep the failure list inside the Request tab's 174-pixel content area.
    private static void renderMissing(TabletAppClientContext ctx, State state){
        int top = 79;
        int bottom = ctx.height() - 33;
        int listTop = top + 19;
        ctx.graphics().fill(ctx.left() + 5, ctx.top() + top, ctx.left() + ctx.width() - 5,
                ctx.top() + bottom, 0xFF332B32);
        state.panel.text(ctx, "Missing machines/ingredients", 10, top + 5,
                ctx.width() - 110, 0xFFFFC8A8);
        state.panel.button(ctx, ctx.width() - 70, top + 1, 64, 17, "Edit", true,
                () -> state.showMissing = false);
        state.missingScroll = Mth.clamp(state.missingScroll, 0,
                Math.max(0, state.missing.size() * 26 - (bottom - listTop)));
        ctx.graphics().enableScissor(ctx.left() + 7, ctx.top() + listTop,
                ctx.left() + ctx.width() - 44, ctx.top() + bottom);
        for(int idx = 0; idx < state.missing.size(); idx++){
            int y = listTop + idx * 26 - state.missingScroll;
            if(y + 26 > listTop && y < bottom)
                state.panel.wrap(ctx, state.missing.get(idx), 12, y, ctx.width() - 61, 24);
        }
        ctx.graphics().disableScissor();
        state.panel.button(ctx, ctx.width() - 41, listTop, 34, 19, "Up", state.missingScroll > 0,
                () -> state.missingScroll = Math.max(0, state.missingScroll - 26));
        state.panel.button(ctx, ctx.width() - 41, bottom - 21, 34, 19, "Dn",
                state.missingScroll < Math.max(0, state.missing.size() * 26 - (bottom - listTop)),
                () -> state.missingScroll += 26);
    }

    // Show enabled item workers from the controller's latest server snapshot
    private static List<CompoundTag> availableItemWorkers(TabletAppClientContext ctx, boolean fluidRequest){
        ListTag rows = ctx.data().getList("Workers", Tag.TAG_COMPOUND);
        List<CompoundTag> available = new ArrayList<>();
        for(int idx = 0; idx < rows.size(); idx++){
            CompoundTag row = rows.getCompound(idx);
            String job = row.getString("Job");
            if(row.hasUUID("Worker") && row.getBoolean("Enabled")
                    && ("any".equals(job) || !fluidRequest && "items".equals(job))) available.add(row);
        }
        return available;
    }

    // Cycle automatic assignment through every selectable worker
    private static UUID nextItemWorker(List<CompoundTag> available, UUID selected){
        if(available.isEmpty()) return null;
        if(selected == null) return available.getFirst().getUUID("Worker");
        for(int idx = 0; idx < available.size(); idx++){
            if(!selected.equals(available.get(idx).getUUID("Worker"))) continue;
            return idx + 1 < available.size() ? available.get(idx + 1).getUUID("Worker") : null;
        }
        return null;
    }

    // Draw a scrollable worker list with independent task and whole-job progress
    private static void workers(TabletAppClientContext ctx, State state){
        ListTag rows = ctx.data().getList("Workers", Tag.TAG_COMPOUND);
        int contentHeight = 0;
        for(int idx = 0; idx < rows.size(); idx++){
            CompoundTag row = rows.getCompound(idx);
            contentHeight += 36;
            if(state.expanded.contains(row.getUUID("Worker")) && orderCount(row) > 0){
                contentHeight += 32 + orderCount(row) * 29;
            }
        }
        int top = 21;
        int bottom = ctx.height() - 4;
        state.scroll = Mth.clamp(state.scroll, 0, Math.max(0, contentHeight - (bottom - top)));
        state.panel.text(ctx, rows.size() + " managed workers", 5, 4, ctx.width() - 10, 0xFFCDE7C5);
        ctx.graphics().enableScissor(ctx.left(), ctx.top() + top, ctx.left() + ctx.width(), ctx.top() + bottom);
        int y = top - state.scroll;
        for(int idx = 0; idx < rows.size(); idx++){
            CompoundTag row = rows.getCompound(idx);
            UUID id = row.getUUID("Worker");
            boolean open = state.expanded.contains(id);
            CompoundTag current = row.getCompound("Current");
            boolean busy = current.hasUUID("Id");
            int count = row.getInt("CompletedTotal") + row.getInt("PlannedTotal")
                    + (busy ? 1 : 0);
            double total = count > 0 ? totalProgress(row) : 0.0D;
            if(y + 31 >= top && y < bottom){
                String label = (open ? "- " : "+ ") + row.getString("Name") + " / " + row.getString("Job");
                state.panel.button(ctx, 5, y, ctx.width() - 151, 19, label, true, () -> {
                    if(!state.expanded.remove(id)) state.expanded.add(id);
                });
                String activity = row.getString("Status");
                if(busy && activity.isBlank()) activity = "Working on "
                        + current.getCompound("Task").getString("Name");
                state.panel.text(ctx, busy ? activity + " / " + Math.round(total * 100.0D) + "%"
                                : activity.isBlank() || "Idle".equalsIgnoreCase(activity) ? "idle" : activity,
                        10, y + 21, ctx.width() - 155, 0xFFB8CBE0);
                state.panel.button(ctx, ctx.width() - 140, y, 66, 23, row.getBoolean("Enabled") ? "Pause" : "Resume", true,
                        () -> ctx.actions().send("worker_enable", id + "|" + !row.getBoolean("Enabled")));
                state.panel.button(ctx, ctx.width() - 70, y, 66, 23, "Cancel", busy,
                        () -> ctx.actions().send("cancel", current.getUUID("Id").toString()));
            }
            y += 36;
            if(!open || orderCount(row) == 0) continue;
            if(y + 30 >= top && y < bottom){
                state.panel.text(ctx, "Total progress", 10, y + 1, ctx.width() - 110, 0xFFCDE7C5);
                state.panel.text(ctx, Math.round(total * 100.0D) + "%", ctx.width() - 58, y + 1, 48, 0xFFEAF3FC);
                state.panel.progress(ctx, 10, y + 13, ctx.width() - 20, 8, total);
            }
            y += 32;
            ListTag completed = row.getList("Completed", Tag.TAG_COMPOUND);
            for(int task = 0; task < completed.size(); task++){
                if(y + 29 >= top && y < bottom) drawOrder(ctx, state, completed.getCompound(task), y, true, false, false);
                y += 29;
            }
            if(busy){
                if(y + 29 >= top && y < bottom) drawOrder(ctx, state, current, y, false, true, false);
                y += 29;
            }
            ListTag planned = row.getList("Planned", Tag.TAG_COMPOUND);
            ListTag waiting = row.getList("Waiting", Tag.TAG_COMPOUND);
            for(int task = 0; task < planned.size(); task++){
                CompoundTag order = planned.getCompound(task);
                boolean processing = false;
                for(int wait = 0; wait < waiting.size(); wait++){
                    if(waiting.getCompound(wait).getUUID("Id").equals(order.getUUID("Id"))) processing = true;
                }
                if(y + 29 >= top && y < bottom) drawOrder(ctx, state, order, y, false, false, processing);
                y += 29;
            }
        }
        ctx.graphics().disableScissor();
        if(rows.isEmpty()) state.panel.wrap(ctx, "Link Worker Pods to this ACC and assign workers before requesting items", 8, 37, ctx.width() - 16, 64);
    }

    private static int orderCount(CompoundTag row){
        return row.getList("Completed", Tag.TAG_COMPOUND).size() + row.getList("Planned", Tag.TAG_COMPOUND).size()
                + (row.getCompound("Current").hasUUID("Id") ? 1 : 0);
    }

    private static double totalProgress(CompoundTag row){
        double done = 0.0D;
        double total = 0.0D;
        ListTag completed = row.getList("Completed", Tag.TAG_COMPOUND);
        for(int idx = 0; idx < completed.size(); idx++){
            double amount = Math.max(1L, completed.getCompound(idx).getCompound("Task").getLong("Requested"));
            done += amount;
        }
        done = Math.max(done, row.getLong("CompletedWeight"));
        total = done;
        CompoundTag current = row.getCompound("Current").getCompound("Task");
        if(row.getCompound("Current").hasUUID("Id")){
            double amount = Math.max(1L, current.getLong("Requested"));
            total += amount;
            done += Math.min(amount, current.getLong("Completed"));
        }
        ListTag planned = row.getList("Planned", Tag.TAG_COMPOUND);
        for(int idx = 0; idx < planned.size(); idx++) total += Math.max(1L, planned.getCompound(idx).getCompound("Task").getLong("Requested"));
        return total <= 0.0D ? 0.0D : done / total;
    }

    private static void drawOrder(TabletAppClientContext ctx, State state, CompoundTag order, int y,
                                  boolean completed, boolean current, boolean waiting){
        CompoundTag task = order.getCompound("Task");
        CompoundTag resource = order.getCompound("Output");
        ResourceLocation output = ResourceLocation.tryParse(resource.getString("Id"));
        String name = output == null ? task.getString("Name")
                : "item".equals(resource.getString("Type"))
                ? BuiltInRegistries.ITEM.get(output).getDescription().getString() : output.toString();
        String action = "return_to_station".equals(order.getString("Mode")) ? "Return to Pod: "
                : order.contains("RecipePlan", Tag.TAG_COMPOUND)
                ? "processing".equals(order.getCompound("RecipePlan").getString("Operation")) ? "Process " : "Craft "
                : "Deliver ";
        String stateName = completed ? "done" : waiting ? "processing" : current ? "active" : "queued";
        state.panel.text(ctx, completed ? "\u2713" : current ? ">" : "-", 12, y + 2, 13,
                completed ? 0xFF9ADD90 : 0xFFB8CBE0);
        state.panel.text(ctx, action + name, 27, y + 2, ctx.width() - 125, 0xFFEAF3FC);
        state.panel.text(ctx, stateName, ctx.width() - 87, y + 2, 78, 0xFFB8CBE0);
        long requested = Math.max(1L, task.getLong("Requested"));
        long done = completed ? requested : Math.min(requested, task.getLong("Completed"));
        state.panel.progress(ctx, 27, y + 15, ctx.width() - 112, 6, (double) done / requested);
        state.panel.text(ctx, done + "/" + requested, ctx.width() - 81, y + 14, 73, 0xFFB8CBE0);
    }

    // Request the selected stocked item using the same server-validated delivery path
    private static void inventory(TabletAppClientContext ctx, State state){
        if(ctx.data().contains("Inventory")) state.inventory.render(ctx, state.panel, ctx.data().getCompound("Inventory"), ctx.height() - 63,
                offset -> ctx.actions().send("refresh", Integer.toString(offset)));
        else state.panel.text(ctx, "Loading inventory...", 7, 30, ctx.width() - 14, 0xFFB8CBE0);
        ResourceLocation selected = state.inventory.selected();
        String name = selected == null ? "Select an item to request" : BuiltInRegistries.ITEM.get(selected).getDescription().getString();
        state.panel.text(ctx, name + " / limit " + ctx.data().getInt("RequestLimit"), 6, ctx.height() - 54, ctx.width() - 12, 0xFFEAF3FC);
        state.panel.input(ctx, "inventory_amount", 6, ctx.height() - 27, 86, "Amount: 64");
        state.panel.button(ctx, 100, ctx.height() - 29, ctx.width() - 106, 24, "Request",
                selected != null && validAmount(state.panel.value("inventory_amount"),
                        ctx.data().getInt("RequestLimit")), () -> {
            String amount = state.panel.value("inventory_amount");
            ctx.actions().send("request", selected + "|" + amount + "|transfer|");
        });
    }

    private static boolean positiveAmount(String value){
        try{ return Long.parseLong(value) > 0L; }
        catch(NumberFormatException ex){ return false; }
    }

    private static boolean validAmount(String value, int limit){
        try{
            int amount = Integer.parseInt(value);
            return amount > 0 && amount <= limit;
        }
        catch(NumberFormatException ex){ return false; }
    }

    @Override public boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int button){
        if("workers".equals(ctx.tab().id()) && (y < ctx.top() + 21 || y >= ctx.top() + ctx.height() - 4)) return false;
        return ((State) raw).panel.click(x, y, button);
    }
    @Override public boolean keyPressed(TabletAppClientContext ctx, TabletAppClientState raw, int key, int scan, int modifiers){ return ((State) raw).panel.keyPressed(key, modifiers); }
    @Override public boolean chatTyped(TabletAppClientContext ctx, TabletAppClientState raw, char ch, int modifiers){ return ((State) raw).panel.charTyped(ch); }
    @Override public boolean mouseScrolled(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, double sx, double sy){
        State state = (State) raw;
        if("inventory".equals(ctx.tab().id())) state.inventory.move(ctx.data().getCompound("Inventory"), -(int) Math.signum(sy),
                offset -> ctx.actions().send("refresh", Integer.toString(offset)));
        else if("workers".equals(ctx.tab().id())) state.scroll = Math.max(0, state.scroll - (int) Math.signum(sy) * 26);
        else if("request".equals(ctx.tab().id()) && state.showMissing && !state.missing.isEmpty()
                && y >= ctx.top() + 98 && y < ctx.top() + ctx.height() - 33)
            state.missingScroll = Math.max(0, state.missingScroll - (int) Math.signum(sy) * 26);
        return true;
    }
}
