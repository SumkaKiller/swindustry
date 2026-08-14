package com.jokerdayn.swindustry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;

/**
 * A delegating {@link VertexConsumer} that scales down the alpha transparency of quads and vertices.
 * Used for true translucent ghost block rendering (Litematica / Create style).
 */
public final class AlphaVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final float alphaScale;

    public AlphaVertexConsumer(VertexConsumer delegate, float alphaScale) {
        this.delegate = delegate;
        this.alphaScale = alphaScale;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        int scaledAlpha = Math.max(0, Math.min(255, (int) (a * alphaScale)));
        delegate.setColor(r, g, b, scaledAlpha);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
        delegate.setNormal(normalX, normalY, normalZ);
        return this;
    }

    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float red, float green, float blue,
                            float alpha, int packedLight, int packedOverlay) {
        delegate.putBulkData(pose, quad, red, green, blue, alpha * alphaScale, packedLight, packedOverlay);
    }

    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness,
                            float red, float green, float blue, float alpha,
                            int[] lightmap, int packedOverlay, boolean readExistingColor) {
        delegate.putBulkData(pose, quad, brightness, red, green, blue, alpha * alphaScale,
            lightmap, packedOverlay, readExistingColor);
    }
}
