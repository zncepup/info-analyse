package com.infoanalyse.cli;

import com.infoanalyse.config.AppConfig;
import com.infoanalyse.config.SourceConfig;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

@Command(name = "sources", description = "List configured sources.")
public class SourcesCommand implements Callable<Integer> {
  @Override
  public Integer call() {
    AppConfig config = AppConfig.load();
    List<SourceConfig> sources = config.resolveSources(config.getSourceIds());
    if (sources.isEmpty()) {
      System.out.println("No sources configured.");
      return 0;
    }
    for (SourceConfig source : sources) {
      System.out.printf("%s\t%s\t%s%n", source.getId(), source.getName(), source.getUrl());
    }
    return 0;
  }
}
