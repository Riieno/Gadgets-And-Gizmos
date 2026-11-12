package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinVariant;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinVariants;
import com.rieno.gadgetsandgizmos.content.SupporterHeads;
import com.rieno.gadgetsandgizmos.lib.client.render.FramebufferGuiRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.PlayerSkinCache;
import com.rieno.gadgetsandgizmos.lib.profile.PlayerSkinLookup;
import com.rieno.gadgetsandgizmos.registry.CTEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// Exercise local fallbacks and live UUID skins through the real mannequin and head renderers
final class SupporterSkinClientValidation{
    private static final Map<PlayerMannequinVariant, PlayerMannequinEntity> MANNEQUINS = new LinkedHashMap<>();
    private static long started;
    private static boolean prepared;

    private SupporterSkinClientValidation(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @SuppressWarnings("unchecked")
    static boolean validate(Minecraft mc, Path out) throws Exception{
        var field = PlayerMannequinSkinResolver.class.getDeclaredField("SUPPORTER_SKINS"); field.setAccessible(true);
        var cache = (PlayerSkinCache)field.get(null);
        if(!prepared){
            started = System.nanoTime();
            var profiles = PlayerSkinCache.class.getDeclaredField("profiles"); profiles.setAccessible(true);
            var entries = (Map<UUID, Object>)profiles.get(cache);
            var entryType = Class.forName(PlayerSkinCache.class.getName() + "$Entry");
            var constructor = entryType.getDeclaredConstructor(CompletableFuture.class, long.class); constructor.setAccessible(true);
            for(var variant : PlayerMannequinVariants.all()){
                var id = PlayerMannequinVariants.skinProfile(variant);
                if(id == null) continue;
                var entity = CTEntityTypes.PLAYER_MANNEQUIN.get().create(mc.level);
                require(entity != null, "The supporter mannequin could not be created");
                entity.setVariant(variant); entity.setSteveSkin(false); MANNEQUINS.put(variant, entity);
                var pending = new CompletableFuture<PlayerSkin>();
                entries.put(id, constructor.newInstance(pending, System.nanoTime()));
                var fallback = PlayerMannequinSkinResolver.skin(entity);
                require(fallback.textureUrl() == null && fallback.texture().getPath().startsWith("mannequin_skins/bundled_"),
                        "A pending supporter skin did not use its local fallback: " + variant.id());
                require(fallback.model() == (variant.slim() ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE), "The fallback used the live model before loading");
                require(mc.getResourceManager().getResource(variant.skinTexture()).isPresent(), "The packaged supporter texture is missing");
                pending.complete(null);
                require(PlayerMannequinSkinResolver.skin(entity).equals(fallback), "An offline profile replaced its local fallback");
            }
            require(MANNEQUINS.size() == 7, "A built-in supporter is missing a UUID");
            capture(mc, out.resolve("supporter-skins-offline.png"));
            cache.clear(); prepared = true;
        }
        boolean ready = true;
        for(var entry : MANNEQUINS.entrySet()){
            var skin = PlayerMannequinSkinResolver.skin(entry.getValue());
            if(skin.textureUrl() == null){ ready = false; continue; }
            var id = PlayerMannequinVariants.skinProfile(entry.getKey());
            var profile = PlayerSkinLookup.lookupUuid(id).getNow(null);
            require(profile != null && profile.found() && profile.profile().getId().equals(id), "The live profile resolved by a different identity");
            require(skin.textureUrl().replace("http://", "https://").equals(profile.skinUrl()), "The rendered skin did not match its UUID profile");
            require((skin.model() == PlayerSkin.Model.SLIM) == profile.slim(), "The live arm model did not match Mojang's skin metadata");
            require(skin.texture().equals(PlayerMannequinSkinResolver.supporterSkin(entry.getKey()).texture()), "The supporter head and mannequin used different live skins");
        }
        if(!ready){
            require(System.nanoTime() - started < 75_000_000_000L, "Supporter skins did not finish loading from the UUID service");
            return false;
        }
        capture(mc, out.resolve("supporter-skins-live.png"));
        var result = new StringBuilder("PASSED all seven UUIDs, pending and offline packaged fallbacks, live head/mannequin rendering and slim/wide metadata\n");
        for(var variant : MANNEQUINS.keySet()) result.append(variant.id()).append(' ').append(PlayerMannequinVariants.skinProfile(variant)).append('\n');
        Files.writeString(out.resolve("supporter-skins-result.txt"), result);
        return true;
    }

    private static void capture(Minecraft mc, Path path) throws Exception{
        try(var renderer = new FramebufferGuiRenderer(ResourceLocation.fromNamespaceAndPath("createthrusters", "supporter_skin_validation"))){
            renderer.render(1470, 420, 1470, 420, gui -> {
                gui.fill(0, 0, 1470, 420, 0xFF18202B);
                int idx = 0;
                for(var entry : MANNEQUINS.entrySet()){
                    int x = 10 + idx++ * 207;
                    gui.drawString(mc.font, entry.getKey().displayName(), x + 10, 15, -1);
                    gui.drawString(mc.font, PlayerMannequinVariants.skinProfile(entry.getKey()).toString().substring(0, 8), x + 10, 29, 0xFF91A9B8);
                    gui.pose().pushPose();
                    try{
                        gui.pose().translate(x + 70, 55, 0); gui.pose().scale(3, 3, 3);
                        gui.renderItem(SupporterHeads.createStack(entry.getKey()), 0, 0);
                    }finally{ gui.pose().popPose(); }
                    InventoryScreen.renderEntityInInventoryFollowsAngle(gui, x + 10, 110, x + 195, 410, 120, 0, 0, 0, entry.getValue());
                }
            });
            RenderSystem.bindTexture(mc.getTextureManager().getTexture(renderer.texture()).getId());
            try(var image = new NativeImage(1470, 420, false)){
                image.downloadTexture(0, false); image.flipY(); image.writeToFile(path);
            }
        }
    }

    private static void require(boolean val, String message){ if(!val) throw new AssertionError(message); }
}
