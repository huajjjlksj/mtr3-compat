package com.xm.mtr3compat.client.mixin;

import org.mtr.core.generated.data.VehicleExtraDataSchema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取服务端下发的原始 thisRouteId（不经过 {@link VehicleExtraDataRouteMixin} 的回填）。
 */
@Mixin(value = VehicleExtraDataSchema.class, remap = false)
public interface VehicleExtraDataRouteAccessor {

	@Accessor("thisRouteId")
	long mtr3compat$getRawThisRouteId();
}
