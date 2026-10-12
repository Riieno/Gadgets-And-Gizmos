package com.rieno.gadgetsandgizmos.content;

// Identify launcher actions independently of their client key assignments
public final class EntityLauncherActions {
    public static final int FIRE = 1;
    public static final int REEL = 2;
    public static final int LENGTHEN = 4;
    public static final int SHORTEN = 8;
    public static final int DETACH = 16;
    public static final int ALL = FIRE | REEL | LENGTHEN | SHORTEN | DETACH;
    public static final int ROPE_CONTROLS = REEL | LENGTHEN | SHORTEN;

    private EntityLauncherActions() {}
}
