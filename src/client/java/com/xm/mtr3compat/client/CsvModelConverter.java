package com.xm.mtr3compat.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * NTE（Sowcer）CSV 模型 → MTR4 可读 OBJ/MTL 的运行时转换。
 * CSV 采用 openBVE 指令集，这里只实现轨道模型实际用到的子集：
 * CreateMeshBuilder / AddVertex / AddFace / AddFace2 / GenerateNormals / LoadTexture / SetTextureCoordinates，
 * 其余指令（Translate/Rotate/Scale/Shear/Mirror/Cube/Cylinder/SetColor/SetEmissiveColor 等）跳过并计数。
 * 每个 CreateMeshBuilder 视为一个独立网格与材质（LoadTexture 作用于该网格所有面），面按扇形三角化。
 * CSV 的 V 轴以上为原点，OBJ 以下为原点，故写入 vt 时取 1 - v。
 */
public final class CsvModelConverter {

	public static final class Result {
		public final String objContent;
		public final String mtlContent;

		private Result(String objContent, String mtlContent) {
			this.objContent = objContent;
			this.mtlContent = mtlContent;
		}
	}

	private CsvModelConverter() {
	}

	public static Result convert(String csvContent, String fileName) {
		final String mtlName = fileName + ".mtl";
		final StringBuilder obj = new StringBuilder();
		final StringBuilder mtl = new StringBuilder();
		obj.append("# mtr3compat: converted from ").append(fileName).append(".csv\n");
		obj.append("mtllib ").append(mtlName).append('\n');
		mtl.append("# mtr3compat: converted from ").append(fileName).append(".csv\n");

		final List<String> unsupported = new ArrayList<>();
		List<float[]> vertices = new ArrayList<>();
		final List<float[]> textureCoordinates = new ArrayList<>();
		List<int[]> faces = new ArrayList<>();
		final List<Boolean> faceDoubleSided = new ArrayList<>();
		String texture = null;
		int vertexOffset = 1;
		int textureCoordinateOffset = 1;
		int normalOffset = 1;
		int meshIndex = 0;

		for (final String rawLine : csvContent.split("\n")) {
			final String line = stripComment(rawLine).trim();
			if (line.isEmpty()) {
				continue;
			}
			final String[] parts = line.split(",", -1);
			final String command = parts[0].trim().toLowerCase(Locale.ENGLISH);
			switch (command) {
				case "createmeshbuilder":
					if (!faces.isEmpty()) {
						meshIndex = writeMesh(obj, mtl, meshIndex, vertices, textureCoordinates, faces, faceDoubleSided, texture, vertexOffset, textureCoordinateOffset, normalOffset);
						vertexOffset += vertices.size();
						textureCoordinateOffset += vertices.size();
						// 每个面写一条 vn（双面面共用同一条法线）
						normalOffset += faces.size();
					}
					vertices = new ArrayList<>();
					textureCoordinates.clear();
					faces = new ArrayList<>();
					faceDoubleSided.clear();
					texture = null;
					break;
				case "addvertex":
					vertices.add(new float[]{(float) parseDouble(parts, 1), (float) parseDouble(parts, 2), (float) parseDouble(parts, 3)});
					textureCoordinates.add(null);
					break;
				case "addface":
				case "addface2": {
					// 该格式每行以逗号结尾，空字段需跳过
					final List<Integer> faceIndices = new ArrayList<>();
					for (int i = 1; i < parts.length; i++) {
						if (!parts[i].trim().isEmpty()) {
							faceIndices.add((int) parseDouble(parts, i));
						}
					}
					final int[] indices = faceIndices.stream().mapToInt(Integer::intValue).toArray();
					if (indices.length >= 3 && validIndices(indices, vertices.size())) {
						faces.add(indices);
						faceDoubleSided.add(command.equals("addface2"));
					}
					break;
				}
				case "settexturecoordinates": {
					final int index = (int) parseDouble(parts, 1);
					if (index >= 0 && index < textureCoordinates.size()) {
						textureCoordinates.set(index, new float[]{(float) parseDouble(parts, 2), (float) parseDouble(parts, 3)});
					}
					break;
				}
				case "loadtexture":
					texture = parts.length > 1 ? parts[1].trim() : null;
					break;
				case "generatenormals":
					// 每个面单独写 vn（平面着色），与 openBVE 行为一致，无需额外处理
					break;
				default:
					unsupported.add(command);
					break;
			}
		}

		if (!faces.isEmpty()) {
			writeMesh(obj, mtl, meshIndex, vertices, textureCoordinates, faces, faceDoubleSided, texture, vertexOffset, textureCoordinateOffset, normalOffset);
		}

		if (!unsupported.isEmpty()) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] CSV 模型包含未处理的指令（已跳过）: {} -> {}", fileName, unsupported);
		}
		return new Result(obj.toString(), mtl.toString());
	}

	private static int writeMesh(StringBuilder obj, StringBuilder mtl, int meshIndex, List<float[]> vertices, List<float[]> textureCoordinates, List<int[]> faces, List<Boolean> faceDoubleSided, String texture, int vertexOffset, int textureCoordinateOffset, int normalOffset) {
		final String materialName = "mesh" + meshIndex;
		mtl.append("newmtl ").append(materialName).append('\n');
		mtl.append("Ka 0 0 0\nKd 1 1 1\nKs 0 0 0\n");
		if (texture != null && !texture.isEmpty()) {
			mtl.append("map_Kd ").append(texture).append('\n');
		}
		mtl.append('\n');

		obj.append("o mesh").append(meshIndex).append('\n');
		for (final float[] vertex : vertices) {
			obj.append("v ").append(vertex[0]).append(' ').append(vertex[1]).append(' ').append(vertex[2]).append('\n');
		}
		for (final float[] textureCoordinate : textureCoordinates) {
			if (textureCoordinate == null) {
				obj.append("vt 0 0\n");
			} else {
				obj.append("vt ").append(textureCoordinate[0]).append(' ').append(1 - textureCoordinate[1]).append('\n');
			}
		}
		obj.append("usemtl ").append(materialName).append('\n');

		int normal = normalOffset;
		for (int faceIndex = 0; faceIndex < faces.size(); faceIndex++) {
			final int[] indices = faces.get(faceIndex);
			final float[] normalVector = computeNormal(vertices, indices);
			obj.append("vn ").append(normalVector[0]).append(' ').append(normalVector[1]).append(' ').append(normalVector[2]).append('\n');
			for (int triangle = 1; triangle < indices.length - 1; triangle++) {
				appendFace(obj, indices[0], indices[triangle], indices[triangle + 1], vertexOffset, textureCoordinateOffset, normal);
			}
			if (faceDoubleSided.get(faceIndex)) {
				for (int triangle = 1; triangle < indices.length - 1; triangle++) {
					appendFace(obj, indices[0], indices[triangle + 1], indices[triangle], vertexOffset, textureCoordinateOffset, normal);
				}
			}
			normal++;
		}
		return meshIndex + 1;
	}

	private static void appendFace(StringBuilder obj, int a, int b, int c, int vertexOffset, int textureCoordinateOffset, int normal) {
		obj.append("f ")
				.append(vertexOffset + a).append('/').append(textureCoordinateOffset + a).append('/').append(normal).append(' ')
				.append(vertexOffset + b).append('/').append(textureCoordinateOffset + b).append('/').append(normal).append(' ')
				.append(vertexOffset + c).append('/').append(textureCoordinateOffset + c).append('/').append(normal).append('\n');
	}

	private static float[] computeNormal(List<float[]> vertices, int[] indices) {
		final float[] p0 = vertices.get(indices[0]);
		final float[] p1 = vertices.get(indices[1]);
		final float[] p2 = vertices.get(indices[2]);
		final float ux = p1[0] - p0[0];
		final float uy = p1[1] - p0[1];
		final float uz = p1[2] - p0[2];
		final float vx = p2[0] - p0[0];
		final float vy = p2[1] - p0[1];
		final float vz = p2[2] - p0[2];
		float nx = uy * vz - uz * vy;
		float ny = uz * vx - ux * vz;
		float nz = ux * vy - uy * vx;
		final float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (length > 1E-6F) {
			nx /= length;
			ny /= length;
			nz /= length;
		} else {
			nx = 0;
			ny = 1;
			nz = 0;
		}
		return new float[]{nx, ny, nz};
	}

	private static boolean validIndices(int[] indices, int vertexCount) {
		for (final int index : indices) {
			if (index < 0 || index >= vertexCount) {
				return false;
			}
		}
		return true;
	}

	private static String stripComment(String line) {
		final int commentIndex = line.indexOf(';');
		return commentIndex < 0 ? line : line.substring(0, commentIndex);
	}

	private static double parseDouble(String[] parts, int index) {
		try {
			return index < parts.length ? Double.parseDouble(parts[index].trim()) : 0;
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
