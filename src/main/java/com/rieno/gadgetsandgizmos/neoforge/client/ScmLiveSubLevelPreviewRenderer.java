package com.rieno.gadgetsandgizmos.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingLinkBlockEntity;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelPreviewRenderer;
import com.simibubi.create.content.kinetics.base.IRotate;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * SCM's grouping/control-face adapter over the reusable library preview API.
 * Rendering, live scene collection, transforms, highlights, and ray picking
 * belong to {@link SubLevelPreviewRenderer}; this class keeps SCM-only filter
 * labels and Auto target-scope semantics out of the shared library.
 */
final class ScmLiveSubLevelPreviewRenderer {
    enum Filter {
        ALL("All"),
        KINETIC("Kinetic Blocks"),
        PROPULSION("Propulsion Blocks"),
        THRUSTERS("Thrusters"),
        JOINTS_AND_ACTUATORS("Joints and Actuators"),
        DOCKING_CONNECTORS("Docking Connectors"),
        REDSTONE("Redstone");

        private final String label;

        Filter(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    record PickTarget(String id, UUID subLevelId, BlockPos position, Direction face) {
        PickTarget {
            id = id == null ? "" : id;
            position = position == null ? BlockPos.ZERO : position.immutable();
        }

        PickTarget(String id, UUID subLevelId, BlockPos position) {
            this(id, subLevelId, position, null);
        }
    }

    record Highlight(UUID subLevelId, BlockPos position, Direction face, int color) {
        Highlight {
            position = position == null ? BlockPos.ZERO : position.immutable();
        }

        Highlight(UUID subLevelId, BlockPos position, int color) {
            this(subLevelId, position, null, color);
        }
    }

    record Marker(UUID subLevelId, Vec3 position, Vec3 direction, int color) {
        Marker {
            position = position == null ? Vec3.ZERO : position;
            direction = direction == null ? Vec3.ZERO : direction;
        }
    }

    /** A server-synchronised fallback block for clients outside craft tracking range. */
    record SnapshotBlock(UUID subLevelId, BlockPos position, BlockState state, Vec3 rootPosition) {
        SnapshotBlock {
            position = position == null ? BlockPos.ZERO : position.immutable();
            rootPosition = rootPosition == null ? Vec3.atLowerCornerOf(position) : rootPosition;
        }
    }

    private final SubLevelPreviewRenderer preview = new SubLevelPreviewRenderer(
            ResourceLocation.fromNamespaceAndPath("gadgetsandgizmos", "scm_live_preview"),
            SubLevelPreviewRenderer.DEFAULT_MAX_RENDERED_BLOCKS,
            List.of(ScmLiveSubLevelPreviewRenderer::renderOptionalBlockEntityDetails));
    private final Set<Filter> visibleFilters = EnumSet.of(Filter.ALL);
    private final Set<Filter> wireframeExcludedFilters = EnumSet.noneOf(Filter.class);

    ScmLiveSubLevelPreviewRenderer() {
        preview.setFullBright(true);
    }

    void setBodies(UUID rootSubLevelId, Collection<UUID> subLevelIds) {
        preview.setBodies(rootSubLevelId, subLevelIds);
    }

    void setSnapshotBlocks(Collection<SnapshotBlock> snapshot) {
        List<SubLevelPreviewRenderer.SnapshotBlock> shared = snapshot == null ? List.of() : snapshot.stream()
                .filter(Objects::nonNull)
                .map(block -> new SubLevelPreviewRenderer.SnapshotBlock(block.subLevelId(),
                        block.position(), block.state(), block.rootPosition()))
                .toList();
        preview.setSnapshotBlocks(shared);
    }

    boolean render(GuiGraphics graphics, int x, int y, int width, int height, float partialTick,
                   Collection<Highlight> highlights) {
        return render(graphics, x, y, width, height, partialTick, highlights, List.of());
    }

    boolean render(GuiGraphics graphics, int x, int y, int width, int height, float partialTick,
                   Collection<Highlight> highlights, Collection<Marker> markers) {
        List<SubLevelPreviewRenderer.Highlight> shared = highlights == null ? List.of() : highlights.stream()
                .filter(Objects::nonNull)
                .map(highlight -> new SubLevelPreviewRenderer.Highlight(highlight.subLevelId(),
                        highlight.position(), highlight.face(), highlight.color()))
                .toList();
        List<SubLevelPreviewRenderer.Marker> sharedMarkers = markers == null ? List.of() : markers.stream()
                .filter(Objects::nonNull)
                .map(marker -> new SubLevelPreviewRenderer.Marker(marker.subLevelId(), marker.position(),
                        marker.direction(), marker.color()))
                .toList();
        return preview.render(graphics, x, y, width, height, partialTick, shared, sharedMarkers);
    }

    int blockCount() {
        return preview.blockCount();
    }

    int visibleBlockCount() {
        return preview.visibleBlockCount();
    }

    boolean truncated() {
        return preview.truncated();
    }

    void invalidate() {
        preview.invalidate();
    }

    void close() {
        preview.close();
    }

    void mousePressed(double mouseX, double mouseY, int button) {
        preview.mousePressed(mouseX, mouseY, button);
    }

    void mouseDragged(double deltaX, double deltaY) {
        preview.mouseDragged(deltaX, deltaY);
    }

    void mouseScrolled(double amount) {
        preview.mouseScrolled(amount);
    }

    PickTarget mouseReleased(double mouseX, double mouseY, int button, int viewX, int viewY,
                             int viewWidth, int viewHeight, float partialTick,
                             Collection<PickTarget> targets) {
        Collection<SubLevelPreviewRenderer.PickTarget> shared = targets == null ? List.of() : targets.stream()
                .filter(Objects::nonNull).map(target -> new SubLevelPreviewRenderer.PickTarget(target.id(),
                        target.subLevelId(), target.position(), target.face())).toList();
        SubLevelPreviewRenderer.PickTarget hit = preview.mouseReleased(mouseX, mouseY, button,
                viewX, viewY, viewWidth, viewHeight, partialTick, shared);
        return hit == null ? null : new PickTarget(hit.id(), hit.subLevelId(), hit.position(), hit.face());
    }

    List<PickTarget> pickTargets() {
        return preview.pickTargets().stream().map(target -> new PickTarget(target.id(), target.subLevelId(),
                target.position(), target.face())).toList();
    }

    // Check whether a picked preview block is a docking connector
    boolean isDockingConnector(PickTarget target) {
        if (target == null || target.subLevelId() == null) return false;
        return preview.block(target.subLevelId(), target.position())
                .map(block -> isDockingConnector(block.state())).orElse(false);
    }

    // Check whether one live preview block belongs to an SCM automatic assignment category
    boolean matchesAutoDetectCategory(UUID subLevelId, BlockPos position, String category) {
        if (subLevelId == null || position == null || category == null) return false;
        return preview.block(subLevelId, position).map(block -> switch (category) {
            case "thrusters" -> containsAny(blockPath(block.state()),
                    "thruster", "engine", "rocket", "jet", "fan");
            case "bearings" -> containsAny(blockPath(block.state()),
                    "bearing", "rotor", "hinge");
            case "actuators" -> isJointOrActuator(block.state());
            case "propulsion" -> isPropulsion(block.state());
            default -> false;
        }).orElse(false);
    }

    void setVisibleFilters(Collection<Filter> filters) {
        Set<Filter> normalized = filters == null || filters.isEmpty()
                ? EnumSet.noneOf(Filter.class) : EnumSet.copyOf(filters);
        if (normalized.size() > 1) normalized.remove(Filter.ALL);
        if (visibleFilters.equals(normalized)) return;
        visibleFilters.clear();
        visibleFilters.addAll(normalized);
        preview.setVisibilityPredicate(this::matchesVisibleFilter);
    }

    void setWireframe(boolean wireframe) {
        preview.setWireframe(wireframe);
    }

    /**
     * These categories stay as normal block models while wireframe is enabled.
     * The shared renderer deliberately excludes wireframe geometry from its
     * hit targets, making the surrounding lines click through to these blocks.
     */
    void setWireframeExcludedFilters(Collection<Filter> filters) {
        Set<Filter> normalized = filters == null || filters.isEmpty()
                ? EnumSet.noneOf(Filter.class) : EnumSet.copyOf(filters);
        normalized.remove(Filter.ALL);
        if (wireframeExcludedFilters.equals(normalized)) return;
        wireframeExcludedFilters.clear();
        wireframeExcludedFilters.addAll(normalized);
        preview.setWireframePredicate(block -> !matchesAny(block, wireframeExcludedFilters));
    }

    String blockName(UUID subLevelId, BlockPos position) {
        return preview.blockName(subLevelId, position);
    }

    BlockPos relativePosition(UUID subLevelId, BlockPos position, float partialTick) {
        return preview.relativePosition(subLevelId, position, partialTick);
    }

    Direction automaticControlFace(PickTarget target) {
        if (target == null || target.subLevelId() == null || target.face() == null) return null;
        SubLevelPreviewRenderer.PreviewBlock block = preview.block(target.subLevelId(), target.position()).orElse(null);
        ClientLevel level = Minecraft.getInstance().level;
        ClientSubLevel source = resolve(level, target.subLevelId());
        // A fallback snapshot has no local Sable level to interrogate. Preserve
        // the clicked face in that case: the server remains authoritative when
        // it validates the saved binding.
        if (block == null) return null;
        if (source == null || source.isRemoved()) return target.face();
        return ContraptionNetworkLinkerData.resolveTargetScope(source.getLevel(), block.position(), block.state(),
                target.face(), ContraptionNetworkLinkerData.TargetMode.AUTO).usesFaces() ? target.face() : null;
    }

    private boolean matchesVisibleFilter(SubLevelPreviewRenderer.PreviewBlock block) {
        if (visibleFilters.contains(Filter.ALL)) return true;
        return matchesAny(block, visibleFilters);
    }

    private static boolean matchesAny(SubLevelPreviewRenderer.PreviewBlock block, Collection<Filter> filters) {
        if (filters == null || filters.isEmpty()) return false;
        for (Filter filter : filters) {
            if (switch (filter) {
                case ALL -> true;
                case KINETIC -> block.state().getBlock() instanceof IRotate;
                case PROPULSION -> isPropulsion(block.state());
                case THRUSTERS -> containsAny(blockPath(block.state()), "thruster");
                case JOINTS_AND_ACTUATORS -> isJointOrActuator(block.state());
                case DOCKING_CONNECTORS -> isDockingConnector(block.state());
                case REDSTONE -> isRedstone(block.state());
            }) return true;
        }
        return false;
    }

    private static boolean isPropulsion(BlockState state) {
        String path = blockPath(state);
        return containsAny(path, "thruster", "propulsion", "engine", "rocket", "jet", "sail", "fan");
    }

    // Check whether a block is a movable joint or controlled mechanical actuator
    private static boolean isJointOrActuator(BlockState state) {
        String path = blockPath(state);
        return containsAny(path, "joint", "actuator", "motor", "bearing", "rotor", "hinge",
                "piston", "servo", "gearshift");
    }

    private static boolean isRedstone(BlockState state) {
        if (state == null) return false;
        if (state.hasProperty(BlockStateProperties.POWERED) || state.hasProperty(BlockStateProperties.POWER)
                || state.hasProperty(BlockStateProperties.LIT) || state.hasProperty(BlockStateProperties.ENABLED)) {
            return true;
        }
        return containsAny(blockPath(state), "redstone", "lever", "button", "repeater", "comparator",
                "observer", "torch", "lamp", "wire", "contact", "gearshift", "link");
    }

    // Check whether this is a supported Simulated docking connector
    private static boolean isDockingConnector(BlockState state) {
        if (state == null) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && "simulated".equals(id.getNamespace())
                && "docking_connector".equals(id.getPath());
    }

    private static String blockPath(BlockState state) {
        if (state == null) return "";
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? "" : id.getPath().toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... snippets) {
        for (String snippet : snippets) if (value.contains(snippet)) return true;
        return false;
    }

    private static ClientSubLevel resolve(ClientLevel level, UUID id) {
        if (level == null || id == null) return null;
        try {
            Object value = ClientSubLevelContainer.getContainer(level).getSubLevel(id);
            return value instanceof ClientSubLevel subLevel ? subLevel : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean renderOptionalBlockEntityDetails(BlockEntity blockEntity, BlockState state, PoseStack pose,
                                                            MultiBufferSource buffers, int light, int overlay) {
        if (blockEntity instanceof ThrusterBearingLinkBlockEntity) {
            // Retain the link's ordinary block model exactly once. Its shared
            // kinetic fallback would rotate that complete model over itself.
            ThrusterBearingRenderer.renderPreview(blockEntity, state, pose, buffers, light);
            return true;
        }
        if (RcsThrusterRenderer.renderPreview(blockEntity, state, pose, buffers, light)) {
            return true;
        }
        if (AileronBearingRenderer.renderPreview(blockEntity, state, pose, buffers, light)) {
            return true;
        }
        if (VectorBearingRenderer.renderPreview(blockEntity, state, pose, buffers, light)) {
            return true;
        }
        if (ThrusterBearingRenderer.renderPreview(blockEntity, state, pose, buffers, light)) {
            return true;
        }
        return EmbeddedCopycatsKineticRenderer.render(blockEntity, state, pose, buffers, light, overlay);
    }
}
