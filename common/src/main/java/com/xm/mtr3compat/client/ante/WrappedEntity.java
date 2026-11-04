package com.xm.mtr3compat.client.ante;

import com.lx862.mtrscripting.core.util.ScriptVector3f;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * ANTE 全局 "WrappedEntity" 替身（源 cn.zbx1425.mtrsteamloco.scripting.util.WrappedEntity）。
 * ANTE 的 sowcer Vector3f 在 JCM 环境对应 ScriptVector3f（同为脚本层向量类型）。
 * entity 字段公开，脚本可继续访问底层原生 Entity（沙盒已放开 net.minecraft.*）。
 */
public class WrappedEntity {
	public final Entity entity;

	public WrappedEntity(Entity entity) {
		this.entity = entity;
	}

	public double getX() {
		return entity == null ? 0 : entity.getX();
	}

	public double getY() {
		return entity == null ? 0 : entity.getY();
	}

	public double getZ() {
		return entity == null ? 0 : entity.getZ();
	}

	public ScriptVector3f getLookAngle() {
		if (entity == null) {
			return new ScriptVector3f(0, 0, 0);
		}
		net.minecraft.world.phys.Vec3 v = entity.getLookAngle();
		return new ScriptVector3f((float) v.x, (float) v.y, (float) v.z);
	}

	public ScriptVector3f getPosition() {
		return new ScriptVector3f((float) getX(), (float) getY(), (float) getZ());
	}

	public boolean isShiftKeyDown() {
		return entity != null && entity.isShiftKeyDown();
	}

	public String getNBT() {
		if (entity == null) {
			return "{}";
		}
		CompoundTag tag = new CompoundTag();
		entity.saveWithoutId(tag);
		return (new JsonStringTagVisitor()).visit(tag);
	}
}
