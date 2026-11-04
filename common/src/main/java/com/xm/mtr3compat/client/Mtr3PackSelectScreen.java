package com.xm.mtr3compat.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 按包勾选界面：列出 resourcepacks 下所有 MTR 资源包，MTR3/MTR4 切换即时写配置；
 * 关闭时重载资源包，避免只是进来看一眼就触发整包重载。
 * 从原版资源包界面进入时，关闭只返回原版界面、不重载，由原版界面的关闭流程统一触发一次。
 * 包数量超过一屏时可翻页（上一页/下一页）。
 */
public class Mtr3PackSelectScreen extends Screen {

	private final Screen parent;
	private List<Path> packList = Collections.emptyList();
	/** 打开界面时的勾选快照，用于判断关闭时是否需要重载。 */
	private Set<String> packsOnOpen;
	private int page;

	public Mtr3PackSelectScreen(Screen parent) {
		super(Component.literal("以 MTR3 加载的资源包"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (packsOnOpen == null) {
			packsOnOpen = new LinkedHashSet<>(Mtr3CompatConfig.getPacks());
		}
		packList = Mtr3CompatPacks.mtrPacks();

		final int bottomRowY = height - 28;
		final int firstY = 52;
		final int perPage = Math.max(1, (bottomRowY - 8 - firstY) / 24);
		final int totalPages = Math.max(1, (packList.size() + perPage - 1) / perPage);
		page = Math.min(Math.max(page, 0), totalPages - 1);

		final int start = page * perPage;
		final int end = Math.min(packList.size(), start + perPage);
		int y = firstY;
		for (int i = start; i < end; i++) {
			final String name = Mtr3CompatPacks.packBaseName(packList.get(i));
			final Component label = Component.literal((Mtr3CompatConfig.isIntercepted(name) ? "MTR3: " : "MTR4: ") + name);
			addRenderableWidget(Button.builder(label, button -> {
				Mtr3CompatConfig.toggle(name);
				init(minecraft, width, height);
			}).bounds(width / 2 - 160, y, 320, 20).build());
			y += 24;
		}

		addRenderableWidget(Button.builder(Component.literal("上一页"), button -> {
			page--;
			init(minecraft, width, height);
		}).bounds(width / 2 - 170, bottomRowY, 60, 20).build()).active = page > 0;

		addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose())
				.bounds(width / 2 - 60, bottomRowY, 120, 20).build());

		addRenderableWidget(Button.builder(Component.literal("下一页"), button -> {
			page++;
			init(minecraft, width, height);
		}).bounds(width / 2 + 110, bottomRowY, 60, 20).build()).active = page < totalPages - 1;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
		guiGraphics.drawCenteredString(font, Component.literal("MTR3 = 该包由 mtr3-compat 接管（MTR4/JCM 不解析），需同时在原版资源包界面启用"), width / 2, 30, 0xAAAAAA);
		if (packList.isEmpty()) {
			guiGraphics.drawCenteredString(font, Component.literal("未在 resourcepacks 目录找到含 mtr_custom_resources.json 的资源包"), width / 2, 60, 0xFFAAAA);
		} else {
			final int bottomRowY = height - 28;
			final int perPage = Math.max(1, (bottomRowY - 8 - 52) / 24);
			final int totalPages = Math.max(1, (packList.size() + perPage - 1) / perPage);
			guiGraphics.drawCenteredString(font, Component.literal(String.format("第 %d/%d 页 · 共 %d 个包", page + 1, totalPages, packList.size())), width / 2, bottomRowY - 14, 0xAAAAAA);
		}
	}

	@Override
	public void onClose() {
		Mtr3CompatConfig.save();
		final boolean packsChanged = packsOnOpen != null && !packsOnOpen.equals(Mtr3CompatConfig.getPacks());
		minecraft.setScreen(parent);
		// 由原版资源包界面进入时不在此时重载：返回原版界面，由原版关闭流程决定（有改动则原版自己重载，无改动则由模组补一次）
		if (packsChanged && !(parent instanceof PackSelectionScreen)) {
			minecraft.reloadResourcePacks();
		}
	}
}
