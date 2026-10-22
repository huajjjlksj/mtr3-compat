package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.Mtr3CompatConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.ResourceManagerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.util.function.Consumer;

/**
 * 核心拦截点：MTR4 的 CustomResourceLoader.reload 与 JCM 的 parseCustom 都经
 * ResourceManagerHelper.readAllResources 枚举启用包中的 mtr_custom_resources.json，
 * 在此按勾选（MTR3 模式）的包过滤即可同时拦截两者。
 */
@Mixin(ResourceManagerHelper.class)
public class ResourceManagerHelperMixin {

	@Inject(method = "readAllResources(Lorg/mtr/mapping/holder/Identifier;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
	private static void mtr3compat$filterInterceptedPacks(Identifier identifier, Consumer<InputStream> consumer, CallbackInfo ci) {
		if (!Mtr3CompatConfig.hasIntercepted()) {
			return;
		}
		ci.cancel();
		if (!(Minecraft.getInstance().getResourceManager() instanceof ReloadableResourceManager resourceManager)) {
			return;
		}
		final ResourceLocation location = new ResourceLocation(identifier.getNamespace(), identifier.getPath());
		for (final PackResources pack : resourceManager.listPacks().toList()) {
			if (Mtr3CompatConfig.isIntercepted(pack.packId())) {
				continue;
			}
			try {
				final IoSupplier<InputStream> supplier = pack.getResource(PackType.CLIENT_RESOURCES, location);
				if (supplier == null) {
					continue;
				}
				try (final InputStream inputStream = supplier.get()) {
					consumer.accept(inputStream);
				}
			} catch (Exception e) {
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] readAllResources 读取失败: " + pack.packId() + " " + location, e);
			}
		}
	}
}
