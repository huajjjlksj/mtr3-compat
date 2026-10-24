package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.core.util.render.ScriptRenderManager;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 诊断用：脚本模型真正回放时使用的 StoredMatrixTransformations 里携带的初始平移量。
 * 只读，不改任何行为；require = 0，目标不匹配时仅跳过。
 */
@Mixin(value = ScriptRenderManager.class, remap = false)
public class ScriptRenderManagerInvokeMixin {

	private static final AtomicInteger LOGGED = new AtomicInteger();
	private static Field FIELD_X;
	private static Field FIELD_Y;
	private static Field FIELD_Z;
	private static Field FIELD_DEFAULT;

	@Inject(method = "invoke", at = @At("HEAD"), require = 0)
	private void mtr3compat$dumpSt(org.mtr.mapping.holder.World world, StoredMatrixTransformations storedMatrixTransformations,
								org.mtr.mapping.holder.Direction direction, int light, CallbackInfo ci) {
		if (storedMatrixTransformations == null || LOGGED.get() >= 6) {
			return;
		}
		try {
			if (FIELD_X == null) {
				FIELD_X = StoredMatrixTransformations.class.getDeclaredField("initialTranslateX");
				FIELD_Y = StoredMatrixTransformations.class.getDeclaredField("initialTranslateY");
				FIELD_Z = StoredMatrixTransformations.class.getDeclaredField("initialTranslateZ");
				FIELD_DEFAULT = StoredMatrixTransformations.class.getDeclaredField("useDefaultOffset");
				FIELD_X.setAccessible(true);
				FIELD_Y.setAccessible(true);
				FIELD_Z.setAccessible(true);
				FIELD_DEFAULT.setAccessible(true);
			}
			LOGGED.incrementAndGet();
			Mtr3CompatClient.LOGGER.info("[mtr3compat] script-ST default={} t=({}, {}, {})",
					FIELD_DEFAULT.getBoolean(storedMatrixTransformations),
					FIELD_X.getDouble(storedMatrixTransformations),
					FIELD_Y.getDouble(storedMatrixTransformations),
					FIELD_Z.getDouble(storedMatrixTransformations));
		} catch (Throwable e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 读取脚本 ST 失败", e);
		}
	}
}
