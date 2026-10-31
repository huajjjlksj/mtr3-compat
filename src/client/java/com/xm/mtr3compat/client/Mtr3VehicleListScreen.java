package com.xm.mtr3compat.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 车型两栏选择界面（仿 MTR 列车选择器）：左栏“可用车型”、右栏“已执行”，只列出本模组（MTR3 模式）加载的车型。
 * 两栏各有独立的搜索框与滚动位置；点车型即可在两栏之间移动。
 * 车厢平移模式下右栏车型下方带 x/y/z 输入框。
 * 返回上一级时把草稿写回配置（不触发重载，由配置页统一处理）。
 */
public class Mtr3VehicleListScreen extends Screen {

	public static final int MODE_LR = 0;
	public static final int MODE_OFFSET = 1;

	private static final int COLUMN_WIDTH = 170;
	private static final int COLUMN_GAP = 12;
	private static final int SEARCH_Y = 54;
	private static final int LIST_TOP = 102;
	private static final int ROW_BUTTON_HEIGHT = 20;

	private final Screen parent;
	private final int mode;

	private List<String> allIds = Collections.emptyList();
	private boolean initialized;
	private final Set<String> lrDraft = new LinkedHashSet<>();
	private final Map<String, float[]> offsetDraft = new LinkedHashMap<>();

	private int leftScroll;
	private int rightScroll;
	private String leftQuery = "";
	private String rightQuery = "";
	private EditBox leftSearchBox;
	private EditBox rightSearchBox;

	public Mtr3VehicleListScreen(Screen parent, int mode) {
		super(Component.literal(mode == MODE_LR ? "脚本左右互换：选择车型" : "脚本车厢平移：选择车型"));
		this.parent = parent;
		this.mode = mode;
	}

