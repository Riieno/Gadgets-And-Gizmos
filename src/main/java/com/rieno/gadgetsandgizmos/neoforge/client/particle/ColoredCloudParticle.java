package com.rieno.gadgetsandgizmos.neoforge.client.particle;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.particle.ColoredCloudParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// Draw a tinted cloud particle with configurable scale and lifetime
public class ColoredCloudParticle extends TextureSheetParticle {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int PLUME_FRAMES = 6;
    private static final int TOTAL_FRAMES = 16;
    private static final double SPEED_MULTIPLIER = 0.144D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Sprites
    private final SpriteSet sprites;
    // Smoke transition age
    private final int smokeTransitionAge;
    // Base size
    private final float baseSize;
    // Base alpha
    private final float baseAlpha;
    // Spread direction
    private final Vec3 spreadDirection;
    // Spread strength
    private final double spreadStrength;
    // Smoke lift
    private final double smokeLift;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the colored cloud particle
    protected ColoredCloudParticle(ClientLevel level, double x, double y, double z,
                                   double xd, double yd, double zd,
                                   ColoredCloudParticleOptions opts,
                                   SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.friction = 0.92F;
        this.speedUpWhenYMotionIsBlocked = false;
        this.xd = xd * SPEED_MULTIPLIER + randomSpread(0.025D);
        this.yd = yd * SPEED_MULTIPLIER + randomSpread(0.025D);
        this.zd = zd * SPEED_MULTIPLIER + randomSpread(0.025D);
        this.baseSize = this.quadSize * (1.65F + this.random.nextFloat() * 0.55F);
        this.quadSize = this.baseSize * 0.35F;
        this.rCol = opts.red();
        this.gCol = opts.green();
        this.bCol = opts.blue();
        this.baseAlpha = 0.88F + this.random.nextFloat() * 0.08F;
        this.alpha = 0.0F;
        this.lifetime = 18 + this.random.nextInt(8);
        this.smokeTransitionAge = 6 + this.random.nextInt(4);
        this.hasPhysics = false;
        this.setSize(0.2F, 0.2F);

        Vec3 motion = new Vec3(this.xd, this.yd, this.zd);
        Vec3 axis = motion.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : motion.normalize();
        Vec3 reference = Math.abs(axis.y) > 0.8D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 axisA = axis.cross(reference).normalize();
        Vec3 axisB = axis.cross(axisA).normalize();
        double angle = this.random.nextDouble() * Math.PI * 2.0D;
        this.spreadDirection = axisA.scale(Math.cos(angle)).add(axisB.scale(Math.sin(angle))).normalize();
        this.spreadStrength = 0.015D + this.random.nextDouble() * 0.035D;
        this.smokeLift = 0.003D + this.random.nextDouble() * 0.012D;
        pickSprite();
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

    // Update the colored cloud particle
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        if (this.age < this.smokeTransitionAge) {
            float plume = this.age / (float) this.smokeTransitionAge;
            this.alpha = this.baseAlpha * Mth.clamp(plume * 3.5F, 0.0F, 1.0F);
            this.quadSize = this.baseSize * (0.45F + plume * 1.15F);
            this.friction = 0.90F;
        } else {
            float smoke = (this.age - this.smokeTransitionAge)
                    / (float) Math.max(1, this.lifetime - this.smokeTransitionAge);
            float fade = (float) Math.pow(Math.max(0.0F, 1.0F - smoke), 2.35D);
            this.alpha = this.baseAlpha * 0.78F * fade;
            this.quadSize = this.baseSize * (1.35F + smoke * 1.75F);
            this.friction = 0.955F;
            double spread = this.spreadStrength * fade;
            this.xd += this.spreadDirection.x * spread;
            this.yd += this.smokeLift * fade + this.spreadDirection.y * spread;
            this.zd += this.spreadDirection.z * spread;
        }

        this.move(this.xd, this.yd, this.zd);
        this.xd *= this.friction;
        this.yd *= this.friction;
        this.zd *= this.friction;
        pickSprite();
    }

    // Get the light color
    @Override
    public int getLightColor(float partialTick) {
        return this.age < this.smokeTransitionAge ? 0xF000F0 : super.getLightColor(partialTick);
    }

    // Pick the sprite
    private void pickSprite() {
        int spriteIndex;
        if (this.age < this.smokeTransitionAge) {
            float plume = this.age / (float) Math.max(1, this.smokeTransitionAge);
            spriteIndex = Mth.clamp((int) (plume * PLUME_FRAMES), 0, PLUME_FRAMES - 1);
        } else {
            int smokeFrames = TOTAL_FRAMES - PLUME_FRAMES;
            float smoke = (this.age - this.smokeTransitionAge)
                    / (float) Math.max(1, this.lifetime - this.smokeTransitionAge);
            spriteIndex = PLUME_FRAMES + Mth.clamp((int) (smoke * smokeFrames), 0, smokeFrames - 1);
        }
        this.setSprite(this.sprites.get(spriteIndex, TOTAL_FRAMES));
    }

    // Get the random spread
    private double randomSpread(double amount) {
        return (this.random.nextDouble() - 0.5D) * 2.0D * amount;
    }

    // Create particle instances
    public static class Provider implements ParticleProvider<ColoredCloudParticleOptions> {
        // Sprites
        private final SpriteSet sprites;

        // Initialize the provider
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        // Create the particle
        @Override
        public Particle createParticle(ColoredCloudParticleOptions opts, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new ColoredCloudParticle(level, x, y, z, xd, yd, zd, opts, sprites);
        }
    }
}
