package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.NativeTrainLoader;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectDoubleImmutablePair;
import org.mtr.mapping.holder.Box;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.mtr.mod.resource.ModelPropertiesPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * 临时诊断：车门开合排查用。对 mtr3-compat 原生车辆的每个门部件，记录
 * doorZ/XMultiplier、门叶数量、命中 openDoorways 的门叶数、门叶包围盒、门叶映射到的 doorway。
 * 定位完成后删除本文件与 mixins.json 中的登记。
 */
@Mixin(ModelPropertiesPart.class)
public class DoorDebugMixin {

	private static final Map<String, Long> mtr3compat$last = new HashMap<>();
	private static Field mtr3compat$fNames;
	private static Field mtr3compat$fDoorZ;
	private static Field mtr3compat$fDoorX;
	private static Field mtr3compat$fPartDetails;
	private static Field mtr3compat$fDoorway;
	private static Field mtr3compat$fBox;
	private static Field mtr3compat$fFlipped;
	private static Field mtr3compat$fOptimizedDoor;

	@Inject(method = "render(Lorg/mtr/mapping/holder/Identifier;Lorg/mtr/mod/render/StoredMatrixTransformations;Lorg/mtr/mod/data/VehicleExtension;I[IILorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;Z)V", at = @At("HEAD"))
	private void mtr3compat$logDoor(Identifier texture, StoredMatrixTransformations storedMatrixTransformations, VehicleExtension vehicle, int carNumber, int[] scrollingDisplayIndexTracker, int light, ObjectArrayList<ObjectDoubleImmutablePair<Box>> openDoorways, boolean fromResourcePackCreator, CallbackInfo ci) {
		if (vehicle == null || openDoorways == null || openDoorways.isEmpty()) {
			return;
		}
		try {
			if (mtr3compat$fDoorZ == null) {
				mtr3compat$fDoorZ = mtr3compat$field(ModelPropertiesPart.class, "doorZMultiplier");
				mtr3compat$fDoorX = mtr3compat$field(ModelPropertiesPart.class, "doorXMultiplier");
				mtr3compat$fNames = mtr3compat$field(ModelPropertiesPart.class, "names");
				mtr3compat$fPartDetails = mtr3compat$field(ModelPropertiesPart.class, "partDetailsList");
			}
			final double doorZ = mtr3compat$fDoorZ.getDouble(this);
			final double doorX = mtr3compat$fDoorX.getDouble(this);
			if (doorZ == 0 && doorX == 0) {
				return;
			}
			final String vehicleId = vehicle.vehicleExtraData.immutableVehicleCars.isEmpty() ? "" : vehicle.vehicleExtraData.immutableVehicleCars.get(0).getVehicleId();
			if (!NativeTrainLoader.isNative(vehicleId)) {
				return;
			}
			final String key = vehicle.getId() + "#" + carNumber + "#" + System.identityHashCode(this);
			final long now = System.currentTimeMillis();
			if (now - mtr3compat$last.getOrDefault(key, 0L) < 3000) {
				return;
			}
			mtr3compat$last.put(key, now);

			if (mtr3compat$fDoorway == null) {
				mtr3compat$fDoorway = mtr3compat$field(Class.forName("org.mtr.mod.resource.ModelPropertiesPart$PartDetails"), "doorway");
				mtr3compat$fBox = mtr3compat$field(Class.forName("org.mtr.mod.resource.ModelPropertiesPart$PartDetails"), "box");
				mtr3compat$fFlipped = mtr3compat$field(Class.forName("org.mtr.mod.resource.ModelPropertiesPart$PartDetails"), "flipped");
				mtr3compat$fOptimizedDoor = mtr3compat$field(Class.forName("org.mtr.mod.resource.ModelPropertiesPart$PartDetails"), "optimizedModelDoor");
			}

			final ObjectArrayList<?> partDetails = (ObjectArrayList<?>) mtr3compat$fPartDetails.get(this);
			if (partDetails == null || partDetails.isEmpty()) {
				return;
			}
			int matched = 0;
			final StringBuilder sb = new StringBuilder();
			for (int i = 0; i < partDetails.size(); i++) {
				final Object partDetail = partDetails.get(i);
				final Object doorway = mtr3compat$fDoorway.get(partDetail);
				boolean hit = false;
				for (final ObjectDoubleImmutablePair<Box> open : openDoorways) {
					if (open.left().equals(doorway)) {
						hit = true;
						break;
					}
				}
				if (hit) {
					matched++;
				}
				if (i == 0) {
					sb.append(" box=").append(mtr3compat$range((Box) mtr3compat$fBox.get(partDetail)))
							.append(" doorway=").append(doorway == null ? "null" : mtr3compat$range((Box) doorway))
							.append(" hit=").append(hit)
							.append(" flipped=").append(mtr3compat$fFlipped.getBoolean(partDetail))
							.append(" optimizedDoor=").append(mtr3compat$fOptimizedDoor.get(partDetail) != null);
				}
			}

			Mtr3CompatClient.LOGGER.info("[mtr3compat] 门 {} car={}/{} part={} zMult={} xMult={} doorValue={} open={} leaves={} matched={}{}",
					vehicleId, carNumber, vehicle.vehicleExtraData.immutableVehicleCars.size(),
					mtr3compat$fNames.get(this), doorZ, doorX, vehicle.persistentVehicleData.getDoorValue(),
					openDoorways.size(), partDetails.size(), matched, sb);
		} catch (Throwable e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 门诊断失败", e);
		}
	}

	private static Field mtr3compat$field(Class<?> clazz, String name) throws NoSuchFieldException {
		Class<?> current = clazz;
		while (current != null) {
			try {
				final Field field = current.getDeclaredField(name);
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException e) {
				current = current.getSuperclass();
			}
		}
		throw new NoSuchFieldException(name);
	}

	private static String mtr3compat$range(Box box) {
		return String.format("x[%.2f,%.2f] y[%.2f,%.2f] z[%.2f,%.2f]",
				box.getMinXMapped(), box.getMaxXMapped(), box.getMinYMapped(), box.getMaxYMapped(), box.getMinZMapped(), box.getMaxZMapped());
	}
}
