package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.SmartContainerMenu;
import com.rieno.gadgetsandgizmos.lib.client.tablet.PaintedContainerRenderer;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientState;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletLayout;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletResourceGauge;
import com.rieno.gadgetsandgizmos.lib.client.tablet.FluidGaugeLayout;
import com.rieno.gadgetsandgizmos.lib.inventory.TransferRateUnit;
import com.rieno.gadgetsandgizmos.lib.power.powergrid.PowerGridPortableBattery;
import com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps.Manifest;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

// Keep native storage actions and container-specific settings on the shared painted screens
public final class SmartContainerManifestRenderer extends PaintedContainerRenderer{
    private static final TabletLayout.Rect TANK_GAUGE = new TabletLayout.Rect(9, 53, 302, 26);
    private static final TabletLayout.Rect TANK_LIST = new TabletLayout.Rect(11, 84, 298, 49);
    private static final class ContainerState extends State{
        private String sentSearch = "";
        private int unit;
    }

    public SmartContainerManifestRenderer(){ super(Manifest.SKIN); }
    @Override public TabletAppClientState createScreenState(){ return new ContainerState(); }
    @Override protected ItemStack energyIcon(TabletAppClientContext ctx){ return new ItemStack(CTItems.WORKER_ENERGY_BATTERY.get()); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        RENDERING
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override
    protected void renderBody(TabletAppClientContext ctx, State state){
        switch(ctx.tab().id()){
            case "storage" -> storage(ctx, (ContainerState)state);
            case "transfers" -> transfers(ctx, (ContainerState)state);
            case "resources" -> {
                if("tank".equals(ctx.data().getString("ContainerKind"))) tankResources(ctx, state);
                else if(ctx.data().getBoolean("PowerGrid")) batteryResources(ctx, state);
                else renderResources(ctx, state);
            }
            default -> super.renderBody(ctx, state);
        }
    }

    private void batteryResources(TabletAppClientContext ctx, State state){
        var connectors = ctx.data().getList("PowerGridConnectors", Tag.TAG_COMPOUND);
        var scroll = state.scroll("power");
        scroll.configure(1, connectors.isEmpty() ? 133 : 151 + connectors.size() * 26, CONTENT.height());
        int offset = scroll.offset();
        try(var clip = clip(ctx, CONTENT)){
            resource(ctx, "FE", ctx.data().getLong("Energy"), ctx.data().getLong("EnergyCapacity"), null, 9, 21 - offset, 302);
            row(ctx, 11, 79 - offset, 142, 26);
            icon(ctx, PowerGridPortableBattery.item(), 18, 84 - offset, 16);
            text(ctx, label("powergrid_energy"), 41, 79 - offset, 106, 10, .65F, GOLD, true);
            text(ctx, String.format(Locale.ROOT, "%,.3f / %,.3f Wh", ctx.data().getDouble("PowerGridWattHours"), ctx.data().getDouble("PowerGridCapacityWh")),
                    41, 90 - offset, 106, 10, .6F, GOLD, true);
            gauge(ctx, null, ctx.data().getDouble("PowerGridWattHours"), ctx.data().getDouble("PowerGridCapacityWh"), 9, 107 - offset, 302, 26, GOLD);
            text(ctx, label("powergrid_output_limits"), 11, 134 - offset, 298, 9, .65F, TEXT, false);
            if(connectors.isEmpty()) text(ctx, label("powergrid_no_connectors"), 11, 144 - offset, 298, 9, .6F, TEXT, false);
            for(int idx = 0; idx < connectors.size(); idx++){
                var row = connectors.getCompound(idx);
                int y = 145 + idx * 26 - offset;
                text(ctx, connectorName(row), 12, y, 121, 12, .65F, TEXT, false);
                boolean allowed = ctx.data().getBoolean("CanConfigure");
                button(ctx, state, 135, y, 20, row.getInt("Polarity") < 0 ? "-" : "+", allowed,
                        () -> ctx.actions().send("powergrid_polarity", connectorKey(row) + (row.getInt("Polarity") < 0 ? "1" : "-1")));
                button(ctx, state, 159, y, 72, String.format(Locale.ROOT, "%.2f W", row.getDouble("Watts")), allowed,
                        () -> editConnector(ctx, row, "powergrid_output", "Watts"));
                button(ctx, state, 235, y, 72, String.format(Locale.ROOT, "%.2f V", row.getDouble("Volts")), allowed,
                        () -> editConnector(ctx, row, "powergrid_voltage", "Volts"));
                text(ctx, String.format(Locale.ROOT, "%.2f V | %.2f A | %.2f W", row.getDouble("ActualVolts"), row.getDouble("ActualAmps"), row.getDouble("TransferWatts")),
                        13, y + 13, 292, 10, .6F, MUTED, false);
            }
        }
        scrollArea(ctx, state, "power", CONTENT, 22, null);
    }

    private static String connectorKey(CompoundTag row){ return row.getLong("Member") + "|" + row.getString("Face") + "|"; }
    private static String connectorName(CompoundTag row){
        var pos = BlockPos.of(row.getLong("Connector"));
        return label("face." + row.getString("Face")) + " (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
    private static void editConnector(TabletAppClientContext ctx, CompoundTag row, String action, String key){
        ctx.ui().editText(label(action + "_prompt"), Double.toString(row.getDouble(key)), 32,
                val -> ctx.actions().send(action, connectorKey(row) + val));
    }

    private void tankResources(TabletAppClientContext ctx, State state){
        boolean allowed = ctx.data().getBoolean("CanConfigure");
        text(ctx, ctx.data().getLong("FluidAmount") + " / " + ctx.data().getLong("FluidCapacity") + " mB", 11, 21, 297, 11, .8F, TEXT, false);
        button(ctx, state, 11, 36, 146, label("fuel") + ": " + fluidName(ctx.data().getString("FuelFluid")), allowed,
                () -> ctx.actions().send("fluid_selection", "fuel|"));
        button(ctx, state, 163, 36, 146, label("pump") + ": " + fluidName(ctx.data().getString("PumpFluid")), allowed,
                () -> ctx.actions().send("fluid_selection", "pump|"));
        var rows = ctx.data().getList("FluidChoices", Tag.TAG_COMPOUND);
        var viewport = new TabletLayout.Rect(11, 53, 298, 88);
        var scroll = state.scroll("choices");
        scroll.configure(rows.size(), 29, viewport.height());
        try(var clip = clip(ctx, viewport)){
            for(int idx = scroll.firstVisibleIndex(); idx < rows.size(); idx++){
                int y = 54 + scroll.rowOffset(idx);
                if(y >= 141) break;
                var row = rows.getCompound(idx);
                String id = row.getString("Id");
                text(ctx, row.getString("Name"), 12, y, 160, 12, .75F, TEXT, false);
                text(ctx, row.getLong("Amount") + " mB", 12, y + 12, 160, 11, .65F, MUTED, false);
                button(ctx, state, 179, y + 4, 61, (id.equals(ctx.data().getString("FuelFluid")) ? "* " : "") + label("fuel"), allowed,
                        () -> ctx.actions().send("fluid_selection", "fuel|" + id));
                button(ctx, state, 246, y + 4, 61, (id.equals(ctx.data().getString("PumpFluid")) ? "* " : "") + label("pump"), allowed,
                        () -> ctx.actions().send("fluid_selection", "pump|" + id));
            }
        }
        scrollArea(ctx, state, "choices", viewport, 22, null);
        text(ctx, label(rows.isEmpty() ? "empty_fluids" : "fluid_selection_hint"), 11, 143, 298, 9, .6F, MUTED, false);
    }

    private void storage(TabletAppClientContext ctx, ContainerState state){
        var inventory = ctx.ui().inventory();
        var menu = inventory != null && inventory.menu() instanceof SmartContainerMenu nativeMenu ? nativeMenu : null;
        if(inventory != null) region(skin.storage(), 74, 180, 174, 90).draw(ctx.graphics(), 73, 180);
        state.panel().paintedInput(ctx, "search", 239, 20, 72, 15, "Search");
        String query = state.panel().value("search");
        if(!query.equals(state.sentSearch)){
            state.sentSearch = query;
            if("vault".equals(ctx.data().getString("ContainerKind"))) ctx.actions().send("search", query);
            if(menu != null && menu.kind == 2) menu.search(query);
        }
        String kind = ctx.data().getString("ContainerKind");
        if("tank".equals(kind)){ tankStorage(ctx, state, query); return; }
        if("battery".equals(kind)){
            text(ctx, ctx.data().getLong("Energy") + " / " + ctx.data().getLong("EnergyCapacity") + " FE", 11, 21, 218, 12, .75F, RED, false);
            gauge(ctx, null, ctx.data().getLong("Energy"), ctx.data().getLong("EnergyCapacity"), 11, 38, 298, 14, RED);
            text(ctx, label("discharge"), 80, 55, 160, 11, .75F, TEXT, false);
            text(ctx, label("charge"), 80, 95, 160, 11, .75F, TEXT, false);
            grid(ctx, SmartContainerMenu.LAYOUT.inputs()); grid(ctx, SmartContainerMenu.LAYOUT.outputs());
            if(inventory == null){
                var rows = ctx.data().getCompound("ChargingItems").getList("Items", Tag.TAG_COMPOUND);
                for(int idx = 0; idx < rows.size(); idx++){
                    var row = rows.getCompound(idx);
                    int slot = row.getInt("Slot");
                    if(slot < 0 || slot >= 18) continue;
                    var layout = slot < 9 ? SmartContainerMenu.LAYOUT.inputs() : SmartContainerMenu.LAYOUT.outputs();
                    var level = Minecraft.getInstance().level;
                    if(level != null) icon(ctx, ItemStack.parseOptional(level.registryAccess(), row), layout.x(slot % 9), layout.y(slot % 9), 16);
                }
            }
            return;
        }
        button(ctx, state, 9, 22, 66, menu == null || menu.network() ? label("network") : label("local"), true,
                () -> ctx.actions().send("storage_control", "100"));
        button(ctx, state, 80, 22, 16, "<", menu == null || menu.row() > 0, () -> ctx.actions().send("storage_control", "101"));
        button(ctx, state, 101, 22, 16, ">", menu == null || menu.row() + SmartContainerMenu.GRID_ROWS < menu.totalRows(),
                () -> ctx.actions().send("storage_control", "102"));
        text(ctx, menu == null ? "Open the tablet to move items" : (menu.row() + 1) + " / " + Math.max(1, menu.totalRows() - SmartContainerMenu.GRID_ROWS + 1),
                123, 22, 109, 11, .65F, MUTED, false);
        grid(ctx, SmartContainerMenu.LAYOUT.storage());
        if(inventory == null){
            var items = ctx.data().getList("Items", Tag.TAG_COMPOUND);
            int slot = 0;
            for(int idx = 0; idx < items.size() && slot < SmartContainerMenu.GRID_SIZE; idx++){
                var row = items.getCompound(idx);
                if(!query.isBlank() && !row.getString("Name").toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
                var layout = SmartContainerMenu.LAYOUT.storage();
                var stack = item(row);
                stack.setCount((int)Math.min(stack.getMaxStackSize(), row.getLong("Count")));
                icon(ctx, stack, layout.x(slot), layout.y(slot), 16);
                if(!stack.isEmpty()) ctx.graphics().renderItemDecorations(ctx.font(), stack, layout.x(slot), layout.y(slot));
                slot++;
            }
        }
    }

    private void grid(TabletAppClientContext ctx, com.rieno.gadgetsandgizmos.lib.tablet.TabletInventoryLayout.Grid grid){
        for(int idx = 0; idx < grid.size(); idx++) region(skin.storage(), 154, 339, 18, 18)
                .draw(ctx.graphics(), grid.x(idx) - 1, grid.y(idx) - 1);
    }

    private void tankStorage(TabletAppClientContext ctx, State state, String query){
        text(ctx, ctx.data().getLong("FluidAmount") + " / " + ctx.data().getLong("FluidCapacity") + " mB", 11, 21, 219, 12, .75F, TEXT, false);
        text(ctx, label("pump") + ": " + fluidName(ctx.data().getString("PumpFluid")), 11, 36, 298, 12, .7F, TEXT, false);
        var fluids = FluidGaugeLayout.contents(ctx.data().getList("Fluids", Tag.TAG_COMPOUND));
        gaugeBackground(ctx, TANK_GAUGE.left(), TANK_GAUGE.top(), TANK_GAUGE.width(), TANK_GAUGE.height());
        TabletResourceGauge.fluidsFill(ctx.graphics(), fluids, ctx.data().getLong("FluidCapacity"),
                TANK_GAUGE.left() + 2, TANK_GAUGE.top() + 2, TANK_GAUGE.width() - 4, TANK_GAUGE.height() - 4);
        var filtered = fluids.stream().filter(entry -> entry.fluid().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        var scroll = state.scroll("tank"); scroll.configure(filtered.size(), 18, TANK_LIST.height());
        try(var clip = clip(ctx, TANK_LIST)){
            for(int idx = scroll.firstVisibleIndex(); idx < filtered.size(); idx++){
                int y = 84 + scroll.rowOffset(idx); if(y >= 133) break;
                var entry = filtered.get(idx);
                TabletResourceGauge.fluidFill(ctx.graphics(), entry.fluid(), 1, 1, 11, y, 14, 14);
                text(ctx, entry.fluid().getHoverName().getString(), 30, y, 181, 15, .75F, TEXT, false);
                text(ctx, entry.amount() + " mB", 217, y, 90, 15, .65F, TEXT, false);
            }
        }
        scrollArea(ctx, state, "tank", TANK_LIST, 18, null);
        button(ctx, state, 11, 138, 70, "Fill item", true, () -> ctx.actions().send("storage_control", "110"));
        button(ctx, state, 87, 138, 70, "Empty item", true, () -> ctx.actions().send("storage_control", "111"));
        text(ctx, label("bucket_hint"), 165, 136, 142, 16, .6F, MUTED, false);
    }

    private void transfers(TabletAppClientContext ctx, ContainerState state){
        if("battery".equals(ctx.data().getString("ContainerKind"))){
            transferPane(ctx, state, true, LEFT); transferPane(ctx, state, false, RIGHT); return;
        }
        var unit = TransferRateUnit.values()[state.unit];
        String resource = "tank".equals(ctx.data().getString("ContainerKind")) ? "mB" : label("items");
        text(ctx, label("transfers"), 11, 24, 197, 15, .95F, TEXT, false);
        button(ctx, state, 220, 24, 88, label("rate_unit." + unit.suffix()), true, () -> state.unit = (state.unit + 1) % TransferRateUnit.values().length);
        text(ctx, label("transfer_in") + ": " + rate(ctx.data().getDouble("TransferIn"), unit, resource), 19, 55, 282, 18, .85F, 0xFF8ABD7A, false);
        text(ctx, label("transfer_out") + ": " + rate(ctx.data().getDouble("TransferOut"), unit, resource), 19, 90, 282, 18, .85F, GOLD, false);
        text(ctx, label("transfer_window"), 11, 124, 298, 22, .7F, MUTED, false);
    }

    private void transferPane(TabletAppClientContext ctx, State state, boolean input, TabletLayout.Rect viewport){
        var rows = ctx.data().getList("PowerGridConnectors", Tag.TAG_COMPOUND).stream().map(tag -> (CompoundTag)tag)
                .filter(row -> input ? row.getDouble("TransferWatts") > 1.0E-9 : row.getDouble("TransferWatts") < -1.0E-9).toList();
        int x = viewport.left() + 3, color = input ? 0xFF8ABD7A : GOLD;
        text(ctx, label(input ? "transfer_in" : "transfer_out"), x, 22, viewport.width() - 6, 13, .85F, color, false);
        text(ctx, rate(ctx.data().getDouble(input ? "TransferIn" : "TransferOut"), TransferRateUnit.TICK, "FE"), x, 37, viewport.width() - 6, 12, .75F, color, false);
        String key = input ? "transfer_in" : "transfer_out";
        var scroll = state.scroll(key); scroll.configure(rows.size(), 30, 88);
        var area = new TabletLayout.Rect(viewport.left(), 57, viewport.width(), 88);
        try(var clip = clip(ctx, area)){
            for(int idx = scroll.firstVisibleIndex(); idx < rows.size(); idx++){
                int y = 58 + scroll.rowOffset(idx); if(y >= 145) break;
                var row = rows.get(idx);
                text(ctx, connectorName(row), x, y, viewport.width() - 6, 12, .65F, TEXT, false);
                text(ctx, String.format(Locale.ROOT, "%.2f V | %.2f A | %.2f W", row.getDouble("ActualVolts"), row.getDouble("ActualAmps"), Math.abs(row.getDouble("TransferWatts"))),
                        x, y + 13, viewport.width() - 6, 11, .6F, color, false);
            }
        }
        scrollArea(ctx, state, key, area, 22, null);
    }

    @Override protected boolean bodyClicked(TabletAppClientContext ctx, State state, double x, double y, int btn){
        if(btn == 1 && "storage".equals(ctx.tab().id()) && "tank".equals(ctx.data().getString("ContainerKind")) && TANK_GAUGE.contains(x, y)){
            ctx.actions().send("storage_control", "112"); return true;
        }
        return false;
    }
    @Override protected boolean scrollBody(TabletAppClientContext ctx, State state, double x, double y, double sy){
        if(!"storage".equals(ctx.tab().id()) || !"vault".equals(ctx.data().getString("ContainerKind")) || !CONTENT.contains(x, y)) return false;
        ctx.actions().send("storage_control", sy > 0 ? "101" : "102"); return true;
    }
    private static String label(String id){ return Component.translatable("createthrusters.smart_container_manifest." + id).getString(); }
    private static String rate(double amount, TransferRateUnit unit, String resource){ return String.format(Locale.ROOT, "%,.2f %s/%s", unit.convert(amount), resource, unit.suffix()); }
    private static String fluidName(String id){
        if(id.isBlank()) return label("automatic");
        var row = new CompoundTag(); row.putString("Id", id); var stack = fluid(row);
        return stack.isEmpty() ? id : stack.getHoverName().getString();
    }
}
