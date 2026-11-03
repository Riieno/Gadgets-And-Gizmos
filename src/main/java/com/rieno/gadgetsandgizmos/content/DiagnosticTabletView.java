package com.rieno.gadgetsandgizmos.content;

import net.minecraft.util.StringRepresentable;

// Keep the tablet's model cycle independent of its mounting face
public enum DiagnosticTabletView implements StringRepresentable{
    FLAT("flat"), NEG_22_5("neg_22_5"), ANGLED_45("45"), ANGLED_22_5("22_5");

    private final String id;

    DiagnosticTabletView(String id){
        this.id = id;
    }

    @Override
    public String getSerializedName(){
        return id;
    }

    // Cycle through the four supplied models
    public DiagnosticTabletView next(){
        return switch(this){
            case FLAT -> NEG_22_5;
            case NEG_22_5 -> ANGLED_45;
            case ANGLED_45 -> ANGLED_22_5;
            case ANGLED_22_5 -> FLAT;
        };
    }
}
