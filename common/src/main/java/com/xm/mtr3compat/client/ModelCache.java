package com.xm.mtr3compat.client;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mod.resource.CachedResource;
import org.mtr.mod.resource.VehicleModel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MTR3 资源包的模型解析缓存。
 * <p>
 * MTR4 的 {@code VehicleResource} 按（第几节车厢 × 总节数）分别缓存模型，
 * {@code extraModelsSupplier} 每次都会新建 {@code VehicleModel}，于是同一份 OBJ
 * （如 CRH2C 单个 ~186MB）会按编组被反复读取、解析 N 次，加载时长时间卡顿。
 * Ante/NTE 的管线是“按文件解析一次、之后复制”，这里做等价处理：
 * <ul>
 *   <li>解析缓存：按（模型路径 + 贴图标识 + flipV）只解析一次；命中时返回共享 rawMeshes、
 *       各自独立 rawModel 的浅拷贝。变换只写入 rawModel，rawMeshes 在解析完成后不再被修改，
 *       因此共享是安全的（与 {@code ModelPropertiesPartObjCopyMixin} 的复制方式一致）。</li>
 *   <li>取消过期：把 {@code VehicleModel.cachedModel} 的 lifespan 拉长，避免闲置 60 秒后
 *       再次重建模型。</li>
 * </ul>
 * 注意：不能复用 {@code VehicleModel} 实例——{@code DynamicVehicleModel.writeFloorsAndDoorways}
 * 会把内部 map 清空，一个实例只能被一节车厢消费一次。
 * <p>
 * 只对勾选（MTR3 模式）资源包注册的车型生效，未勾选的包仍走 MTR4 原有 OBJ 加载。
 */
public final class ModelCache {

	/** 解析缓存：key -> (组名 -> ObjModel)。 */
	private static final Map<String, Map<String, OptimizedModel.ObjModel>> PARSED = new HashMap<>();

	private static Constructor<?> objModelConstructor;
	private static Field rawMeshesField;
	private static Field cachedModelField;
	private static Field lifespanField;
	private static boolean loggedCopyFailure;
	private static boolean loggedExpiryFailure;

	private ModelCache() {
	}

	/** 解析缓存键。 */
	public static String parseKey(String modelResource, Identifier modelLocation, boolean flipV) {
		final String location = modelLocation == null ? "" : modelLocation.getNamespace() + ":" + modelLocation.getPath();
		return modelResource + '|' + location + '|' + flipV;
	}

	/** 命中缓存时返回一份可直接使用的浅拷贝，未命中返回 null。 */
	public static Object2ObjectAVLTreeMap<String, OptimizedModel.ObjModel> copyParsed(String key) {
		final Map<String, OptimizedModel.ObjModel> models = PARSED.get(key);
		if (models == null) {
			return null;
		}
		final Object2ObjectAVLTreeMap<String, OptimizedModel.ObjModel> copies = new Object2ObjectAVLTreeMap<>();
		for (final Map.Entry<String, OptimizedModel.ObjModel> entry : models.entrySet()) {
			copies.put(entry.getKey(), copyObjModel(entry.getValue()));
		}
		return copies;
	}

	/** 保存首次解析结果（保留实例本身；后续使用方拿到的都是浅拷贝）。 */
	public static void storeParsed(String key, Map<String, OptimizedModel.ObjModel> models) {
		if (models == null || models.isEmpty() || PARSED.containsKey(key)) {
			return;
		}
		PARSED.put(key, new HashMap<>(models));
	}

	/** 复制一个共享 rawMeshes、独立 rawModel 的 ObjModel。 */
	private static OptimizedModel.ObjModel copyObjModel(OptimizedModel.ObjModel source) {
		try {
			if (objModelConstructor == null) {
				objModelConstructor = OptimizedModel.ObjModel.class.getDeclaredConstructor(List.class, boolean.class, float.class, float.class, float.class, float.class, float.class, float.class);
				objModelConstructor.setAccessible(true);
				rawMeshesField = OptimizedModel.ObjModel.class.getDeclaredField("rawMeshes");
				rawMeshesField.setAccessible(true);
			}
			// boolean 参数为 false：网格已是解析后的结果，不再二次处理；rawModel 为新建的空模型
			return (OptimizedModel.ObjModel) objModelConstructor.newInstance(
					rawMeshesField.get(source), false,
					source.getMinX(), source.getMinY(), source.getMinZ(), source.getMaxX(), source.getMaxY(), source.getMaxZ()
			);
		} catch (Exception e) {
			if (!loggedCopyFailure) {
				loggedCopyFailure = true;
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] 模型浅拷贝失败，退回原实例", e);
			}
			return source;
		}
	}

	/** 资源包重载时清空；正在使用的模型仍被渲染代码引用，不会被释放。 */
	public static void clear() {
		PARSED.clear();
	}

	/**
	 * 取消 {@code VehicleModel.cachedModel} 的过期：默认未预载的模型闲置 60 秒后会被丢弃并重新解析。
	 * lifespan 是 final 字段，Java 对普通类的实例 final 字段允许反射赋值。
	 */
	public static void disableExpiry(VehicleModel model) {
		try {
			if (cachedModelField == null) {
				cachedModelField = VehicleModel.class.getDeclaredField("cachedModel");
				cachedModelField.setAccessible(true);
				lifespanField = CachedResource.class.getDeclaredField("lifespan");
				lifespanField.setAccessible(true);
			}
			final CachedResource<?> cached = (CachedResource<?>) cachedModelField.get(model);
			if (cached != null) {
				lifespanField.setLong(cached, Integer.MAX_VALUE);
			}
		} catch (Exception e) {
			if (!loggedExpiryFailure) {
				loggedExpiryFailure = true;
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] 取消模型过期失败（不影响加载，仅会周期性重建）", e);
			}
		}
	}
}
