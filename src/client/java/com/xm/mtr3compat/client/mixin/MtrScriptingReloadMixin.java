package com.xm.mtr3compat.client.mixin;

import com.google.gson.JsonElement;
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
					com.lx862.mtrscripting.core.api.ClassRule.parse("com.mojang.*"));
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
				MtrScriptingResourceManager.vehicle.parseMtrResources(root.getAsJsonObject(), root.getAsJsonObject().has("custom_trains"));
				MtrScriptingResourceManager.eyecandy.parseMtrResources(root.getAsJsonObject(), root.getAsJsonObject().has("custom_eyecandy"));
				MtrScriptingResourceManager.lift.parseMtrResources(root.getAsJsonObject(), root.getAsJsonObject().has("custom_lifts"));
			} catch (Exception e) {
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] 脚本重喂失败: " + packName, e);
			}
		}
		} finally {
			Mtr3CompatClient.SPOOF_MTR_VERSION = false;
		}
	}
}
