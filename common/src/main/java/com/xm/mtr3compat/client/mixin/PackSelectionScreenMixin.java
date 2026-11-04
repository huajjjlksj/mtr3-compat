package com.xm.mtr3compat.client.mixin;

import com.xm.mtr3compat.client.Mtr3CompatClient;
import com.xm.mtr3compat.client.Mtr3CompatConfig;
import com.xm.mtr3compat.client.Mtr3IconButton;
import com.xm.mtr3compat.client.Mtr3PackSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 原版资源包选择界面右上角加一个图标按钮，无需进入世界即可打开「以 MTR3 加载」的资源包勾选界面。
 * 关闭原版界面时统一决定是否重载：原版自己已重载（原版勾选有改动）则不重复拉取；
 * 仅模组勾选有改动时由模组补一次；两者都没改动则不重载。
 */
@Mixin(PackSelectionScreen.class)
public class PackSelectionScreenMixin {

	/** 打开原版界面时的模组勾选快照；关闭原版界面时用于判断净变化（来回切换回原样也算未改动）。 */
	@Unique
	private Set<String> mtr3compat$packsOnOpen;

	@Inject(method = "init()V", at = @At("TAIL"))
	private void mtr3compat$addMtr3IconButton(CallbackInfo ci) {
		if (mtr3compat$packsOnOpen == null) {
			mtr3compat$packsOnOpen = new LinkedHashSet<>(Mtr3CompatConfig.getPacks());
		}
		final Screen self = (Screen) (Object) this;
		self.addRenderableWidget(new Mtr3IconButton(self.width - 24, 6, button -> Minecraft.getInstance().setScreen(new Mtr3PackSelectScreen(self))));
	}

	@Inject(method = "onClose()V", at = @At("HEAD"))
	private void mtr3compat$resetVanillaReloadFlag(CallbackInfo ci) {
		Mtr3CompatClient.vanillaReloaded = false;
	}

	@Inject(method = "onClose()V", at = @At("TAIL"))
	private void mtr3compat$reloadIfOnlyMtr3Changed(CallbackInfo ci) {
		try {
			if (mtr3compat$packsOnOpen != null && !mtr3compat$packsOnOpen.equals(Mtr3CompatConfig.getPacks()) && !Mtr3CompatClient.vanillaReloaded) {
				Minecraft.getInstance().reloadResourcePacks();
			}
		} finally {
			mtr3compat$packsOnOpen = null;
			Mtr3CompatClient.vanillaReloaded = false;
		}
	}
}
