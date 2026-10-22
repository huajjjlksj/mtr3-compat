package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.NativeTrainLoader;
import org.mtr.core.data.VehicleCar;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.render.RenderVehicles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 临时诊断：幽灵车排查用。每 3 秒记录客户端车辆表、每节车的 vehicleId、
 * CustomResourceLoader 查表结果与 occlusion culling 的 rayTracing 结果。
 * 定位完成后删除本文件与 mixins.json 中的登记。
 */
@Mixin(RenderVehicles.class)
public class RenderVehiclesDebugMixin {

	private static long mtr3compat$lastLog = 0;

	@Inject(method = "render(JLorg/mtr/mapping/holder/Vector3d;)V", at = @At("HEAD"))
	private static void mtr3compat$logVehicles(long millisElapsed, Vector3d cameraShakeOffset, CallbackInfo ci) {
		final long now = System.currentTimeMillis();
		if (now - mtr3compat$lastLog < 3000) {
			return;
		}
		mtr3compat$lastLog = now;
		try {
			final ObjectArraySet<VehicleExtension> vehicles = MinecraftClientData.getInstance().vehicles;
			int nativeCount = 0;
			final StringBuilder summary = new StringBuilder("[mtr3compat] render 车辆总数=").append(vehicles.size());
			for (final VehicleExtension vehicle : vehicles) {
				final int carCount = vehicle.vehicleExtraData.immutableVehicleCars.size();
				boolean nativeVehicle = false;
				for (int i = 0; i < carCount; i++) {
					if (NativeTrainLoader.isNative(vehicle.vehicleExtraData.immutableVehicleCars.get(i).getVehicleId())) {
						nativeVehicle = true;
						break;
					}
				}
				if (!nativeVehicle) {
					continue;
				}
				nativeCount++;
				final StringBuilder sb = new StringBuilder("[mtr3compat] 原生车辆 ").append(vehicle.getName())
						.append(" mode=").append(vehicle.getTransportMode())
						.append(" cars=").append(carCount)
						.append(" onRoute=").append(vehicle.getIsOnRoute())
						.append(" reversed=").append(vehicle.getReversed())
						.append(" speed=").append(vehicle.getSpeed());
				final org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList<org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair<VehicleCar, org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList<org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair<org.mtr.core.tool.Vector, org.mtr.core.tool.Vector>>>> carsAndPositions = vehicle.getVehicleCarsAndPositions();
				if (!carsAndPositions.isEmpty()) {
					final org.mtr.core.tool.Vector position0 = carsAndPositions.get(0).right().get(0).left();
					sb.append(" pos0=").append(position0.x).append(',').append(position0.y).append(',').append(position0.z);
				} else {
					sb.append(" pos0=空");
				}
				for (int i = 0; i < carCount; i++) {
					final VehicleCar car = vehicle.vehicleExtraData.immutableVehicleCars.get(i);
					final boolean[] found = {false};
					CustomResourceLoader.getVehicleById(vehicle.getTransportMode(), car.getVehicleId(), pair -> found[0] = true);
					sb.append(" | ").append(i).append(':').append(car.getVehicleId())
							.append(" ray=").append(i < vehicle.persistentVehicleData.rayTracing.length && vehicle.persistentVehicleData.rayTracing[i])
							.append(" res=").append(found[0]);
				}
				Mtr3CompatClient.LOGGER.info(sb.toString());
			}
			summary.append(" 原生=").append(nativeCount);
			Mtr3CompatClient.LOGGER.info(summary.toString());
		} catch (Throwable e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] render 诊断失败", e);
		}
	}
}
