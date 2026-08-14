package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.createmod.catnip.config.ui.ConfigModListScreen;
import net.createmod.catnip.platform.CatnipServices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Locale;

// Match Create config entries by their displayed mod name as well as their ID
@Mixin(value = ConfigModListScreen.class, remap = false)
public abstract class ConfigModListScreenSearchMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Match one Create config search entry
    @WrapOperation(method = "updateFilter",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"))
    private boolean createthrusters$matchDisplayName(String modId, CharSequence query,
                                                     Operation<Boolean> original) {
        if (original.call(modId, query)) {
            return true;
        }
        String normalizedQuery = createthrusters$normalizeSearch(query == null ? "" : query.toString());
        if (normalizedQuery.isEmpty()) {
            return false;
        }
        String displayName = CatnipServices.PLATFORM.getModDisplayName(modId);
        return createthrusters$normalizeSearch(displayName).contains(normalizedQuery);
    }

    // Normalize a Create config search value
    @Unique
    private static String createthrusters$normalizeSearch(String val) {
        if (val == null || val.isBlank()) {
            return "";
        }
        return val.toLowerCase(Locale.ROOT)
                .replace("&", " and ")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
