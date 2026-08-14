package com.rieno.gadgetsandgizmos.content.pose;

// Define editable Armor Stand pose parts
public enum ArmorStandPosePart {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    HEAD("Head", "Head"),
    BODY("Body", "Body"),
    LEFT_ARM("LeftArm", "Left Arm"),
    RIGHT_ARM("RightArm", "Right Arm"),
    LEFT_LEG("LeftLeg", "Left Leg"),
    RIGHT_LEG("RightLeg", "Right Leg");

    // Tag key
    private final String tagKey;
    // Display label
    private final String label;

    // Initialize the armor stand pose part
    ArmorStandPosePart(String tagKey, String label) {
        this.tagKey = tagKey;
        this.label = label;
    }

    // Get the tag key
    public String tagKey() {
        return tagKey;
    }

    // Get the label
    public String label() {
        return label;
    }
}
