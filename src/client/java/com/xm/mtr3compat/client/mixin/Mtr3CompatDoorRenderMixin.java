package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.LegacyDoorMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mod.data.PersistentVehicleData;
import org.mtr.mod.resource.DoorAnimationType;
import org.mtr.mod.resource.PositionDefinitions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 旧版 OBJ 资源的门部件在 writeCache 阶段已把门板坐标烘焙进网格（addTransformation），
 * 镜像条目还额外烘焙了绕 Y 180°。MTR4 的 renderNormal 会再按部件坐标平移一次、镜像件再旋转一次，
 * 于是开门时门板相对车身多偏移一个部件坐标，镜像节还会把烘焙好的镜像抵消。
 * 这里对旧版 OBJ 门部件改为：只平移动画量（不含部件坐标），并跳过重复旋转。
 */
@Mixin(targets = "org.mtr.mod.resource.ModelPropertiesPart")
public class Mtr3CompatDoorRenderMixin {

	/** 该部件是否为本模组注册的旧版 OBJ 门部件（writeCache 的 OBJ 重载 + names 中的门标记）。 */
	@Unique
	private boolean mtr3compat$objDoor = false;

	/** 当前部件的 X 向开门动画量（像素）。 */
	@Unique
	private double mtr3compat$doorAnimationX;

	/** 当前部件的 Z 向开门动画量（像素）。 */
	@Unique
	private double mtr3compat$doorAnimationZ;

	@Inject(
			method = "writeCache(Ljava/util/Map;Lorg/mtr/mod/resource/PositionDefinitions;Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap;Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap;D)V",
			at = @At("HEAD")
	)
	private void mtr3compat$markObjDoor(Map<String, OptimizedModel.ObjModel> nameToObjModels, PositionDefinitions positionDefinitionsObject, Object2ObjectOpenHashMap<?, ?> materialGroups, Object2ObjectOpenHashMap<?, ?> objModels, double modelYOffset, CallbackInfo callbackInfo) {
		mtr3compat$objDoor = LegacyDoorMap.hasMarker(this);
	}

	/**
	 * 记录当前部件的 X 向动画量。该调用只在 shouldRender 为真时发生。
	 */
	@Redirect(method = "lambda$renderNormal$25", at = @At(value = "INVOKE", target = "Lorg/mtr/mod/resource/DoorAnimationType;getDoorAnimationX(DZD)D"))
	private double mtr3compat$captureDoorAnimationX(DoorAnimationType doorAnimationType, double multiplier, boolean flipped, double time) {
		mtr3compat$doorAnimationX = doorAnimationType.getDoorAnimationX(multiplier, flipped, time);
		return mtr3compat$doorAnimationX;
	}

	@Redirect(method = "lambda$renderNormal$25", at = @At(value = "INVOKE", target = "Lorg/mtr/mod/data/PersistentVehicleData;getInterpolatedDoorValue(Lorg/mtr/mod/resource/DoorAnimationType;DZDZ)F"))
	private float mtr3compat$captureDoorAnimationZ(PersistentVehicleData persistentVehicleData, DoorAnimationType doorAnimationType, double multiplier, boolean flipped, double overrideValue, boolean opening) {
		final float value = persistentVehicleData.getInterpolatedDoorValue(doorAnimationType, multiplier, flipped, overrideValue, opening);
		mtr3compat$doorAnimationZ = value;
		return value;
	}

	@Redirect(method = "lambda$renderNormal$25", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/mapper/GraphicsHolder;translate(DDD)V"))
	private void mtr3compat$translate(GraphicsHolder graphicsHolder, double x, double y, double z) {
		// shouldRender 为假时 x/y 被置为 Integer.MAX_VALUE，保持上游行为（渲染到极远处）。
		if (mtr3compat$objDoor && Math.abs(x) < 1000 && Math.abs(y) < 1000) {
			graphicsHolder.translate(mtr3compat$doorAnimationX / 16, 0, mtr3compat$doorAnimationZ / 16);
		} else {
			graphicsHolder.translate(x, y, z);
		}
		// 每次平移后清零，保证下一个部件（可能不触发动画求值）从零开始。
		mtr3compat$doorAnimationX = 0;
		mtr3compat$doorAnimationZ = 0;
	}

	@Redirect(method = "lambda$renderNormal$25", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/mapper/GraphicsHolder;rotateYDegrees(F)V"))
	private void mtr3compat$rotate(GraphicsHolder graphicsHolder, float degrees) {
		if (!mtr3compat$objDoor) {
			graphicsHolder.rotateYDegrees(degrees);
		}
	}
}
