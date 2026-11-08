package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.NativeVehicleRenderer;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.mtr.mod.resource.VehicleResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原生（MTR3 模式）车型的静态部件由 {@link NativeVehicleRenderer} 合批渲染；
 * 原版 queue 仍会执行，用于渲染每节车模型里保留的门部件。
 */
@Mixin(value = VehicleResource.class, remap = false)
public abstract class NativeVehicleRenderMixin {

	@Inject(method = "queue(Lorg/mtr/mod/render/StoredMatrixTransformations;Lorg/mtr/mod/data/VehicleExtension;IIIZ)V", at = @At("HEAD"))
	private void mtr3compat$queueNativeBatches(StoredMatrixTransformations storedMatrixTransformations, VehicleExtension vehicle, int carNumber, int totalCars, int light, boolean noOpenDoorways, CallbackInfo ci) {
		NativeVehicleRenderer.queue((VehicleResource) (Object) this, storedMatrixTransformations, vehicle, carNumber, totalCars, light, noOpenDoorways);
	}
}
