package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.content.ScissorPistonArmBlockEntity;
import com.rieno.gadgetsandgizmos.content.ScissorPistonBlock;
import com.rieno.gadgetsandgizmos.content.ScissorPistonBlockEntity;
import com.rieno.gadgetsandgizmos.content.ScissorPistonLinkBlockEntity;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.lib.geometry.BlockVolume;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Exercise actual client vertices, hitboxes and packet timing around a moving arm break
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class ScissorPistonClientValidation{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String OUTPUT = System.getProperty("gadgetsngizmos.scissorPistonValidation");
    private static final BlockPos BASE = new BlockPos(0, -55, 0);
    // Advance the client only after the server has published its attachment
    private static volatile UUID headId;
    private static volatile BlockPos headPos;
    private static volatile Throwable failure;
    private static volatile boolean breakRequested, broken;
    // Retain client capture progress until both stopped spans have been checked
    private static boolean installed, hidden, complete;
    private static int beforeTicks, afterTicks, beforeVertices;
    private static long started;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Wait for real synchronization before sampling both ends of a partial-tick interval
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if(!hidden){
            hidden = true;
            started = System.nanoTime();
            GLFW.glfwHideWindow(mc.getWindow().getWindow());
        }
        try{
            if(failure != null) throw new AssertionError("Server fixture failed", failure);
            require(System.nanoTime() - started < 240_000_000_000L, "Client validation timed out");
            if(mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            mc.options.pauseOnLostFocus = false;
            if(!installed){ installed = true; install(); }
            if(headId == null || headPos == null) return;
            var container = SubLevelContainer.getContainer(mc.level);
            var head = container == null ? null : container.getSubLevel(headId);
            if(head == null || !(mc.level.getBlockEntity(headPos) instanceof ScissorPistonLinkBlockEntity link)) return;
            var parent = link.getParentPiston();
            if(parent == null) return;
            if(!breakRequested){
                if(parent.getMaxRange() != 6 || ++beforeTicks < 40) return;
                var first = vertices(link, .1F);
                require(!first.isEmpty(), "The moving head did not render any arms");
                same(first, vertices(link, .9F));
                continuousVolume(link);
                beforeVertices = first.size();
                Screenshot.grab(mc.gameDirectory, "before-break.png", mc.getMainRenderTarget(), msg -> {});
                breakRequested = true;
                return;
            }
            if(!broken || parent.getMaxRange() != 5 || ++afterTicks < 40) return;
            var first = vertices(link, .1F);
            require(first.size() * 7 == beforeVertices * 6, "Shortening duplicated or omitted arm models");
            same(first, vertices(link, .9F));
            BlockPos armPos = headPos.relative(Direction.DOWN, 2);
            require(mc.level.getBlockEntity(armPos) instanceof ScissorPistonArmBlockEntity, "The broken cell did not return");
            var arm = (ScissorPistonArmBlockEntity)mc.level.getBlockEntity(armPos);
            require(mc.getBlockEntityRenderDispatcher().getRenderer(arm) == null, "An arm cell still owns a renderer");
            require(!arm.getBlockState().getShape(mc.level, armPos).isEmpty(), "The client lost the mining hitbox");
            require(!arm.getBlockState().getCollisionShape(mc.level, armPos).isEmpty(), "The client cached an empty collision shape");
            var volume = continuousVolume(link);
            var original = arm.saveWithoutMetadata(mc.level.registryAccess());
            try{
                var stale = original.copy();
                stale.putInt("MaxRange", 1);
                stale.putDouble("Extension", .25);
                stale.putDouble("PreviousExtension", 0);
                arm.loadWithComponents(stale, mc.level.registryAccess());
                same(first, vertices(link, .1F));
                require(arm.getBlockVolume() == volume, "Stale arm data changed the continuous hitbox");
            }finally{ arm.loadWithComponents(original, mc.level.registryAccess()); }
            Screenshot.grab(mc.gameDirectory, "after-break.png", mc.getMainRenderTarget(), msg -> {});
            finish("PASSED: complete head renderer, one growing hitbox, stable stopped vertices and stale cell independence");
        }catch(Throwable err){
            err.printStackTrace();
            finish("FAILED: " + err);
        }
    }

    // Build a real piston in the isolated validation world and expose its interior arm cells
    private static void install(){
        var mc = Minecraft.getInstance();
        mc.options.hideGui = false;
        mc.setScreen(null);
        mc.getSingleplayerServer().execute(() -> {
            try{
                var server = mc.getSingleplayerServer();
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.connection.teleport(2, -53, 4, 156.8F, 10);
                level.setDayTime(6000);
                if(level.getBlockEntity(BASE) instanceof ScissorPistonBlockEntity old) old.detachMountedSubLevel(true);
                level.setBlock(BASE, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(BASE, CTBlocks.SCISSOR_PISTON.get().defaultBlockState()
                        .setValue(ScissorPistonBlock.FACING, Direction.UP), 3);
                var piston = (ScissorPistonBlockEntity)level.getBlockEntity(BASE);
                var data = piston.saveWithoutMetadata(level.registryAccess());
                data.putInt("MaxRange", 6);
                piston.loadWithComponents(data, level.registryAccess());
                piston.tick();
                require(piston.tryAssembleMountedBlock(), "The validation head did not assemble");
                for(int idx = 0; idx < 64 && piston.getExtension() < 5.5; idx++){
                    piston.setSpeed(128);
                    piston.tick();
                }
                require(piston.getExtension() >= 5.5, "The validation piston did not extend");
                piston.setSpeed(0);
                headPos = piston.getMountedLocalPos();
                headId = piston.getMountedSubLevelId();
            }catch(Throwable err){ failure = err; }
        });
    }

    // Break an interior cell during real movement, then let the following world tick rebuild it
    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post evt){
        if(OUTPUT == null || !breakRequested || broken || failure != null) return;
        try{
            var level = evt.getServer().overworld();
            var piston = (ScissorPistonBlockEntity)level.getBlockEntity(BASE);
            piston.setSpeed(128);
            piston.tick();
            var child = (ServerSubLevel)piston.getMountedSubLevelHandle();
            BlockPos pos = headPos.relative(Direction.DOWN, 2).subtract(child.getPlot().getCenterBlock());
            child.getPlot().getEmbeddedLevelAccessor().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            piston.setSpeed(0);
            broken = true;
        }catch(Throwable err){ failure = err; }
    }

    // Record vertices produced by the registered renderer without replacing its model or poses
    private static List<Vec3> vertices(ScissorPistonLinkBlockEntity head, float partialTicks){
        var res = new ArrayList<Vec3>();
        VertexConsumer sink = (VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                    if(method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    if(method.getName().equals("addVertex") && args.length == 3){
                        res.add(new Vec3(((Number)args[0]).doubleValue(), ((Number)args[1]).doubleValue(),
                                ((Number)args[2]).doubleValue()));
                    }
                    return method.getReturnType() == VertexConsumer.class ? proxy : null;
                });
        var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(head);
        require(renderer instanceof ScissorPistonArmRenderer, "The head has no complete-chain renderer");
        ((ScissorPistonArmRenderer)renderer).renderSafe(head, partialTicks, new PoseStack(), type -> sink,
                LightTexture.FULL_BRIGHT, 0);
        for(Vec3 pos : res) require(Double.isFinite(pos.lengthSqr()), "The renderer emitted a nonfinite vertex");
        return res;
    }

    // Verify that every selected cell outlines the same full volume visible on the client
    private static BlockVolume continuousVolume(ScissorPistonLinkBlockEntity head){
        var mc = Minecraft.getInstance();
        var volume = head.getBlockVolume();
        require(volume != null && volume.bounds().getYsize() > 5, "The client did not receive a complete column");
        var expected = volume.bounds().move(volume.origin());
        for(int distance = 0; distance <= 4; distance++){
            BlockPos pos = head.getBlockPos().below(distance);
            var shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
            require(shape.toAabbs().size() == 1 && shape.bounds().move(pos).equals(expected),
                    "The client outlined a separate segment instead of the shared column");
        }
        require(mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK,
                "The crosshair could not select the continuous column");
        return volume;
    }

    // Reject repeated animation and section reassignment after the native head has stopped
    private static void same(List<Vec3> first, List<Vec3> next){
        require(first.size() == next.size(), "The renderer changed its arm count between frames");
        for(int idx = 0; idx < first.size(); idx++){
            require(first.get(idx).distanceTo(next.get(idx)) < .001, "A stopped arm moved between partial ticks");
        }
    }

    // Report a failed live assertion to the isolated run
    private static void require(boolean val, String message){
        if(!val) throw new AssertionError(message);
    }

    // Publish the validation result before closing the hidden client
    private static void finish(String result){
        complete = true;
        try{ Files.writeString(Path.of(OUTPUT).resolve("result.txt"), result); }
        catch(Exception err){ throw new RuntimeException(err); }
        Minecraft.getInstance().stop();
    }
}
