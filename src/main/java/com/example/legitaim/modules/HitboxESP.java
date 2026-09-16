package com.example.legitaim.modules;

import com.example.legitaim.LegitAimAssistClient;
import com.example.legitaim.config.ModConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

public final class HitboxESP {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private HitboxESP() {}

    public static void render(WorldRenderContext context) {
        ModConfig.EspSnapshot cfg = ModConfig.snapshotEsp();
        if (!cfg.enabled()) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        if (context.camera() == null) return;
        Vec3d cameraPos = context.camera().getPos();
        if (cameraPos == null) return;

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        if (mc.world == null) return;
        List<AbstractClientPlayerEntity> players = mc.world.getPlayers();
        if (players == null || players.isEmpty()) return;

        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            if (cfg.throughWalls()) {
                RenderSystem.depthMask(false);
                RenderSystem.disableDepthTest();
            } else {
                RenderSystem.enableDepthTest();
            }

            RenderSystem.lineWidth(1.0f);

            VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());

            matrices.push();
            try {
                int size = players.size();
                for (int i = 0; i < size; i++) {
                    if (i >= players.size()) break;

                    AbstractClientPlayerEntity player = players.get(i);
                    if (player == null) continue;
                    if (player == mc.player) continue;
                    if (!player.isAlive()) continue;
                    if (player.isSpectator()) continue;
                    if (player.isRemoved()) continue;

                    Box box = player.getBoundingBox();

                    double x0 = box.minX - cameraPos.x;
                    double y0 = box.minY - cameraPos.y;
                    double z0 = box.minZ - cameraPos.z;
                    double x1 = box.maxX - cameraPos.x;
                    double y1 = box.maxY - cameraPos.y;
                    double z1 = box.maxZ - cameraPos.z;

                    drawBox(matrices, buffer,
                        x0, y0, z0, x1, y1, z1,
                        cfg.r(), cfg.g(), cfg.b(), cfg.a());
                }
            } finally {
                matrices.pop();
            }

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();

        } catch (Throwable t) {
            LegitAimAssistClient.LOGGER.error("[LegitAimAssist] ESP render failed", t);
        }
    }

    private static void drawBox(
            MatrixStack matrices, VertexConsumer buffer,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            float r, float g, float b, float a) {

        Matrix4f m = matrices.peek().getPositionMatrix();

        line(m, buffer, x0, y0, z0, x1, y0, z0, r, g, b, a);
        line(m, buffer, x1, y0, z0, x1, y0, z1, r, g, b, a);
        line(m, buffer, x1, y0, z1, x0, y0, z1, r, g, b, a);
        line(m, buffer, x0, y0, z1, x0, y0, z0, r, g, b, a);

        line(m, buffer, x0, y1, z0, x1, y1, z0, r, g, b, a);
        line(m, buffer, x1, y1, z0, x1, y1, z1, r, g, b, a);
        line(m, buffer, x1, y1, z1, x0, y1, z1, r, g, b, a);
        line(m, buffer, x0, y1, z1, x0, y1, z0, r, g, b, a);

        line(m, buffer, x0, y0, z0, x0, y1, z0, r, g, b, a);
        line(m, buffer, x1, y0, z0, x1, y1, z0, r, g, b, a);
        line(m, buffer, x1, y0, z1, x1, y1, z1, r, g, b, a);
        line(m, buffer, x0, y0, z1, x0, y1, z1, r, g, b, a);
    }

    private static void line(
            Matrix4f m, VertexConsumer buffer,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float r, float g, float b, float a) {

        // Đã xoá .next() để fix lỗi "cannot find symbol"
        buffer.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(1.0f, 0.0f, 0.0f);
        buffer.vertex(m, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(1.0f, 0.0f, 0.0f);
    }
}
