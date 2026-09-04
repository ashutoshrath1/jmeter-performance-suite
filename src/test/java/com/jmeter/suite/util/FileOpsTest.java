package com.jmeter.suite.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies artifact archiving and cleanup helpers.
 */
class FileOpsTest {

    /**
     * Creates a nested directory tree with two files.
     */
    private Path sampleTree(Path dir) throws IOException {
        Path root = dir.resolve("html");
        Files.createDirectories(root.resolve("assets"));
        Files.write(root.resolve("index.html"), "<h1>report</h1>".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("assets").resolve("app.js"), "console.log(1)".getBytes(StandardCharsets.UTF_8));
        return root;
    }

    @Test
    void zipsDirectoryPreservingRelativePaths(@TempDir Path dir) throws IOException {
        Path root = sampleTree(dir);
        Path zip = dir.resolve("report.zip");

        FileOps.zipDirectory(root, zip);

        assertTrue(Files.exists(zip));
        List<String> entries = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            zf.stream().map(ZipEntry::getName).forEach(entries::add);
        }
        assertEquals(2, entries.size());
        assertTrue(entries.contains("index.html"), "entries: " + entries);
        assertTrue(entries.stream().anyMatch(name -> name.endsWith("app.js")),
                "nested files keep their relative path: " + entries);
    }

    @Test
    void deletesDirectoryTreeRecursively(@TempDir Path dir) throws IOException {
        Path root = sampleTree(dir);

        FileOps.deleteDir(root);

        assertFalse(Files.exists(root));
    }

    @Test
    void deletingAMissingDirectoryIsANoOp(@TempDir Path dir) throws IOException {
        FileOps.deleteDir(dir.resolve("never-existed"));
    }
}
