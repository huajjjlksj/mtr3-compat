package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.MtrClientDataCompat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MTR4 客户端只同步玩家附近的数据；且脚本用的 Stop 对象来自 JCM 数据集。
 * 这里每帧驱动全量数据请求与数据集变化后的 DATA_CACHE 重建。
 */
@Mixin(Minecraft.class)
public class ClientTickDataCacheMixin {

	@Inject(method = "tick()V", at = @At("HEAD"))
	private void mtr3compat$ensureDataCache(CallbackInfo ci) {
		MtrClientDataCompat.tickEnsureData();
	}
}
