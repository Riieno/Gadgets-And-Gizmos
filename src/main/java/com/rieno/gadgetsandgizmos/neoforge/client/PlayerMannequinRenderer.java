package com.rieno.gadgetsandgizmos.neoforge.client;

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import com.rieno.gadgetsandgizmos.lib.client.render.HeadwearRenderSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

// Render the existing mannequin entity through Minecraft's player renderer and baked player models.
public class PlayerMannequinRenderer extends EntityRenderer<PlayerMannequinEntity> {
    private final Map<PlayerMannequinEntity, CachedPlayer> players = new IdentityHashMap<>();
    private int renderCount;

    public PlayerMannequinRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerMannequinEntity mannequin) {
        return PlayerMannequinSkinResolver.skin(mannequin).texture();
    }

    // Swap the entity before the dispatcher chooses a renderer or enters the shader render pass.
    public static MannequinRenderPlayer renderPlayerFor(PlayerMannequinEntity mannequin) {
        EntityRenderer<? super PlayerMannequinEntity> renderer =
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mannequin);
        if (!(renderer instanceof PlayerMannequinRenderer mannequinRenderer)) {
            throw new IllegalStateException("Player mannequin renderer is not registered");
        }
        MannequinRenderPlayer player = mannequinRenderer.playerFor(mannequin);
        player.copyFrom(mannequin);
        return player;
    }

    private MannequinRenderPlayer playerFor(PlayerMannequinEntity mannequin) {
        if (++renderCount % 200 == 0) {
            Iterator<Map.Entry<PlayerMannequinEntity, CachedPlayer>> entries = players.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<PlayerMannequinEntity, CachedPlayer> entry = entries.next();
                if (entry.getKey().isRemoved() || renderCount - entry.getValue().lastRender() > 400) {
                    entries.remove();
                }
            }
        }
        CachedPlayer cached = players.get(mannequin);
        if (cached == null || cached.player().level() != mannequin.level()) {
            MannequinRenderPlayer player = new MannequinRenderPlayer((ClientLevel) mannequin.level(), mannequin);
            players.put(mannequin, new CachedPlayer(player, renderCount));
            return player;
        }
        players.put(mannequin, new CachedPlayer(cached.player(), renderCount));
        return cached.player();
    }

    private record CachedPlayer(MannequinRenderPlayer player, int lastRender) {
    }

    // This client-only player is a rendering proxy. It never enters the world or replaces the saved mannequin.
    public static final class MannequinRenderPlayer extends RemotePlayer implements HeadwearRenderSource {
        private final PlayerMannequinEntity mannequin;

        private MannequinRenderPlayer(ClientLevel level, PlayerMannequinEntity mannequin) {
            super(level, new GameProfile(mannequin.getUUID(), "Mannequin"));
            this.mannequin = mannequin;
        }

        public PlayerMannequinEntity mannequin() {
            return mannequin;
        }

        @Override
        public LivingEntity headwearSource() {
            return mannequin;
        }

        @Override
        public PlayerSkin getSkin() {
            return PlayerMannequinSkinResolver.skin(mannequin);
        }

        @Override
        public boolean isModelPartShown(PlayerModelPart part) {
            return true;
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public boolean shouldShowName() {
            return mannequin.isCustomNameVisible();
        }

        @Override
        public Component getDisplayName() {
            return mannequin.getDisplayName();
        }

        @Override
        public boolean displayFireAnimation() {
            return mannequin.displayFireAnimation();
        }

        @Override
        public boolean isCurrentlyGlowing() {
            return mannequin.isCurrentlyGlowing();
        }

        private void copyFrom(PlayerMannequinEntity source) {
            setPos(source.getX(), source.getY(), source.getZ());
            xo = source.xo;
            yo = source.yo;
            zo = source.zo;
            xOld = source.xOld;
            yOld = source.yOld;
            zOld = source.zOld;
            setYRot(source.getYRot());
            yRotO = source.yRotO;
            setXRot(source.getXRot());
            xRotO = source.xRotO;
            yBodyRot = source.yBodyRot;
            yBodyRotO = source.yBodyRotO;
            yHeadRot = source.yHeadRot;
            yHeadRotO = source.yHeadRotO;
            float hitTime = (float) (source.level().getGameTime() - source.lastHit);
            if (hitTime < 5.0F) {
                float wobble = Mth.sin(hitTime / 1.5F * (float) Math.PI) * 3.0F;
                yBodyRot += wobble;
                yBodyRotO += wobble;
                yHeadRot += wobble;
                yHeadRotO += wobble;
            }
            tickCount = source.tickCount;
            hurtTime = source.hurtTime;
            setInvisible(source.isInvisible());
            var scale = getAttribute(Attributes.SCALE);
            if (scale != null && scale.getBaseValue() != source.getScale()) {
                scale.setBaseValue(source.getScale());
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack worn = source.getItemBySlot(slot);
                if (!ItemStack.matches(getItemBySlot(slot), worn)) {
                    setItemSlot(slot, worn.copy());
                }
            }
        }
    }

}
