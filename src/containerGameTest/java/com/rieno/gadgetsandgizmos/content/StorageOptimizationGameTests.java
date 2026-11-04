package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.inventory.ContainerNetworkAccess;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerStorageNetworks;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

// Exercise cached automation against actual server capabilities and block replacement
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class StorageOptimizationGameTests{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final BlockPos FIRST = new BlockPos(1, 2, 1);
    private static final BlockPos SECOND = new BlockPos(3, 2, 1);

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void itemRoutingKeepsSimulationAndExecutionIndependent(GameTestHelper helper){
        helper.setBlock(FIRST, Blocks.BARREL);
        helper.setBlock(SECOND, Blocks.BARREL);
        helper.runAfterDelay(2, () -> {
            var first = (BarrelBlockEntity)helper.getBlockEntity(FIRST);
            var second = (BarrelBlockEntity)helper.getBlockEntity(SECOND);
            ContainerStorageNetworks.track(first, ignored -> List.of(first, second));
            var access = new ContainerNetworkAccess(first);
            helper.assertTrue(access.getSlots() == 54, "Connected inventory lost slots");
            second.setItem(3, new ItemStack(Items.IRON_INGOT, 16));
            for(int pass = 0; pass < 5; pass++){
                for(int slot = 0; slot < access.getSlots(); slot++) access.getStackInSlot(slot);
            }
            helper.assertTrue(access.extractItem(30, 5, true).getCount() == 5
                    && second.getItem(3).getCount() == 16, "Simulated extraction changed storage");
            helper.assertTrue(access.extractItem(30, 5, false).getCount() == 5
                    && second.getItem(3).getCount() == 11, "Live extraction changed its routing");
            helper.assertTrue(access.insertItem(30, new ItemStack(Items.IRON_INGOT, 7), true).isEmpty()
                    && second.getItem(3).getCount() == 11, "Simulated insertion changed storage");
            access.insertItem(30, new ItemStack(Items.IRON_INGOT, 7), false);
            helper.assertTrue(second.getItem(3).getCount() == 18, "Insertion lost or duplicated items");
            helper.succeed();
        });
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void warmRoutingRejectsRemovedAndReplacedMembers(GameTestHelper helper){
        helper.setBlock(FIRST, Blocks.BARREL);
        helper.setBlock(SECOND, Blocks.BARREL);
        helper.runAfterDelay(2, () -> {
            var first = (BarrelBlockEntity)helper.getBlockEntity(FIRST);
            var second = (BarrelBlockEntity)helper.getBlockEntity(SECOND);
            AtomicReference<List<net.minecraft.world.level.block.entity.BlockEntity>> linked =
                    new AtomicReference<>(List.of(first, second));
            ContainerStorageNetworks.track(first, ignored -> linked.get());
            var access = new ContainerNetworkAccess(first);
            second.setItem(0, new ItemStack(Items.GOLD_INGOT, 8));
            helper.assertTrue(access.getStackInSlot(27).getCount() == 8, "Remote slot was unavailable");
            helper.setBlock(SECOND, Blocks.AIR);
            helper.assertTrue(access.getStackInSlot(27).isEmpty()
                    && access.extractItem(27, 8, false).isEmpty(), "Cached slot retained a removed inventory");
            helper.setBlock(SECOND, Blocks.BARREL);
            var replacement = (BarrelBlockEntity)helper.getBlockEntity(SECOND);
            replacement.setItem(0, new ItemStack(Items.DIAMOND, 3));
            linked.set(List.of(first, replacement));
            ContainerStorageNetworks.invalidate();
            helper.assertTrue(access.getStackInSlot(27).is(Items.DIAMOND), "Revision retained an old handler");
            linked.set(List.of(first));
            ContainerStorageNetworks.invalidate();
            helper.assertTrue(access.getSlots() == 27 && access.getStackInSlot(27).isEmpty(), "Disconnect retained remote slots");
            helper.setBlock(FIRST, Blocks.AIR);
            helper.assertTrue(access.getSlots() == 0, "Removed entry remained available");
            helper.succeed();
        });
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void retainedFluidPoolReadsLiveContents(GameTestHelper helper){
        helper.setBlock(FIRST, CTBlocks.SMART_TANK.get());
        helper.runAfterDelay(2, () -> {
            var tank = (SmartTankBlockEntity)helper.getBlockEntity(FIRST);
            var access = tank.networkResources();
            var pool = tank.localResources();
            helper.assertTrue(access.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.SIMULATE) == 1000,
                    "Tank rejected simulated input");
            helper.assertTrue(access.getFluidInTank(0).isEmpty(), "Simulated fill changed storage");
            helper.assertTrue(access.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000,
                    "Tank lost input");
            helper.assertTrue(pool == tank.localResources(), "Stable fluid handler rebuilt its pool");
            helper.assertTrue(access.drain(400, FluidAction.SIMULATE).getAmount() == 400
                    && access.getFluidInTank(0).getAmount() == 1000, "Simulated drain changed storage");
            helper.assertTrue(access.drain(400, FluidAction.EXECUTE).getAmount() == 400
                    && access.getFluidInTank(0).getAmount() == 600, "Drain lost or duplicated fluid");
            helper.succeed();
        });
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void retainedEnergyPoolPreservesBatteryAccounting(GameTestHelper helper){
        helper.setBlock(FIRST, CTBlocks.SMART_BATTERY.get());
        helper.runAfterDelay(2, () -> {
            var battery = (SmartBatteryBlockEntity)helper.getBlockEntity(FIRST);
            var access = battery.networkResources();
            var pool = battery.localResources();
            int before = access.getEnergyStored();
            helper.assertTrue(access.receiveEnergy(10000, true) == 10000
                    && access.getEnergyStored() == before, "Simulated charge changed storage");
            helper.assertTrue(access.receiveEnergy(10000, false) == 10000
                    && access.getEnergyStored() == before + 10000, "Battery lost incoming energy");
            helper.assertTrue(pool == battery.localResources(), "Stable energy handler rebuilt its pool");
            helper.assertTrue(access.extractEnergy(4000, true) == 4000
                    && access.getEnergyStored() == before + 10000, "Simulated extraction changed energy");
            helper.assertTrue(access.extractEnergy(4000, false) == 4000
                    && access.getEnergyStored() == before + 6000, "Battery lost or duplicated energy");
            helper.succeed();
        });
    }
}
