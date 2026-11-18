package com.rieno.gadgetsandgizmos.content.peripheral;

import com.rieno.gadgetsandgizmos.compat.computercraft.NavigationTablePeripheralData;
import com.rieno.gadgetsandgizmos.lib.script.ScriptMethods;
import com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheralBinding;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

// Expose the basic table's native queries and the existing coordinate bridge
public final class BasicNavigationScriptBindings{
    private BasicNavigationScriptBindings(){}
    public static ScriptPeripheralBinding resolve(BlockEntity target){
        if(!(target instanceof NavTableBlockEntity table)) return null;
        return new ScriptPeripheralBinding(){
            @Override public String getType(){ return "navigation_table"; }
            @Override public void bindMethods(ScriptMethods methods){
                methods.bind("getRelativeAngle", args -> new Object[]{table.getRelativeAngle()});
                methods.bind("getRelativeAngleRad", args -> new Object[]{Math.toRadians(table.getRelativeAngle())});
                for(String method : java.util.List.of("getTablePosition", "getBlockPos", "getblock"))
                    methods.bind(method, args -> new Object[]{NavigationTablePeripheralData.tablePosition(table)});
                for(String method : java.util.List.of("getTargetPosition", "getTargetPos"))
                    methods.bind(method, args -> new Object[]{NavigationTablePeripheralData.targetPosition(table, table.getTargetPosition(false))});
                methods.bind("getTargetDistance", args -> new Object[]{NavigationTablePeripheralData.targetDistance(table, table.getTargetPosition(false))});
            }
        };
    }
}
