package opus.minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Analytic rounded shapes with antialiased fills, strokes and cutouts. */
final class ShapeRenderer {
    private ShapeRenderer() {}

    public record Rect(float x0, float y0, float x1, float y1) {}

    /** Rounded shader stroke, including its antialiasing fringe. */
    public static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float width, int color) {
        float length = (float) Math.hypot(x1 - x0, y1 - y0);
        if (length == 0 || width <= 0) return;
        g.pose().pushPose();
        try {
            g.pose().translate(x0, y0, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) Math.atan2(y1 - y0, x1 - x0)));
            rounded(g, -width / 2, -width / 2, length + width, width,
                width / 2, width / 2, width / 2, width / 2, color);
        } finally { g.pose().popPose(); }
    }

    public static void rounded(GuiGraphics g, float x, float y, float w, float h,
                               float tl, float tr, float br, float bl, int argb) {
        rounded(g, x, y, w, h, tl, tr, br, bl, argb, null);
    }

    public static void rounded(GuiGraphics g, float x, float y, float w, float h,
                               float tl, float tr, float br, float bl, int argb, Rect cutout) {
        drawShape(g, x, y, w, h, tl, tr, br, bl, argb, 0, 0, cutout);
    }

    public static void outline(GuiGraphics g, float x, float y, float w, float h,
                               float radius, float width, float offset, int argb, Rect cutout) {
        if (width <= 0 || w <= 0 || h <= 0) return;
        float expansion = Math.max(0, offset) + width;
        float r = Math.max(0, Math.min(radius, Math.min(w, h) / 2f)) + expansion;
        drawShape(g, x - expansion, y - expansion, w + expansion * 2, h + expansion * 2,
            r, r, r, r, 0, argb, width, cutout);
    }

    public static void shadow(GuiGraphics g, float x, float y, float w, float h,
                              float cornerRadius, float blurRadius, float offX, float offY, int argb) {
        drawShape(g, x + offX, y + offY, w, h, cornerRadius, cornerRadius, cornerRadius, cornerRadius,
            argb, 0, 0, null, Math.max(0, blurRadius));
    }

    /** Draws the fill and the inset border together, including transparent fills. */
    public static void border(GuiGraphics g, float x, float y, float w, float h,
                              float radius, float width, int borderColor, Integer bgColor, Rect cutout) {
        drawShape(g, x, y, w, h, radius, radius, radius, radius,
            bgColor == null ? 0 : bgColor, borderColor, Math.max(0, width), cutout);
    }

    private static void drawShape(GuiGraphics g, float x, float y, float w, float h,
                                  float tl, float tr, float br, float bl,
                                  int fill, int edge, float strokeWidth, Rect cutout) {
        drawShape(g, x, y, w, h, tl, tr, br, bl, fill, edge, strokeWidth, cutout, 0);
    }

    private static void drawShape(GuiGraphics g, float x, float y, float w, float h,
                                  float tl, float tr, float br, float bl,
                                  int fill, int edge, float strokeWidth, Rect cutout, float shadowBlur) {
        if (w <= 0 || h <= 0) return;
        // Flush the shared builder before changing uniforms or submitting our quad.
        g.flush();
        ShaderInstance shader = ShaderPrograms.roundedShape();
        ShaderInstance previousShader = RenderSystem.getShader();
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        try {
            float maxRadius = Math.min(w, h) / 2f;
            shader.safeGetUniform("ShapeSize").set(w, h);
            shader.safeGetUniform("CornerRadii").set(clampRadius(tl, maxRadius), clampRadius(tr, maxRadius),
                clampRadius(br, maxRadius), clampRadius(bl, maxRadius));
            shader.safeGetUniform("StrokeWidth").set(strokeWidth);
            shader.safeGetUniform("ShadowBlur").set(shadowBlur);
            setColor(shader, "FillColor", fill);
            setColor(shader, "EdgeColor", edge);
            boolean hasCutout = cutout != null && cutout.x1() > cutout.x0() && cutout.y1() > cutout.y0();
            shader.safeGetUniform("HasCutout").set(hasCutout ? 1 : 0);
            if (hasCutout) {
                shader.safeGetUniform("Cutout").set(cutout.x0() - x, cutout.y0() - y,
                    cutout.x1() - x, cutout.y1() - y);
            }

            Matrix4f pose = g.pose().last().pose();
            // Extend beyond the mathematical shape so the outside half of the AA fringe is rasterized.
            float scaleX = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
            float scaleY = (float) Math.sqrt(pose.m10() * pose.m10() + pose.m11() * pose.m11());
            float pixelScale = (float) Minecraft.getInstance().getWindow().getGuiScale() * Math.min(scaleX, scaleY);
            float pad = shadowBlur + 1.5f / Math.max(pixelScale, 0.001f);
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            vertex(buffer, pose, x, y, -pad, -pad);
            vertex(buffer, pose, x, y, -pad, h + pad);
            vertex(buffer, pose, x, y, w + pad, h + pad);
            vertex(buffer, pose, x, y, w + pad, -pad);
            RenderSystem.setShader(() -> shader);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.setShader(() -> previousShader);
            if (cullEnabled) RenderSystem.enableCull();
            if (!blendEnabled) RenderSystem.disableBlend();
        }
    }

    private static float clampRadius(float radius, float maxRadius) {
        return Math.max(0, Math.min(radius, maxRadius));
    }

    private static void setColor(ShaderInstance shader, String uniform, int color) {
        shader.safeGetUniform(uniform).set(((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f,
            (color & 255) / 255f, ((color >>> 24) & 255) / 255f);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f pose, float x, float y, float u, float v) {
        buffer.addVertex(pose, x + u, y + v, 0f).setUv(u, v);
    }
}
