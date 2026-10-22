package com.xm.mtr3compat.client.ante;

import com.google.common.collect.Lists;
import net.minecraft.nbt.*;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * NBT -> SNBT 字符串访问器（照抄 cn.zbx1425.mtrsteamloco.scripting.util.JsonStringTagVisitor，
 * 供 WrappedEntity.getNBT 使用）。mojmap 1.20.1 的 net.minecraft.nbt API 与 ANTE 源码一致。
 */
public class JsonStringTagVisitor implements TagVisitor {
	private static final Pattern SIMPLE_VALUE = Pattern.compile("[A-Za-z0-9._+-]+");
	private final StringBuilder builder = new StringBuilder();

	public String visit(Tag tag) {
		tag.accept(this);
		return this.builder.toString();
	}

	public void visitString(StringTag tag) {
		this.builder.append(StringTag.quoteAndEscape(tag.getAsString()));
	}

	public void visitByte(ByteTag tag) {
		this.builder.append(tag.getAsNumber());
	}

	public void visitShort(ShortTag tag) {
		this.builder.append(tag.getAsNumber());
	}

	public void visitInt(IntTag tag) {
		this.builder.append(tag.getAsNumber());
	}

	public void visitLong(LongTag tag) {
		this.builder.append(tag.getAsNumber());
	}

	public void visitFloat(FloatTag tag) {
		this.builder.append(tag.getAsFloat());
	}

	public void visitDouble(DoubleTag tag) {
		this.builder.append(tag.getAsDouble());
	}

	public void visitByteArray(ByteArrayTag tag) {
		this.builder.append("[");
		byte[] bytes = tag.getAsByteArray();
		for (int i = 0; i < bytes.length; ++i) {
			if (i != 0) {
				this.builder.append(',');
			}
			this.builder.append((int) bytes[i]);
		}
		this.builder.append(']');
	}

	public void visitIntArray(IntArrayTag tag) {
		this.builder.append("[");
		int[] ints = tag.getAsIntArray();
		for (int i = 0; i < ints.length; ++i) {
			if (i != 0) {
				this.builder.append(',');
			}
			this.builder.append(ints[i]);
		}
		this.builder.append(']');
	}

	public void visitLongArray(LongArrayTag tag) {
		this.builder.append("[");
		long[] longs = tag.getAsLongArray();
		for (int i = 0; i < longs.length; ++i) {
			if (i != 0) {
				this.builder.append(',');
			}
			this.builder.append(longs[i]);
		}
		this.builder.append(']');
	}

	public void visitList(ListTag tag) {
		this.builder.append('[');
		for (int i = 0; i < tag.size(); ++i) {
			if (i != 0) {
				this.builder.append(',');
			}
			this.builder.append((new JsonStringTagVisitor()).visit(tag.get(i)));
		}
		this.builder.append(']');
	}

	public void visitCompound(CompoundTag tag) {
		this.builder.append('{');
		List<String> keys = Lists.newArrayList(tag.getAllKeys());
		Collections.sort(keys);
		for (String key : keys) {
			if (this.builder.length() != 1) {
				this.builder.append(',');
			}
			this.builder.append('\"').append(handleEscape(key)).append("\":").append((new JsonStringTagVisitor()).visit(tag.get(key)));
		}
		this.builder.append('}');
	}

	protected static String handleEscape(String name) {
		return SIMPLE_VALUE.matcher(name).matches() ? name : StringTag.quoteAndEscape(name);
	}

	public void visitEnd(EndTag tag) {
		this.builder.append("END");
	}
}
