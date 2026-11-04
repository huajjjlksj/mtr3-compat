package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.LegacyDoorMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.Box;
import org.mtr.mod.resource.ModelPropertiesPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 旧版资源包的双层门（内外两片）在 MTR4 里按各自包围盒就近绑定门洞，内层常被绑到相邻门洞格，
 * 开门时只有外层滑动。绑定完成后统一同槽位门扇的门口，见 {@link LegacyDoorMap}。
 */
@Mixin(value = ModelPropertiesPart.class, remap = false)
public class Mtr3CompatDoorMapMixin {

	@Shadow
	@Final
	private ObjectArrayList<Object> partDetailsList;

	@Inject(method = "mapDoors", at = @At("TAIL"))
	private void mtr3compat$unifyDoorways(ObjectArrayList<Box> doorways, CallbackInfo callbackInfo) {
		LegacyDoorMap.unify(this, partDetailsList, doorways);
	}
}
