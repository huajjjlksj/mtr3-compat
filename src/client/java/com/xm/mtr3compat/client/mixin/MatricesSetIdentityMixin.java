package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.core.util.Matrices;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Stack;

/**
 * NTE 的 Matrices 有 setIdentity()（把变换栈重置为单位矩阵），JCM 移植时漏掉了。
 * 部分 NTE 脚本每次计算车门矩阵前都会调用它，缺方法会让整个 render() 抛异常、
 * 模型一个都画不出来（只剩 MTR 原生风挡）。这里补上等价实现：清空栈并压入一个空变换。
 */
@Mixin(Matrices.class)
public class MatricesSetIdentityMixin {

	@Shadow
	@Final
	private Stack<StoredMatrixTransformations> storedMatrixTransformations;

	public void setIdentity() {
		storedMatrixTransformations.clear();
		storedMatrixTransformations.push(new StoredMatrixTransformations());
	}
}
