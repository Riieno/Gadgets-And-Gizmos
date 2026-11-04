package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.compat.simulated.SimulatedDockingApi;
import com.rieno.gadgetsandgizmos.lib.shipping.LogisticsBindings.Group;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.CargoThresholdCondition;
import com.simibubi.create.content.trains.schedule.condition.FluidThresholdCondition;
import com.simibubi.create.content.trains.schedule.condition.ItemThresholdCondition;
import net.minecraft.world.level.block.entity.BlockEntity;

// Satisfy the active schedule conditions through the selected physical docking pair
final class ScmLogisticsSchedule{
    private ScmLogisticsSchedule(){}

    static int exchange(AdvancedContraptionControllerBlockEntity controller, ShipDockRegistry.Dock dock,
                        BlockEntity ship, BlockEntity station, ScheduleEntry entry, int[] progress, boolean autoRefuel){
        if(controller.getLevel() == null || !DockingConnectorAutomation.isLockedPair(ship, station)
                || !SimulatedDockingApi.hasPhysicalConnection(ship)) return 0;
        return ScmDockingAccess.nativeAccess(() -> {
            var target = SimulatedHelper.findLoadedBlockEntityExact(station.getLevel(), dock.subLevelId(), dock.pos());
            if(!(target instanceof ShipDockBlockEntity shipDock)) return 0;
            var role = controller.getScmDockingConnectorGroup(SimulatedHelper.getContainingSubLevelId(ship), ship.getBlockPos());
            boolean itemsAllowed = shipDock.allowsLogistics(station, false)
                    && (role == ScmConfigurationProfile.DockingConnectorGroup.ANY || role == ScmConfigurationProfile.DockingConnectorGroup.ITEMS);
            boolean fluidsAllowed = shipDock.allowsLogistics(station, true)
                    && (role == ScmConfigurationProfile.DockingConnectorGroup.ANY || role == ScmConfigurationProfile.DockingConnectorGroup.FLUIDS);
            boolean energyAllowed = shipDock.allowsLogistics(station, true)
                    && (role == ScmConfigurationProfile.DockingConnectorGroup.ANY || role == ScmConfigurationProfile.DockingConnectorGroup.FE);
            boolean fuelAllowed = shipDock.allowsLogistics(station, true)
                    && (role == ScmConfigurationProfile.DockingConnectorGroup.ANY || role == ScmConfigurationProfile.DockingConnectorGroup.FUEL);
            var storage = ShipCargoAutomation.dockStorage(controller, dock);
            var logistics = controller.logistics();
            int moved = 0;
            for(int idx = 0; idx < entry.conditions.size(); idx++){
                var column = entry.conditions.get(idx);
                int current = progress.length == entry.conditions.size() ? progress[idx] : 0;
                if(current < 0 || current >= column.size()) continue;
                var condition = column.get(current);
                if(condition instanceof ItemThresholdCondition items){
                    if(!itemsAllowed) continue;
                    long amount = items.getMeasure() == 1 ? ShipCargoAutomation.countFullItemStacks(controller, items.getItem(0))
                            : ShipCargoAutomation.countItems(controller, items.getItem(0));
                    long delta = demand(items.getOperator(), amount, items.getThreshold());
                    var filter = FilterItemStack.of(items.getItem(0));
                    int limit = (int)Math.min(64L, Math.abs(delta) * (items.getMeasure() == 1 ? 64L : 1L));
                    moved += logistics.exchange(storage, Group.ITEMS, delta > 0L,
                            stack -> filter.test(controller.getLevel(), stack), stack -> false, limit, 0, 0);
                }else if(condition instanceof FluidThresholdCondition fluids){
                    if(!fluidsAllowed) continue;
                    long amount = ShipCargoAutomation.countFluids(controller, fluids.getItem(0));
                    long delta = demand(fluids.getOperator(), amount / 1000L, fluids.getThreshold());
                    long fluidTarget = switch(fluids.getOperator()){
                        case GREATER -> ((long)fluids.getThreshold() + 1L) * 1000L;
                        case LESS -> Math.max(0L, (long)fluids.getThreshold() * 1000L - 1L);
                        case EQUAL -> (long)fluids.getThreshold() * 1000L;
                    };
                    var filter = FilterItemStack.of(fluids.getItem(0));
                    moved += logistics.exchange(storage, Group.FLUIDS, delta > 0L, stack -> false,
                            stack -> filter.test(controller.getLevel(), stack), 0,
                            delta == 0L ? 0 : (int)Math.min(4000L, Math.abs(fluidTarget - amount)), 0);
                }else if(condition instanceof CargoThresholdCondition energy
                        && "createaddition:energy_threshold".equals(String.valueOf(condition.getId()))){
                    if(!energyAllowed) continue;
                    long delta = demand(energy.getOperator(), ShipCargoAutomation.resourceStatus(controller).energy(),
                            EnergyCargoUnits.toFe(energy.getThreshold(), energy.getMeasure()));
                    moved += logistics.exchange(storage, Group.ENERGY, delta > 0L,
                            stack -> false, stack -> false, 0, 0, (int)Math.min(1_000_000L, Math.abs(delta)));
                }
            }
            if(fuelAllowed && (autoRefuel || entry.instruction instanceof RefuelIfInstruction)){
                RefuelIfInstruction refuel = entry.instruction instanceof RefuelIfInstruction instruction ? instruction : null;
                var filter = FilterItemStack.of(refuel == null ? net.minecraft.world.item.ItemStack.EMPTY : refuel.getItem(0));
                boolean energyOnly = refuel != null && refuel.usesEnergy();
                boolean filtered = refuel != null && refuel.hasFuelFilter();
                moved += logistics.exchange(storage, Group.FUEL, true,
                        stack -> !energyOnly && logistics.acceptsFuel(stack) && (!filtered || filter.test(controller.getLevel(), stack)),
                        stack -> !energyOnly && logistics.acceptsFuel(stack) && (!filtered || filter.test(controller.getLevel(), stack)),
                        energyOnly ? 0 : 64, energyOnly ? 0 : 4000, filtered && !energyOnly ? 0 : 1_000_000);
            }
            return moved;
        });
    }

    static long demand(CargoThresholdCondition.Ops operator, long amount, long threshold){
        return com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting.demand(
                com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting.Comparison.valueOf(operator.name()), amount, threshold);
    }
}
