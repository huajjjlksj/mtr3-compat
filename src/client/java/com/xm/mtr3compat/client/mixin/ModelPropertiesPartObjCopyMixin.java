package com.xm.mtr3compat.client.mixin;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mod.resource.ModelPropertiesPart;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

/**
 * MTR3 资源包常把同一个 OBJ 组复用给多节车（同一部件同时用于多节车厢）。
 * MTR4 的 ModelPropertiesPart.writeCache 直接包装 nameToObjModels 里的共享 ObjModel 实例，
 * 而 ObjModel.addTransformation 会把变换后的网格<原地追加>到该实例，导致多节车的位移全部累积，
 * 部件被复制到其它车厢的位置（表现为部件/内饰错位）。NTE 中每个部件都会 copy 一份模型，故 MTR3 无此问题。
 * 这里在每个部件包装时创建共享网格、独立 rawModel 的副本，恢复 NTE 语义。
 *
 * 另外，OBJ 走优化渲染时车门会单独绘制一份"开门态"模型：
 * renderNormal 里再次 translate/rotateY180，而这份模型本身就是 writeCache 阶段
 * 已经按关门位姿烘焙过的同一实例，于是门叶被平移/旋转两次（镜像门板被转 360° 后
 * 落到车身另一侧，开门时表现为门板瞬间消失、对侧也开门）。
 * 这里把车门单独渲染用的模型改为从未施加变换的原始网格重建，门叶只承受渲染时那一次变换。
 * 注意 ObjModel 构造器的 boolean 是 applyUVMirror，不会把网格装入 rawModel（新实例的 rawModel 恒为空），
 * 因此重建后必须自己调用一次 addTransformation 把原始网格装入。
 */
@Mixin(ModelPropertiesPart.class)
public class ModelPropertiesPartObjCopyMixin {

	private static Constructor<?> mtr3compat$ctor;
	private static Field mtr3compat$rawMeshes;
	private static boolean mtr3compat$logged = false;
	private static boolean mtr3compat$doorLogged = false;
	private static double mtr3compat$modelYOffset = 0;

	/**
	 * 车体烘焙时 addObjModelPosition 会把模型整体下移 modelYOffset，
	 * 而 renderNormal 的门叶绘制不含该偏移，这里捕获下来供门叶模型补齐同一基准。
	 */
	@Inject(method = "addObjModelPosition", at = @At("HEAD"))
	private void mtr3compat$captureModelYOffset(ObjectArrayList<?> objModels, Object2ObjectOpenHashMap<?, ?> map, double x, double y, double z, boolean flipped, double modelYOffset, CallbackInfo callbackInfo) {
		mtr3compat$modelYOffset = modelYOffset;
	}

	@Redirect(method = "lambda$writeCache$10", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
	private static Object mtr3compat$isolateObjModel(java.util.Map<?, ?> objModels, Object name) {
		if (!mtr3compat$logged) {
			mtr3compat$logged = true;
			com.xm.mtr3compat.client.Mtr3CompatClient.LOGGER.info("[mtr3compat] OBJ 部件副本重定向已命中");
		}
		final Object objModel = objModels.get(name);
		return objModel instanceof OptimizedModel.ObjModel ? mtr3compat$copy((OptimizedModel.ObjModel) objModel) : objModel;
	}

	@Redirect(method = "lambda$writeCache$11", at = @At(value = "INVOKE", target = "Lorg/mtr/mod/resource/OptimizedModelWrapper;fromObjModels(Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;)Lorg/mtr/mod/resource/OptimizedModelWrapper;"), require = 0)
	private static OptimizedModelWrapper mtr3compat$doorFromUntransformed(ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper> objModels) {
		if (!mtr3compat$doorLogged) {
			mtr3compat$doorLogged = true;
			com.xm.mtr3compat.client.Mtr3CompatClient.LOGGER.info("[mtr3compat] OBJ 车门独立模型重定向已命中");
		}
		final ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper> untransformed = new ObjectArrayList<>();
		objModels.forEach(wrapper -> {
			final OptimizedModel.ObjModel fresh = wrapper.objModel == null ? null : mtr3compat$copy(wrapper.objModel);
			if (fresh != null) {
				// 把未变换的原始网格装入 rawModel；只补车体的 y 基准偏移，剩下的一次变换交给 renderNormal。
				@SuppressWarnings("unchecked")
				final List<RawMesh> meshes = (List<RawMesh>) mtr3compat$rawMeshes(wrapper.objModel);
				if (!meshes.isEmpty()) {
					fresh.addTransformation(meshes.get(0).materialProperties.shaderType, 0, -mtr3compat$modelYOffset, 0, false);
				}
			}
			untransformed.add(fresh == null ? wrapper : new OptimizedModelWrapper.ObjModelWrapper(fresh));
		});
		return OptimizedModelWrapper.fromObjModels(untransformed);
	}

	@SuppressWarnings("unchecked")
	private static List<?> mtr3compat$rawMeshes(OptimizedModel.ObjModel source) {
		try {
			if (mtr3compat$rawMeshes == null) {
				mtr3compat$rawMeshes = OptimizedModel.ObjModel.class.getDeclaredField("rawMeshes");
				mtr3compat$rawMeshes.setAccessible(true);
			}
			return (List<?>) mtr3compat$rawMeshes.get(source);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 共享原始网格、独立空 rawModel 的 ObjModel 副本。
	 * 构造器的 boolean 只能用于 applyUVMirror，绝不能置 true（那会改动被多节车共享的原始网格 UV）。
	 */
	@SuppressWarnings("unchecked")
	private static OptimizedModel.ObjModel mtr3compat$copy(OptimizedModel.ObjModel source) {
		try {
			if (mtr3compat$ctor == null) {
				mtr3compat$ctor = OptimizedModel.ObjModel.class.getDeclaredConstructor(List.class, boolean.class, float.class, float.class, float.class, float.class, float.class, float.class);
				mtr3compat$ctor.setAccessible(true);
			}
			return (OptimizedModel.ObjModel) mtr3compat$ctor.newInstance(
					mtr3compat$rawMeshes(source), false,
					source.getMinX(), source.getMinY(), source.getMinZ(), source.getMaxX(), source.getMaxY(), source.getMaxZ()
			);
		} catch (Exception e) {
			return source;
		}
	}
}
