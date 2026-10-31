package com.xm.mtr3compat.client.mixin;

import com.google.gson.JsonObject;
import com.lx862.mtrscripting.core.primitive.ParsedScript;
import com.lx862.mtrscripting.mod.resource.ScriptResourceProvider;
import com.lx862.mtrscripting.mod.resource.VehicleResourceProvider;
import com.xm.mtr3compat.client.JcmScriptApi;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.ante.ConfigCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 捕获每脚本 config（vehicle：parseMtrResources 内 2 处 tryParseScript 调用，字节码偏移 211/729）。
 * 语义同 {@link ConfigCaptureMixin}。
 * 另外在解析前去掉 script_files 数组内的重复条目：Rhino 在同一作用域逐个执行脚本，
 * 同一文件出现两次会触发 const 重声明、整段脚本解析失败。只在 JCM 新版脚本事件签名下启用，
 * 旧版（beta.2 及更早）保持原样。
 */
@Mixin(VehicleResourceProvider.class)
public class VehicleConfigCaptureMixin {

	@Redirect(method = "parseMtrResources", at = @At(value = "INVOKE", target = "Lcom/lx862/mtrscripting/mod/resource/ScriptResourceProvider;tryParseScript(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/google/gson/JsonObject;ZZ)Lcom/lx862/mtrscripting/core/primitive/ParsedScript;", ordinal = 0))
	private ParsedScript mtr3compat$captureConfig0(String name, String path, String content, JsonObject config, boolean legacy, boolean debug) {
		ConfigCapture.LAST.set(config);
		mtr3compat$dedupeScriptFiles(name, config);
		return ScriptResourceProvider.tryParseScript(name, path, content, config, legacy, debug);
	}

	@Redirect(method = "parseMtrResources", at = @At(value = "INVOKE", target = "Lcom/lx862/mtrscripting/mod/resource/ScriptResourceProvider;tryParseScript(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/google/gson/JsonObject;ZZ)Lcom/lx862/mtrscripting/core/primitive/ParsedScript;", ordinal = 1))
	private ParsedScript mtr3compat$captureConfig1(String name, String path, String content, JsonObject config, boolean legacy, boolean debug) {
		ConfigCapture.LAST.set(config);
		mtr3compat$dedupeScriptFiles(name, config);
		return ScriptResourceProvider.tryParseScript(name, path, content, config, legacy, debug);
	}

	private static void mtr3compat$dedupeScriptFiles(String name, JsonObject config) {
		if (!JcmScriptApi.supportsParsedScriptListener()) {
			return;
		}
		final int removed = JcmScriptApi.dedupeScriptFiles(config);
		if (removed > 0) {
			Mtr3CompatClient.LOGGER.info("[mtr3compat] 脚本去重 {}: 移除 script_files 重复条目 {} 条", name, removed);
		}
	}
}
