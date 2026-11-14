package com.yompix.glukhoman.client.render;

import com.yompix.glukhoman.entity.ChoirEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/** Хор: тёмная туша с бледными лицами-головами, которые «дышат», и длинными руками. */
public class ChoirModel extends SinglePartEntityModel<ChoirEntity> {
    private final ModelPart root, body, lArm, rArm;
    private final ModelPart[] faces = new ModelPart[7];
    static final float[][] FACE = {{-5, -22, -7}, {3, -25, -6}, {-1, -14, -7}, {6, -16, -6}, {-7, -10, -6}, {2, -6, -6}, {-3, -29, -3}};

    public ChoirModel(ModelPart root) {
        this.root = root;
        body = root.getChild("body");
        lArm = root.getChild("left_arm");
        rArm = root.getChild("right_arm");
        for (int i = 0; i < faces.length; i++) faces[i] = body.getChild("face" + i);
    }

    public static TexturedModelData data() {
        ModelData d = new ModelData();
        ModelPartData r = d.getRoot();
        ModelPartData b = r.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-9, -30, -6, 18, 30, 12).uv(0, 44).cuboid(-7, 0, -5, 14, 20, 10),
                ModelTransform.pivot(0, 4, 0));
        for (int i = 0; i < FACE.length; i++)
            b.addChild("face" + i, ModelPartBuilder.create().uv(64, 0).cuboid(-2.5f, -2.5f, -2.5f, 5, 5, 5),
                    ModelTransform.pivot(FACE[i][0], FACE[i][1], FACE[i][2]));
        r.addChild("left_arm", ModelPartBuilder.create().uv(64, 20).cuboid(0, 0, -1.5f, 3, 34, 3), ModelTransform.pivot(9, -22, 0));
        r.addChild("right_arm", ModelPartBuilder.create().uv(80, 20).cuboid(-3, 0, -1.5f, 3, 34, 3), ModelTransform.pivot(-9, -22, 0));
        return TexturedModelData.of(d, 128, 128);
    }

    @Override
    public ModelPart getPart() {
        return root;
    }

    @Override
    public void setAngles(ChoirEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        boolean stun = e.stunTicks() > 0;
        float s = MathHelper.cos(limbAngle * 0.4f) * 0.4f * limbDistance;
        lArm.pitch = -s - (stun ? 1.2f : 0);
        rArm.pitch = s - (stun ? 1.2f : 0);
        lArm.roll = -0.1f;
        rArm.roll = 0.1f;
        body.yaw = headYaw * 0.004f;
        body.roll = stun ? MathHelper.sin(age * 2.5f) * 0.08f : MathHelper.sin(age * 0.05f) * 0.03f;
        for (int i = 0; i < faces.length; i++) {
            float k = MathHelper.sin(age * 0.12f + i * 1.7f);
            faces[i].xScale = faces[i].yScale = faces[i].zScale = 1f + k * 0.12f;
            faces[i].yaw = MathHelper.sin(age * 0.07f + i) * 0.4f;
            faces[i].roll = MathHelper.cos(age * 0.05f + i * 2) * 0.3f;
        }
    }
}
