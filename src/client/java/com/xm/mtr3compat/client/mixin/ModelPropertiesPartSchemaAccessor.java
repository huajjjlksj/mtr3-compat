package com.xm.mtr3compat.client.mixin;

import org.mtr.mod.generated.resource.ModelPropertiesPartSchema;
import org.mtr.mod.resource.PartType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * ModelPropertiesPart 的 type 字段声明在父类 ModelPropertiesPartSchema（受保护、无 getter），
 * 直接对它做 @Shadow 会失败（父类字段不在目标类里），所以用接口访问器从父类读取。
 */
@Mixin(ModelPropertiesPartSchema.class)
public interface ModelPropertiesPartSchemaAccessor {

	@Accessor("type")
	PartType mtr3compat$getPartType();
}
