package com.xm.mtr3compat.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Siding;
import org.mtr.core.data.VehicleCar;
import org.mtr.mod.client.MinecraftClientData;

import java.util.ArrayList;
import java.util.List;

/**
 * 不透明弹窗：列出当前地图所有车库，按键边框显示车库颜色；
 * 选中车库会把该车库内的本模组车型全部加入“执行”栏。
 */
public class Mtr3DepotSelectScreen extends Screen {

	private static final int PANEL_WIDTH = 340;
	private static final int ROW_HEIGHT = 24;

	private final Mtr3VehicleListScreen parent;
	private final List<Depot> depots = new ArrayList<>();
	private int scroll;

	public Mtr3DepotSelectScreen(Mtr3VehicleListScreen parent) {
		super(Component.literal("按车库添加车辆"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (depots.isEmpty()) {
			for (final Depot depot : MinecraftClientData.getDashboardInstance().depots) {
				depots.add(depot);
			}
			depots.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(formatName(a), formatName(b)));
		}
		scroll = Math.min(Math.max(scroll, 0), Math.max(0, depots.size() - maxRows()));

		final int panelX = width / 2 - PANEL_WIDTH / 2;
		final int firstY = panelTop() + 42;
		for (int row = 0; row < maxRows(); row++) {
			final int index = scroll + row;
			if (index >= depots.size()) {
				break;
			}
			final Depot depot = depots.get(index);
			addRenderableWidget(Button.builder(Component.literal(formatName(depot)), button -> {
				parent.addVehicles(vehiclesIn(depot));
				minecraft.setScreen(parent);
			}).bounds(panelX + 20, firstY + row * ROW_HEIGHT, PANEL_WIDTH - 40, 20).build());
		}

		addRenderableWidget(Button.builder(Component.literal("取消"), button -> minecraft.setScreen(parent))
				.bounds(width / 2 - 40, panelTop() + panelHeight() - 30, 80, 20).build());
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics);
		final int panelX = width / 2 - PANEL_WIDTH / 2;
		final int panelTop = panelTop();
		final int panelHeight = panelHeight();
		// 不透明底板
		guiGraphics.fill(panelX, panelTop, panelX + PANEL_WIDTH, panelTop + panelHeight, 0xFF0E0E12);
		guiGraphics.fill(panelX, panelTop, panelX + PANEL_WIDTH, panelTop + 2, 0xFF8A8A8A);
		guiGraphics.fill(panelX, panelTop + panelHeight - 2, panelX + PANEL_WIDTH, panelTop + panelHeight, 0xFF8A8A8A);
		guiGraphics.fill(panelX, panelTop, panelX + 2, panelTop + panelHeight, 0xFF8A8A8A);
		guiGraphics.fill(panelX + PANEL_WIDTH - 2, panelTop, panelX + PANEL_WIDTH, panelTop + panelHeight, 0xFF8A8A8A);

		super.render(guiGraphics, mouseX, mouseY, partialTick);

		guiGraphics.drawCenteredString(font, title, width / 2, panelTop + 10, 0xFFFFFF);
		guiGraphics.drawCenteredString(font, Component.literal("选择车库会把该车库内的本模组车辆加入执行栏"), width / 2, panelTop + 26, 0xAAAAAA);

		final int firstY = panelTop + 42;
		for (int row = 0; row < maxRows(); row++) {
			final int index = scroll + row;
			if (index >= depots.size()) {
				break;
			}
			final int color = 0xFF000000 | depots.get(index).getColor();
			final int x = panelX + 20;
			final int y = firstY + row * ROW_HEIGHT;
			guiGraphics.fill(x, y, x + PANEL_WIDTH - 40, y + 2, color);
			guiGraphics.fill(x, y + 18, x + PANEL_WIDTH - 40, y + 20, color);
			guiGraphics.fill(x, y, x + 2, y + 20, color);
			guiGraphics.fill(x + PANEL_WIDTH - 42, y, x + PANEL_WIDTH - 40, y + 20, color);
		}

		if (depots.isEmpty()) {
			guiGraphics.drawCenteredString(font, Component.literal("当前地图没有车库"), width / 2, firstY + 20, 0xFFAAAA);
		} else if (depots.size() > maxRows()) {
			guiGraphics.drawString(font, Component.literal(String.format("%d/%d", scroll + 1, depots.size())),
					panelX + PANEL_WIDTH - 44, panelTop + 10, 0x888888, false);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (delta != 0 && depots.size() > maxRows()) {
			scroll = Math.max(0, Math.min(scroll + (delta > 0 ? -1 : 1), depots.size() - maxRows()));
			init(minecraft, width, height);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	private int maxRows() {
		return Math.max(1, (height - 140) / ROW_HEIGHT);
	}

	private int panelHeight() {
		return 42 + Math.min(depots.size(), maxRows()) * ROW_HEIGHT + 36;
	}

	private int panelTop() {
		return (height - panelHeight()) / 2;
	}

	private static String formatName(Depot depot) {
		return depot.getName().replace('|', ' ');
	}

	private static List<String> vehiclesIn(Depot depot) {
		final List<String> ids = new ArrayList<>();
		for (final Siding siding : MinecraftClientData.getDashboardInstance().sidings) {
			if (siding.area != null && siding.area.getId() == depot.getId()) {
				for (final VehicleCar car : siding.getVehicleCars()) {
					ids.add(car.getVehicleId());
				}
			}
		}
		return ids;
	}
}
