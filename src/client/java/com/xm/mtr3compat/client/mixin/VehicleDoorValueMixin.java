package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MTR3 资源包脚本用精确比较判断车门动画阶段，例如：
 * {@code if (train.doorValue() == 0)} 判「门已关」、{@code lastDoorValue == 1} 判「门已开」。
 *
 * MTR4 里车门进度是平滑插值，静态时约为 0.01 / 0.99 而非恰好 0 / 1，
 * 这些相等比较永远不成立，脚本状态机因此卡在「开门」状态（表现为开门小灯只亮一次）。
 * 这里在读数上钳制到 0 / 1，脚本逻辑即可正常走完开→关→熄灭的循环。
 */
@Mixin(value = VehicleWrapper.class, remap = false)
public class VehicleDoorValueMixin {

	@Inject(method = "getDoorValue", at = @At("RETURN"), cancellable = true, require = 0)
	private void mtr3compat$clampDoorValue(CallbackInfoReturnable<Double> cir) {
		final double value = cir.getReturnValueD();
		if (value < 0.01) {
			cir.setReturnValue(0.0);
		} else if (value > 0.99) {
			cir.setReturnValue(1.0);
		}
	}
}
