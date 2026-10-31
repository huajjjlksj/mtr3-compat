package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleWrapper;
import org.mtr.mod.data.VehicleExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 脚本的“本站”被算成下一站的原因：JCM 用 railProgress 与站点 distance 比较，
 * 列车停站时 railProgress 会略大于本站距离，下标前移一格，脚本于是把下一站当成当前站显示
 * （走字屏/LCD 的“本站”取到下一站信息）。
 * <p>
 * 这里改用车辆数据里的 stoppingPoint（本次停车点）作为基准：行车途中它正好是下一站距离，
 * 停站期间仍等于本站距离，下标不会前移，与 NTE 的 ceilingEntry 语义一致。
 */
@Mixin(VehicleWrapper.class)
public abstract class VehicleNextStopIndexMixin {

	@Shadow
	@Final
	protected VehicleExtension vehicleExtension;

	@Inject(method = "findNextStopIndex", at = @At("HEAD"), cancellable = true)
	private void mtr3compat$useStoppingPoint(double overrunTolerance, double currentRailProgress, List<VehicleWrapper.Stop> stops, CallbackInfoReturnable<Integer> cir) {
		if (stops == null || stops.isEmpty() || stops.get(0).distance < 0) {
			// 没有站点距离信息（仅线路概要数据）时沿用原逻辑
			return;
		}
		final double stoppingPoint = vehicleExtension.vehicleExtraData.getStoppingPoint();
		if (stoppingPoint <= 0) {
			return;
		}
		int index = 0;
		for (final VehicleWrapper.Stop stop : stops) {
			if (stop.distance < stoppingPoint - 0.5) {
				index++;
			} else {
				break;
			}
		}
		cir.setReturnValue(Math.min(index, stops.size()));
	}
}
