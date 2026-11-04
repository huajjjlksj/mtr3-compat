package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.AutoConsist;
import com.xm.mtr3compat.client.NativeTrainLoader;
import org.mtr.core.data.Siding;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.resource.VehicleResource;
import org.mtr.mod.screen.DashboardList;
import org.mtr.mod.screen.DashboardListItem;
import org.mtr.mod.screen.DashboardListSelectorScreen;
import org.mtr.mod.screen.VehicleSelectorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * 选车列表：
 * <ul>
 *   <li>只过滤左侧“可选用”列表——MTR3 自动编组开启时隐藏 MTR4 的车头/车厢/车尾变体，关闭时隐藏本模组注册的单车厢条目；
 *       右侧已选区域、列车建模与侧线数据都不受影响，因此不需要注销车型也不需要重载；</li>
 *   <li>选择本模组原生车型后，按轨长自动铺满整列编组。</li>
 * </ul>
 */
@Mixin(value = VehicleSelectorScreen.class, remap = false)
public abstract class VehicleSelectorScreenMixin {

	@Shadow
	private Siding siding;

	/** 父类 DashboardListSelectorScreen 的字段：可选用列表与全部车型条目。 */
	private static Field mtr3compat$availableListField;
	private static Field mtr3compat$allDataField;
	/** 选车列表里每项都对应一个 VehicleResource（私有内部类字段）。 */
	private static Field mtr3compat$vehicleResourceField;

	@Inject(method = "updateList()V", at = @At("TAIL"))
	private void mtr3compat$autoConsist(CallbackInfo ci) {
		if (!NativeTrainLoader.isAutoConsistMode()) {
			return;
		}
		final ObjectArrayList<VehicleCar> cars = siding.getVehicleCars();
		if (cars.size() != 1) {
			return;
		}
		final ObjectArrayList<VehicleCar> expanded = AutoConsist.buildConsist(siding, cars.get(0).getVehicleId());
		if (expanded == null || expanded.size() <= 1) {
			return;
		}
		siding.setVehicleCars(expanded);
		InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(new UpdateDataRequest(MinecraftClientData.getDashboardInstance()).addSiding(siding)));
	}

	@Inject(method = "updateList()V", at = @At("TAIL"))
	@SuppressWarnings("unchecked")
	private void mtr3compat$filterAvailable(CallbackInfo ci) {
		try {
			final Field availableListField = mtr3compat$getAvailableListField();
			final Field allDataField = mtr3compat$getAllDataField();
			if (availableListField == null || allDataField == null) {
				return;
			}
			final DashboardList availableList = (DashboardList) availableListField.get(this);
			final ObjectImmutableList<DashboardListItem> allData = (ObjectImmutableList<DashboardListItem>) allDataField.get(this);
			final boolean autoConsist = NativeTrainLoader.isAutoConsistMode();
			final ObjectArrayList<DashboardListItem> filtered = new ObjectArrayList<>();
			for (final DashboardListItem item : allData) {
				if (mtr3compat$shouldHide(item, autoConsist)) {
					continue;
				}
				filtered.add(item);
			}
			// 本界面的可选用列表包含全部 allData（可重复选择），只在真的有隐藏项时重建，避免多余的滚动重置。
			if (filtered.size() != allData.size()) {
				availableList.setData(filtered, false, false, false, false, true, false);
			}
		} catch (Exception e) {
			// 反射失败时退回原版列表，不影响选车。
		}
	}

	private static boolean mtr3compat$shouldHide(DashboardListItem item, boolean autoConsist) {
		final String vehicleId = mtr3compat$vehicleId(item);
		if (vehicleId == null) {
			return false;
		}
		return autoConsist ? NativeTrainLoader.isMtr4Variation(vehicleId) : NativeTrainLoader.isNative(vehicleId);
	}

	private static String mtr3compat$vehicleId(DashboardListItem item) {
		try {
			Field field = mtr3compat$vehicleResourceField;
			if (field == null) {
				field = item.getClass().getDeclaredField("vehicleResource");
				field.setAccessible(true);
				mtr3compat$vehicleResourceField = field;
			}
			final Object resource = field.get(item);
			return resource instanceof VehicleResource ? ((VehicleResource) resource).getId() : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static Field mtr3compat$getAvailableListField() {
		if (mtr3compat$availableListField == null) {
			mtr3compat$availableListField = mtr3compat$findField("availableList");
		}
		return mtr3compat$availableListField;
	}

	private static Field mtr3compat$getAllDataField() {
		if (mtr3compat$allDataField == null) {
			mtr3compat$allDataField = mtr3compat$findField("allData");
		}
		return mtr3compat$allDataField;
	}

	private static Field mtr3compat$findField(String name) {
		try {
			final Field field = DashboardListSelectorScreen.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (Exception e) {
			return null;
		}
	}
}
