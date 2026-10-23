package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.core.util.model.RawModelJS;
import org.spongepowered.asm.mixin.Mixin;

/**
 * NTE 的 RawModel 有 sourceLocation 字段，脚本会写它（如西安包 train_main.js#228 的
 * clonedModel.sourceLocation = null，用于清掉克隆模型的来源标记），JCM 移植时漏掉了该字段，
 * 脚本直接报 "has no public instance field or method named sourceLocation" 并中断整个车辆脚本。
 * 这里补一个同名公开字段，语义与原版一致（仅作来源标记，不参与渲染）。
 */
@Mixin(RawModelJS.class)
public class RawModelSourceLocationMixin {

	public String sourceLocation;
}
