package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.profile.PlayerSkinLookup;

import java.util.concurrent.CompletableFuture;

// Preserve the mannequin lookup API while sharing profile resolution with head items
public final class PlayerMannequinSkinLookup{
    private PlayerMannequinSkinLookup(){}

    // Resolve one Minecraft player skin through the shared cache
    public static CompletableFuture<LookupResult> lookup(String playerName){
        return PlayerSkinLookup.lookup(playerName).thenApply(res -> new LookupResult(res.found(),
                res.playerName(), res.skinUrl(), res.slim(), Status.valueOf(res.status().name()), res.message()));
    }

    // Normalize one Minecraft player name
    public static String normalizePlayerName(String playerName){
        return PlayerSkinLookup.normalizePlayerName(playerName);
    }

    // Store one completed skin lookup
    public record LookupResult(boolean found, String playerName, String skinUrl, boolean slim,
                               Status status, String message){}

    // Distinguish an unknown player from a profile-service failure
    public enum Status{
        RESOLVED,
        PLAYER_DOES_NOT_EXIST,
        UNAVAILABLE
    }
}
