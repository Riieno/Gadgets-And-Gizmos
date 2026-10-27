package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerAutomation;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerAutomationStore;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerContentsSnapshot;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerNetworkSnapshot;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerStorageNetworks;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletTabDefinition;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletActionPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Set;

// A dedicated container app which is never registered in the handheld app catalogue or store
public final class SmartContainerManifest{
    public static final ResourceLocation ID = DiagnosticTabletData.appId("smart_container_manifest");
    public static final TabletAppDefinition APP = new TabletAppDefinition(ID,
            Component.translatable("createthrusters.smart_container_manifest.title"), Component.empty(), 0xFFDCB75F,
            List.of(tab("contents"), tab("resources"), tab("stock"), tab("network"), tab("storage"), tab("transfers")),
            DiagnosticTabletData.appId("textures/gui/apps/smart_container_manifest.png"), Set.of(), true);
    private SmartContainerManifest(){}

    private static TabletTabDefinition tab(String id){
        return new TabletTabDefinition(id, Component.translatable("createthrusters.smart_container_manifest." + id),
                id.equals("stock") ? List.of("refresh", "stock") : List.of("refresh"));
    }

    // Use the experimental controller's worker storage topology
    public static List<BlockEntity> linkedStorage(BlockEntity source){
        return source instanceof AdvancedContraptionControllerBlockEntity acc ? acc.linkedStorage() : List.of();
    }

    public static void handle(ServerPlayer player, DiagnosticTabletActionPayload payload){
        if(!payload.placed() || !ID.equals(payload.appId())) return;
        var selected = SimulatedHelper.findLoadedBlockEntityExact(player.level(), payload.tabletSubLevelId(), payload.tabletPos());
        if(!SmartContainerTablets.supports(selected)) return;
        var target = SmartContainerTablets.controller(selected);
        var global = SimulatedHelper.toGlobalWorldPosition(target, target.getBlockPos().getCenter());
        if(global == null || player.distanceToSqr(global) > 256 || !WorldAccessPolicy.canAccessLocal(
                player, player.serverLevel(), SimulatedHelper.getContainingSubLevelId(target), target.getBlockPos())) return;
        if(!SmartContainerTablets.tabletId(target).equals(payload.sourceTabletId())) return;
        if(APP.tabs().stream().noneMatch(tab -> tab.id().equals(payload.tabId()))) return;
        if("menu_refresh".equals(payload.actionId())){
            if(player.containerMenu instanceof SmartContainerMenu menu && menu.tabletId.equals(payload.sourceTabletId())
                    && menu.targetPos.equals(payload.tabletPos()) && java.util.Objects.equals(menu.subLevelId, payload.tabletSubLevelId())) menu.refreshSnapshot(payload.value());
            return;
        }
        if(("refresh".equals(payload.actionId()) || "background_refresh".equals(payload.actionId()))
                && player.containerMenu instanceof SmartContainerMenu menu && menu.tabletId.equals(payload.sourceTabletId())) return;
        if("search".equals(payload.actionId())){
            if(player.containerMenu instanceof SmartContainerMenu menu && menu.tabletId.equals(payload.sourceTabletId())
                    && menu.targetPos.equals(payload.tabletPos()) && java.util.Objects.equals(menu.subLevelId, payload.tabletSubLevelId())) menu.search(payload.value());
            return;
        }
        if("select".equals(payload.actionId()) && player.containerMenu instanceof SmartContainerMenu menu
                && menu.tabletId.equals(payload.sourceTabletId())){
            menu.clickMenuButton(player, SmartContainerMenu.TABS.indexOf(payload.tabId()));
        }
        CompoundTag data;
        try{
            data = snapshot(player, target, payload.tabId(), payload.actionId(), payload.value());
        }catch(IllegalArgumentException ex){
            data = new CompoundTag();
            data.putString("Error", ex.getMessage() == null ? "Container unavailable" : ex.getMessage());
        }
        PacketDistributor.sendToPlayer(player, new DiagnosticTabletAppSnapshotPayload(ID, true,
                payload.sourceTabletId(), payload.tabletSubLevelId(), payload.tabletPos(), data));
    }

