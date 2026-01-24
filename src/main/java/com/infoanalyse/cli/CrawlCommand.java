package com.infoanalyse.cli;

import com.infoanalyse.config.AppConfig;
import com.infoanalyse.service.CrawlService;
import com.infoanalyse.service.CrawlStats;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "crawl", description = "Fetch articles and save them locally.")
public class CrawlCommand implements Callable<Integer> {
  @Option(names = "--sources", description = "Comma-separated source ids to crawl.")
  private String sources;

  @Option(names = "--output", description = "Output JSONL path for crawled articles.")
  private Path outputPath;

  @Option(names = "--max-per-source", description = "Max items to fetch per source.")
  private Integer maxPerSource;

  @Override
  public Integer call() throws Exception {
    AppConfig config = AppConfig.load();
    List<String> sourceIds = AppConfig.parseCsv(sources);

    CrawlService service = new CrawlService(config);
    CrawlStats stats = service.run(sourceIds, outputPath, maxPerSource);

    System.out.printf(
        "Fetched %d items, saved %d new items. Failed sources: %d. Output: %s%n",
        stats.getFetched(),
        stats.getSaved(),
        stats.getFailedSources(),
        stats.getOutputPath());
    return 0;
  }
}
