package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.RopeWinchUnstickWindow;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Skip Sable collision briefly while a Rope Winch unsticks the player
@Mixin(targets = "dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision")
public class SubLevelEntityCollisionPlayerBypassMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Skip the ignored tracking sub levels
    @Redirect(
            method = "collide",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/ActiveSableCompanion;getTrackingSubLevel(Lnet/minecraft/world/entity/Entity;)Ldev/ryanhcode/sable/sublevel/SubLevel;"
            )
    )
    private static SubLevel createthrusters$skipIgnoredTrackingSubLevels(ActiveSableCompanion helper, Entity entity) {
        SubLevel trackingSubLevel = helper.getTrackingSubLevel(entity);
        return RopeWinchUnstickWindow.shouldIgnore(entity, trackingSubLevel) ? null : trackingSubLevel;
    }

    // Skip the ignored intersecting sub levels
    @Redirect(
            method = "collide",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/ActiveSableCompanion;getAllIntersecting(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;)Ljava/lang/Iterable;"
            )
    )
    private static Iterable<SubLevel> createthrusters$skipIgnoredIntersectingSubLevels(ActiveSableCompanion helper,
                                                                                        Level level,
                                                                                        BoundingBox3dc bounds,
                                                                                        Entity entity,
                                                                                        Vec3 collisionMotionMoj,
                                                                                        Vec3 velocityMotionMoj,
                                                                                        LevelReusedVectors sink) {
        return RopeWinchUnstickWindow.filter(entity, helper.getAllIntersecting(level, bounds));
    }
}
