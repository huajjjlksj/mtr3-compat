package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.MtrClientDataCompat;
import org.mtr.core.data.Data;
import org.mtr.core.operation.DataResponse;
import org.mtr.mod.client.MinecraftClientData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MTR4 的 DataResponse.write() 用请求里的 depotsToKeep 把客户端车厂裁剪成本次请求范围内的集合，
 * 车厂离玩家太远，附近窗口永远不带它，脚本的 MTRClientData.DATA_CACHE.sidingIdToDepot 会拿到 undefined。
 * 这里在 write() 完成（addAll + 裁剪 + sync）之后把已知车厂加回客户端数据并重新 sync。
 * 响应里的 depots 字段声明在父类 DataResponseSchema 中，@Shadow 定位不到，改由兼容层反射读取。
 */
@Mixin(DataResponse.class)
public class DataResponseStickyDepotMixin {

	@Shadow
	@Final
	private Data data;

	/**
	 * write() 会 addAll 到客户端数据后调用 Data.sync()，而 sync() 内的 mapAreasAndSavedRails
	 * 会把每个车厂的 savedRails 清空、只用“客户端当前存在的侧线”重填。
	 * 所以在写进客户端之前先抓一次：这时车厂的 savedRails 仍是服务端的完整列表，
	 * 用它建立“侧线 -> 车厂”映射，就不会随附近窗口的裁剪丢失。
	 */
	@Inject(method = "write()V", at = @At("HEAD"))
	private void mtr3compat$captureIncomingDepots(CallbackInfo ci) {
		if (data instanceof MinecraftClientData && data == MinecraftClientData.getInstance()) {
			MtrClientDataCompat.captureIncomingDepots((DataResponse) (Object) this);
		}
	}

	/** 写完后把被 depotsToKeep 裁掉的已知车厂加回去。 */
	@Inject(method = "write()V", at = @At("RETURN"))
	private void mtr3compat$stickyDepots(CallbackInfo ci) {
		// 只处理主客户端数据集；Dashboard 用的是另一个 MinecraftClientData 实例，不要混进累积表。
		if (data instanceof MinecraftClientData && data == MinecraftClientData.getInstance()) {
			MtrClientDataCompat.captureAndRestoreDepots((MinecraftClientData) data);
		}
	}
}
