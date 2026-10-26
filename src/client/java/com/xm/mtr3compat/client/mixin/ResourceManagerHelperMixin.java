package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.Mtr3CompatClient;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.ResourceManagerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脚本诊断：MTR4 的 CustomResourceLoader.reload 与 JCM 的 parseCustom 都经
 * ResourceManagerHelper.readAllResources 枚举启用包中的 mtr_custom_resources.json。
 * 这里不替换原实现（自行遍历资源包会漏掉模组自带资源，导致内置车型与默认轨道样式丢失）。
 */
@Mixin(ResourceManagerHelper.class)
public class ResourceManagerHelperMixin {

	/** 调试用：把 catch (x) { 改写为 catch (x) { mtr3compatLog(x);，并在脚本尾部追加去重打印助手，暴露被空 catch 吞掉的异常。 */
	private static final Pattern CATCH_OPEN = Pattern.compile("catch\\s*\\(\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\)\\s*\\{");
	private static final String CATCH_HELPER = "\n;var __mtr3compatSeen={};function mtr3compatLog(err){try{var k=\"\"+err;if(!__mtr3compatSeen[k]){__mtr3compatSeen[k]=1;print(\"[mtr3compat] caught: \"+k+\" @ \"+(err&&err.fileName?err.fileName:\"?\")+\":\"+(err&&err.lineNumber?err.lineNumber:\"?\"));}}catch(e2){}}\n";

	@Inject(method = "readResource(Lorg/mtr/mapping/holder/Identifier;)Ljava/lang/String;", at = @At("RETURN"), cancellable = true)
	private static void mtr3compat$instrumentCatch(Identifier identifier, CallbackInfoReturnable<String> cir) {
		if (!Mtr3CompatClient.DEBUG_INSTRUMENT_CATCH || !Mtr3CompatClient.SPOOF_MTR_VERSION) {
			return;
		}
		final String text = cir.getReturnValue();
		if (text == null || text.isEmpty() || !identifier.getPath().endsWith(".js") || !text.contains("catch")) {
			return;
		}
		final Matcher matcher = CATCH_OPEN.matcher(text);
		final StringBuilder result = new StringBuilder();
		int patched = 0;
		while (matcher.find()) {
			matcher.appendReplacement(result, "catch (" + matcher.group(1) + ") { mtr3compatLog(" + matcher.group(1) + ");");
			patched++;
		}
		if (patched == 0) {
			return;
		}
		matcher.appendTail(result);
		result.append(CATCH_HELPER);
		Mtr3CompatClient.LOGGER.info("[mtr3compat] catch 注入: {} 处 ({})", patched, identifier.getPath());
		cir.setReturnValue(result.toString());
	}
}
