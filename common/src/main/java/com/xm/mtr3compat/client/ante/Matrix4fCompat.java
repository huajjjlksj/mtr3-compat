package com.xm.mtr3compat.client.ante;

import com.lx862.mtrscripting.core.util.ScriptVector3f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

/**
 * ANTE 全局 "Matrix4f" 替身（源 cn.zbx1425.sowcer.math.Matrix4f 的 1.19.3+ joml 分支，MC 1.20.1 即此分支）。
 * sowcer Vector3f -> ScriptVector3f（脚本层向量）；asMoj()/getRotationPart() 直接暴露 joml 对象，
 * 脚本可经沙盒放开后的 org.joml.* 继续操作。
 * 已知边界：sowcer 的 Posture 转换链（getAsPose/getAsPoseStack/getAsMatrices）依赖 sowcer 渲染管线，JCM 下不适用；
 * JCM 的 "Matrices" 全局（实例式变换栈）保持原样不覆盖。
 */
public class Matrix4fCompat {

	protected final Matrix4f impl;

	public Matrix4fCompat() {
		this.impl = new Matrix4f();
		this.impl.identity();
	}

	public Matrix4fCompat(Matrix4f moj) {
		this.impl = moj;
	}

	public Matrix4fCompat(Matrix4fCompat other) {
		this.impl = new Matrix4f(other.impl);
	}

	public Matrix4fCompat copy() {
		return new Matrix4fCompat(this);
	}

	public Matrix4f asMoj() {
		return impl;
	}

	public static Matrix4fCompat translation(float x, float y, float z) {
		Matrix4fCompat result = new Matrix4fCompat();
		result.impl.translation(x, y, z);
		return result;
	}

	public void multiply(Matrix4fCompat other) {
		impl.mul(other.impl);
	}

	public void multiply(Quaternionf q) {
		impl.rotate(q);
	}

	public void store(FloatBuffer buffer) {
		buffer
				.put(0, impl.m00())
				.put(1, impl.m01())
				.put(2, impl.m02())
				.put(3, impl.m03())
				.put(4, impl.m10())
				.put(5, impl.m11())
				.put(6, impl.m12())
				.put(7, impl.m13())
				.put(8, impl.m20())
				.put(9, impl.m21())
				.put(10, impl.m22())
				.put(11, impl.m23())
				.put(12, impl.m30())
				.put(13, impl.m31())
				.put(14, impl.m32())
				.put(15, impl.m33());
	}

	public void load(FloatBuffer buffer) {
		float[] bufferValues = new float[16];
		buffer.get(bufferValues);
		impl.set(bufferValues);
	}

	public void rotateX(float rad) {
		impl.rotateX(rad);
	}

	public void rotateY(float rad) {
		impl.rotateY(rad);
	}

	public void rotateZ(float rad) {
		impl.rotateZ(rad);
	}

	public void rotate(ScriptVector3f axis, float rad) {
		impl.rotate(rad, toJoml(axis));
	}

	public void translate(float x, float y, float z) {
		impl.translate(x, y, z);
	}

	public void translate(ScriptVector3f vec) {
		translate(vec.x(), vec.y(), vec.z());
	}

	public ScriptVector3f transform(ScriptVector3f src) {
		Vector3f result = impl.transformPosition(toJoml(src));
		return new ScriptVector3f(result.x, result.y, result.z);
	}

	public ScriptVector3f transform3(ScriptVector3f src) {
		Vector3f result = impl.transformDirection(toJoml(src));
		return new ScriptVector3f(result.x, result.y, result.z);
	}

	public Matrix3f getRotationPart() {
		Matrix3f result = new Matrix3f();
		return impl.get3x3(result);
	}

	public ScriptVector3f getTranslationPart() {
		Vector3f result = new Vector3f();
		impl.getTranslation(result);
		return new ScriptVector3f(result.x, result.y, result.z);
	}

	public void scale(float x, float y, float z) {
		impl.scale(x, y, z);
	}

	public void scale(float factor) {
		scale(factor, factor, factor);
	}

	public void mul(Matrix4fCompat other) {
		multiply(other);
	}

	public void rotateXYZ(float x, float y, float z) {
		rotateX(x);
		rotateY(y);
		rotateZ(z);
	}

	public void rotateXYZ(ScriptVector3f vec) {
		rotateXYZ(vec.x(), vec.y(), vec.z());
	}

	public void rotateYXZ(float y, float x, float z) {
		rotateY(y);
		rotateX(x);
		rotateZ(z);
	}

	public void rotateYXZ(ScriptVector3f vec) {
		rotateYXZ(vec.y(), vec.x(), vec.z());
	}

	public void rotateZYX(float z, float y, float x) {
		rotateZ(z);
		rotateY(y);
		rotateX(x);
	}

	public void rotateZYX(ScriptVector3f vec) {
		rotateZYX(vec.z(), vec.y(), vec.x());
	}

	public ScriptVector3f getEulerAnglesZYX() {
		float[] src = new float[16];
		store(java.nio.FloatBuffer.wrap(src));
		float x = (float) Math.atan2(src[index(1, 2)], src[index(2, 2)]);
		float y = (float) Math.atan2(-src[index(0, 2)], Math.sqrt(1.0F - src[index(0, 2)] * src[index(0, 2)]));
		float z = (float) Math.atan2(src[index(0, 1)], src[index(0, 0)]);
		return new ScriptVector3f(x, y, z);
	}

	public ScriptVector3f getEulerAnglesXYZ() {
		float[] src = new float[16];
		store(java.nio.FloatBuffer.wrap(src));
		float x = (float) Math.atan2(-src[index(2, 1)], src[index(2, 2)]);
		float y = (float) Math.atan2(src[index(2, 0)], Math.sqrt(1.0F - src[index(2, 0)] * src[index(2, 0)]));
		float z = (float) Math.atan2(-src[index(1, 0)], src[index(0, 0)]);
		return new ScriptVector3f(x, y, z);
	}

	public ScriptVector3f getEulerAnglesYXZ() {
		float[] src = new float[16];
		store(java.nio.FloatBuffer.wrap(src));
		float x = (float) Math.atan2(-src[index(2, 1)], Math.sqrt(1.0F - src[index(2, 1)] * src[index(2, 1)]));
		float y = (float) Math.atan2(src[index(2, 0)], src[index(2, 2)]);
		float z = (float) Math.atan2(src[index(1, 0)], src[index(1, 1)]);
		return new ScriptVector3f(x, y, z);
	}

	int index(int row, int col) {
		return col * 4 + row;
	}

	@Override
	public int hashCode() {
		return impl.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		return impl.equals(((Matrix4fCompat) o).impl);
	}

	@Override
	public String toString() {
		float[] src = new float[16];
		store(java.nio.FloatBuffer.wrap(src));
		StringBuilder sb = new StringBuilder();
		sb.append("Matrix4f[\n");
		for (int i = 0; i < 4; i++) {
			sb.append("  ");
			for (int j = 0; j < 4; j++) {
				sb.append(src[i * 4 + j]);
				sb.append(", ");
			}
			sb.append("\n");
		}
		sb.append("]");
		return sb.toString();
	}

	public static final Matrix4fCompat IDENTITY = new Matrix4fCompat();

	private static Vector3f toJoml(ScriptVector3f v) {
		return new Vector3f(v.x(), v.y(), v.z());
	}
}
