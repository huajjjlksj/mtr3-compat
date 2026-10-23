package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.core.util.Matrices;
import com.lx862.mtrscripting.core.util.model.ModelJS;
import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleScriptContext;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.Mtr3CompatConfig;
import com.xm.mtr3compat.client.Mtr3CompatTweak;
import com.xm.mtr3compat.client.NativeTrainLoader;
import org.mtr.mod.data.VehicleExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 部分 MTR3 资源包的脚本在“车厢空间”绘制（drawCarModel 传 null matrices）时，
 * 整体沿车长存在常量偏移。平移量来自 config 的 car_space_z_shift 与运行中可编辑的
 * mtr3-compat-tweak.txt，按车型所属资源包取值；未配置的包不受影响，
 * 显式传 matrices 的绘制也不受影响。整段逻辑吞掉所有异常，避免打断脚本渲染。
 */
@Mixin(value = VehicleScriptContext.class, remap = false)
public class VehicleScriptCarSpaceShiftMixin {

	@Shadow
	private VehicleExtension vehicleExtension;

	@Shadow
	private int[] myCars;

	@Inject(
			method = "drawCarModel(Lcom/lx862/mtrscripting/core/util/model/ModelJS;ILcom/lx862/mtrscripting/core/util/Matrices;)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void mtr3compat$shiftCarSpace(ModelJS model, int carIndex, Matrices matrices, CallbackInfo ci) {
		if (matrices != null) {
			return;
		}
		try {
			final String pack = packOfCar(carIndex);
			if (pack == null) {
				return;
			}
			final double shift = Mtr3CompatConfig.carSpaceShift(pack) + Mtr3CompatTweak.shiftFor(pack);
			if (shift == 0) {
				return;
			}
			final Matrices shifted = new Matrices();
			shifted.translate(0, 0, (float) shift);
			((VehicleScriptContext) (Object) this).drawCarModel(model, carIndex, shifted);
			ci.cancel();
		} catch (Throwable e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 车厢空间平移失败，按原样绘制", e);
		}
	}

	private String packOfCar(int carIndex) {
		try {
			final int[] cars = myCars;
			final int globalCar = cars != null && carIndex >= 0 && carIndex < cars.length ? cars[carIndex] : carIndex;
			return NativeTrainLoader.packOf(vehicleExtension.vehicleExtraData.immutableVehicleCars.get(globalCar).getVehicleId());
		} catch (Throwable ignored) {
			return null;
		}
	}
}
