package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.view.ViewControlPrediction;
import com.rieno.gadgetsandgizmos.lib.view.ViewRaycast;
import com.rieno.gadgetsandgizmos.lib.view.ViewRig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.Set;

// Verify each placed camera's feed axes against its actual server controls and retained joints
@GameTestHolder("createthrusters_camera_mounts")
@PrefixGameTestTemplate(false)
public final class CameraMountGameTests{
    // Check floor mounting including the straight-up control singularity
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void floorCameraUsesImageAxes(GameTestHelper helper){ verify(helper, Direction.UP); }
    // Check ceiling mounting including the straight-down control singularity
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void ceilingCameraUsesImageAxes(GameTestHelper helper){ verify(helper, Direction.DOWN); }
    // Check the north wall's image axes
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void northWallCameraUsesImageAxes(GameTestHelper helper){ verify(helper, Direction.NORTH); }
    // Check the south wall's image axes
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void southWallCameraUsesImageAxes(GameTestHelper helper){ verify(helper, Direction.SOUTH); }
    // Check the east wall's image axes
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void eastWallCameraUsesImageAxes(GameTestHelper helper){ verify(helper, Direction.EAST); }
    // Check the west wall's image axes
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void westWallCameraUsesImageAxes(GameTestHelper helper){ verify(helper, Direction.WEST); }

    // Exercise graph aiming, image-relative controls, prediction and unchanged saved joint values
    private static void verify(GameTestHelper helper, Direction face){
        BlockPos pos = new BlockPos(2, 2, 2);
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createthrusters:camera"));
        helper.setBlock(pos, block.defaultBlockState().setValue(CameraBlock.FACING, face));
        var camera = (CameraBlockEntity)helper.getBlockEntity(pos);
        for(var frame : ViewRig.Orientation.values()) for(double tilt : new double[]{0, -45}){
            camera.configure(64, 0, new ViewRaycast.Filter(Set.of(), false), ViewRig.Mode.MANUAL, frame, 0, tilt);
            var before = camera.viewPose(0);
            helper.assertTrue(before.orientation().transform(new Vector3d(0, 1, 0)).y >= -1.0E-8,
                    "The mounted feed remained upside down");
            var prediction = new ViewControlPrediction(camera.viewAim());
            prediction.sample(-3, 0, 0);
            camera.controlView(-3, 0, 0);
            var after = camera.viewPose(0);
            var right = before.orientation().transform(new Vector3d(1, 0, 0));
            var forward = new Vector3d(after.forward().x, after.forward().y, after.forward().z);
            helper.assertTrue(forward.dot(right) > .04, "Joystick panning rolled the feed instead of moving its sight line");
            helper.assertTrue(camera.viewAim().equals(prediction.preview(0, 0, 0, camera::predictViewAim)),
                    "First-person prediction diverged from mounted server input");
            var physical = ViewRig.worldOrientation(camera.mountOrientation(), frame,
                    new ViewRig.Angles(camera.viewAim().pan(), camera.viewAim().tilt())).transform(new Vector3d(0, 0, -1));
            helper.assertTrue(physical.distance(forward) < 1.0E-8, "Feed and physical lens point in different directions");
            var data = camera.saveWithFullMetadata(helper.getLevel().registryAccess());
            helper.assertTrue(data.getDouble("CameraPan") == camera.viewAim().pan()
                            && data.getDouble("CameraTilt") == camera.viewAim().tilt(),
                    "Mounted controls changed the saved joint format");
        }
        helper.succeed();
    }
}
