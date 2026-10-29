package com.xm.mtr3compat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackResources;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 包发现：扫描原版 resourcepacks 目录下的 MTR 资源包（zip 或目录，含 assets/mtr/mtr_custom_resources.json
 * 或 NTE 轨道定义 assets/mtrsteamloco/rails/*.json），并直接读磁盘读取资源包文件。
 * 勾选（MTR3 模式）的包由 ResourceManagerHelperMixin 拦截 MTR4/JCM 解析。
 */
public final class Mtr3CompatPacks {

	private Mtr3CompatPacks() {
	}

	public static Path packsDir() {
		return Minecraft.getInstance().getResourcePackDirectory();
	}

	/** resourcepacks 目录下的全部资源包（zip 文件与目录）。 */
	public static ObjectArrayList<Path> packs() {
		final ObjectArrayList<Path> result = new ObjectArrayList<>();
		final Path dir = packsDir();
		if (!Files.isDirectory(dir)) {
			return result;
		}
		try (final Stream<Path> stream = Files.list(dir)) {
			stream.forEach(path -> {
				final String name = path.getFileName().toString().toLowerCase(Locale.ENGLISH);
				if (Files.isDirectory(path) || name.endsWith(".zip")) {
					result.add(path);
				}
			});
		} catch (IOException e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 扫描 resourcepacks 目录失败", e);
		}
		return result;
	}

	/** 全部 MTR 资源包：含 assets/mtr/mtr_custom_resources.json（车型等），或含 NTE 轨道定义（assets/mtrsteamloco/rails/*.json，纯轨道包）。 */
	public static ObjectArrayList<Path> mtrPacks() {
		final ObjectArrayList<Path> result = new ObjectArrayList<>();
		for (final Path path : packs()) {
			if (readPackFile(path, "assets/mtr/mtr_custom_resources.json") != null || !listPackFiles(path, "assets/mtrsteamloco/rails/", ".json").isEmpty()) {
				result.add(path);
			}
		}
		return result;
	}

	/** 按包名（可含或不含 .zip）解析磁盘路径，找不到返回 null。 */
	public static Path resolve(String packName) {
		final String key = Mtr3CompatConfig.normalize(packName);
		for (final Path path : packs()) {
			if (packBaseName(path).equals(key)) {
				return path;
			}
		}
		return null;
	}

	/** 读取资源包内的文件（zip 条目名使用正斜杠；目录包直接解析路径）。找不到返回 null。 */
	public static byte[] readPackFile(Path packPath, String entryName) {
		try {
			if (Files.isDirectory(packPath)) {
				final Path file = packPath.resolve(entryName.replace('/', java.io.File.separatorChar));
				return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
			}
			try (final ZipFile zipFile = new ZipFile(packPath.toFile())) {
				final ZipEntry entry = zipFile.getEntry(entryName);
				if (entry == null) {
					return null;
				}
				try (final InputStream inputStream = zipFile.getInputStream(entry)) {
					final ByteArrayOutputStream out = new ByteArrayOutputStream();
					final byte[] buffer = new byte[8192];
					int len;
					while ((len = inputStream.read(buffer)) > 0) {
						out.write(buffer, 0, len);
					}
					return out.toByteArray();
				}
			}
		} catch (IOException e) {
			return null;
		}
	}

	/** 列出资源包内以 prefix 开头、以 suffix 结尾的条目名（zip 与目录包统一用正斜杠，不含目录条目）。 */
	public static ObjectArrayList<String> listPackFiles(Path packPath, String prefix, String suffix) {
		final ObjectArrayList<String> result = new ObjectArrayList<>();
		try {
			if (Files.isDirectory(packPath)) {
				final Path dir = packPath.resolve(prefix.replace('/', java.io.File.separatorChar));
				if (Files.isDirectory(dir)) {
					try (final Stream<Path> stream = Files.walk(dir)) {
						stream.filter(Files::isRegularFile).forEach(file -> {
							final String relative = packPath.relativize(file).toString().replace(java.io.File.separatorChar, '/');
							if (relative.startsWith(prefix) && relative.endsWith(suffix)) {
								result.add(relative);
							}
						});
					}
				}
			} else {
				try (final ZipFile zipFile = new ZipFile(packPath.toFile())) {
					final java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
					while (entries.hasMoreElements()) {
						final ZipEntry entry = entries.nextElement();
						final String name = entry.getName();
						if (!entry.isDirectory() && name.startsWith(prefix) && name.endsWith(suffix)) {
							result.add(name);
						}
					}
				}
			}
		} catch (IOException e) {
			Mtr3CompatClient.LOGGER.warn("[mtr3compat] 列出资源包条目失败: " + packPath, e);
		}
		return result;
	}

	public static String packBaseName(Path path) {
		final String name = path.getFileName().toString();
		return Mtr3CompatConfig.normalize(name);
	}

	/**
	 * 当前由原版资源包管理器实际加载的 resourcepacks 目录包名（不含 .zip）。
	 * 未启用的包不在这个集合里：它们的车虽然能从磁盘读到定义，但贴图/脚本/JSON 一律读不到，
	 * 注册出来只会产生一堆 "Script ... is missing" 报错，所以调用方应直接跳过。
	 */
	public static Set<String> loadedPackNames() {
		final Set<String> names = new HashSet<>();
		final Minecraft client = Minecraft.getInstance();
		if (client == null || client.getResourceManager() == null) {
			return names;
		}
		for (final PackResources pack : client.getResourceManager().listPacks().toList()) {
			final String id = pack.packId();
			if (id.startsWith("file/")) {
				names.add(Mtr3CompatConfig.normalize(id.substring("file/".length())));
			}
		}
		return names;
	}
}
