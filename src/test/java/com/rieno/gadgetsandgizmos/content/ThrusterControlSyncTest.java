package com.rieno.gadgetsandgizmos.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ThrusterControlSyncTest {
    @BeforeAll
    static void bootstrap() { ControllerTestBootstrap.bootstrap(); }

    @Test
    void throttleChangesNotifyEveryTargetWithoutWaitingForItsSyncPhase() throws Exception {
        Level level = mock(Level.class);
        BlockState state = mock(BlockState.class);
        ThrusterBlockEntity[] targets = new ThrusterBlockEntity[3];
        for (int i = 0; i < targets.length; i++) {
            BlockPos pos = new BlockPos(i, 64, 0);
            ThrusterBlockEntity target = mock(ThrusterBlockEntity.class, CALLS_REAL_METHODS);
            var position = BlockEntity.class.getDeclaredField("worldPosition");
            position.setAccessible(true);
            position.set(target, pos);
            target.setLevel(level);
            doNothing().when(target).setChanged();
            doReturn(state).when(target).getBlockState();
            targets[i] = target;
        }
        for (float throttle : new float[]{1, 0, 1, 0}) {
            clearInvocations(level);
            for (ThrusterBlockEntity target : targets) target.setThrottle(throttle);
            for (int i = 0; i < targets.length; i++) {
                assertEquals(throttle, targets[i].getThrottle());
                verify(level).sendBlockUpdated(new BlockPos(i, 64, 0), state, state, 3);
            }
            clearInvocations(level);
            for (ThrusterBlockEntity target : targets) target.setThrottle(throttle);
            verifyNoInteractions(level);
        }
    }
}
