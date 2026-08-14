package com.rieno.gadgetsandgizmos.compat.sable;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AileronBearingLinkBlockEntity;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

// Ignore the player's own mounted aileron during camera collision checks
public final class AileronBearingCameraCollisionFilter {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<UUID, BlockPos> AILERON_SUB_LEVELS = new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the aileron bearing camera collision filter
    private AileronBearingCameraCollisionFilter() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the extend ignored chain
    public static Collection<SubLevel> extendIgnoredChain(Collection<SubLevel> connectedChain) {
        Map<SubLevel, Boolean> matches = new IdentityHashMap<>();
        return extendContains(connectedChain, candidate -> candidate instanceof SubLevel subLevel
                && matches.computeIfAbsent(
                        subLevel, AileronBearingCameraCollisionFilter::isAileronSubLevel));
    }

    // Get the extend contains
    static <T> Collection<T> extendContains(Collection<T> original, Predicate<Object> additionalContains) {
        return new ExtendedContainsCollection<>(original, additionalContains);
    }

    // Check if this is an aileron sublevel
    private static boolean isAileronSubLevel(SubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved()) {
            return false;
        }

        UUID id = subLevel.getUniqueId();
        BlockPos cachedPos = id == null ? null : AILERON_SUB_LEVELS.get(id);
        if (cachedPos != null) {
            BlockEntity cached = SubLevelBlockEntityCollector.getBlockEntity(subLevel, cachedPos);
            if (cached instanceof AileronBearingLinkBlockEntity) {
                return true;
            }
            AILERON_SUB_LEVELS.remove(id, cachedPos);
        }

        for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
            if (blockEntity instanceof AileronBearingLinkBlockEntity) {
                if (id != null) {
                    AILERON_SUB_LEVELS.put(id, blockEntity.getBlockPos().immutable());
                }
                return true;
            }
        }
        return false;
    }

    // Handle the extended contains collection
    private static final class ExtendedContainsCollection<T> extends AbstractCollection<T> {
        // Original
        private final Collection<T> original;
        // Additional contains
        private final Predicate<Object> additionalContains;

        // Initialize the extended contains collection
        private ExtendedContainsCollection(Collection<T> original, Predicate<Object> additionalContains) {
            this.original = original;
            this.additionalContains = additionalContains;
        }

        // Get the iterator
        @Override
        public Iterator<T> iterator() {
            return original.iterator();
        }

        // Get the size
        @Override
        public int size() {
            return original.size();
        }

        // Check if this contains the value
        @Override
        public boolean contains(Object candidate) {
            return original.contains(candidate) || additionalContains.test(candidate);
        }
    }
}
