package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.core.util.Matrices;
import com.lx862.mtrscripting.core.util.ScriptVector3f;
import com.lx862.mtrscripting.core.util.render.QuadDrawCall;
import com.lx862.mtrscripting.core.util.render.ScriptRenderManager;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.ante.ConnModelRegistry;
import com.xm.mtr3compat.client.ante.ConnStretchRegistry;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.holder.World;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.render.DynamicVehicleModel;
import org.mtr.mod.render.PositionAndRotation;
import org.mtr.mod.render.RenderVehicles;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.mtr.mod.resource.VehicleResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * drawConnStretchTexture 的渲染落地：
 * - render(HEAD)：推进帧号并清理过期登记；
 * - lambda$render$12(HEAD)（每节车厢渲染回调）：若该车厢登记了拉伸纹理，
 *   取 MTR 已算好的前一端面 4 角点（PreviousConnectionPositions，世界坐标），
 *   变换到本车局部坐标系，按 ANTE commitConnImmediate 的顶点/UV 顺序提交 8 个四边形
 *   （4 外侧面 EXTERIOR + 4 内侧面 INTERIOR，UV 各占贴图一半）。
 */
@Mixin(RenderVehicles.class)
public class ConnStretchRenderMixin {

	private static Field[] mtr3compat$prevFields;
	private static boolean mtr3compat$loggedFail = false;

	@Inject(method = "render(JLorg/mtr/mapping/holder/Vector3d;)V", at = @At("HEAD"))
	private static void mtr3compat$nextFrame(long millisElapsed, Vector3d cameraShakeOffset, CallbackInfo ci) {
		ConnStretchRegistry.nextFrame();
		ConnStretchRegistry.prune();
		ConnModelRegistry.nextFrame();
		ConnModelRegistry.prune();
	}

