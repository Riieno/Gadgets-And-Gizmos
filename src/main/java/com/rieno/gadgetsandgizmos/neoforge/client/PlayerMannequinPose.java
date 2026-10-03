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
        animateWorker(model, mannequin, ageInTicks);

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

    private static void animateWorker(PlayerModel<?> model, PlayerMannequinEntity mannequin, float ageInTicks) {
        PlayerMannequinEntity.WorkerAnimation animation = mannequin.workerAnimation();
        if (animation == PlayerMannequinEntity.WorkerAnimation.IDLE) return;
        float stride = (float) Math.sin(ageInTicks * 0.72F) * 0.75F;
        boolean walking = animation == PlayerMannequinEntity.WorkerAnimation.WALK
                || animation == PlayerMannequinEntity.WorkerAnimation.CARRY_WALK;
        boolean carrying = animation == PlayerMannequinEntity.WorkerAnimation.CARRY_IDLE
                || animation == PlayerMannequinEntity.WorkerAnimation.CARRY_WALK;
        if (walking) {
            model.rightLeg.xRot = stride;
            model.leftLeg.xRot = -stride;
        }
        if (carrying) {
            float sway = walking ? stride * 0.08F : 0.0F;
            model.rightArm.xRot = -1.18F - sway;
            model.rightArm.yRot = -0.52F;
            model.rightArm.zRot = 0.16F;
            model.leftArm.xRot = -1.18F + sway;
            model.leftArm.yRot = 0.52F;
            model.leftArm.zRot = -0.16F;
            return;
        }
        if (animation == PlayerMannequinEntity.WorkerAnimation.INTERACT) {
            float reach = 0.2F + (float) Math.sin(ageInTicks * 1.4F) * 0.18F;
            model.rightArm.xRot = -1.25F - reach;
            model.leftArm.xRot = -0.45F;
            return;
        }
        model.rightArm.xRot = -stride;
        model.leftArm.xRot = stride;
    }
}
