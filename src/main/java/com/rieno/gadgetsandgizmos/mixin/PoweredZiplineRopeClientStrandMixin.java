package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PoweredZiplineBlockEntity;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineClientRopeCache;
import com.rieno.gadgetsandgizmos.neoforge.client.PoweredZiplineClientRopeAccess;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopePoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Keep the client rope strand attached to a Powered Zipline inside a Sable level
@Mixin(RopeStrandHolderBehavior.class)
public class PoweredZiplineRopeClientStrandMixin implements PoweredZiplineClientRopeAccess, PoweredZiplineClientRopeCache {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked zipline client strands
    @Unique
    private final Map<UUID, ClientRopeStrand> createthrusters$ziplineClientStrands = new HashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Receive the zipline client strand
    @Inject(method = "receiveClientStrand", at = @At("HEAD"), cancellable = true)
    private void createthrusters$receiveZiplineClientStrand(int interpolationTick, List<Vector3d> incomingPoints,
                                                            UUID uuid, @Nullable BlockPos startAttachmentPos,
                                                            @Nullable BlockPos endAttachmentPos, CallbackInfo ci) {
        RopeStrandHolderBehavior self = (RopeStrandHolderBehavior) (Object) this;
        if (!(self.blockEntity instanceof PoweredZiplineBlockEntity)) {
            return;
        }
        Level level = self.blockEntity.getLevel();
        if (level == null) {
            return;
        }
        ClientRopeStrand strand = createthrusters$ziplineClientStrands.computeIfAbsent(uuid, ClientRopeStrand::new);
        ClientLevelRopeManager.getOrCreate(level).addStrand(strand);

        Vec3 startAttachment = createthrusters$getAttachmentPoint(level, startAttachmentPos);
        Vec3 endAttachment = createthrusters$getAttachmentPoint(level, endAttachmentPos);
        if (startAttachment != null) {
            strand.startAttachment = startAttachment;
        }
        if (endAttachment != null) {
            strand.endAttachment = endAttachment;
        }

        ObjectArrayList<ClientRopePoint> points = strand.getPoints();
        strand.setStopped(false);
        while (points.size() < incomingPoints.size()) {
            Vector3d pos = incomingPoints.get(incomingPoints.size() - points.size() - 1);
            points.addFirst(new ClientRopePoint(new Vector3d(pos), new Vector3d(pos),
                    (ObjectList<ClientRopePoint.Snapshot>) new ObjectArrayList<ClientRopePoint.Snapshot>()));
        }
        while (points.size() > incomingPoints.size()) {
            points.removeFirst();
        }
        for (int i = 0; i < incomingPoints.size(); i++) {
            points.get(i).snapshots().add(new ClientRopePoint.Snapshot(interpolationTick, incomingPoints.get(i)));
        }
        ci.cancel();
    }

    // Stop the zipline client strands
    @Inject(method = "receiveClientStrandStopped", at = @At("HEAD"), cancellable = true)
    private void createthrusters$stopZiplineClientStrands(CallbackInfo ci) {
        RopeStrandHolderBehavior self = (RopeStrandHolderBehavior) (Object) this;
        if (!(self.blockEntity instanceof PoweredZiplineBlockEntity)) {
            return;
        }
        for (ClientRopeStrand strand : createthrusters$ziplineClientStrands.values()) {
            strand.setStopped(true);
        }
        ci.cancel();
    }

    // Get the attachment point
    private static @Nullable Vec3 createthrusters$getAttachmentPoint(Level level, @Nullable BlockPos attachment) {
        if (attachment == null) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(attachment);
        if (!(blockEntity instanceof SmartBlockEntity smartBlockEntity)) {
            return null;
        }
        RopeStrandHolderBehavior holder = smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
        return holder == null ? null : holder.getAttachmentPoint();
    }

    // Get the zipline client strands
    @Override
    public Collection<ClientRopeStrand> createthrusters$getZiplineClientStrands() {
        return createthrusters$ziplineClientStrands.values();
    }

    // Retain the zipline client strands
    @Override
    public void createthrusters$retainZiplineClientStrands(Set<UUID> activeRopes, Level level) {
        ClientLevelRopeManager manager = ClientLevelRopeManager.getOrCreate(level);
        Iterator<Map.Entry<UUID, ClientRopeStrand>> iterator = createthrusters$ziplineClientStrands.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ClientRopeStrand> entry = iterator.next();
            if (activeRopes.contains(entry.getKey())) {
                continue;
            }
            if (manager != null) {
                manager.removeStrand(entry.getKey());
            }
            iterator.remove();
        }
    }
}
