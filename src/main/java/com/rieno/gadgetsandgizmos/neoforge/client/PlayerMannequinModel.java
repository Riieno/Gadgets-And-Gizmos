package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Rotations;

// Apply saved player-model parts and poses to mannequin rendering
public class PlayerMannequinModel extends PlayerModel<PlayerMannequinEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether skin layers are rendered
    private boolean renderSkinLayers = true;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the player mannequin model
    public PlayerMannequinModel(ModelPart root) {
        super(root, false);
    }

    // Set up the anim
    @Override
    public void setupAnim(PlayerMannequinEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        this.attackTime = 0.0F;
        this.crouching = false;
        this.swimAmount = 0.0F;
        this.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        this.leftArmPose = HumanoidModel.ArmPose.EMPTY;

        applyPose(this.head, entity.getHeadPose());
        applyPose(this.body, entity.getBodyPose());
        applyPose(this.leftArm, entity.getLeftArmPose());
        applyPose(this.rightArm, entity.getRightArmPose());
        applyPose(this.leftLeg, entity.getLeftLegPose());
        applyPose(this.rightLeg, entity.getRightLegPose());

        if (entity.isPassenger()) {
            this.leftLeg.xRot = -1.4137167F;
            this.leftLeg.yRot = (float) (Math.PI / 10.0);
            this.leftLeg.zRot = 0.07853982F;
            this.rightLeg.xRot = -1.4137167F;
            this.rightLeg.yRot = (float) (-Math.PI / 10.0);
            this.rightLeg.zRot = -0.07853982F;
        }

        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);

        showBaseOnly(entity);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Show all mannequin layers
    void showAllLayers(PlayerMannequinEntity entity) {
        boolean showArms = entity.isShowArms();
        this.head.visible = true;
        this.body.visible = true;
        this.leftArm.visible = showArms;
        this.rightArm.visible = showArms;
        this.leftLeg.visible = true;
        this.rightLeg.visible = true;
        this.hat.visible = renderSkinLayers;
        this.jacket.visible = renderSkinLayers;
        this.leftSleeve.visible = renderSkinLayers && showArms;
        this.rightSleeve.visible = renderSkinLayers && showArms;
        this.leftPants.visible = renderSkinLayers;
        this.rightPants.visible = renderSkinLayers;
        this.hat.skipDraw = !renderSkinLayers;
        this.jacket.skipDraw = !renderSkinLayers;
        this.leftSleeve.skipDraw = !renderSkinLayers || !showArms;
        this.rightSleeve.skipDraw = !renderSkinLayers || !showArms;
        this.leftPants.skipDraw = !renderSkinLayers;
        this.rightPants.skipDraw = !renderSkinLayers;
    }

    // Configure the detail
    void configureDetail(boolean skinLayers, boolean limbs) {
        renderSkinLayers = skinLayers;
    }

    // Show the base only
    void showBaseOnly(PlayerMannequinEntity entity) {
        boolean showArms = entity.isShowArms();
        this.head.visible = true;
        this.body.visible = true;
        this.leftArm.visible = showArms;
        this.rightArm.visible = showArms;
        this.leftLeg.visible = true;
        this.rightLeg.visible = true;
        this.hat.visible = false;
        this.jacket.visible = false;
        this.leftSleeve.visible = false;
        this.rightSleeve.visible = false;
        this.leftPants.visible = false;
        this.rightPants.visible = false;
        this.hat.skipDraw = true;
        this.jacket.skipDraw = true;
        this.leftSleeve.skipDraw = true;
        this.rightSleeve.skipDraw = true;
        this.leftPants.skipDraw = true;
        this.rightPants.skipDraw = true;
    }

    // Show the skin layers only
    void showSkinLayersOnly(PlayerMannequinEntity entity) {
        boolean showArms = entity.isShowArms();
        this.head.visible = false;
        this.body.visible = false;
        this.leftArm.visible = false;
        this.rightArm.visible = false;
        this.leftLeg.visible = false;
        this.rightLeg.visible = false;
        this.hat.visible = renderSkinLayers;
        this.jacket.visible = renderSkinLayers;
        this.leftSleeve.visible = renderSkinLayers && showArms;
        this.rightSleeve.visible = renderSkinLayers && showArms;
        this.leftPants.visible = renderSkinLayers;
        this.rightPants.visible = renderSkinLayers;
        this.hat.skipDraw = !renderSkinLayers;
        this.jacket.skipDraw = !renderSkinLayers;
        this.leftSleeve.skipDraw = !renderSkinLayers || !showArms;
        this.rightSleeve.skipDraw = !renderSkinLayers || !showArms;
        this.leftPants.skipDraw = !renderSkinLayers;
        this.rightPants.skipDraw = !renderSkinLayers;
    }

    // Apply the pose
    private static void applyPose(ModelPart part, Rotations rotations) {
        part.xRot = rotations.getX() * DEG_TO_RAD;
        part.yRot = rotations.getY() * DEG_TO_RAD;
        part.zRot = rotations.getZ() * DEG_TO_RAD;
    }
}
