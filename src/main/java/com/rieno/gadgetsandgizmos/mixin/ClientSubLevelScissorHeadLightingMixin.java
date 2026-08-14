package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ScissorPistonLinkBlockEntity;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep client sublevel Scissor Head Lighting behavior valid across Sable sub-levels
@Mixin(ClientSubLevel.class)
public abstract class ClientSubLevelScissorHeadLightingMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Sample the scissor head ambient sky
    @Inject(method = "computeSubLevelSkyLight", at = @At("RETURN"), cancellable = true)
    private void createthrusters$sampleScissorHeadAmbientSky(Pose3dc pose, CallbackInfoReturnable<Integer> cir) {
        ClientSubLevel subLevel = (ClientSubLevel) (Object) this;
        if (!createthrusters$hasConnectedScissorHeadLink(subLevel)) {
            return;
        }
        int sampledSky = createthrusters$sampleExternalSkyLight(subLevel);
        if (sampledSky > cir.getReturnValue()) {
            cir.setReturnValue(sampledSky);
        }
    }

    // Check if this has connected scissor head link
    @Unique
    private boolean createthrusters$hasConnectedScissorHeadLink(ClientSubLevel subLevel) {
        if (createthrusters$hasScissorHeadLink(subLevel)) {
            return true;
        }
        try {
            for (SubLevel connected : SubLevelHelper.getConnectedChain(subLevel)) {
                if (connected instanceof ClientSubLevel clientSubLevel
                        && createthrusters$hasScissorHeadLink(clientSubLevel)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    // Check if this has scissor head link
    @Unique
    private boolean createthrusters$hasScissorHeadLink(ClientSubLevel subLevel) {
        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof ScissorPistonLinkBlockEntity) {
                return true;
            }
        }
        return false;
    }

    // Sample the external sky light
    @Unique
    private int createthrusters$sampleExternalSkyLight(ClientSubLevel subLevel) {
        ClientLevel level = subLevel.getLevel();
        BoundingBox3dc bounds = subLevel.boundingBox();
        double centerX = (bounds.minX() + bounds.maxX()) * 0.5D;
        double centerY = (bounds.minY() + bounds.maxY()) * 0.5D;
        double centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5D;
        double minX = bounds.minX() - 0.6D;
        double maxX = bounds.maxX() + 0.6D;
        double minY = bounds.minY() - 0.6D;
        double maxY = bounds.maxY() + 0.6D;
        double minZ = bounds.minZ() - 0.6D;
        double maxZ = bounds.maxZ() + 0.6D;

        int maxLight = 0;
        double[] xs = {minX, centerX, maxX};
        double[] zs = {minZ, centerZ, maxZ};
        for (double x : xs) {
            for (double z : zs) {
                maxLight = Math.max(maxLight, createthrusters$skyLightAt(level, x, maxY, z));
            }
        }

        double[] ys = {minY, centerY, maxY};
        for (double y : ys) {
            maxLight = Math.max(maxLight, createthrusters$skyLightAt(level, minX, y, centerZ));
            maxLight = Math.max(maxLight, createthrusters$skyLightAt(level, maxX, y, centerZ));
            maxLight = Math.max(maxLight, createthrusters$skyLightAt(level, centerX, y, minZ));
            maxLight = Math.max(maxLight, createthrusters$skyLightAt(level, centerX, y, maxZ));
        }
        return maxLight;
    }

    // Get the sky light
    @Unique
    private int createthrusters$skyLightAt(ClientLevel level, double x, double y, double z) {
        return level.getBrightness(LightLayer.SKY, BlockPos.containing(x, y, z));
    }
}
