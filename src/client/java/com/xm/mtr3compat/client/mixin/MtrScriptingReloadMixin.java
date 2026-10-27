package com.xm.mtr3compat.client.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lx862.mtrscripting.core.ScriptManager;
import com.lx862.mtrscripting.lib.org.mozilla.javascript.NativeJavaClass;
import com.lx862.mtrscripting.mod.impl.mtr.MTRContentScripting;
import com.lx862.mtrscripting.mod.resource.MtrScriptingResourceManager;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.Mtr3CompatConfig;
import com.xm.mtr3compat.client.Mtr3CompatPacks;
import com.xm.mtr3compat.client.MtrClientDataCompat;
import com.xm.mtr3compat.client.ante.AnteGlobals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 脚本接管：勾选包的 mtr_custom_resources.json 已被 readAllResources 过滤（JCM parseCustom 不再解析），
 * 在 JCM reload 尾部从磁盘重喂给三个 provider。
 * 必须走 legacy 分支（isLegacy=true）：包内是 custom_trains 结构，native 分支只认 vehicles/vehicleScripts。
 * legacy 分支把车辆脚本登记在 mtr_custom_train_(id)_trailer 等变体 id 上，原生车型的原 id
 * 由 {@link com.xm.mtr3compat.client.JcmScriptAlias} 在渲染前补登别名。
 */
@Mixin(MtrScriptingResourceManager.class)
public class MtrScriptingReloadMixin {

	/** parseScriptEvent 监听只注册一次；注册后晚于 JCM 自身监听，勾选包解析窗口内覆盖 MTRClientData 绑定。 */
	private static boolean globalsHooked = false;

	/** RhinoParsedScript 的脚本作用域（private 字段），供解析事件回调取用。 */
	private static final java.lang.reflect.Field SCOPE_FIELD = mtr3compat$scopeField();

	private static java.lang.reflect.Field mtr3compat$scopeField() {
		try {
			final java.lang.reflect.Field field = com.lx862.mtrscripting.core.rhinojs.RhinoParsedScript.class.getDeclaredField("scope");
			field.setAccessible(true);
			return field;
		} catch (NoSuchFieldException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private static void mtr3compat$ensureGlobalsHooked() {
		if (globalsHooked) {
			return;
		}
		final ScriptManager manager = MTRContentScripting.getScriptManager();
		if (manager == null) {
			return;
		}
		globalsHooked = true;
		// ANTE 包脚本常用 Java.type 访问原生类与 joml，放开 ClassShutter（首次 refeed 时永久生效）。
		try {
			manager.getClassShutter().allowClass(
					com.lx862.mtrscripting.core.api.ClassRule.parse("net.minecraft.*"),
					com.lx862.mtrscripting.core.api.ClassRule.parse("org.joml.*"),
					com.lx862.mtrscripting.core.api.ClassRule.parse("com.mojang.*"),
					// 兼容层自身（NativeJavaClass 绑定与返回值包装）
					com.lx862.mtrscripting.core.api.ClassRule.parse("com.xm.mtr3compat.*"),
					// ScriptVector3f 等 JCM 核心类型（不含 Rhino 内部）
					com.lx862.mtrscripting.core.api.ClassRule.parse("com.lx862.mtrscripting.core.*"),
					// org.mtr.mapping.holder/mapper、org.mtr.core/mod：API 参数与返回值
					com.lx862.mtrscripting.core.api.ClassRule.parse("org.mtr.*"));
		} catch (Exception e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] ClassShutter 放开失败", e);
		}
		// 部分 MTR3 资源包的 LCD 脚本运行期用 java.lang.reflect.Array.newInstance 与 java.lang.Integer.TYPE，
		// 被 JCM 默认 deny 的 java.lang.reflect.* / java.lang.Class 拦截（空 catch 吞掉后 LCD 空白）。
		// 反射移除这两条 deny；Process/ProcessBuilder/ClassLoader/System 等危险类仍保持禁用。
		try {
			final java.lang.reflect.Field deniedField = com.lx862.mtrscripting.core.api.MTRClassShutter.class.getDeclaredField("deniedScriptClasses");
			deniedField.setAccessible(true);
			@SuppressWarnings("unchecked")
			final java.util.List<com.lx862.mtrscripting.core.api.ClassRule> denied =
					(java.util.List<com.lx862.mtrscripting.core.api.ClassRule>) deniedField.get(manager.getClassShutter());
			final int before = denied.size();
			denied.removeIf(rule -> rule.match("java.lang.reflect.Array") || rule.match("java.lang.Class"));
			Mtr3CompatClient.LOGGER.info("[mtr3compat] ClassShutter deny 移除 {} 条（reflect/Class）", before - denied.size());
		} catch (Exception e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] ClassShutter deny 移除失败", e);
		}
		final java.util.function.BiConsumer<String, com.lx862.mtrscripting.core.primitive.ParsedScript> override =
				(name, script) -> {
					if (Mtr3CompatClient.SPOOF_MTR_VERSION) {
						final long bindStart = System.nanoTime();
						final boolean hasConfig = com.xm.mtr3compat.client.ante.ConfigCapture.LAST.get() != null;
						try {
							final com.lx862.mtrscripting.lib.org.mozilla.javascript.Scriptable scope =
									(com.lx862.mtrscripting.lib.org.mozilla.javascript.Scriptable) SCOPE_FIELD.get(script);
							final com.lx862.mtrscripting.lib.org.mozilla.javascript.Context ctx =
									com.lx862.mtrscripting.lib.org.mozilla.javascript.Context.enter();
							try {
								AnteGlobals.bind(name, ctx, scope);
							} finally {
								com.lx862.mtrscripting.lib.org.mozilla.javascript.Context.exit();
							}
						} catch (Exception e) {
							Mtr3CompatClient.LOGGER.warn("[mtr3compat] ANTE 全局绑定失败: " + name, e);
						}
						final long bindMs = (System.nanoTime() - bindStart) / 1_000_000L;
						if (bindMs >= 50L) {
							Mtr3CompatClient.LOGGER.info("[mtr3compat] ANTE 绑定慢: {} = {}ms (config={})", name, bindMs, hasConfig);
						}
					}
				};
		manager.parseScriptEvent.register(override);
	}

