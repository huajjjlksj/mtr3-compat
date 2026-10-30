package org.mtr.mod.resource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "org.mtr.mod.resource.ModelPropertiesPart$PartDetails")
public abstract class Mtr3CompatPartDetailsMixin implements Mtr3CompatObjDoorFlag {

	@Unique
	private boolean mtr3compat$objDoor = false;

	@Override
	public void mtr3compat$setObjDoor() {
		mtr3compat$objDoor = true;
	}

	@Override
	public boolean mtr3compat$isObjDoor() {
		return mtr3compat$objDoor;
	}
}
