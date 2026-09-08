package com.rieno.gadgetsandgizmos.compat.simulated;

import java.util.UUID;

// Expose dock and SCM binding state on Simulated docking connectors
public interface DockingConnectorBindingAccess {
    void createthrusters$setShipDockBinding(UUID dockId, String dockName, int connectorIndex);
    void createthrusters$clearShipDockBinding(UUID dockId);
    boolean createthrusters$hasShipDockBinding();
    String createthrusters$getShipDockName();
    int createthrusters$getShipDockConnectorIndex();
    void createthrusters$setShipControlModuleBinding(String shipName, int connectorIndex);
    void createthrusters$clearShipControlModuleBinding();
    boolean createthrusters$hasShipControlModuleBinding();
    boolean createthrusters$usesBoundTexture();
}