	/**
	 * 注入点在 reload() 内、finishReload() 之前：JCM 的 reload 用 beginReload()（GlStateTracker.capture）
	 * 与 finishReload()（restore）包住解析窗口，脚本在解析期调用 uploadVertArrays 需要该 GL 保护状态，
	 * 注入到 TAIL 会在 restore 之后执行并抛 "GlStateTracker: Not protected"。
	 */
	@Inject(method = "reload()V", at = @At(value = "INVOKE", target = "Lorg/mtr/mod/resource/OptimizedRendererWrapper;finishReload()V"))
	private static void mtr3compat$refeedScripts(CallbackInfo ci) {
		if (!Mtr3CompatConfig.hasIntercepted()) {
			return;
		}
		mtr3compat$ensureGlobalsHooked();
		Mtr3CompatClient.SPOOF_MTR_VERSION = true;
		try {
			for (final String packName : Mtr3CompatConfig.getPacks()) {
			final Path packPath = Mtr3CompatPacks.resolve(packName);
			if (packPath == null) {
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] 勾选的资源包不存在: " + packName);
				continue;
			}
			final byte[] bytes = Mtr3CompatPacks.readPackFile(packPath, "assets/mtr/mtr_custom_resources.json");
			if (bytes == null) {
				continue;
			}
			try {
				final JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
				if (!root.isJsonObject()) {
					continue;
				}
				final JsonObject rootObject = root.getAsJsonObject();
				mtr3compat$dedupScripts(rootObject);
				final long vehicleStart = System.nanoTime();
				MtrScriptingResourceManager.vehicle.parseMtrResources(rootObject, rootObject.has("custom_trains"));
				final long vehicleMs = (System.nanoTime() - vehicleStart) / 1_000_000L;
				final long eyecandyStart = System.nanoTime();
				MtrScriptingResourceManager.eyecandy.parseMtrResources(rootObject, rootObject.has("custom_eyecandy"));
				final long eyecandyMs = (System.nanoTime() - eyecandyStart) / 1_000_000L;
				final long liftStart = System.nanoTime();
				MtrScriptingResourceManager.lift.parseMtrResources(rootObject, rootObject.has("custom_lifts"));
				final long liftMs = (System.nanoTime() - liftStart) / 1_000_000L;
				Mtr3CompatClient.LOGGER.info("[mtr3compat] 脚本解析耗时 {}: vehicle={}ms eyecandy={}ms lift={}ms", packName, vehicleMs, eyecandyMs, liftMs);
			} catch (Exception e) {
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] 脚本重喂失败: " + packName, e);
			}
		}
		} finally {
			Mtr3CompatClient.SPOOF_MTR_VERSION = false;
		}
	}

	/**
	 * 重喂前按 script_files（保持原始顺序）分组去重：每组仅保留首个条目完整脚本，
	 * 其余条目改写为 script_id 引用。JCM legacy 分支对带 script_id 的条目只登记别名映射、
	 * 不再重复编译，避免同一脚本链被逐条目完整编译（顶层含 display_helper 建网格、字体加载等重活）。
	 * 仅影响传给 JCM 的副本；NativeTrainLoader 的原生注册读独立解析的原始 JSON，不受影响。
	 */
	private static int mtr3compat$dedupScripts(JsonObject rootObject) {
		final JsonElement trainsElement = rootObject.get("custom_trains");
		if (trainsElement == null || !trainsElement.isJsonObject()) {
			return 0;
		}
		final JsonObject trains = trainsElement.getAsJsonObject();
		final Map<String, String> firstByScript = new HashMap<>();
		int deduped = 0;
		int intraDeduped = 0;
		for (final Map.Entry<String, JsonElement> entry : trains.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			final JsonObject vehicleObject = entry.getValue().getAsJsonObject();
			if (vehicleObject.has("script_id")) {
				continue;
			}
			final JsonElement scriptFiles = vehicleObject.get("script_files");
			if (scriptFiles == null || !scriptFiles.isJsonArray()) {
				continue;
			}
			// 先去掉同一车型 script_files 内的重复条目（同一脚本文件被写了两次）。JCM/Rhino 把所有脚本在同一作用域求值，
			// 同一文件执行两次会触发 const 重声明，整段脚本解析失败。保持首次出现顺序。
			final JsonArray originalArray = scriptFiles.getAsJsonArray();
			final JsonArray dedupedArray = mtr3compat$dedupIntraArray(originalArray);
			if (dedupedArray.size() != originalArray.size()) {
				vehicleObject.add("script_files", dedupedArray);
				intraDeduped += originalArray.size() - dedupedArray.size();
			}
			final String key = mtr3compat$scriptKey(vehicleObject);
			if (key == null) {
				continue;
			}
			final String canonical = firstByScript.putIfAbsent(key, entry.getKey());
			if (canonical != null) {
				vehicleObject.remove("script_files");
				vehicleObject.addProperty("script_id", "mtr_custom_train_" + canonical);
				deduped++;
			}
		}
		if (intraDeduped > 0 || deduped > 0) {
			Mtr3CompatClient.LOGGER.info("[mtr3compat] 脚本去重: 数组内重复 {} 条, 跨车型 script_id 引用 {} 个", intraDeduped, deduped);
		}
		return deduped;
	}

	/** 去掉 JsonArray 内的重复字符串条目，保持首次出现顺序。非字符串条目原样保留。 */
	private static JsonArray mtr3compat$dedupIntraArray(JsonArray array) {
		final JsonArray result = new JsonArray();
		final java.util.Set<String> seen = new java.util.HashSet<>();
		for (final JsonElement element : array) {
			if (element.isJsonPrimitive()) {
				final String value = element.getAsString();
				if (seen.add(value)) {
					result.add(element);
				}
			} else {
				result.add(element);
			}
		}
		return result;
	}

	private static String mtr3compat$scriptKey(JsonObject vehicleObject) {
		final StringBuilder builder = new StringBuilder();
		for (final JsonElement element : vehicleObject.getAsJsonArray("script_files")) {
			if (!element.isJsonPrimitive()) {
				return null;
			}
			builder.append(element.getAsString()).append('\n');
		}
		// script_texts 决定脚本的具体参数（例如 LCD 的 slots 文件：门数不同 offset 不同），
		// 相同 script_files 但 script_texts 不同的车型必须各自编译，不能合并到同一脚本条目。
		final JsonElement scriptTexts = vehicleObject.get("script_texts");
		builder.append("--texts--\n");
		if (scriptTexts != null && scriptTexts.isJsonArray()) {
			for (final JsonElement element : scriptTexts.getAsJsonArray()) {
				if (!element.isJsonPrimitive()) {
					return null;
				}
				builder.append(element.getAsString()).append('\n');
			}
		}
		return builder.toString();
	}
}
