package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.util.Mth;

// Build the simplified ship outline drawn in the controller minimap
final class AdvancedControllerMinimapGeometry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced controller minimap geometry
    private AdvancedControllerMinimapGeometry() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the fit
    static Transform fit(double worldLeft, double worldTop, double worldRight, double worldBottom,
                         double mapLeft, double mapTop, double mapWidth, double mapHeight) {
        double worldWidth = Math.max(1.0D, worldRight - worldLeft);
        double worldHeight = Math.max(1.0D, worldBottom - worldTop);
        double usableWidth = Math.max(1.0D, mapWidth);
        double usableHeight = Math.max(1.0D, mapHeight);
        double scale = Math.min(usableWidth / worldWidth, usableHeight / worldHeight);
        double originX = mapLeft + (usableWidth - worldWidth * scale) * 0.5D;
        double originY = mapTop + (usableHeight - worldHeight * scale) * 0.5D;
        return new Transform(worldLeft, worldTop, worldWidth, worldHeight,
                originX, originY, scale);
    }

    // Get the viewport
    static Rect viewport(Transform transform, double worldLeft, double worldTop,
                         double worldRight, double worldBottom) {
        Rect mapped = new Rect(
                transform.mapX(worldLeft),
                transform.mapY(worldTop),
                transform.mapX(worldRight),
                transform.mapY(worldBottom));
        return mapped.clippedTo(transform.contentBounds());
    }

    // Get the pan for world center
    static double panForWorldCenter(double worldCenter, double zoom, double viewportPixels) {
        return viewportPixels * 0.5D - worldCenter * zoom;
    }

    // Store the transform
    record Transform(double worldLeft, double worldTop, double worldWidth, double worldHeight,
                     double originX, double originY, double scale) {
        // Map the x
        double mapX(double worldX) {
            return originX + (worldX - worldLeft) * scale;
        }

        // Map the y
        double mapY(double worldY) {
            return originY + (worldY - worldTop) * scale;
        }

        // Get the world x
        double worldX(double mapX) {
            return worldLeft + (mapX - originX) / scale;
        }

        // Get the world y
        double worldY(double mapY) {
            return worldTop + (mapY - originY) / scale;
        }

        // Get the content bounds
        Rect contentBounds() {
            return new Rect(originX, originY,
                    originX + worldWidth * scale, originY + worldHeight * scale);
        }
    }

    // Store the rect
    record Rect(double left, double top, double right, double bottom) {
        // Get the width
        double width() {
            return Math.max(0.0D, right - left);
        }

        // Get the height
        double height() {
            return Math.max(0.0D, bottom - top);
        }

        // Get the center x
        double centerX() {
            return (left + right) * 0.5D;
        }

        // Get the center y
        double centerY() {
            return (top + bottom) * 0.5D;
        }

        // Check if this contains the value
        boolean contains(double x, double y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }

        // Clip the rectangle to the bounds
        Rect clippedTo(Rect bounds) {
            double clippedLeft = Mth.clamp(left, bounds.left, bounds.right);
            double clippedTop = Mth.clamp(top, bounds.top, bounds.bottom);
            double clippedRight = Mth.clamp(right, bounds.left, bounds.right);
            double clippedBottom = Mth.clamp(bottom, bounds.top, bounds.bottom);
            return new Rect(
                    Math.min(clippedLeft, clippedRight),
                    Math.min(clippedTop, clippedBottom),
                    Math.max(clippedLeft, clippedRight),
                    Math.max(clippedTop, clippedBottom));
        }
    }
}
