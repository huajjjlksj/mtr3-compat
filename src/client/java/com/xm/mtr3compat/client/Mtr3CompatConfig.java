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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 勾选状态持久化：config/mtr3-compat.json { "mtr3_packs": ["包名（不含 .zip）", ...] }。
 * 勾选的包被本 mod 拦截 MTR4/JCM 解析，改以 MTR3 模式加载。
 */
public final class Mtr3CompatConfig {

	private static final Set<String> MTR3_PACKS = new LinkedHashSet<>();
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

	public static void load() {
		try {
			final Path path = file();
			if (!Files.isRegularFile(path)) {
				return;
			}
			final JsonElement root = JsonParser.parseString(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
			if (root.isJsonObject() && root.getAsJsonObject().has("mtr3_packs")) {
				MTR3_PACKS.clear();
				for (final JsonElement element : root.getAsJsonObject().getAsJsonArray("mtr3_packs")) {
					MTR3_PACKS.add(normalize(element.getAsString()));
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
			Files.write(path, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 保存配置失败", e);
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("mtr3-compat.json");
	}
}
