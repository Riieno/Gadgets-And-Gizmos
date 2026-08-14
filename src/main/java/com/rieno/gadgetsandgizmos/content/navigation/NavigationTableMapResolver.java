package com.rieno.gadgetsandgizmos.content.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget;
import dev.simulated_team.simulated.index.SimDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Resolve Open Map and JourneyMap data into navigation table markers
public final class NavigationTableMapResolver {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the navigation table map resolver
    private NavigationTableMapResolver() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is navigation map
    public static boolean isNavigationMap(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.has(SimDataComponents.TARGET);
    }

    // Resolve the nearest target
    public static @Nullable ResolvedTarget resolveNearestTarget(Level level, Vec3 origin, ItemStack stack, int slotIndex) {
        return resolveNearestTarget(level, origin, stack, slotIndex, null);
    }

    // Resolve the nearest target
    public static @Nullable ResolvedTarget resolveNearestTarget(Level level, Vec3 origin, ItemStack stack, int slotIndex,
                                                                @Nullable NavTableBlockEntity navTable) {
        if (level == null || origin == null || !isNavigationMap(stack)) {
            return null;
        }

        if (!stack.is(Items.FILLED_MAP)) {
            if (navTable == null) {
                return null;
            }
            NavigationTarget target = NavigationTarget.ofStack(stack);
            if (target == null) {
                return null;
            }
            Vec3 localTargetPos = target.getTarget(navTable, stack);
            if (localTargetPos == null) {
                return null;
            }
            Object targetSubLevel = SimulatedHelper.getContainingSubLevel(level, localTargetPos);
            UUID targetSubLevelId = SimulatedHelper.getSubLevelId(targetSubLevel);
            Vec3 targetPos = SimulatedHelper.projectOutOfSubLevels(level, localTargetPos);
            double distanceSquared = horizontalDistanceSquared(origin, targetPos);
            return new ResolvedTarget(slotIndex, targetPos, "", false, distanceSquared,
                    targetSubLevelId == null ? null : localTargetPos, targetSubLevelId);
        }

        MapId mapId = stack.get(DataComponents.MAP_ID);
        if (mapId == null) {
            return null;
        }

        MapItemSavedData mapData = level.getMapData(mapId);
        if (mapData == null) {
            return null;
        }

        ResolvedTarget nearest = null;
        for (MapBanner banner : mapData.getBanners()) {
            Vec3 bannerPos = banner.pos().getCenter();
            double distanceSquared = horizontalDistanceSquared(origin, bannerPos);
            if (nearest == null || distanceSquared < nearest.distanceSquared()) {
                String label = banner.name().map(Component::getString).orElse("");
                nearest = new ResolvedTarget(slotIndex, bannerPos, label, true, distanceSquared);
            }
        }

        MapDecorations decorations = stack.get(DataComponents.MAP_DECORATIONS);
        if (decorations == null) {
            return nearest;
        }

        for (MapDecorations.Entry decoration : decorations.decorations().values()) {
            Vec3 decorationPos = new Vec3(decoration.x(), origin.y(), decoration.z());
            double distanceSquared = horizontalDistanceSquared(origin, decorationPos);
            if (nearest == null || distanceSquared < nearest.distanceSquared()) {
                nearest = new ResolvedTarget(slotIndex, decorationPos, "", false, distanceSquared);
            }
        }

        return nearest;
    }

    // Get the horizontal distance squared
    private static double horizontalDistanceSquared(Vec3 origin, Vec3 target) {
        double dx = origin.x - target.x;
        double dz = origin.z - target.z;
        return dx * dx + dz * dz;
    }

    // Store the resolved target
    public record ResolvedTarget(int slotIndex, Vec3 targetPos, String label, boolean bannerTarget,
                                 double distanceSquared, @Nullable Vec3 localTargetPos,
                                 @Nullable UUID subLevelId) {
        // Initialize the resolved target
        public ResolvedTarget(int slotIndex, Vec3 targetPos, String label, boolean bannerTarget,
                              double distanceSquared) {
            this(slotIndex, targetPos, label, bannerTarget, distanceSquared, null, null);
        }
    }
}
