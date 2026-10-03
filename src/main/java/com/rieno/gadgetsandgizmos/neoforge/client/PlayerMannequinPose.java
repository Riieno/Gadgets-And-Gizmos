package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Rotations;

// Apply the mannequin's saved pose to Minecraft's own player model after vanilla animation.
public final class PlayerMannequinPose {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private PlayerMannequinPose() {
    }

    public static void apply(PlayerModel<?> model, PlayerMannequinEntity mannequin, float ageInTicks) {
        model.attackTime = 0.0F;
        model.crouching = false;
        model.swimAmount = 0.0F;

        rotate(model.head, mannequin.getHeadPose());
        rotate(model.body, mannequin.getBodyPose());
        rotate(model.leftArm, mannequin.getLeftArmPose());
        rotate(model.rightArm, mannequin.getRightArmPose());
        rotate(model.leftLeg, mannequin.getLeftLegPose());
        rotate(model.rightLeg, mannequin.getRightLegPose());

        if (mannequin.isZiplineRiding()) {
            model.body.xRot = 0.0F;
            model.leftArm.xRot = -(float) Math.PI;
            model.rightArm.xRot = -(float) Math.PI;
            model.leftArm.zRot = -0.12F;
            model.rightArm.zRot = 0.12F;
            model.leftLeg.xRot = -0.12F;
            model.rightLeg.xRot = 0.12F;
        }

        if (mannequin.isPassenger()) {
            model.leftLeg.xRot = -1.4137167F;
            model.leftLeg.yRot = (float) (Math.PI / 10.0);
            model.leftLeg.zRot = 0.07853982F;
            model.rightLeg.xRot = -1.4137167F;
            model.rightLeg.yRot = (float) (-Math.PI / 10.0);
            model.rightLeg.zRot = -0.07853982F;
        }

        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftArm.visible = mannequin.isShowArms();
        model.rightArm.visible = mannequin.isShowArms();
        model.leftSleeve.visible = mannequin.isShowArms();
        model.rightSleeve.visible = mannequin.isShowArms();
    }

    private static void rotate(ModelPart part, Rotations pose) {
        part.xRot = pose.getX() * DEG_TO_RAD;
        part.yRot = pose.getY() * DEG_TO_RAD;
        part.zRot = pose.getZ() * DEG_TO_RAD;
    }

}
