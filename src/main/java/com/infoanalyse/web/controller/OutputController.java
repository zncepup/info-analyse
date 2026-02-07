package com.infoanalyse.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/outputs")
public class OutputController {
    private final Path outputDir = Path.of("output").toAbsolutePath().normalize();

    @GetMapping
    public List<AuthorInfo> listAuthors() {
        if (!Files.exists(outputDir)) {
            return List.of();
        }

        List<AuthorInfo> authors = new ArrayList<>();
        try (Stream<Path> stream = Files.list(outputDir)) {
            stream.filter(Files::isDirectory)
                    .forEach(dir -> {
                        try {
                            String name = dir.getFileName().toString();
                            FileTime lastModified = Files.getLastModifiedTime(dir);
                            int mdCount = countFiles(dir, ".md");
                            int docCount = countFiles(dir, ".docx");
                            authors.add(new AuthorInfo(name, mdCount, docCount, lastModified.toMillis()));
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }

        authors.sort(Comparator.comparingLong(AuthorInfo::lastModified).reversed());
        return authors;
    }

    @GetMapping("/{author}/files")
    public List<FileInfo> listFiles(@PathVariable("author") String author) {
        Path authorDir = resolveAuthorDir(author);
        List<FileInfo> files = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(authorDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> hasExtension(p, ".md") || hasExtension(p, ".docx"))
                    .filter(p -> !p.getFileName().toString().equalsIgnoreCase("INDEX.md"))
                    .forEach(p -> {
                        try {
                            String relative = authorDir.relativize(p).toString().replace("\\", "/");
                            String fileName = p.getFileName().toString();
                            long size = Files.size(p);
                            long modified = Files.getLastModifiedTime(p).toMillis();
                            String type = hasExtension(p, ".docx") ? "docx" : "md";
                            String downloadUrl = "/output/" + author + "/" + relative;
                            String viewUrl = type.equals("md") ? "/view/" + author + "/" + fileName : null;
                            boolean analyzed = type.equals("md") && isAnalyzed(p);
                            String displayName = type.equals("md") ? readMarkdownTitle(p).orElse(fileName) : fileName;
                            files.add(new FileInfo(displayName, relative, size, modified, type, viewUrl, downloadUrl, analyzed));
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }

        files.sort(Comparator.comparingLong(FileInfo::lastModified).reversed());
        return files;
    }

    @GetMapping("/exports")
    public List<FileInfo> listExports() {
        if (!Files.exists(outputDir)) {
            return List.of();
        }

        List<FileInfo> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(outputDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> hasExtension(p, ".docx"))
                    .forEach(p -> {
                        try {
                            Path authorDir = p.getParent();
                            while (authorDir != null && authorDir.getParent() != null
                                    && !authorDir.getParent().equals(outputDir)) {
                                authorDir = authorDir.getParent();
                            }
                            if (authorDir == null || authorDir.getParent() == null) {
                                return;
                            }
                            String author = authorDir.getFileName().toString();
                            String relative = authorDir.relativize(p).toString().replace("\\", "/");
                            String name = p.getFileName().toString();
                            long size = Files.size(p);
                            long modified = Files.getLastModifiedTime(p).toMillis();
                            String downloadUrl = "/output/" + author + "/" + relative;
                            files.add(new FileInfo(name, relative, size, modified, "docx", null, downloadUrl, false));
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }

        files.sort(Comparator.comparingLong(FileInfo::lastModified).reversed());
        return files;
    }

    private Path resolveAuthorDir(String author) {
        if (author == null || author.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "author is required");
        }
        Path dir = outputDir.resolve(author).normalize();
        if (!dir.startsWith(outputDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid author path");
        }
        if (!Files.exists(dir)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "author not found");
        }
        return dir;
    }

    private int countFiles(Path dir, String ext) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            return (int) stream.filter(Files::isRegularFile)
                    .filter(p -> hasExtension(p, ext))
                    .count();
        }
    }

    private boolean hasExtension(Path path, String ext) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ext);
    }

    private java.util.Optional<String> readMarkdownTitle(Path path) {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            for (int i = 0; i < 30; i++) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                String trimmed = line.trim();
                if (trimmed.startsWith("# ")) {
                    return java.util.Optional.of(trimmed.substring(2).trim());
                }
            }
        } catch (IOException ignored) {
        }
        return java.util.Optional.empty();
    }

    public record AuthorInfo(String name, int mdCount, int docCount, long lastModified) {}

    public record FileInfo(String name, String relativePath, long size, long lastModified, String type,
                           String viewUrl, String downloadUrl, boolean analyzed) {}

    private boolean isAnalyzed(Path path) {
        try {
            String content = Files.readString(path);
            if (!content.contains("## AI ")) {
                return false;
            }
            String lower = content.toLowerCase(Locale.ROOT);
            return !content.contains("分析失败")
                    && !content.contains("DeepSeek API 未配置")
                    && !content.contains("API 调用失败")
                    && !lower.contains("invalid header value")
                    && !lower.contains("analysis failed");
        } catch (IOException e) {
            return false;
        }
    }
}
