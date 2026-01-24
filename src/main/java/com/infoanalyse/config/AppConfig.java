package com.infoanalyse.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class AppConfig {
  private final String appName;
  private final String userAgent;
  private final int timeoutMs;
  private final int intervalMinutes;
  private final List<String> sourceIds;
  private final Map<String, SourceConfig> sources;
  private final Path outputPath;
  private final int maxPerSource;
  private final AnalysisConfig analysisConfig;

  private AppConfig(Properties props) {
    this.appName = props.getProperty("app.name", "info-analyse");
    this.userAgent = props.getProperty("crawl.userAgent", "Mozilla/5.0");
    this.timeoutMs = parseInt(props.getProperty("crawl.timeoutMs"), 10000);
    this.intervalMinutes = parseInt(props.getProperty("crawl.intervalMinutes"), 30);
    this.sourceIds = parseCsv(props.getProperty("crawl.sources", ""));
    this.outputPath = Paths.get(props.getProperty("crawl.outputPath", "data/articles.jsonl"));
    this.maxPerSource = parseInt(props.getProperty("crawl.maxPerSource"), 30);
    this.sources = loadSources(props, this.sourceIds);
    this.analysisConfig = AnalysisConfig.fromProperties(props);
  }

  public static AppConfig load() {
    Properties props = new Properties();
    try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
      if (in != null) {
        props.load(in);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load application.properties", e);
    }
    return new AppConfig(props);
  }

  public String getAppName() {
    return appName;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public int getTimeoutMs() {
    return timeoutMs;
  }

  public int getIntervalMinutes() {
    return intervalMinutes;
  }

  public List<String> getSourceIds() {
    return Collections.unmodifiableList(sourceIds);
  }

  public Map<String, SourceConfig> getSources() {
    return Collections.unmodifiableMap(sources);
  }

  public Path getOutputPath() {
    return outputPath;
  }

  public int getMaxPerSource() {
    return maxPerSource;
  }

  public AnalysisConfig getAnalysisConfig() {
    return analysisConfig;
  }

  public List<SourceConfig> resolveSources(List<String> ids) {
    List<String> resolvedIds = ids == null || ids.isEmpty() ? this.sourceIds : ids;
    List<SourceConfig> resolved = new ArrayList<>();
    for (String id : resolvedIds) {
      SourceConfig source = sources.get(id);
      if (source != null) {
        resolved.add(source);
      }
    }
    return resolved;
  }

  private static Map<String, SourceConfig> loadSources(Properties props, List<String> ids) {
    Map<String, SourceConfig> map = new HashMap<>();
    for (String id : ids) {
      SourceConfig source = SourceConfig.fromProperties(props, id);
      if (source != null) {
        map.put(id, source);
      }
    }
    return map;
  }

  public static List<String> parseCsv(String value) {
    if (value == null || value.trim().isEmpty()) {
      return Collections.emptyList();
    }
    String[] parts = value.split(",");
    List<String> result = new ArrayList<>();
    for (String part : parts) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return result;
  }

  private static int parseInt(String value, int fallback) {
    if (value == null || value.trim().isEmpty()) {
      return fallback;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
