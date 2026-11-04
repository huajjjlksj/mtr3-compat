package com.xm.mtr3compat.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JCM 的 ClassShutter 默认只允许白名单包；本兼容层返回给脚本的包装对象
 * （如 {@link com.xm.mtr3compat.client.jcm.LegacyModelCluster.VertArrays}）
 * 会被判定为 prohibited，导致整段脚本解析失败。
 *
 * 之前只在“勾选接管”的包（mtr3_packs）解析前才放开 com.xm.mtr3compat.*，
 * 原生加载的资源包走不到那条路径。这里改为对兼容层自身包无条件放行。
 */
@Mixin(targets = "com.lx862.mtrscripting.core.api.MTRClassShutter", remap = false)
public class ClassShutterCompatMixin {

	private static final String COMPAT_PREFIX = "com.xm.mtr3compat.";

	@Inject(method = "visibleToScripts", at = @At("HEAD"), cancellable = true, require = 0)
	private void mtr3compat$allowCompatClasses(String fullClassName, CallbackInfoReturnable<Boolean> cir) {
		if (fullClassName != null && fullClassName.startsWith(COMPAT_PREFIX)) {
			cir.setReturnValue(true);
		}
	}
}
