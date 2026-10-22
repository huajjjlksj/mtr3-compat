package com.xm.mtr3compat.client;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * JCM（mtrscripting）的车辆脚本（custom_trains[].script_files）按 MTR4 legacy 变体 id 登记：
 * VehicleResourceProvider.parseMtrResources 里把脚本登记为
 *   mtr_custom_train_(id) / (id)_cab_1 / _cab_2 / _cab_3 / _trailer
 * 而 mtr3-compat 以原 id 注册原生车辆，导致 getVehicleScriptEntryId(原id) 查表落空、脚本不执行
 * （典型症状：CR300AF 由 wheel.js 绘制的车轮不渲染）。
 *
 * 本类在渲染原生车辆前，把原 id 别名到已登记的 legacy 条目，不改动车辆 id 与 JCM 代码。
 * 通过反射访问 JCM 内部字段，JCM 不存在或字段变更时静默跳过。
 */
public final class JcmScriptAlias {

	private static final String MANAGER_CLASS = "com.lx862.mtrscripting.mod.resource.MtrScriptingResourceManager";
	private static final String LEGACY_PREFIX = "mtr_custom_train_";

	private static boolean applied = false;
	private static boolean unsupported = false;

	private JcmScriptAlias() {
	}

	/** 每次资源重载（NativeTrainLoader.scan）后需要重新登记，因为 JCM 会清空映射表。 */
	public static void invalidate() {
		applied = false;
	}

	@SuppressWarnings("unchecked")
	public static void applyOnce() {
		if (applied || unsupported) {
			return;
		}
		applied = true;
		try {
			final Class<?> managerClass = Class.forName(MANAGER_CLASS);
			final Object provider = managerClass.getField("vehicle").get(null);
			final Field idsField = provider.getClass().getDeclaredField("vehicleScriptIds");
			idsField.setAccessible(true);
			final Map<String, String> ids = (Map<String, String>) idsField.get(provider);
			if (ids == null) {
				return;
			}
			for (final String id : NativeTrainLoader.nativeIds()) {
				if (ids.containsKey(id)) {
					continue;
				}
				String target = ids.get(LEGACY_PREFIX + id + "_trailer");
				if (target == null) {
					target = ids.get(LEGACY_PREFIX + id);
				}
				if (target == null) {
					target = ids.get(LEGACY_PREFIX + id + "_cab_1");
				}
				if (target != null) {
					ids.put(id, target);
					Mtr3CompatClient.LOGGER.info("[mtr3compat] 车辆脚本别名 {} -> {}", id, target);
				}
			}
		} catch (ClassNotFoundException e) {
			unsupported = true;
		} catch (Throwable e) {
			unsupported = true;
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 无法登记 JCM 车辆脚本别名（脚本部件可能不渲染）", e);
		}
	}
}
