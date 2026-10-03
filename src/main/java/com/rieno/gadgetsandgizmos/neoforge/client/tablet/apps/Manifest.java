package com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps;

import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppPanel;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletResourceGauge;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

// Show exact cargo components and configure filters, transfers and desired stock
public final class Manifest implements TabletAppClientRenderer{
    private static final class State implements TabletAppClientState{
        private final TabletAppPanel panel = new TabletAppPanel();
        private int scroll;
        private String selected = "";
        private boolean craft;
    }

    @Override public TabletAppClientState createScreenState(){ return new State(); }

    @Override public void render(TabletAppClientContext ctx, TabletAppClientState raw){
        State state = (State) raw;
        state.panel.begin(ctx);
        if(!ctx.data().getBoolean("Attached")){
            state.panel.wrap(ctx, ctx.data().getString("Help"), 7, 25, ctx.width() - 14, 65);
            state.panel.button(ctx, 7, 96, 170, 22, "Inspect looked-at container", true,
                    () -> ctx.actions().send("inspect", ""));
            if(ctx.data().getBoolean("Bound")) stop(ctx, state);
            state.panel.error(ctx);
            return;
        }
        boolean configure = ctx.data().getBoolean("CanConfigure");
        if("stock".equals(ctx.tab().id())){ stock(ctx, state, configure); stop(ctx, state); state.panel.error(ctx); return; }
        if("resources".equals(ctx.tab().id())){ resources(ctx, state); stop(ctx, state); state.panel.error(ctx); return; }
        int width = (ctx.width() - 20) / 5;
        String[] labels = {ctx.data().getBoolean("Locked") ? "Unlock" : "Lock", ctx.data().getBoolean("Push") ? "Push: On" : "Push: Off", ctx.data().getBoolean("Pull") ? "Pull: On" : "Pull: Off", "Set filter", "Clear filter"};
        String[] actions = {"lock", "push", "pull", "filter", "filter"};
        for(int idx = 0; idx < labels.length; idx++){
            String action = actions[idx];
            String val = idx == 4 ? "clear" : "";
            state.panel.button(ctx, 4 + idx * (width + 3), 1, width, 21, labels[idx], configure, () -> ctx.actions().send(action, val));
        }
        var rows = ctx.data().getList("Items", Tag.TAG_COMPOUND);
        state.scroll = Mth.clamp(state.scroll, 0, Math.max(0, rows.size() - 4));
        int listWidth = ctx.width() * 2 / 3;
        for(int idx = 0; idx < 4 && state.scroll + idx < rows.size(); idx++){
            var row = rows.getCompound(state.scroll + idx);
            int y = 29 + idx * 25;
            String key = row.getString("Id") + row.getString("Components");
            ItemStack item = item(row);
            ctx.graphics().renderItem(item, ctx.left() + 5, ctx.top() + y);
            state.panel.button(ctx, 25, y, listWidth - 29, 22, row.getString("Name") + " x " + row.getLong("Count"), true, () -> state.selected = key);
        }
        CompoundTag selected = new CompoundTag();
        for(int idx = 0; idx < rows.size(); idx++){
            var row = rows.getCompound(idx);
            if((row.getString("Id") + row.getString("Components")).equals(state.selected)) selected = row;
        }
        if(selected.isEmpty() && !rows.isEmpty()) selected = rows.getCompound(0);
        int detailX = listWidth + 3;
        int detailWidth = ctx.width() - detailX - 6;
        if(!selected.isEmpty()){
            state.panel.wrap(ctx, selected.getString("Id"), detailX, 30, detailWidth, 28);
            state.panel.text(ctx, "Slots: " + selected.getInt("Slots") + " / stack " + selected.getInt("MaxStack"), detailX, 60, detailWidth, 0xFFEAD19A);
            state.panel.wrap(ctx, selected.getString("Components").equals("{}") ? "Standard item components" : selected.getString("Components"), detailX, 77, detailWidth, 48);
        }else state.panel.wrap(ctx, "No item cargo", 8, 38, listWidth - 16, 35);
        state.panel.text(ctx, ctx.data().getLong("ItemCount") + " items / " + ctx.data().getInt("Occupied") + "/" + ctx.data().getInt("Slots") + " slots / capacity " + ctx.data().getLong("Capacity"), 6, ctx.height() - 39, ctx.width() - 12, 0xFFB8CBE0);
        if(ctx.data().getInt("OmittedSlots") > 0) state.panel.text(ctx, ctx.data().getInt("OmittedSlots") + " occupied slots omitted by snapshot limits", detailX, ctx.height() - 51, detailWidth, 0xFFFFB884);
        var liquids = ctx.data().getList("Fluids", Tag.TAG_COMPOUND);
        String fluids = liquids.isEmpty() ? "" : liquids.getCompound(0).getString("Name") + " " + liquids.getCompound(0).getInt("Amount") + "/" + liquids.getCompound(0).getInt("Capacity") + " mB";
        state.panel.text(ctx, fluids + (ctx.data().contains("Energy") ? " / " + ctx.data().getLong("Energy") + "/" + ctx.data().getLong("EnergyCapacity") + " FE" : ""), 6, ctx.height() - 19, ctx.width() - 125, 0xFFB8CBE0);
        stop(ctx, state);
        state.panel.error(ctx);
    }

