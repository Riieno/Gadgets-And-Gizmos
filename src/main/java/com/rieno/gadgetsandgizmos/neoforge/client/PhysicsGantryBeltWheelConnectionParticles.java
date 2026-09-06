package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryBeltWheelBlockEntity;
import com.rieno.gadgetsandgizmos.neoforge.network.PhysicsGantryBeltWheelSelectionPayload;
import com.rieno.gadgetsandgizmos.particle.worldspace.WorldSpaceParticleEmitter;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

// Render Create's dotted belt-connector preview in the root world for physics gantry belt wheels.
public final class PhysicsGantryBeltWheelConnectionParticles {
    private static final int SELECTION_TIMEOUT_TICKS = 20 * 30;
    private static final double PARTICLE_INTERVAL = 0.0625D;
    private static final ParticleOptions VALID_PARTICLE = new DustParticleOptions(new Vector3f(0.3F, 0.9F, 0.5F), 1.0F);
    private static final ParticleOptions INVALID_PARTICLE = new DustParticleOptions(new Vector3f(0.9F, 0.3F, 0.5F), 1.0F);
    private static @Nullable Selection selection;

    private PhysicsGantryBeltWheelConnectionParticles() {
    }

    public static void handle(PhysicsGantryBeltWheelSelectionPayload payload) {
        if (!payload.active()) {
            clear();
            return;
        }
        selection = new Selection(BlockPos.of(payload.position()), payload.subLevelId(),
                new Vec3(payload.fallbackWorldX(), payload.fallbackWorldY(), payload.fallbackWorldZ()),
                Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime(),
                Math.max(1, payload.maximumDistance()));
    }

    public static void tick() {
        Selection current = selection;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel clientLevel = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (current == null || clientLevel == null || player == null) {
            return;
        }
        if (clientLevel.getGameTime() - current.selectedAtTick() > SELECTION_TIMEOUT_TICKS) {
            clear();
            return;
        }
        if (minecraft.screen != null || !isHoldingBeltConnector(player)) {
            return;
        }

        PhysicsGantryBeltWheelBlockEntity sourceWheel = SimulatedHelper.findBlockEntity(
                clientLevel, current.subLevelId(), current.position(), PhysicsGantryBeltWheelBlockEntity.class);
        Vec3 source = sourceWheel == null ? current.fallbackWorldPosition() : sourceWheel.getWorldAnchorPosition();
        Level particleLevel = sourceWheel == null ? clientLevel : WorldSpaceParticleEmitter.resolveWorldLevel(sourceWheel);
        if (particleLevel == null) {
            return;
        }

        RandomSource random = clientLevel.random;
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
            spawnSourceMarker(particleLevel, source, random);
            return;
        }

        PhysicsGantryBeltWheelBlockEntity targetWheel = SimulatedHelper.findBlockEntityIncludingSubLevels(
                clientLevel, hit.getBlockPos(), PhysicsGantryBeltWheelBlockEntity.class);
        if (targetWheel == null) {
            spawnSourceMarker(particleLevel, source, random);
            return;
        }

        Vec3 target = targetWheel.getWorldAnchorPosition();
        boolean valid = !sameWheel(sourceWheel, current, targetWheel)
                && source.distanceToSqr(target) <= current.maximumDistance() * (double) current.maximumDistance();
        spawnConnection(particleLevel, source, target, valid ? VALID_PARTICLE : INVALID_PARTICLE, random);
    }

    public static void clear() {
        selection = null;
    }

    private static boolean isHoldingBeltConnector(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof BeltConnectorItem
                || player.getOffhandItem().getItem() instanceof BeltConnectorItem;
    }

    private static boolean sameWheel(@Nullable PhysicsGantryBeltWheelBlockEntity sourceWheel, Selection current,
                                     PhysicsGantryBeltWheelBlockEntity targetWheel) {
        if (targetWheel == sourceWheel) {
            return true;
        }
        return current.position().equals(targetWheel.getBlockPos())
                && Objects.equals(current.subLevelId(), SimulatedHelper.getContainingSubLevelId(targetWheel));
    }

    private static void spawnSourceMarker(Level level, Vec3 source, RandomSource random) {
        if (random.nextInt(50) != 0) {
            return;
        }
        level.addParticle(VALID_PARTICLE,
                source.x + randomOffset(random, 0.25F),
                source.y + randomOffset(random, 0.25F),
                source.z + randomOffset(random, 0.25F),
                0.0D, 0.0D, 0.0D);
    }

    private static void spawnConnection(Level level, Vec3 source, Vec3 target,
                                        ParticleOptions particle, RandomSource random) {
        Vec3 delta = target.subtract(source);
        double length = delta.length();
        if (length <= 1.0E-4D) {
            return;
        }
        Vec3 direction = delta.scale(1.0D / length);
        for (double distance = 0.0D; distance < length; distance += PARTICLE_INTERVAL) {
            if (random.nextInt(10) != 0) {
                continue;
            }
            Vec3 position = source.add(direction.scale(distance));
            level.addParticle(particle, position.x, position.y, position.z, 0.0D, 0.0D, 0.0D);
        }
    }

    private static float randomOffset(RandomSource random, float amount) {
        return (random.nextFloat() - 0.5F) * 2.0F * amount;
    }

    private record Selection(BlockPos position, @Nullable UUID subLevelId,
                             Vec3 fallbackWorldPosition, long selectedAtTick, int maximumDistance) {
    }
}
