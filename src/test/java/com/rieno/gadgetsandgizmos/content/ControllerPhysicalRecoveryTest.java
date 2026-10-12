package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS                                                        
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Exercise the physical-state boundary used by retained SCM programs and saved controller blocks
class ControllerPhysicalRecoveryTest{
    /*--------------------------------------------------------##---------------------------------------------------------
    
    =======================================================================================================================
                                                           FUNCTIONS                                                       
    =======================================================================================================================
    
    ------------------------------------------------------------##-----------------------------------------------------*/

    @Test void standaloneReplacementCannotRecoverAnEmbeddedModule(){
        CompoundTag stored = programWithModule("down");
        CompoundTag recovered = ControllerPhysicalState.recoverProgram(stored, new CompoundTag());
        assertFalse(recovered.contains(ControllerPhysicalState.EMBEDDED_BLOCK));
        assertFalse(recovered.contains(ControllerPhysicalState.EMBEDDED_BLOCK_ENTITY));
        assertFalse(recovered.contains(ControllerPhysicalState.PLACER));
        assertFalse(recovered.contains(ControllerPhysicalState.MODULE_PLACER));
        assertEquals(stored.getCompound("AdvancedActiveGraph"), recovered.getCompound("AdvancedActiveGraph"));
        assertEquals(stored.getCompound("ScmConfigurationProfile"), recovered.getCompound("ScmConfigurationProfile"));
        assertEquals("createthrusters:ship_control_module",
                stored.getCompound(ControllerPhysicalState.EMBEDDED_BLOCK).getString("Name"));
    }

    @Test void recoveringAStandaloneProgramCannotDeleteTheNewModule(){
        CompoundTag stored = new CompoundTag();
        stored.putString("ShipName", "Retained ship");
        CompoundTag physical = module("north");
        CompoundTag recovered = ControllerPhysicalState.recoverProgram(stored, physical);
        assertEquals(physical.getCompound(ControllerPhysicalState.EMBEDDED_BLOCK),
                recovered.getCompound(ControllerPhysicalState.EMBEDDED_BLOCK));
        assertEquals(physical.getCompound(ControllerPhysicalState.EMBEDDED_BLOCK_ENTITY),
                recovered.getCompound(ControllerPhysicalState.EMBEDDED_BLOCK_ENTITY));
        assertEquals("Retained ship", recovered.getString("ShipName"));
    }

    @Test void newlyPlacedOrientationAndOwnersOverrideTheRetainedMount(){
        CompoundTag stored = programWithModule("down");
        CompoundTag physical = module("east");
        CompoundTag recovered = ControllerPhysicalState.recoverProgram(stored, physical);
        assertEquals("east", recovered.getCompound(ControllerPhysicalState.EMBEDDED_BLOCK)
                .getCompound("Properties").getString("mount_face"));
        assertEquals(physical.getUUID(ControllerPhysicalState.PLACER), recovered.getUUID(ControllerPhysicalState.PLACER));
        assertEquals(physical.getUUID(ControllerPhysicalState.MODULE_PLACER),
                recovered.getUUID(ControllerPhysicalState.MODULE_PLACER));
        assertNotEquals(stored.getUUID(ControllerPhysicalState.MODULE_PLACER),
                recovered.getUUID(ControllerPhysicalState.MODULE_PLACER));
    }

    @Test void blockSnapshotWinsWhenTheBackendAttachmentChanges(){
        CompoundTag physical = module("up");
        CompoundTag savedBlock = new CompoundTag();
        savedBlock.put(ControllerPhysicalState.TAG_KEY, physical);
        CompoundTag recovered = ControllerPhysicalState.readSavedBlock(programWithModule("down"), savedBlock);
        assertEquals(physical.getCompound(ControllerPhysicalState.EMBEDDED_BLOCK),
                recovered.getCompound(ControllerPhysicalState.EMBEDDED_BLOCK));
        savedBlock.put(ControllerPhysicalState.TAG_KEY, new CompoundTag());
        assertFalse(ControllerPhysicalState.readSavedBlock(programWithModule("down"), savedBlock)
                .contains(ControllerPhysicalState.EMBEDDED_BLOCK));
    }

    @Test void olderBlocksKeepTheirSavedBackingUntilTheyAreSavedAgain(){
        CompoundTag stored = programWithModule("down");
        assertSame(stored, ControllerPhysicalState.readSavedBlock(stored, new CompoundTag()));
    }

    @Test void copiedControllerItemsDoNotCarryAHiddenBackendModule(){
        CompoundTag stored = programWithModule("down");
        stored.putString(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_ID, "retained_manifest");
        stored.put(ControllerPhysicalState.TAG_KEY, module("down"));
        assertTrue(AnalogueContraptionControllerBlockItem.stripEmbeddedSupportData(stored));
        assertFalse(stored.contains(ControllerPhysicalState.EMBEDDED_BLOCK));
        assertTrue(stored.contains(ControllerPhysicalState.TAG_KEY));
        assertTrue(stored.getCompound(ControllerPhysicalState.TAG_KEY).isEmpty());
        assertEquals("retained_manifest", stored.getString(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_ID));
        assertFalse(ControllerPhysicalState.readSavedBlock(programWithModule("down"), stored)
                .contains(ControllerPhysicalState.EMBEDDED_BLOCK));
    }

    /*--------------------------------------------------------##---------------------------------------------------------
    
    =======================================================================================================================
                                                            HELPERS                                                        
    =======================================================================================================================
    
    ------------------------------------------------------------##-----------------------------------------------------*/

    private static CompoundTag programWithModule(String mount){
        CompoundTag stored = module(mount);
        CompoundTag graph = new CompoundTag();
        graph.putString("Node", "schedule");
        stored.put("AdvancedActiveGraph", graph);
        CompoundTag profile = new CompoundTag();
        profile.putString("VehicleType", "airship");
        stored.put("ScmConfigurationProfile", profile);
        return stored;
    }

    private static CompoundTag module(String mount){
        CompoundTag physical = new CompoundTag();
        CompoundTag state = new CompoundTag();
        state.putString("Name", "createthrusters:ship_control_module");
        CompoundTag properties = new CompoundTag();
        properties.putString("mount_face", mount);
        state.put("Properties", properties);
        physical.put(ControllerPhysicalState.EMBEDDED_BLOCK, state);
        CompoundTag entity = new CompoundTag();
        entity.putUUID("Placer", UUID.randomUUID());
        physical.put(ControllerPhysicalState.EMBEDDED_BLOCK_ENTITY, entity);
        physical.putUUID(ControllerPhysicalState.PLACER, UUID.randomUUID());
        physical.putUUID(ControllerPhysicalState.MODULE_PLACER, UUID.randomUUID());
        return physical;
    }
}