    public static CompoundTag snapshot(ServerPlayer player, BlockEntity target, String tab, String action, String value){
        if(!Set.of("refresh", "background_refresh", "select", "stock", "fluid_selection", "powergrid_output", "powergrid_voltage", "powergrid_polarity").contains(action)){
            throw new IllegalArgumentException("This tablet is locked to its container manifest");
        }
        var subLevel = SimulatedHelper.getContainingSubLevelId(target);
        var store = ContainerAutomationStore.get(player.serverLevel());
        ContainerAutomation config = store.find(subLevel, target.getBlockPos());
        if("powergrid_output".equals(action) || "powergrid_voltage".equals(action) || "powergrid_polarity".equals(action)){
            if(!(target instanceof SmartBatteryBlockEntity battery) || !"resources".equals(tab)){
                throw new IllegalArgumentException("Choose the battery Resources tab");
            }
            String[] args = value.split("\\|", 3);
            net.minecraft.core.BlockPos member;
            net.minecraft.core.Direction face;
            boolean voltage = "powergrid_voltage".equals(action);
            boolean polarity = "powergrid_polarity".equals(action);
            double setting;
            try{
                if(args.length != 3) throw new IllegalArgumentException();
                member = net.minecraft.core.BlockPos.of(Long.parseLong(args[0]));
                face = net.minecraft.core.Direction.byName(args[1]);
                setting = Double.parseDouble(args[2].strip());
                double maximum = voltage ? com.rieno.gadgetsandgizmos.lib.power.ConnectorOutputLimits.MAX_VOLTS
                        : com.rieno.gadgetsandgizmos.lib.power.ConnectorOutputLimits.MAX_WATTS;
                if(face == null || !Double.isFinite(setting) || (polarity ? setting != -1 && setting != 1 : setting < 0 || setting > maximum)){
                    throw new IllegalArgumentException();
                }
            }catch(IllegalArgumentException ex){ throw new IllegalArgumentException(polarity ? "Choose positive or negative voltage polarity" : voltage
                    ? "Enter a valid output voltage (0 disables output)" : "Enter a valid output limit in watts (0 disables output)"); }
            if(config == null) config = store.configure(player.getUUID(), subLevel, target.getBlockPos());
            if(!config.claim(player.getUUID()) && !player.hasPermissions(2)){
                throw new IllegalArgumentException("Only the container owner or an operator can change connector output");
            }
            boolean changed = polarity ? battery.setPowerGridPolarity(member, face, (int)setting)
                    : voltage ? battery.setPowerGridVoltage(member, face, setting) : battery.setPowerGridOutput(member, face, setting);
            if(!changed) throw new IllegalArgumentException("That Device Connector is no longer attached to this battery");
            store.setDirty();
        }
        if("fluid_selection".equals(action)){
            if(!(target instanceof SmartTankBlockEntity tank) || !"resources".equals(tab)){
                throw new IllegalArgumentException("Choose the tank Resources tab");
            }
            if(config == null) config = store.configure(player.getUUID(), subLevel, target.getBlockPos());
            if(!config.claim(player.getUUID()) && !player.hasPermissions(2)){
                throw new IllegalArgumentException("Only the container owner or an operator can change fluid routing");
            }
            String[] args = value.split("\\|", 2);
            if(args.length != 2 || !("fuel".equals(args[0]) || "pump".equals(args[0]))){
                throw new IllegalArgumentException("Choose a fuel or pump fluid");
            }
            tank.selectFluid("fuel".equals(args[0]), args[1]);
            store.setDirty();
        }
        if("stock".equals(action)){
            if(!"stock".equals(tab)) throw new IllegalArgumentException("Choose the Stock tab");
            if(config == null) config = store.configure(player.getUUID(), subLevel, target.getBlockPos());
            if(!config.claim(player.getUUID()) && !player.hasPermissions(2)){
                throw new IllegalArgumentException("Only the container owner or an operator can change stock");
            }
            String[] args = value.split("\\|", 3);
            if(args.length < 2) throw new IllegalArgumentException("Choose an item and desired stock amount");
            ResourceLocation id = ResourceLocation.tryParse(args[0]);
            var item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
            ItemStack sample = player.getOffhandItem().is(item) ? player.getOffhandItem() : new ItemStack(item);
            int amount;
            try{ amount = Integer.parseInt(args[1]); }
            catch(NumberFormatException ex){ throw new IllegalArgumentException("Enter a valid stock amount"); }
            if(item == Items.AIR || !config.setTarget(sample, amount, args.length == 3 ? args[2] : "transfer")){
                throw new IllegalArgumentException("Invalid stock target");
            }
            store.setDirty();
        }
        if(config == null) config = new ContainerAutomation();
        CompoundTag data = config.toTag(target.getLevel().registryAccess());
        data.merge(ContainerContentsSnapshot.capture(target));
        data.putString("ContainerKind", target instanceof SmartBatteryBlockEntity ? "battery" : target instanceof SmartTankBlockEntity ? "tank" : "vault");
        if(target instanceof SmartBatteryBlockEntity battery){
            data.put("ChargingItems", battery.chargingItems().serializeNBT(target.getLevel().registryAccess()));
            battery.addPowerGridSnapshot(data);
        }
        if(target instanceof SmartTankBlockEntity tank){
            if("resources".equals(tab)) data.put("FluidChoices", ContainerNetworkSnapshot.fluidChoices(ContainerStorageNetworks.connected(target, player)));
            data.putString("FuelFluid", tank.fuelFluid());
            data.putString("PumpFluid", tank.pumpFluid());
            data.putInt("FluidAmount", tank.getTankInventory().getFluidAmount());
            data.putInt("FluidCapacity", tank.getTankInventory().getCapacity());
        }
        data.putBoolean("CanConfigure", config.owner() == null || player.getUUID().equals(config.owner()) || player.hasPermissions(2));
        if("network".equals(tab)){
            int offset = 0;
            if(("refresh".equals(action) || "background_refresh".equals(action)) && !value.isBlank()){
                try{ offset = Math.max(0, Integer.parseInt(value)); }
                catch(NumberFormatException ignored){}
            }
            data.put("Inventory", ContainerNetworkSnapshot.capture(ContainerStorageNetworks.connected(target, player), offset, 32));
        }
        return data;
    }
}
