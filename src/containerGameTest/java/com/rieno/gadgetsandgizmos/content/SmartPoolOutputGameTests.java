package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.inventory.ContainerStorageNetworks;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

// Exercise output ports when resources exist only in another linked container
@GameTestHolder("createthrusters_pool_outputs")
@PrefixGameTestTemplate(false)
public final class SmartPoolOutputGameTests{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void controllerLinkedPoolsAreExtractableFromEveryMatchingContainer(GameTestHelper helper){
        var firstTank = tank(helper, 1); var secondTank = tank(helper, 5);
        var firstBattery = batteryAt(helper, new BlockPos(1, 2, 5));
        var secondBattery = batteryAt(helper, new BlockPos(5, 2, 5));
        var controllerPos = new BlockPos(3, 2, 3);
        helper.setBlock(controllerPos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        var controller = (AdvancedContraptionControllerBlockEntity)helper.getBlockEntity(controllerPos);
        var firstFluidPort = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, firstTank.getBlockPos(), Direction.SOUTH);
        var secondFluidPort = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, secondTank.getBlockPos(), Direction.NORTH);
        var firstEnergyPort = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, firstBattery.getBlockPos(), Direction.SOUTH);
        var secondEnergyPort = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, secondBattery.getBlockPos(), Direction.NORTH);
        firstFluidPort.getTanks(); secondFluidPort.getTanks();
        firstEnergyPort.getEnergyStored(); secondEnergyPort.getEnergyStored();
        List<BlockEntity> members = List.of(firstTank, secondTank, firstBattery, secondBattery);
        var targets = members.stream().map(member -> new ContraptionNetworkLinkerData.LinkedTarget(member.getBlockPos(), null,
                BuiltInRegistries.BLOCK.getKey(member.getBlockState().getBlock()).toString(), "Pool member",
                ContraptionNetworkLinkerData.LinkMode.SCM, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of())).toList();
        var edited = ContraptionNetworkLinkerData.writeClientEditRoot(ItemStack.EMPTY, targets,
                ContraptionNetworkLinkerData.LinkMode.SCM, ContraptionNetworkLinkerData.TargetMode.BLOCK);
        var saved = controller.saveWithoutMetadata(helper.getLevel().registryAccess());
        saved.put("ControllerStoredTargets", edited);
        controller.readAdditionalControllerManifestData(saved, helper.getLevel().registryAccess(), "advanced_controller");
        helper.assertTrue(controller.getLinkedTargets().size() == members.size(), "Controller could not restore linked storage");
        secondTank.getFluidHandler().fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE);
        secondBattery.receiveEnergy(10000L, false);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(controller.linkedStorage().containsAll(members), "Controller storage discovery omitted tanks or batteries");
            helper.assertTrue(firstFluidPort.drain(1000, FluidAction.EXECUTE).getAmount() == 1000, "First linked tank could not drain remote fluid");
            helper.assertTrue(secondFluidPort.drain(1000, FluidAction.EXECUTE).getAmount() == 1000, "Second linked tank could not drain pooled fluid");
            helper.assertTrue(firstEnergyPort.extractEnergy(1000, false) == 1000, "First linked battery could not drain remote FE");
            helper.assertTrue(secondEnergyPort.extractEnergy(1000, false) == 1000, "Second linked battery could not drain pooled FE");
            helper.assertTrue(firstBattery.electricalStorage().extractEnergy(1000, false) == 1000,
                    "First linked battery voltage port could not drain pooled FE");
            helper.assertTrue(secondBattery.electricalStorage().extractEnergy(1000, false) == 1000,
                    "Second linked battery voltage port could not drain pooled FE");
            helper.assertTrue(firstBattery.getLongEnergyStored() + secondBattery.getLongEnergyStored() == 6000,
                    "Network FE and voltage ports did not share one physical energy balance");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void createPumpPullsRemoteFluidThroughAnEmptyLinkedTank(GameTestHelper helper){
        verifyPump(helper, true, false);
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void createPumpFixtureTransfersLocalFluid(GameTestHelper helper){
        verifyPump(helper, false, false);
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void runningPipeSeesFluidAddedLaterToAnotherLinkedTank(GameTestHelper helper){
        verifyPump(helper, true, true);
    }

    @GameTest(template = "empty")
    public static void tankCapabilityDrainsRemoteFluidWithPumpSelection(GameTestHelper helper){
        var local = tank(helper, 1); var remote = tank(helper, 5);
        remote.getFluidHandler().fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE);
        link(local, remote);
        local.selectFluid(false, "minecraft:water");
        var capability = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, local.getBlockPos(), Direction.SOUTH);
        helper.assertTrue(capability != null, "Smart Tank fluid capability is missing");
        helper.assertTrue(capability.drain(1000, FluidAction.SIMULATE).getAmount() == 1000, "Pipe simulation cannot see pooled fluid");
        helper.assertTrue(capability.drain(1000, FluidAction.EXECUTE).getAmount() == 1000, "Pipe extraction cannot drain pooled fluid");
        helper.assertTrue(local.getFluidHandler().drain(1000, FluidAction.SIMULATE).isEmpty(), "Empty local tank gained fluid during extraction");
        helper.assertTrue(remote.getTankInventory().getFluidAmount() == 1000, "Remote tank balance is wrong");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bucketPortDrainsRemoteFluid(GameTestHelper helper){
        var local = tank(helper, 1); var remote = tank(helper, 5);
        remote.getFluidHandler().fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE);
        link(local, remote);
        local.selectFluid(false, "minecraft:water");
        helper.assertTrue(local.bucketHandler().drain(1000, FluidAction.EXECUTE).getAmount() == 1000, "Bucket port only sees local fluid");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batteryCapabilityExtractsRemoteEnergy(GameTestHelper helper){
        var local = battery(helper, 1); var remote = battery(helper, 5);
        remote.receiveEnergy(10000L, false); link(local, remote);
        var capability = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, local.getBlockPos(), Direction.SOUTH);
        helper.assertTrue(capability != null && capability.extractEnergy(1000, true) == 1000, "FE cable simulation cannot see pooled energy");
        helper.assertTrue(capability.extractEnergy(1000, false) == 1000 && remote.getLongEnergyStored() == 9000,
                "FE cable extraction did not debit the remote battery");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batterySuppliesAdjacentMachinesFromPool(GameTestHelper helper){
        var local = battery(helper, 1); var remote = battery(helper, 5);
        helper.setBlock(new BlockPos(1, 2, 2), CTBlocks.INDUSTRIAL_MOTOR.get().defaultBlockState());
        var motor = (IndustrialMotorBlockEntity)helper.getBlockEntity(new BlockPos(1, 2, 2));
        remote.receiveEnergy(10000L, false); link(local, remote);
        local.tick();
        helper.assertTrue(motor.getEnergyStorage().getEnergyStored() > 0, "Adjacent machine cannot draw pooled FE through an empty battery");
        helper.assertTrue(local.getLongEnergyStored() == 0 && remote.getLongEnergyStored() < 10000, "Pooled FE transfer used the wrong store");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void electricalPortUsesTheNetworkEnergyPool(GameTestHelper helper){
        var local = battery(helper, 1); var remote = battery(helper, 5);
        remote.receiveEnergy(10000L, false); link(local, remote);
        helper.assertTrue(local.electricalStorage().getEnergyStored() == 10000, "Voltage output only sees local FE");
        helper.assertTrue(local.electricalStorage().extractEnergy(1000L, false) == 1000 && remote.getLongEnergyStored() == 9000,
                "Voltage output cannot debit pooled FE");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void retainedEnergyPortsStopUsingDetachedBatteryStorage(GameTestHelper helper){
        var local = battery(helper, 1); var remote = battery(helper, 5);
        remote.receiveEnergy(10000L, false); link(local, remote);
        var cable = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, local.getBlockPos(), Direction.SOUTH);
        var voltage = local.electricalStorage();
        helper.assertTrue(cable.getEnergyStored() == 10000 && voltage.getEnergyStored() == 10000, "Ports did not see pooled energy");
        ContainerStorageNetworks.track(local, ignored -> List.of(local));
        ContainerStorageNetworks.invalidate();
        helper.assertTrue(cable.extractEnergy(1000, false) == 0 && voltage.extractEnergy(1000, false) == 0,
                "Cached output port retained detached energy storage");
        helper.assertTrue(remote.getLongEnergyStored() == 10000, "Detaching a pool member changed its stored energy");
        helper.succeed();
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static void verifyPump(GameTestHelper helper, boolean pooled, boolean later){
        var local = tank(helper, 1); var remote = tank(helper, 5);
        var destinationPos = new BlockPos(1, 2, 5);
        helper.setBlock(destinationPos, CTBlocks.SMART_TANK.get().defaultBlockState());
        var destination = (SmartTankBlockEntity)helper.getBlockEntity(destinationPos);
        Runnable fill = () -> (pooled ? remote : local).getFluidHandler().fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE);
        if(later) helper.runAfterDelay(40, fill); else fill.run();
        link(local, remote);
        var pipe = com.simibubi.create.AllBlocks.FLUID_PIPE.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH, true)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH, true);
        helper.setBlock(new BlockPos(1, 2, 2), pipe);
        helper.setBlock(new BlockPos(1, 2, 4), pipe);
        helper.setBlock(new BlockPos(1, 2, 3), com.simibubi.create.AllBlocks.MECHANICAL_PUMP.getDefaultState()
                .setValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING, Direction.SOUTH));
        var pump = (com.simibubi.create.content.fluids.pump.PumpBlockEntity)helper.getBlockEntity(new BlockPos(1, 2, 3));
        helper.setBlock(new BlockPos(2, 2, 3), com.simibubi.create.AllBlocks.COGWHEEL.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.Z));
        helper.setBlock(new BlockPos(2, 2, 2), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.SOUTH));
        helper.succeedWhen(() -> {
            helper.assertTrue(destination.getTankInventory().getFluidAmount() > 0, "Create pump did not transfer fluid: pooled=" + pooled
                    + ", speed=" + pump.getSpeed() + ", available=" + local.networkResources().drain(1000, FluidAction.SIMULATE).getAmount());
            if(pooled){
                helper.assertTrue(local.getTankInventory().getFluidAmount() == 0, "Pump refilled the entry tank instead of extracting pooled fluid");
                helper.assertTrue(remote.getTankInventory().getFluidAmount() < 2000, "Pump did not drain the remote tank");
            }
        });
    }

    private static SmartTankBlockEntity tank(GameTestHelper helper, int x){
        var pos = new BlockPos(x, 2, 1); helper.setBlock(pos, CTBlocks.SMART_TANK.get().defaultBlockState());
        return (SmartTankBlockEntity)helper.getBlockEntity(pos);
    }
    private static SmartBatteryBlockEntity battery(GameTestHelper helper, int x){
        return batteryAt(helper, new BlockPos(x, 2, 1));
    }
    private static SmartBatteryBlockEntity batteryAt(GameTestHelper helper, BlockPos pos){
        helper.setBlock(pos, CTBlocks.SMART_BATTERY.get().defaultBlockState());
        return (SmartBatteryBlockEntity)helper.getBlockEntity(pos);
    }
    private static void link(BlockEntity local, BlockEntity remote){
        ContainerStorageNetworks.track(local, ignored -> List.of(local, remote));
        ContainerStorageNetworks.invalidate();
    }
}
