package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.ModelCache;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.NativeTrainLoader;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mod.resource.ModelResourceLoader;
import org.mtr.mod.resource.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MTR3 资源包的 OBJ 解析缓存。
 * <p>
 * MTR4 会按编组（第几节车厢 × 总节数）反复走到 {@code ModelResourceLoader.loadModel}，
 * 大型 OBJ（CRH2C 约 186MB/个）因此被重复读取与解析。这里按（模型路径 + 贴图标识 + flipV）缓存首次解析结果，
 * 命中时只做浅拷贝即可。
 * <p>
 * 仅当 ResourceProvider 是本模组为勾选资源包提供的实例时生效；未勾选的资源包照旧走 MTR4 原有加载。
 */
@Mixin(value = ModelResourceLoader.class, remap = false)
public class ModelResourceLoaderCacheMixin {

	private static boolean mtr3compat$loggedHit;

	@Inject(method = "loadModel(Ljava/lang/String;Lorg/mtr/mapping/holder/Identifier;ZLorg/mtr/mod/resource/ResourceProvider;)Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/Object2ObjectAVLTreeMap;",
			at = @At("HEAD"), cancellable = true)
	private static void mtr3compat$loadFromCache(String modelResource, Identifier modelLocation, boolean flipV, ResourceProvider provider, CallbackInfoReturnable<Object2ObjectAVLTreeMap<String, OptimizedModel.ObjModel>> cir) {
		if (!NativeTrainLoader.isNativeResourceProvider(provider)) {
			return;
		}
		final Object2ObjectAVLTreeMap<String, OptimizedModel.ObjModel> cached = ModelCache.copyParsed(ModelCache.parseKey(modelResource, modelLocation, flipV));
		if (cached != null) {
			if (!mtr3compat$loggedHit) {
				mtr3compat$loggedHit = true;
				Mtr3CompatClient.LOGGER.info("[mtr3compat] 解析缓存已生效，重复编组不再重新解析 OBJ");
			}
			cir.setReturnValue(cached);
		}
	}

	@Inject(method = "loadModel(Ljava/lang/String;Lorg/mtr/mapping/holder/Identifier;ZLorg/mtr/mod/resource/ResourceProvider;)Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/Object2ObjectAVLTreeMap;",
			at = @At("RETURN"))
	private static void mtr3compat$storeCache(String modelResource, Identifier modelLocation, boolean flipV, ResourceProvider provider, CallbackInfoReturnable<Object2ObjectAVLTreeMap<String, OptimizedModel.ObjModel>> cir) {
		if (!NativeTrainLoader.isNativeResourceProvider(provider)) {
			return;
		}
		ModelCache.storeParsed(ModelCache.parseKey(modelResource, modelLocation, flipV), cir.getReturnValue());
	}
}
