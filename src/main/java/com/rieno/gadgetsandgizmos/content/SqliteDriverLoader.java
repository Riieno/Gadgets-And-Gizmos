package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

// Load the bundled SQLite driver before any tablet database is opened
final class SqliteDriverLoader {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String DRIVER_CLASS = "org.sqlite.JDBC";
    private static final String BUNDLED_DRIVER = "META-INF/optional-libs/sqlite-jdbc-bundled.jar";
    private static final System.Logger LOGGER = System.getLogger(SqliteDriverLoader.class.getName());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared driver
    private static Driver driver;
    // Shared bundled class loader
    private static URLClassLoader bundledClassLoader;
    // Controls whether bundled driver is being used
    private static boolean usingBundledDriver;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the sqlite driver loader
    private SqliteDriverLoader() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Connect the sqlite driver loader
    static Connection connect(String url) throws SQLException, IOException {
        Connection connection = getDriver().connect(url, new Properties());
        if (connection == null) {
            throw new SQLException("The selected SQLite JDBC driver did not accept " + url);
        }
        return connection;
    }

    // Check if this has bundled driver resource
    static boolean hasBundledDriverResource() {
        return SqliteDriverLoader.class.getClassLoader().getResource(BUNDLED_DRIVER) != null;
    }

    // Check if this is using bundled driver
    static boolean isUsingBundledDriver() {
        return usingBundledDriver;
    }

    // Get the driver
    private static synchronized Driver getDriver() throws SQLException, IOException {
        if (driver != null) return driver;

        ClassLoader applicationClassLoader = SqliteDriverLoader.class.getClassLoader();
        try {
            Class<?> driverClass = Class.forName(DRIVER_CLASS, true, applicationClassLoader);
            driver = instantiate(driverClass);
            usingBundledDriver = false;
            LOGGER.log(System.Logger.Level.INFO, "Using SQLite JDBC provider already available on the mod classpath");
            return driver;
        } catch (ClassNotFoundException ignored) {
        } catch (LinkageError error) {
            throw new SQLException("The installed SQLite JDBC provider could not be initialized", error);
        }

        URL resource = applicationClassLoader.getResource(BUNDLED_DRIVER);
        if (resource == null) {
            throw new SQLException("The bundled SQLite JDBC fallback is missing");
        }

        Path extractedJar = Files.createTempFile("createthrusters-sqlite-jdbc-", ".jar");
        extractedJar.toFile().deleteOnExit();
        try (InputStream input = resource.openStream()) {
            Files.copy(input, extractedJar, StandardCopyOption.REPLACE_EXISTING);
        }

        bundledClassLoader = new URLClassLoader(new URL[]{extractedJar.toUri().toURL()}, applicationClassLoader);
        try {
            driver = instantiate(Class.forName(DRIVER_CLASS, true, bundledClassLoader));
        } catch (ClassNotFoundException err) {
            throw new SQLException("The bundled SQLite JDBC fallback does not contain " + DRIVER_CLASS, err);
        } catch (LinkageError error) {
            throw new SQLException("The bundled SQLite JDBC fallback could not be initialized", error);
        }
        usingBundledDriver = true;
        LOGGER.log(System.Logger.Level.INFO, "Using bundled SQLite JDBC fallback");
        return driver;
    }

    // Get the instantiate
    private static Driver instantiate(Class<?> driverClass) throws SQLException {
        if (!Driver.class.isAssignableFrom(driverClass)) {
            throw new SQLException(driverClass.getName() + " does not implement java.sql.Driver");
        }
        try {
            return (Driver) driverClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException err) {
            throw new SQLException("Unable to create SQLite JDBC driver", err);
        }
    }
}
