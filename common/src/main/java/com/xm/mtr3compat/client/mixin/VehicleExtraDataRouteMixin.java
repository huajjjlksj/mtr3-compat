package com.xm.mtr3compat.client.mixin;

import org.mtr.core.data.VehicleExtraData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MTR4 的车厂站序表把每条线路的第一站台记成“无线路”（route 为 null），
 * 车停在这种换线/折返站台时 thisRouteId 为 0，下游只能回退到上一条线路（正线），
 * 表现就是折返后仍按正线的线路名/站表显示。
 * <p>
 * MTR3 在同一位置返回的是该站台所属线路（RailwayData 跨线路时 sum--，共享站台归上一条线路，
 * 独立站台归新线路）。这里按同样口径：原始值为 0 且确实停在站台上时，用下一条线路回填。
 */
@Mixin(value = VehicleExtraData.class, remap = false)
public class VehicleExtraDataRouteMixin {

	@Inject(method = "getThisRouteId", at = @At("HEAD"), cancellable = true)
	private void mtr3compat$thisRouteId(CallbackInfoReturnable<Long> cir) {
		final long raw = ((VehicleExtraDataRouteAccessor) (Object) this).mtr3compat$getRawThisRouteId();
		if (raw != 0L) {
			return;
		}

		final VehicleExtraData self = (VehicleExtraData) (Object) this;
		if (self.getThisPlatformId() == 0L) {
			return;
		}

		final long next = self.getNextRouteId();
		cir.setReturnValue(next != 0L ? next : self.getPreviousRouteId());
	}
}
