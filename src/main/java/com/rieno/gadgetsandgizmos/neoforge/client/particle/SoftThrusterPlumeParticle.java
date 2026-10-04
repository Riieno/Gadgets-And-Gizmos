package com.rieno.gadgetsandgizmos.neoforge.client.particle;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.PlumeRainbow;
import com.rieno.gadgetsandgizmos.lib.client.render.ClientParticleBudget;
import com.rieno.gadgetsandgizmos.lib.client.render.SoftBillboardParticle;
import com.rieno.gadgetsandgizmos.lib.client.render.WorldParticleCollision;
import com.rieno.gadgetsandgizmos.neoforge.client.ShaderPackClientCompat;
import com.rieno.gadgetsandgizmos.particle.ColoredCloudParticleOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// Animate directed thruster exhaust and archive smoke
public final class SoftThrusterPlumeParticle extends SoftBillboardParticle {
    private static final ClientParticleBudget IMPACT_BUDGET = new ClientParticleBudget(12, 2);
    private static final float[][] IMPACT_COLORS = {
            {0.16F, 0.16F, 0.17F},
            {0.28F, 0.27F, 0.26F},
            {0.40F, 0.39F, 0.37F},
            {0.43F, 0.16F, 0.045F},
            {0.65F, 0.25F, 0.055F}
    };
    private static boolean occlusionCullingEnabled = true;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        DEFAULTS
                                                   #################
                                                       Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Transverse wave axis and starting phase
    private final Vec3 waveAxis;
    private final Vec3 flowAxis;
    private final float wavePhase;
    // Initial size and opacity
    private final float baseSize;
    private final float baseAlpha;
    private final float plumeHalfLength;
    // Thruster particles sweep world geometry while archive smoke keeps its original drift
    private final boolean collidesWithBlocks;
    private final boolean metaball;
    private final int metaballFrameOffset;
    private final SpriteSet impactSprites;
    private final PlumeRainbow.Mode rainbowMode;
    private final PlumeRainbow.Palette rainbowPalette;
    private final float plumeProgress;

    // Control view occlusion for plume particles on this client
    public static boolean isOcclusionCullingEnabled() {
        return occlusionCullingEnabled;
    }

    // Set view occlusion for plume particles on this client
    public static void setOcclusionCullingEnabled(boolean enabled) {
        occlusionCullingEnabled = enabled;
    }

