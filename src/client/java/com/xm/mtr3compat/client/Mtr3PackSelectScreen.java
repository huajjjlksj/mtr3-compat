package com.xm.mtr3compat.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 按包勾选界面：列出 resourcepacks 下所有 MTR 资源包，MTR3/MTR4 切换即时写配置；
 * 关闭时只有在勾选确实变化过才重载资源包，避免只是进来看一眼就触发整包重载。
 */
public class Mtr3PackSelectScreen extends Screen {

	private final Screen parent;
	private List<Path> packList = Collections.emptyList();
	/** 打开界面时的勾选快照，用于判断关闭时是否需要重载。 */
	private Set<String> packsOnOpen;

	public Mtr3PackSelectScreen(Screen parent) {
		super(Component.literal("以 MTR3 加载的资源包"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (packsOnOpen == null) {
			packsOnOpen = new LinkedHashSet<>(Mtr3CompatConfig.getPacks());
		}
		addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose()).bounds(width / 2 - 100, height - 30, 200, 20).build());
		packList = Mtr3CompatPacks.mtrPacks();
		int y = 52;
		for (final Path path : packList) {
			final String name = Mtr3CompatPacks.packBaseName(path);
			final Component label = Component.literal((Mtr3CompatConfig.isIntercepted(name) ? "MTR3: " : "MTR4: ") + name);
			addRenderableWidget(Button.builder(label, button -> {
				Mtr3CompatConfig.toggle(name);
				init(minecraft, width, height);
			}).bounds(width / 2 - 160, y, 320, 20).build());
			y += 24;
			if (y > height - 44) {
				break;
			}
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
		guiGraphics.drawCenteredString(font, Component.literal("MTR3 = 该包由 mtr3-compat 接管（MTR4/JCM 不解析），需同时在原版资源包界面启用"), width / 2, 30, 0xAAAAAA);
		if (packList.isEmpty()) {
			guiGraphics.drawCenteredString(font, Component.literal("未在 resourcepacks 目录找到含 mtr_custom_resources.json 的资源包"), width / 2, 60, 0xFFAAAA);
		}
	}

	@Override
	public void onClose() {
		Mtr3CompatConfig.save();
		minecraft.setScreen(parent);
		if (packsOnOpen == null || !packsOnOpen.equals(Mtr3CompatConfig.getPacks())) {
			minecraft.reloadResourcePacks();
		}
	}
}
