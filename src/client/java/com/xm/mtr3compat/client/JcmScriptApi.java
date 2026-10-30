package com.xm.mtr3compat.client;

import com.lx862.mtrscripting.core.ScriptManager;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 运行时判定 JCM 的脚本解析事件签名。
 * 2.3.0-prerelease.1 起（含 beta.3 等后续版本）为 BiConsumer&lt;String, ParsedScript&gt;；
 * 2.3.0-beta.2 及更早为 TriConsumer&lt;String, Context, Scriptable&gt;。
 * 两者字段描述符相同，只能通过字段的泛型签名区分。
 * 判定为旧签名时关闭新版脚本补丁（解析事件监听、列车级脚本入口注入）。
 */
public final class JcmScriptApi {

	private static Boolean parsedScriptListener;
	private static Boolean trainScriptEntries;

	private JcmScriptApi() {
	}

	public static boolean supportsParsedScriptListener() {
		if (parsedScriptListener == null) {
			parsedScriptListener = mtr3compat$detect();
			if (!parsedScriptListener) {
				Mtr3CompatClient.LOGGER.info("[mtr3compat] 检测到旧版 JCM 脚本事件签名（TriConsumer），新版脚本补丁已关闭");
			}
		}
		return parsedScriptListener;
	}

	/**
	 * 是否需要本模组补入列车级脚本入口（createTrain / renderTrain / disposeTrain）。
	 * JCM 只按名字收集 create / render / dispose 三个函数（见 RhinoParsedScript.parse），
	 * 从不识别 createTrain 系列，因此只要新版解析事件可用就必须补入；
	 * 补入前会检查函数是否已在列表中，重复补入不会造成一帧多调。
	 */
	public static boolean needsTrainScriptEntries() {
		if (trainScriptEntries == null) {
			trainScriptEntries = supportsParsedScriptListener();
		}
		return trainScriptEntries;
	}

	private static boolean mtr3compat$detect() {
		try {
			final Field field = ScriptManager.class.getField("parseScriptEvent");
			final Type type = field.getGenericType();
			if (type instanceof ParameterizedType) {
				final Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
				return arguments.length > 0 && arguments[0].getTypeName().contains("BiConsumer");
			}
		} catch (Throwable ignored) {
			// 字段不存在或泛型信息丢失：按旧签名处理，避免注册出错误的监听器
		}
		return false;
	}
}
