package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.core.util.Matrices;
import com.lx862.mtrscripting.core.util.model.ModelJS;
import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleScriptContext;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.NativeTrainLoader;
import org.mtr.mod.data.VehicleExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * “车厢空间”脚本绘制的沿车长补偿。
 * <p>
 * MTR3 资源包的车门部件按半个门距成对建模，脚本绘制的门载贴图（如车门 LCD）相对原生车门部件
 * 整体差半个门距。补偿量由 {@link NativeTrainLoader#scriptZShiftOf(String)} 从包自身的车门
 * positions 推导，不读取任何配置、也不涉及具体包名。
 * <p>
 * 只有 matrices == null（脚本以车厢坐标系直接绘制）时才需要补偿；脚本自带矩阵时保持原样。
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
			final double shift = NativeTrainLoader.scriptZShiftOf(vehicleIdOf(carIndex));
			if (shift == 0) {
				return;
			}
			final Matrices shifted = new Matrices();
			shifted.translate(0, 0, (float) -shift);
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
