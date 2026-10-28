package com.xm.mtr3compat.client.mixin;

import com.google.gson.JsonObject;
import com.lx862.mtrscripting.mod.resource.VehicleResourceProvider;
import com.xm.mtr3compat.client.ante.ConfigCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 捕获每脚本 config（vehicle：parseMtrResources 内 2 处 tryParseScript 调用，字节码偏移 211/729）。
 * 语义同 {@link ConfigCaptureMixin}。
 *
 * 使用 @ModifyArg 而非 @Redirect：@Redirect 会进入 MixinExtras 的 FactoryRedirectWrapper 处理路径，
 * 在 MixinExtras 0.4.x（Fabric Loader 0.16.x 自带）下会抛 ClassCastException 导致 JCM 类加载失败。
 */
@Mixin(VehicleResourceProvider.class)
public class VehicleConfigCaptureMixin {

	@ModifyArg(method = "parseMtrResources", at = @At(value = "INVOKE", target = "Lcom/lx862/mtrscripting/mod/resource/ScriptResourceProvider;tryParseScript(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/google/gson/JsonObject;ZZ)Lcom/lx862/mtrscripting/core/primitive/ParsedScript;", ordinal = 0), index = 3)
	private JsonObject mtr3compat$captureConfig0(JsonObject config) {
		ConfigCapture.LAST.set(config);
		return config;
	}

	@ModifyArg(method = "parseMtrResources", at = @At(value = "INVOKE", target = "Lcom/lx862/mtrscripting/mod/resource/ScriptResourceProvider;tryParseScript(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/google/gson/JsonObject;ZZ)Lcom/lx862/mtrscripting/core/primitive/ParsedScript;", ordinal = 1), index = 3)
	private JsonObject mtr3compat$captureConfig1(JsonObject config) {
		ConfigCapture.LAST.set(config);
		return config;
	}
}
