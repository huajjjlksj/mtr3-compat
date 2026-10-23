package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.core.ScriptManager;
import com.lx862.mtrscripting.core.primitive.ParsedScript;
import com.lx862.mtrscripting.core.primitive.ScriptContent;
import com.lx862.mtrscripting.lib.org.mozilla.javascript.Context;
import com.lx862.mtrscripting.lib.org.mozilla.javascript.Function;
import com.lx862.mtrscripting.lib.org.mozilla.javascript.Scriptable;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * JCM 的 ParsedScript 只收集 create / render / dispose 三个入口，NTE 的列车级 LCD 脚本
 * （createTrain / renderTrain / disposeTrain，如西安包 xa_metro_lcd/.../main_xa15.js）
 * 因此从未被执行：既不显示、也不报错。这里按 JCM 自身 tryAndAddFunction 的规则
 * （取 scope 顶层函数 -> 加入列表 -> 从 scope 删除）补上三个入口。
 * 构造期当前线程没有 Rhino Context，必须先 Context.enter() 才能操作 scope。
 */
@Mixin(ParsedScript.class)
public class ParsedScriptTrainEntryMixin {

	@Shadow
	@Final
	private Scriptable scope;

	@Shadow
	@Final
	private List<Function> createFunctions;

	@Shadow
	@Final
	private List<Function> renderFunctions;

	@Shadow
	@Final
	private List<Function> disposeFunctions;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void mtr3compat$addTrainEntries(ScriptManager scriptManager, String displayName, String script, List<ScriptContent> scriptContents, boolean log, CallbackInfo ci) {
		if (scope == null) {
			return;
		}
		final Context context = Context.enter();
		try {
			final boolean added = addIfPresent("createTrain", createFunctions)
					| addIfPresent("renderTrain", renderFunctions)
					| addIfPresent("disposeTrain", disposeFunctions);
			if (added) {
				Mtr3CompatClient.LOGGER.info("[mtr3compat] 注册列车级脚本入口: " + displayName);
			}
		} catch (Throwable e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 注册列车级脚本入口失败: " + displayName, e);
		} finally {
			Context.exit();
		}
	}

	private boolean addIfPresent(String name, List<Function> functions) {
		final Object value = scope.get(name, scope);
		if (!(value instanceof Function)) {
			return false;
		}
		functions.add((Function) value);
		scope.delete(name);
		return true;
	}
}
