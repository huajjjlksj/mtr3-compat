package com.xm.mtr3compat.client.mixin;

import com.lx862.mtrscripting.mod.impl.mtr.vehicle.NTETrainWrapper;
import com.xm.mtr3compat.client.Mtr3CompatClient;
import org.mtr.mod.render.PositionAndRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 诊断用（临时）：打印 JCM 判定出的两侧车门可开启状态（脚本里的小灯就靠它）。
 * 大连 LCD 的本侧/对侧开门小灯 = train.doorLeftOpen[] / doorRightOpen[]，
 * 这两个值在 VehicleWrapper 构造时由 MTRUtil.canOpenDoors 扫描站台方块得出。
 */
@Mixin(value = NTETrainWrapper.class, remap = false)
public class TrainLampDebugMixin {

	private static final Map<String, String> LAST = new HashMap<>();
	private static final Field POS_FIELD = findPosField();

	@Inject(method = "getThisRoutePlatformsNextIndex", at = @At("HEAD"), require = 0)
	private void mtr3compat$logDoors(CallbackInfoReturnable<Integer> cir) {
		try {
			final NTETrainWrapper self = (NTETrainWrapper) (Object) this;
			final String vehicleId = self.getVehicleId(0);
			if (vehicleId == null) {
				return;
			}
			final double doorValue = self.getDoorValue();
			if (doorValue <= 0) {
				return;
			}
			final StringBuilder left = new StringBuilder();
			final StringBuilder right = new StringBuilder();
			for (int i = 0; i < self.doorLeftOpen.length; i++) {
				left.append(self.doorLeftOpen[i] ? '1' : '0');
				right.append(self.doorRightOpen[i] ? '1' : '0');
			}
			final String signature = String.format(Locale.ROOT, "%.1f|%s|%s", doorValue, left, right);
			if (signature.equals(LAST.get(vehicleId))) {
				return;
			}
			LAST.put(vehicleId, signature);
			Mtr3CompatClient.LOGGER.info("[mtr3compat] door id={} dv={} opn={} L={} R={} w={} len={} pos=[{}]",
					vehicleId, String.format(Locale.ROOT, "%.2f", doorValue), self.isDoorOpening(), left, right,
					fmt(self.getWidth(0)), fmt(self.getLength(0)), positions((Object) self));
		} catch (Throwable e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] door 诊断失败", e);
		}
	}

	private static String positions(Object wrapper) {
		if (POS_FIELD == null) {
			return "";
		}
		try {
			final Object value = POS_FIELD.get(wrapper);
			if (!(value instanceof List)) {
				return "";
			}
			final StringBuilder sb = new StringBuilder();
			final List<?> list = (List<?>) value;
			for (int i = 0; i < Math.min(list.size(), 3); i++) {
				final Object entry = list.get(i);
				if (!(entry instanceof PositionAndRotation)) {
					continue;
				}
				final PositionAndRotation p = (PositionAndRotation) entry;
				if (sb.length() > 0) {
					sb.append(' ');
				}
				sb.append(String.format(Locale.ROOT, "(%d:%.2f,%.2f,%.2f,yaw=%.2f)", i, p.position.x, p.position.y, p.position.z, p.yaw));
			}
			return sb.toString();
		} catch (Throwable e) {
			return "";
		}
	}

	private static Field findPosField() {
		try {
			final Field field = Class.forName("com.lx862.mtrscripting.mod.impl.mtr.vehicle.VehicleWrapper")
					.getDeclaredField("posAndRotations");
			field.setAccessible(true);
			return field;
		} catch (Throwable e) {
			return null;
		}
	}

	private static String fmt(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
