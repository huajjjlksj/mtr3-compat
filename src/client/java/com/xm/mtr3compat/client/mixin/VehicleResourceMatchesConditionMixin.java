package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.NativeTrainLoader;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.mtr.mod.resource.PartCondition;
import org.mtr.mod.resource.VehicleResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MTR3 的 MOVING_FORWARDS/BACKWARDS 只看行驶方向、不要求在运营线路上（试车台/机务段也渲染受电弓、头尾灯），
 * 而 MTR4 的 ON_ROUTE_FORWARDS/BACKWARDS 额外要求 getIsOnRoute()。
 * 本 mixin 在渲染 compat 原生车辆期间放宽为纯方向判定，与 MTR3+NTE 行为一致。
 */
@Mixin(VehicleResource.class)
public abstract class VehicleResourceMatchesConditionMixin {

	/** 当前正在渲染 mtr3-compat 原生车辆（客户端渲染线程单线程访问）。 */
	private static boolean mtr3compat$nativeRender = false;
	private static final java.util.Set<String> mtr3compat$queueLogs = new java.util.HashSet<>();

	@Invoker("getId")
	public abstract String mtr3compat$getId();

	@Inject(method = "queue(Lorg/mtr/mod/render/StoredMatrixTransformations;Lorg/mtr/mod/data/VehicleExtension;IIIZ)V", at = @At("HEAD"))
	private void mtr3compat$queueHead(StoredMatrixTransformations storedMatrixTransformations, VehicleExtension vehicle, int carNumber, int totalCars, int light, boolean noOpenDoorways, CallbackInfo ci) {
		mtr3compat$nativeRender = NativeTrainLoader.isNative(mtr3compat$getId());
		if (mtr3compat$nativeRender) {
			// 渲染发生在资源重载之后，此时 JCM 的脚本表已就绪，补登原 id 别名（脚本部件如车轮才会渲染）
			com.xm.mtr3compat.client.JcmScriptAlias.applyOnce();
			// 进世界首帧脚本可能早于客户端 tick 的 DATA_CACHE 重建执行，
			// 空表查 stationIdToConnectingStations 会抛 forEach of undefined 中断渲染（LCD 定格/不显示）。
			com.xm.mtr3compat.client.MtrClientDataCompat.tickEnsureData();
			final String logKey = mtr3compat$getId() + "#" + carNumber + "/" + totalCars;
			if (mtr3compat$queueLogs.add(logKey)) {
				com.xm.mtr3compat.client.Mtr3CompatClient.LOGGER.info("[mtr3compat] queue {} car={}/{} ray={} onRoute={} reversed={} doors={} cacheNull={}",
						mtr3compat$getId(), carNumber, totalCars,
						carNumber < vehicle.persistentVehicleData.rayTracing.length && vehicle.persistentVehicleData.rayTracing[carNumber],
						vehicle.getIsOnRoute(), vehicle.getReversed(), noOpenDoorways,
						getCachedVehicleResource(carNumber, totalCars, false) == null);
			}
		}
	}

	@Inject(method = "queueBogie(ILorg/mtr/mod/render/StoredMatrixTransformations;Lorg/mtr/mod/data/VehicleExtension;I)V", at = @At("HEAD"))
	private void mtr3compat$queueBogieHead(int bogieIndex, StoredMatrixTransformations storedMatrixTransformations, VehicleExtension vehicle, int light, CallbackInfo ci) {
		if (NativeTrainLoader.isNative(mtr3compat$getId())) {
			final String logKey = "bogie#" + mtr3compat$getId() + "#" + bogieIndex;
			if (mtr3compat$queueLogs.add(logKey)) {
				com.xm.mtr3compat.client.Mtr3CompatClient.LOGGER.info("[mtr3compat] queueBogie {} bogie={} cacheNull={}",
						mtr3compat$getId(), bogieIndex, getCachedVehicleResource(0, 1, false) == null);
			}
		}
	}

	@Shadow
	protected abstract org.mtr.mod.resource.VehicleResourceCache getCachedVehicleResource(int carNumber, int totalCars, boolean force);

	@Inject(method = "queue(Lorg/mtr/mod/render/StoredMatrixTransformations;Lorg/mtr/mod/data/VehicleExtension;IIIZ)V", at = @At("TAIL"))
	private void mtr3compat$queueTail(StoredMatrixTransformations storedMatrixTransformations, VehicleExtension vehicle, int carNumber, int totalCars, int light, boolean noOpenDoorways, CallbackInfo ci) {
		mtr3compat$nativeRender = false;
	}

	@Inject(method = "matchesCondition(Lorg/mtr/mod/data/VehicleExtension;Lorg/mtr/mod/resource/PartCondition;Z)Z", at = @At("HEAD"), cancellable = true)
	private static void mtr3compat$relaxDirectionConditions(VehicleExtension vehicle, PartCondition condition, boolean noOpenDoorways, CallbackInfoReturnable<Boolean> cir) {
		if (!mtr3compat$nativeRender) {
			return;
		}
		if (condition == PartCondition.ON_ROUTE_FORWARDS) {
			cir.setReturnValue(!vehicle.getReversed());
		} else if (condition == PartCondition.ON_ROUTE_BACKWARDS) {
			cir.setReturnValue(vehicle.getReversed());
		}
	}
}
