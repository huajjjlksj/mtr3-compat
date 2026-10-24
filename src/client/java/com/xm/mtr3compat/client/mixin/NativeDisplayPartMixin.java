package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.Mtr3CompatConfig;
import com.xm.mtr3compat.client.NativeTrainLoader;
import org.mtr.mapping.holder.Box;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.resource.ModelPropertiesPart;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.mtr.mod.resource.PartType;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectDoubleImmutablePair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 按配置 hide_native_display 关闭 MTR3 车型的原生 display 部件。
 * 这些 display 部件是 MTR3 时代的遗留（LCD 已改由脚本绘制），MTR4 仍会把它们画成一块块文字屏，
 * 表现为门上出现多余的屏幕。仅在对应资源包被配置进来时才跳过。
 */
@Mixin(ModelPropertiesPart.class)
public class NativeDisplayPartMixin {

	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
	private void mtr3compat$hideNativeDisplay(Identifier texture, StoredMatrixTransformations storedMatrixTransformations, VehicleExtension vehicle, int carNumber, int[] scrollingDisplayIndexTracker, int light, ObjectArrayList<ObjectDoubleImmutablePair<Box>> openDoorways, boolean fromResourcePackCreator, CallbackInfo ci) {
		try {
			if (((ModelPropertiesPartSchemaAccessor) this).mtr3compat$getPartType() != PartType.DISPLAY || vehicle == null) {
				return;
			}
			final String vehicleId = vehicle.vehicleExtraData.immutableVehicleCars.get(carNumber).getVehicleId();
			if (Mtr3CompatConfig.hideNativeDisplay(NativeTrainLoader.packOf(vehicleId))) {
				ci.cancel();
			}
		} catch (Throwable ignored) {
		}
	}
}
