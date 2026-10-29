package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.tablet.MountedTabletState;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

// Exercise presentation settings and delayed controller loading against real storage blocks
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class ContainerPresentationGameTests{
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void hiddenTabletsOpenTheirGuiWithEmptyHands(GameTestHelper helper){
        var positions = List.of(new BlockPos(1, 2, 1), new BlockPos(3, 2, 1), new BlockPos(5, 2, 1));
        helper.setBlock(positions.get(0), CTBlocks.SMART_VAULT.get());
        helper.setBlock(positions.get(1), CTBlocks.SMART_BATTERY.get());
        helper.setBlock(positions.get(2), CTBlocks.SMART_TANK.get());
        helper.runAfterDelay(4, () -> {
            var player = new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "container-viewer")){
                @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider,
                        java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> extraData){
                    containerMenu = provider.createMenu(1, getInventory(), this);
                    return java.util.OptionalInt.of(1);
                }
            };
            try{
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                for(var relative : positions){
                    var entity = helper.getBlockEntity(relative); var pos = entity.getBlockPos();
                    player.setPos(Vec3.atCenterOf(pos).add(0, 0, -2));
                    var hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0, -.5), Direction.NORTH, pos, false);
                    helper.assertTrue(!SmartContainerTablets.tabletVisible(entity), "Fixture tablet was already visible");
                    helper.assertTrue(SmartContainerTablets.interact(helper.getLevel(), pos, player, hit), "Hidden tablet rejected an empty hand");
                    helper.assertTrue(player.containerMenu instanceof SmartContainerMenu menu && menu.targetPos.equals(pos) && menu.stillValid(player),
                            "Empty hand did not open the storage GUI for the selected container");
                    helper.assertTrue(!SmartContainerTablets.tabletVisible(entity), "Opening the GUI enabled world tablet rendering");
                    player.closeContainer();
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.WRENCH.get()));
                    helper.assertTrue(!SmartContainerTablets.interact(helper.getLevel(), pos, player, hit), "GUI access consumed wrench interaction");
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
                    helper.assertTrue(!SmartContainerTablets.interact(helper.getLevel(), pos, player, hit), "GUI access consumed bucket interaction");
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    helper.assertTrue(SmartContainerTablets.resolveInteraction(helper.getLevel(), new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0, -2), Direction.NORTH, pos, false)) == null,
                            "Hidden tablet accepted a hit outside the container");
                    SmartContainerTablets.cyclePresentation(entity);
                    var housing = new BlockHitResult(Vec3.atLowerCornerOf(pos).add(.01, .01, 0), Direction.NORTH, pos, false);
                    helper.assertTrue(SmartContainerTablets.resolveInteraction(helper.getLevel(), housing) == null, "Visible tablet stole interaction from its container housing");
                }
                helper.succeed();
            }finally{
                player.closeContainer();
                player.discard();
            }
        });
    }
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void wrenchPresentationPersistsWithoutChangingStorage(GameTestHelper helper){
        var pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, CTBlocks.SMART_TANK.get());
        helper.setBlock(pos.above(), CTBlocks.SMART_TANK.get());
        helper.runAfterDelay(3, () -> {
            var tank = (SmartTankBlockEntity)helper.getBlockEntity(pos);
            tank.getTankInventory().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
            int capacity = tank.getTankInventory().getCapacity();
            helper.assertTrue(tank.getHeight() == 2, "Tank fixture did not form");
            helper.assertTrue(!SmartContainerTablets.tabletVisible(tank) && SmartContainerTablets.contentsVisible(tank), "Tank default changed");
            assertTankWindows(helper, tank, true);
            for(var expected : List.of(MountedTabletState.Presentation.TABLET, MountedTabletState.Presentation.NONE,
                    MountedTabletState.Presentation.BOTH, MountedTabletState.Presentation.CONTENTS)){
                wrench(helper, helper.getBlockEntity(pos.above()));
                helper.assertTrue(SmartContainerTablets.presentation(tank) == expected, "Tank presentation cycle changed");
                assertTankWindows(helper, tank, expected.contents());
                helper.assertTrue(((SmartTankBlockEntity)helper.getBlockEntity(pos.above())).mountedTabletState().presentation() == expected,
                        "Tank member did not save the controller's presentation");
                var saved = tank.saveWithFullMetadata(helper.getLevel().registryAccess());
                var restored = (SmartTankBlockEntity)BlockEntity.loadStatic(tank.getBlockPos(), tank.getBlockState(), saved, helper.getLevel().registryAccess());
                helper.assertTrue(restored != null && restored.mountedTabletState().presentation() == expected, "Presentation was not saved");
                helper.assertTrue(tank.getTankInventory().getCapacity() == capacity, "Presentation changed capacity");
                helper.assertTrue(tank.getTankInventory().getFluidAmount() == 1000, "Presentation lost fluid");
            }
            helper.succeed();
        });
    }

    private static void assertTankWindows(GameTestHelper helper, SmartTankBlockEntity tank, boolean visible){
        helper.assertTrue(Boolean.TRUE.equals(tank.getExtraData()) == visible, "Tank window flag disagrees with its presentation");
        var origin = tank.getController();
        int width = tank.getWidth();
        for(int x = 0; x < width; x++) for(int y = 0; y < tank.getHeight(); y++) for(int z = 0; z < width; z++){
            var shape = helper.getLevel().getBlockState(origin.offset(x, y, z)).getValue(com.simibubi.create.content.fluids.tank.FluidTankBlock.SHAPE);
            var expected = com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape.PLAIN;
            if(visible){
                if(width == 1 || width == 3 && Math.abs(x - z) == 1) expected = com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape.WINDOW;
                if(width == 2) expected = x == 0 ? z == 0 ? com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape.WINDOW_NW
                        : com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape.WINDOW_SW
                        : z == 0 ? com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape.WINDOW_NE
                        : com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape.WINDOW_SE;
            }
            helper.assertTrue(shape == expected, "Tank housing did not follow presentation at " + origin.offset(x, y, z));
        }
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void squareTankWindowsFollowEveryMode(GameTestHelper helper){ tankWindowCycle(helper, 2); }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void wideTankWindowsFollowEveryMode(GameTestHelper helper){ tankWindowCycle(helper, 3); }

    private static void tankWindowCycle(GameTestHelper helper, int width){
        var pos = new BlockPos(1, 2, 1);
        for(int x = 0; x < width; x++) for(int y = 0; y < 2; y++) for(int z = 0; z < width; z++) helper.setBlock(pos.offset(x, y, z), CTBlocks.SMART_TANK.get());
        helper.runAfterDelay(10, () -> {
            var tank = (SmartTankBlockEntity)helper.getBlockEntity(pos);
            helper.assertTrue(tank.isController() && tank.getWidth() == width && tank.getHeight() == 2, "Tank fixture did not form");
            var storage = tank.getTankInventory();
            int capacity = storage.getCapacity();
            storage.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
            storage.fill(new FluidStack(Fluids.LAVA, 2000), FluidAction.EXECUTE);
            assertTankWindows(helper, tank, true);
            for(var mode : List.of(MountedTabletState.Presentation.TABLET, MountedTabletState.Presentation.NONE,
                    MountedTabletState.Presentation.BOTH, MountedTabletState.Presentation.CONTENTS)){
                wrench(helper, helper.getBlockEntity(pos.offset(width - 1, 1, width - 1)));
                assertTankWindows(helper, tank, mode.contents());
                helper.assertTrue(storage.getCapacity() == capacity && storage.getFluidAmount() == 3000, "Window changes affected stored fluids or capacity");
                helper.assertTrue(tank.getWidth() == width && tank.getHeight() == 2, "Window changes broke the multiblock");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void tankReloadRepairsOldHousingInEveryMode(GameTestHelper helper){
        helper.setBlock(new BlockPos(1, 2, 1), CTBlocks.SMART_TANK.get());
        helper.setBlock(new BlockPos(1, 3, 1), CTBlocks.SMART_TANK.get());
        helper.runAfterDelay(5, () -> reloadTankMode(helper, 0));
    }

    private static void reloadTankMode(GameTestHelper helper, int idx){
        var modes = MountedTabletState.Presentation.values();
        if(idx == modes.length){ helper.succeed(); return; }
        var root = (SmartTankBlockEntity)helper.getBlockEntity(new BlockPos(1, 2, 1));
        root.getTankInventory().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        var members = List.copyOf(root.members());
        for(var member : members) ((MountedTabletState.Host)member).mountedTabletState().setPresentation(modes[idx]);
        root.setWindows(!modes[idx].contents());
        int fluid = root.getTankInventory().getFluidAmount(), capacity = root.getTankInventory().getCapacity();
        var saved = members.stream().map(member -> member.saveWithFullMetadata(helper.getLevel().registryAccess())).toList();
        for(var member : members) helper.getLevel().removeBlockEntity(member.getBlockPos());
        for(int member = 0; member < members.size(); member++){
            var pos = members.get(member).getBlockPos();
            helper.getLevel().setBlockEntity(BlockEntity.loadStatic(pos, helper.getLevel().getBlockState(pos), saved.get(member), helper.getLevel().registryAccess()));
        }
        helper.runAfterDelay(5, () -> {
            var loaded = (SmartTankBlockEntity)helper.getBlockEntity(new BlockPos(1, 2, 1));
            assertTankWindows(helper, loaded, modes[idx].contents());
            helper.assertTrue(loaded.getTankInventory().getCapacity() == capacity && loaded.getTankInventory().getFluidAmount() == fluid,
                    "Repairing old tank housing lost storage");
            reloadTankMode(helper, idx + 1);
        });
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void windowUpdatesWaitForSavedMembers(GameTestHelper helper){
        var pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, CTBlocks.SMART_TANK.get()); helper.setBlock(pos.above(), CTBlocks.SMART_TANK.get());
        helper.runAfterDelay(5, () -> {
            var root = (SmartTankBlockEntity)helper.getBlockEntity(pos);
            var member = helper.getBlockEntity(pos.above()); var absolute = member.getBlockPos();
            var saved = member.saveWithFullMetadata(helper.getLevel().registryAccess());
            helper.getLevel().removeBlockEntity(absolute);
            helper.assertTrue(!root.trySetWindows(false), "Window update ran before saved members arrived");
            helper.assertTrue(!helper.getLevel().getChunkAt(absolute).getBlockEntities().containsKey(absolute), "Window update fabricated a saved member");
            helper.assertTrue(Boolean.TRUE.equals(root.getExtraData()), "Deferred window update mutated its controller");
            helper.getLevel().setBlockEntity(BlockEntity.loadStatic(absolute, helper.getLevel().getBlockState(absolute), saved, helper.getLevel().registryAccess()));
            helper.assertTrue(root.trySetWindows(false), "Window update did not resume with loaded members");
            assertTankWindows(helper, root, false);
            helper.succeed();
        });
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 80)
    public static void vaultAndBatteryWrenchKeepStorageAndPersistEveryMember(GameTestHelper helper){
        for(int idx = 0; idx < 3; idx++){
            helper.setBlock(new BlockPos(1 + idx, 2, 1), CTBlocks.SMART_BATTERY.get().defaultBlockState()
                    .setValue(ItemVaultBlock.HORIZONTAL_AXIS, Direction.Axis.X));
            helper.setBlock(new BlockPos(1 + idx, 2, 4), CTBlocks.SMART_VAULT.get().defaultBlockState()
                    .setValue(ItemVaultBlock.HORIZONTAL_AXIS, Direction.Axis.X));
        }
        helper.runAfterDelay(10, () -> {
            var battery = (SmartBatteryBlockEntity)helper.getBlockEntity(new BlockPos(1, 2, 1));
            var vault = (SmartVaultBlockEntity)helper.getBlockEntity(new BlockPos(1, 2, 4));
            battery.receiveEnergy(2_000_000, false);
            vault.getItemHandler().insertItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 17), false);
            long energy = battery.getLongEnergyStored(), capacity = battery.getLongCapacity();
            int slots = vault.getItemHandler().getSlots();
            for(var expected : List.of(MountedTabletState.Presentation.TABLET, MountedTabletState.Presentation.NONE)){
                wrench(helper, helper.getBlockEntity(new BlockPos(3, 2, 1)));
                wrench(helper, helper.getBlockEntity(new BlockPos(3, 2, 4)));
                for(int idx = 0; idx < 3; idx++) for(int z : new int[]{1, 4}){
                    var member = helper.getBlockEntity(new BlockPos(1 + idx, 2, z));
                    helper.assertTrue(((MountedTabletState.Host)member).mountedTabletState().presentation() == expected,
                            "Wrench did not update every multiblock member");
                    var restored = BlockEntity.loadStatic(member.getBlockPos(), member.getBlockState(),
                            member.saveWithFullMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
                    helper.assertTrue(((MountedTabletState.Host)restored).mountedTabletState().presentation() == expected, "Saved member lost visibility");
                }
                helper.assertTrue(battery.getLongEnergyStored() == energy && battery.getLongCapacity() == capacity, "Visibility changed battery storage");
                helper.assertTrue(vault.getItemHandler().getSlots() == slots && vault.getItemHandler().getStackInSlot(0).getCount() == 17,
                        "Visibility changed vault storage");
                helper.assertTrue(SmartContainerTablets.panels(vault).isEmpty() == !expected.tablet(), "Hidden tablet retained its hit targets");
            }
            helper.succeed();
        });
    }

    private static void wrench(GameTestHelper helper, BlockEntity target){
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.WRENCH.get()));
        var hit = new BlockHitResult(Vec3.atCenterOf(target.getBlockPos()), Direction.NORTH, target.getBlockPos(), false);
        var result = ((IWrenchable)target.getBlockState().getBlock()).onWrenched(target.getBlockState(),
                new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(result.consumesAction(), "Wrench presentation was rejected");
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void joinedMembersKeepPresentationWhenControllerIsRemoved(GameTestHelper helper){
        var batteryPos = new BlockPos(1, 2, 1);
        var vaultPos = new BlockPos(1, 2, 4);
        var tankPos = new BlockPos(4, 2, 4);
        var battery = CTBlocks.SMART_BATTERY.get().defaultBlockState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, Direction.Axis.X);
        var vault = CTBlocks.SMART_VAULT.get().defaultBlockState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, Direction.Axis.X);
        helper.setBlock(batteryPos, battery); helper.setBlock(vaultPos, vault); helper.setBlock(tankPos, CTBlocks.SMART_TANK.get());
        helper.runAfterDelay(3, () -> {
            for(var pos : List.of(batteryPos, vaultPos, tankPos)) wrench(helper, helper.getBlockEntity(pos));
            helper.setBlock(batteryPos.east(), battery); helper.setBlock(vaultPos.east(), vault); helper.setBlock(tankPos.above(), CTBlocks.SMART_TANK.get());
            helper.runAfterDelay(25, () -> {
                for(var pos : List.of(batteryPos.east(), vaultPos.east(), tankPos.above())){
                    helper.assertTrue(((MountedTabletState.Host)helper.getBlockEntity(pos)).mountedTabletState().presentation() == MountedTabletState.Presentation.TABLET,
                            "Joined member did not inherit the saved presentation");
                    if(helper.getBlockEntity(pos) instanceof SmartTankBlockEntity tank) assertTankWindows(helper, (SmartTankBlockEntity)SmartContainerTablets.controller(tank), false);
                }
                for(var pos : List.of(batteryPos, vaultPos, tankPos)) helper.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR);
                helper.runAfterDelay(25, () -> {
                    for(var pos : List.of(batteryPos.east(), vaultPos.east(), tankPos.above())){
                        var member = helper.getBlockEntity(pos);
                        helper.assertTrue(SmartContainerTablets.controller(member) == member && SmartContainerTablets.tabletVisible(member),
                                "Controller removal left a broken multiblock or lost its visibility");
                        if(member instanceof SmartTankBlockEntity tank) assertTankWindows(helper, tank, false);
                    }
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 120)
    public static void batteryReloadWaitsForSavedController(GameTestHelper helper){
        batteryReload(helper, Direction.Axis.X, true);
    }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 120)
    public static void batteryReloadWaitsForSavedMember(GameTestHelper helper){
        batteryReload(helper, Direction.Axis.Z, false);
    }

    private static BlockPos batteryPos(int idx, Direction.Axis axis){
        return new BlockPos(1 + (axis == Direction.Axis.X ? idx : 0), 2, 1 + (axis == Direction.Axis.Z ? idx : 0));
    }

    private static void batteryReload(GameTestHelper helper, Direction.Axis axis, boolean rootLast){
        for(int idx = 0; idx < 3; idx++) helper.setBlock(batteryPos(idx, axis),
                CTBlocks.SMART_BATTERY.get().defaultBlockState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, axis));
        helper.runAfterDelay(10, () -> {
            var first = (SmartBatteryBlockEntity)helper.getBlockEntity(batteryPos(0, axis));
            var root = (SmartBatteryBlockEntity)first.getControllerBE();
            helper.assertTrue(root != null && root.getHeight() == 3, "Battery fixture did not form");
            long capacity = root.getLongCapacity(); root.receiveEnergy(capacity - 1234L, false);
            long energy = root.getLongEnergyStored();
            var positions = new ArrayList<BlockPos>(); var saved = new ArrayList<net.minecraft.nbt.CompoundTag>();
            for(int idx = 0; idx < 3; idx++){
                var member = (SmartBatteryBlockEntity)helper.getBlockEntity(batteryPos(idx, axis));
                positions.add(member.getBlockPos()); saved.add(member.saveWithFullMetadata(helper.getLevel().registryAccess()));
            }
            BlockPos rootPos = root.getBlockPos();
            BlockPos pending = rootLast ? rootPos : positions.getLast();
            for(var pos : positions) helper.getLevel().removeBlockEntity(pos);
            for(int idx = 0; idx < positions.size(); idx++){
                var pos = positions.get(idx); if(pos.equals(pending)) continue;
                helper.getLevel().setBlockEntity(BlockEntity.loadStatic(pos, helper.getLevel().getBlockState(pos), saved.get(idx), helper.getLevel().registryAccess()));
            }
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(!helper.getLevel().getChunkAt(pending).getBlockEntities().containsKey(pending),
                        "Reload fabricated an empty member before its saved data arrived");
                for(int idx = 0; idx < positions.size(); idx++) if(positions.get(idx).equals(pending)){
                    helper.getLevel().setBlockEntity(BlockEntity.loadStatic(pending, helper.getLevel().getBlockState(pending), saved.get(idx), helper.getLevel().registryAccess()));
                }
                helper.runAfterDelay(10, () -> {
                    var loaded = (SmartBatteryBlockEntity)helper.getLevel().getBlockEntity(rootPos);
                    helper.assertTrue(loaded != null && loaded.getLongCapacity() == capacity, "Battery reload lost multiblock capacity");
                    helper.assertTrue(loaded.getLongEnergyStored() == energy, "Battery reload lost or duplicated energy");
                    for(var pos : positions){
                        var member = (SmartBatteryBlockEntity)helper.getLevel().getBlockEntity(pos);
                        helper.assertTrue(member.getControllerBE() == loaded, "Battery reload retained a broken controller link");
                    }
                    helper.succeed();
                });
            });
        });
    }
}
