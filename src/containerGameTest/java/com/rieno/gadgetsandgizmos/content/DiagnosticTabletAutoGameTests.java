package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.inventory.ContainerNetworkSnapshot;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting;
import com.rieno.gadgetsandgizmos.lib.shipping.LogisticsBindings;
import com.rieno.gadgetsandgizmos.lib.shipping.LogisticsBindings.Group;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

// Verify the Auto manifest reads authored ship resources without moving cargo or fuel
@GameTestHolder("createthrusters_physics_regressions")
@PrefixGameTestTemplate(false)
public final class DiagnosticTabletAutoGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @GameTest(template = "empty", batch = "tablet_auto")
    public static void cargoAndFuelStaySeparateAndIncludeConsumerBuffers(GameTestHelper helper){
        var accPos = new BlockPos(1, 2, 1);
        var cargoPos = new BlockPos(3, 2, 1);
        var fuelPos = new BlockPos(5, 2, 1);
        var furnacePos = new BlockPos(7, 2, 1);
        helper.setBlock(accPos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        helper.setBlock(cargoPos, Blocks.CHEST.defaultBlockState());
        helper.setBlock(fuelPos, Blocks.CHEST.defaultBlockState());
        helper.setBlock(furnacePos, Blocks.FURNACE.defaultBlockState());
        var acc = (AdvancedContraptionControllerBlockEntity)helper.getBlockEntity(accPos);
        var cargo = (ChestBlockEntity)helper.getBlockEntity(cargoPos);
        var fuel = (ChestBlockEntity)helper.getBlockEntity(fuelPos);
        var furnace = (FurnaceBlockEntity)helper.getBlockEntity(furnacePos);
        cargo.setItem(0, new ItemStack(Items.IRON_INGOT, 17));
        fuel.setItem(0, new ItemStack(Items.COAL, 12));
        furnace.setItem(1, new ItemStack(Items.COAL, 3));
        var config = ScmConfigurationProfile.empty();
        var bindings = new LogisticsBindings();
        bindings.assign(Group.ITEMS, List.of(new LogisticsBindings.Endpoint(null, helper.absolutePos(cargoPos), null)));
        bindings.assign(Group.FUEL, List.of(new LogisticsBindings.Endpoint(null, helper.absolutePos(fuelPos), null)));
        bindings.assign(Group.FUEL_CONSUMER, List.of(new LogisticsBindings.Endpoint(null, helper.absolutePos(furnacePos), null)));
        config.setLogistics(bindings);
        acc.setScmConfigurationProfile(config);
        var cargoRows = DiagnosticTabletAutoApp.resourceSnapshot(acc, "items", "", 0).getList("Rows", Tag.TAG_COMPOUND);
        var fuelRows = DiagnosticTabletAutoApp.resourceSnapshot(acc, "fuel", "", 0).getList("Rows", Tag.TAG_COMPOUND);
        helper.assertTrue(cargoRows.size() == 1 && cargoRows.getCompound(0).getLong("Amount") == 17
                && cargoRows.getCompound(0).getString("Id").equals("minecraft:iron_ingot"), "Cargo group included unrelated resources");
        helper.assertTrue(fuelRows.size() == 1 && fuelRows.getCompound(0).getLong("Amount") == 15,
                "Fuel reserves and consumer buffers were not combined");
        helper.assertTrue(fuel.getItem(0).getCount() == 12 && furnace.getItem(1).getCount() == 3,
                "Inspecting fuel changed the ship's storage");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tablet_auto")
    public static void largeCargoKeepsFluidsVisibleAndCanReachEveryItem(GameTestHelper helper){
        var items = new ItemStackHandler(70);
        var registered = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).limit(70).toList();
        for(int idx = 0; idx < registered.size(); idx++) items.setStackInSlot(idx, new ItemStack(registered.get(idx), 2));
        var tank = new FluidTank(4000);
        tank.fill(new FluidStack(Fluids.WATER, 1200), IFluidHandler.FluidAction.EXECUTE);
        var pool = new ResourceRouting.Pool(List.of(items, items), List.of(tank, tank), List.of());
        var first = ContainerNetworkSnapshot.capturePool(pool, "item", "", 0, 32);
        var last = ContainerNetworkSnapshot.capturePool(pool, "item", "", 64, 32);
        var fluids = ContainerNetworkSnapshot.capturePool(pool, "fluid", "water", 0, 32);
        helper.assertTrue(first.getInt("Total") == 70 && last.getInt("Offset") == 64
                && last.getList("Rows", Tag.TAG_COMPOUND).size() == 6, "Cargo pagination omitted late entries");
        helper.assertTrue(fluids.getInt("Total") == 1 && fluids.getList("Rows", Tag.TAG_COMPOUND).getCompound(0).getLong("Amount") == 1200,
                "Large item cargo hid or duplicated the ship's fluids");
        helper.assertTrue(tank.getFluidAmount() == 1200 && items.getStackInSlot(0).getCount() == 2,
                "Manifest capture moved resources");
        helper.succeed();
    }
}
