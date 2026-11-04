package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.client.render.*;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramChunk;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import dev.engine_room.flywheel.api.instance.*;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.task.TaskExecutor;
import dev.engine_room.flywheel.api.visual.*;
import dev.engine_room.flywheel.api.visualization.*;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.FrustumIntersection;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Exercise retained geometry and real Create visualizers against loaded client fixtures
final class HybridHologramValidation{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    static final BlockPos TANK = new BlockPos(3, -59, 4);
    static final BlockPos CHEST = new BlockPos(-2, -59, 4);
    static final BlockPos COG = new BlockPos(-2, -59, -2);
    private static final TaskExecutor DIRECT = new TaskExecutor(){
        @Override public int threadCount(){ return 1; }
        @Override public void execute(Runnable task){ task.run(); }
    };
    private static int tessellations;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    static void run(){
        checkTank();
        checkCompatibility();
        checkNativeVisual();
        checkNativeVisual(false);
        checkStaticDetails();
        checkClosedTankMesh();
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    @SuppressWarnings("unchecked")
    private static void checkTank(){
        var mc = Minecraft.getInstance();
        var tank = (SmartTankBlockEntity)mc.level.getBlockEntity(TANK);
        require(tank != null, "Smart tank fixture was not loaded");
        tank.setExtraData(true);
        tank.localTank().setFluid(new FluidStack(Fluids.WATER, 4000));
        tank.setFluidLevel(LerpedFloat.linear().startWithValue(.5));
        BlockEntityRenderer<BlockEntity> registered = mc.getBlockEntityRenderDispatcher().getRenderer(tank);
        require(registered instanceof HologramRendererProvider, "Smart tank did not provide a retained renderer");
        var unknown = new MultiFluidTankRenderer(null){};
        require(unknown.createHologramRenderer(tank) == null, "Unknown tank renderer subclass was cached without opting in");
        var sink = new Sink();
        MultiBufferSource buffers = ignored -> sink;
        tessellations = 0;
        try(var scope = HologramRenderScope.enter(); var retained = new RetainedTankRenderer(tank,
                (target, partial, poses, output, light, overlay) -> {
                    tessellations++;
                    registered.render(target, partial, poses, output, light, overlay);
                })){
            drawTank(retained, buffers, .5F);
            int vertices = sink.vertices;
            require(vertices > 0, "Tank contents and tablet emitted no geometry");
            for(int idx = 0; idx < 100; idx++) drawTank(retained, buffers, .5F);
            require(tessellations == 1 && sink.vertices == vertices * 101, "Stable tank geometry was rebuilt or lost");
            tank.localTank().setFluid(new FluidStack(Fluids.LAVA, 4000));
            drawTank(retained, buffers, .5F);
            require(tessellations == 2, "Fluid material change retained old geometry");
            tank.setFluidLevel(LerpedFloat.linear().startWithValue(.75));
            drawTank(retained, buffers, .5F);
            require(tessellations == 3, "Fill height change retained old geometry");
            tank.localTank().setFluid(new FluidStack(Fluids.LAVA, 2000));
            drawTank(retained, buffers, .5F);
            require(tessellations == 4, "Fluid amount change retained old geometry");
            tank.setHeight(2);
            drawTank(retained, buffers, .5F);
            require(tessellations == 5, "Multiblock height change retained old geometry");
            tank.setHeight(1);
            tank.setFluidLevel(LerpedFloat.linear().startWithValue(.25).chase(.75, .5, LerpedFloat.Chaser.EXP));
            tank.getFluidLevel().tickChaser();
            int before = tessellations;
            drawTank(retained, buffers, 0);
            drawTank(retained, buffers, 1);
            require(tessellations == before + 2, "Fill interpolation froze between ticks");
            retained.close();
            drawTank(retained, buffers, 1);
            require(tessellations == before + 3, "Disposed tank cache retained old geometry");
        }
    }

    private static void drawTank(RetainedHologramRenderer renderer, MultiBufferSource buffers, float partial){
        renderer.render(partial, new PoseStack(), buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }

    private static void checkCompatibility(){
        var mc = Minecraft.getInstance();
        var chest = mc.level.getBlockEntity(CHEST);
        require(chest instanceof ChestBlockEntity && chest.getBlockState().getRenderShape() == net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED,
                "Renderer-only chest fixture was not loaded");
        var sink = new Sink();
        var snapshot = surface(chest, Map.of());
        try(var renderer = new HologramBlockEntityRenderer(tick -> projection())){
            drawDetails(renderer, snapshot, sink);
            require(sink.vertices > 0, "Renderer-only block disappeared from the hologram");
            int visibleVertices = sink.vertices;
            SubLevelClientRenderApi.withProjection(new org.joml.Matrix4f().ortho(-128, 128, -128, 128, -128, 128), () -> {
                var offscreen = new PoseStack(); offscreen.translate(10000, 0, 0);
                renderer.beginFrame();
                renderer.render(offscreen, ignored -> sink, Vec3.ZERO, List.of(snapshot), null, .5F);
                renderer.endFrame();
            });
            require(sink.vertices == visibleVertices, "Fully clipped block entity details still emitted geometry");
            var chunks = (Map<?, ?>)field(renderer, "chunks");
            Object cached = chunks.values().iterator().next();
            drawDetails(renderer, surface(chest, Map.of(new BlockPos(CHEST.getX() & 15, CHEST.getY(), CHEST.getZ() & 15), 32F)), sink);
            require(chunks.values().iterator().next() == cached, "Kinetic metadata rebuilt the detail entry list");
            var entries = (List<?>)field(cached, "entries");
            require(field(entries.getFirst(), "entity") == chest, "Detail cache did not resolve the loaded entity");
            var replacement = new ChestBlockEntity(CHEST, chest.getBlockState());
            replacement.setLevel(mc.level);
            var entities = mc.level.getChunkAt(CHEST).getBlockEntities();
            entities.put(CHEST, replacement);
            try{
                drawDetails(renderer, snapshot, sink);
                require(field(entries.getFirst(), "entity") == replacement, "Detail cache retained a replaced block entity");
            }finally{ entities.put(CHEST, chest); }
            renderer.beginFrame();
            renderer.endFrame();
            require(chunks.isEmpty(), "Invisible detail chunks were retained");
            var tank = mc.level.getBlockEntity(TANK);
            drawDetails(renderer, surface(tank, Map.of()), sink);
            Object tankChunk = chunks.values().iterator().next();
            Object tankEntry = ((List<?>)field(tankChunk, "entries")).getFirst();
            require(field(tankEntry, "retained") instanceof RetainedTankRenderer, "Detail routing bypassed the tank's retained renderer");
        }
        var cog = mc.level.getBlockEntity(COG);
        var partials = new Sink();
        try(var renderer = new HologramBlockEntityRenderer(tick -> projection())){
            Object nativeVisuals = field(renderer, "flywheel");
            Object effect = field(nativeVisuals, "effect");
            @SuppressWarnings("unchecked") var failed = (java.util.Set<BlockEntity>)field(effect, "failed");
            failed.add(cog);
            drawDetails(renderer, surface(cog, Map.of()), partials);
            require(partials.vertices > 0, "Unsupported native visuals lost the kinetic partial fallback");
        }
    }

    private static void drawDetails(HologramBlockEntityRenderer renderer, HologramChunk snapshot, Sink sink){
        SubLevelClientRenderApi.withProjection(new org.joml.Matrix4f().ortho(-128, 128, -128, 128, -128, 128), () -> {
            renderer.beginFrame();
            renderer.render(new PoseStack(), ignored -> sink, Vec3.ZERO, List.of(snapshot), null, .5F);
            renderer.endFrame();
        });
    }

    private static void checkNativeVisual(){
        checkNativeVisual(true);
    }

    private static void checkNativeVisual(boolean animated){
        var mc = Minecraft.getInstance();
        var cog = (KineticBlockEntity)mc.level.getBlockEntity(COG);
        require(cog != null, "Kinetic fixture was not loaded");
        float prevSpeed = cog.getSpeed();
        var ctx = new Context(BlockPos.ZERO);
        try(var nativeVisuals = new FlywheelHologramVisuals(mc.level, tick -> projection(), animated)){
            cog.setSpeed(32);
            nativeVisuals.beginFrame();
            require(nativeVisuals.request(cog, cog.getBlockState()), "Native kinetic visualization was unavailable");
            nativeVisuals.endFrame();
            var effect = (Effect)field(nativeVisuals, "effect");
            Object desired = field(effect, "desired");
            var visual = effect.visualize(ctx, .5F);
            try{
                var plan = ((DynamicVisual)visual).planFrame();
                var frame = new DynamicVisual.Context(){
                    @Override public Camera camera(){ return mc.gameRenderer.getMainCamera(); }
                    @Override public FrustumIntersection frustum(){ return new FrustumIntersection(); }
                    @Override public float partialTick(){ return .5F; }
                    @Override public DistanceUpdateLimiter limiter(){ return distance -> true; }
                };
                plan.execute(DIRECT, frame);
                require(!ctx.shared.instances.isEmpty(), "Create's visualizer produced no instances");
                var rotating = ctx.shared.instances.stream().filter(instance -> instance instanceof RotatingInstance)
                        .map(instance -> (RotatingInstance)instance).findFirst().orElseThrow();
                float initialSpeed = rotating.rotationalSpeed;
                require(animated || initialSpeed == 0, "Static native partial retained shader rotation");
                int instances = ctx.shared.instances.size();
                int transforms = ctx.shared.transforms;
                int uploads = ctx.shared.handles.stream().mapToInt(handle -> handle.changes).sum();
                for(int idx = 0; idx < 100; idx++){
                    nativeVisuals.beginFrame();
                    nativeVisuals.request(cog, cog.getBlockState());
                    nativeVisuals.endFrame();
                    plan.execute(DIRECT, frame);
                }
                require(field(effect, "desired") == desired, "Stable native membership copied its map");
                require(ctx.shared.instances.size() == instances && ctx.shared.transforms == transforms, "Stable kinetic geometry or transforms were rebuilt");
                require(ctx.shared.handles.stream().mapToInt(handle -> handle.changes).sum() == uploads, "Stable kinetic instances were uploaded again");
                setField(effect, "lastVisible", System.nanoTime() - 500_000_000L);
                plan.execute(DIRECT, frame);
                require(ctx.shared.instances.size() == instances && ctx.shared.handles.stream().noneMatch(handle -> handle.deleted),
                        "A slow frame deleted retained native instances");
                cog.setSpeed(64);
                plan.execute(DIRECT, frame);
                require(animated ? rotating.rotationalSpeed != initialSpeed : rotating.rotationalSpeed == 0,
                        "Kinetic speed change broke the native presentation mode");
                require(cog.getSpeed() == 64, "Freezing a hologram changed the source machine's speed");
                nativeVisuals.beginFrame(); nativeVisuals.endFrame();
                plan.execute(DIRECT, frame);
                require(ctx.shared.handles.stream().allMatch(handle -> handle.deleted), "Removed native visuals retained instances");
                nativeVisuals.beginFrame(); nativeVisuals.request(cog, cog.getBlockState()); nativeVisuals.endFrame();
                plan.execute(DIRECT, frame);
                require(ctx.shared.instances.size() > instances, "Returning native details did not recreate their instances");
                setField(effect, "lastVisible", System.nanoTime() - 6_000_000_000L);
                plan.execute(DIRECT, frame);
                require(ctx.shared.handles.stream().allMatch(handle -> handle.deleted), "Expired native scenes retained instances");
            }finally{ visual.delete(); }
        }finally{ cog.setSpeed(prevSpeed); }
    }

    private static void checkStaticDetails(){
        var mc = Minecraft.getInstance();
        var chest = mc.level.getBlockEntity(CHEST);
        var sink = new Sink();
        try(var renderer = new HologramBlockEntityRenderer(tick -> projection(), entity -> true, false)){
            var snapshot = surface(chest, Map.of());
            drawDetails(renderer, snapshot, sink);
            var chunks = (Map<?, ?>)field(renderer, "chunks");
            var entry = ((List<?>)field(chunks.values().iterator().next(), "entries")).getFirst();
            var geometry = field(entry, "staticGeometry");
            var batches = List.copyOf(((Map<?, ?>)field(geometry, "batches")).values());
            int vertices = sink.vertices;
            require(vertices > 0 && (boolean)field(entry, "recorded"), "Static renderer-only block lost its geometry");
            for(int idx = 0; idx < 100; idx++) drawDetails(renderer, snapshot, sink);
            require(sink.vertices == vertices * 101 && List.copyOf(((Map<?, ?>)field(geometry, "batches")).values()).equals(batches),
                    "Static renderer-only geometry disappeared or was replaced");
        }
    }

    private static void checkClosedTankMesh(){
        var mc = Minecraft.getInstance();
        var tank = mc.level.getBlockEntity(TANK);
        var state = tank.getBlockState().setValue(com.simibubi.create.content.fluids.tank.FluidTankBlock.SHAPE,
                com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape.WINDOW);
        var closed = HologramBlockModels.closedTank(state);
        var shaper = mc.getBlockRenderer().getBlockModelShaper();
        @SuppressWarnings("unchecked") var models = (Map<net.minecraft.world.level.block.state.BlockState, net.minecraft.client.resources.model.BakedModel>)field(shaper, "modelByStateCache");
        var original = mc.getBlockRenderer().getBlockModel(closed);
        int[] reads = {0};
        var probe = new net.neoforged.neoforge.client.model.BakedModelWrapper<net.minecraft.client.resources.model.BakedModel>(original){
            @Override public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState selected,
                    net.minecraft.core.Direction face, net.minecraft.util.RandomSource random,
                    net.neoforged.neoforge.client.model.data.ModelData data, net.minecraft.client.renderer.RenderType layer){
                require(selected == closed, "Static mesh requested the tank's window model"); reads[0]++;
                return super.getQuads(selected, face, random, data, layer);
            }
        };
        models.put(closed, probe);
        try(var renderer = HologramProjectionRenderer.staticModels(null)){
            var pos = tank.getBlockPos();
            var snapshot = new HologramChunk(new ChunkPos(pos), 1, 2, 3, List.of(new HologramChunk.SurfaceBlock(
                    new BlockPos(pos.getX() & 15, pos.getY(), pos.getZ() & 15), state, 63)));
            var build = HologramProjectionRenderer.class.getDeclaredMethod("build", HologramChunk.class); build.setAccessible(true);
            var mesh = build.invoke(renderer, snapshot);
            var buffer = (com.mojang.blaze3d.vertex.VertexBuffer)field(mesh, "buffer");
            try{ require(reads[0] > 0 && buffer != null && !buffer.isInvalid(), "Closed tank produced no GPU mesh"); }
            finally{ if(buffer != null) buffer.close(); }
        }catch(ReflectiveOperationException err){ throw new AssertionError("Cannot exercise closed tank meshes", err); }
        finally{ models.put(closed, original); }
    }

    private static HologramProjectionFrame projection(){ return new HologramProjectionFrame(Vec3.ZERO, new Quaternionf(), new Vec3(1, 1, 1), Vec3.ZERO); }
    private static HologramChunk surface(BlockEntity entity, Map<BlockPos, Float> speeds){
        var pos = entity.getBlockPos();
        return new HologramChunk(new ChunkPos(pos), 1, 2, 3,
                List.of(new HologramChunk.SurfaceBlock(new BlockPos(pos.getX() & 15, pos.getY(), pos.getZ() & 15), entity.getBlockState(), 63)), speeds);
    }

    private static Object field(Object owner, String name){
        try{
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        }catch(ReflectiveOperationException err){ throw new AssertionError("Cannot inspect retained rendering state", err); }
    }
    private static void require(boolean val, String message){ if(!val) throw new AssertionError(message); }

    private static void setField(Object owner, String name, Object val){
        try{
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(owner, val);
        }catch(ReflectiveOperationException err){ throw new AssertionError("Cannot set the validation clock", err); }
    }

    private static final class Shared{
        private final List<Instance> instances = new ArrayList<>();
        private final List<Handle> handles = new ArrayList<>();
        private int transforms;
    }

    private static final class Context implements VisualEmbedding{
        private final Vec3i origin;
        private final Shared shared;
        private Context(Vec3i origin){ this(origin, new Shared()); }
        private Context(Vec3i origin, Shared shared){ this.origin = origin; this.shared = shared; }
        @Override public Vec3i renderOrigin(){ return origin; }
        @Override public VisualEmbedding createEmbedding(Vec3i origin){ return new Context(origin, shared); }
        @Override public void transforms(Matrix4fc pose, Matrix3fc normal){ shared.transforms++; }
        @Override public void delete(){}
        @Override public InstancerProvider instancerProvider(){
            return new InstancerProvider(){
                @Override public <I extends Instance> Instancer<I> instancer(InstanceType<I> type, Model model, int bias){
                    return new Instancer<>(){
                        @Override public I createInstance(){
                            var handle = new Handle();
                            I instance = type.create(handle);
                            shared.handles.add(handle); shared.instances.add(instance);
                            return instance;
                        }
                        @Override public void stealInstance(I instance){}
                    };
                }
            };
        }
    }
    private static final class Handle implements InstanceHandle{
        private int changes;
        private boolean deleted;
        @Override public void setChanged(){ changes++; }
        @Override public void setDeleted(){ deleted = true; }
        @Override public void setVisible(boolean visible){}
        @Override public boolean isVisible(){ return !deleted; }
    }
    private static final class Sink implements VertexConsumer{
        private int vertices;
        @Override public VertexConsumer addVertex(float x, float y, float z){ vertices++; return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a){ return this; }
        @Override public VertexConsumer setUv(float u, float v){ return this; }
        @Override public VertexConsumer setUv1(int u, int v){ return this; }
        @Override public VertexConsumer setUv2(int u, int v){ return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){ return this; }
    }
}
