package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.Mtr3CompatConfig;
import com.xm.mtr3compat.client.NativeTrainLoader;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.mtr.mod.resource.ModelPropertiesPart;
import org.mtr.mod.resource.PartType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 部分 MTR3 资源包的车内 LCD 完全由脚本（JCM 脚本自绘模型）提供，模型里的 display 部件
 * 只是 MTR3 时代的原生文字屏。MTR4 会把它们一并画出来，脚本 LCD 之外就多出原生文字
 * （德水表现为每节车前后端面各多一块屏）。
 * <p>
 * 按包开关：config 的 {@code disable_native_display} 里的包，跳过所有 DISPLAY 部件的渲染；
 * 未列出的包（原生文字屏才是其 LCD 的包）不受影响。
 * 每个被跳过的部件只打印一次，便于核对到底是哪些部件。
 */
@Mixin(ModelPropertiesPart.class)
public abstract class ModelPropertiesPartNativeDisplayMixin {

	private static final Set<String> LOGGED = new LinkedHashSet<>();

	@Shadow
	@Final
	protected PartType type;

	@Shadow
	@Final
	protected ObjectArrayList<String> names;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void mtr3compat$skipNativeDisplay(
			Identifier texture,
			StoredMatrixTransformations storedMatrixTransformations,
			VehicleExtension vehicle,
			int carNumber,
			int[] scrollingDisplayIndexTracker,
			int light,
			org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList openDoorways,
			boolean fromResourcePackCreator,
			CallbackInfo ci
	) {
		if (type != PartType.DISPLAY || vehicle == null) {
			return;
		}
		try {
			final String pack = packOf(vehicle, carNumber);
			if (!Mtr3CompatConfig.nativeDisplayDisabled(pack)) {
				return;
			}
			ci.cancel();
			final String key = pack + "|" + names;
			if (LOGGED.size() < 64 && LOGGED.add(key)) {
				Mtr3CompatClient.LOGGER.info("[mtr3compat] 跳过原生 display 部件 pack={} names={}", pack, names);
			}
		} catch (Throwable e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 原生 display 判定失败，按原样渲染", e);
		}
	}

	private static String packOf(VehicleExtension vehicle, int carNumber) {
		try {
			final var cars = vehicle.vehicleExtraData.immutableVehicleCars;
			final var car = cars.get(carNumber >= 0 && carNumber < cars.size() ? carNumber : 0);
			return NativeTrainLoader.packOf(car.getVehicleId());
		} catch (Throwable ignored) {
			return null;
		}
	}
}
