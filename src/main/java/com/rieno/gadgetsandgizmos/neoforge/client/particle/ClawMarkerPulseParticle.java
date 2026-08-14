package com.rieno.gadgetsandgizmos.neoforge.client.particle;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.particle.ClawMarkerPulseParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

// Animate the short pulse used to mark a claw target
public class ClawMarkerPulseParticle extends TextureSheetParticle {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Sprites
    private final SpriteSet sprites;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the claw marker pulse particle
    protected ClawMarkerPulseParticle(ClientLevel level, double x, double y, double z,
                                      ClawMarkerPulseParticleOptions opts,
                                      SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.friction = 1.0F;
        this.quadSize = 0.38F;
        this.rCol = opts.red();
        this.gCol = opts.green();
        this.bCol = opts.blue();
        this.alpha = 0.95F;

        this.lifetime = 18;
        this.hasPhysics = false;
        this.setSpriteFromAge(sprites);
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

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the claw marker pulse particle
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        this.yd += 0.0025D;
        this.alpha = 0.95F * (1.0F - (float) this.age / (float) this.lifetime);
        this.quadSize *= 1.01F;
        this.setSpriteFromAge(this.sprites);
        this.move(0.0D, 0.0D, 0.0D);
    }

    // Create particle instances
    public static class Provider implements ParticleProvider<ClawMarkerPulseParticleOptions> {
        // Sprites
        private final SpriteSet sprites;

        // Initialize the provider
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        // Create the particle
        @Override
        public Particle createParticle(ClawMarkerPulseParticleOptions opts, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new ClawMarkerPulseParticle(level, x, y, z, opts, sprites);
        }
    }
}
