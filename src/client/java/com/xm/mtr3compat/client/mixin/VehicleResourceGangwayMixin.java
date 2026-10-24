package com.xm.mtr3compat.client.mixin;

import org.mtr.mod.resource.VehicleResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NTE 时代资源包没有 hasGangway1/hasGangway2 字段（只有 gangway_connection_id，且常为空），
 * MTR 4 会把这两个布尔默认成 false，于是不会在两节车厢之间生成可站立的过道框，
 * 玩家在车里被限制在单节车厢地板内（表现为风挡过不去）。
 * 这里对没有声明 barrier 的列车补开过道，恢复 NTE 下可自由穿行车厢的行为。
 */
@Mixin(VehicleResource.class)
public abstract class VehicleResourceGangwayMixin {

	@Shadow
	public abstract boolean hasBarrier1();

	@Shadow
	public abstract boolean hasBarrier2();

	@Inject(method = "hasGangway1", at = @At("TAIL"), cancellable = true)
	private void mtr3compat$enableGangway1(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && !hasBarrier1()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "hasGangway2", at = @At("TAIL"), cancellable = true)
	private void mtr3compat$enableGangway2(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && !hasBarrier2()) {
			cir.setReturnValue(true);
		}
	}
}
