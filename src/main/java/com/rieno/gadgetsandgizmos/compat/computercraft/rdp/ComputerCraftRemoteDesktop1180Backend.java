package com.rieno.gadgetsandgizmos.compat.computercraft.rdp;

import dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.network.container.ComputerContainerData;
import dan200.computercraft.shared.platform.PlatformHelper;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.shared.peripheral.modem.ModemPeripheral;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessModemBlockEntity;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessModemPeripheral;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;

// Adapt the native computer-menu ABI shared by CC:Tweaked 1.118 and 1.120
public final class ComputerCraftRemoteDesktop1180Backend
        implements ComputerCraftRemoteDesktopBackend {
    @Override
    public boolean supports(BlockEntity target){
        return target instanceof AbstractComputerBlockEntity && target.getLevel() != null && !target.isRemoved();
    }

    @Override
    public boolean isAvailable(BlockEntity target){
        if(!supports(target)) return false;
        var computer = (AbstractComputerBlockEntity)target;
        var server = computer.getServerComputer();
        if(server != null) for(var side : ComputerSide.values()){
            var peripheral = server.getPeripheral(side);
            if(peripheral instanceof WirelessModemPeripheral || peripheral instanceof ModemPeripheral modem && modem.isWireless()) return true;
        }
        for(var dir : Direction.values()){
            var adjacent = target.getLevel().getBlockEntity(target.getBlockPos().relative(dir));
            if(adjacent instanceof WirelessModemBlockEntity modem && modem.getPeripheral(dir.getOpposite()) != null) return true;
        }
        return false;
    }

    @Override
    public boolean open(ServerPlayer player, BlockEntity target) {
        if (player == null || !isAvailable(target)
                || !(target instanceof AbstractComputerBlockEntity computer) || !computer.isUsable(player)) {
            return false;
        }
        ServerComputer serverComputer = computer.createServerComputer();
        serverComputer.turnOn();
        ItemStack displayStack = new ItemStack(computer.getBlockState().getBlock());
        displayStack.applyComponents(computer.collectComponents());
        PlatformHelper.get().openMenu(player, computer.getName(), computer,
                new ComputerContainerData(serverComputer, displayStack));
        return true;
    }
}
