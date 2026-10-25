package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.core.util.model.ModelJS;
import com.xm.mtr3compat.client.jcm.LegacyModelCluster;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 给 JCM 的 ModelJS 补上 MTR3/NTE 脚本用的 ModelCluster 属性
 * （uploadedOpaqueParts / uploadedTranslucentParts），见 {@link LegacyModelCluster}。
 */
@Mixin(value = ModelJS.class, remap = false)
public class ModelJSClusterMixin {

	public Object getUploadedOpaqueParts() {
		return LegacyModelCluster.opaque(((ModelJS) (Object) this).getUnderlyingModel());
	}

	public Object getUploadedTranslucentParts() {
		return LegacyModelCluster.translucent(((ModelJS) (Object) this).getUnderlyingModel());
	}
}