    private static void stop(TabletAppClientContext ctx, State state){
        state.panel.button(ctx, ctx.width() - 119, ctx.height() - 22, 115, 20,
                "Stop inspecting", true, () -> ctx.actions().send("detach", ""));
    }

    private static void stock(TabletAppClientContext ctx, State state, boolean configure){
        state.panel.input(ctx, "item", 6, 4, ctx.width() - 100, "minecraft:iron_ingot");
        state.panel.input(ctx, "amount", ctx.width() - 88, 4, 82, "Stock amount");
        state.panel.button(ctx, 6, 31, 120, 22, state.craft ? "Craft shortages" : "Fetch shortages", configure, () -> state.craft = !state.craft);
        state.panel.button(ctx, 132, 31, 142, 22, "Set stock target", configure, () -> {
            String item = state.panel.value("item").isBlank() ? "minecraft:iron_ingot" : state.panel.value("item");
            String amount = state.panel.value("amount").isBlank() ? "64" : state.panel.value("amount");
            ctx.actions().send("stock", item + "|" + amount + "|" + (state.craft ? "craft" : "transfer"));
        });
        state.panel.text(ctx, "Adjacent inventories transfer; nearby workers can craft shortages", 6, 62, ctx.width() - 12, 0xFFB8CBE0);
        var rows = ctx.data().getList("Stock", Tag.TAG_COMPOUND);
        state.scroll = Mth.clamp(state.scroll, 0, Math.max(0, rows.size() - 3));
        for(int idx = 0; idx < 3 && state.scroll + idx < rows.size(); idx++){
            var row = rows.getCompound(state.scroll + idx);
            ItemStack item = item(row);
            int y = 79 + idx * 21;
            ctx.graphics().renderItem(item, ctx.left() + 7, ctx.top() + y);
            state.panel.text(ctx, item.getHoverName().getString() + " / keep " + row.getInt("Amount") + " / " + row.getString("Task"), 28, y + 5, ctx.width() - 106, 0xFFEAF1F8);
            state.panel.button(ctx, ctx.width() - 72, y, 65, 19, "Remove", configure,
                    () -> ctx.actions().send("stock", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()) + "|0"));
        }
    }

    private static ItemStack item(CompoundTag row){
        var level = Minecraft.getInstance().level;
        return level == null ? ItemStack.EMPTY : ItemStack.parseOptional(level.registryAccess(), row.getCompound("Item"));
    }

    private static void resources(TabletAppClientContext ctx, State state){
        var rows = ctx.data().getList("Fluids", Tag.TAG_COMPOUND);
        boolean hasEnergy = ctx.data().contains("Energy");
        int visible = hasEnergy ? 3 : 4;
        state.scroll = Mth.clamp(state.scroll, 0, Math.max(0, rows.size() - visible));
        state.panel.text(ctx, rows.size() + " fluid tanks" + (rows.size() > visible ? " / scroll for more" : ""), 6, 4, ctx.width() - 12, 0xFFEAD19A);
        for(int idx = 0; idx < visible && state.scroll + idx < rows.size(); idx++){
            var row = rows.getCompound(state.scroll + idx);
            int y = 22 + idx * 29;
            int amount = row.getInt("Amount");
            int capacity = row.getInt("Capacity");
            int percent = capacity <= 0 ? 0 : (int) Math.min(100, Math.round(amount * 100.0D / capacity));
            state.panel.text(ctx, "Tank " + (state.scroll + idx + 1) + ": " + row.getString("Name") + "  " + amount + "/" + capacity + " mB (" + percent + "%)", 7, y, ctx.width() - 14, 0xFFEAF1F8);
            ResourceLocation id = ResourceLocation.tryParse(row.getString("Id"));
            var fluid = id == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.getOptional(id).orElse(Fluids.EMPTY);
            FluidStack stack = fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, Math.max(1, amount));
            TabletResourceGauge.fluid(ctx.graphics(), stack, amount, capacity,
                    ctx.left() + 7, ctx.top() + y + 11, ctx.width() - 14, 11);
        }
        if(hasEnergy){
            long energy = ctx.data().getLong("Energy");
            long capacity = ctx.data().getLong("EnergyCapacity");
            int percent = capacity <= 0 ? 0 : (int) Math.min(100, Math.round(energy * 100.0D / capacity));
            state.panel.text(ctx, "FE: " + energy + "/" + capacity + " (" + percent + "%)", 7, 111, ctx.width() - 14, 0xFFFFADB0);
            TabletResourceGauge.energy(ctx.graphics(), energy, capacity,
                    ctx.left() + 7, ctx.top() + 123, ctx.width() - 14, 13);
        }
        if(rows.isEmpty() && !hasEnergy) state.panel.wrap(ctx, "This container holds item cargo only", 8, 35, ctx.width() - 16, 45);
    }

    @Override public boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int button){ return ((State) raw).panel.click(x, y, button); }
    @Override public boolean keyPressed(TabletAppClientContext ctx, TabletAppClientState raw, int key, int scan, int modifiers){ return ((State) raw).panel.keyPressed(key, modifiers); }
    @Override public boolean chatTyped(TabletAppClientContext ctx, TabletAppClientState raw, char ch, int modifiers){ return ((State) raw).panel.charTyped(ch); }
    @Override public boolean mouseScrolled(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, double sx, double sy){ ((State) raw).scroll = Math.max(0, ((State) raw).scroll - (int) Math.signum(sy)); return true; }
}
