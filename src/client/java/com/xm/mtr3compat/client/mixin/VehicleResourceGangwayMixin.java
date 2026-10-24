package com.xm.mtr3compat.client.mixin;

import org.mtr.mod.resource.VehicleResource;
import org.mtr.mod.resource.VehicleResourceCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NTE 时代资源包没有 hasGangway1/hasGangway2 字段（只有 gangway_connection_id，且常为空），
 * MTR 4 会把这两个布尔默认成 false，于是不会在两节车厢之间生成可站立的过道框，
 * 玩家在车里被限制在单节车厢地板内（表现为风挡过不去）。
 * <p>
 * MTR4 自己处理这个问题的方式是给同一车型生成 4 个变体
 * （{@code mtr_custom_train_<id>_trailer/cab_1/cab_2/cab_3}，见 LegacyVehicleResource#L34-L136），
 * 由 cab 变体把车头/车尾那一端关掉。本 mod 一车一条目、没有变体，所以这里按
 * “该端是否真的有相邻车厢”来补开：
 * <ul>
 *   <li>side1（模型局部 -z）朝向序号更小的车厢 -> carNumber == 0 时关闭；</li>
 *   <li>side2（模型局部 +z）朝向序号更大的车厢 -> carNumber == totalCars - 1 时关闭。</li>
 * </ul>
 * 车头/车尾端保持关闭后，MTR 会生成墙壁碰撞，玩家在驾驶室不会掉下车。
 * 位置由 {@code getCachedVehicleResource(carNumber, totalCars, force)} 在每节车渲染前记录，
 * 该方法在 RenderVehicles 里恰好先于过道地板生成被调用。
 */
@Mixin(VehicleResource.class)
public abstract class VehicleResourceGangwayMixin {

	/** {carNumber, totalCars}，由渲染循环逐车写入。 */
	private static final ThreadLocal<int[]> CAR_POSITION = new ThreadLocal<>();

	@Shadow
	public abstract boolean hasBarrier1();

	@Shadow
	public abstract boolean hasBarrier2();

	@Inject(method = "getCachedVehicleResource", at = @At("HEAD"))
	private void mtr3compat$trackCarPosition(int carNumber, int totalCars, boolean force, CallbackInfoReturnable<VehicleResourceCache> cir) {
		CAR_POSITION.set(new int[]{carNumber, totalCars});
	}

	@Inject(method = "hasGangway1", at = @At("TAIL"), cancellable = true)
	private void mtr3compat$enableGangway1(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && !hasBarrier1() && hasNeighborTowards(-1)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "hasGangway2", at = @At("TAIL"), cancellable = true)
	private void mtr3compat$enableGangway2(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && !hasBarrier2() && hasNeighborTowards(1)) {
			cir.setReturnValue(true);
		}
	}

	/** direction = -1 表示局部 -z 端（朝向 carNumber-1），+1 表示局部 +z 端（朝向 carNumber+1）。 */
	private static boolean hasNeighborTowards(int direction) {
		final int[] position = CAR_POSITION.get();
		if (position == null) {
			return true;
		}
		final int carNumber = position[0];
		final int totalCars = position[1];
		if (totalCars <= 1) {
			return false;
		}
		return direction < 0 ? carNumber > 0 : carNumber < totalCars - 1;
	}
}
