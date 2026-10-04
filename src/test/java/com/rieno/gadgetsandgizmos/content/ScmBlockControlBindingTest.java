package com.rieno.gadgetsandgizmos.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScmBlockControlBindingTest {
    @BeforeAll
    static void bootstrap() {
        ControllerTestBootstrap.bootstrap();
    }

    @Test
    void explicitControlModesSelectDifferentTargetScopes() {
        BlockPos pos = new BlockPos(3, 70, 9);
        var state = Blocks.STONE.defaultBlockState();

        assertEquals(ContraptionNetworkLinkerData.TargetScope.BLOCK,
                ContraptionNetworkLinkerData.resolveTargetScope(null, pos, state, Direction.NORTH,
                        ContraptionNetworkLinkerData.TargetMode.BLOCK));
        assertEquals(ContraptionNetworkLinkerData.TargetScope.FACE,
                ContraptionNetworkLinkerData.resolveTargetScope(null, pos, state, Direction.NORTH,
                        ContraptionNetworkLinkerData.TargetMode.FACE));
        assertEquals(ContraptionNetworkLinkerData.TargetScope.FACE,
                ContraptionNetworkLinkerData.resolveTargetScope(null, pos, state, Direction.NORTH,
                        ContraptionNetworkLinkerData.TargetMode.AUTO));
    }

    @Test
    void blockAndRedstoneBindingsRemainSeparateAfterSaving() {
        UUID subLevelId = UUID.randomUUID();
        BlockPos blockPos = new BlockPos(3, 70, 9);
        var block = new ContraptionNetworkLinkerData.LinkedTarget(blockPos, subLevelId,
                "createthrusters:thruster", "Thruster",
                ContraptionNetworkLinkerData.LinkMode.SCM,
                ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        var face = new ContraptionNetworkLinkerData.LinkedTarget(blockPos.relative(Direction.NORTH), subLevelId,
                "createthrusters:contraption_network_linker_plane", "Thruster North",
                ContraptionNetworkLinkerData.LinkMode.SCM,
                ContraptionNetworkLinkerData.TargetScope.FACE,
                List.of(new ContraptionNetworkLinkerData.LinkedFace(Direction.NORTH, "North", "")));

        var root = ContraptionNetworkLinkerData.writeRoot(List.of(block, face),
                ContraptionNetworkLinkerData.LinkMode.SCM,
                ContraptionNetworkLinkerData.TargetMode.BLOCK);

        assertEquals(ContraptionNetworkLinkerData.TargetMode.BLOCK,
                ContraptionNetworkLinkerData.TargetMode.byId(root.getString("TargetMode")));
        assertEquals(2, ContraptionNetworkLinkerData.readTargets(root).size());
        assertTrue(ContraptionNetworkLinkerData.readTargets(root).stream()
                .anyMatch(target -> target.scope() == ContraptionNetworkLinkerData.TargetScope.BLOCK));
        assertTrue(ContraptionNetworkLinkerData.readTargets(root).stream()
                .anyMatch(target -> target.scope() == ContraptionNetworkLinkerData.TargetScope.FACE));
    }

    @Test
    void savedAutoGroupUsesDirectControlWhenBothScopesExist() {
        UUID subLevelId = UUID.randomUUID();
        BlockPos blockPos = new BlockPos(3, 70, 9);
        var direct = new ScmConfigurationProfile.UnitReference(
                subLevelId, blockPos, "direct_thruster", null);
        var face = direct.withFace(Direction.NORTH);
        CompoundTag savedGroup = new CompoundTag();
        savedGroup.putString("Id", "action_" + ScmConfigurationProfile.AUTO_ACTION);
        savedGroup.putString("Label", "Auto");
        ListTag units = new ListTag();
        units.add(face.toTag());
        units.add(direct.toTag());
        savedGroup.put("Units", units);

        var restored = ScmConfigurationProfile.Group.fromTag(savedGroup);

        assertEquals(1, restored.units().size());
        assertTrue(restored.units().contains(direct));
    }
}
