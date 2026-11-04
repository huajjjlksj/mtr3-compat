package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.core.util.Matrices;
import com.lx862.mtrscripting.core.util.model.ModelJS;
import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleScriptContext;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.Mtr3CompatConfig;
import com.xm.mtr3compat.client.NativeTrainLoader;
import org.mtr.mod.data.VehicleExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * “车厢空间”脚本绘制的平移校正。
 * <p>
 * 配置项 script_car_offsets 按资源包名给出 x/y/z 平移量（缺省轴为 0），例如：
 * <pre>
 * "script_car_offsets": { "包名": { "z": -2.5 } }
 * </pre>
 * 只对 matrices == null（脚本直接在车厢坐标系绘制）生效；脚本自带矩阵时保持原样。
 * 配置文件改动后自动热重载，无需刷新资源包。
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
			final float[] offset = Mtr3CompatConfig.scriptCarOffset(NativeTrainLoader.packOf(vehicleIdOf(carIndex)));
			if (offset == null) {
				return;
			}
			final Matrices shifted = new Matrices();
			shifted.translate(offset[0], offset[1], offset[2]);
			((VehicleScriptContext) (Object) this).drawCarModel(model, carIndex, shifted);
			ci.cancel();
		} catch (Throwable e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 车厢空间平移失败，按原样绘制", e);
		}
	}

	private String vehicleIdOf(int carIndex) {
		try {
			final int[] cars = myCars;
			final int globalCar = cars != null && carIndex >= 0 && carIndex < cars.length ? cars[carIndex] : carIndex;
			return vehicleExtension.vehicleExtraData.immutableVehicleCars.get(globalCar).getVehicleId();
		} catch (Throwable ignored) {
			return null;
		}
	}
}
