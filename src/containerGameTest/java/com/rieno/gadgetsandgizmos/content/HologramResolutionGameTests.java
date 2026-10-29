package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramResolution;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class HologramResolutionGameTests{
    @GameTest(template = "acc_presentation_empty")
    public static void resolutionIsClientOnlyAndPreservesExistingQuality(GameTestHelper helper){
        String key = "client.worldHologramProjectorResolution";
        helper.assertTrue(CTConfigs.CLIENT_SPEC.getSpec().contains(key), "Hologram resolution was not registered on the client");
        helper.assertTrue(!CTConfigs.SERVER_SPEC.getSpec().contains(key) && !CTConfigs.COMMON_SPEC.getSpec().contains(key), "Client quality setting leaked into server configuration");
        helper.assertTrue(CTConfigs.CLIENT.worldHologramProjectorResolution.getDefault() == HologramResolution.FULL, "Existing holograms changed quality by default");
        for(var mode : HologramResolution.values()){
            var config = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
            config.set(key, mode.name()); CTConfigs.CLIENT_SPEC.correct(config);
            helper.assertTrue(mode.name().equals(config.get(key)), "Config rejected a supported hologram resolution");
        }
        helper.succeed();
    }
}
