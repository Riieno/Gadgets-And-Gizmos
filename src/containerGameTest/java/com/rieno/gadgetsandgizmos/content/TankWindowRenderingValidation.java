package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.tablet.MountedTabletState.Presentation;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import net.minecraft.client.Minecraft;

// Check the server's saved mode reaches both the client's block model and tank renderer
final class TankWindowRenderingValidation{
    private static long started;
    private static int phase;

    static void begin(){
        phase = 0; started = System.nanoTime();
        select(Presentation.NONE);
    }

    static boolean finish(){
        if(System.nanoTime() - started > 10_000_000_000L) throw new AssertionError("Tank housing did not synchronize at phase " + phase);
        var mc = Minecraft.getInstance();
        var pos = HybridHologramValidation.TANK;
        if(!(mc.level.getBlockEntity(pos) instanceof SmartTankBlockEntity tank)) return false;
        var expected = phase == 1 ? Presentation.CONTENTS : Presentation.NONE;
        if(SmartContainerTablets.presentation(tank) != expected) return false;
        if(Boolean.TRUE.equals(tank.getExtraData()) != expected.contents()) return false;
        var shape = mc.level.getBlockState(pos).getValue(FluidTankBlock.SHAPE);
        if(shape != (expected.contents() ? FluidTankBlock.Shape.WINDOW : FluidTankBlock.Shape.PLAIN)) return false;
        if(phase == 0){ phase = 1; select(Presentation.CONTENTS); return false; }
        if(phase == 1){
            phase = 2; select(Presentation.NONE);
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                player.connection.teleport(pos.getX() + .5, pos.getY(), pos.getZ() - 2, 0, 0);
                var hit = new net.minecraft.world.phys.BlockHitResult(pos.getCenter().add(0, 0, -.5), net.minecraft.core.Direction.NORTH, pos, false);
                if(!SmartContainerTablets.interact(server.overworld(), pos, player, hit)) throw new AssertionError("Hidden tank GUI rejected the player");
            });
            return false;
        }
        if(!(mc.player.containerMenu instanceof SmartContainerMenu) || mc.screen == null) return false;
        mc.setScreen(null); mc.player.closeContainer();
        return true;
    }

    private static void select(Presentation mode){
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            var tank = (SmartTankBlockEntity)server.overworld().getBlockEntity(HybridHologramValidation.TANK);
            for(int idx = 0; idx < 4 && SmartContainerTablets.presentation(tank) != mode; idx++) SmartContainerTablets.cyclePresentation(tank);
        });
    }
}