    // Check whether solid geometry hides a plume particle from the camera
    public static boolean isHiddenFromCamera(ClientLevel level, double x, double y, double z) {
        if (!occlusionCullingEnabled) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level || minecraft.player == null) return false;
        return WorldParticleCollision.isOccluded(level,
                minecraft.gameRenderer.getMainCamera().getPosition(), new Vec3(x, y, z), 0.16D);
    }

    // Initialize the colored soft plume particle
    public SoftThrusterPlumeParticle(ClientLevel level, double x, double y, double z,
                                    double xd, double yd, double zd, ColoredCloudParticleOptions opts,
                                    SpriteSet impactSprites) {
        this(level, x, y, z, xd, yd, zd, opts.red(), opts.green(), opts.blue(), true,
                CTConfigs.CLIENT.usePlumeMetaballRendering.get(), impactSprites,
                opts.rainbowMode(), opts.rainbowPalette(), opts.plumeProgress());
    }

    // Reuse the soft plume for a neutral archive smoke burst
    private SoftThrusterPlumeParticle(ClientLevel level, double x, double y, double z,
                                     double xd, double yd, double zd, float red, float green, float blue,
                                     boolean collidesWithBlocks, boolean metaball, SpriteSet impactSprites,
                                     PlumeRainbow.Mode rainbowMode, PlumeRainbow.Palette rainbowPalette,
                                     float plumeProgress) {
        super(level, x, y, z);
        Vec3 motion = new Vec3(xd, yd, zd);
        Vec3 dir = motion.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : motion.normalize();
        Vec3 reference = Math.abs(dir.y) > 0.8D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        this.flowAxis = dir;
        this.waveAxis = dir.cross(reference).normalize();
        this.wavePhase = this.random.nextFloat() * Mth.TWO_PI;
        double speedScale = collidesWithBlocks ? 0.25D : 0.13D;
        this.xd = xd * speedScale;
        this.yd = yd * speedScale;
        this.zd = zd * speedScale;
        this.friction = collidesWithBlocks ? 0.985F : 0.92F;
        this.hasPhysics = false;
        this.collidesWithBlocks = collidesWithBlocks;
        this.metaball = metaball;
        this.metaballFrameOffset = this.random.nextInt(16);
        this.impactSprites = impactSprites;
        this.rainbowMode = rainbowMode;
        this.rainbowPalette = rainbowPalette;
        this.plumeProgress = plumeProgress;
        this.baseSize = collidesWithBlocks
                ? (metaball ? 0.72F + this.random.nextFloat() * 0.22F
                        : 1.4F + this.random.nextFloat() * 0.32F)
                : 0.33F + this.random.nextFloat() * 0.17F;
        this.baseAlpha = collidesWithBlocks
                ? (ShaderPackClientCompat.isActive() ? 0.56F : 0.48F) + this.random.nextFloat() * 0.06F
                : 0.55F + this.random.nextFloat() * 0.13F;
        this.plumeHalfLength = collidesWithBlocks
                ? Mth.clamp((float) (motion.length() * speedScale * 4.5D), 2.5F, 5.25F)
                : 0.0F;
        this.quadSize = baseSize * 0.65F;
        this.alpha = 0.0F;
        this.lifetime = collidesWithBlocks ? 10 + this.random.nextInt(5)
                : 19 + this.random.nextInt(10);
        this.rCol = red;
        this.gCol = green;
        this.bCol = blue;
        updateRainbowColor();
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
    }

    // Drift the cloud along the nozzle direction with a transverse wave
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        if (this.collidesWithBlocks && (this.age & 7) == 0
                && isHiddenFromCamera(this.level, this.x, this.y, this.z)) {
            this.remove();
            return;
        }

        float progress = this.age / (float) this.lifetime;
        float fadeIn = this.collidesWithBlocks ? 1.0F : Mth.clamp(this.age / 2.0F, 0.0F, 1.0F);
        float fadeOut = (float) Math.pow(1.0F - progress,
                this.collidesWithBlocks ? 0.6D : 1.4D);
        this.alpha = this.baseAlpha * fadeIn * fadeOut;
        this.quadSize = this.baseSize * (this.collidesWithBlocks
                ? 1.0F + progress * 0.2F : 0.65F + progress * 2.6F);
        updateRainbowColor();
        this.oRoll = this.roll;
        this.roll += 0.035F;

        double wave = Math.sin(this.age * 0.46F + this.wavePhase) * 0.009D;
        Vec3 motion = new Vec3(this.xd + this.waveAxis.x * wave,
                this.yd + this.waveAxis.y * wave,
                this.zd + this.waveAxis.z * wave);
        Vec3 start = new Vec3(this.x, this.y, this.z);
        if (this.collidesWithBlocks) {
            WorldParticleCollision.MotionResult collision = WorldParticleCollision.collide(this.level, start, motion);
            Vec3 allowed = collision.motion();
            this.move(allowed.x, allowed.y, allowed.z);
            if (collision.collided()) {
                spawnImpactCloud(collision.normal());
                this.remove();
                return;
            }
        } else {
            this.move(motion.x, motion.y, motion.z);
        }
        this.xd *= this.friction;
        this.yd *= this.friction;
        this.zd *= this.friction;
    }

    // Advance the exhaust color while keeping the whole solid plume in phase
    private void updateRainbowColor() {
        if (!this.collidesWithBlocks || this.rainbowMode == PlumeRainbow.Mode.OFF) return;
        int color = PlumeRainbow.color(this.rainbowMode, this.rainbowPalette,
                this.level.getGameTime(), this.plumeProgress, this.age);
        this.rCol = (color >> 16 & 0xFF) / 255.0F;
        this.gCol = (color >> 8 & 0xFF) / 255.0F;
        this.bCol = (color & 0xFF) / 255.0F;
    }

    // Send one larger cloud across the contact plane
    private void spawnImpactCloud(Vec3 normal) {
        if (this.impactSprites == null) return;
        Vec3 origin = new Vec3(this.x, this.y, this.z).add(normal.scale(0.12D));
        if (isHiddenFromCamera(this.level, origin.x, origin.y, origin.z)) return;
        if (!IMPACT_BUDGET.tryAcquire(this.level)) return;
        Vec3 tangent = WorldParticleCollision.tangentDirection(normal,
                this.random.nextDouble() * Mth.TWO_PI);
        Vec3 speed = tangent.scale(0.18D + this.random.nextDouble() * 0.09D)
                .add(normal.scale(0.025D));
        float[] color = IMPACT_COLORS[this.random.nextInt(IMPACT_COLORS.length)];
        Minecraft.getInstance().particleEngine.add(new ThrusterImpactSmokeParticle(
                this.level, origin, speed, color[0], color[1], color[2],
                this.impactSprites, IMPACT_BUDGET));
    }

    // Use a long exhaust streak when metaball rendering is disabled
    @Override
    protected boolean usesStreakTexture() {
        return this.collidesWithBlocks && !this.metaball;
    }

    // Keep thruster effects off the scene depth copy path
    @Override
    protected boolean usesFastRendering() {
        return this.collidesWithBlocks;
    }

    // Keep the V2 exhaust bright without adding its color to the background
    @Override
    protected boolean usesAlphaEmissiveBlend() {
        return this.collidesWithBlocks;
    }

    // Align the active exhaust with its travel direction
    @Override
    protected Vec3 getStretchedAxis() {
        return this.collidesWithBlocks ? this.flowAxis : Vec3.ZERO;
    }

    // Stretch the active exhaust into a visible high velocity lance
    @Override
    protected float getStretchedHalfLength() {
        if (!this.collidesWithBlocks) return 0.0F;
        return this.plumeHalfLength;
    }

    // Keep the exhaust behind each particle clear of the flame and thruster body
    @Override
    protected float getStretchedTrailingLength() {
        return this.collidesWithBlocks ? 0.0F : super.getStretchedTrailingLength();
    }

    // Draw the plume outward from its sampled position
    @Override
    protected float getStretchedLeadingLength() {
        return this.collidesWithBlocks ? this.plumeHalfLength * 1.6F
                : super.getStretchedLeadingLength();
    }

    // Begin the visible streak close to the exhaust-side edge
    @Override
    protected float getV1() {
        float bottom = super.getV1();
        if (!this.collidesWithBlocks) return bottom;
        float top = super.getV0();
        return top + (bottom - top) * (this.metaball ? 0.78F : 0.92F);
    }

    // Give active exhaust a wide, translucent shell around its bright center
    @Override
    protected float getOuterLayerWidthScale() {
        return this.collidesWithBlocks ? (this.metaball ? 1.8F : 2.2F) : 1.0F;
    }

    // Keep the shell subtle enough to preserve the luminous core
    @Override
    protected float getOuterLayerOpacity() {
        return this.collidesWithBlocks ? (this.metaball ? 0.28F : 0.4F) : 0.0F;
    }

    // Warm the broad fringe as the exhaust cools and spreads
    @Override
    protected int getOuterLayerColor() {
        if (!this.collidesWithBlocks) return super.getOuterLayerColor();
        float progress = this.age / (float) this.lifetime;
        float cooling = Mth.clamp((progress - 0.55F) / 0.4F, 0.0F, 1.0F);
        float ember = Mth.clamp((progress - 0.75F) / 0.2F, 0.0F, 1.0F);
        float warmRed = Mth.lerp(ember, 1.0F, 0.85F);
        float warmGreen = Mth.lerp(ember, 0.54F, 0.29F);
        float warmBlue = Mth.lerp(ember, 0.17F, 0.11F);
        int red = Mth.clamp((int) (Mth.lerp(cooling, this.rCol, warmRed)
                * 255.0F), 0, 255);
        int green = Mth.clamp((int) (Mth.lerp(cooling, this.gCol, warmGreen)
                * 255.0F), 0, 255);
        int blue = Mth.clamp((int) (Mth.lerp(cooling, this.bCol, warmBlue)
                * 255.0F), 0, 255);
        return red << 16 | green << 8 | blue;
    }

    // Concentrate the luminous hotspot near the start of a particle's life
    @Override
    protected float getHotspotOpacity() {
        if (!this.collidesWithBlocks) return 0.0F;
        float progress = this.age / (float) this.lifetime;
        float ignition = Mth.clamp(progress / 0.15F, 0.0F, 1.0F);
        float cooling = 1.0F - Mth.clamp((progress - 0.35F) / 0.45F, 0.0F, 1.0F);
        return 1.45F * ignition * cooling;
    }

    // Keep the central hotspot narrow enough to preserve the plume color
    @Override
    protected float getHotspotWidthScale() {
        return this.collidesWithBlocks ? 0.42F : 0.0F;
    }

    // Illuminate the center without completely washing out rainbow colors
    @Override
    protected int getHotspotColor() {
        float white = this.rainbowMode == PlumeRainbow.Mode.OFF ? 0.75F : 0.55F;
        int red = (int) (Mth.lerp(white, this.rCol, 1.0F) * 255.0F);
        int green = (int) (Mth.lerp(white, this.gCol, 1.0F) * 255.0F);
        int blue = (int) (Mth.lerp(white, this.bCol, 1.0F) * 255.0F);
        return red << 16 | green << 8 | blue;
    }

    // Use the shared animated metaball billboard when selected
    @Override
    protected boolean usesMetaballTexture() {
        return this.metaball;
    }

    // Start nearby metaballs at different animation phases
    @Override
    protected int getMetaballFrame() {
        return super.getMetaballFrame() + this.metaballFrameOffset;
    }

    // Keep the active exhaust emissive
    @Override
    protected boolean isEmissive() {
        return this.collidesWithBlocks;
    }

    // Preserve the archive smoke brightness
    @Override
    public int getLightColor(float partialTick) {
        return !this.collidesWithBlocks ? LightTexture.FULL_BRIGHT : super.getLightColor(partialTick);
    }

    // Render archive smoke with the v2 soft-particle layer regardless of thruster settings
    public static final class SmokeProvider implements ParticleProvider<SimpleParticleType>{
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd){
            return new SoftThrusterPlumeParticle(level, x, y, z, xd, yd + 0.7D, zd,
                    0.58F, 0.66F, 0.72F, false, false, null,
                    PlumeRainbow.Mode.OFF, PlumeRainbow.Palette.NORMAL, 0.0F);
        }
    }
}
