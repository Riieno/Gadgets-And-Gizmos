package com.rieno.gadgetsandgizmos.compat.sable;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;

// Repair stale Sable plot section data left by moved or deleted sub-levels
public final class SablePlotSectionDataRepair {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String CHUNKS_KEY = "chunks";
    private static final String SECTIONS_KEY = "sections";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sable plot section data repair
    private SablePlotSectionDataRepair() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Remove the invalid section keys
    public static int removeInvalidSectionKeys(CompoundTag plotTag, int sectionCount) {
        if (plotTag == null || sectionCount <= 0 || !plotTag.contains(CHUNKS_KEY, Tag.TAG_COMPOUND)) {
            return 0;
        }

        int removed = 0;
        CompoundTag chunksTag = plotTag.getCompound(CHUNKS_KEY);
        for (String chunkKey : List.copyOf(chunksTag.getAllKeys())) {
            if (!chunksTag.contains(chunkKey, Tag.TAG_COMPOUND)) {
                continue;
            }

            CompoundTag chunkTag = chunksTag.getCompound(chunkKey);
            if (!chunkTag.contains(SECTIONS_KEY, Tag.TAG_COMPOUND)) {
                continue;
            }

            CompoundTag sectionsTag = chunkTag.getCompound(SECTIONS_KEY);
            for (String sectionKey : List.copyOf(sectionsTag.getAllKeys())) {
                if (!isValidSectionIndex(sectionKey, sectionCount)) {
                    sectionsTag.remove(sectionKey);
                    removed++;
                }
            }
        }
        return removed;
    }

    // Check if the section index is valid
    private static boolean isValidSectionIndex(String sectionKey, int sectionCount) {
        try {
            int sectionIndex = Integer.parseInt(sectionKey);
            return sectionIndex >= 0 && sectionIndex < sectionCount;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
