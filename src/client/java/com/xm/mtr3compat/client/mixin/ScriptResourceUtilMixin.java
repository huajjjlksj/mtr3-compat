package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.core.util.ScriptResourceUtil;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NTE 包常用 Resources.getMTRVersion().includes("4.0") 主动拦截 MTR4（画蓝屏 return）。
 * 仅在勾选包脚本解析窗口（Mtr3CompatClient.SPOOF_MTR_VERSION）内把版本伪装为 MTR 3.2.2，
 * 窗口外（未勾选包、JCM 原生脚本）返回真实版本，不影响其他脚本的版本分支。
 */
@Mixin(ScriptResourceUtil.class)
public class ScriptResourceUtilMixin {

	@Inject(method = "getMTRVersion()Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
	private static void mtr3compat$spoofVersion(CallbackInfoReturnable<String> cir) {
		if (Mtr3CompatClient.SPOOF_MTR_VERSION) {
			cir.setReturnValue("3.2.2");
		}
	}
}
