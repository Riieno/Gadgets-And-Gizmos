package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Exercise custom vault formation and splitting with other vault mods installed
@GameTestHolder("createthrusters_smart_storage")
@PrefixGameTestTemplate(false)
public final class SmartStorageConnectivityGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Form an east-west smart vault through its normal placement lifecycle
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void vaultFormsAlongX(GameTestHelper helper){
        formation(helper, CTBlocks.SMART_VAULT.get(), Direction.Axis.X);
    }

    // Form a north-south smart vault through its normal placement lifecycle
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void vaultFormsAlongZ(GameTestHelper helper){
        formation(helper, CTBlocks.SMART_VAULT.get(), Direction.Axis.Z);
    }

    // Form an east-west smart battery through its normal placement lifecycle
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void batteryFormsAlongX(GameTestHelper helper){
        formation(helper, CTBlocks.SMART_BATTERY.get(), Direction.Axis.X);
    }

    // Form a north-south smart battery through its normal placement lifecycle
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void batteryFormsAlongZ(GameTestHelper helper){
        formation(helper, CTBlocks.SMART_BATTERY.get(), Direction.Axis.Z);
    }

    // Expose the full battery capacity without truncating FE to an integer
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fullBatteryRetainsFiveBillionEnergy(GameTestHelper helper){
        formation(helper, CTBlocks.SMART_BATTERY.get(), Direction.Axis.X, 3, 9);
    }

    // Reconnect saved vault members when their controller loads several ticks later
    @GameTest(template = "empty", timeoutTicks = 150)
    public static void vaultReloadPreservesItems(GameTestHelper helper){
        reload(helper, CTBlocks.SMART_VAULT.get());
    }

    // Reconnect saved battery members without replacing their stored energy
    @GameTest(template = "empty", timeoutTicks = 150)
    public static void batteryReloadPreservesEnergy(GameTestHelper helper){
        reload(helper, CTBlocks.SMART_BATTERY.get());
    }

    // Preserve Create's original vault formation alongside custom vaults
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void createVaultStillForms(GameTestHelper helper){
        formation(helper, AllBlocks.ITEM_VAULT.get(), Direction.Axis.X);
    }

    // Split both surviving ends and preserve their local item inventories
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void brokenVaultSplitsWithoutLosingItems(GameTestHelper helper){
        split(helper, CTBlocks.SMART_VAULT.get(), Direction.Axis.X);
    }

    // Split both surviving ends and preserve their local FE storage
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void brokenBatterySplitsWithoutLosingEnergy(GameTestHelper helper){
        split(helper, CTBlocks.SMART_BATTERY.get(), Direction.Axis.Z);
    }

    // Keep touching storage types in separate controller groups
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void differentStorageTypesStaySeparate(GameTestHelper helper){
        var vault = place(helper, CTBlocks.SMART_VAULT.get(), Direction.Axis.X, 1, 2);
        var battery = place(helper, CTBlocks.SMART_BATTERY.get(), Direction.Axis.X, 2, 2);
        var standard = place(helper, AllBlocks.ITEM_VAULT.get(), Direction.Axis.X, 3, 2);
        helper.runAfterDelay(30, () -> {
            for(var member : new ItemVaultBlockEntity[]{vault, battery, standard}){
                helper.assertTrue(member.isController() && member.getHeight() == 1 && member.getWidth() == 1,
                        "Different storage types joined one multiblock");
            }
            helper.succeed();
        });
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Verify the complete cross-section, shared controller and pooled capacity
    private static void formation(GameTestHelper helper, Block block, Direction.Axis axis){
        formation(helper, block, axis, 2, 3);
    }

    // Verify both normal cross-sections and the largest supported vault geometry
    private static void formation(GameTestHelper helper, Block block, Direction.Axis axis, int size, int depth){
        BlockPos origin = new BlockPos(1, 2, 1);
        for(int length = 0; length < depth; length++){
            for(int y = 0; y < size; y++){
                for(int width = 0; width < size; width++){
                    BlockPos pos = origin.offset(axis == Direction.Axis.X ? length : width, y,
                            axis == Direction.Axis.Z ? length : width);
                    helper.setBlock(pos, block.defaultBlockState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, axis));
                }
            }
        }
        helper.runAfterDelay(30, () -> {
            var root = (ItemVaultBlockEntity)helper.getBlockEntity(origin);
            helper.assertTrue(root.isController() && root.getWidth() == size && root.getHeight() == depth,
                    "Vault did not form the expected multiblock: " + root.getWidth() + "x" + root.getHeight());
            BlockPos opposite = origin.offset(axis == Direction.Axis.X ? depth - 1 : size - 1, size - 1,
                    axis == Direction.Axis.Z ? depth - 1 : size - 1);
            for(BlockPos pos : BlockPos.betweenClosed(origin, opposite)){
                var member = (ItemVaultBlockEntity)helper.getBlockEntity(pos);
                helper.assertTrue(member.getControllerBE() == root, "Member did not resolve the shared controller");
                helper.assertTrue(helper.getBlockState(pos).getValue(ItemVaultBlock.LARGE) == (size > 2),
                        "Member retained the wrong large-vault model state");
            }
            if(root instanceof SmartVaultBlockEntity vault){
                helper.assertTrue(vault.getItemHandler().getSlots() == vault.getInventoryOfBlock().getSlots() * size * size * depth,
                        "Formed vault did not expose all member inventories");
            }
            if(root instanceof SmartBatteryBlockEntity battery){
                long capacity = size == 3 ? 5_000_000_000L : 696_125_000L;
                helper.assertTrue(battery.getLongCapacity() == capacity,
                        "Formed battery did not expose all member capacities");
                helper.assertTrue(battery.receiveEnergy(capacity, false) == capacity
                                && battery.getLongEnergyStored() == capacity
                                && battery.extractEnergy(capacity, false) == capacity,
                        "Formed battery could not store and extract its exact FE capacity");
            }
            helper.succeed();
        });
    }

    // Reload non-controller members first and wait for the original controller data
    private static void reload(GameTestHelper helper, Block block){
        BlockPos origin = new BlockPos(1, 2, 1);
        var first = place(helper, block, Direction.Axis.X, 1, 1);
        var last = place(helper, block, Direction.Axis.X, 2, 1);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(last.getControllerBE() == first && first.getHeight() == 2,
                    "Reload fixture did not form its initial controller");
            if(first instanceof SmartVaultBlockEntity){
                last.getInventoryOfBlock().setStackInSlot(0, new ItemStack(Items.DIAMOND, 13));
            }else{
                ((SmartBatteryBlockEntity)first).receiveEnergy(12_345L, false);
            }
            CompoundTag rootData = first.saveWithFullMetadata(helper.getLevel().registryAccess());
            CompoundTag memberData = last.saveWithFullMetadata(helper.getLevel().registryAccess());
            helper.getLevel().removeBlockEntity(first.getBlockPos());
            helper.getLevel().removeBlockEntity(last.getBlockPos());
            var restoredMember = restore(helper, origin.east(), memberData);
            helper.assertTrue(restoredMember.getControllerBE() == null,
                    "An unloaded controller was replaced by an empty block entity");
            helper.runAfterDelay(5, () -> {
                var restoredRoot = restore(helper, origin, rootData);
                helper.runAfterDelay(30, () -> {
                    helper.assertTrue(restoredMember.getControllerBE() == restoredRoot && restoredRoot.getHeight() == 2,
                            "Saved members did not reconnect to their original controller");
                    if(restoredRoot instanceof SmartVaultBlockEntity vault){
                        helper.assertTrue(vault.getItemHandler().getStackInSlot(first.getInventoryOfBlock().getSlots()).getCount() == 13,
                                "Reloading lost the non-controller item inventory");
                    }else{
                        helper.assertTrue(((SmartBatteryBlockEntity)restoredRoot).getLongEnergyStored() == 12_345L,
                                "Reloading lost the original battery energy");
                    }
                    helper.succeed();
                });
            });
        });
    }

    // Install saved data without asking the world to create a replacement block entity
    private static ItemVaultBlockEntity restore(GameTestHelper helper, BlockPos pos, CompoundTag data){
        BlockPos abs = helper.absolutePos(pos);
        var restored = (ItemVaultBlockEntity)BlockEntity.loadStatic(abs, helper.getBlockState(pos), data,
                helper.getLevel().registryAccess());
        helper.getLevel().setBlockEntity(restored);
        return restored;
    }

    // Seed an existing controller so breaking is tested independently of initial formation
    private static void split(GameTestHelper helper, Block block, Direction.Axis axis){
        var first = place(helper, block, axis, 1, 1);
        var middle = place(helper, block, axis, axis == Direction.Axis.X ? 2 : 1, axis == Direction.Axis.Z ? 2 : 1);
        var last = place(helper, block, axis, axis == Direction.Axis.X ? 3 : 1, axis == Direction.Axis.Z ? 3 : 1);
        first.setWidth(1);
        first.setHeight(3);
        for(var member : new ItemVaultBlockEntity[]{first, middle, last}){
            member.setController(first.getBlockPos());
            member.preventConnectivityUpdate();
        }
        if(first instanceof SmartVaultBlockEntity){
            first.getInventoryOfBlock().setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
            last.getInventoryOfBlock().setStackInSlot(0, new ItemStack(Items.EMERALD, 9));
        }else{
            ((SmartBatteryBlockEntity)first).receiveEnergy(1_000L, false);
        }
        helper.destroyBlock(new BlockPos(axis == Direction.Axis.X ? 2 : 1, 2, axis == Direction.Axis.Z ? 2 : 1));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(first.isController() && last.isController()
                            && first.getHeight() == 1 && last.getHeight() == 1,
                    "Broken vault retained its old controller geometry");
            if(first instanceof SmartVaultBlockEntity){
                helper.assertTrue(first.getInventoryOfBlock().getStackInSlot(0).getCount() == 7
                                && last.getInventoryOfBlock().getStackInSlot(0).getCount() == 9,
                        "Splitting changed the surviving item inventories");
            }else{
                helper.assertTrue(((SmartBatteryBlockEntity)first).getLongEnergyStored() == 1_000L,
                        "Splitting lost the surviving battery energy");
            }
            helper.succeed();
        });
    }

    // Place one vault member with an explicit horizontal connection axis
    private static ItemVaultBlockEntity place(GameTestHelper helper, Block block, Direction.Axis axis, int x, int z){
        BlockPos pos = new BlockPos(x, 2, z);
        helper.setBlock(pos, block.defaultBlockState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, axis));
        return (ItemVaultBlockEntity)helper.getBlockEntity(pos);
    }
}
