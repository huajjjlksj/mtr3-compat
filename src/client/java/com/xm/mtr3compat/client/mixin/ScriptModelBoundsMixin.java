package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.core.util.model.ModelJS;
import com.lx862.mtrscripting.core.util.model.ModelManagerJS;
import com.lx862.mtrscripting.core.util.model.RawModelJS;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 诊断用：打印脚本模型上传时顶点坐标的包围盒（只打前 3 个模型），用于判断 LCD 面片在
 * “模型构建/上传”阶段是否已经偏离（预期 z 极值 ±2.5 / ±7.5）。
 * 两个注入都使用 require = 0，目标方法名不匹配时只跳过，不会打断渲染。
 */
@Mixin(value = ModelManagerJS.class, remap = false)
public class ScriptModelBoundsMixin {

	private static final AtomicInteger LOGGED = new AtomicInteger();

	@Inject(method = "upload", at = @At("HEAD"), require = 0)
	private static void mtr3compat$dumpBounds(RawModelJS rawModel, CallbackInfoReturnable<ModelJS> cir) {
		dump(rawModel);
	}

	@Inject(method = "uploadVertArrays", at = @At("HEAD"), require = 0)
	private static void mtr3compat$dumpBoundsVertArrays(RawModelJS rawModel, CallbackInfoReturnable<ModelJS> cir) {
		dump(rawModel);
	}

	private static void dump(RawModelJS rawModel) {
		if (rawModel == null || LOGGED.get() >= 3) {
			return;
		}
		try {
			final double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
			final double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
			final int[] count = {0};
			rawModel.rawModel.iterateRawMeshList(mesh -> {
				for (final org.mtr.mapping.render.vertex.Vertex vertex : mesh.vertices) {
					final double x = vertex.position.getX();
					final double y = vertex.position.getY();
					final double z = vertex.position.getZ();
					min[0] = Math.min(min[0], x);
					min[1] = Math.min(min[1], y);
					min[2] = Math.min(min[2], z);
					max[0] = Math.max(max[0], x);
					max[1] = Math.max(max[1], y);
					max[2] = Math.max(max[2], z);
					count[0]++;
				}
			});
			if (count[0] == 0) {
				return;
			}
			LOGGED.incrementAndGet();
			Mtr3CompatClient.LOGGER.info(
					"[mtr3compat] script-model verts={} x=[{}, {}] y=[{}, {}] z=[{}, {}]",
					count[0], fmt(min[0]), fmt(max[0]), fmt(min[1]), fmt(max[1]), fmt(min[2]), fmt(max[2]));
		} catch (Throwable e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 打印脚本模型包围盒失败", e);
		}
	}

	private static String fmt(double value) {
		return String.format(java.util.Locale.ROOT, "%.3f", value);
	}
}
