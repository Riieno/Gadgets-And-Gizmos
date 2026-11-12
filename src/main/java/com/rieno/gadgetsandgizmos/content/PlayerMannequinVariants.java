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
import java.util.UUID;

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
    private static final Map<String, UUID> SKIN_PROFILES = Map.of(
            "rayray", UUID.fromString("5d69650b-0622-4202-bf30-bedd76e9bcce"),
            "djrag", UUID.fromString("82292320-0955-4a8c-bab5-eb0aabf99975"),
            "christeroph", UUID.fromString("ccfa37a4-8ba8-411d-bc1c-b48b7adba688"),
            "zelaux_", UUID.fromString("4260a245-0d34-4475-9f8b-038a1eee3d9c"),
            "riieno", UUID.fromString("94899ef5-d6ad-4dd2-90b1-2f2b4333b6cd"),
            "waniiii_", UUID.fromString("d7c18b2f-2ab7-4363-a024-49c8600a5181"),
            "bigjim", UUID.fromString("413828f5-3633-447c-a291-0a564198ead9"));

    public static final PlayerMannequinVariant RIIENO = registerBuiltIn("riieno", Component.translatable("createthrusters.text.riieno"));
    public static final PlayerMannequinVariant BIGJIM = registerBuiltIn("bigjim", Component.translatable("createthrusters.text.bigjim"));
    public static final PlayerMannequinVariant CHRISTEROPH =
            registerBuiltIn("christeroph", Component.translatable("createthrusters.text.christeroph"), true);
    public static final PlayerMannequinVariant RAYRAY = registerBuiltIn("rayray", Component.translatable("createthrusters.text.rayray"));
    public static final PlayerMannequinVariant DJRAG = registerBuiltIn("djrag", Component.translatable("createthrusters.text.djrag"));
    public static final PlayerMannequinVariant WANIIII = registerBuiltIn("waniiii_", Component.translatable("createthrusters.text.waniiii"));
    public static final PlayerMannequinVariant ZELAUX = registerBuiltIn("zelaux_", Component.translatable("createthrusters.text.zelaux"));

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
        return registerBuiltIn(id, displayName, false);
    }

    // Register a built-in mannequin variant with its matching player arm model
    private static PlayerMannequinVariant registerBuiltIn(String id, Component displayName, boolean slim) {
        String normalizedId = normalize(id);
        return register(normalizedId, displayName, THANKS_KEY_PREFIX + normalizedId,
                REASON_KEY_PREFIX + normalizedId, slim);
    }

    // Register the player mannequin variants
    public static synchronized PlayerMannequinVariant register(String id, Component displayName, String reasonTranslationKey) {
        return register(id, displayName, THANKS_KEY_PREFIX + "generic", reasonTranslationKey, false);
    }

    // Register the player mannequin variants
    public static synchronized PlayerMannequinVariant register(String id, Component displayName,
                                                               String thanksTranslationKey,
                                                               String reasonTranslationKey) {
        return register(id, displayName, thanksTranslationKey, reasonTranslationKey, false);
    }

    // Register one player mannequin variant with its matching slim or wide player arm model
    public static synchronized PlayerMannequinVariant register(String id, Component displayName,
                                                               String thanksTranslationKey,
                                                               String reasonTranslationKey, boolean slim) {
        String normalizedId = normalize(id);
        PlayerMannequinVariant variant = new PlayerMannequinVariant(
                normalizedId,
                displayName,
                thanksTranslationKey,
                reasonTranslationKey,
                ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID,
                        "textures/models/entity/" + normalizedId + ".png"),
                slim);
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

    // Keep supporter skin ownership stable when an account changes its username
    public static UUID skinProfile(PlayerMannequinVariant variant){
        return variant == null ? null : SKIN_PROFILES.get(variant.id());
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
