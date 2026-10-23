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
import org.apache.logging.log4j.util.TriConsumer;
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
		final TriConsumer<String, com.lx862.mtrscripting.lib.org.mozilla.javascript.Context, com.lx862.mtrscripting.lib.org.mozilla.javascript.Scriptable> override =
				(name, ctx, scope) -> {
					if (Mtr3CompatClient.SPOOF_MTR_VERSION) {
						try {
							AnteGlobals.bind(name, ctx, scope);
						} catch (Exception e) {
							Mtr3CompatClient.LOGGER.warn("[mtr3compat] ANTE 全局绑定失败: " + name, e);
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
				final int deduped = mtr3compat$dedupScripts(rootObject);
				MtrScriptingResourceManager.vehicle.parseMtrResources(rootObject, rootObject.has("custom_trains"));
				MtrScriptingResourceManager.eyecandy.parseMtrResources(rootObject, rootObject.has("custom_eyecandy"));
				MtrScriptingResourceManager.lift.parseMtrResources(rootObject, rootObject.has("custom_lifts"));
				if (deduped > 0) {
					Mtr3CompatClient.LOGGER.info("[mtr3compat] 脚本去重: {} 个重复条目改为 script_id 引用（省 {} 次编译）", packName, deduped);
				}
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
			final String key = mtr3compat$scriptKey(scriptFiles.getAsJsonArray());
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
		return deduped;
	}

	private static String mtr3compat$scriptKey(JsonArray scriptFiles) {
		final StringBuilder builder = new StringBuilder();
		for (final JsonElement element : scriptFiles) {
			if (!element.isJsonPrimitive()) {
				return null;
			}
			builder.append(element.getAsString()).append('\n');
		}
		return builder.toString();
	}
}
