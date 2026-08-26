package com.rieno.gadgetsandgizmos.compat.computercraft.rdp;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.network.container.ComputerContainerData;
import dan200.computercraft.shared.peripheral.modem.ModemPeripheral;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessModemBlockEntity;
import dan200.computercraft.shared.platform.PlatformHelper;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;

// Implement the CC:Tweaked 1.118.x native computer-menu bridge
public final class ComputerCraftRemoteDesktop1180Backend
        implements ComputerCraftRemoteDesktopBackend {
    @Override
    public boolean isAvailable(BlockEntity target) {
        if (!(target instanceof AbstractComputerBlockEntity computer)) return false;
        Level level = computer.getLevel();
        if (level == null) return false;
        for (Direction direction : Direction.values()) {
            BlockEntity adjacent = level.getBlockEntity(
                    computer.getBlockPos().relative(direction));
            if (!(adjacent instanceof WirelessModemBlockEntity modem)) continue;
            IPeripheral peripheral = modem.getPeripheral(direction.getOpposite());
            if (peripheral instanceof ModemPeripheral modemPeripheral
                    && modemPeripheral.isWireless()
                    && modemPeripheral.getModemState().isOpen()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean open(ServerPlayer player, BlockEntity target) {
        if (player == null || !isAvailable(target)
                || !(target instanceof AbstractComputerBlockEntity computer)) {
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
