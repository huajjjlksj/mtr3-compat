package com.xm.mtr3compat.client;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * config/mtr3-compat-tweak.txt：游戏运行中可随时编辑的临时调校值，用于对准脚本“车厢空间”绘制的平移量。
 * 每行一条，格式 包名关键字=格数（# 开头为注释），例如：
 * <pre>
 * 某资源包=-2.5
 * </pre>
 * 与 car_space_z_shift 相加生效；调到满意后把值填进 mtr3-compat.json 的 car_space_z_shift 即可。
 */
public final class Mtr3CompatTweak {

	private static final long RELOAD_INTERVAL_MS = 1000;
	private static final Map<String, Double> VALUES = new LinkedHashMap<>();
	private static long lastReload;

	private Mtr3CompatTweak() {
	}

	/** 该包的关键字匹配调校值；未命中为 0。脚本线程会调用，故内部限频读取。 */
	public static double shiftFor(String packName) {
		if (packName == null) {
			return 0;
		}
		reloadIfStale();
		final String key = Mtr3CompatConfig.normalize(packName).toLowerCase(Locale.ENGLISH);
		final Double exact = VALUES.get(key);
		if (exact != null) {
			return exact;
		}
		for (final Map.Entry<String, Double> entry : VALUES.entrySet()) {
			if (key.contains(entry.getKey())) {
				return entry.getValue();
			}
		}
		return 0;
	}

	private static synchronized void reloadIfStale() {
		final long now = System.currentTimeMillis();
		if (now - lastReload < RELOAD_INTERVAL_MS) {
			return;
		}
		lastReload = now;
		try {
			final Path path = FabricLoader.getInstance().getConfigDir().resolve("mtr3-compat-tweak.txt");
			if (!Files.isRegularFile(path)) {
				return;
			}
			VALUES.clear();
			for (final String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
				final String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) {
					continue;
				}
				final int split = trimmed.lastIndexOf('=');
				if (split <= 0) {
					continue;
				}
				try {
					VALUES.put(trimmed.substring(0, split).trim().toLowerCase(Locale.ENGLISH),
							Double.parseDouble(trimmed.substring(split + 1).trim()));
				} catch (NumberFormatException ignored) {
				}
			}
		} catch (Exception e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 读取调校文件失败", e);
		}
	}
}
