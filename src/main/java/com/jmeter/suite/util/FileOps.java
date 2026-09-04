package com.jmeter.suite.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Filesystem helpers for preparing and packaging run artifacts.
 */
public final class FileOps {

    /**
     * Prevents instantiation of this utility type.
     */
    private FileOps() {
    }

    /**
     * Recursively removes a directory tree, ignoring individual deletion failures.
     *
     * <p>Best-effort by design: these are regenerable artifacts from a previous run, and a locked
     * file should not abort the run that is about to replace them.
     */
    public static void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // best-effort cleanup of generated artifacts
                        }
                    });
        }
    }

    /**
     * Archives every file under a directory into a zip, preserving relative paths.
     */
    public static void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile));
             Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry entry = new ZipEntry(sourceDir.relativize(path).toString());
                        try {
                            zos.putNextEntry(entry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }
}
