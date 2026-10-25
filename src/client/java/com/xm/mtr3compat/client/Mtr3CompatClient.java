package com.xm.mtr3compat.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mtr3CompatClient implements ClientModInitializer {

	public static final String MOD_ID = "mtr3compat";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** MTR3 自动编组开关（车辆选择面板顶部按钮切换）；默认开启，与 MTR3 选择车型后自动按轨长铺满一致。 */
	public static boolean autoConsist = true;

	/**
	 * 勾选包脚本解析窗口内为 true：ScriptResourceUtilMixin 据此把 getMTRVersion() 伪装为 3.2.2，
	 * 并让 parseScriptEvent 监听把全局 MTRClientData 换为带 DATA_CACHE 的替身（MtrClientDataCompat）。
	 */
	public static volatile boolean SPOOF_MTR_VERSION = false;

	/** 调试用：重喂窗口内把脚本里的空 catch 替换为一次性 print，暴露被吞掉的脚本异常（诊断完改回 false）。 */
	public static final boolean DEBUG_INSTRUMENT_CATCH = true;

	@Override
	public void onInitializeClient() {
		Mtr3CompatConfig.load();
		LOGGER.info("[mtr3compat] MTR3 兼容层已加载（按包勾选 MTR3 模式）");
	}
}
