package com.xm.mtr3compat.client;

import com.lx862.mtrscripting.core.primitive.ParsedScript;
import com.lx862.mtrscripting.core.primitive.ScriptInstance;
import com.lx862.mtrscripting.lib.org.mozilla.javascript.Context;
import com.lx862.mtrscripting.lib.org.mozilla.javascript.Function;
import com.lx862.mtrscripting.lib.org.mozilla.javascript.Scriptable;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 补回列车级脚本入口：旧脚本宿主只收集 create/render/dispose 三个函数，
 * 定义 createTrain/renderTrain/disposeTrain 的脚本从未被执行。
 * 这里把这三个函数追加进同一个调用列表——宿主本就是以「整列车」为参数调用（wrapper 为列车对象），
 * 语义与列车级入口一致。
 */
public final class TrainScriptSupport {

	private static final Set<ParsedScript> PROCESSED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
	private static final String[][] ENTRIES = {
			{"createTrain", "createFunctions"},
			{"renderTrain", "renderFunctions"},
			{"disposeTrain", "disposeFunctions"}
	};

	private TrainScriptSupport() {
	}

	public static void ensureTrainEntries(ScriptInstance<?> instance) {
		if (instance == null) {
			return;
		}
		final ParsedScript script = instance.getScript();
		if (script == null || !PROCESSED.add(script)) {
			return;
		}
		try {
			final Object scopeObject = field(script.getClass(), "scope").get(script);
			final Scriptable scope = (Scriptable) scopeObject;
			final Context context = Context.enter();
			try {
				for (final String[] entry : ENTRIES) {
					final Object value = scope.get(entry[0], scope);
					if (value instanceof Function) {
						@SuppressWarnings("unchecked") final List<Function> functions = (List<Function>) field(script.getClass(), entry[1]).get(script);
						if (functions != null && !functions.contains(value)) {
							functions.add((Function) value);
							Mtr3CompatClient.LOGGER.info("[mtr3compat] 启用列车级脚本入口: {}", entry[0]);
						}
					}
				}
			} finally {
				Context.exit();
			}
		} catch (Throwable throwable) {
			PROCESSED.remove(script);
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 列车级脚本入口注入失败", throwable);
		}
	}

	private static Field field(Class<?> type, String name) throws NoSuchFieldException {
		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			try {
				final Field field = current.getDeclaredField(name);
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException ignored) {
				// 继续向父类查找
			}
		}
		throw new NoSuchFieldException(name);
	}
}
