package com.xm.mtr3compat.client.jcm;

import com.xm.mtr3compat.client.Mtr3CompatClient;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mod.resource.OptimizedModelWrapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * MTR3 / NTE 脚本的 ModelCluster API 兼容层（不改资源包）。
 *
 * 旧脚本常用以下写法调整模型亮度：
 * <pre>
 * let vertarray = modelCluster.uploadedTranslucentParts.meshList;
 * for (...) vertarray[i].materialProp.attrState.setColor(r, g, b, a);
 * </pre>
 * JCM 的 {@code ModelManager.uploadVertArrays()} 返回的是 {@code ModelJS}，没有
 * uploadedOpaqueParts / uploadedTranslucentParts / meshList，脚本在上面直接抛
 * TypeError，导致整辆车（LCD、电显、电视、车号）的脚本都不执行。
 *
 * 这里把 MTR4 的 {@code OptimizedModel.uploadedParts} 包装成旧 API 的形状；
 * 颜色通过换成带新 MaterialProperties 的 VertexArray 生效（MTR4 的顶点色是
 * 实例属性，在 bind 时下发，无需重传顶点）。
 */
public final class LegacyModelCluster {

	private static Field wrapperModelField;
	private static Field uploadedPartsField;
	private static boolean unsupported;

	private LegacyModelCluster() {
	}

	public static Object opaque(Object wrapper) {
		return parts(wrapper, false);
	}

	public static Object translucent(Object wrapper) {
		return parts(wrapper, true);
	}

	private static VertArrays parts(Object wrapper, boolean wantTranslucent) {
		final List<VertexArray> all = uploadedParts(wrapper);
		if (all == null) {
			return new VertArrays(new ArrayList<>(), new ArrayList<>());
		}
		final List<Integer> indexes = new ArrayList<>();
		for (int i = 0; i < all.size(); i++) {
			final VertexArray array = all.get(i);
			if (array != null && array.materialProperties.translucent == wantTranslucent) {
				indexes.add(i);
			}
		}
		return new VertArrays(all, indexes);
	}

	private static List<VertexArray> uploadedParts(Object wrapper) {
		if (unsupported || wrapper == null) {
			return null;
		}
		try {
			if (wrapperModelField == null) {
				final Field field = OptimizedModelWrapper.class.getDeclaredField("optimizedModel");
				field.setAccessible(true);
				wrapperModelField = field;
			}
			if (uploadedPartsField == null) {
				final Field field = OptimizedModel.class.getDeclaredField("uploadedParts");
				field.setAccessible(true);
				uploadedPartsField = field;
			}
			final Object model = wrapperModelField.get(wrapper);
			if (model == null) {
				return null;
			}
			//noinspection unchecked
			return (List<VertexArray>) uploadedPartsField.get(model);
		} catch (Throwable e) {
			unsupported = true;
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 无法读取模型网格（旧 ModelCluster API 不可用）", e);
			return null;
		}
	}

	private static VertexArray current(List<VertexArray> source, int index) {
		return index >= 0 && index < source.size() ? source.get(index) : null;
	}

	/** 对应旧 API 的 VertArrays。 */
	public static final class VertArrays {
		private final List<VertexArray> source;
		private final List<Integer> indexes;

		VertArrays(List<VertexArray> source, List<Integer> indexes) {
			this.source = source;
			this.indexes = indexes;
		}

		public List<VertArray> getMeshList() {
			final List<VertArray> meshes = new ArrayList<>(indexes.size());
			for (final int index : indexes) {
				meshes.add(new VertArray(source, index));
			}
			return meshes;
		}
	}

	/** 对应旧 API 的 VertArray。 */
	public static final class VertArray {
		private final List<VertexArray> source;
		private final int index;

		VertArray(List<VertexArray> source, int index) {
			this.source = source;
			this.index = index;
		}

		public MaterialProp getMaterialProp() {
			return new MaterialProp(source, index);
		}
	}

	/** 对应旧 API 的 MaterialProp。 */
	public static final class MaterialProp {
		private final List<VertexArray> source;
		private final int index;

		MaterialProp(List<VertexArray> source, int index) {
			this.source = source;
			this.index = index;
		}

		public Identifier getTexture() {
			final VertexArray array = current(source, index);
			return array == null ? null : array.materialProperties.getTexture();
		}

		public boolean isTranslucent() {
			final VertexArray array = current(source, index);
			return array != null && array.materialProperties.translucent;
		}

		public boolean isWriteDepthBuf() {
			final VertexArray array = current(source, index);
			return array != null && array.materialProperties.writeDepthBuf;
		}

		public VertAttrState getAttrState() {
			return new VertAttrState(source, index);
		}
	}

	/** 对应旧 API 的 VertAttrState：颜色为 RRGGBBAA。 */
	public static final class VertAttrState {
		private static boolean warned;

		private final List<VertexArray> source;
		private final int index;

		VertAttrState(List<VertexArray> source, int index) {
			this.source = source;
			this.index = index;
		}

		public int getColor() {
			final VertexArray array = current(source, index);
			final Integer color = array == null ? null : array.materialProperties.vertexAttributeState.color;
			return color == null ? -1 : color;
		}

		public void setColor(int rgba) {
			apply(rgba);
		}

		public void setColor(int red, int green, int blue, int alpha) {
			apply(((red & 0xFF) << 24) | ((green & 0xFF) << 16) | ((blue & 0xFF) << 8) | (alpha & 0xFF));
		}

		private void apply(int rgba) {
			final VertexArray old = current(source, index);
			if (old == null) {
				return;
			}
			try {
				final MaterialProperties properties = new MaterialProperties(
						old.materialProperties.shaderType, old.materialProperties.getTexture(), rgba);
				source.set(index, new VertexArray(old, properties));
			} catch (Throwable e) {
				if (!warned) {
					warned = true;
					Mtr3CompatClient.LOGGER.warn("[mtr3compat] 设置模型颜色失败（资源包自定义颜色不生效）", e);
				}
			}
		}
	}
}
