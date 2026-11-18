package com.rieno.gadgetsandgizmos.compat.simulated;

import com.rieno.gadgetsandgizmos.lib.compat.simulated.SimulatedDockingApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmDockingOrientation;
import java.util.UUID;

// Expose dock and SCM binding state on Simulated docking connectors
public interface DockingConnectorBindingAccess extends SimulatedDockingApi.AlignmentPolicy{
    @Override default double dockingRollStepDegrees(){
        return createthrusters$hasShipDockBinding() ? ScmDockingOrientation.ROLL_STEP_DEGREES : 0;
    }

    void createthrusters$setShipDockBinding(UUID dockId, String dockName, int connectorIndex);
    void createthrusters$clearShipDockBinding(UUID dockId);
    boolean createthrusters$hasShipDockBinding();
    default UUID createthrusters$getShipDockId(){ return null; }
    String createthrusters$getShipDockName();
    int createthrusters$getShipDockConnectorIndex();
    void createthrusters$setShipControlModuleBinding(String shipName, int connectorIndex);
    void createthrusters$clearShipControlModuleBinding();
    boolean createthrusters$hasShipControlModuleBinding();
    boolean createthrusters$usesBoundTexture();
}
