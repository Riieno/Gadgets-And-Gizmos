package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

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

// Connect wireless ComputerCraft computers to the tablet remote desktop
public final class ComputerCraftRemoteDesktop {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the computer craft remote desktop
    private ComputerCraftRemoteDesktop() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is available
    public static boolean isAvailable(BlockEntity target) {
        if (!(target instanceof AbstractComputerBlockEntity computer)) return false;
        Level level = computer.getLevel();
        if (level == null) return false;
        for (Direction dir : Direction.values()) {
            BlockEntity adjacent = level.getBlockEntity(computer.getBlockPos().relative(dir));
            if (!(adjacent instanceof WirelessModemBlockEntity modem)) continue;
            IPeripheral peripheral = modem.getPeripheral(dir.getOpposite());
            if (peripheral instanceof ModemPeripheral modemPeripheral
                    && modemPeripheral.isWireless() && modemPeripheral.getModemState().isOpen()) {
                return true;
            }
        }
        return false;
    }

    // Open the computer craft remote desktop
    public static boolean open(ServerPlayer player, BlockEntity target) {
        if (player == null || !isAvailable(target) || !(target instanceof AbstractComputerBlockEntity computer)) {
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
