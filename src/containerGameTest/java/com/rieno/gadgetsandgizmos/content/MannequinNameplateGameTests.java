package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.pose.ArmorStandPoseData;
import com.rieno.gadgetsandgizmos.neoforge.network.MannequinSkinChangePayload;
import com.rieno.gadgetsandgizmos.registry.CTEntityTypes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class MannequinNameplateGameTests{
    private MannequinNameplateGameTests(){}

    @GameTest(template = "empty_container", batch = "mannequin_nameplates")
    public static void poseVisibilityAndSkinNamesSurviveSkinChangesAndReload(GameTestHelper ctx) throws Exception{
        var mannequin = new PlayerMannequinEntity(CTEntityTypes.PLAYER_MANNEQUIN.get(), ctx.getLevel());
        mannequin.setVariant(PlayerMannequinVariants.CHRISTEROPH);
        mannequin.setOriginalSupporterVariant(PlayerMannequinVariants.CHRISTEROPH);
        mannequin.setCustomName(PlayerMannequinVariants.CHRISTEROPH.displayName());
        mannequin.setCustomNameVisible(true);
        var pose = ArmorStandPoseData.fromEntity(mannequin);
        pose.nameVisible = false;
        ArmorStandPoseData.applyAllowedTag(mannequin, pose.toTag());
        ctx.assertTrue(!mannequin.isCustomNameVisible(), "Poser did not hide the nameplate");

        var remote = MannequinSkinChangePayload.class.getDeclaredMethod("applyRemoteSkin",
                PlayerMannequinEntity.class, String.class, String.class, boolean.class);
        remote.setAccessible(true);
        ctx.assertTrue((boolean)remote.invoke(null, mannequin, "Canonical_Name",
                "https://textures.minecraft.net/texture/nameplate_test", true), "Skin was rejected");
        ctx.assertTrue("Canonical_Name".equals(mannequin.getDisplayName().getString()), "Custom skin retained the supporter name");
        ctx.assertTrue(!mannequin.isCustomNameVisible(), "Changing skin enabled the hidden nameplate");

        var restored = new PlayerMannequinEntity(CTEntityTypes.PLAYER_MANNEQUIN.get(), ctx.getLevel());
        restored.load(mannequin.saveWithoutId(new CompoundTag()));
        ctx.assertTrue("Canonical_Name".equals(restored.getDisplayName().getString()), "Reload lost the skin's player name");
        ctx.assertTrue(!restored.isCustomNameVisible(), "Reload enabled the hidden nameplate");
        pose = ArmorStandPoseData.fromEntity(restored);
        pose.nameVisible = true;
        ArmorStandPoseData.applyAllowedTag(restored, pose.toTag());
        ctx.assertTrue(restored.isCustomNameVisible(), "Poser could not re-enable the nameplate");

        var supporter = MannequinSkinChangePayload.class.getDeclaredMethod("applySupporterSkin",
                PlayerMannequinEntity.class, PlayerMannequinVariant.class);
        supporter.setAccessible(true);
        supporter.invoke(null, restored, PlayerMannequinVariants.RAYRAY);
        ctx.assertTrue(PlayerMannequinVariants.RAYRAY.displayName().equals(restored.getCustomName()), "Bundled skin retained the custom player name");
        ctx.assertTrue(restored.isCustomNameVisible(), "Bundled skin changed nameplate visibility");
        ctx.assertTrue(restored.getOriginalSupporterVariant().equals(PlayerMannequinVariants.CHRISTEROPH), "Skin changes replaced the original supporter reward");
        ctx.assertTrue(!(boolean)remote.invoke(null, restored, "Rejected_Name", "https://example.invalid/skin", false), "Invalid skin was accepted");
        ctx.assertTrue(PlayerMannequinVariants.RAYRAY.displayName().equals(restored.getCustomName()), "Rejected skin changed the nameplate");
        ctx.succeed();
    }
}
