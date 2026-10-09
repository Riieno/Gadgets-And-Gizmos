package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

// Generate the tablet toggle alongside the other server-owned block and item gates
class TabletFeatureConfigTest{
    @Test void generatedServerSpecIncludesDisabledTabletToggle(){
        var val = CTConfigs.SERVER.blockFeatures.get("diagnostic_tablet");
        assertNotNull(val);
        assertEquals(List.of("server","features","blocks","diagnostic_tablet"),val.getPath());
        ModConfigSpec.ValueSpec spec = CTConfigs.SERVER_SPEC.getSpec().get(val.getPath());
        assertEquals(false,spec.getDefault());
    }
    @Test void tabletItemAndStorePermissionFollowServerToggle(){
        try{
            for(boolean enabled : new boolean[]{true,false}){
                CTFeatureToggles.applyServerOverrides(Map.of("diagnostic_tablet",enabled), Map.of(), Map.of());
                assertEquals(enabled, CTFeatureToggles.isItemEnabled("diagnostic_tablet"));
                assertEquals(enabled, ShipPermissions.storeEnabled());
            }
        }finally{
            CTFeatureToggles.clearServerOverrides();
        }
    }
}
