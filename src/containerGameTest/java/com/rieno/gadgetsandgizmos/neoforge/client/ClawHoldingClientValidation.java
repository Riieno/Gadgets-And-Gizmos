package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.ClawBlock;
import com.rieno.gadgetsandgizmos.content.ClawBlockEntity;
import com.rieno.gadgetsandgizmos.lib.entity.HangingEntityMotion;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

// Exercise real client-owned player movement alongside server-owned mob holding
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class ClawHoldingClientValidation{
    private static final String OUTPUT = System.getProperty("gadgetsngizmos.clawHoldingValidation");
    private static final BlockPos PLAYER_CLAW = new BlockPos(0, 122, 0);
    private static final BlockPos OPEN_CLAW = new BlockPos(4, 122, 0);
    private static final BlockPos MOB_CLAW = new BlockPos(8, 122, 0);
    private static boolean hidden, installed, complete, prevGrab;
    private static volatile boolean prepared, released, readyForImage;
    private static volatile Throwable failure;
    private static int ticks, imageTicks;
    private static long started;
    private static double minimumY = Double.POSITIVE_INFINITY, maximumY = Double.NEGATIVE_INFINITY;
    private static Cow cow;

    @SubscribeEvent public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if(!hidden){ hidden = true; GLFW.glfwHideWindow(mc.getWindow().getWindow()); started = System.nanoTime(); }
        try{
            if(System.nanoTime() - started > 180_000_000_000L) throw new AssertionError("Claw client validation timed out");
            if(failure != null) throw new AssertionError("Server fixture failed", failure);
            if(mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
            mc.options.pauseOnLostFocus = false;
            mc.setScreen(null);
            if(!installed){ installed = true; install(mc); return; }
            if(!prepared) return;
            ticks++;
            if(ticks >= 90 && ticks < 120){
                var claw = (ClawBlockEntity)mc.level.getBlockEntity(PLAYER_CLAW);
                check(claw != null && claw.isHoldingEntity(), "The client lost its held player binding");
                Vec3 target = HangingEntityMotion.feetBelowGrip(claw.getEntityGripPosition(), mc.player.getBbHeight(), 0.125D);
                check(HangingEntityMotion.worldFeet(mc.player).distanceTo(target) < 0.55D, "The local player did not settle below the Claw");
                check(mc.player.getY() + mc.player.getBbHeight() < claw.getEntityGripPosition().y, "The player overlaps the Claw");
                minimumY = Math.min(minimumY, mc.player.getY());
                maximumY = Math.max(maximumY, mc.player.getY());
            }
            if(ticks == 120){
                check(maximumY - minimumY < 0.08D, "The held player's client position is jittering");
                mc.getSingleplayerServer().execute(() -> validateAndRelease(mc));
            }
            if(released && ticks >= 145 && !readyForImage){
                var claw = (ClawBlockEntity)mc.level.getBlockEntity(PLAYER_CLAW);
                check(claw != null && !claw.isHoldingEntity(), "Release was not synchronized to the client");
                check(mc.player.getY() < 119.0D, "The released player is still suspended");
                readyForImage = true;
                mc.getSingleplayerServer().execute(() -> {
                    try{
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        player.setGameMode(GameType.SPECTATOR);
                        player.connection.teleport(4.5D, 121.0D, -7.5D, 0.0F, 8.0F);
                    }catch(Throwable err){ failure = err; }
                });
            }
            if(readyForImage && ++imageTicks >= 20){
                try(var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                    image.writeToFile(Path.of(OUTPUT).resolve("claw-open-closed.png"));
                }
                finish("PASSED real player client prediction, stable head clearance, mob holding, synchronized release and matching open/closed jaw states; player jitter "
                        + (maximumY - minimumY) + " blocks\n");
            }
        }catch(Throwable err){
            var trace = new StringWriter();
            err.printStackTrace(new PrintWriter(trace));
            finish("FAILED\n" + trace);
        }
    }

    private static void install(Minecraft mc){
        mc.getSingleplayerServer().execute(() -> {
            try{
                var level = mc.getSingleplayerServer().overworld();
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                prevGrab = CTConfigs.COMMON.clawCanGrabEntities.get();
                CTConfigs.COMMON.clawCanGrabEntities.set(true);
                level.getEntitiesOfClass(Cow.class, new AABB(-3, 117, -3, 12, 125, 4)).forEach(Cow::discard);
                for(int x = -3; x <= 11; x++) for(int z = -10; z <= 3; z++){
                    level.setBlock(new BlockPos(x, 116, z), Blocks.STONE.defaultBlockState(), 3);
                    for(int y = 117; y <= 125; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
                for(BlockPos pos : new BlockPos[]{PLAYER_CLAW, OPEN_CLAW, MOB_CLAW}){
                    level.setBlock(pos, CTBlocks.CLAW.get().defaultBlockState().setValue(ClawBlock.FACING, Direction.DOWN), 3);
                }
                player.setGameMode(GameType.SURVIVAL);
                player.connection.teleport(0.5D, 120.25D, 0.5D, 0.0F, 0.0F);
                player.setDeltaMovement(Vec3.ZERO);
                cow = new Cow(EntityType.COW, level){
                    @Override protected void registerGoals(){}
                };
                cow.setPos(8.5D, 120.25D, 0.5D);
                level.addFreshEntity(cow);
                claw(level, PLAYER_CLAW).setComputerSignalOverride(15);
                claw(level, MOB_CLAW).setComputerSignalOverride(15);
                prepared = true;
            }catch(Throwable err){ failure = err; }
        });
    }

    private static void validateAndRelease(Minecraft mc){
        try{
            var level = mc.getSingleplayerServer().overworld();
            var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            var claw = claw(level, PLAYER_CLAW);
            check(claw.isHoldingEntity() && HangingEntityMotion.isHanging(player), "The server did not retain the player's hanging state");
            check(HangingEntityMotion.isServerHeld(player), "The server did not validate the player's active grip");
            var floating = ServerGamePacketListenerImpl.class.getDeclaredField("clientIsFloating");
            var floatingTicks = ServerGamePacketListenerImpl.class.getDeclaredField("aboveGroundTickCount");
            floating.setAccessible(true);
            floatingTicks.setAccessible(true);
            floating.setBoolean(player.connection, true);
            floatingTicks.setInt(player.connection, 85);
            player.connection.tick();
            check(!floating.getBoolean(player.connection) && floatingTicks.getInt(player.connection) == 0,
                    "The active grip did not clear unsupported-flight detection");
            check(!player.hurtMarked && !player.isNoGravity(), "Holding changed player movement authority or gravity");
            Vec3 target = HangingEntityMotion.feetBelowGrip(claw(level, MOB_CLAW).getEntityGripPosition(), cow.getBbHeight(), 0.125D);
            check(claw(level, MOB_CLAW).isHoldingEntity() && HangingEntityMotion.worldFeet(cow).distanceTo(target) < 0.55D, "The server-owned mob did not settle below its Claw");
            check(claw.getJawOpening(1.0F) == 0.0F && claw(level, MOB_CLAW).getJawOpening(1.0F) == 0.0F, "Closed jaws render open");
            check(claw(level, OPEN_CLAW).getJawOpening(1.0F) == 1.0F, "Open jaws render closed");
            claw.setComputerSignalOverride(0);
            check(!claw.isHoldingEntity() && !HangingEntityMotion.isHanging(player)
                    && !HangingEntityMotion.isServerHeld(player), "Release retained the player's hanging state");
            released = true;
        }catch(Throwable err){ failure = err; }
    }

    private static ClawBlockEntity claw(ServerLevel level, BlockPos pos){
        return (ClawBlockEntity)level.getBlockEntity(pos);
    }

    private static void finish(String result){
        if(complete) return;
        complete = true;
        CTConfigs.COMMON.clawCanGrabEntities.set(prevGrab);
        try{ Files.writeString(Path.of(OUTPUT).resolve("result.txt"), result); }
        catch(Exception err){ throw new RuntimeException(err); }
        finally{ Minecraft.getInstance().stop(); }
    }

    private static void check(boolean val, String msg){
        if(!val) throw new AssertionError(msg);
    }
}
