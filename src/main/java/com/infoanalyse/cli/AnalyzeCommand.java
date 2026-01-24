package com.infoanalyse.cli;

import com.infoanalyse.analysis.Analyzer;
import com.infoanalyse.analysis.Opportunity;
import com.infoanalyse.config.AnalysisConfig;
import com.infoanalyse.config.AppConfig;
import com.infoanalyse.model.Article;
import com.infoanalyse.store.FileArticleStore;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "analyze", description = "Analyze stored articles and print opportunities.")
public class AnalyzeCommand implements Callable<Integer> {
  @Option(names = "--input", description = "Input JSONL path for crawled articles.")
  private Path inputPath;

  @Option(names = "--top", description = "Limit number of opportunities printed.")
  private Integer topN;

  @Option(names = "--min-score", description = "Minimum score to treat as opportunity.")
  private Integer minScore;

  @Override
  public Integer call() throws Exception {
    AppConfig config = AppConfig.load();
    AnalysisConfig analysis = config.getAnalysisConfig().withOverrides(minScore, topN);

    Path path = inputPath == null ? config.getOutputPath() : inputPath;
    FileArticleStore store = new FileArticleStore(path);
    List<Article> articles = store.readAll();

    Analyzer analyzer = new Analyzer(analysis);
    List<Opportunity> results = analyzer.analyze(articles);

    if (results.isEmpty()) {
      System.out.println("No opportunities found.");
      return 0;
    }

    for (Opportunity opportunity : results) {
      Article article = opportunity.getArticle();
      System.out.printf(
          "[%d] %s (%s) - %s%n",
          opportunity.getScore(),
          article.getTitle(),
          opportunity.getMarket(),
          article.getSourceName());
      if (article.getUrl() != null && !article.getUrl().isEmpty()) {
        System.out.printf("  %s%n", article.getUrl());
      }
      if (article.getSummary() != null && !article.getSummary().isEmpty()) {
        System.out.printf("  %s%n", article.getSummary());
      }
      if (!opportunity.getPositiveHits().isEmpty() || !opportunity.getNegativeHits().isEmpty()) {
        System.out.printf("  signals: +%s -%s%n", opportunity.getPositiveHits(), opportunity.getNegativeHits());
      }
    }

    return 0;
  }
}
