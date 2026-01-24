package com.infoanalyse;

import com.infoanalyse.cli.AnalyzeCommand;
import com.infoanalyse.cli.CrawlCommand;
import com.infoanalyse.cli.ScheduleCommand;
import com.infoanalyse.cli.SourcesCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "info-analyse",
    mixinStandardHelpOptions = true,
    version = "info-analyse 0.1.0",
    description = "Fetches market news and prints basic investment signals.",
    subcommands = {
        CrawlCommand.class,
        AnalyzeCommand.class,
        ScheduleCommand.class,
        SourcesCommand.class
    }
)
public class InfoAnalyseApp implements Runnable {
  public static void main(String[] args) {
    int exitCode = new CommandLine(new InfoAnalyseApp()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }
}
