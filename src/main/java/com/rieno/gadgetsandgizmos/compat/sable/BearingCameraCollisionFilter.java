package com.rieno.gadgetsandgizmos.compat.sable;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.content.AileronBearingLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.VectorBearingLinkBlockEntity;
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

// Ignore bearing-owned mounted sublevels during contraption camera collision checks
public final class BearingCameraCollisionFilter {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<UUID, BlockPos> BEARING_SUB_LEVELS = new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the bearing camera collision filter
    private BearingCameraCollisionFilter() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add every bearing-owned sublevel to the camera's ignored connected chain
    public static Collection<SubLevel> extendIgnoredChain(Collection<SubLevel> connectedChain) {
        Map<SubLevel, Boolean> matches = new IdentityHashMap<>();
        return extendContains(connectedChain, candidate -> candidate instanceof SubLevel subLevel
                && matches.computeIfAbsent(
                        subLevel, BearingCameraCollisionFilter::isBearingSubLevel));
    }

    // Get the extend contains
    static <T> Collection<T> extendContains(Collection<T> original, Predicate<Object> additionalContains) {
        return new ExtendedContainsCollection<>(original, additionalContains);
    }

    // Check if this sublevel belongs to one of this addon's bearing heads
    private static boolean isBearingSubLevel(SubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved()) {
            return false;
        }

        UUID id = subLevel.getUniqueId();
        BlockPos cachedPos = id == null ? null : BEARING_SUB_LEVELS.get(id);
        if (cachedPos != null) {
            BlockEntity cached = SubLevelBlockEntityCollector.getBlockEntity(subLevel, cachedPos);
            if (isBearingLink(cached)) {
                return true;
            }
            BEARING_SUB_LEVELS.remove(id, cachedPos);
        }

        for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
            if (isBearingLink(blockEntity)) {
                if (id != null) {
                    BEARING_SUB_LEVELS.put(id, blockEntity.getBlockPos().immutable());
                }
                return true;
            }
        }
        return false;
    }

    // Check whether a block entity is the mounted link for one of this addon's bearing types
    private static boolean isBearingLink(BlockEntity blockEntity) {
        return blockEntity instanceof AileronBearingLinkBlockEntity
                || blockEntity instanceof ThrusterBearingLinkBlockEntity
                || blockEntity instanceof VectorBearingLinkBlockEntity;
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
