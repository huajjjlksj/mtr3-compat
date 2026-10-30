package org.mtr.mod.resource;

/**
 * 标记「由旧版 OBJ 路径创建的门部件」（{@code ModelPropertiesPart.writeCache(Map, ...)}）。
 * 只有这类部件在开门渲染时需要修正，常规 bbmodel 资源必须保持 MTR4 原行为。
 */
public interface Mtr3CompatObjDoorFlag {

	void mtr3compat$setObjDoor();

	boolean mtr3compat$isObjDoor();
}
