package com.xm.mtr3compat.client.mixin;

import com.google.gson.JsonObject;
import com.lx862.mtrscripting.core.primitive.ParsedScript;
import com.lx862.mtrscripting.mod.resource.ScriptResourceProvider;
import com.lx862.mtrscripting.mod.resource.VehicleResourceProvider;
import com.xm.mtr3compat.client.ante.ConfigCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 捕获每脚本 config（vehicle：parseMtrResources 内 2 处 tryParseScript 调用，字节码偏移 211/729）。
 * 语义同 {@link ConfigCaptureMixin}。
 */
@Mixin(VehicleResourceProvider.class)
public class VehicleConfigCaptureMixin {

	@Redirect(method = "parseMtrResources", at = @At(value = "INVOKE", target = "Lcom/lx862/mtrscripting/mod/resource/ScriptResourceProvider;tryParseScript(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/google/gson/JsonObject;ZZ)Lcom/lx862/mtrscripting/core/primitive/ParsedScript;", ordinal = 0))
	private ParsedScript mtr3compat$captureConfig0(String name, String path, String content, JsonObject config, boolean legacy, boolean debug) {
		ConfigCapture.LAST.set(config);
		return ScriptResourceProvider.tryParseScript(name, path, content, config, legacy, debug);
	}

	@Redirect(method = "parseMtrResources", at = @At(value = "INVOKE", target = "Lcom/lx862/mtrscripting/mod/resource/ScriptResourceProvider;tryParseScript(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/google/gson/JsonObject;ZZ)Lcom/lx862/mtrscripting/core/primitive/ParsedScript;", ordinal = 1))
	private ParsedScript mtr3compat$captureConfig1(String name, String path, String content, JsonObject config, boolean legacy, boolean debug) {
		ConfigCapture.LAST.set(config);
		return ScriptResourceProvider.tryParseScript(name, path, content, config, legacy, debug);
	}
}
