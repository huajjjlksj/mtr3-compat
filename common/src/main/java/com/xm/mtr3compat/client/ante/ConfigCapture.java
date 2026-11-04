package com.xm.mtr3compat.client.ante;

import com.google.gson.JsonObject;

/**
 * 由 ConfigCaptureMixin 在 ScriptResourceProvider.tryParseScript 调用点写入当前脚本条目的 config JsonObject，
 * AnteGlobals.bind 在 parseScriptEvent 监听（同一线程、同一调用栈内）读取并绑定为 CONFIG_INFO。
 */
public final class ConfigCapture {

	public static final ThreadLocal<JsonObject> LAST = new ThreadLocal<>();

	private ConfigCapture() {
	}
}
