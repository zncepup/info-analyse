package com.infoanalyse.service;

import com.infoanalyse.config.AppConfig;
import com.infoanalyse.config.SourceConfig;
import com.infoanalyse.crawler.HtmlCssCrawler;
import com.infoanalyse.model.Article;
import com.infoanalyse.store.FileArticleStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrawlService {
  private static final Logger logger = LoggerFactory.getLogger(CrawlService.class);

  private final AppConfig config;
  private final HtmlCssCrawler crawler;

  public CrawlService(AppConfig config) {
    this.config = config;
    this.crawler = new HtmlCssCrawler(config);
  }

  public CrawlStats run(List<String> sourceIds, Path outputPath, Integer maxPerSourceOverride) throws IOException {
    Path targetPath = outputPath == null ? config.getOutputPath() : outputPath;
    FileArticleStore store = new FileArticleStore(targetPath);
    Set<String> existingIds = store.loadIds();

    int fetched = 0;
    int saved = 0;
    int failedSources = 0;
    List<Article> toSave = new ArrayList<>();

    List<SourceConfig> sources = config.resolveSources(sourceIds);
    if (sources.isEmpty()) {
      logger.warn("No valid sources configured.");
    }

    for (SourceConfig source : sources) {
      try {
        int maxPerSource = maxPerSourceOverride == null ? config.getMaxPerSource() : maxPerSourceOverride;
        List<Article> items = crawler.crawl(source, maxPerSource);
        fetched += items.size();
        for (Article article : items) {
          String id = article.getId();
          if (id == null || id.isEmpty()) {
            continue;
          }
          if (existingIds.add(id)) {
            toSave.add(article);
            saved++;
          }
        }
      } catch (IOException e) {
        failedSources++;
        logger.warn("Failed to crawl source {}: {}", source.getName(), e.getMessage());
      }
    }

    store.append(toSave);
    return new CrawlStats(fetched, saved, failedSources, targetPath);
  }
}
