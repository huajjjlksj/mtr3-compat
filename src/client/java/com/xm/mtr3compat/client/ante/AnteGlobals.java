package com.xm.mtr3compat.client.ante;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.lx862.mtrscripting.lib.org.mozilla.javascript.BaseFunction;
import com.lx862.mtrscripting.lib.org.mozilla.javascript.Context;
import com.lx862.mtrscripting.lib.org.mozilla.javascript.NativeArray;
import com.lx862.mtrscripting.lib.org.mozilla.javascript.NativeJavaClass;
import com.lx862.mtrscripting.lib.org.mozilla.javascript.NativeObject;
import com.lx862.mtrscripting.lib.org.mozilla.javascript.Scriptable;
import com.xm.mtr3compat.client.MtrClientDataCompat;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 在 JCM parseScriptEvent 监听（勾选包解析窗口内）里覆盖绑定 ANTE 应用层全局。
 * 绑定清单照抄 ANTE ScriptHolderClient 的注入集；JCM 已原生提供且语义一致的全局
 * （include/print/Resources/GraphicsTexture/StateTracker/Networking/Files/Matrices/Vector3f 等）不在此覆盖。
 */
public final class AnteGlobals {

	private AnteGlobals() {
	}

	public static void bind(String scriptName, Context ctx, Scriptable scope) {
		scope.put("SIDE", scope, "client");
		scope.put("MOD_ENV", scope, "cn");
		scope.put("foundMadParticle", scope, "false");

		scope.put("MTRClientData", scope, new NativeJavaClass(scope, MtrClientDataCompat.class));
		scope.put("MinecraftClient", scope, new NativeJavaClass(scope, MinecraftClientCompat.class));
		scope.put("GlobalRegister", scope, new NativeJavaClass(scope, GlobalRegister.class));
		scope.put("WrappedEntity", scope, new NativeJavaClass(scope, WrappedEntity.class));
		scope.put("ComponentUtil", scope, new NativeJavaClass(scope, ComponentUtil.class));
		scope.put("OrderedMap", scope, new NativeJavaClass(scope, OrderedMap.class));
		scope.put("PlacementOrder", scope, new NativeJavaClass(scope, OrderedMap.PlacementOrder.class));
		scope.put("SoundHelper", scope, new NativeJavaClass(scope, SoundHelperCompat.class));
		scope.put("ParticleHelper", scope, new NativeJavaClass(scope, ParticleHelperCompat.class));
		scope.put("TickableSound", scope, new NativeJavaClass(scope, TickableSoundCompat.class));
		scope.put("Matrix4f", scope, new NativeJavaClass(scope, Matrix4fCompat.class));
		scope.put("UtilitiesClient", scope, new NativeJavaClass(scope, UtilitiesClientCompat.class));
		scope.put("Component", scope, new NativeJavaClass(scope, Component.class));
		scope.put("Optional", scope, new NativeJavaClass(scope, Optional.class));
		scope.put("asJavaArray", scope, new AsJavaArrayFunction());

		try {
			scope.put("IBlock", scope, new NativeJavaClass(scope, Class.forName("org.mtr.mod.block.IBlock")));
			scope.put("IDrawing", scope, new NativeJavaClass(scope, Class.forName("org.mtr.mod.client.IDrawing")));
		} catch (ClassNotFoundException e) {
			// MTR 版本不含该类时跳过（不影响其余绑定）
		}

		final JsonObject config = ConfigCapture.LAST.get();
		if (config != null) {
			scope.put("CONFIG_INFO", scope, toRhino(ctx, scope, config));
		}
	}

	/** Gson JsonObject -> Rhino 对象树（ANTE 的 CONFIG_INFO 是脚本对应条目的 config 字段）。 */
	private static Object toRhino(Context ctx, Scriptable scope, JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (element.isJsonObject()) {
			final NativeObject obj = (NativeObject) ctx.newObject(scope);
			for (final Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				obj.put(entry.getKey(), obj, toRhino(ctx, scope, entry.getValue()));
			}
			return obj;
		}
		if (element.isJsonArray()) {
			final JsonArray src = element.getAsJsonArray();
			final NativeArray arr = (NativeArray) ctx.newArray(scope, src.size());
			for (int i = 0; i < src.size(); i++) {
				arr.put(i, arr, toRhino(ctx, scope, src.get(i)));
			}
			return arr;
		}
		final JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (primitive.isBoolean()) {
			return primitive.getAsBoolean();
		}
		if (primitive.isNumber()) {
			return primitive.getAsDouble();
		}
		return primitive.getAsString();
	}

	/** 全局函数 asJavaArray(jsArray, JavaType)：JS 数组/List -> Java 类型数组（Rhino 1.9.0 无 Java.to）。 */
	public static class AsJavaArrayFunction extends BaseFunction {
		@Override
		public Object call(Context ctx, Scriptable scope, Scriptable thisObj, Object[] args) {
			if (args.length < 2) {
				throw new IllegalArgumentException("asJavaArray(array, clazz) requires 2 arguments");
			}
			return JavaArrayUtil.asJavaArray(args[0], args[1]);
		}

		@Override
		public String getFunctionName() {
			return "asJavaArray";
		}
	}
}
