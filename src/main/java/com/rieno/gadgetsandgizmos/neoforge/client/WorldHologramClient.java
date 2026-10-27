package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityLookupApi;
import com.rieno.gadgetsandgizmos.content.WorldHologramProjectorBlockEntity;
import com.rieno.gadgetsandgizmos.lib.client.render.HologramEntityRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.HologramProjectionRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.HologramBlockEntityRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.HologramProjectionFrame;
import com.rieno.gadgetsandgizmos.lib.client.render.HologramRenderScope;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletBlockEntity;
import com.rieno.gadgetsandgizmos.lib.client.render.ProjectionSmoothing;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelClientRenderApi;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelHologramRenderer;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramSlice;
import com.rieno.gadgetsandgizmos.lib.physics.ColoredLightBridge;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import org.joml.Vector3f;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramChunk;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramEntity;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramRegion;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.neoforge.network.WorldHologramDataPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.WorldHologramRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Own client projection lifetimes while all geometry work stays in the reusable library
public final class WorldHologramClient{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<WorldHologramProjectorBlockEntity, Projection> PROJECTIONS = new IdentityHashMap<>();
    private static Object currentLevel;
    private static long generation;
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private WorldHologramClient(){}

    // Register GPU cache cleanup with the normal resource reload lifecycle
    public static void registerReloadListeners(RegisterClientReloadListenersEvent evt){
        evt.registerReloadListener((ResourceManagerReloadListener)resources -> { generation++; clear(); });
    }

    public static void onClientTick(ClientTickEvent.Post evt){
        var level = Minecraft.getInstance().level;
        if(currentLevel != level){ clear(); currentLevel = level; }
        if(level == null) return;
        long now = level.getGameTime();
        PROJECTIONS.entrySet().removeIf(entry -> {
            if(!entry.getKey().isRemoved() && now - entry.getValue().lastVisible < 100) return false;
            entry.getValue().close();
            return true;
        });
    }

    private static void clear(){
        PROJECTIONS.values().forEach(Projection::close);
        PROJECTIONS.clear();
    }

    // Reject stale settings generations and preserve unchanged mesh identities
    public static void accept(WorldHologramDataPayload payload){
        var mc = Minecraft.getInstance();
        if(mc.level == null) return;
        var blockEntity = BlockEntityLookupApi.findLoadedExact(mc.level, payload.target().subLevelId(),
                payload.target().pos());
        if(!(blockEntity instanceof WorldHologramProjectorBlockEntity projector)) return;
        Projection projection = PROJECTIONS.get(projector);
        if(projection == null || payload.revision() < projection.revision) return;
        if(payload.revision() > projection.revision){
            projection.chunks.clear();
            projection.terrain.close();
            projection.ships.close();
            projection.dynamic.close();
            projection.dynamic = projection.dynamic(projector);
            projection.entities = List.of();
            projection.revision = payload.revision();
        }
        projection.surfaceY = payload.surfaceY();
        projection.hasHeight = true;
        projection.slice = payload.slice();
        projection.chunks.values().removeIf(chunk -> !payload.region().contains(chunk.pos()));
        if(payload.chunk() != null){
            long key = payload.chunk().pos().toLong();
            if(!payload.chunk().equals(projection.chunks.get(key))) projection.chunks.put(key, payload.chunk());
        }
        if(payload.entities() != null) projection.entities = List.copyOf(payload.entities());
    }

