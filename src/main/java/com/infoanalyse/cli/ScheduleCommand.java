package com.infoanalyse.cli;

import com.infoanalyse.analysis.Analyzer;
import com.infoanalyse.analysis.Opportunity;
import com.infoanalyse.config.AnalysisConfig;
import com.infoanalyse.config.AppConfig;
import com.infoanalyse.model.Article;
import com.infoanalyse.service.CrawlService;
import com.infoanalyse.service.CrawlStats;
import com.infoanalyse.store.FileArticleStore;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "schedule", description = "Run crawler on an interval.")
public class ScheduleCommand implements Callable<Integer> {
  private static final Logger logger = LoggerFactory.getLogger(ScheduleCommand.class);

  @Option(names = "--interval-minutes", description = "Interval in minutes.")
  private Integer intervalMinutes;

  @Option(names = "--sources", description = "Comma-separated source ids to crawl.")
  private String sources;

  @Option(names = "--output", description = "Output JSONL path for crawled articles.")
  private Path outputPath;

  @Option(names = "--max-per-source", description = "Max items to fetch per source.")
  private Integer maxPerSource;

  @Option(names = "--run-analyze", description = "Run analysis after each crawl.")
  private boolean runAnalyze;

  @Option(names = "--top", description = "Limit number of opportunities printed.")
  private Integer topN;

  @Option(names = "--min-score", description = "Minimum score to treat as opportunity.")
  private Integer minScore;

  @Override
  public Integer call() throws Exception {
    AppConfig config = AppConfig.load();
    int interval = intervalMinutes == null ? config.getIntervalMinutes() : intervalMinutes;
    List<String> sourceIds = AppConfig.parseCsv(sources);

    CrawlService service = new CrawlService(config);
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    CountDownLatch latch = new CountDownLatch(1);

    Runnable task = () -> {
      try {
        logger.info("Run started at {}", Instant.now());
        CrawlStats stats = service.run(sourceIds, outputPath, maxPerSource);
        logger.info(
            "Run finished. Fetched: {}, Saved: {}, Failed sources: {}",
            stats.getFetched(),
            stats.getSaved(),
            stats.getFailedSources());

        if (runAnalyze) {
          AnalysisConfig analysis = config.getAnalysisConfig().withOverrides(minScore, topN);
          FileArticleStore store = new FileArticleStore(stats.getOutputPath());
          List<Article> articles = store.readAll();
          Analyzer analyzer = new Analyzer(analysis);
          List<Opportunity> results = analyzer.analyze(articles);
          if (results.isEmpty()) {
            System.out.println("No opportunities found.");
          } else {
            for (Opportunity opportunity : results) {
              Article article = opportunity.getArticle();
              System.out.printf(
                  "[%d] %s (%s) - %s%n",
                  opportunity.getScore(),
                  article.getTitle(),
                  opportunity.getMarket(),
                  article.getSourceName());
            }
          }
        }
      } catch (Exception e) {
        logger.warn("Scheduled run failed: {}", e.getMessage());
      }
    };

    scheduler.scheduleWithFixedDelay(task, 0, interval, TimeUnit.MINUTES);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  logger.info("Shutting down scheduler.");
                  scheduler.shutdown();
                  latch.countDown();
                }));

    latch.await();
    return 0;
  }
}
