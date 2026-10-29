package com.xm.mtr3compat.client;

import com.lx862.mtrscripting.core.ScriptManager;
import net.fabricmc.loader.api.FabricLoader;

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
	 * 2.3.0-beta.* 原生就收集这三个函数，再注入会让同一函数在一帧内被调用多次；
	 * 2.3.0-prerelease 起移除原生支持，才需要补丁。版本按 mod 版本号判断，而不是按事件泛型签名。
	 */
	public static boolean needsTrainScriptEntries() {
		if (trainScriptEntries == null) {
			trainScriptEntries = mtr3compat$detectTrainScriptEntries();
			if (!trainScriptEntries) {
				Mtr3CompatClient.LOGGER.info("[mtr3compat] JCM {} 自带列车级脚本入口，跳过注入", jcmVersion());
			}
		}
		return trainScriptEntries;
	}

	private static boolean mtr3compat$detectTrainScriptEntries() {
		if (!supportsParsedScriptListener()) {
			// 旧签名（TriConsumer）拿不到 ParsedScript 实例，本来就无法补入
			return false;
		}
		return !jcmVersion().contains("-beta");
	}

	private static String jcmVersion() {
		try {
			return FabricLoader.getInstance().getModContainer("jsblock")
					.map(container -> container.getMetadata().getVersion().getFriendlyString())
					.orElse("");
		} catch (Throwable ignored) {
			// 拿不到版本时按新版处理，保持 prerelease 的功能可用
			return "";
		}
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
