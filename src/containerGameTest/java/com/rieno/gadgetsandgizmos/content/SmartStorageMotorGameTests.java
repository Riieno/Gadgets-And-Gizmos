package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class SmartStorageMotorGameTests{
    private SmartStorageMotorGameTests(){}

    @GameTest(template = "smart_storage_motor", timeoutTicks = 100)
    public static void vaultFormsFromEitherEndOnXAxis(GameTestHelper ctx){ formation(ctx, false, Direction.Axis.X, true); }

    @GameTest(template = "smart_storage_motor", timeoutTicks = 100)
    public static void vaultFormsFromEitherEndOnZAxis(GameTestHelper ctx){ formation(ctx, false, Direction.Axis.Z, false); }

    @GameTest(template = "smart_storage_motor", timeoutTicks = 100)
    public static void batteryFormsFromEitherEndOnXAxis(GameTestHelper ctx){ formation(ctx, true, Direction.Axis.X, false); }

    @GameTest(template = "smart_storage_motor", timeoutTicks = 100)
    public static void batteryFormsFromEitherEndOnZAxis(GameTestHelper ctx){ formation(ctx, true, Direction.Axis.Z, true); }

    @GameTest(template = "smart_storage_motor", timeoutTicks = 100)
    public static void loadedStandaloneVaultsRecoverConnectivity(GameTestHelper ctx){ loadedSingles(ctx, false); }

    @GameTest(template = "smart_storage_motor", timeoutTicks = 100)
    public static void loadedStandaloneBatteriesRecoverConnectivity(GameTestHelper ctx){ loadedSingles(ctx, true); }

    @GameTest(template = "smart_storage_motor", timeoutTicks = 100)
    public static void missingLoadedBatteryControllersRecoverWithoutLosingFe(GameTestHelper ctx){
        BlockPos origin = new BlockPos(2, 2, 2);
        var state = CTBlocks.SMART_BATTERY.get().defaultBlockState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, Direction.Axis.X);
        for(BlockPos pos : positions(origin, Direction.Axis.X, 2, 3)){
            ctx.getLevel().setBlock(ctx.absolutePos(pos), state, 64);
            var battery = (SmartBatteryBlockEntity)ctx.getBlockEntity(pos);
            battery.receiveEnergy(1000L, false);
            battery.setController(ctx.absolutePos(origin.below()));
        }
        ctx.runAtTickTime(30, () -> {
            assertFormed(ctx, origin, Direction.Axis.X, 2, 3);
            ctx.assertTrue(((SmartBatteryBlockEntity)ctx.getBlockEntity(origin)).getLongEnergyStored() == 12_000L,
                    "Missing-controller recovery changed the stored FE");
            ctx.succeed();
        });
    }

    @GameTest(template = "smart_storage_motor", timeoutTicks = 100)
    public static void anUnloadedVaultControllerIsNotDiscardedOrForcedToLoad(GameTestHelper ctx){
        BlockPos pos = new BlockPos(4, 3, 4);
        ctx.setBlock(pos, CTBlocks.SMART_VAULT.get());
        var vault = (SmartVaultBlockEntity)ctx.getBlockEntity(pos);
        BlockPos unloaded = vault.getBlockPos().offset(10_000, 0, 0);
        ctx.assertTrue(!ctx.getLevel().isLoaded(unloaded), "Fixture controller chunk was already loaded");
        vault.setController(unloaded);
        ctx.runAtTickTime(30, () -> {
            ctx.assertTrue(!ctx.getLevel().isLoaded(unloaded), "Vault forced its unavailable controller chunk to load");
            ctx.assertTrue(vault.getController().equals(unloaded), "Vault discarded an unloaded controller");
            ctx.succeed();
        });
    }

    @GameTest(template = "smart_storage_motor", timeoutTicks = 100)
    public static void batteryCableSuppliesContinuousMotorDemand(GameTestHelper ctx){
        BlockPos origin = new BlockPos(3, 3, 3);
        place(ctx, CTBlocks.SMART_BATTERY.get(), origin, Direction.Axis.X, 2, 3, false);
        var battery = (SmartBatteryBlockEntity)ctx.getBlockEntity(origin);
        battery.receiveEnergy(1_000_000L, false);
        BlockPos motorPos = origin.offset(-2, 0, 0);
        ctx.setBlock(motorPos, CTBlocks.INDUSTRIAL_MOTOR.get().defaultBlockState()
                .setValue(IndustrialMotorBlock.FACING, Direction.WEST));
        ctx.setBlock(motorPos.above(), CTBlocks.INDUSTRIAL_MOTOR.get().defaultBlockState()
                .setValue(IndustrialMotorBlock.FACING, Direction.WEST));
        ctx.setBlock(origin.west(), CTBlocks.ANDESITE_CABLE.get().defaultBlockState()
                .setValue(PipeBlock.EAST, true).setValue(PipeBlock.WEST, true).setValue(PipeBlock.UP, true));
        ctx.setBlock(origin.west().above(), CTBlocks.ANDESITE_CABLE.get().defaultBlockState()
                .setValue(PipeBlock.EAST, true).setValue(PipeBlock.WEST, true).setValue(PipeBlock.DOWN, true));
        var motor = (IndustrialMotorBlockEntity)ctx.getBlockEntity(motorPos);
        var secondMotor = (IndustrialMotorBlockEntity)ctx.getBlockEntity(motorPos.above());
        ctx.runAtTickTime(10, () -> {
            ctx.assertTrue(Math.abs(motor.getGeneratedSpeed()) > 0 && Math.abs(secondMotor.getGeneratedSpeed()) > 0,
                    "Cable throttled the motors below their continuous FE demand");
            ctx.assertTrue(battery.getLongEnergyStored() <= 960_000L, "Cable retained its fixed passive FE budget");
            ctx.succeed();
        });
    }

    @GameTest(template = "smart_storage_motor", timeoutTicks = 100)
    public static void cableReportsLargeBatteryTotalsAndHonoursDirectPullRequests(GameTestHelper ctx){
        BlockPos origin = new BlockPos(3, 3, 3);
        place(ctx, CTBlocks.SMART_BATTERY.get(), origin, Direction.Axis.X, 3, 9, false);
        ctx.setBlock(origin.offset(-2, 0, 0), CTBlocks.INDUSTRIAL_MOTOR.get());
        ctx.setBlock(origin.west(), CTBlocks.ANDESITE_CABLE.get().defaultBlockState()
                .setValue(PipeBlock.EAST, true).setValue(PipeBlock.WEST, true));
        ctx.runAtTickTime(3, () -> {
            var battery = (SmartBatteryBlockEntity)ctx.getBlockEntity(origin);
            ctx.assertTrue(battery.receiveEnergy(3_000_000_000L, false) == 3_000_000_000L, "Full battery did not accept its long FE capacity");
            var cable = ((AndesiteCableBlockEntity)ctx.getBlockEntity(origin.west())).getEnergyView(null);
            ctx.assertTrue(cable.getEnergyStored() == Integer.MAX_VALUE, "Cable overflowed its large stored-energy total");
            ctx.assertTrue(cable.getMaxEnergyStored() == Integer.MAX_VALUE, "Cable overflowed its capacity total");
            ctx.assertTrue(cable.extractEnergy(1_000_000, true) == 1_000_000, "Cable capped a simulated pull");
            ctx.assertTrue(battery.getLongEnergyStored() == 3_000_000_000L, "Simulated cable pull changed the stored FE");
            ctx.assertTrue(cable.extractEnergy(1_000_000, false) == 1_000_000, "Cable capped a real pull");
            ctx.assertTrue(battery.getLongEnergyStored() == 2_999_000_000L, "Cable pull changed the wrong FE amount");
            ctx.succeed();
        });
    }

    @GameTest(template = "smart_storage_motor", timeoutTicks = 100)
    public static void batteryExtractionHonoursTheFullRequestWithoutAPerTickCap(GameTestHelper ctx){
        BlockPos origin = new BlockPos(3, 3, 3);
        place(ctx, CTBlocks.SMART_BATTERY.get(), origin, Direction.Axis.X, 2, 3, false);
        ctx.runAtTickTime(3, () -> {
            var battery = (SmartBatteryBlockEntity)ctx.getBlockEntity(origin);
            battery.receiveEnergy(10_000_000L, false);
            var handler = ((SmartBatteryBlockEntity)ctx.getBlockEntity(origin.offset(2, 1, 1))).getEnergyHandler();
            ctx.assertTrue(handler.extractEnergy(2_000_000, true) == 2_000_000, "Battery capped a simulated FE request");
            ctx.assertTrue(battery.getLongEnergyStored() == 10_000_000L, "Simulation drained the battery");
            ctx.assertTrue(handler.extractEnergy(2_000_000, false) == 2_000_000, "Battery capped a real FE request");
            ctx.assertTrue(handler.extractEnergy(3_000_000, false) == 3_000_000, "Battery capped a second request in the same tick");
            ctx.assertTrue(battery.getLongEnergyStored() == 5_000_000L, "Battery extraction lost or duplicated FE");
            ctx.succeed();
        });
    }

    @GameTest(template = "smart_storage_motor", timeoutTicks = 100)
    public static void motorPanelUsesBothDirectionRowsAndDrivesSignedSpeed(GameTestHelper ctx){
        BlockPos pos = new BlockPos(4, 3, 4);
        ctx.setBlock(pos, CTBlocks.INDUSTRIAL_MOTOR.get().defaultBlockState());
        var motor = (IndustrialMotorBlockEntity)ctx.getBlockEntity(pos);
        ctx.runAtTickTime(2, () -> {
            var behaviour = BlockEntityBehaviour.get(ctx.getLevel(), motor.getBlockPos(), ScrollValueBehaviour.TYPE);
            ctx.assertTrue(behaviour != null, "Motor lost its value control");
            ctx.assertTrue(behaviour.createBoard(null, null).rows().size() == 2, "Motor panel is missing its direction row");
            var extended = new com.rieno.gadgetsandgizmos.lib.create.ConfigurableKineticScrollValueBehaviour(
                    net.minecraft.network.chat.Component.literal("Speed"), motor, null).between(-1024, 1024);
            ctx.assertTrue(extended.createBoard(null, null).maxValue() == 1024, "Motor-style board ignored its configured range");
            behaviour.setValueSettings(null, new ValueSettings(0, 128), false);
            ctx.assertTrue(motor.getTargetSpeedRPM() == -128, "Negative direction row did not set reverse RPM");
            motor.getEnergyStorage().receiveEnergy(5000, false);
        });
        ctx.runAtTickTime(3, () -> {
            float reverse = motor.getGeneratedSpeed();
            ctx.assertTrue(reverse != 0, "Reverse motor did not run");
            motor.setTargetSpeedRPM(128);
            motor.getEnergyStorage().receiveEnergy(5000, false);
            ctx.runAfterDelay(1, () -> {
                ctx.assertTrue(reverse * motor.getGeneratedSpeed() < 0, "Changing motor direction did not reverse its output");
                ctx.succeed();
            });
        });
    }

    private static void formation(GameTestHelper ctx, boolean battery, Direction.Axis axis, boolean reverse){
        Block block = battery ? CTBlocks.SMART_BATTERY.get() : CTBlocks.SMART_VAULT.get();
        BlockPos origin = new BlockPos(2, 2, 2);
        place(ctx, block, origin, axis, 3, 9, reverse);
        ctx.runAtTickTime(5, () -> {
            assertFormed(ctx, origin, axis, 3, 9);
            ctx.succeed();
        });
    }

    private static void loadedSingles(GameTestHelper ctx, boolean battery){
        Block block = battery ? CTBlocks.SMART_BATTERY.get() : CTBlocks.SMART_VAULT.get();
        BlockPos origin = new BlockPos(2, 2, 2);
        for(BlockPos pos : positions(origin, Direction.Axis.X, 2, 3)){
            var state = block.defaultBlockState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, Direction.Axis.X);
            ctx.getLevel().setBlock(ctx.absolutePos(pos), state, 64);
            var entity = (ItemVaultBlockEntity)ctx.getBlockEntity(pos);
            CompoundTag saved = entity.saveWithFullMetadata(ctx.getLevel().registryAccess());
            saved.remove("Uninitialized");
            entity.loadWithComponents(saved, ctx.getLevel().registryAccess());
            if(battery) ((SmartBatteryBlockEntity)entity).receiveEnergy(1000L, false);
            else ((SmartVaultBlockEntity)entity).getItemHandler().insertItem(0,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND), false);
        }
        ctx.runAtTickTime(5, () -> {
            assertFormed(ctx, origin, Direction.Axis.X, 2, 3);
            if(battery){
                ctx.assertTrue(((SmartBatteryBlockEntity)ctx.getBlockEntity(origin)).getLongEnergyStored() == 12_000L,
                        "Battery formation changed the stored FE");
            }else{
                var inventory = ((SmartVaultBlockEntity)ctx.getBlockEntity(origin)).getItemHandler();
                int items = 0;
                for(int slot = 0; slot < inventory.getSlots(); slot++) items += inventory.getStackInSlot(slot).getCount();
                ctx.assertTrue(items == 12, "Vault formation changed the stored items");
            }
            ctx.succeed();
        });
    }

    private static void place(GameTestHelper ctx, Block block, BlockPos origin, Direction.Axis axis,
                              int width, int length, boolean reverse){
        List<BlockPos> positions = positions(origin, axis, width, length);
        if(reverse) Collections.reverse(positions);
        for(BlockPos pos : positions) ctx.setBlock(pos, block.defaultBlockState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, axis));
    }

    private static List<BlockPos> positions(BlockPos origin, Direction.Axis axis, int width, int length){
        List<BlockPos> positions = new ArrayList<>();
        for(int along = 0; along < length; along++){
            for(int y = 0; y < width; y++){
                for(int across = 0; across < width; across++) positions.add(axis == Direction.Axis.X
                        ? origin.offset(along, y, across) : origin.offset(across, y, along));
            }
        }
        return positions;
    }

    private static void assertFormed(GameTestHelper ctx, BlockPos origin, Direction.Axis axis, int width, int length){
        var controller = (ItemVaultBlockEntity)ctx.getBlockEntity(origin);
        ctx.assertTrue(controller.isController() && controller.getWidth() == width && controller.getHeight() == length,
                "Multiblock did not form: width=" + controller.getWidth() + " length=" + controller.getHeight());
        for(BlockPos pos : positions(origin, axis, width, length)){
            var member = (ItemVaultBlockEntity)ctx.getBlockEntity(pos);
            ctx.assertTrue(member.getControllerBE() == controller, "Member retained a separate controller at " + pos);
        }
    }
}
