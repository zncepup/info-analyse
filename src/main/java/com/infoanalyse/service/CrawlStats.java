package com.infoanalyse.service;

import java.nio.file.Path;

public class CrawlStats {
  private final int fetched;
  private final int saved;
  private final int failedSources;
  private final Path outputPath;

  public CrawlStats(int fetched, int saved, int failedSources, Path outputPath) {
    this.fetched = fetched;
    this.saved = saved;
    this.failedSources = failedSources;
    this.outputPath = outputPath;
  }

  public int getFetched() {
    return fetched;
  }

  public int getSaved() {
    return saved;
  }

  public int getFailedSources() {
    return failedSources;
  }

  public Path getOutputPath() {
    return outputPath;
  }
}
