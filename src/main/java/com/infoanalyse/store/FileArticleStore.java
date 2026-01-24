package com.infoanalyse.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infoanalyse.model.Article;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileArticleStore {
  private final Path path;
  private final ObjectMapper mapper;

  public FileArticleStore(Path path) {
    this.path = path;
    this.mapper = new ObjectMapper();
  }

  public Set<String> loadIds() throws IOException {
    Set<String> ids = new HashSet<>();
    if (!Files.exists(path)) {
      return ids;
    }

    try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        try {
          Article article = mapper.readValue(trimmed, Article.class);
          if (article.getId() != null) {
            ids.add(article.getId());
          }
        } catch (IOException ignored) {
          // Skip malformed lines.
        }
      }
    }

    return ids;
  }

  public List<Article> readAll() throws IOException {
    List<Article> articles = new ArrayList<>();
    if (!Files.exists(path)) {
      return articles;
    }

    try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        try {
          articles.add(mapper.readValue(trimmed, Article.class));
        } catch (IOException ignored) {
          // Skip malformed lines.
        }
      }
    }

    return articles;
  }

  public void append(List<Article> articles) throws IOException {
    if (articles == null || articles.isEmpty()) {
      return;
    }

    Path parent = path.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    try (BufferedWriter writer = Files.newBufferedWriter(
        path,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND)) {
      for (Article article : articles) {
        writer.write(mapper.writeValueAsString(article));
        writer.newLine();
      }
    }
  }

  public Path getPath() {
    return path;
  }
}
