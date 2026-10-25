package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.core.util.ScriptVector3f;
import org.mtr.mapping.holder.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ANTE（joml / com.mojang.math）的 Vector3f.rotX/rotZ 是标准右手旋转；
 * JCM 的 ScriptVector3f 走 Minecraft Vec3.rotateX/rotateZ，方向与标准相反
 * （Vec3 的 rotateX/rotateZ 相当于标准旋转的 -angle；rotateY 方向一致，无需处理）。
 * gzmtr 等 MTR3 包的 getCubeVertices 三点定屏依赖标准方向，这里补回。
 */
@Mixin(ScriptVector3f.class)
public class ScriptVector3fRotationMixin {

	@Shadow
	private Vector3d impl;

	@Inject(method = "rotX", at = @At("HEAD"), cancellable = true, require = 0)
	private void mtr3compat$rotX(float rad, CallbackInfoReturnable<ScriptVector3f> cir) {
		impl = impl.rotateX(-rad);
		cir.setReturnValue((ScriptVector3f) (Object) this);
	}

	@Inject(method = "rotZ", at = @At("HEAD"), cancellable = true, require = 0)
	private void mtr3compat$rotZ(float rad, CallbackInfoReturnable<ScriptVector3f> cir) {
		impl = impl.rotateZ(-rad);
		cir.setReturnValue((ScriptVector3f) (Object) this);
	}
}
