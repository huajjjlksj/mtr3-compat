package org.mtr.mod.resource;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.OptimizedModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 旧版 OBJ 资源的镜像部件在烘焙网格时已按 {@code addTransformation} 做过绕 Y 180°，
 * 开门渲染再转一次会把镜像抵消，因此只对这类部件跳过旋转、改为把动画量按镜像取反。
 * 常规 bbmodel 资源保持 MTR4 原行为。
 */
@Mixin(ModelPropertiesPart.class)
public class Mtr3CompatDoorRenderMixin {

	@Shadow
	@Final
	private ObjectArrayList<Object> partDetailsList;

	private static boolean mtr3compat$currentObjDoor;
	private static double mtr3compat$translateX;
	private static double mtr3compat$translateZ;

	@Inject(
			method = "writeCache(Ljava/util/Map;Lorg/mtr/mod/resource/PositionDefinitions;Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap;Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap;D)V",
			at = @At("RETURN")
	)
	private void mtr3compat$markObjParts(Map<String, OptimizedModel.ObjModel> nameToObjModels, PositionDefinitions positionDefinitionsObject, Object2ObjectOpenHashMap<?, ?> a, Object2ObjectOpenHashMap<?, ?> b, double modelYOffset, CallbackInfo callbackInfo) {
		partDetailsList.forEach(partDetails -> ((Mtr3CompatObjDoorFlag) partDetails).mtr3compat$setObjDoor());
	}

	@Inject(method = "lambda$renderNormal$25", at = @At("HEAD"))
	private void mtr3compat$capturePart(Object partDetails, CallbackInfo callbackInfo) {
		mtr3compat$currentObjDoor = partDetails instanceof Mtr3CompatObjDoorFlag && ((Mtr3CompatObjDoorFlag) partDetails).mtr3compat$isObjDoor();
	}

	@Redirect(method = "lambda$renderNormal$25", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/mapper/GraphicsHolder;translate(DDD)V"))
	private void mtr3compat$translate(GraphicsHolder graphicsHolder, double x, double y, double z) {
		mtr3compat$translateX = x;
		mtr3compat$translateZ = z;
		graphicsHolder.translate(x, y, z);
	}

	@Redirect(method = "lambda$renderNormal$25", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/mapper/GraphicsHolder;rotateYDegrees(F)V"))
	private void mtr3compat$rotate(GraphicsHolder graphicsHolder, float degrees) {
		if (mtr3compat$currentObjDoor) {
			graphicsHolder.translate(-2 * mtr3compat$translateX, 0, -2 * mtr3compat$translateZ);
		} else {
			graphicsHolder.rotateYDegrees(degrees);
		}
	}
}
