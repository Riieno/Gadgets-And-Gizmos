package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudCameraSource;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

// Verify widget properties survive saves and remain overridable by graph inputs
@GameTestHolder("createthrusters_visual_graph")
@PrefixGameTestTemplate(false)
public final class WidgetPropertiesGameTests{
    // Resolve live block cameras from the same direct and plane selections offered by the picker
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pickedCamerasResolveLiveSources(GameTestHelper helper){
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(pos, CTBlocks.CAMERA.get().defaultBlockState(), 3);
        var camera = (CameraBlockEntity)helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(camera != null && camera.isViewAvailable(), "The test camera is unavailable");
        var direct = new ContraptionNetworkLinkerData.LinkedTarget(pos, null, "createthrusters:camera", "Camera",
                ContraptionNetworkLinkerData.LinkMode.INPUT, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        var plane = new ContraptionNetworkLinkerData.LinkedTarget(pos.relative(Direction.EAST), null,
                "createthrusters:contraption_network_linker_plane", "Camera plane", ContraptionNetworkLinkerData.LinkMode.INPUT,
                ContraptionNetworkLinkerData.TargetScope.FACE, List.of(new ContraptionNetworkLinkerData.LinkedFace(Direction.EAST, "", "")));
        var multiFace = new ContraptionNetworkLinkerData.LinkedTarget(plane.blockPos(), null, plane.blockId(), plane.label(),
                plane.mode(), plane.scope(), List.of(new ContraptionNetworkLinkerData.LinkedFace(Direction.DOWN, "", ""),
                new ContraptionNetworkLinkerData.LinkedFace(Direction.EAST, "", "")));
        for(var linked : List.of(direct, plane, multiFace)){
            var controller = new AdvancedContraptionControllerBlockEntity(pos.above(),
                    CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()){
                @Override public List<ContraptionNetworkLinkerData.LinkedTarget> getLinkedTargets(){ return List.of(linked); }
            };
            controller.setLevel(helper.getLevel());
            var target = ContraptionNetworkLinkerData.toDiscoveryNodes(List.of(linked)).getFirst();
            var picked = target.withBlockTarget("createthrusters:camera", "Camera", pos);
            for(String type : List.of("advanced_hud_element", "acc_display_widget", "acc_hologram_widget")){
                CompoundTag elm = new CompoundTag(); elm.putString("Type", AdvancedHudCameraSource.TYPE);
                elm.put(AdvancedHudCameraSource.PROPERTY, picked.toTag());
                var node = new AdvancedGraphDocument.Node("widget", type, "Widget", 0, 0, new CompoundTag());
                ViewReference ref = AdvancedHudCameraSource.reference(controller, node, elm, port -> null);
                helper.assertTrue(ViewReference.of(camera).equals(ref), type + " could not resolve the picked " + linked.scope().id() + " camera");
                AdvancedHudElementBinding.bind(elm, AdvancedHudCameraSource.PROPERTY, "camera");
                CompoundTag override = AdvancedHudCameraSource.pickerSelection(picked, List.of(linked));
                ref = AdvancedHudCameraSource.reference(controller, node, elm, port -> AdvancedGraphDocument.Value.target(override));
                helper.assertTrue(ViewReference.of(camera).equals(ref), type + " could not resolve the live camera override");
                elm.put(AdvancedHudCameraSource.PROPERTY, override);
                AdvancedHudElementBinding.bind(elm, AdvancedHudCameraSource.PROPERTY, "");
                var graph = new AdvancedGraphDocument();
                CompoundTag data = AdvancedGraphNodeFactory.createDefaultData(type, null);
                ListTag elements = new ListTag(); elements.add(elm); data.put("WidgetElements", elements);
                graph.nodes().add(new AdvancedGraphDocument.Node("widget", type, "Widget", 0, 0, data));
                var runtime = new GraphRuntime(controller, true);
                try{
                    runtime.replaceSimulationGraph(null, graph); runtime.tickSimulation(graph, null);
                    var snapshot = runtime.liveOutput("widget", "widget");
                    CompoundTag resolved = snapshot.payload().getList("WidgetElements", Tag.TAG_COMPOUND).getCompound(0);
                    helper.assertTrue(ViewReference.of(camera).toTag().equals(resolved.getCompound("ViewReference")),
                            type + " omitted the picked camera from its live Widget output");
                }finally{ runtime.clear(); }
            }
        }
        helper.succeed();
    }

    // Resolve saved and data driven cameras in every shared widget editor
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cameraPropertiesRetainInputOverrides(GameTestHelper helper){
        for(String type : List.of("advanced_hud_element", "acc_display_widget", "acc_hologram_widget")){
            CompoundTag elm = new CompoundTag();
            elm.putString("Type", AdvancedHudCameraSource.TYPE);
            CompoundTag saved = new CompoundTag();
            saved.putString("NodeId", "saved_camera");
            elm.put(AdvancedHudCameraSource.PROPERTY, saved);
            AdvancedHudElementBinding.bind(elm, AdvancedHudCameraSource.PROPERTY, "source");
            elm = roundTrip(type, elm);
            helper.assertTrue(saved.equals(AdvancedHudCameraSource.selection(elm, port -> null, name -> null)),
                    type + " lost its saved camera");
            CompoundTag live = new CompoundTag();
            live.putString("NodeId", "live_camera");
            helper.assertTrue(live.equals(AdvancedHudCameraSource.selection(elm,
                    port -> AdvancedGraphDocument.Value.target(live), name -> null)), type + " ignored its camera override");
            helper.assertTrue(AdvancedHudCameraSource.selection(elm,
                    port -> AdvancedGraphDocument.Value.target(new CompoundTag()), name -> null).isEmpty(),
                    type + " ignored an explicit empty camera override");
            helper.assertTrue(saved.equals(elm.getCompound(AdvancedHudCameraSource.PROPERTY)),
                    type + " overwrote the saved camera with an override");
        }
        helper.succeed();
    }

    // Keep saved slider limits while independently overriding each range property
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void sliderPropertiesRetainInputOverrides(GameTestHelper helper){
        for(String type : List.of("advanced_hud_element", "acc_display_widget", "acc_hologram_widget")){
            CompoundTag elm = new CompoundTag();
            elm.putString("Type", "slider");
            elm.putDouble("Min", -25); elm.putDouble("Max", 75); elm.putDouble("Step", 0.25);
            for(String property : List.of("Min", "Max", "Step")) AdvancedHudElementBinding.bind(elm, property, property);
            elm = roundTrip(type, elm);
            CompoundTag defaults = AdvancedHudElementBinding.resolvedCopy(elm, port -> null);
            helper.assertTrue(defaults.getDouble("Min") == -25 && defaults.getDouble("Max") == 75
                    && defaults.getDouble("Step") == 0.25, type + " lost saved slider properties");
            CompoundTag live = AdvancedHudElementBinding.resolvedCopy(elm, port -> switch(port){
                case "Min" -> AdvancedGraphDocument.Value.number(-50);
                case "Max" -> AdvancedGraphDocument.Value.number(100);
                case "Step" -> AdvancedGraphDocument.Value.number(0.5);
                default -> null;
            });
            helper.assertTrue(live.getDouble("Min") == -50 && live.getDouble("Max") == 100
                    && live.getDouble("Step") == 0.5, type + " ignored slider overrides");
            helper.assertTrue(elm.getDouble("Min") == -25 && elm.getDouble("Max") == 75
                    && elm.getDouble("Step") == 0.25, type + " overwrote saved slider properties");
        }
        helper.succeed();
    }

    // Save and restore an authored element through the graph document
    private static CompoundTag roundTrip(String type, CompoundTag elm){
        var graph = new AdvancedGraphDocument();
        CompoundTag data = new CompoundTag();
        ListTag elements = new ListTag(); elements.add(elm); data.put("WidgetElements", elements);
        graph.nodes().add(new AdvancedGraphDocument.Node("widget", type, "Widget", 0, 0, data));
        return AdvancedGraphDocument.fromTag(graph.toTag()).nodes().getFirst().data()
                .getList("WidgetElements", Tag.TAG_COMPOUND).getCompound(0);
    }
}
