package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.MtrClientDataCompat;
import org.mtr.core.data.ClientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端数据每次同步后重建 NTE DATA_CACHE 替身的派生映射（跟随 sync，不做每帧重建）。
 */
@Mixin(ClientData.class)
public class ClientDataSyncMixin {

	@Inject(method = "sync()V", at = @At("TAIL"))
	private void mtr3compat$rebuildDataCache(CallbackInfo ci) {
		MtrClientDataCompat.rebuild();
	}
}
