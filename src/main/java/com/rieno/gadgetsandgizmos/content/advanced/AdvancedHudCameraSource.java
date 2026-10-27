package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.BoundGlassesCamera;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetSelection;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.lib.view.ViewControlAction;
import com.rieno.gadgetsandgizmos.lib.view.ViewControlInput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.Map;

// Adapt a designer element's selected input to the controller's camera targets
public final class AdvancedHudCameraSource{
    public static final String TYPE = "camera_source";
    public static final String PROPERTY = "CameraSource";
    private AdvancedHudCameraSource(){}

    // Keep resolved camera references separate from the element's original graph input
    public static String runtimePort(CompoundTag elm){
        return "camera_source:" + elm.getString("InteractionId");
    }

    // Supply goggles observers with references even when their controller is outside client range
    public static void addHudReferences(AdvancedContraptionControllerBlockEntity controller,
            AdvancedGraphDocument graph, Map<String, AdvancedGraphDocument.Value> inputs){
        for(var node : graph.nodes()){
            if(!"advanced_hud_element".equals(node.type())) continue;
            for(var raw : node.data().getList("WidgetElements", net.minecraft.nbt.Tag.TAG_COMPOUND)){
                CompoundTag elm = (CompoundTag)raw;
                if(!TYPE.equals(elm.getString("Type"))) continue;
                ViewReference ref = reference(controller, node, elm, port -> {
                    var val = inputs.get(node.id() + ":" + port);
                    return val == null ? AdvancedGraphDocument.Value.fromTag(
                            node.data().getCompound("Defaults").getCompound(port)) : val;
                });
                inputs.put(node.id() + ":" + runtimePort(elm), AdvancedGraphDocument.Value.target(
                        ref == null ? new CompoundTag() : ref.toTag()));
            }
        }
    }

    // Resolve names through the same linker lookup used by Target input ports
    public static CompoundTag selection(CompoundTag elm, Function<String, AdvancedGraphDocument.Value> values,
                                        Function<String, AdvancedGraphDocument.Value> names){
        String port = AdvancedHudElementBinding.port(elm, PROPERTY);
        AdvancedGraphDocument.Value val = port.isBlank() ? null : values.apply(port);
        if(val == null) return new CompoundTag();
        return GraphTargetSelection.resolveNamedBinding(val.type(), val.payload(), name -> {
            var target = names.apply(name);
            return target == null ? null : GraphTargetSelection.resolveBinding(null, target.type(), target.payload());
        });
    }

    // Keep empty or unavailable inputs from falling back to the widget's display target
    public static @Nullable ViewReference reference(AdvancedContraptionControllerBlockEntity controller,
            AdvancedGraphDocument.Node node, CompoundTag elm,
            Function<String, AdvancedGraphDocument.Value> values){
        if(controller == null || controller.getLevel() == null) return null;
        CompoundTag target = selection(elm, values, controller::findNamedGraphTarget);
        var pair = BoundGlassesCamera.pairId(target);
        if(pair != null){
            if(!controller.getGogglesTrackerPairLabels().containsKey(pair)) return null;
            if(controller.getLevel().isClientSide){
                for(var player : controller.getLevel().players()){
                    if(player.isAlive() && AdvancedContraptionControllerBlockEntity.playerHasGogglesPair(player, pair))
                        return ViewReference.of(player);
                }
                return null;
            }
            var src = BoundGlassesCamera.source(controller, target);
            return src == null ? null : ViewReference.of(src.viewEntity());
        }
        if(!target.contains("BlockPos")) return null;
        CompoundTag data = node.data().copy();
        data.put("TargetData", target);
        var selected = new AdvancedGraphDocument.Node(node.id(), node.type(), node.label(), node.x(), node.y(), data);
        var camera = controller.graphCamera(selected, AdvancedGraphDocument.Value.target(target));
        return camera == null || !camera.isViewAvailable() ? null : ViewReference.of(camera);
    }

    // Enter only the camera selected by the current server-side element inputs
    public static boolean open(AdvancedContraptionControllerBlockEntity controller, ServerPlayer player,
            AdvancedGraphDocument.Node node, CompoundTag elm,
            Function<String, AdvancedGraphDocument.Value> values){
        return apply(controller, player, node, elm, values, ViewControlAction.TAKE_CONTROL, null);
    }

    // Decode paired HUD controls without accepting a client-selected camera reference
    public static boolean handle(AdvancedContraptionControllerBlockEntity controller, ServerPlayer player,
            AdvancedGraphDocument.Node node, CompoundTag elm,
            Function<String, AdvancedGraphDocument.Value> values, AdvancedGraphDocument.Value command){
        if(command == null) return false;
        if("boolean".equals(command.type())) return command.asBoolean() && open(controller, player, node, elm, values);
        if(!"map".equals(command.type())) return false;
        ViewControlAction action;
        try{ action = ViewControlAction.valueOf(command.payload().getString("Action")); }
        catch(IllegalArgumentException err){ return false; }
        return apply(controller, player, node, elm, values, action,
                ViewControlInput.fromTag(command.payload().getCompound("Input")));
    }

    // Authorize every overlay command against the current visible element and graph input
    private static boolean apply(AdvancedContraptionControllerBlockEntity controller, ServerPlayer player,
            AdvancedGraphDocument.Node node, CompoundTag elm,
            Function<String, AdvancedGraphDocument.Value> values, ViewControlAction action, ViewControlInput input){
        if(controller.getLevel() == null || controller.getLevel().isClientSide
                || !AdvancedHudElementBinding.bool(elm, "Visible",
                !elm.contains("Visible") || elm.getBoolean("Visible"), values)) return false;
        var visible = values.apply("visible");
        if(visible != null && !visible.asBoolean()) return false;
        ViewReference ref = reference(controller, node, elm, values);
        return ref != null && action.apply(player, ref, input);
    }
}
