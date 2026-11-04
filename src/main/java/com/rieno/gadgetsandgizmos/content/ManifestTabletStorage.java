package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

// Recognize a purchased Manifest app open on a tablet attached to a container
final class ManifestTabletStorage{
    private ManifestTabletStorage(){}

    static boolean attached(Level level, BlockPos pos){
        if(level == null || level.isClientSide || pos == null) return false;
        for(Direction direction : Direction.values()){
            BlockPos tabletPos = pos.relative(direction);
            if(!level.isLoaded(tabletPos)) continue;
            if(!(level.getBlockEntity(tabletPos) instanceof DiagnosticTabletBlockEntity tablet)) continue;
            if(!tablet.getBlockState().hasProperty(DiagnosticTabletBlock.FACING)
                    || !tabletPos.relative(tablet.getBlockState().getValue(DiagnosticTabletBlock.FACING).getOpposite())
                    .equals(pos)) continue;
            var state = tablet.state();
            if(state.tabletId() == null || !PaidTabletApps.MANIFEST.id().equals(state.app())) continue;
            if(TabletStorageApi.storage().installedApps(state.tabletId()).contains(PaidTabletApps.MANIFEST.id()))
                return true;
        }
        return false;
    }
}
