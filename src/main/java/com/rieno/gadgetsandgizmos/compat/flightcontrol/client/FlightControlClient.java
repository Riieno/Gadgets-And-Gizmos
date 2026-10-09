package com.rieno.gadgetsandgizmos.compat.flightcontrol.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlGraphRuntime;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlIntegration;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler;
import ace.flight.block.MouseFlightControllerBlockEntity;
import ace.flight.block.HudProjectorBlockEntity;
import ace.flight.block.AttitudeDisplayBlockEntity;
import ace.flight.block.AerodynamicTrailCreatorBlockEntity;
import com.rieno.gadgetsandgizmos.lib.physics.HostedBlockEntities;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelProjectionContext;
import com.rieno.gadgetsandgizmos.mixin.FlightControlTrailRenderAccess;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import ace.flight.platform.PlatformHooks;
import com.rieno.gadgetsandgizmos.lib.client.render.WorldProjectionRenderTypes;
import com.rieno.gadgetsandgizmos.lib.scm.ScmAttitude;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelClientRenderApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.mixin.FlightControlHudRenderAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Reuse Flight Control's own projectors at the ACC's rendered world position
public final class FlightControlClient{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final java.util.Map<BlockEntity, Long> TRAIL_TICKS = new java.util.WeakHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private FlightControlClient(){}

    // Draw native projections from Sable's interpolated pose independently of ACC mesh visibility
    public static void onRenderWorld(RenderLevelStageEvent event){
        if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.level == null){ TRAIL_TICKS.clear(); return; }
        var hosts = FlightControlIntegration.hosts(minecraft.level);
        if(hosts.isEmpty()) return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = event.getCamera().getPosition();
        var view = new org.joml.Matrix4f(event.getModelViewMatrix());
        SubLevelClientRenderApi.withProjection(event.getProjectionMatrix(), buffers -> {
            for(var host : hosts){
                var runtime = FlightControlIntegration.runtime(host);
                if(runtime == null) continue;
                var body = SableLevelApi.containing(host);
                var frame = FlightControlIntegration.orientation(host).rotation();
                var anchor = host.getBlockPos().getCenter();
                for(BlockEntity display : runtime.displays()){
                    if(display.isRemoved()) continue;
                    var origin = display.getBlockPos().getCenter();
                    var center = HostedBlockEntities.host(display) == null ? origin : anchor;
                    var pose = body instanceof ClientSubLevel client
                            ? SubLevelClientRenderApi.anchoredPose(client, partialTick, origin, center, frame) : null;
                    var matrix = pose != null
                            ? SubLevelClientRenderApi.localModelView(pose,
                                    net.minecraft.world.phys.Vec3.atLowerCornerOf(display.getBlockPos()), camera, view)
                            : SubLevelClientRenderApi.localModelView(body, partialTick, anchor, camera, view)
                                    .rotate(new org.joml.Quaternionf(frame)).translate(-.5F, -.5F, -.5F);
                    if(new org.joml.Vector3f(matrix.m30(), matrix.m31(), matrix.m32()).lengthSquared() > 256F * 256F) continue;
                    if(display instanceof HudProjectorBlockEntity hud){
                        var attitude = body == null ? null : ScmAttitude.measure(
                                body instanceof ClientSubLevel client ? client.renderPose(partialTick).orientation() : body.logicalPose().orientation(),
                                FlightControlIntegration.orientation(host));
                        SubLevelProjectionContext.render(hud, pose, () -> {
                            var projection = FlightControlHudRenderAccess.createThrusters$projectorMatrix(matrix, hud);
                            FlightControlHudRenderAccess.createThrusters$renderHud(projection,
                                    buffers.getBuffer(WorldProjectionRenderTypes.color()), attitude != null || hud.hasAttitudeData,
                                    attitude == null ? hud.displayedPitch : attitude.pitch(),
                                    attitude == null ? hud.displayedYaw : attitude.yaw(),
                                    attitude == null ? hud.displayedRoll : attitude.roll(), hud.displayedSpeed);
                        });
                        buffers.endBatch(WorldProjectionRenderTypes.color());
                        continue;
                    }
                    if(display instanceof AerodynamicTrailCreatorBlockEntity trail && body instanceof ClientSubLevel client){
                        long tick = minecraft.level.getGameTime();
                        if(!java.util.Objects.equals(TRAIL_TICKS.put(trail, tick), tick)){
                            SubLevelProjectionContext.render(trail, pose,
                                    () -> FlightControlTrailRenderAccess.createThrusters$sampleTrail(client, pose, trail));
                        }
                    }
                    PoseStack ms = new PoseStack();
                    ms.mulPose(matrix);
                    if(display instanceof AerodynamicTrailCreatorBlockEntity trail && pose != null){
                        FlightControlTrailProjection.render(trail, pose, ms, buffers, camera);
                    }else{
                        BlockEntityRenderer<BlockEntity> renderer = minecraft.getBlockEntityRenderDispatcher().getRenderer(display);
                        if(renderer != null) SubLevelProjectionContext.render(display, pose, () -> renderer.render(display, partialTick,
                                ms, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY));
                    }
                    if(display instanceof AttitudeDisplayBlockEntity attitude && body instanceof ClientSubLevel client){
                        FlightControlAttitudeProjection.render(attitude, client, partialTick, matrix, frame, buffers, event.getProjectionMatrix());
                    }
                }
            }
        });
    }

    // Register detached controllers with native mouse steering during physical ACC interaction
    public static void steer(AdvancedContraptionControllerBlockEntity host){
        Minecraft minecraft = Minecraft.getInstance();
        if(host == null || minecraft.player == null || minecraft.screen != null
                || !ModList.get().isLoaded("create_flight_control")) return;
        FlightControlGraphRuntime runtime = FlightControlIntegration.runtime(host);
        if(runtime == null || !runtime.hasMouseSteering()) return;
        FlightControlIntegration.clientInteraction((controller, player) -> controller ==
                AnalogueContraptionControllerClientHandler.physicalAdvancedController() && Minecraft.getInstance().screen == null);
        for(MouseFlightControllerBlockEntity component : runtime.mouseControllers()) PlatformHooks.tickMouseFlightControllerClient(component);
    }

    // Hand selection, camera alignment and network rebasing to the native client
    public static MouseFlightControllerBlockEntity activeMouse(){
        AdvancedContraptionControllerBlockEntity host = AnalogueContraptionControllerClientHandler.physicalAdvancedController();
        if(host == null || Minecraft.getInstance().screen != null) return null;
        FlightControlGraphRuntime runtime = FlightControlIntegration.runtime(host);
        if(runtime == null) return null;
        return runtime.pilotMouse();
    }
}
