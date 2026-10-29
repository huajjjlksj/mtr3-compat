package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.NativeRailLoader;
import com.xm.mtr3compat.client.NativeTrainLoader;
import org.mtr.core.data.TransportMode;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.resource.RailResource;
import org.mtr.mod.resource.VehicleResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CustomResourceLoader.class)
public class CustomResourceLoaderMixin {

	/** reload 前直接读磁盘收集 mtr3-compat 包的车型 id（此时资源包尚未确认启用状态）。 */
	@Inject(method = "reload()V", at = @At("HEAD"))
	private static void mtr3compat$scanNative(CallbackInfo ci) {
		NativeTrainLoader.scan();
		NativeRailLoader.scan();
	}

	/** legacy 转换注册后，以 MTR3 语义注册原生车型（registerVehicle 私有静态，经 @Invoker 桥接）与 NTE 轨道。 */
	@Inject(method = "reload()V", at = @At("TAIL"))
	private static void mtr3compat$registerNative(CallbackInfo ci) {
		NativeTrainLoader.register(vehicleResource -> mtr3compat$invokeRegisterVehicle(vehicleResource, false));
		NativeRailLoader.register((id, railResource) -> {
			mtr3compat$rails().add(railResource);
			mtr3compat$railsCache().put(id, railResource);
		}, id -> mtr3compat$railsCache().containsKey(id));
		final int[] trainCount = {0};
		CustomResourceLoader.iterateVehicles(TransportMode.TRAIN, vehicleResource -> trainCount[0]++);
		Mtr3CompatClient.LOGGER.info("[mtr3compat] 注册完成: TRAIN 车型={} 轨道样式={} 原生车型={}",
				trainCount[0], CustomResourceLoader.getRails().size(), NativeTrainLoader.countNative());
	}

	/** 抑制 mtr3-compat 包经 LegacyVehicleResource 生成的 4 变体注册。 */
	@Inject(method = "registerVehicle(Lorg/mtr/mod/resource/VehicleResource;Z)V", at = @At("HEAD"), cancellable = true)
	private static void mtr3compat$suppressVariants(VehicleResource vehicleResource, boolean fromResourcePackCreator, CallbackInfo ci) {
		if (NativeTrainLoader.shouldSuppress(vehicleResource.getId())) {
			ci.cancel();
		}
	}

	@Invoker("registerVehicle")
	private static void mtr3compat$invokeRegisterVehicle(VehicleResource vehicleResource, boolean fromResourcePackCreator) {
		throw new RuntimeException("mtr3compat: @Invoker bridge for CustomResourceLoader.registerVehicle should not be called directly");
	}

	@Accessor("RAILS")
	private static ObjectArrayList<RailResource> mtr3compat$rails() {
		throw new RuntimeException("mtr3compat: @Accessor bridge for CustomResourceLoader.RAILS should not be called directly");
	}

	@Accessor("RAILS_CACHE")
	private static Object2ObjectAVLTreeMap<String, RailResource> mtr3compat$railsCache() {
		throw new RuntimeException("mtr3compat: @Accessor bridge for CustomResourceLoader.RAILS_CACHE should not be called directly");
	}
}
