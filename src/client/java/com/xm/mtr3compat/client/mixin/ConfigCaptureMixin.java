package com.xm.mtr3compat.client.mixin;

import com.google.gson.JsonObject;
import com.lx862.mtrscripting.core.primitive.ParsedScript;
import com.lx862.mtrscripting.mod.resource.EyecandyResourceProvider;
import com.lx862.mtrscripting.mod.resource.LiftResourceProvider;
import com.lx862.mtrscripting.mod.resource.ScriptResourceProvider;
import com.xm.mtr3compat.client.ante.ConfigCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 捕获每脚本 config（eyecandy/lift：parseMtrResources 内各 1 处 tryParseScript 调用）。
 * 把 JsonObject 参数存入 {@link ConfigCapture#LAST}，供 parseScriptEvent 监听里的 AnteGlobals.bind 读取。
 * 仅透传调用，不改变行为。vehicle 的两处调用点见 VehicleConfigCaptureMixin。
 */
@Mixin({EyecandyResourceProvider.class, LiftResourceProvider.class})
public class ConfigCaptureMixin {

	@Redirect(method = "parseMtrResources", at = @At(value = "INVOKE", target = "Lcom/lx862/mtrscripting/mod/resource/ScriptResourceProvider;tryParseScript(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/google/gson/JsonObject;ZZ)Lcom/lx862/mtrscripting/core/primitive/ParsedScript;"))
	private ParsedScript mtr3compat$captureConfig(String name, String path, String content, JsonObject config, boolean legacy, boolean debug) {
		ConfigCapture.LAST.set(config);
		return ScriptResourceProvider.tryParseScript(name, path, content, config, legacy, debug);
	}
}