	@Inject(method = "lambda$render$12", at = @At("HEAD"))
	private static void mtr3compat$drawConnStretch(StoredMatrixTransformations stored, VehicleExtension vehicle,
			int ignoredIndex, int[] ignoredCars, PositionAndRotation carPose,
			ObjectArrayList<?> ignoredList, boolean ignoredFlag, ObjectArrayList<?> prevConnList,
			ObjectArrayList<?> prevBarrierList, VehicleResource resource, PositionAndRotation connPose,
			Vector3d ignoredVec, ObjectObjectImmutablePair<?, ?> carPair, double ignoredDouble, int carIndex,
			DynamicVehicleModel model, CallbackInfo ci) {
		try {
			final ConnStretchRegistry.Entry entry = ConnStretchRegistry.get(vehicle.getHexId(), carIndex);
			final ConnModelRegistry.Entry connEntry = ConnModelRegistry.get(vehicle.getHexId(), carIndex);
			if (entry == null && connEntry == null) {
				return;
			}
			if (carIndex < 0 || carIndex >= prevConnList.size()) {
				return;
			}
			final Object prev = prevConnList.get(carIndex);
			if (prev == null) {
				return;
			}
			final Field[] fields = mtr3compat$fields();
			if (fields == null) {
				return;
			}
			final Vector[] pv = new Vector[4];
			for (int i = 0; i < 4; i++) {
				final Object o = fields[i].get(prev);
				if (!(o instanceof Vector)) {
					return;
				}
				pv[i] = (Vector) o;
			}
			final VehicleCar car = (VehicleCar) carPair.left();
			if (car == null) {
				return;
			}
			// 世界 -> 本车局部（先平移，再反旋转 yaw/pitch）
			final Vector cp = carPose.position;
			final Vector[] lc = new Vector[4];
			double zSum = 0;
			for (int i = 0; i < 4; i++) {
				lc[i] = pv[i].add(-cp.x, -cp.y, -cp.z).rotateY(-carPose.yaw).rotateX(-carPose.pitch);
				zSum += lc[i].z;
			}
			final double faceZ = (zSum >= 0 ? 1 : -1) * car.getLength() / 2.0;
			final ScriptRenderManager manager = new ScriptRenderManager();
			if (entry != null) {
				final Vector[] tc = new Vector[4];
				for (int i = 0; i < 4; i++) {
					tc[i] = new Vector(lc[i].x, lc[i].y, faceZ);
				}
				// 外侧 4 面（UV 左半）
				mtr3compat$quad(manager, entry.texture, "EXTERIOR", 0f, 0f, 0.5f, 0.5f, tc[1], lc[2], lc[3], tc[0]);
				mtr3compat$quad(manager, entry.texture, "EXTERIOR", 0f, 0f, 0.5f, 0.5f, lc[1], tc[2], tc[3], lc[0]);
				mtr3compat$quad(manager, entry.texture, "EXTERIOR", 0f, 0f, 0.5f, 0.5f, lc[2], tc[1], tc[2], lc[1]);
				mtr3compat$quad(manager, entry.texture, "EXTERIOR", 0f, 0f, 0.5f, 0.5f, lc[0], tc[3], tc[0], lc[3]);
				// 内侧 4 面（UV 右半，全亮）
				mtr3compat$quad(manager, entry.texture, "INTERIOR", 0.5f, 0f, 1f, 0.5f, tc[2], lc[1], lc[0], tc[3]);
				mtr3compat$quad(manager, entry.texture, "INTERIOR", 0.5f, 0f, 1f, 0.5f, lc[2], tc[1], tc[0], lc[3]);
				mtr3compat$quad(manager, entry.texture, "INTERIOR", 0f, 0.5f, 0.5f, 1f, lc[1], tc[2], tc[1], lc[2]);
				mtr3compat$quad(manager, entry.texture, "INTERIOR", 0.5f, 0.5f, 1f, 1f, lc[3], tc[0], tc[3], lc[0]);
			}
			if (connEntry != null) {
				// 贯通道模型以关节为原点，平移到本车端面后按脚本给的姿态绘制
				final Matrices matrices = connEntry.matrices == null ? new Matrices() : connEntry.matrices.copy();
				matrices.translate(0, 0, (float) faceZ);
				manager.drawModel(connEntry.model, matrices);
			}
			final World world = World.cast(MinecraftClient.getInstance().getWorldMapped());
			if (world == null) {
				return;
			}
			manager.invoke(world, stored, Direction.NORTH, carPose.light);
		} catch (Throwable t) {
			if (!mtr3compat$loggedFail) {
				mtr3compat$loggedFail = true;
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] drawConnStretchTexture 渲染失败（后续同类错误不再记录）", t);
			}
		}
	}

	private static void mtr3compat$quad(ScriptRenderManager manager, Identifier texture, String renderType,
			float u1, float v1, float u2, float v2, Vector a, Vector b, Vector c, Vector d) {
		manager.draw(QuadDrawCall.create()
				.quad(mtr3compat$vec(a), mtr3compat$vec(b), mtr3compat$vec(c), mtr3compat$vec(d))
				.uv(u1, v1, u2, v2)
				.texture(texture)
				.renderType(renderType));
	}

	private static ScriptVector3f mtr3compat$vec(Vector v) {
		return new ScriptVector3f((float) v.x, (float) v.y, (float) v.z);
	}

	private static Field[] mtr3compat$fields() {
		Field[] fields = mtr3compat$prevFields;
		if (fields != null) {
			return fields;
		}
		try {
			final Class<?> cls = RenderVehicles.PreviousConnectionPositions.class;
			fields = new Field[4];
			for (int i = 0; i < 4; i++) {
				fields[i] = cls.getDeclaredField("position" + (i + 1));
				fields[i].setAccessible(true);
			}
			mtr3compat$prevFields = fields;
			return fields;
		} catch (Throwable t) {
			if (!mtr3compat$loggedFail) {
				mtr3compat$loggedFail = true;
				Mtr3CompatClient.LOGGER.warn("[mtr3compat] PreviousConnectionPositions 字段反射失败", t);
			}
			return null;
		}
	}
}
