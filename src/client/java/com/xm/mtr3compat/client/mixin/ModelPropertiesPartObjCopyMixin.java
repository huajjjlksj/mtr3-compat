package com.xm.mtr3compat.client.mixin;

import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mod.resource.ModelPropertiesPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

/**
 * MTR3 资源包常把同一个 OBJ 组复用给多节车（同一部件同时用于多节车厢）。
 * MTR4 的 ModelPropertiesPart.writeCache 直接包装 nameToObjModels 里的共享 ObjModel 实例，
 * 而 ObjModel.addTransformation 会把变换后的网格<原地追加>到该实例，导致多节车的位移全部累积，
 * 部件被复制到其它车厢的位置（表现为部件/内饰错位）。NTE 中每个部件都会 copy 一份模型，故 MTR3 无此问题。
 * 这里在每个部件包装时创建共享网格、独立 rawModel 的副本，恢复 NTE 语义。
 */
@Mixin(ModelPropertiesPart.class)
public class ModelPropertiesPartObjCopyMixin {

	private static Constructor<?> mtr3compat$ctor;
	private static Field mtr3compat$rawMeshes;
	private static boolean mtr3compat$logged = false;

	@Redirect(method = "lambda$writeCache$10", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
	private static Object mtr3compat$isolateObjModel(java.util.Map<?, ?> objModels, Object name) {
		if (!mtr3compat$logged) {
			mtr3compat$logged = true;
			com.xm.mtr3compat.client.Mtr3CompatClient.LOGGER.info("[mtr3compat] OBJ 部件副本重定向已命中");
		}
		final Object objModel = objModels.get(name);
		return objModel instanceof OptimizedModel.ObjModel ? mtr3compat$copy((OptimizedModel.ObjModel) objModel) : objModel;
	}

	@SuppressWarnings("unchecked")
	private static OptimizedModel.ObjModel mtr3compat$copy(OptimizedModel.ObjModel source) {
		try {
			if (mtr3compat$ctor == null) {
				mtr3compat$ctor = OptimizedModel.ObjModel.class.getDeclaredConstructor(List.class, boolean.class, float.class, float.class, float.class, float.class, float.class, float.class);
				mtr3compat$ctor.setAccessible(true);
				mtr3compat$rawMeshes = OptimizedModel.ObjModel.class.getDeclaredField("rawMeshes");
				mtr3compat$rawMeshes.setAccessible(true);
			}
			// boolean 参数为 false：网格已是 loadModel 烘焙后的结果，不再二次处理
			return (OptimizedModel.ObjModel) mtr3compat$ctor.newInstance(
					mtr3compat$rawMeshes.get(source), false,
					source.getMinX(), source.getMinY(), source.getMinZ(), source.getMaxX(), source.getMaxY(), source.getMaxZ()
			);
		} catch (Exception e) {
			return source;
		}
	}
}
