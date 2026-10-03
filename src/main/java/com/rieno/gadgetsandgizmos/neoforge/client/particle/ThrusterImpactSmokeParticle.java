package com.rieno.gadgetsandgizmos.neoforge.client.particle;

import com.rieno.gadgetsandgizmos.lib.client.render.ClientParticleBudget;
import com.rieno.gadgetsandgizmos.lib.client.render.SoftParticleRenderTypes;
import com.rieno.gadgetsandgizmos.lib.client.render.WorldParticleCollision;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.world.phys.Vec3;

// Spread a short lived campfire style smoke cloud across an exhaust contact
public final class ThrusterImpactSmokeParticle extends CampfireSmokeParticle {
    private final ClientParticleBudget budget;
    private final float baseSize;
    private final float baseAlpha;

    // Blend contact smoke behind glass and cutout textures
    @Override
    public ParticleRenderType getRenderType() {
        return SoftParticleRenderTypes.earlyTranslucentParticleSheet();
    }

    // Initialize a tinted cloud using the existing smoke sprites and motion
    public ThrusterImpactSmokeParticle(ClientLevel level, Vec3 pos, Vec3 motion,
            float red, float green, float blue, SpriteSet sprites, ClientParticleBudget budget) {
        super(level, pos.x, pos.y, pos.z, motion.x, motion.y, motion.z, false);
        this.budget = budget;
        this.hasPhysics = false;
        this.lifetime = 8 + this.random.nextInt(4);
        this.baseSize = 0.68F + this.random.nextFloat() * 0.26F;
        this.baseAlpha = 0.52F + this.random.nextFloat() * 0.12F;
        this.quadSize = this.baseSize;
        this.alpha = this.baseAlpha;
        this.setColor(red, green, blue);
        this.setSprite(sprites.get(6 + this.random.nextInt(10), 16));
        this.roll = this.random.nextFloat() * (float) (Math.PI * 2.0D);
        this.oRoll = this.roll;
    }

    // Let the campfire drift spread along the contact plane and fade quickly
    @Override
    public void tick() {
        if (this.age % 3 == 0) {
            Vec3 start = new Vec3(this.x, this.y, this.z);
            Vec3 projected = new Vec3(this.xd, this.yd, this.zd).scale(3.0D);
            if (WorldParticleCollision.clipMotion(this.level, start, projected)
                    .distanceToSqr(projected) > 1.0E-6D) {
                this.remove();
                return;
            }
        }
        this.yd += 0.002D;
        super.tick();
        if (!this.isAlive()) return;
        float progress = this.age / (float) this.lifetime;
        this.quadSize = this.baseSize * (1.0F + progress * 0.5F);
        this.alpha = this.baseAlpha * (float) Math.pow(1.0F - progress, 1.2D);
        this.xd *= 0.94D;
        this.yd *= 0.94D;
        this.zd *= 0.94D;
        this.oRoll = this.roll;
        this.roll += 0.014F;
    }

    // Release the shared impact slot once
    @Override
    public void remove() {
        if (!this.removed) this.budget.release(this.level);
        super.remove();
    }
}
