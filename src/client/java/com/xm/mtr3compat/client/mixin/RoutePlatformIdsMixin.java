package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.jcm.Mtr3RoutePlatformIds;
import org.mtr.core.data.Route;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MTR3 脚本按 {@code route.platformIds} 读取站台列表，MTR4 只有 getRoutePlatforms()。
 * 这里给 Route 补一个同名公有字段，值是惰性委托列表，脚本无需改动。
 */
@Mixin(Route.class)
public class RoutePlatformIdsMixin {

	public Mtr3RoutePlatformIds platformIds;

	@Inject(method = "<init>*", at = @At("TAIL"), require = 0)
	private void mtr3compat$initPlatformIds(CallbackInfo ci) {
		platformIds = new Mtr3RoutePlatformIds(this);
	}
}
