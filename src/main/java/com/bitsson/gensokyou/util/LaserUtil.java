package com.bitsson.gensokyou.util;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * 激光弹幕工具类 - 使用粒子渲染，无需Entity
 * 
 * 用法:
 * LaserUtil.spawnLaser(level, start, direction, length, color, delayTicks, durationTicks, damage, owner);
 */
public class LaserUtil {

    /**
     * 生成一条激光（延迟 + 激活）
     * 
     * @param level 世界
     * @param start 起点
     * @param direction 方向（会被归一化）
     * @param maxLength 最大长度
     * @param color RGB颜色
     * @param delayTicks 延迟时间（tick）
     * @param durationTicks 持续时间（tick）
     * @param damage 每次伤害
     * @param owner 发射者
     */
    public static void spawnLaser(ServerLevel level, Vec3 start, Vec3 direction, 
                                 double maxLength, int color,
                                 int delayTicks, int durationTicks,
                                 float damage, LivingEntity owner) {
        Vec3 normalizedDir = direction.normalize();
        
        // 计算实际长度（碰到方块会缩短）
        Vec3 end = start.add(normalizedDir.scale(maxLength));
        BlockHitResult hitResult = level.clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                owner
        ));
        
        double actualLength = maxLength;
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            actualLength = start.distanceTo(hitResult.getLocation());
        }
        Vec3 actualEnd = start.add(normalizedDir.scale(actualLength));
        
        // 延迟阶段 - 红色警告线
        spawnDelayIndicator(level, start, actualEnd, delayTicks);
        
        // 激活阶段 - 实际激光
        spawnActiveLaser(level, start, actualEnd, normalizedDir, color, 
                        delayTicks, durationTicks, damage, owner);
    }

    /**
     * 生成延迟警告指示器（红色粒子线）
     */
    private static void spawnDelayIndicator(ServerLevel level, Vec3 start, Vec3 end, int delayTicks) {
        // 每5 tick显示一次红色警告线
        for (int tick = 0; tick < delayTicks; tick += 5) {
            int finalTick = tick;
            level.getServer().tell(new net.minecraft.server.TickTask(
                    level.getServer().getTickCount() + finalTick,
                    () -> spawnParticleLine(level, start, end, 0xFF0000, 0.1f, 20)
            ));
        }
    }

    /**
     * 生成激活阶段激光（彩色粒子 + 伤害）
     */
    private static void spawnActiveLaser(ServerLevel level, Vec3 start, Vec3 end, Vec3 direction,
                                        int color, int delayTicks, int durationTicks,
                                        float damage, LivingEntity owner) {
        double radius = 0.3;
        
        // 每5 tick更新一次激光
        for (int tick = 0; tick < durationTicks; tick += 5) {
            int finalTick = tick;
            level.getServer().tell(new net.minecraft.server.TickTask(
                    level.getServer().getTickCount() + delayTicks + finalTick,
                    () -> {
                        // 显示激光粒子
                        spawnParticleLine(level, start, end, color, 0.3f, 30);
                        
                        // 造成伤害
                        damageLaser(level, start, end, radius, damage, owner);
                    }
            ));
        }
    }

    /**
     * 生成粒子线
     */
    private static void spawnParticleLine(ServerLevel level, Vec3 start, Vec3 end, 
                                         int color, float size, int particleCount) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        
        DustParticleOptions particle = new DustParticleOptions(
                new Vector3f(r, g, b), size
        );
        
        for (int i = 0; i < particleCount; i++) {
            double t = (double) i / particleCount;
            Vec3 pos = start.lerp(end, t);
            
            level.sendParticles(
                    particle,
                    pos.x, pos.y, pos.z,
                    1, 0, 0, 0, 0
            );
        }
    }

    /**
     * 激光造成伤害
     */
    private static void damageLaser(ServerLevel level, Vec3 start, Vec3 end, 
                                   double radius, float damage, LivingEntity owner) {
        AABB scanBox = new AABB(start, end).inflate(radius);
        List<Entity> entities = level.getEntities(owner, scanBox);
        
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity)) {
                continue;
            }
            
            Vec3 entityPos = entity.position().add(0, entity.getBbHeight() / 2, 0);
            double dist = distanceToLineSegment(entityPos, start, end);
            
            if (dist <= radius && entity.isAlive()) {
                DamageSource source = level.damageSources().mobProjectile(
                        owner, owner
                );
                entity.hurt(source, damage);
            }
        }
    }

    /**
     * 计算点到线段的距离
     */
    private static double distanceToLineSegment(Vec3 point, Vec3 lineStart, Vec3 lineEnd) {
        Vec3 line = lineEnd.subtract(lineStart);
        double lineLength = line.length();
        
        if (lineLength < 0.0001) {
            return point.distanceTo(lineStart);
        }
        
        Vec3 lineDir = line.normalize();
        Vec3 toPoint = point.subtract(lineStart);
        
        double t = toPoint.dot(lineDir) / lineLength;
        t = Math.max(0, Math.min(1, t));
        
        Vec3 closestPoint = lineStart.add(line.scale(t));
        return point.distanceTo(closestPoint);
    }
}
