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
        synchronized(terminal){
            return ScriptTerminal.capture(new ScriptTerminal.Access(){
                @Override public int width(){ return terminal.getWidth(); }
                @Override public int height(){ return terminal.getHeight(); }
                @Override public String text(int row){ return terminal.getLine(row).toString(); }
                @Override public String foreground(int row){ return terminal.getTextColourLine(row).toString(); }
                @Override public String background(int row){ return terminal.getBackgroundColourLine(row).toString(); }
                @Override public int color(int index){ return terminal.getPalette().getRenderColours(15 - index); }
                @Override public int cursorX(){ return terminal.getCursorX(); }
                @Override public int cursorY(){ return terminal.getCursorY(); }
                @Override public boolean blink(){ return terminal.getCursorBlink(); }
                @Override public int cursorColor(){ return terminal.getTextColour(); }
            });
        }
    }
}
