package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.jcm.Mtr3RoutePlatformIds;
import org.mtr.core.data.SimplifiedRoute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * JCM 的 {@code Stop.route} 是 MTR4 的 SimplifiedRoute（只有 getPlatforms()），
 * 而 MTR3 脚本按 {@code route.platformIds} 读取站台（gzmtr mtr_util.js 等）。
 * 这里补同名公有字段，值为惰性委托列表。
 */
@Mixin(SimplifiedRoute.class)
public class SimplifiedRoutePlatformIdsMixin {

	public Mtr3RoutePlatformIds platformIds;

	@Inject(method = "<init>*", at = @At("TAIL"), require = 0)
	private void mtr3compat$initPlatformIds(CallbackInfo ci) {
		platformIds = new Mtr3RoutePlatformIds(this);
	}
}
