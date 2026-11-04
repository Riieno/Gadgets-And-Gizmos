package com.rieno.gadgetsandgizmos.compat.createpropulsion;

import com.rieno.gadgetsandgizmos.content.ThrusterDyeData;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectState;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectTarget;
import com.rieno.gadgetsandgizmos.lib.inventory.PaletteDyeStorage;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlock;
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity;
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.ThrusterBlockEntity;
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.creative_thruster.CreativeThrusterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Exercise native cube formation and the real applicator on every multiblock member
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class PropulsionDyeMultiblockGameTests{
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void dyeEveryMemberOfTwoByTwoThrusters(GameTestHelper helper){ verify(helper, 2); }

    @GameTest(template = "acc_presentation_empty", timeoutTicks = 100)
    public static void dyeEveryMemberOfThreeByThreeThrusters(GameTestHelper helper){ verify(helper, 3); }

    private static void verify(GameTestHelper helper, int width){
        if(!ModList.get().isLoaded("createpropulsion") || !PropulsionDyeCompatibility.enabled()){
            helper.succeed();
            return;
        }
        NativeThrusters.verify(helper, width);
        helper.succeed();
    }

    // Keep optional implementation classes out of the test holder's signatures
    private static final class NativeThrusters{
        private static void verify(GameTestHelper helper, int width){
            for(String name : List.of("thruster", "creative_thruster", "ion_thruster")){
                for(Direction facing : Direction.values()) verifyCube(helper, width, name, facing);
            }
        }

        private static void verifyCube(GameTestHelper helper, int width, String name, Direction facing){
            var level = helper.getLevel();
            var origin = helper.absolutePos(new BlockPos(1, 2, 1));
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createpropulsion:" + name));
            var state = block.defaultBlockState().setValue(AbstractThrusterBlock.FACING, facing);
            var members = new ArrayList<AbstractThrusterBlockEntity>();
            for(int x = 0; x < width; x++){
                for(int y = 0; y < width; y++){
                    for(int z = 0; z < width; z++){
                        var pos = origin.offset(x, y, z);
                        level.setBlockAndUpdate(pos, state);
                        members.add((AbstractThrusterBlockEntity)level.getBlockEntity(pos));
                    }
                }
            }
            try{
                var controller = members.getFirst();
                var nativeClass = controller instanceof CreativeThrusterBlockEntity
                        ? CreativeThrusterBlockEntity.class : ThrusterBlockEntity.class;
                var assemble = nativeClass.getDeclaredMethod("tryAssemble");
                assemble.setAccessible(true);
                assemble.invoke(controller);
                helper.assertTrue(controller.isController() && controller.width == width,
                        "Native cube did not form: " + name + " " + width + " " + facing);
                String nativeDye = controller.getDyeId();
                var player = FakePlayerFactory.getMinecraft(level);
                player.getAbilities().instabuild = true;
                int idx = 0;
                for(var member : members){
                    var target = (ColorEffectTarget)member;
                    helper.assertTrue(member.isMultiblock() && target.colorEffectTarget() == controller,
                            "Member did not resolve its native controller: " + member.getBlockPos());
                    var preset = ColorEffectPreset.fromColors(List.of(0xFF0000 | ++idx, 0x0000FF), (idx & 1) == 0).withAnimated(false);
                    var applicator = CTItems.THRUSTER_DYE.get().getDefaultInstance();
                    ThrusterDyeData.write(applicator, new PaletteDyeStorage(), preset);
                    player.setItemInHand(InteractionHand.MAIN_HAND, applicator);
                    var pos = member.getBlockPos();
                    player.setPos(pos.getX(), pos.getY(), pos.getZ());
                    var hit = new BlockHitResult(Vec3.atCenterOf(pos), facing, pos, false);
                    var result = applicator.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                    helper.assertTrue(result.consumesAction() && ((ColorEffectTarget)controller).colorEffect().equals(preset),
                            "Applicator did not dye the shared plume: " + pos);
                    for(var other : members){
                        helper.assertTrue(((ColorEffectTarget)other).colorEffect().equals(preset), "Member retained a different plume preset");
                    }
                    var packet = controller.getUpdateTag(level.registryAccess());
                    helper.assertTrue(ColorEffectPreset.read(packet.getCompound(ColorEffectState.NBT_KEY)).equals(preset),
                            "Controller update packet lost the plume preset");
                    var loaded = BlockEntity.loadStatic(controller.getBlockPos(), controller.getBlockState(),
                            controller.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
                    helper.assertTrue(loaded instanceof ColorEffectTarget effect && effect.colorEffect().equals(preset),
                            "Controller save lost the plume preset");
                    helper.assertTrue(Objects.equals(nativeDye, controller.getDyeId()), "Applicator replaced the native dye");
                }
                ((ColorEffectTarget)controller).applyColorEffect(ColorEffectPreset.DEFAULT);
                for(var member : members){
                    helper.assertTrue(((ColorEffectTarget)member).colorEffect().equals(ColorEffectPreset.DEFAULT),
                            "Clearing left a member with a different plume preset");
                }
            }catch(ReflectiveOperationException err){
                throw new IllegalStateException("Failed to form a native Propulsion multiblock", err);
            }finally{
                for(var member : members) level.setBlockAndUpdate(member.getBlockPos(), Blocks.AIR.defaultBlockState());
            }
        }
    }
}
