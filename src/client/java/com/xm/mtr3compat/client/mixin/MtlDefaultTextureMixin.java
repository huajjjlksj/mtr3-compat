package com.xm.mtr3compat.client.mixin;

import org.mtr.mod.resource.ModelResourceLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * MTR3 资源包的 MTL 常把主贴图写成「带目录的 default.png」（如 {@code map_Kd crt3/default.png}），
 * 表示「使用车辆本身的 texture_id」。NTE 按文件名后缀匹配（VertArrays.replaceTexture），
 * 而 MTR4 只认裸 {@code default.png}（ModelResourceLoader 里的 StringUtils.equals），
 * 于是带目录的写法会被当成真实文件去加载，文件不存在 → 该材质紫黑。
 * 这里把 MTL 内容中的 {@code 任意目录/default.png} 归一化为 {@code default.png}，恢复 NTE 语义。
 */
@Mixin(ModelResourceLoader.class)
public class MtlDefaultTextureMixin {

	private static final Pattern DIR_DEFAULT_TEXTURE = Pattern.compile("(?im)^([ \\t]*map_Kd[ \\t]+)(?:[^ \\t\\r\\n]*/)?(default\\.png[ \\t\\r]*)$");

	@ModifyArg(method = "loadModel", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/mapper/OptimizedModel$ObjModel;loadModel"), index = 1)
	private static Function<String, String> mtr3compat$normalizeDefaultTexture(Function<String, String> mtlFunction) {
		return mtlString -> {
			final String content = mtlFunction.apply(mtlString);
			return content == null ? null : DIR_DEFAULT_TEXTURE.matcher(content).replaceAll("$1$2");
		};
	}
}
