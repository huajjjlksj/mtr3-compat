package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.core.util.model.ModelJS;
import com.lx862.mtrscripting.core.util.model.ModelManagerJS;
import com.lx862.mtrscripting.core.util.model.RawModelJS;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 诊断用：每个脚本模型上传时打印它的“面片中心”列表（每 4 个顶点一组），
 * 用于核对 LCD 面片到底有几个、分别落在什么 z 上。
 */
@Mixin(value = ModelManagerJS.class, remap = false)
public class ScriptModelBoundsMixin {

	private static final AtomicInteger LOGGED = new AtomicInteger();

	@Inject(method = "upload", at = @At("HEAD"), require = 0)
	private static void mtr3compat$dump(RawModelJS rawModel, CallbackInfoReturnable<ModelJS> cir) {
		dump(rawModel);
	}

	@Inject(method = "uploadVertArrays", at = @At("HEAD"), require = 0)
	private static void mtr3compat$dumpVertArrays(RawModelJS rawModel, CallbackInfoReturnable<ModelJS> cir) {
		dump(rawModel);
	}

	private static void dump(RawModelJS rawModel) {
		if (rawModel == null || LOGGED.get() >= 20) {
			return;
		}
		try {
			final Set<String> quads = new LinkedHashSet<>();
			final int[] count = {0};
			rawModel.rawModel.iterateRawMeshList(mesh -> {
				final java.util.List<org.mtr.mapping.render.vertex.Vertex> vertices = mesh.vertices;
				count[0] += vertices.size();
				for (int i = 0; i + 3 < vertices.size(); i += 4) {
					double x = 0, y = 0, z = 0;
					for (int k = 0; k < 4; k++) {
						x += vertices.get(i + k).position.getX();
						y += vertices.get(i + k).position.getY();
						z += vertices.get(i + k).position.getZ();
					}
					quads.add(String.format(java.util.Locale.ROOT, "(%+.2f,%+.2f,%+.2f)", x / 4, y / 4, z / 4));
				}
			});
			if (count[0] == 0) {
				return;
			}
			LOGGED.incrementAndGet();
			final StringBuilder builder = new StringBuilder();
			quads.forEach(quad -> builder.append(quad).append(' '));
			Mtr3CompatClient.LOGGER.info("[mtr3compat] script-model verts={} quads={} centers={}", count[0], quads.size(), builder);
		} catch (Throwable e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 打印脚本模型面片失败", e);
		}
	}
}
