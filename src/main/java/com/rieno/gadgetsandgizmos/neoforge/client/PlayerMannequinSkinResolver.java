package com.rieno.gadgetsandgizmos.neoforge.client;

import com.google.common.hash.Hashing;
import com.mojang.blaze3d.platform.NativeImage;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

// Load remote mannequin skins with the same legacy-skin and alpha processing Minecraft uses for players.
final class PlayerMannequinSkinResolver {
    private static final int MAX_SKINS = 128;
    private static final Map<String, ResourceLocation> REMOTE_SKINS = new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<ResourceLocation, ResourceLocation> BUNDLED_SKINS = new HashMap<>();

    private PlayerMannequinSkinResolver() {
    }

    static PlayerSkin skin(PlayerMannequinEntity mannequin) {
        if (mannequin == null || mannequin.usesSteveSkin()) {
            return new PlayerSkin(DefaultPlayerSkin.getDefaultTexture(), null, null, null,
                    PlayerSkin.Model.WIDE, true);
        }
        PlayerSkin.Model model = mannequin.usesSlimSkin() ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;
        String remoteUrl = mannequin.remoteSkinUrl();
        if (!remoteUrl.isBlank()) {
            ResourceLocation texture = remoteTexture(remoteUrl);
            if (texture != null) {
                return new PlayerSkin(texture, remoteUrl, null, null, model, false);
            }
        }
        return new PlayerSkin(bundledTexture(mannequin.getVariant().skinTexture()), null, null, null, model, true);
    }

    private static ResourceLocation bundledTexture(ResourceLocation source) {
        return BUNDLED_SKINS.computeIfAbsent(source, location -> {
            String hash = Hashing.sha1().hashString(location.toString(), StandardCharsets.UTF_8).toString();
            ResourceLocation processed = ResourceLocation.fromNamespaceAndPath(
                    "createthrusters", "mannequin_skins/bundled_" + hash);
            Minecraft.getInstance().getTextureManager().register(processed, new BundledPlayerSkinTexture(location));
            return processed;
        });
    }

    // HttpTexture performs this normalization for downloaded player skins. Packaged skins need it too:
    // transparent pixels on the base model can otherwise hide the body behind a translucent jacket.
    private static final class BundledPlayerSkinTexture extends SimpleTexture {
        private final ResourceLocation source;

        private BundledPlayerSkinTexture(ResourceLocation source) {
            super(source);
            this.source = source;
        }

        @Override
        protected TextureImage getTextureImage(ResourceManager resourceManager) {
            TextureImage texture = TextureImage.load(resourceManager, source);
            try {
                NativeImage image = texture.getImage();
                if (image.getWidth() == 64 && image.getHeight() == 64) {
                    forceOpaque(image, 0, 0, 32, 16);
                    forceOpaque(image, 0, 16, 64, 32);
                    forceOpaque(image, 16, 48, 48, 64);
                }
            } catch (IOException ignored) {
                // Let SimpleTexture report and handle a missing or invalid skin resource.
            }
            return texture;
        }

        private static void forceOpaque(NativeImage image, int x0, int y0, int x1, int y1) {
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    image.setPixelRGBA(x, y, image.getPixelRGBA(x, y) | 0xFF000000);
                }
            }
        }
    }

    private static ResourceLocation remoteTexture(String url) {
        ResourceLocation cached = REMOTE_SKINS.get(url);
        if (cached != null) return cached;

        String hash = Hashing.sha1().hashString(url, StandardCharsets.UTF_8).toString();
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                "createthrusters", "mannequin_skins/" + hash);
        File cache = new File(Minecraft.getInstance().gameDirectory,
                "assets/skins/gadgetsandgizmos/" + hash.substring(0, 2) + "/" + hash);
        Minecraft.getInstance().getTextureManager().register(location,
                new HttpTexture(cache, url, DefaultPlayerSkin.getDefaultTexture(), true, null));
        REMOTE_SKINS.put(url, location);
        if (REMOTE_SKINS.size() > MAX_SKINS) {
            Map.Entry<String, ResourceLocation> oldest = REMOTE_SKINS.entrySet().iterator().next();
            REMOTE_SKINS.remove(oldest.getKey());
            Minecraft.getInstance().getTextureManager().release(oldest.getValue());
        }
        return location;
    }
}
