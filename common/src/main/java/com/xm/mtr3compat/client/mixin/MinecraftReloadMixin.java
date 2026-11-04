package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.Mtr3CompatClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/** 记录原版是否已自行拉起重载，供原版资源包界面关闭时判断是否需要模组补一次。 */
@Mixin(Minecraft.class)
public class MinecraftReloadMixin {

	@Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
	private void mtr3compat$markVanillaReload(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		Mtr3CompatClient.vanillaReloaded = true;
	}
}
