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
 * 复刻 ANTE 对“车厢空间”脚本绘制的坐标系语义（验证阶段）。
 * <p>
 * NTE：RawMeshBuilder 写入顶点时 y、z 已取负，渲染侧 SowcerModelAgent 再乘 Rx(π) 抵消，
 * 最终与脚本坐标一致。
 * JCM：RawMeshBuilderJS#vertex 同样写成 (x, -y, -z)，但 matrices == null 时走 RenderDrawCall
 * 的空变换分支，只补 Rx(180)·I·Rx(-180) = I，抵消丢失。
 * 这里对 MTR3 模式拦截的包改传一个显式 rotateXDegrees(180) 的 Matrices，
 * 使 JCM 组合出 Rx(180)，等价于 NTE 的 Rx(π)。不再读取任何位移配置。
 */
@Mixin(value = VehicleScriptContext.class, remap = false)
public class VehicleScriptCarSpaceShiftMixin {

	@Shadow
	private VehicleExtension vehicleExtension;

	@Shadow
	private int[] myCars;

	private static boolean logged = false;

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
			if (!logged) {
				logged = true;
				Mtr3CompatClient.LOGGER.info("[mtr3compat] drawCarModel car={} pack={} intercepted={} shift={}",
						carIndex, pack, pack != null && Mtr3CompatConfig.isIntercepted(pack),
						pack == null ? 0 : Mtr3CompatConfig.carSpaceShift(pack));
			}
			if (pack == null) {
				return;
			}
			final double shift = Mtr3CompatConfig.carSpaceShift(pack);
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
