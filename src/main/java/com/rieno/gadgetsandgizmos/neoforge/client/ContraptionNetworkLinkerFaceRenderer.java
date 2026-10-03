package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.client.render.WorldAreaOverlayRenderer;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerArea;
import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerAreaAdjustPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Draw the Contraption Network Linker Face
public final class ContraptionNetworkLinkerFaceRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation PLANE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "textures/block/contraption_network_linker_plane.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final TargetColor INPUT_COLOR = new TargetColor(0.2f, 0.45f, 0.95f);
    private static final TargetColor OUTPUT_COLOR = new TargetColor(0.95f, 0.24f, 0.28f);
    private static final TargetColor SCM_COLOR = new TargetColor(0.20f, 0.90f, 0.32f);
    private static final TargetColor MACHINE_INPUT_COLOR = new TargetColor(1.0f, 0.82f, 0.10f);
    private static final TargetColor MACHINE_OUTPUT_COLOR = new TargetColor(0.72f, 0.32f, 0.96f);
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked command targets
    private static List<ContraptionNetworkLinkerData.LinkedTarget> commandTargets = List.of();
    private static List<ContraptionNetworkLinkerData.LinkedArea> commandAreas = List.of();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the contraption network linker face
    private ContraptionNetworkLinkerFaceRenderer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the render world event
    public static void onRenderWorld(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        List<ContraptionNetworkLinkerData.LinkedTarget> targets = new ArrayList<>(commandTargets);
        List<ContraptionNetworkLinkerData.LinkedArea> areas = new ArrayList<>(commandAreas);
        ItemStack linker = heldLinker(minecraft.player);
        ContraptionNetworkLinkerData.LinkedArea draft = null;
        if (!linker.isEmpty()) {
            targets.addAll(ContraptionNetworkLinkerData.readClientTargets(linker));
            areas.addAll(ContraptionNetworkLinkerData.readClientAreas(linker));
            draft = draftArea(minecraft, linker);
        }
        if (targets.isEmpty() && areas.isEmpty() && draft == null) {
            return;
        }

        Camera camera = evt.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack poseStack = evt.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        RenderType plateRenderType = RenderType.entityTranslucentEmissive(PLANE_TEXTURE, true);
        RenderType lineRenderType = RenderType.lines();

        poseStack.pushPose();
        try {
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            VertexConsumer plate = bufferSource.getBuffer(plateRenderType);
            try {
                renderPlates(minecraft, poseStack, plate, targets);
            } finally {
                bufferSource.endBatch(plateRenderType);
            }

            VertexConsumer lines = bufferSource.getBuffer(lineRenderType);
            try {
                renderOutlines(minecraft, poseStack, lines, targets);
            } finally {
                bufferSource.endBatch(lineRenderType);
            }
            renderAreas(minecraft, poseStack, bufferSource, areas, draft);
        } finally {
            poseStack.popPose();
        }
    }

    // Move the wall under the crosshair with sneak and scroll
    public static void onMouseScrolling(InputEvent.MouseScrollingEvent evt){
        Minecraft minecraft = Minecraft.getInstance();
        if(evt.isCanceled() || minecraft.screen != null || minecraft.level == null || minecraft.player == null
                || !minecraft.player.isShiftKeyDown() || evt.getScrollDeltaY() == 0.0D) return;
        ItemStack linker = heldLinker(minecraft.player);
        if(linker.isEmpty() || ContraptionNetworkLinkerData.getClientEditMode(linker)
                != ContraptionNetworkLinkerData.LinkMode.SCM
                || ContraptionNetworkLinkerData.getClientTargetMode(linker)
                != ContraptionNetworkLinkerData.TargetMode.AREA) return;
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 end = eye.add(minecraft.player.getLookAngle().scale(96.0D));
        ContraptionNetworkLinkerData.LinkedArea selected = null;
        WorldAreaOverlayRenderer.Hit selectedHit = null;
        for(var area : ContraptionNetworkLinkerData.readClientAreas(linker)){
            RenderTarget target = resolveRenderTarget(minecraft, area.subLevelId());
            if(target == null) continue;
            var hit = WorldAreaOverlayRenderer.hitFace(transformCorners(target, areaCorners(area.bounds())), eye, end);
            if(hit != null && (selectedHit == null || hit.distance() < selectedHit.distance())){
                selected = area;
                selectedHit = hit;
            }
        }
        if(selected == null) return;
        int blocks = evt.getScrollDeltaY() > 0.0D ? -1 : 1;
        if(selectedHit.face().getAxisDirection() == Direction.AxisDirection.NEGATIVE) blocks = -blocks;
        InteractionHand hand = minecraft.player.getMainHandItem() == linker
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        PacketDistributor.sendToServer(new ContraptionNetworkLinkerAreaAdjustPayload(
                hand, selected.id(), selectedHit.face(), blocks));
        evt.setCanceled(true);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the plates
    private static void renderPlates(Minecraft minecraft,
                                     PoseStack poseStack,
                                     VertexConsumer plate,
                                     List<ContraptionNetworkLinkerData.LinkedTarget> targets) {
        for (ContraptionNetworkLinkerData.LinkedTarget target : targets) {
            if (target.scope() == ContraptionNetworkLinkerData.TargetScope.BLOCK) {
                continue;
            }
            RenderTarget renderTarget = resolveRenderTarget(minecraft, target.subLevelId());
            if (renderTarget == null) {
                continue;
            }
            TargetColor col = colorFor(target);
            for (ContraptionNetworkLinkerData.LinkedFace face : target.faces()) {
                Direction plateSide = visiblePlateSide(minecraft, target, face);
                Vec3[] corners = transformCorners(renderTarget, visiblePlateCorners(target.blockPos(),
                        plateSide, face.face()));
                Vec3 normal = transformDirection(renderTarget, directionVector(plateSide));
                renderTexturedPlate(poseStack, plate, corners, normal,
                        col.red(), col.green(), col.blue(), 0.2f);
            }
        }
    }

    // Draw the outlines
    private static void renderOutlines(Minecraft minecraft,
                                       PoseStack poseStack,
                                       VertexConsumer lines,
                                       List<ContraptionNetworkLinkerData.LinkedTarget> targets) {
        for (ContraptionNetworkLinkerData.LinkedTarget target : targets) {
            RenderTarget renderTarget = resolveRenderTarget(minecraft, target.subLevelId());
            if (renderTarget == null) {
                continue;
            }
            TargetColor col = colorFor(target);
            if (target.scope() == ContraptionNetworkLinkerData.TargetScope.BLOCK) {
                renderBlockOutline(poseStack, lines, renderTarget, target.blockPos(),
                        col.red(), col.green(), col.blue(), 1.0f);
                continue;
            }
            for (ContraptionNetworkLinkerData.LinkedFace face : target.faces()) {
                Direction plateSide = visiblePlateSide(minecraft, target, face);
                Vec3[] corners = transformCorners(renderTarget, visiblePlateCorners(target.blockPos(),
                        plateSide, face.face()));
                renderPlateOutline(poseStack, lines, corners,
                        col.red(), col.green(), col.blue(), 1.0f);
            }
        }
    }

    // Get the color
    private static TargetColor colorFor(ContraptionNetworkLinkerData.LinkedTarget target) {
        return switch (target.mode()) {
            case INPUT -> INPUT_COLOR;
            case OUTPUT -> OUTPUT_COLOR;
            case SCM -> SCM_COLOR;
        };
    }

    // Draw an occupied face cell on the exposed surface while retaining its original support
    private static Direction visiblePlateSide(Minecraft minecraft, ContraptionNetworkLinkerData.LinkedTarget target,
                                              ContraptionNetworkLinkerData.LinkedFace face){
        var targetLevel = SubLevelBlockEntityCollector.resolveTargetLevel(minecraft.level, target.subLevelId());
        if(targetLevel != null && targetLevel.isLoaded(target.blockPos())){
            var state = targetLevel.getBlockState(target.blockPos());
            if(!state.isAir() && !(state.getBlock() instanceof
                    com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerPlaneBlock)) return face.face();
        }
        return face.face().getOpposite();
    }

    // Offset an occupied cell's plate beyond the new block face so depth testing keeps it visible
    private static Vec3[] visiblePlateCorners(BlockPos pos, Direction plateSide, Direction linkedFace){
        Vec3[] corners = plateCorners(pos, plateSide);
        if(plateSide != linkedFace) return corners;
        Vec3 offset = directionVector(plateSide).scale(0.004D);
        for(int idx = 0; idx < corners.length; idx++) corners[idx] = corners[idx].add(offset);
        return corners;
    }

    // Set the command highlights
    public static void setCommandHighlights(boolean visible, CompoundTag root) {
        commandTargets = visible ? ContraptionNetworkLinkerData.readTargets(root) : List.of();
        commandAreas = visible ? ContraptionNetworkLinkerData.readAreas(root) : List.of();
    }

    // Preview the second corner while the linker remains in area mode
    private static ContraptionNetworkLinkerData.LinkedArea draftArea(Minecraft minecraft, ItemStack linker){
        var mode = ContraptionNetworkLinkerData.getClientTargetMode(linker);
        if(ContraptionNetworkLinkerData.getClientEditMode(linker) != ContraptionNetworkLinkerData.LinkMode.SCM
                || mode != ContraptionNetworkLinkerData.TargetMode.AREA
                && mode != ContraptionNetworkLinkerData.TargetMode.NO_ENTRY) return null;
        ContraptionNetworkLinkerData.AreaStart start = ContraptionNetworkLinkerData.readClientAreaStart(linker);
        if(start == null) return null;
        BlockPos corner = minecraft.hitResult instanceof BlockHitResult hit ? hit.getBlockPos()
                : BlockPos.containing(minecraft.player.getEyePosition().add(minecraft.player.getLookAngle().scale(5.0D)));
        var selected = SimulatedHelper.resolveBlockPositionIncludingSubLevels(minecraft.level, corner);
        if(!java.util.Objects.equals(start.subLevelId(), selected.subLevelId())) return null;
        try{
            return new ContraptionNetworkLinkerData.LinkedArea(new UUID(0L, 0L), start.subLevelId(),
                    new WorkerArea(start.pos(), selected.blockPos()), "",
                    "", mode == ContraptionNetworkLinkerData.TargetMode.NO_ENTRY
                    ? ContraptionNetworkLinkerData.AreaKind.NO_ENTRY
                    : ContraptionNetworkLinkerData.AreaKind.MACHINE, List.of(), List.of());
        }catch(IllegalArgumentException ignored){
            return null;
        }
    }

    // Draw saved and pending SCM areas in the same world space as linker faces
    private static void renderAreas(Minecraft minecraft, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                    List<ContraptionNetworkLinkerData.LinkedArea> areas,
                                    ContraptionNetworkLinkerData.LinkedArea draft){
        if(areas.isEmpty() && draft == null) return;
        List<ContraptionNetworkLinkerData.LinkedArea> visible = new ArrayList<>(areas);
        if(draft != null) visible.add(draft);
        RenderType walls = RenderType.entityTranslucentEmissive(
                ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png"), true);
        VertexConsumer wallVertices = buffers.getBuffer(walls);
        for(var area : visible){
            RenderTarget target = resolveRenderTarget(minecraft, area.subLevelId());
            if(target == null) continue;
            boolean noEntry = area.kind() == ContraptionNetworkLinkerData.AreaKind.NO_ENTRY;
            WorldAreaOverlayRenderer.walls(pose, wallVertices, transformCorners(target, areaCorners(area.bounds())),
                    noEntry ? 0.96F : 0.16F, noEntry ? 0.12F : 0.94F,
                    noEntry ? 0.12F : 0.28F, 0.10F);
        }
        buffers.endBatch(walls);
        VertexConsumer edges = buffers.getBuffer(RenderType.lines());
        for(var area : visible){
            RenderTarget target = resolveRenderTarget(minecraft, area.subLevelId());
            if(target == null) continue;
            boolean noEntry = area.kind() == ContraptionNetworkLinkerData.AreaKind.NO_ENTRY;
            WorldAreaOverlayRenderer.edges(pose, edges, transformCorners(target, areaCorners(area.bounds())),
                    noEntry ? 1.0F : 0.12F, noEntry ? 0.16F : 0.96F,
                    noEntry ? 0.16F : 0.24F);
        }
        buffers.endBatch(RenderType.lines());
        VertexConsumer plate = buffers.getBuffer(RenderType.entityTranslucentEmissive(PLANE_TEXTURE, true));
        VertexConsumer portLines = buffers.getBuffer(RenderType.lines());
        for(var area : visible){
            RenderTarget target = resolveRenderTarget(minecraft, area.subLevelId());
            if(target == null) continue;
            for(var port : area.ports()){
                TargetColor col = port.role() == ContraptionNetworkLinkerData.MachinePortRole.INPUT
                        ? MACHINE_INPUT_COLOR : MACHINE_OUTPUT_COLOR;
                Vec3[] corners = transformCorners(target, visiblePlateCorners(port.pos(), port.face(), port.face()));
                renderTexturedPlate(pose, plate, corners, transformDirection(target, directionVector(port.face())),
                        col.red(), col.green(), col.blue(), 0.25F);
                renderPlateOutline(pose, portLines, corners, col.red(), col.green(), col.blue(), 1.0F);
            }
        }
        buffers.endBatch(RenderType.entityTranslucentEmissive(PLANE_TEXTURE, true));
        buffers.endBatch(RenderType.lines());
    }

    private static Vec3[] areaCorners(WorkerArea area){
        double x0 = area.min().getX() - 0.01D;
        double y0 = area.min().getY() - 0.01D;
        double z0 = area.min().getZ() - 0.01D;
        double x1 = area.max().getX() + 1.01D;
        double y1 = area.max().getY() + 1.01D;
        double z1 = area.max().getZ() + 1.01D;
        return new Vec3[]{
                new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1),
                new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1)
        };
    }

    // Get the held linker
    private static ItemStack heldLinker(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof ContraptionNetworkLinkerItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof ContraptionNetworkLinkerItem) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    // Resolve the render target
    private static RenderTarget resolveRenderTarget(Minecraft minecraft, UUID subLevelId) {
        if (minecraft.level == null) {
            return null;
        }
        if (subLevelId == null) {
            return new RenderTarget(null);
        }
        Object subLevel = SubLevelBlockEntityCollector.getSubLevel(minecraft.level, subLevelId);
        return subLevel == null ? null : new RenderTarget(subLevel);
    }

    // Draw the block outline
    private static void renderBlockOutline(PoseStack poseStack, VertexConsumer consumer, RenderTarget target,
                                           BlockPos pos, float red, float green, float blue, float alpha) {
        Vec3[] corners = transformCorners(target, blockCorners(pos));
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            addLine(poseStack, consumer, corners[edge[0]], corners[edge[1]], red, green, blue, alpha);
        }
    }

    // Get the block corners
    private static Vec3[] blockCorners(BlockPos pos) {
        double minX = pos.getX() - 0.01D;
        double minY = pos.getY() - 0.01D;
        double minZ = pos.getZ() - 0.01D;
        double maxX = pos.getX() + 1.01D;
        double maxY = pos.getY() + 1.01D;
        double maxZ = pos.getZ() + 1.01D;
        return new Vec3[] {
                new Vec3(minX, minY, minZ),
                new Vec3(maxX, minY, minZ),
                new Vec3(maxX, minY, maxZ),
                new Vec3(minX, minY, maxZ),
                new Vec3(minX, maxY, minZ),
                new Vec3(maxX, maxY, minZ),
                new Vec3(maxX, maxY, maxZ),
                new Vec3(minX, maxY, maxZ)
        };
    }

    // Get the plate corners
    private static Vec3[] plateCorners(BlockPos pos, Direction face) {
        double inset = 0.002D;
        double minX = pos.getX();
        double maxX = pos.getX() + 1.0D;
        double minY = pos.getY();
        double maxY = pos.getY() + 1.0D;
        double minZ = pos.getZ();
        double maxZ = pos.getZ() + 1.0D;
        return switch (face) {
            case NORTH -> new Vec3[] {
                    new Vec3(minX, minY, minZ + inset),
                    new Vec3(maxX, minY, minZ + inset),
                    new Vec3(maxX, maxY, minZ + inset),
                    new Vec3(minX, maxY, minZ + inset)
            };
            case SOUTH -> new Vec3[] {
                    new Vec3(maxX, minY, maxZ - inset),
                    new Vec3(minX, minY, maxZ - inset),
                    new Vec3(minX, maxY, maxZ - inset),
                    new Vec3(maxX, maxY, maxZ - inset)
            };
            case WEST -> new Vec3[] {
                    new Vec3(minX + inset, minY, maxZ),
                    new Vec3(minX + inset, minY, minZ),
                    new Vec3(minX + inset, maxY, minZ),
                    new Vec3(minX + inset, maxY, maxZ)
            };
            case EAST -> new Vec3[] {
                    new Vec3(maxX - inset, minY, minZ),
                    new Vec3(maxX - inset, minY, maxZ),
                    new Vec3(maxX - inset, maxY, maxZ),
                    new Vec3(maxX - inset, maxY, minZ)
            };
            case DOWN -> new Vec3[] {
                    new Vec3(minX, minY + inset, maxZ),
                    new Vec3(maxX, minY + inset, maxZ),
                    new Vec3(maxX, minY + inset, minZ),
                    new Vec3(minX, minY + inset, minZ)
            };
            case UP -> new Vec3[] {
                    new Vec3(minX, maxY - inset, minZ),
                    new Vec3(maxX, maxY - inset, minZ),
                    new Vec3(maxX, maxY - inset, maxZ),
                    new Vec3(minX, maxY - inset, maxZ)
            };
        };
    }

    // Transform the corners
    private static Vec3[] transformCorners(RenderTarget target, Vec3[] localCorners) {
        Vec3[] transformed = new Vec3[localCorners.length];
        for (int idx = 0; idx < localCorners.length; idx++) {
            transformed[idx] = transformPosition(target, localCorners[idx]);
        }
        return transformed;
    }

    // Transform the position
    private static Vec3 transformPosition(RenderTarget target, Vec3 localPosition) {
        if (target == null || target.subLevel() == null) {
            return localPosition;
        }
        Vec3 transformed = SimulatedHelper.toContainingWorldPosition(target.subLevel(), localPosition);
        return transformed == null ? localPosition : transformed;
    }

    // Transform the direction
    private static Vec3 transformDirection(RenderTarget target, Vec3 localDirection) {
        if (target == null || target.subLevel() == null) {
            return localDirection.normalize();
        }
        Vec3 transformed = SimulatedHelper.toContainingWorldDirection(target.subLevel(), localDirection);
        return transformed == null ? localDirection.normalize() : transformed.normalize();
    }

    // Get the direction vector
    private static Vec3 directionVector(Direction dir) {
        return new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }

    // Draw the textured plate
    private static void renderTexturedPlate(PoseStack poseStack, VertexConsumer consumer, Vec3[] corners, Vec3 normal,
                                            float red, float green, float blue, float alpha) {
        if (corners.length < 4) {
            return;
        }
        Matrix4f matrix = poseStack.last().pose();
        addDoubleSidedQuad(matrix, consumer,
                (float) corners[0].x, (float) corners[0].y, (float) corners[0].z,
                (float) corners[1].x, (float) corners[1].y, (float) corners[1].z,
                (float) corners[2].x, (float) corners[2].y, (float) corners[2].z,
                (float) corners[3].x, (float) corners[3].y, (float) corners[3].z,
                red, green, blue, alpha,
                (float) normal.x, (float) normal.y, (float) normal.z);
    }

    // Draw the plate outline
    private static void renderPlateOutline(PoseStack poseStack, VertexConsumer consumer, Vec3[] corners,
                                           float red, float green, float blue, float alpha) {
        if (corners.length < 4) {
            return;
        }
        addLine(poseStack, consumer, corners[0], corners[1], red, green, blue, alpha);
        addLine(poseStack, consumer, corners[1], corners[2], red, green, blue, alpha);
        addLine(poseStack, consumer, corners[2], corners[3], red, green, blue, alpha);
        addLine(poseStack, consumer, corners[3], corners[0], red, green, blue, alpha);
    }

    // Add the double sided quad
    private static void addDoubleSidedQuad(Matrix4f matrix,
                                           VertexConsumer consumer,
                                           float x1, float y1, float z1,
                                           float x2, float y2, float z2,
                                           float x3, float y3, float z3,
                                           float x4, float y4, float z4,
                                           float red, float green, float blue, float alpha,
                                           float normalX, float normalY, float normalZ) {
        addVertex(matrix, consumer, x1, y1, z1, 0.0f, 0.0f, red, green, blue, alpha, normalX, normalY, normalZ);
        addVertex(matrix, consumer, x2, y2, z2, 1.0f, 0.0f, red, green, blue, alpha, normalX, normalY, normalZ);
        addVertex(matrix, consumer, x3, y3, z3, 1.0f, 1.0f, red, green, blue, alpha, normalX, normalY, normalZ);
        addVertex(matrix, consumer, x4, y4, z4, 0.0f, 1.0f, red, green, blue, alpha, normalX, normalY, normalZ);
        addVertex(matrix, consumer, x4, y4, z4, 0.0f, 1.0f, red, green, blue, alpha, -normalX, -normalY, -normalZ);
        addVertex(matrix, consumer, x3, y3, z3, 1.0f, 1.0f, red, green, blue, alpha, -normalX, -normalY, -normalZ);
        addVertex(matrix, consumer, x2, y2, z2, 1.0f, 0.0f, red, green, blue, alpha, -normalX, -normalY, -normalZ);
        addVertex(matrix, consumer, x1, y1, z1, 0.0f, 0.0f, red, green, blue, alpha, -normalX, -normalY, -normalZ);
    }

    // Add the vertex
    private static void addVertex(Matrix4f matrix, VertexConsumer consumer, float x, float y, float z,
                                  float u, float v, float red, float green, float blue, float alpha,
                                  float normalX, float normalY, float normalZ) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setLight(FULL_BRIGHT)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(normalX, normalY, normalZ);
    }

    // Add the line
    private static void addLine(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to,
                                float red, float green, float blue, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        Vec3 normal = to.subtract(from);
        if (normal.lengthSqr() < 1.0E-8D) {
            normal = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            normal = normal.normalize();
        }
        consumer.addVertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .setColor(red, green, blue, alpha)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .setColor(red, green, blue, alpha)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    // Store the render target
    private record RenderTarget(Object subLevel) {
    }

    // Store the target color
    private record TargetColor(float red, float green, float blue) {
    }
}