	@Override
	protected void init() {
		if (!initialized) {
			initialized = true;
			final List<String> ids = new ArrayList<>();
			NativeTrainLoader.nativeIds().forEach(ids::add);
			ids.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(NativeTrainLoader.nativeName(a), NativeTrainLoader.nativeName(b)));
			allIds = ids;
			final Map<String, float[]> configured = Mtr3CompatConfig.getScriptCarOffsets();
			final Set<String> lrConfigured = Mtr3CompatConfig.getScriptLrSwapKeys();
			for (final String id : allIds) {
				if (lrConfigured.contains(Mtr3CompatConfig.key(id))) {
					lrDraft.add(id);
				}
				final float[] offset = configured.get(Mtr3CompatConfig.key(id));
				if (offset != null) {
					offsetDraft.put(id, offset);
				}
			}
		}
		rebuild();
	}

	/** 重建所有控件（保留搜索框焦点与内容）。 */
	private void rebuild() {
		final boolean leftFocused = leftSearchBox != null && leftSearchBox.isFocused();
		final boolean rightFocused = rightSearchBox != null && rightSearchBox.isFocused();
		clearWidgets();

		final int leftX = leftX();
		final int rightX = rightX();
		final int leftMaxRows = leftMaxRows();
		final int rightMaxRows = rightMaxRows();

		final List<String> left = filterAvailable(leftQuery);
		final List<String> right = filterSelected(rightQuery);
		leftScroll = Math.min(Math.max(leftScroll, 0), Math.max(0, left.size() - leftMaxRows));
		rightScroll = Math.min(Math.max(rightScroll, 0), Math.max(0, right.size() - rightMaxRows));

		leftSearchBox = new EditBox(font, leftX, SEARCH_Y, COLUMN_WIDTH, 18, Component.literal(""));
		leftSearchBox.setHint(Component.literal("搜索可用车型"));
		leftSearchBox.setMaxLength(64);
		leftSearchBox.setValue(leftQuery);
		leftSearchBox.setResponder(text -> {
			if (!leftQuery.equals(text)) {
				leftQuery = text;
				leftScroll = 0;
				rebuild();
			}
		});
		addRenderableWidget(leftSearchBox);
		if (leftFocused) {
			setFocused(leftSearchBox);
		}

		rightSearchBox = new EditBox(font, rightX, SEARCH_Y, COLUMN_WIDTH, 18, Component.literal(""));
		rightSearchBox.setHint(Component.literal("搜索已执行车型"));
		rightSearchBox.setMaxLength(64);
		rightSearchBox.setValue(rightQuery);
		rightSearchBox.setResponder(text -> {
			if (!rightQuery.equals(text)) {
				rightQuery = text;
				rightScroll = 0;
				rebuild();
			}
		});
		addRenderableWidget(rightSearchBox);
		if (rightFocused) {
			setFocused(rightSearchBox);
		}

		for (int row = 0; row < leftMaxRows; row++) {
			final int leftIndex = leftScroll + row;
			if (leftIndex < left.size()) {
				final String id = left.get(leftIndex);
				addRenderableWidget(Button.builder(Component.literal(NativeTrainLoader.nativeName(id)), button -> {
					moveToRight(id);
					rebuild();
				}).bounds(leftX, LIST_TOP + row * leftRowHeight(), COLUMN_WIDTH, ROW_BUTTON_HEIGHT).build());
			}
		}

		for (int row = 0; row < rightMaxRows; row++) {
			final int rightIndex = rightScroll + row;
			if (rightIndex >= right.size()) {
				break;
			}
			final String id = right.get(rightIndex);
			final int y = LIST_TOP + row * rightRowHeight();
			addRenderableWidget(Button.builder(Component.literal(NativeTrainLoader.nativeName(id)), button -> {
				moveToLeft(id);
				rebuild();
			}).bounds(rightX, y, COLUMN_WIDTH, ROW_BUTTON_HEIGHT).build());

			if (mode == MODE_OFFSET) {
				final float[] offset = offsetDraft.computeIfAbsent(id, key -> new float[3]);
				for (int axis = 0; axis < 3; axis++) {
					final EditBox box = new EditBox(font, rightX + 14 + axis * 56, y + ROW_BUTTON_HEIGHT + 2, 42, 16, Component.literal(""));
					box.setMaxLength(12);
					box.setValue(number(offset[axis]));
					final int axisIndex = axis;
					box.setResponder(text -> updateOffset(id, axisIndex, text));
					addRenderableWidget(box);
				}
			}
		}

		addRenderableWidget(Button.builder(Component.literal("▲"), button -> {
			leftScroll--;
			rebuild();
		}).bounds(leftX, height - 28, 20, 20).build()).active = leftScroll > 0;

		addRenderableWidget(Button.builder(Component.literal("▼"), button -> {
			leftScroll++;
			rebuild();
		}).bounds(leftX + 22, height - 28, 20, 20).build()).active = leftScroll < Math.max(0, left.size() - leftMaxRows);

		addRenderableWidget(Button.builder(Component.literal("▲"), button -> {
			rightScroll--;
			rebuild();
		}).bounds(rightX + COLUMN_WIDTH - 42, height - 28, 20, 20).build()).active = rightScroll > 0;

		addRenderableWidget(Button.builder(Component.literal("▼"), button -> {
			rightScroll++;
			rebuild();
		}).bounds(rightX + COLUMN_WIDTH - 20, height - 28, 20, 20).build()).active = rightScroll < Math.max(0, right.size() - rightMaxRows);

		addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose())
				.bounds(width / 2 - 50, height - 28, 100, 20).build());

		addRenderableWidget(Button.builder(Component.literal("以车库车辆添加"), button -> minecraft.setScreen(new Mtr3DepotSelectScreen(this)))
				.bounds(width / 2 - 75, 76, 150, 18).build());
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics);
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		final int leftX = leftX();
		final int rightX = rightX();
		final List<String> left = filterAvailable(leftQuery);
		final List<String> right = filterSelected(rightQuery);
		final int listBottom = height - 34;

		guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
		guiGraphics.fill(leftX, LIST_TOP - 4, leftX + COLUMN_WIDTH, listBottom, 0x30000000);
		guiGraphics.fill(rightX, LIST_TOP - 4, rightX + COLUMN_WIDTH, listBottom, 0x30000000);

		guiGraphics.drawCenteredString(font, Component.literal("可用车型 (" + left.size() + ")"), leftX + COLUMN_WIDTH / 2, 38, 0xAAAAAA);
		guiGraphics.drawCenteredString(font, Component.literal("已执行 (" + right.size() + ")"), rightX + COLUMN_WIDTH / 2, 38, 0xFFD070);

		if (allIds.isEmpty()) {
			guiGraphics.drawCenteredString(font, Component.literal("未找到本模组加载的车型，请先进世界重载资源包"),
					width / 2, LIST_TOP + 10, 0xFFAAAA);
		} else if (left.isEmpty()) {
			guiGraphics.drawCenteredString(font, Component.literal("无匹配车型"), leftX + COLUMN_WIDTH / 2, LIST_TOP + 10, 0x888888);
		}
		if (!allIds.isEmpty() && right.isEmpty()) {
			guiGraphics.drawCenteredString(font, Component.literal("尚未选择"), rightX + COLUMN_WIDTH / 2, LIST_TOP + 10, 0x888888);
		}

		drawScrollBar(guiGraphics, leftX + COLUMN_WIDTH - 3, LIST_TOP, listBottom, left.size(), leftMaxRows(), leftScroll);
		drawScrollBar(guiGraphics, rightX + COLUMN_WIDTH - 3, LIST_TOP, listBottom, right.size(), rightMaxRows(), rightScroll);

		if (mode == MODE_OFFSET) {
			for (int row = 0; row < rightMaxRows(); row++) {
				final int index = rightScroll + row;
				if (index >= right.size()) {
					continue;
				}
				final int y = LIST_TOP + row * rightRowHeight() + ROW_BUTTON_HEIGHT + 4;
				guiGraphics.drawString(font, "x", rightX + 4, y, 0xAAAAAA, false);
				guiGraphics.drawString(font, "y", rightX + 60, y, 0xAAAAAA, false);
				guiGraphics.drawString(font, "z", rightX + 116, y, 0xAAAAAA, false);
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (delta == 0) {
			return super.mouseScrolled(mouseX, mouseY, delta);
		}
		final int step = delta > 0 ? -1 : 1;
		if (mouseX < width / 2) {
			final int rows = filterAvailable(leftQuery).size();
			leftScroll = Math.max(0, Math.min(leftScroll + step, Math.max(0, rows - leftMaxRows())));
		} else {
			final int rows = filterSelected(rightQuery).size();
			rightScroll = Math.max(0, Math.min(rightScroll + step, Math.max(0, rows - rightMaxRows())));
		}
		rebuild();
		return true;
	}

	@Override
	public void onClose() {
		if (mode == MODE_LR) {
			Mtr3CompatConfig.applyVehicleLrSwap(allIds, lrDraft);
		} else {
			Mtr3CompatConfig.applyVehicleOffsets(allIds, offsetDraft);
		}
		minecraft.setScreen(parent);
	}

	/** 按车库批量加入“执行”栏（车型 id 大小写不敏感匹配）。 */
	void addVehicles(Collection<String> vehicleIds) {
		final Map<String, String> index = new HashMap<>();
		for (final String id : allIds) {
			index.put(Mtr3CompatConfig.key(id), id);
		}
		for (final String vehicleId : vehicleIds) {
			final String id = index.get(Mtr3CompatConfig.key(vehicleId));
			if (id != null) {
				moveToRight(id);
			}
		}
	}

	private int leftX() {
		return width / 2 - COLUMN_GAP / 2 - COLUMN_WIDTH;
	}

	private int rightX() {
		return width / 2 + COLUMN_GAP / 2;
	}

	/** 左栏只有车型按钮，始终紧凑排列。 */
	private int leftRowHeight() {
		return 24;
	}

	/** 右栏在平移模式下每行下方要放 x/y/z 输入框。 */
	private int rightRowHeight() {
		return mode == MODE_OFFSET ? 46 : 24;
	}

	private int leftMaxRows() {
		return Math.max(1, (height - 34 - LIST_TOP) / leftRowHeight());
	}

	private int rightMaxRows() {
		return Math.max(1, (height - 34 - LIST_TOP) / rightRowHeight());
	}

	private List<String> filterAvailable(String query) {
		final List<String> list = new ArrayList<>();
		for (final String id : allIds) {
			if (!isSelected(id) && matches(id, query)) {
				list.add(id);
			}
		}
		return list;
	}

	private List<String> filterSelected(String query) {
		final List<String> list = new ArrayList<>();
		for (final String id : allIds) {
			if (isSelected(id) && matches(id, query)) {
				list.add(id);
			}
		}
		return list;
	}

	private boolean matches(String id, String query) {
		final String trimmed = query.trim().toLowerCase(Locale.ENGLISH);
		return trimmed.isEmpty()
				|| id.toLowerCase(Locale.ENGLISH).contains(trimmed)
				|| NativeTrainLoader.nativeName(id).toLowerCase(Locale.ENGLISH).contains(trimmed);
	}

	private boolean isSelected(String id) {
		return mode == MODE_LR ? lrDraft.contains(id) : offsetDraft.containsKey(id);
	}

	private void moveToRight(String id) {
		if (mode == MODE_LR) {
			lrDraft.add(id);
		} else {
			offsetDraft.computeIfAbsent(id, key -> new float[3]);
		}
	}

	private void moveToLeft(String id) {
		if (mode == MODE_LR) {
			lrDraft.remove(id);
		} else {
			offsetDraft.remove(id);
		}
	}

	private void updateOffset(String id, int axis, String text) {
		float value = 0;
		try {
			value = text.trim().isEmpty() ? 0 : Float.parseFloat(text.trim());
		} catch (Exception ignored) {
		}
		offsetDraft.computeIfAbsent(id, key -> new float[3])[axis] = value;
	}

	private void drawScrollBar(GuiGraphics guiGraphics, int x, int top, int bottom, int totalRows, int maxRows, int scroll) {
		if (totalRows <= maxRows) {
			return;
		}
		final int trackHeight = bottom - top;
		final int thumbHeight = Math.max(16, trackHeight * maxRows / totalRows);
		final int maxScroll = totalRows - maxRows;
		final int thumbY = top + (trackHeight - thumbHeight) * scroll / maxScroll;
		guiGraphics.fill(x, top, x + 2, bottom, 0x40FFFFFF);
		guiGraphics.fill(x, thumbY, x + 2, thumbY + thumbHeight, 0xFFAAAAAA);
	}

	private static String number(float value) {
		return value == (float) Math.floor(value) && Math.abs(value) < 1000 ? Integer.toString((int) value) : Float.toString(value);
	}
}
