package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleWrapper;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.Mtr3CompatConfig;
import com.xm.mtr3compat.client.NativeTrainLoader;
import org.mtr.mod.data.VehicleExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 脚本左右互换（按车型 id / 资源包名开启）：
 * <pre>
 * "script_lr_swap": { "gzmtr_a3_gm_lcd": true }
 * </pre>
 * 部分资源包的脚本按“模型 +x = 右侧”命名屏体，但本站 doorLeftOpen 对应模型 +x，
 * 于是两块屏显示的是对侧内容。这里让脚本看到的左右与 isReversed 整体镜像一次，
 * 使每块屏的线路图、开门提示都回到自己那一侧。未配置的车型完全不受影响。
 */
@Mixin(value = VehicleWrapper.class, remap = false)
public class VehicleWrapperSideSwapMixin {

	@Shadow
	@Final
	protected VehicleExtension vehicleExtension;

	@Shadow
	@Final
	public boolean[] doorLeftOpen;

	@Shadow
	@Final
	public boolean[] doorRightOpen;

	@Unique
	private boolean mtr3compat$sideSwap;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void mtr3compat$applySideSwap(CallbackInfo ci) {
		try {
			final String vehicleId = vehicleExtension.vehicleExtraData.immutableVehicleCars.get(0).getVehicleId();
			mtr3compat$sideSwap = Mtr3CompatConfig.scriptLrSwap(vehicleId, NativeTrainLoader.packOf(vehicleId));
			if (mtr3compat$sideSwap) {
				final int cars = Math.min(doorLeftOpen.length, doorRightOpen.length);
				for (int i = 0; i < cars; i++) {
					final boolean left = doorLeftOpen[i];
					doorLeftOpen[i] = doorRightOpen[i];
					doorRightOpen[i] = left;
				}
			}
		} catch (Throwable e) {
			mtr3compat$sideSwap = false;
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 脚本左右互换失败，保持原样", e);
		}
	}

	@Inject(method = "isReversed", at = @At("RETURN"), cancellable = true)
	private void mtr3compat$flipReversed(CallbackInfoReturnable<Boolean> cir) {
		if (mtr3compat$sideSwap) {
			cir.setReturnValue(!cir.getReturnValueZ());
		}
	}
}
