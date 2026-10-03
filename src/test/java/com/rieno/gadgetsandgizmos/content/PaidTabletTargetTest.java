package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaidTabletTargetTest{
    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    @Test void onlyBlockmatesOffersControllerPairing(){
        assertTrue(PaidTabletApps.standalone(PaidTabletApps.DIGISABLE.id()));
        assertTrue(PaidTabletApps.standalone(PaidTabletApps.MANIFEST.id()));
        assertFalse(PaidTabletApps.standalone(PaidTabletApps.BLOCKMATES.id()));
        assertTrue(PaidTabletApps.readerEnabled(PaidTabletApps.DIGISABLE.id()));
        assertFalse(PaidTabletApps.readerEnabled(PaidTabletApps.MANIFEST.id()));
        assertTrue(PaidTabletApps.readerEnabled(PaidTabletApps.BLOCKMATES.id()));
        assertTrue("archives".equals(PaidTabletApps.DIGISABLE.tabs().getFirst().id()));
        assertFalse(PaidTabletApps.DIGISABLE.tabs().stream().flatMap(tab -> tab.actions().stream()).anyMatch("pair"::equals));
        assertFalse(PaidTabletApps.MANIFEST.tabs().stream().flatMap(tab -> tab.actions().stream()).anyMatch("pair"::equals));
        assertFalse(PaidTabletApps.MANIFEST.tabs().stream().flatMap(tab -> tab.actions().stream()).anyMatch("link_workers"::equals));
        assertTrue(PaidTabletApps.MANIFEST.tabs().stream().allMatch(tab -> tab.actions().contains("inspect")));
        assertTrue(PaidTabletApps.MANIFEST.tabs().stream().allMatch(tab -> tab.actions().contains("detach")));
        assertTrue(PaidTabletApps.BLOCKMATES.tabs().stream().flatMap(tab -> tab.actions().stream()).anyMatch("pair"::equals));
    }

    @Test void paidAppsAreNotMistakenForRemoteDesktop(){
        assertFalse(DiagnosticTabletApps.isCanonicalDefinition(PaidTabletApps.DIGISABLE, "rdp"));
        assertFalse(DiagnosticTabletApps.isCanonicalDefinition(PaidTabletApps.MANIFEST, "rdp"));
        assertFalse(DiagnosticTabletApps.isCanonicalDefinition(PaidTabletApps.BLOCKMATES, "rdp"));
        assertTrue(DiagnosticTabletApps.isCanonicalDefinition(PaidTabletApps.DIGISABLE, "digisable"));
        assertTrue(DiagnosticTabletApps.isCanonicalDefinition(PaidTabletApps.MANIFEST, "manifest"));
    }
}