    // Rendering follows the mounting sublevel while sampling remains in the root world
    public static void render(WorldHologramProjectorBlockEntity projector, PoseStack poses, MultiBufferSource buffers, float partialTicks){
        if(HologramRenderScope.isActive()) return;
        var mc = Minecraft.getInstance();
        if(mc.level == null || mc.player == null) return;
        Projection projection = PROJECTIONS.computeIfAbsent(projector, ignored -> new Projection(projector));
        long now = mc.level.getGameTime();
        projection.lastVisible = now;
        if(now >= projection.nextRequest){
            PacketDistributor.sendToServer(new WorldHologramRequestPayload(MenuConfigTarget.of(projector.getBlockPos(), SableLevelApi.containingId(projector))));
            projection.nextRequest = now + 40;
        }
        HologramProjectionFrame frame = frame(projector, projection, partialTicks);
        Vec3 center = SubLevelClientRenderApi.worldFrame(projector, partialTicks).position();
        var settings = projector.settings();
        var region = new HologramRegion(new ChunkPos(net.minecraft.core.BlockPos.containing(center)), settings.radius());
        projection.chunks.values().removeIf(chunk -> !region.contains(chunk.pos()));
        Vec3 origin = frame.origin();
        projection.light.update(frame.target(), 0x4ADFFF, Math.max(3, projector.settings().size()), 0.9F);
        float scale = settings.size() / (settings.radius() * 16);
        projection.dynamic.beginFrame();
        poses.pushPose();
        try{
            poses.translate(0.5, settings.height() + 0.25, 0.5);
            poses.scale(scale, scale, scale);
            projection.terrain.render(poses, buffers, origin, projection.chunks.values(), generation);
            projection.dynamic.render(poses, buffers, origin, projection.chunks.values(), null, partialTicks);
            if(settings.ships()) projection.ships.render(poses, buffers, origin, region, projection.slice,
                    CTConfigs.SERVER.worldHologramObfuscateOres.get(), partialTicks, generation, projection.dynamic);
            else projection.ships.close();
            projection.models.render(poses, buffers, origin, projection.entities, partialTicks, entity ->
                    entity instanceof net.minecraft.world.entity.player.Player ? settings.players() :
                    entity instanceof net.minecraft.world.entity.monster.Enemy ? settings.hostile() : settings.passive());
        }finally{ poses.popPose(); projection.dynamic.endFrame(); }
    }

    // Use the same interpolated carrier pose for the source anchor and native Flywheel embeddings
    private static HologramProjectionFrame frame(WorldHologramProjectorBlockEntity projector, Projection projection, float tick){
        var mount = SubLevelClientRenderApi.worldFrame(projector, tick);
        var settings = projector.settings();
        double seconds = (Minecraft.getInstance().level.getGameTime() + tick) / 20D;
        double baseline = projection.hasHeight ? HologramSlice.projectionOriginY(projection.surfaceY, settings.yOffset()) : mount.position().y;
        double y = projection.smoothing.update(new Vec3(0, baseline, 0), seconds).y;
        Vector3f lift = new Vector3f(0, (float)((settings.height() - 0.25) * mount.scale().y), 0);
        mount.orientation().transform(lift);
        Vec3 target = mount.position().add(lift.x, lift.y, lift.z);
        double scale = settings.size() / (settings.radius() * 16D);
        return new HologramProjectionFrame(target, mount.orientation(), mount.scale().scale(scale),
                new Vec3(mount.position().x, y, mount.position().z));
    }

    private static final class Projection{
        private final HologramProjectionRenderer terrain = new HologramProjectionRenderer();
        private final HologramEntityRenderer models = new HologramEntityRenderer();
        private final SubLevelHologramRenderer ships = new SubLevelHologramRenderer();
        private final ProjectionSmoothing smoothing = new ProjectionSmoothing();
        private final ColoredLightBridge.Light light = ColoredLightBridge.create();
        private HologramBlockEntityRenderer dynamic;
        private final Map<Long, HologramChunk> chunks = new LinkedHashMap<>();
        private List<HologramEntity> entities = List.of();
        private HologramSlice slice = HologramSlice.SURFACE;
        private int revision = -1;
        private double surfaceY;
        private boolean hasHeight;
        private long lastVisible;
        private long nextRequest = Long.MIN_VALUE;
        private Projection(WorldHologramProjectorBlockEntity projector){ dynamic = dynamic(projector); }
        private HologramBlockEntityRenderer dynamic(WorldHologramProjectorBlockEntity projector){
            return new HologramBlockEntityRenderer(tick -> Minecraft.getInstance().level == null ? null : frame(projector, this, tick),
                    entity -> !(entity instanceof WorldHologramProjectorBlockEntity)
                            && !(entity instanceof AccDisplayBlockEntity) && !(entity instanceof DiagnosticTabletBlockEntity));
        }
        private void close(){ terrain.close(); ships.close(); dynamic.close(); light.close(); models.clear(); }
    }
}
