package com.xm.mtr3compat.client.mixin;

import com.lx862.jcm.mod.util.MTRUtil;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import org.mtr.mapping.holder.Box;
import org.mtr.mod.render.PositionAndRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * JCM 判断“本侧/对侧能否开门”用的是自车身中线 ±width/2 的一条薄板 + 1 格半径做方块扫描
 * （见 MTRUtil#canOpenDoors）。部分 MTR3 资源包的模型比 properties.width 宽，或者车门内侧
 * 有装饰方块，扫描扫不到真实站台，doorLeftOpen/doorRightOpen 全为 false，于是 LCD 上的
 * 本侧/对侧开门小灯永远不亮（原生车门仍由 MTR4 自己的门框判定，所以照常开关）。
 * <p>
 * 这里只在原判定为 false 时，用放大后的盒子重试一次，命中即视为可开门。该判定只被脚本使用，
 * 不影响 MTR4 原生的车门行为。
 */
@Mixin(value = MTRUtil.class, remap = false)
public class DoorScanFallbackMixin {

	private static final ThreadLocal<Boolean> IN_FALLBACK = new ThreadLocal<>();
	private static final AtomicInteger LOGGED = new AtomicInteger();

	@Inject(method = "canOpenDoors", at = @At("RETURN"), cancellable = true)
	private static void mtr3compat$widenScan(Box doorway, PositionAndRotation positionAndRotation, CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(IN_FALLBACK.get())) {
			return;
		}
		if (Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}
		IN_FALLBACK.set(true);
		try {
			for (final double expand : new double[]{1.5, 3.0}) {
				final Box wider = new Box(
						doorway.getMinXMapped() - expand, doorway.getMinYMapped() - 1, doorway.getMinZMapped() - 1,
						doorway.getMaxXMapped() + expand, doorway.getMaxYMapped() + 1, doorway.getMaxZMapped() + 1);
				if (Boolean.TRUE.equals(MTRUtil.canOpenDoors(wider, positionAndRotation))) {
					if (LOGGED.getAndIncrement() < 6) {
						Mtr3CompatClient.LOGGER.info("[mtr3compat] 开门扫描放宽命中 expand={} x=[{},{}] y={} z=[{},{}]",
								expand, fmt(doorway.getMinXMapped()), fmt(doorway.getMaxXMapped()),
								fmt(doorway.getMaxYMapped()), fmt(doorway.getMinZMapped()), fmt(doorway.getMaxZMapped()));
					}
					cir.setReturnValue(true);
					return;
				}
			}
		} catch (Throwable e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 开门扫描放宽失败", e);
		} finally {
			IN_FALLBACK.remove();
		}
	}

	private static String fmt(double value) {
		return String.format(java.util.Locale.ROOT, "%.2f", value);
	}
}
