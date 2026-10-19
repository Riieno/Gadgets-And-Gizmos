package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.advanced.*;
import com.rieno.gadgetsandgizmos.lib.display.DisplayWidgetProjection;
import com.rieno.gadgetsandgizmos.lib.display.WidgetCanvasProjection;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.function.Function;

// Use the same canvas transform for drawing and picking projected controls
public final class AccDisplayWidgetGeometry{
    private AccDisplayWidgetGeometry(){}

    public static WidgetCanvasProjection.Layout layout(AccDisplayBlockEntity display, AdvancedGraphDocument.Node node,
                                                        int width, int height){
        int canvasWidth = node.data().getInt("WidgetWidth");
        int canvasHeight = node.data().getInt("WidgetHeight");
        return WidgetCanvasProjection.layout("acc_hologram_widget".equals(node.type()),
                canvasWidth > 1 ? canvasWidth : 320, canvasHeight > 1 ? canvasHeight : 180,
                width, height, number(display, node, "size_x"), number(display, node, "size_y"),
                number(display, node, "offset_x"), number(display, node, "offset_y"),
                number(display, node, "offset_z"), number(display, node, "projection_rotation"),
                AccDisplayBlockEntity.PIXELS_PER_BLOCK);
    }

    private static double number(AccDisplayBlockEntity display, AdvancedGraphDocument.Node node, String port){
        return display.value(node.id(), port).asNumber();
    }

    public static double verticalCenter(AccDisplayBlock.DisplayType type, AccDisplayBlock.Alignment alignment){
        if(type != AccDisplayBlock.DisplayType.HALF_PANEL && type != AccDisplayBlock.DisplayType.SLAB) return 0.5;
        return switch(alignment){ case NEGATIVE -> 0.25; case CENTER -> 0.5; case POSITIVE -> 0.75; };
    }

    public static double renderPlane(AccDisplayBlock.DisplayType type, AccDisplayBlock.Alignment alignment){
        double outward = switch(type){
            case BLOCK, SLAB -> -0.502;
            case BOARD -> switch(alignment){ case NEGATIVE -> -0.502; case CENTER -> -0.3145; case POSITIVE -> -0.002; };
            case PANEL, HALF_PANEL -> switch(alignment){ case NEGATIVE -> -0.502; case CENTER -> -0.09575; case POSITIVE -> 0.3105; };
        };
        return outward - 0.01;
    }

    public static @Nullable Hit pick(AccDisplayBlockEntity display, Vec3 eye, Vec3 direction, double reach){
        CompoundTag frame = display.displayFrame();
        if(!display.isNetworkRoot() || !"graph".equals(frame.getString("State"))
                || !"widgets".equals(frame.getString("Mode"))) return null;
        var state = display.getBlockState();
        Direction facing = state.getValue(AccDisplayBlock.FACING);
        Vec3 right = Vec3.atLowerCornerOf(display.screenRight().getNormal());
        Vec3 down = new Vec3(0, -1, 0);
        var type = ((AccDisplayBlock)state.getBlock()).displayType();
        double ppb = AccDisplayBlockEntity.PIXELS_PER_BLOCK;
        Vec3 center = Vec3.atLowerCornerOf(display.getBlockPos()).add(0.5,
                verticalCenter(type, state.getValue(AccDisplayBlock.Y_ALIGNMENT)) - (display.networkHeight() - 1) * 0.5, 0.5)
                .add(right.scale((display.networkWidth() - 1) * 0.5))
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(-renderPlane(type, state.getValue(AccDisplayBlock.Z_ALIGNMENT))));
        Vec3 topLeft = center.subtract(right.scale(display.surfacePixelWidth() / (2 * ppb)))
                .subtract(down.scale(display.surfacePixelHeight() / (2 * ppb)));
        Vec3 worldTopLeft = SimulatedHelper.toGlobalWorldPosition(display, topLeft);
        Vec3 worldRight = SimulatedHelper.toGlobalWorldPosition(display, topLeft.add(right)).subtract(worldTopLeft).normalize();
        Vec3 worldDown = SimulatedHelper.toGlobalWorldPosition(display, topLeft.add(down)).subtract(worldTopLeft).normalize();
        Hit best = null;
        var nodes = display.graph().nodes();
        for(int ni = nodes.size() - 1; ni >= 0; ni--){
            var node = nodes.get(ni);
            if(!"acc_hologram_widget".equals(node.type()) || !display.targetsThisDisplay(node)
                    || !display.value(node.id(), "visible").asBoolean()) continue;
            var layout = layout(display, node, display.surfacePixelWidth(), display.surfacePixelHeight());
            var point = WidgetCanvasProjection.intersect(eye, direction, reach, worldTopLeft, worldRight, worldDown, layout,
                    AccDisplayBlockEntity.PIXELS_PER_BLOCK);
            if(point == null || best != null && point.distance() >= best.distance()) continue;
            ListTag elements = node.data().getList("WidgetElements", Tag.TAG_COMPOUND);
            for(int i = elements.size() - 1; i >= 0; i--){
                CompoundTag elm = AdvancedHudElementBinding.resolvedCopy(elements.getCompound(i), port -> display.value(node.id(), port));
                if(!AdvancedHudInteractions.isInteractiveType(elm.getString("Type")) || !elm.getBoolean("Visible")) continue;
                var bounds = new DisplayWidgetProjection.Bounds((int)Math.round(elm.getInt("X") * layout.scaleX()),
                        (int)Math.round(elm.getInt("Y") * layout.scaleY()),
                        Math.max(1, (int)Math.round(elm.getInt("W") * layout.scaleX())),
                        Math.max(1, (int)Math.round(elm.getInt("H") * layout.scaleY())));
                var local = DisplayWidgetProjection.unproject(bounds, point.x() * layout.scaleX(), point.y() * layout.scaleY(),
                        net.minecraft.util.Mth.clamp(elm.getDouble("Scale"), 0.01, 100), elm.getDouble("Rotation"));
                if(!local.isInside(bounds)) continue;
                best = new Hit(node, elm, point.distance(), local.horizontalFraction(bounds));
                break;
            }
        }
        return best;
    }

    public record Hit(AdvancedGraphDocument.Node node, CompoundTag element, double distance, double fraction){}
}
