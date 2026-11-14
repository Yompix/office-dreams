package com.yompix.glukhoman.client.render;

import com.yompix.glukhoman.entity.ListenerEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/** Слухач: ползёт на четырёх длинных конечностях, вместо глаз — гладкая голова с веерами «ушей». */
public class ListenerModel extends SinglePartEntityModel<ListenerEntity> {
    private final ModelPart root, head, lEar, rEar, fl, fr, bl, br;

    public ListenerModel(ModelPart root) {
        this.root = root;
        head = root.getChild("head");
        lEar = head.getChild("left_ear");
        rEar = head.getChild("right_ear");
        fl = root.getChild("front_left");
        fr = root.getChild("front_right");
        bl = root.getChild("back_left");
        br = root.getChild("back_right");
    }

    public static TexturedModelData data() {
        ModelData d = new ModelData();
        ModelPartData r = d.getRoot();
        r.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-4, -3, -8, 8, 6, 16), ModelTransform.of(0, 15, 0, -0.08f, 0, 0));
        ModelPartData h = r.addChild("head", ModelPartBuilder.create().uv(0, 24).cuboid(-3, -3, -7, 6, 6, 7).uv(0, 40).cuboid(-2, 1, -8, 4, 2, 2),
                ModelTransform.pivot(0, 15, -8));
        h.addChild("left_ear", ModelPartBuilder.create().uv(28, 24).cuboid(0, -7, -2, 1, 9, 6), ModelTransform.of(3, -1, -3, 0, 0, 0.35f));
        h.addChild("right_ear", ModelPartBuilder.create().uv(28, 24).mirrored().cuboid(-1, -7, -2, 1, 9, 6), ModelTransform.of(-3, -1, -3, 0, 0, -0.35f));
        r.addChild("front_left", ModelPartBuilder.create().uv(48, 0).cuboid(-1, 0, -1, 2, 12, 2), ModelTransform.of(4, 13, -6, -0.35f, 0, -0.5f));
        r.addChild("front_right", ModelPartBuilder.create().uv(48, 0).cuboid(-1, 0, -1, 2, 12, 2), ModelTransform.of(-4, 13, -6, -0.35f, 0, 0.5f));
        r.addChild("back_left", ModelPartBuilder.create().uv(56, 0).cuboid(-1, 0, -1, 2, 12, 2), ModelTransform.of(4, 13, 6, 0.35f, 0, -0.5f));
        r.addChild("back_right", ModelPartBuilder.create().uv(56, 0).cuboid(-1, 0, -1, 2, 12, 2), ModelTransform.of(-4, 13, 6, 0.35f, 0, 0.5f));
        return TexturedModelData.of(d, 64, 64);
    }

    @Override
    public ModelPart getPart() {
        return root;
    }

    @Override
    public void setAngles(ListenerEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        float s = MathHelper.cos(limbAngle * 0.9f) * 0.7f * limbDistance;
        fl.pitch = -0.35f + s;
        br.pitch = 0.35f + s;
        fr.pitch = -0.35f - s;
        bl.pitch = 0.35f - s;
        boolean listen = e.mode() == ListenerEntity.LISTEN;
        float twitch = listen ? MathHelper.sin(age * 2.7f) * 0.25f : 0;
        head.yaw = headYaw * 0.0174533f + twitch;
        head.roll = listen ? MathHelper.sin(age * 1.3f) * 0.3f : 0;
        float flare = listen || e.mode() == ListenerEntity.HUNT ? 0.75f : 0.3f;
        lEar.roll = flare + MathHelper.sin(age * 0.4f) * 0.05f;
        rEar.roll = -flare - MathHelper.sin(age * 0.4f) * 0.05f;
    }
}
