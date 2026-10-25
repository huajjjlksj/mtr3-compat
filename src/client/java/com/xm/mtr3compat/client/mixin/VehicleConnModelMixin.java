package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.core.util.Matrices;
import com.lx862.mtrscripting.core.util.model.ModelJS;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.ante.ConnModelRegistry;
import org.mtr.mod.data.VehicleExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * JCM 的 {@code VehicleScriptContext.drawConnModel(...)} 是空实现，直接
 * {@code throw new NotImplementedException}（车厢贯通道模型）。
 *
 * 部分脚本在 render 里每帧调用它，异常被 ParsedScript 捕获后进入 4 秒失败冷却，
 * 该车辆所有脚本（含 LCD）随之停止刷新 —— 表现为 LCD 显示出来但画面卡死。
 *
 * 这里改为登记模型+矩阵，由 {@link ConnStretchRenderMixin} 在渲染管线中
 * 平移到该连接处端面后绘制，与 drawConnStretchTexture 走同一套关节定位。
 */
@Mixin(targets = "com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleScriptContext", remap = false)
public class VehicleConnModelMixin {

	private static boolean warned;

	@Shadow
	private VehicleExtension vehicleExtension;

	@Shadow
	private int[] myCars;

	@Inject(method = "drawConnModel(Lcom/lx862/mtrscripting/core/util/model/ModelJS;ILcom/lx862/mtrscripting/core/util/Matrices;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void mtr3compat$registerConnModel(ModelJS model, int carIndex, Matrices matrices, CallbackInfo ci) {
		ci.cancel();
		final int trailing = trailingCar(carIndex);
		if (trailing < 0 || model == null) {
			return;
		}
		ConnModelRegistry.put(vehicleExtension.getHexId(), trailing, model, matrices);
	}

	@Inject(method = "drawConnModel(Lcom/lx862/mtrscripting/core/util/model/DynamicModelHolderJS;ILcom/lx862/mtrscripting/core/util/Matrices;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void mtr3compat$skipDynamicConnModel(CallbackInfo ci) {
		ci.cancel();
		if (!warned) {
			warned = true;
			Mtr3CompatClient.LOGGER.info("[mtr3compat] drawConnModel(DynamicModelHolder) 暂不支持，已忽略以保持脚本继续运行");
		}
	}

	/** conn 的 carIndex 位于 myCars[carIndex] 与 myCars[carIndex+1] 之间，取其“后车”的全局车厢序号。 */
	private int trailingCar(int carIndex) {
		final int[] cars = myCars;
		if (cars == null || carIndex < 0 || carIndex + 1 >= cars.length) {
			return -1;
		}
		final int a = cars[carIndex];
		final int b = cars[carIndex + 1];
		if (b == a + 1) {
			return b;
		} else if (b == a - 1) {
			return a;
		}
		return -1;
	}
}
