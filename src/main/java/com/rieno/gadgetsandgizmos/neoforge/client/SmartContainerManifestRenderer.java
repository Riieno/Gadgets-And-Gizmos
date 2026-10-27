package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.tablet.*;
import com.rieno.gadgetsandgizmos.lib.inventory.TransferRateUnit;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

// Show the embedded manifest using the same inventory browser as Blockmates
public final class SmartContainerManifestRenderer implements TabletAppClientRenderer{
    private static final class State implements TabletAppClientState{
        final TabletAppPanel panel = new TabletAppPanel();
        final TabletInventoryView network = new TabletInventoryView();
        final TabletSelectDropdown rateUnits = new TabletSelectDropdown(java.util.Arrays.stream(TransferRateUnit.values())
                .map(unit -> Component.translatable("createthrusters.smart_container_manifest.rate_unit." + unit.suffix())).toList());
        int scroll;
        int fluidScroll;
        int connectorScroll;
        int inputScroll;
        int outputScroll;
        boolean craft;
        String sentSearch = "";
    }
    @Override public TabletAppClientState createScreenState(){ return new State(); }

    @Override public void render(TabletAppClientContext ctx, TabletAppClientState raw){
        State state = (State)raw;
        var panel = state.panel;
        panel.begin(ctx);
        if(!"transfers".equals(ctx.tab().id())) state.rateUnits.close();
        if("transfers".equals(ctx.tab().id())){
            renderTransfers(ctx, panel, state);
        }else if("storage".equals(ctx.tab().id())){
            renderStorage(ctx, panel, state);
        }else if("network".equals(ctx.tab().id())){
            state.network.render(ctx, panel, ctx.data().getCompound("Inventory"), ctx.height() - 12,
                    offset -> ctx.actions().send("refresh", Integer.toString(offset)));
        }else if("stock".equals(ctx.tab().id())){
            boolean allowed = ctx.data().getBoolean("CanConfigure");
            panel.button(ctx, 6, 4, ctx.width() / 2 - 10, 21, "Choose item", allowed,
                    () -> ctx.ui().pickItem(null, id -> panel.setValue("item", id == null ? "" : id.toString())));
            panel.text(ctx, panel.value("item"), ctx.width() / 2, 10, ctx.width() / 2 - 6, 0xFFEAD19A);
            panel.input(ctx, "amount", 6, 31, 105, "Stock amount");
            panel.button(ctx, 118, 31, Math.max(60, ctx.width() - 225), 21, state.craft ? "Craft" : "Transfer", allowed,
                    () -> state.craft = !state.craft);
            panel.button(ctx, ctx.width() - 101, 31, 95, 21, "Set stock", allowed, () -> ctx.actions().send("stock",
                    panel.value("item") + "|" + panel.value("amount") + "|" + (state.craft ? "craft" : "transfer")));
            var stock = ctx.data().getList("Stock", Tag.TAG_COMPOUND);
            for(int idx = 0; idx < Math.min(5, stock.size()); idx++){
                var row = stock.getCompound(idx);
                ItemStack item = ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), row.getCompound("Item"));
                panel.text(ctx, item.getHoverName().getString() + " x " + row.getInt("Amount")
                        + " (" + row.getString("Task") + ")", 7, 66 + idx * 19, ctx.width() - 14, 0xFFDDEAF4);
            }
        }else if("resources".equals(ctx.tab().id())){
            if("tank".equals(ctx.data().getString("ContainerKind"))){
                renderTankResources(ctx, panel, state);
                panel.error(ctx);
                return;
            }
            var fluids = ctx.data().getList("Fluids", Tag.TAG_COMPOUND);
            for(int idx = 0; idx < Math.min(4, fluids.size()); idx++){
                var row = fluids.getCompound(idx);
                panel.text(ctx, row.getString("Name") + ": " + row.getInt("Amount") + " / " + row.getInt("Capacity") + " mB",
                        7, 7 + idx * 29, ctx.width() - 14, 0xFFB8D8EF);
                var fluidId = net.minecraft.resources.ResourceLocation.tryParse(row.getString("Id"));
                var fluid = fluidId == null ? net.minecraft.world.level.material.Fluids.EMPTY
                        : net.minecraft.core.registries.BuiltInRegistries.FLUID.getOptional(fluidId).orElse(net.minecraft.world.level.material.Fluids.EMPTY);
                var stack = fluid == net.minecraft.world.level.material.Fluids.EMPTY ? net.neoforged.neoforge.fluids.FluidStack.EMPTY
                        : new net.neoforged.neoforge.fluids.FluidStack(fluid, Math.max(1, row.getInt("Amount")));
                TabletResourceGauge.fluid(ctx.graphics(), stack, row.getInt("Amount"), row.getInt("Capacity"),
                        ctx.left() + 7, ctx.top() + 20 + idx * 29, ctx.width() - 14, 10);
            }
            if(ctx.data().contains("Energy")){
                long energy = ctx.data().getLong("Energy"), capacity = ctx.data().getLong("EnergyCapacity");
                panel.text(ctx, energy + " / " + capacity + " FE", 7, 20, ctx.width() - 14, 0xFFFFADB0);
                TabletResourceGauge.energy(ctx.graphics(), energy, capacity, ctx.left() + 7, ctx.top() + 41, ctx.width() - 14, 16);
                if(ctx.data().getBoolean("PowerGrid")) renderPowerGridResources(ctx, panel, state);
            }
            if(fluids.isEmpty() && !ctx.data().contains("Energy")) panel.text(ctx, "This container stores items", 7, 20, ctx.width() - 14, 0xFFB8CBE0);
        }else{
            var items = ctx.data().getList("Items", Tag.TAG_COMPOUND);
            int visible = Math.max(1, (ctx.height() - 35) / 30);
            state.scroll = Mth.clamp(state.scroll, 0, Math.max(0, items.size() - visible));
            panel.text(ctx, ctx.data().getString("Name") + " / " + ctx.data().getLong("ItemCount") + " items",
                    7, 3, ctx.width() - 14, 0xFFEAD19A);
            for(int idx = 0; idx < visible && state.scroll + idx < items.size(); idx++){
                var row = items.getCompound(state.scroll + idx);
                ItemStack stack = ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), row.getCompound("Item"));
                int y = 24 + idx * 30;
                ctx.graphics().renderItem(stack, ctx.left() + 8, ctx.top() + y);
                panel.text(ctx, stack.getHoverName().getString() + " x " + row.getLong("Count"), 31, y + 3, ctx.width() - 40, 0xFFDDEAF4);
                if(ctx.mouseX() >= ctx.left() + 6 && ctx.mouseX() < ctx.left() + ctx.width() - 6
                        && ctx.mouseY() >= ctx.top() + y && ctx.mouseY() < ctx.top() + y + 28){
                    ctx.graphics().renderTooltip(ctx.font(), stack, ctx.mouseX(), ctx.mouseY());
                }
            }
            if(items.isEmpty()) panel.text(ctx, "No items stored. View Resources for fluids or FE.", 7, 32, ctx.width() - 14, 0xFFB8CBE0);
        }
        panel.error(ctx);
    }

    private void renderTransfers(TabletAppClientContext ctx, TabletAppPanel panel, State state){
        String kind = ctx.data().getString("ContainerKind");
        if("battery".equals(kind)){ renderBatteryTransfers(ctx, panel, state); return; }
        var unit = TransferRateUnit.values()[state.rateUnits.selected()];
        String resource = "tank".equals(kind) ? "mB" : label("items");
        panel.text(ctx, label("transfers"), 7, 9, ctx.width() - 175, 0xFFEAD19A);
        panel.text(ctx, label("transfer_in") + ": " + rate(ctx.data().getDouble("TransferIn"), unit, resource),
                7, 47, ctx.width() - 14, 0xFF8ABD7A);
        panel.text(ctx, label("transfer_out") + ": " + rate(ctx.data().getDouble("TransferOut"), unit, resource),
                7, 73, ctx.width() - 14, 0xFFFFC46A);
        panel.wrap(ctx, label("transfer_window"), 7, 105, ctx.width() - 14, 30);
        state.rateUnits.render(ctx, ctx.width() - 161, 4, 154);
    }

    private void renderBatteryTransfers(TabletAppClientContext ctx, TabletAppPanel panel, State state){
        panel.text(ctx, label("transfers"), 7, 9, ctx.width() - 14, 0xFFEAD19A);
        int half = ctx.width() / 2;
        panel.text(ctx, label("transfer_in"), 7, 35, half - 14, 0xFF8ABD7A);
        panel.text(ctx, rate(ctx.data().getDouble("TransferIn"), TransferRateUnit.TICK, "FE"), 7, 49, half - 14, 0xFF8ABD7A);
        panel.text(ctx, label("transfer_out"), half + 7, 35, half - 14, 0xFFFFC46A);
        panel.text(ctx, rate(ctx.data().getDouble("TransferOut"), TransferRateUnit.TICK, "FE"), half + 7, 49, half - 14, 0xFFFFC46A);
        var input = electricalTransfers(ctx, true);
        var output = electricalTransfers(ctx, false);
        int visible = Math.max(1, (ctx.height() - 101) / 30);
        state.inputScroll = Mth.clamp(state.inputScroll, 0, Math.max(0, input.size() - visible));
        state.outputScroll = Mth.clamp(state.outputScroll, 0, Math.max(0, output.size() - visible));
        renderElectricalTransfers(ctx, panel, input, state.inputScroll, visible, 7, half - 14, 0xFF8ABD7A);
        renderElectricalTransfers(ctx, panel, output, state.outputScroll, visible, half + 7, half - 14, 0xFFFFC46A);
        panel.wrap(ctx, label(input.isEmpty() && output.isEmpty() ? "transfer_window" : "powergrid_transfer_window"),
                7, ctx.height() - 27, ctx.width() - 14, 26);
    }

    private static java.util.List<net.minecraft.nbt.CompoundTag> electricalTransfers(TabletAppClientContext ctx, boolean input){
        if(!ctx.data().getBoolean("PowerGrid")) return java.util.List.of();
        return ctx.data().getList("PowerGridConnectors", Tag.TAG_COMPOUND).stream()
                .map(tag -> (net.minecraft.nbt.CompoundTag)tag)
                .filter(row -> input ? row.getDouble("TransferWatts") > 1.0E-9 : row.getDouble("TransferWatts") < -1.0E-9).toList();
    }

    private static void renderElectricalTransfers(TabletAppClientContext ctx, TabletAppPanel panel,
            java.util.List<net.minecraft.nbt.CompoundTag> rows, int scroll, int visible, int x, int width, int color){
        for(int idx = 0; idx < visible && scroll + idx < rows.size(); idx++){
            var row = rows.get(scroll + idx);
            int y = 73 + idx * 30;
            panel.text(ctx, connectorName(row), x, y, width, 0xFFDDEAF4);
            panel.text(ctx, String.format(java.util.Locale.ROOT, "%,.2f V | %,.2f A | %,.2f W",
                    row.getDouble("ActualVolts"), row.getDouble("ActualAmps"), Math.abs(row.getDouble("TransferWatts"))),
                    x, y + 12, width, color);
        }
    }

    private static String rate(double perTick, TransferRateUnit unit, String resource){
        return String.format(java.util.Locale.ROOT, "%,.2f %s/%s", unit.convert(perTick), resource, unit.suffix());
    }

    private void renderPowerGridResources(TabletAppClientContext ctx, TabletAppPanel panel, State state){
        panel.text(ctx, label("powergrid_energy"), 7, 68, ctx.width() - 14, 0xFFEAD19A);
        panel.text(ctx, String.format(java.util.Locale.ROOT, "%,.3f / %,.3f Wh",
                ctx.data().getDouble("PowerGridWattHours"), ctx.data().getDouble("PowerGridCapacityWh")),
                7, 83, ctx.width() - 14, 0xFFB8D8EF);
        var rows = ctx.data().getList("PowerGridConnectors", Tag.TAG_COMPOUND);
        panel.text(ctx, label("powergrid_output_limits"), 7, 101, ctx.width() - 14, 0xFFDDEAF4);
        int visible = Math.max(1, (ctx.height() - 132) / 21);
        state.connectorScroll = Mth.clamp(state.connectorScroll, 0, Math.max(0, rows.size() - visible));
        for(int idx = 0; idx < visible && state.connectorScroll + idx < rows.size(); idx++){
            var row = rows.getCompound(state.connectorScroll + idx);
            int y = 115 + idx * 21;
            panel.text(ctx, connectorName(row), 7, y + 6, ctx.width() - 257, 0xFFDDEAF4);
            boolean negative = row.getInt("Polarity") < 0;
            panel.button(ctx, ctx.width() - 247, y, 32, 20, negative ? "-" : "+", ctx.data().getBoolean("CanConfigure"),
                    () -> ctx.actions().send("powergrid_polarity", row.getLong("Member") + "|" + row.getString("Face") + "|" + (negative ? "1" : "-1")));
            panel.button(ctx, ctx.width() - 211, y, 99, 20, String.format(java.util.Locale.ROOT, "%,.2f W", row.getDouble("Watts")),
                    ctx.data().getBoolean("CanConfigure"), () -> ctx.ui().editText(label("powergrid_output_prompt"),
                            Double.toString(row.getDouble("Watts")), 32, val -> ctx.actions().send("powergrid_output",
                                    row.getLong("Member") + "|" + row.getString("Face") + "|" + val)));
            panel.button(ctx, ctx.width() - 108, y, 101, 20, String.format(java.util.Locale.ROOT, "%,.2f V", row.getDouble("Volts")),
                    ctx.data().getBoolean("CanConfigure"), () -> ctx.ui().editText(label("powergrid_voltage_prompt"),
                            Double.toString(row.getDouble("Volts")), 32, val -> ctx.actions().send("powergrid_voltage",
                                    row.getLong("Member") + "|" + row.getString("Face") + "|" + val)));
        }
        if(rows.isEmpty()) panel.wrap(ctx, label("powergrid_no_connectors"), 7, 118, ctx.width() - 14, 30);
        else panel.text(ctx, label("powergrid_output_hint"), 7, ctx.height() - 12, ctx.width() - 14, 0xFF8B9BAA);
    }

    private static String connectorName(net.minecraft.nbt.CompoundTag row){
        var pos = net.minecraft.core.BlockPos.of(row.getLong("Connector"));
        return Component.translatable("createthrusters.smart_container_manifest.face." + row.getString("Face")).getString()
                + " (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    // Both the partial and opened tablet use this app renderer and its shared slot coordinates
    private void renderStorage(TabletAppClientContext ctx, TabletAppPanel panel, State state){
        var layout = com.rieno.gadgetsandgizmos.lib.tablet.TabletInventoryLayout.split(ctx.width());
        var inventory = ctx.ui().inventory();
        var menu = inventory != null && inventory.menu() instanceof com.rieno.gadgetsandgizmos.content.SmartContainerMenu storage ? storage : null;
        if(inventory != null) inventory.renderBackgrounds(ctx.graphics());
        else{
            drawGrid(ctx, layout.inventory());
            drawGrid(ctx, layout.hotbar());
        }
        panel.text(ctx, net.minecraft.network.chat.Component.translatable("container.inventory").getString(),
                layout.inventory().left(), 19, 170, 0xFFDDEAF4);
        String kind = ctx.data().getString("ContainerKind");
        if("battery".equals(kind)){
            long energy = ctx.data().getLong("Energy"), capacity = ctx.data().getLong("EnergyCapacity");
            String percent = String.format(java.util.Locale.ROOT, "%.2f%%", capacity > 0 ? energy * 100.0 / capacity : 0);
            panel.text(ctx, percent + "  " + energy + " / " + capacity + " FE", 7, 4, 260, 0xFFFFC46A);
            TabletResourceGauge.energy(ctx.graphics(), energy, capacity, ctx.left() + 7, ctx.top() + 20, 250, 12);
            panel.text(ctx, label("discharge"), 7, 39, 250, 0xFFDDEAF4);
            panel.text(ctx, label("charge"), 7, 79, 250, 0xFFDDEAF4);
            if(inventory == null){
                drawGrid(ctx, layout.inputs());
                drawGrid(ctx, layout.outputs());
                var charging = ctx.data().getCompound("ChargingItems").getList("Items", Tag.TAG_COMPOUND);
                for(int idx = 0; idx < charging.size(); idx++){
                    var row = charging.getCompound(idx);
                    int slot = row.getInt("Slot");
                    if(slot < 0 || slot >= 18) continue;
                    drawItem(ctx, slot < 9 ? layout.inputs() : layout.outputs(), slot % 9,
                            ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), row));
                }
            }
        }else if("tank".equals(kind)){
            renderTankStorage(ctx, panel, state);
        }else{
            panel.input(ctx, "search", 7, 4, 162, label("search"));
            panel.text(ctx, ctx.data().getLong("ItemCount") + " items", 177, 10, 95, 0xFFEAD19A);
            if(inventory == null){
                drawGrid(ctx, layout.storage());
                var items = ctx.data().getList("Items", Tag.TAG_COMPOUND);
                for(int slot = 0; slot < Math.min(36, items.size()); slot++){
                    var row = items.getCompound(slot);
                    var stack = ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), row.getCompound("Item"));
                    if(!stack.isEmpty()) stack.setCount((int)Math.min(stack.getMaxStackSize(), row.getLong("Count")));
                    drawItem(ctx, layout.storage(), slot, stack);
                }
            }
            control(ctx, panel, 7, 114, 82, menu == null || menu.network() ? "network" : "local", 100);
            control(ctx, panel, 96, 114, 26, "previous", 101);
            control(ctx, panel, 128, 114, 26, "next", 102);
            if(menu != null) panel.text(ctx, (menu.row() + 1) + " / " + Math.max(1, menu.totalRows() - 3), 164, 120, 100, 0xFFDDEAF4);
        }
    }

    private void renderTankStorage(TabletAppClientContext ctx, TabletAppPanel panel, State state){
        var fluids = FluidGaugeLayout.contents(ctx.data().getList("Fluids", Tag.TAG_COMPOUND));
        long capacity = ctx.data().getLong("FluidCapacity");
        panel.text(ctx, ctx.data().getLong("FluidAmount") + " / " + capacity + " mB", 7, 7, 250, 0xFFB8D8EF);
        panel.text(ctx, label("pump") + ": " + fluidName(ctx.data().getString("PumpFluid")), 7, 26, 250, 0xFFDDEAF4);
        TabletResourceGauge.fluids(ctx.graphics(), fluids, capacity, ctx.left() + 7, ctx.top() + 49, 250, 32);
        var bounds = tankFluidList(ctx);
        int visible = Math.max(1, bounds.height() / 18);
        state.fluidScroll = Mth.clamp(state.fluidScroll, 0, Math.max(0, fluids.size() - visible));
        FluidGaugeLayout.FluidAmount hovered = null;
        for(var segment : FluidGaugeLayout.segments(fluids, capacity, 248)){
            if(new TabletLayout.Rect(ctx.left() + 8 + segment.offset(), ctx.top() + 50, segment.width(), 30)
                    .contains(ctx.mouseX(), ctx.mouseY())) hovered = segment.contents();
        }
        for(int idx = 0; idx < visible && state.fluidScroll + idx < fluids.size(); idx++){
            var entry = fluids.get(state.fluidScroll + idx);
            int y = 88 + idx * 18;
            TabletResourceGauge.fluid(ctx.graphics(), entry.fluid(), 1, 1, ctx.left() + 7, ctx.top() + y, 14, 14);
            String amount = entry.amount() + " mB";
            int valueX = 257 - ctx.font().width(amount);
            panel.text(ctx, entry.fluid().getHoverName().getString(), 25, y + 3, valueX - 30, 0xFFDDEAF4);
            panel.text(ctx, amount, valueX, y + 3, 257 - valueX, 0xFFB8D8EF);
            if(new TabletLayout.Rect(bounds.left(), ctx.top() + y, 250, 18).contains(ctx.mouseX(), ctx.mouseY())) hovered = entry;
        }
        if(fluids.size() > visible){
            int track = visible * 18, thumb = Math.max(6, track * visible / fluids.size());
            int y = bounds.top() + (track - thumb) * state.fluidScroll / (fluids.size() - visible);
            ctx.graphics().fill(ctx.left() + 261, bounds.top(), ctx.left() + 264, bounds.top() + track, 0xFF22394D);
            ctx.graphics().fill(ctx.left() + 261, y, ctx.left() + 264, y + thumb, 0xFF8ABD7A);
        }
        panel.wrap(ctx, label("bucket_hint"), 7, ctx.height() - 29, 260, 28);
        if(hovered != null){
            String percent = String.format(java.util.Locale.ROOT, "%.2f%%", capacity > 0 ? hovered.amount() * 100D / capacity : 0);
            ctx.graphics().renderTooltip(ctx.font(), net.minecraft.network.chat.Component.literal(
                    hovered.fluid().getHoverName().getString() + ": " + hovered.amount() + " mB (" + percent + ")"),
                    ctx.mouseX(), ctx.mouseY());
        }
    }

    private static TabletLayout.Rect tankFluidList(TabletAppClientContext ctx){
        return new TabletLayout.Rect(ctx.left() + 7, ctx.top() + 88, 260, Math.max(0, ctx.height() - 118));
    }

    private void renderTankResources(TabletAppClientContext ctx, TabletAppPanel panel, State state){
        boolean allowed = ctx.data().getBoolean("CanConfigure");
        var fluids = ctx.data().getList("FluidChoices", Tag.TAG_COMPOUND);
        int half = (ctx.width() - 21) / 2;
        panel.text(ctx, ctx.data().getInt("FluidAmount") + " / " + ctx.data().getInt("FluidCapacity") + " mB",
                7, 4, ctx.width() - 14, 0xFFB8D8EF);
        panel.button(ctx, 7, 20, half, 21, label("fuel") + ": " + fluidName(ctx.data().getString("FuelFluid")), allowed,
                () -> ctx.actions().send("fluid_selection", "fuel|"));
        panel.button(ctx, 14 + half, 20, half, 21, label("pump") + ": " + fluidName(ctx.data().getString("PumpFluid")), allowed,
                () -> ctx.actions().send("fluid_selection", "pump|"));
        int visible = Math.max(1, (ctx.height() - 67) / 32);
        state.scroll = Mth.clamp(state.scroll, 0, Math.max(0, fluids.size() - visible));
        for(int idx = 0; idx < visible && state.scroll + idx < fluids.size(); idx++){
            var row = fluids.getCompound(state.scroll + idx);
            String id = row.getString("Id");
            if(row.getLong("Amount") <= 0) continue;
            int y = 48 + idx * 32;
            int textWidth = Math.max(35, ctx.width() - 170);
            panel.text(ctx, row.getString("Name"), 7, y + 1, textWidth, 0xFFDDEAF4);
            panel.text(ctx, label("network") + ": " + row.getLong("Amount") + " mB", 7, y + 14, textWidth, 0xFFB8D8EF);
            panel.button(ctx, ctx.width() - 158, y + 2, 72, 22,
                    (id.equals(ctx.data().getString("FuelFluid")) ? "✓ " : "") + label("fuel"), allowed,
                    () -> ctx.actions().send("fluid_selection", "fuel|" + id));
            panel.button(ctx, ctx.width() - 79, y + 2, 72, 22,
                    (id.equals(ctx.data().getString("PumpFluid")) ? "✓ " : "") + label("pump"), allowed,
                    () -> ctx.actions().send("fluid_selection", "pump|" + id));
        }
        panel.text(ctx, label(fluids.isEmpty() ? "empty_fluids" : "fluid_selection_hint"),
                7, ctx.height() - 13, ctx.width() - 14, 0xFF8B9BAA);
    }

    private static String fluidName(String id){
        if(id.isBlank()) return label("automatic");
        var key = net.minecraft.resources.ResourceLocation.tryParse(id);
        var fluid = key == null ? net.minecraft.world.level.material.Fluids.EMPTY
                : net.minecraft.core.registries.BuiltInRegistries.FLUID.getOptional(key).orElse(net.minecraft.world.level.material.Fluids.EMPTY);
        return fluid == net.minecraft.world.level.material.Fluids.EMPTY ? id
                : new net.neoforged.neoforge.fluids.FluidStack(fluid, 1).getHoverName().getString();
    }

    private static String label(String id){
        return net.minecraft.network.chat.Component.translatable("createthrusters.smart_container_manifest." + id).getString();
    }

    private void control(TabletAppClientContext ctx, TabletAppPanel panel, int x, int y, int width, String label, int id){
        panel.button(ctx, x, y, width, 21, label(label), true,
                () -> ctx.actions().send("storage_control", Integer.toString(id)));
    }

    private void drawGrid(TabletAppClientContext ctx, com.rieno.gadgetsandgizmos.lib.tablet.TabletInventoryLayout.Grid grid){
        for(int slot = 0; slot < grid.size(); slot++) TabletMenuInventory.background(ctx.graphics(), ctx.left() + grid.x(slot), ctx.top() + grid.y(slot));
    }

    private void drawItem(TabletAppClientContext ctx, com.rieno.gadgetsandgizmos.lib.tablet.TabletInventoryLayout.Grid grid, int slot, ItemStack stack){
        if(stack.isEmpty()) return;
        int x = ctx.left() + grid.x(slot), y = ctx.top() + grid.y(slot);
        ctx.graphics().renderItem(stack, x, y);
        ctx.graphics().renderItemDecorations(ctx.font(), stack, x, y);
    }

    private void search(TabletAppClientContext ctx, State state){
        if(!"storage".equals(ctx.tab().id()) || !"vault".equals(ctx.data().getString("ContainerKind"))) return;
        String value = state.panel.value("search");
        if(value.equals(state.sentSearch)) return;
        state.sentSearch = value;
        ctx.actions().send("search", value);
    }

    @Override public void refresh(TabletAppClientContext ctx, TabletAppClientState raw){
        ctx.actions().send("refresh", "network".equals(ctx.tab().id()) ? Integer.toString(((State)raw).network.offset()) : "");
    }
    @Override public boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int button){
        if("transfers".equals(ctx.tab().id()) && !"battery".equals(ctx.data().getString("ContainerKind"))
                && ((State)raw).rateUnits.mouseClicked(x, y, button)) return true;
        if(button == 1 && "storage".equals(ctx.tab().id()) && "tank".equals(ctx.data().getString("ContainerKind"))
                && new TabletLayout.Rect(ctx.left() + 7, ctx.top() + 49, 250, 32).contains(x, y)){
            ctx.actions().send("storage_control", "112");
            return true;
        }
        return ((State)raw).panel.click(x, y, button);
    }
    @Override public boolean keyPressed(TabletAppClientContext ctx, TabletAppClientState raw, int key, int scan, int modifiers){
        State state = (State)raw;
        if(state.rateUnits.keyPressed(key)) return true;
        boolean handled = state.panel.keyPressed(key, modifiers);
        if(handled) search(ctx, state);
        return handled;
    }
    @Override public boolean chatTyped(TabletAppClientContext ctx, TabletAppClientState raw, char ch, int modifiers){
        State state = (State)raw;
        boolean handled = state.panel.charTyped(ch);
        if(handled) search(ctx, state);
        return handled;
    }
    @Override public boolean mouseScrolled(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, double sx, double sy){
        State state = (State)raw;
        if("transfers".equals(ctx.tab().id()) && "battery".equals(ctx.data().getString("ContainerKind"))){
            if(x < ctx.left() || x >= ctx.left() + ctx.width() || y < ctx.top() + 68 || y >= ctx.top() + ctx.height() - 27) return false;
            boolean input = x < ctx.left() + ctx.width() / 2;
            int maximum = Math.max(0, electricalTransfers(ctx, input).size() - Math.max(1, (ctx.height() - 101) / 30));
            if(input) state.inputScroll = Mth.clamp(state.inputScroll - (int)Math.signum(sy), 0, maximum);
            else state.outputScroll = Mth.clamp(state.outputScroll - (int)Math.signum(sy), 0, maximum);
        }else if("resources".equals(ctx.tab().id()) && ctx.data().getBoolean("PowerGrid")){
            if(x < ctx.left() || x >= ctx.left() + ctx.width() || y < ctx.top() + 112 || y >= ctx.top() + ctx.height() - 15) return false;
            int count = ctx.data().getList("PowerGridConnectors", Tag.TAG_COMPOUND).size();
            state.connectorScroll = Mth.clamp(state.connectorScroll - (int)Math.signum(sy), 0,
                    Math.max(0, count - Math.max(1, (ctx.height() - 132) / 21)));
        }else if("storage".equals(ctx.tab().id()) && "tank".equals(ctx.data().getString("ContainerKind"))){
            var bounds = tankFluidList(ctx);
            if(!bounds.contains(x, y)) return false;
            int count = FluidGaugeLayout.contents(ctx.data().getList("Fluids", Tag.TAG_COMPOUND)).size();
            state.fluidScroll = Mth.clamp(state.fluidScroll - (int)Math.signum(sy), 0,
                    Math.max(0, count - Math.max(1, bounds.height() / 18)));
        }else if("storage".equals(ctx.tab().id()) && "vault".equals(ctx.data().getString("ContainerKind"))){
            ctx.actions().send("storage_control", sy > 0 ? "101" : "102");
        }else if("network".equals(ctx.tab().id())) state.network.move(ctx.data().getCompound("Inventory"), -(int)Math.signum(sy), offset -> ctx.actions().send("refresh", Integer.toString(offset)));
        else state.scroll = Math.max(0, state.scroll - (int)Math.signum(sy));
        return true;
    }
}
