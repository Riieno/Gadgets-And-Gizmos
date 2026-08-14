package com.rieno.gadgetsandgizmos.neoforge.client.particle;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.particle.RcsSteamParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

// Animate the short directional steam plume produced by an RCS nozzle
public class RcsSteamParticle extends TextureSheetParticle {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Sprites
    private final SpriteSet sprites;
    // Base size
    private final float baseSize;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the RCS steam particle
    protected RcsSteamParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xd,
            double yd,
            double zd,
            RcsSteamParticleOptions opts,
            SpriteSet sprites
    ) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.friction = 0.93F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.speedUpWhenYMotionIsBlocked = true;

        float speedVariation = 0.7F + random.nextFloat() * 0.5F;
        this.xd = xd * speedVariation;
        this.yd = yd * speedVariation;
        this.zd = zd * speedVariation;
        this.rCol = opts.red();
        this.gCol = opts.green();
        this.bCol = opts.blue();
        this.baseSize = 0.22F * opts.scale();
        this.quadSize = baseSize * 0.4F;
        this.alpha = 0.52F;
        this.lifetime = Math.max(6, (int) ((8.0D / (random.nextDouble() * 0.5D + 0.5D))
                * opts.scale()));
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        setSpriteFromAge(sprites);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the render type
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // Get the quad size
    @Override
    public float getQuadSize(float partialTick) {
        float progress = Mth.clamp((age + partialTick) / Math.max(1.0F, lifetime), 0.0F, 1.0F);
        float expansion = (float) Math.sin(progress * Math.PI);
        return baseSize * (0.40F + expansion * 0.85F);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the RCS steam particle
    @Override
    public void tick() {
        super.tick();
        if (removed) {
            return;
        }
        float progress = Mth.clamp(age / (float) Math.max(1, lifetime), 0.0F, 1.0F);
        alpha = 0.52F * (float) Math.pow(1.0F - progress, 1.25D);
        oRoll = roll;
        roll += 0.08F;
        setSpriteFromAge(sprites);
    }

    // Create particle instances
    public static class Provider implements ParticleProvider<RcsSteamParticleOptions> {
        // Sprites
        private final SpriteSet sprites;

        // Initialize the provider
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        // Create the particle
        @Override
        public Particle createParticle(
                RcsSteamParticleOptions opts,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xd,
                double yd,
                double zd
        ) {
            return new RcsSteamParticle(level, x, y, z, xd, yd, zd, opts, sprites);
        }
    }
}
