package com.rieno.gadgetsandgizmos.compat.computercraft.rdp;

import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity;
import dan200.computercraft.shared.peripheral.monitor.MonitorBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

// Bind native CC internals to the shared terminal snapshot API
public final class ComputerCraftTerminalPreview{
    private ComputerCraftTerminalPreview(){}

    public static CompoundTag frame(BlockEntity target){
        Terminal terminal = null;
        if(target instanceof AbstractComputerBlockEntity computer){
            var server = computer.getServerComputer();
            if(server != null && server.getTerminalState() != null) terminal = server.getTerminalState().create();
        }else if(target instanceof MonitorBlockEntity monitor){
            var server = monitor.getCachedServerMonitor();
            if(server != null) terminal = server.getTerminal();
        }
        var snapshot = terminal == null ? null : snapshot(terminal);
        return snapshot == null ? new CompoundTag() : snapshot.displayFrame();
    }

    public static ScriptTerminal snapshot(Terminal terminal){
        return com.rieno.gadgetsandgizmos.lib.compat.computercraft.ComputerCraftTerminalColors.snapshot(terminal);
    }
}
