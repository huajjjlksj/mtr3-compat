package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.core.primitive.ScriptInstance;
import com.lx862.mtrscripting.core.primitive.UniqueKey;
import com.xm.mtr3compat.client.TrainScriptSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/** 脚本实例取用时（首次渲染前）补入列车级入口，使 createTrain/renderTrain/disposeTrain 参与调用。 */
@Mixin(targets = "com.lx862.mtrscripting.core.ScriptInstanceManager", remap = false)
public class ScriptInstanceManagerMixin {

	@Inject(method = "getInstance(Lcom/lx862/mtrscripting/core/primitive/UniqueKey;Ljava/util/function/Supplier;)Lcom/lx862/mtrscripting/core/primitive/ScriptInstance;", at = @At("RETURN"), remap = false)
	private void mtr3compat$addTrainEntries(UniqueKey key, Supplier<?> supplier, CallbackInfoReturnable<ScriptInstance<?>> callback) {
		TrainScriptSupport.ensureTrainEntries(callback.getReturnValue());
	}
}
