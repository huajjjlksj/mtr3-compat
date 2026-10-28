package com.xm.mtr3compat.client.mixin;

import com.google.gson.JsonObject;
import com.lx862.mtrscripting.mod.resource.EyecandyResourceProvider;
import com.lx862.mtrscripting.mod.resource.LiftResourceProvider;
import com.xm.mtr3compat.client.ante.ConfigCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 捕获每脚本 config（eyecandy/lift：parseMtrResources 内各 1 处 tryParseScript 调用）。
 * 把 JsonObject 参数存入 {@link ConfigCapture#LAST}，供 parseScriptEvent 监听里的 AnteGlobals.bind 读取。
 * 仅透传调用，不改变行为。vehicle 的两处调用点见 VehicleConfigCaptureMixin。
 *
 * 使用 @ModifyArg 而非 @Redirect：@Redirect 会进入 MixinExtras 的 FactoryRedirectWrapper 处理路径，
 * 在 MixinExtras 0.4.x（Fabric Loader 0.16.x 自带）下会抛 ClassCastException 导致 JCM 类加载失败。
 */
@Mixin({EyecandyResourceProvider.class, LiftResourceProvider.class})
public class ConfigCaptureMixin {

	@ModifyArg(method = "parseMtrResources", at = @At(value = "INVOKE", target = "Lcom/lx862/mtrscripting/mod/resource/ScriptResourceProvider;tryParseScript(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/google/gson/JsonObject;ZZ)Lcom/lx862/mtrscripting/core/primitive/ParsedScript;"), index = 3)
	private JsonObject mtr3compat$captureConfig(JsonObject config) {
		ConfigCapture.LAST.set(config);
		return config;
	}
}
