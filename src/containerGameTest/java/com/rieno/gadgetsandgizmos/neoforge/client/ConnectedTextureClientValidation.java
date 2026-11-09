package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlock;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.lib.client.create.ConnectedTextureModel;
import com.rieno.gadgetsandgizmos.lib.client.create.HorizontalPanelConnectedTextureBehaviour;
import com.rieno.gadgetsandgizmos.lib.client.create.StateConnectedTextureBehaviour;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Validate baked connected textures against real atlas sprites without entering a saved world
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class ConnectedTextureClientValidation{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run once after the client finishes loading its atlas and block models
    private static boolean complete;
    // Count verified quads across the storage and display fixtures
    private static int checkedQuads;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Exercise storage reveals and every registered display orientation and alignment
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        String output = System.getProperty("gadgetsngizmos.connectedTextureValidation");
        if(output == null || complete) return;
        var mc = Minecraft.getInstance();
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.screen.onClose();
        if(mc.getOverlay() != null || !(mc.screen instanceof TitleScreen)) return;
        complete = true;
        Path result = Path.of(output, "result.txt");
        try{
            net.neoforged.fml.config.ConfigTracker.INSTANCE.loadDefaultServerConfigs();
            storage(mc);
            int states = displays(mc);
            preview(mc, Path.of(output));
            Files.writeString(result, "PASSED " + checkedQuads + " textured quads, " + states
                    + " display states, packed side atlas regions, perimeter rotations, tiled backs, storage reloads and unchanged display fronts\n");
        }catch(Throwable err){
            err.printStackTrace();
            try{ Files.writeString(result, "FAILED " + err); }
            catch(Exception writeErr){ throw new RuntimeException(writeErr); }
        }finally{ mc.stop(); }
    }

    // Verify both vault axes and sizes plus tank textures before and after delayed member loading
    private static void storage(Minecraft mc){
        for(Block block : List.of(CTBlocks.SMART_VAULT.get(), CTBlocks.SMART_BATTERY.get(), CTBlocks.SMART_TANK.get())){
            for(Direction.Axis axis : List.of(Direction.Axis.X, Direction.Axis.Z)) for(int width : new int[]{2, 3}){
                TextureWorld world = new TextureWorld();
                BlockState state = block.defaultBlockState();
                if(state.hasProperty(ItemVaultBlock.HORIZONTAL_AXIS)) state = state.setValue(ItemVaultBlock.HORIZONTAL_AXIS, axis);
                if(state.hasProperty(ItemVaultBlock.LARGE)) state = state.setValue(ItemVaultBlock.LARGE, width == 3);
                for(int x = 0; x < width; x++) for(int y = 0; y < width; y++) for(int z = 0; z < width; z++){
                    world.states.put(new BlockPos(x, y, z), state);
                }
                var model = mc.getBlockRenderer().getBlockModel(state);
                require(model instanceof ConnectedTextureModel, "Storage model was not wrapped: " + state);
                List<BlockPos> positions = List.copyOf(world.states.keySet());
                for(BlockPos pos : positions){
                    BlockEntity entity = ((EntityBlock)block).newBlockEntity(pos, state);
                    var tag = new CompoundTag();
                    tag.putInt("Size", width); tag.putInt("Length", width); tag.putInt("Height", width);
                    if(!pos.equals(BlockPos.ZERO)) tag.put("Controller", NbtUtils.writeBlockPos(BlockPos.ZERO));
                    tag.put("LastKnownPos", NbtUtils.writeBlockPos(pos));
                    entity.loadWithComponents(tag, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
                    world.entities.put(pos, entity);
                    model.getModelData(world, pos, state, ModelData.EMPTY);
                }
                for(BlockPos pos : positions){
                    ModelData data = model.getModelData(world, pos, state, ModelData.EMPTY);
                    List<BakedQuad> raw = quads(sourceModel(model), state, ModelData.EMPTY);
                    List<BakedQuad> actual = quads(model, state, data);
                    for(int idx = 0; idx < actual.size(); idx++){
                        BakedQuad quad = actual.get(idx);
                        String texture = quad.getSprite().contents().name().getPath();
                        require(!texture.endsWith("_small") && !texture.equals("missingno")
                                        && !List.of("block/smart_tank/fluid_tank", "block/smart_tank/fluid_tank_top",
                                        "block/smart_tank/fluid_tank_inner").contains(texture),
                                "Hidden or reloaded storage face kept its source texture: " + state + " " + pos + " " + texture);
                        if(texture.endsWith("_connected") || texture.endsWith("_medium") || texture.endsWith("_large")){
                            int expected = expectedTile(world, state, pos, raw.get(idx));
                            require(tile(quad, 4) == expected, "Storage UVs selected the wrong tile: " + state + " " + pos
                                    + " " + quad.getDirection() + " expected " + expected + " got " + tile(quad, 4));
                        }
                        checkedQuads++;
                    }
                }
            }
        }
    }

    // Validate packed atlas artwork on single panels, strips and full multiblocks
    private static int displays(Minecraft mc){
        int states = 0;
        for(var block : List.of(CTBlocks.ACC_DISPLAY.get(), CTBlocks.ACC_DISPLAY_BLOCK.get(), CTBlocks.ACC_DISPLAY_PANEL.get(),
                CTBlocks.ACC_DISPLAY_HALF_PANEL.get(), CTBlocks.ACC_DISPLAY_SLAB.get())){
            for(BlockState state : block.getStateDefinition().getPossibleStates()){
                states++;
                for(int[] size : new int[][]{{1, 1}, {1, 3}, {3, 1}, {3, 3}, {5, 4}}){
                    TextureWorld world = displayWorld(state, size[0], size[1]);
                    checkDisplay(mc, world, state);
                }
            }
        }
        return states;
    }

    // Build fixtures in the panel plane so all four world facings exercise the same artwork
    private static TextureWorld displayWorld(BlockState state, int width, int height){
        TextureWorld world = new TextureWorld();
        Direction right = state.getValue(AccDisplayBlock.FACING).getClockWise();
        for(int x = 0; x < width; x++) for(int y = 0; y < height; y++){
            world.states.put(BlockPos.ZERO.relative(right, x).above(y), state);
        }
        return world;
    }

    // Preserve front UVs and physical face area while checking every casing patch against its artwork
    private static void checkDisplay(Minecraft mc, TextureWorld world, BlockState state){
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        require(model instanceof ConnectedTextureModel, "Display model was not wrapped: " + state);
        BakedModel source = sourceModel(model);
        BakedModel baseline = new CTModel(source, front((AccDisplayBlock)state.getBlock()));
        ModelProperty<String> marker = new ModelProperty<>();
        ModelData input = ModelData.builder().with(marker, "retained").build();
        for(BlockPos pos : world.states.keySet()){
            ModelData data = model.getModelData(world, pos, state, input);
            require("retained".equals(data.get(marker)), "Wrapped model data was discarded");
            List<BakedQuad> raw = quads(source, state, ModelData.EMPTY);
            List<BakedQuad> actual = quads(model, state, data);
            List<BakedQuad> prev = quads(baseline, state, baseline.getModelData(world, pos, state, input));
            List<BakedQuad> oldFront = prev.stream().filter(quad -> !casingSource(quad)).toList();
            List<BakedQuad> newFront = actual.stream().filter(quad -> !casingTarget(quad)).toList();
            require(oldFront.size() == newFront.size(), "Display front geometry changed");
            for(int idx = 0; idx < oldFront.size(); idx++){
                require(Arrays.equals(oldFront.get(idx).getVertices(), newFront.get(idx).getVertices()),
                        "Existing display front UVs changed: " + state + " " + pos);
            }
            for(BakedQuad original : raw){
                if(!casingSource(original)) continue;
                List<BakedQuad> patches = actual.stream().filter(ConnectedTextureClientValidation::casingTarget)
                        .filter(quad -> quad.getDirection() == original.getDirection()).toList();
                require(Math.abs(area(original) - patches.stream().mapToDouble(ConnectedTextureClientValidation::area).sum()) < .00001,
                        "Casing patches changed the face area: " + state);
                if(original.getSprite().contents().name().getPath().contains("back")){
                    checkBack(state, original, patches);
                }else{
                    checkEdge(world, state, pos, original, patches);
                }
            }
            checkedQuads += actual.size();
        }
    }

    // Identify standalone casing sprites without matching the working screen artwork
    private static boolean casingSource(BakedQuad quad){
        String path = quad.getSprite().contents().name().getPath();
        return path.endsWith("_face") || path.equals("block/advanced_display_back");
    }

    // Identify both packed casing atlases in the mapped geometry
    private static boolean casingTarget(BakedQuad quad){
        String path = quad.getSprite().contents().name().getPath();
        return path.equals("block/advanced_display_back") || path.equals("block/advanced_display_side");
    }

    // Keep every back plate on its original atlas region regardless of adjacent panels
    private static void checkBack(BlockState state, BakedQuad original, List<BakedQuad> patches){
        require(patches.size() == 1, "Tiled back plates must remain a single face");
        BakedQuad quad = patches.getFirst();
        require(quad.getSprite() == original.getSprite() && Arrays.equals(quad.getVertices(), original.getVertices()),
                "Adjacent displays changed the tiled back mapping: " + state);
        var type = ((AccDisplayBlock)state.getBlock()).displayType();
        boolean half = type == AccDisplayBlock.DisplayType.SLAB || type == AccDisplayBlock.DisplayType.HALF_PANEL;
        atlasRect(quad, 32, 0, half ? 16 : 0, 16, half ? 8 : 16, state);
    }

    // Check real top, middle and bottom strips and keep their metal edge toward the display front
    private static void checkEdge(TextureWorld world, BlockState state, BlockPos pos, BakedQuad original, List<BakedQuad> patches){
        Direction face = original.getDirection(), facing = state.getValue(AccDisplayBlock.FACING);
        Direction up = uvDirection(original, true);
        require(uvDirection(original, false) == facing.getOpposite(), "Perimeter texture runs across the wrong depth axis: " + state + " " + face);
        require(up == (face == Direction.UP ? facing.getClockWise() : face == Direction.DOWN ? facing.getCounterClockWise() : Direction.UP),
                "Perimeter texture runs along the wrong panel axis: " + state + " " + face);
        boolean above = touches(world, state, pos, up), below = touches(world, state, pos, up.getOpposite());
        var type = ((AccDisplayBlock)state.getBlock()).displayType();
        boolean rail = type == AccDisplayBlock.DisplayType.PANEL || type == AccDisplayBlock.DisplayType.HALF_PANEL;
        boolean half = !face.getAxis().isVertical()
                && (type == AccDisplayBlock.DisplayType.SLAB || type == AccDisplayBlock.DisplayType.HALF_PANEL);
        boolean board = type == AccDisplayBlock.DisplayType.BOARD;
        boolean rear = rail || board && state.getValue(AccDisplayBlock.Z_ALIGNMENT) == AccDisplayBlock.Alignment.CENTER;
        require(patches.size() == (rail ? (half ? 2 : 3) : rear ? 2 : 1), "Perimeter contains unexpected patches");
        for(BakedQuad quad : patches){
            String sprite = quad.getSprite().contents().name().getPath();
            if(sprite.equals("block/advanced_display_back")){
                int py = half ? 16 : above ? 2 : 0;
                int stripHeight = half ? 8 : 16 - (above ? 2 : 0) - (below ? 2 : 0);
                atlasRect(quad, 32, rail ? 28 : 24, py, rail ? 1 : 2, stripHeight, state);
                require(Math.abs(depthPixels(quad, facing.getAxis()) - (rail ? 1 : 2)) < .0001, "Rear strip lost its native width");
                continue;
            }
            require(sprite.equals("block/advanced_display_side"), "Edge used unrelated artwork");
            require(Math.abs(depthPixels(quad, facing.getAxis()) - (rail ? 2 : board ? 8 : 16)) < .0001,
                    "Perimeter artwork stretched across the wrong physical depth: " + state);
            if(rail && half){ atlasRect(quad, 64, 48, 56, 2, 8, state); continue; }
            if(half){ atlasRect(quad, 64, 32, 40, 16, 8, state); continue; }
            if(rail){
                boolean top = center(quad).subtract(center(original)).dot(net.minecraft.world.phys.Vec3.atLowerCornerOf(up.getNormal())) > 0;
                atlasRect(quad, 64, top ? (above ? 16 : 0) : (below ? 16 : 32), top ? 48 : 56, 2, 8, state);
                continue;
            }
            boolean narrow = board;
            int x = above ? (narrow ? 48 : 16) : (narrow ? 32 : 0);
            int y = above && below || !above && !below ? 0 : 16;
            atlasRect(quad, 64, x, y, narrow ? 8 : 16, 16, state);
        }
    }

    // Measure the actual depth occupied by a vent, rail or rear strip in model pixels
    private static double depthPixels(BakedQuad quad, Direction.Axis axis){
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for(int idx = 0; idx < 4; idx++){
            var pos = BakedQuadHelper.getXYZ(quad.getVertices(), idx);
            double val = axis.choose(pos.x, pos.y, pos.z);
            min = Math.min(min, val); max = Math.max(max, val);
        }
        return (max - min) * 16;
    }

    // Compare sampled pixel rectangles with the atlas layout rather than a regular tile formula
    private static void atlasRect(BakedQuad quad, int size, int x, int y, int width, int height, BlockState state){
        float minU = Float.POSITIVE_INFINITY, minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
        for(int idx = 0; idx < 4; idx++){
            float u = net.createmod.catnip.render.SpriteShiftEntry.getUnInterpolatedU(quad.getSprite(), BakedQuadHelper.getU(quad.getVertices(), idx)) * size;
            float v = net.createmod.catnip.render.SpriteShiftEntry.getUnInterpolatedV(quad.getSprite(), BakedQuadHelper.getV(quad.getVertices(), idx)) * size;
            minU = Math.min(minU, u); maxU = Math.max(maxU, u);
            minV = Math.min(minV, v); maxV = Math.max(maxV, v);
        }
        require(Math.abs(minU - x) < .05 && Math.abs(maxU - x - width) < .05
                        && Math.abs(minV - y) < .05 && Math.abs(maxV - y - height) < .05,
                "Wrong casing artwork: " + state + " " + quad.getDirection() + " expected " + x + "," + y + "," + width + "," + height
                        + " got " + minU + "," + minV + "," + maxU + "," + maxV);
        var image = quad.getSprite().contents().getOriginalImage();
        for(int py = y; py < y + height; py++) for(int px = x; px < x + width; px++){
            require((image.getPixelRGBA(px, py) >>> 24) == 255, "Casing samples transparent atlas padding");
        }
    }

    // Measure face area independently of how many patches it contains
    private static double area(BakedQuad quad){
        int[] vertices = quad.getVertices();
        var a = BakedQuadHelper.getXYZ(vertices, 1).subtract(BakedQuadHelper.getXYZ(vertices, 0));
        var b = BakedQuadHelper.getXYZ(vertices, 3).subtract(BakedQuadHelper.getXYZ(vertices, 0));
        return a.cross(b).length();
    }

    // Locate a patch within its original face using physical vertex positions
    private static net.minecraft.world.phys.Vec3 center(BakedQuad quad){
        var res = net.minecraft.world.phys.Vec3.ZERO;
        for(int idx = 0; idx < 4; idx++) res = res.add(BakedQuadHelper.getXYZ(quad.getVertices(), idx));
        return res.scale(.25);
    }

    // Retain an actual GPU render of the five casing shapes from their back and top edges
    private static void preview(Minecraft mc, Path output) throws Exception{
        int width = 1400, height = 700;
        var blocks = List.of(CTBlocks.ACC_DISPLAY_BLOCK.get(), CTBlocks.ACC_DISPLAY.get(), CTBlocks.ACC_DISPLAY_PANEL.get(),
                CTBlocks.ACC_DISPLAY_SLAB.get(), CTBlocks.ACC_DISPLAY_HALF_PANEL.get());
        try(var renderer = new FramebufferGuiRenderer(texture("casing_validation_preview"))){
            renderer.render(width, height, width, height, graphics -> {
                graphics.fill(0, 0, width, height, 0xFFADB8B9);
                Lighting.setupFor3DItems();
                for(int idx = 0; idx < blocks.size(); idx++){
                    var block = blocks.get(idx);
                    var state = block.defaultBlockState().setValue(AccDisplayBlock.FACING, Direction.NORTH);
                    TextureWorld world = displayWorld(state, 3, 3);
                    graphics.drawString(mc.font, block.displayType().name(), idx * 280 + 15, 15, 0xFF111111, false);
                    for(int view = 0; view < 2; view++){
                        var poses = graphics.pose();
                        poses.pushPose();
                        poses.translate(idx * 280 + 120, view * 330 + 280, 200);
                        poses.scale(48, -48, 48);
                        poses.mulPose(Axis.XP.rotationDegrees(view == 0 ? 15 : 55));
                        poses.mulPose(Axis.YP.rotationDegrees(view == 0 ? 25 : -25));
                        poses.translate(-1.5, 0, -.5);
                        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
                        for(BlockPos pos : world.states.keySet()){
                            poses.pushPose(); poses.translate(pos.getX(), pos.getY(), pos.getZ());
                            mc.getBlockRenderer().getModelRenderer().renderModel(poses.last(), graphics.bufferSource().getBuffer(RenderType.cutout()),
                                    state, model, 1, 1, 1, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                                    model.getModelData(world, pos, state, ModelData.EMPTY), null);
                            poses.popPose();
                        }
                        graphics.flush(); poses.popPose();
                    }
                }
            });
            RenderSystem.bindTexture(mc.getTextureManager().getTexture(renderer.texture()).getId());
            try(var image = new NativeImage(width, height, false)){
                image.downloadTexture(0, false); image.flipY(); image.writeToFile(output.resolve("casing-preview.png"));
            }
        }
    }

    // Reproduce the original front behaviour independently of the new casing layer
    private static ConnectedTextureBehaviour front(AccDisplayBlock block){
        boolean small = block.displayType() == AccDisplayBlock.DisplayType.HALF_PANEL
                || block.displayType() == AccDisplayBlock.DisplayType.SLAB;
        var type = small ? AllCTTypes.HORIZONTAL_KRYPPERS : AllCTTypes.OMNIDIRECTIONAL;
        String name = small ? "advanced_display_small" : "advanced_display";
        var screen = CTSpriteShifter.getCT(type, texture(name), texture(name + "_connected"));
        var border = CTSpriteShifter.getCT(type, texture(name + "_border"), texture(name + "_border_connected"));
        return small ? new HorizontalPanelConnectedTextureBehaviour(screen, border)
                : new StateConnectedTextureBehaviour(screen, border);
    }

    // Resolve test sprite shifts in the content namespace
    private static ResourceLocation texture(String path){
        return ResourceLocation.fromNamespaceAndPath("createthrusters", "block/" + path);
    }

    // Access unshifted geometry through the legacy quad path used by inventory models
    private static BakedModel sourceModel(BakedModel model){
        return new BakedModelWrapper<>(model){
            // Preserve source geometry without invoking connected texture layers
            @Override
            public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
                                            net.minecraft.client.renderer.RenderType type){
                return model.getQuads(state, side, rand);
            }
        };
    }

    // Collect both culled and unculled faces to exercise section reveals
    private static List<BakedQuad> quads(BakedModel model, BlockState state, ModelData data){
        List<BakedQuad> res = new ArrayList<>(model.getQuads(state, null, RandomSource.create(42), data, null));
        for(Direction face : Direction.values()) res.addAll(model.getQuads(state, face, RandomSource.create(42), data, null));
        return res;
    }

    // Infer texture directions from baked vertex positions rather than the behaviour's orientation helpers
    private static int expectedTile(TextureWorld world, BlockState state, BlockPos pos, BakedQuad quad){
        Direction right = uvDirection(quad, false), up = uvDirection(quad, true);
        var ctx = new ConnectedTextureBehaviour.CTContext();
        ctx.left = touches(world, state, pos, right.getOpposite());
        ctx.right = touches(world, state, pos, right);
        ctx.up = touches(world, state, pos, up);
        ctx.down = touches(world, state, pos, up.getOpposite());
        return AllCTTypes.RECTANGLE.getTextureIndex(ctx);
    }

    // Find the physical direction in which a quad's U increases or V decreases
    private static Direction uvDirection(BakedQuad quad, boolean vertical){
        int[] vertices = quad.getVertices();
        for(int idx = 1; idx < 4; idx++){
            float du = BakedQuadHelper.getU(vertices, idx) - BakedQuadHelper.getU(vertices, 0);
            float dv = BakedQuadHelper.getV(vertices, idx) - BakedQuadHelper.getV(vertices, 0);
            if(vertical ? Math.abs(du) > .000001F || Math.abs(dv) < .000001F
                    : Math.abs(dv) > .000001F || Math.abs(du) < .000001F) continue;
            var delta = BakedQuadHelper.getXYZ(vertices, idx).subtract(BakedQuadHelper.getXYZ(vertices, 0));
            double scale = Math.signum(vertical ? -dv : du);
            return Direction.getNearest((float)(delta.x * scale), (float)(delta.y * scale), (float)(delta.z * scale));
        }
        throw new AssertionError("Quad has no rectangular UV axis");
    }

    // Require matching neighbours whose geometry reaches the shared block boundary
    private static boolean touches(TextureWorld world, BlockState state, BlockPos pos, Direction dir){
        if(world.getBlockState(pos.relative(dir)) != state) return false;
        var shape = state.getShape(world, pos);
        return shape.min(dir.getAxis()) <= 0 && shape.max(dir.getAxis()) >= 1;
    }

    // Resolve the atlas cell containing the quad's UV centre
    private static int tile(BakedQuad quad, int size){
        float u = 0, v = 0;
        for(int idx = 0; idx < 4; idx++){
            u += BakedQuadHelper.getU(quad.getVertices(), idx) / 4;
            v += BakedQuadHelper.getV(quad.getVertices(), idx) / 4;
        }
        var sprite = quad.getSprite();
        return (int)((u - sprite.getU0()) / (sprite.getU1() - sprite.getU0()) * size)
                + size * (int)((v - sprite.getV0()) / (sprite.getV1() - sprite.getV0()) * size);
    }

    // Fail with the exact state or surface that violated the rendering contract
    private static void require(boolean value, String message){
        if(!value) throw new AssertionError(message);
    }

    // Keep fixture blocks and delayed NBT members independent of any saved world
    private static final class TextureWorld implements BlockAndTintGetter{
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Map<BlockPos, BlockEntity> entities = new HashMap<>();

        // Return only members already installed by the fixture
        @Override public BlockEntity getBlockEntity(BlockPos pos){ return entities.get(pos); }
        // Keep air outside the fixture's block bounds
        @Override public BlockState getBlockState(BlockPos pos){ return states.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        // Use the fixture block's fluid state
        @Override public FluidState getFluidState(BlockPos pos){ return getBlockState(pos).getFluidState(); }
        // Keep model inspection independent of directional lighting
        @Override public float getShade(Direction dir, boolean shade){ return 1; }
        // Avoid constructing a light engine for geometry inspection
        @Override public LevelLightEngine getLightEngine(){ return null; }
        // Keep fixture tint neutral
        @Override public int getBlockTint(BlockPos pos, ColorResolver resolver){ return -1; }
        // Include the fixture and its neighbouring surfaces
        @Override public int getHeight(){ return 384; }
        // Match the standard overworld build range
        @Override public int getMinBuildHeight(){ return -64; }
    }
}
