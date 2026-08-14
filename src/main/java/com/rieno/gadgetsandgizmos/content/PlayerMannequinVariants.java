package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Map mannequin model variants to their saved IDs and dimensions
public final class PlayerMannequinVariants {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String DEFAULT_ID = "riieno";
    private static final String THANKS_KEY_PREFIX = "item.createthrusters.player_mannequin.tooltip.thanks.";
    private static final String REASON_KEY_PREFIX = "item.createthrusters.player_mannequin.tooltip.reason.";

    private static final Map<String, PlayerMannequinVariant> VARIANTS = new LinkedHashMap<>();

    public static final PlayerMannequinVariant RIIENO = registerBuiltIn("riieno", Component.literal("Riieno"));
    public static final PlayerMannequinVariant BIGJIM = registerBuiltIn("bigjim", Component.literal("BigJim"));
    public static final PlayerMannequinVariant CHRISTEROPH = registerBuiltIn("christeroph", Component.literal("Christeroph"));
    public static final PlayerMannequinVariant RAYRAY = registerBuiltIn("rayray", Component.literal("RayRay"));
    public static final PlayerMannequinVariant DJRAG = registerBuiltIn("djrag", Component.literal("Djrag"));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the player mannequin variants
    private PlayerMannequinVariants() {
    }

    // Register a built-in mannequin variant
    private static PlayerMannequinVariant registerBuiltIn(String id, Component displayName) {
        String normalizedId = normalize(id);
        return register(normalizedId, displayName, THANKS_KEY_PREFIX + normalizedId, REASON_KEY_PREFIX + normalizedId);
    }

    // Register the player mannequin variants
    public static synchronized PlayerMannequinVariant register(String id, Component displayName, String reasonTranslationKey) {
        return register(id, displayName, THANKS_KEY_PREFIX + "generic", reasonTranslationKey);
    }

    // Register the player mannequin variants
    public static synchronized PlayerMannequinVariant register(String id, Component displayName,
                                                               String thanksTranslationKey,
                                                               String reasonTranslationKey) {
        String normalizedId = normalize(id);
        PlayerMannequinVariant variant = new PlayerMannequinVariant(
                normalizedId,
                displayName,
                thanksTranslationKey,
                reasonTranslationKey,
                ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID,
                        "textures/models/entity/" + normalizedId + ".png"));
        VARIANTS.put(normalizedId, variant);
        return variant;
    }

    // Register the player mannequin variants
    public static synchronized PlayerMannequinVariant register(String id, Component displayName) {
        return register(id, displayName, REASON_KEY_PREFIX + "generic");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get all player mannequin variants values
    public static synchronized List<PlayerMannequinVariant> all() {
        return List.copyOf(VARIANTS.values());
    }

    // Find the player mannequin variants by id
    public static synchronized PlayerMannequinVariant byId(String id) {
        return VARIANTS.get(normalize(id));
    }

    // Get a mannequin variant by id or use the default
    public static PlayerMannequinVariant byIdOrDefault(String id) {
        PlayerMannequinVariant variant = byId(id);
        if (variant != null) {
            return variant;
        }
        return byId(DEFAULT_ID);
    }

    // Get a mannequin variant by name tag
    public static synchronized PlayerMannequinVariant byNameTag(String name) {
        String normalizedName = normalize(name);
        for (PlayerMannequinVariant variant : VARIANTS.values()) {
            if (normalize(variant.id()).equals(normalizedName)
                    || normalize(variant.displayName().getString()).equals(normalizedName)) {
                return variant;
            }
        }
        return null;
    }

    // Normalize the player mannequin variants
    public static String normalize(String val) {
        if (val == null) {
            return "";
        }
        return val.strip().toLowerCase(Locale.ROOT);
    }
}
