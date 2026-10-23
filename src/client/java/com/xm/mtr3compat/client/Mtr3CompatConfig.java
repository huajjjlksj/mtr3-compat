package com.xm.mtr3compat.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * config/mtr3-compat.json：
 * <pre>
 * {
 *   "mtr3_packs": ["包名（不含 .zip）", ...],
 *   "car_space_z_shift": { "包名": -5.0 },
 *   "disable_native_display": ["包名（不含 .zip）", ...]
 * }
 * </pre>
 * mtr3_packs：被本 mod 拦截 MTR4/JCM 解析、改以 MTR3 模式加载的资源包。
 * car_space_z_shift：个别包的脚本“车厢空间”绘制需要沿车长平移的格数（不需要就别写）。
 * disable_native_display：LCD 完全由脚本自绘的包，关掉 MTR 原生 display 部件（见 ModelPropertiesPartNativeDisplayMixin）。
 */
public final class Mtr3CompatConfig {

	private static final Set<String> MTR3_PACKS = new LinkedHashSet<>();
	/** 包名 -> 车厢空间 z 平移（格）。仅由配置文件提供，mod 内不内置任何资源包名。 */
	private static final Map<String, Double> CAR_SPACE_SHIFT = new LinkedHashMap<>();
	/** 关闭 MTR 原生 display 部件的包名。仅由配置文件提供，mod 内不内置任何资源包名。 */
	private static final Set<String> NATIVE_DISPLAY_DISABLED = new LinkedHashSet<>();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private Mtr3CompatConfig() {
	}

	/** 去掉 .zip 后缀（大小写不敏感判断，保留原名）。 */
	public static String normalize(String packName) {
		return packName.toLowerCase(Locale.ENGLISH).endsWith(".zip") ? packName.substring(0, packName.length() - 4) : packName;
	}

	public static boolean isIntercepted(String packName) {
		return MTR3_PACKS.contains(normalize(packName));
	}

	public static boolean hasIntercepted() {
		return !MTR3_PACKS.isEmpty();
	}

	public static Set<String> getPacks() {
		return MTR3_PACKS;
	}

	public static void toggle(String packName) {
		final String key = normalize(packName);
		if (!MTR3_PACKS.remove(key)) {
			MTR3_PACKS.add(key);
		}
		save();
	}

	/** 该包脚本“车厢空间”绘制的 z 平移（格），只读取配置，缺省为 0。 */
	public static double carSpaceShift(String packName) {
		return packName == null ? 0 : CAR_SPACE_SHIFT.getOrDefault(normalize(packName), 0.0);
	}

	/** 该包是否要关掉 MTR 原生 display 部件（仅对拦截为 MTR3 模式的包生效）。 */
	public static boolean nativeDisplayDisabled(String packName) {
		return packName != null && isIntercepted(packName) && NATIVE_DISPLAY_DISABLED.contains(normalize(packName));
	}

	public static void load() {
		try {
			final Path path = file();
			if (!Files.isRegularFile(path)) {
				return;
			}
			final JsonElement root = JsonParser.parseString(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
				return;
			}
			final JsonObject object = root.getAsJsonObject();
			if (object.has("mtr3_packs")) {
				MTR3_PACKS.clear();
				for (final JsonElement element : object.getAsJsonArray("mtr3_packs")) {
					MTR3_PACKS.add(normalize(element.getAsString()));
				}
			}
			CAR_SPACE_SHIFT.clear();
			if (object.has("car_space_z_shift") && object.get("car_space_z_shift").isJsonObject()) {
				for (final Map.Entry<String, JsonElement> entry : object.getAsJsonObject("car_space_z_shift").entrySet()) {
					CAR_SPACE_SHIFT.put(normalize(entry.getKey()), entry.getValue().getAsDouble());
				}
			}
			NATIVE_DISPLAY_DISABLED.clear();
			if (object.has("disable_native_display") && object.get("disable_native_display").isJsonArray()) {
				for (final JsonElement element : object.getAsJsonArray("disable_native_display")) {
					NATIVE_DISPLAY_DISABLED.add(normalize(element.getAsString()));
				}
			}
		} catch (Exception e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 读取配置失败", e);
		}
	}

	public static void save() {
		try {
			final Path path = file();
			Files.createDirectories(path.getParent());
			final JsonObject root = new JsonObject();
			final JsonArray packs = new JsonArray();
			MTR3_PACKS.forEach(packs::add);
			root.add("mtr3_packs", packs);
			final JsonObject shifts = new JsonObject();
			CAR_SPACE_SHIFT.forEach(shifts::addProperty);
			root.add("car_space_z_shift", shifts);
			final JsonArray disabled = new JsonArray();
			NATIVE_DISPLAY_DISABLED.forEach(disabled::add);
			root.add("disable_native_display", disabled);
			Files.write(path, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 保存配置失败", e);
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("mtr3-compat.json");
	}
}
