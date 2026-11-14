package com.yompix.glukhoman.client.render;

import com.yompix.glukhoman.entity.PoleEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/** Жердь: длинные ноги и руки до колен, тонкое тело, шея и голова набок, сучья на макушке. */
public class PoleModel extends SinglePartEntityModel<PoleEntity> {
    private final ModelPart root, head, neck, leftArm, rightArm, leftLeg, rightLeg;

    public PoleModel(ModelPart root) {
        this.root = root;
        head = root.getChild("head");
        neck = root.getChild("neck");
        leftArm = root.getChild("left_arm");
        rightArm = root.getChild("right_arm");
        leftLeg = root.getChild("left_leg");
        rightLeg = root.getChild("right_leg");
    }

    public static TexturedModelData data() {
        ModelData d = new ModelData();
        ModelPartData r = d.getRoot();
        r.addChild("left_leg", ModelPartBuilder.create().uv(0, 0).cuboid(-1, 0, -1, 2, 30, 2), ModelTransform.pivot(2, -6, 0));
        r.addChild("right_leg", ModelPartBuilder.create().uv(8, 0).cuboid(-1, 0, -1, 2, 30, 2), ModelTransform.pivot(-2, -6, 0));
        r.addChild("body", ModelPartBuilder.create().uv(16, 0).cuboid(-3, 0, -2, 6, 16, 4), ModelTransform.pivot(0, -22, 0));
        r.addChild("left_arm", ModelPartBuilder.create().uv(36, 0).cuboid(0, 0, -1, 2, 32, 2), ModelTransform.pivot(3, -21, 0));
        r.addChild("right_arm", ModelPartBuilder.create().uv(44, 0).cuboid(-2, 0, -1, 2, 32, 2), ModelTransform.pivot(-3, -21, 0));
        r.addChild("neck", ModelPartBuilder.create().uv(52, 0).cuboid(-1, -7, -1, 2, 7, 2), ModelTransform.pivot(0, -22, 0));
        r.addChild("head", ModelPartBuilder.create().uv(0, 40).cuboid(-3, -8, -3, 6, 8, 6)
                .uv(30, 40).cuboid(-5, -13, 0, 1, 7, 1).uv(30, 40).cuboid(3, -14, -1, 1, 8, 1).uv(34, 40).cuboid(-4, -11, 0, 2, 1, 1),
                ModelTransform.pivot(0, -29, 0));
        return TexturedModelData.of(d, 64, 64);
    }

    @Override
    public ModelPart getPart() {
        return root;
    }

    @Override
    public void setAngles(PoleEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        boolean frozen = e.isFrozen() || e.isApparition();
        float swing = frozen ? 0 : MathHelper.cos(limbAngle * 0.5f) * 0.5f * limbDistance;
        leftLeg.pitch = swing;
        rightLeg.pitch = -swing;
        leftArm.pitch = -swing * 0.6f;
        rightArm.pitch = swing * 0.6f;
        leftArm.roll = -0.06f;
        rightArm.roll = 0.06f;
        head.yaw = headYaw * 0.0174533f;
        head.pitch = headPitch * 0.0174533f;
        head.roll = 0.42f + (frozen ? 0 : MathHelper.sin(age * 0.3f) * 0.15f);
        neck.roll = 0.12f;
    }
}
