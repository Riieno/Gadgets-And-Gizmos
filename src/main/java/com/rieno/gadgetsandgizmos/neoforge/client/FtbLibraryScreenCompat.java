package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Method;

// Handle FTB Library screen input
public final class FtbLibraryScreenCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CLIENT_API = "dev.ftb.mods.ftblibrary.api.client.FTBLibraryClientApi";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ftb library screen compat
    private FtbLibraryScreenCompat() {
    }

    // Register the advanced controller screen blacklist
    public static boolean registerAdvancedControllerScreenBlacklist() {
        try {
            Class<?> apiClass = Class.forName(CLIENT_API);
            Object api = apiClass.getMethod("get").invoke(null);
            Method addBlacklist = apiClass.getMethod("addSidebarScreenBlacklist", String[].class);
            addBlacklist.invoke(api, (Object) new String[]{
                    AdvancedContraptionControllerScreen.class.getName()
            });
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException | LinkageError err) {
            LOGGER.warn("[CT][Compat] Could not hide the FTB sidebar on the controller graph", err);
            return false;
        }
    }
}
