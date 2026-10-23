package com.xm.mtr3compat.client.mixin;

import org.mtr.mod.data.PersistentVehicleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 MTR4 渲染用的平滑轨道进度 smoothedRailProgress。
 * 渲染管线每帧用 getSmoothedRailProgress() 更新该字段并按它摆放车厢，
 * 但脚本 API getRailProgress() 返回的是服务端原始进度，两者在同步瞬间/开门冻结时不一致，
 * 导致西安包 conn.js 用原始进度算出的贯通道偏航角与车厢实际渲染朝向错位（表现为风挡左右大幅偏移）。
 */
@Mixin(PersistentVehicleData.class)
public interface PersistentVehicleDataAccessor {

	@Accessor("smoothedRailProgress")
	double mtr3compat$getSmoothedRailProgress();
}
