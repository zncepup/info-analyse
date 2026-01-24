package com.infoanalyse.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class AnalysisConfig {
  private final List<String> positiveKeywords;
  private final List<String> negativeKeywords;
  private final int minScore;
  private final int topN;
  private final Map<String, List<String>> marketKeywords;

  private AnalysisConfig(
      List<String> positiveKeywords,
      List<String> negativeKeywords,
      int minScore,
      int topN,
      Map<String, List<String>> marketKeywords) {
    this.positiveKeywords = positiveKeywords;
    this.negativeKeywords = negativeKeywords;
    this.minScore = minScore;
    this.topN = topN;
    this.marketKeywords = marketKeywords;
  }

  public static AnalysisConfig fromProperties(Properties props) {
    List<String> positive = AppConfig.parseCsv(props.getProperty("analysis.positiveKeywords", ""));
    List<String> negative = AppConfig.parseCsv(props.getProperty("analysis.negativeKeywords", ""));
    int minScore = parseInt(props.getProperty("analysis.minScore"), 2);
    int topN = parseInt(props.getProperty("analysis.topN"), 20);

    Map<String, List<String>> marketKeywords = new HashMap<>();
    marketKeywords.put("ashare", AppConfig.parseCsv(props.getProperty("analysis.marketKeywords.ashare", "")));
    marketKeywords.put("hshare", AppConfig.parseCsv(props.getProperty("analysis.marketKeywords.hshare", "")));
    marketKeywords.put("usshare", AppConfig.parseCsv(props.getProperty("analysis.marketKeywords.usshare", "")));

    return new AnalysisConfig(positive, negative, minScore, topN, marketKeywords);
  }

  public List<String> getPositiveKeywords() {
    return positiveKeywords;
  }

  public List<String> getNegativeKeywords() {
    return negativeKeywords;
  }

  public int getMinScore() {
    return minScore;
  }

  public int getTopN() {
    return topN;
  }

  public Map<String, List<String>> getMarketKeywords() {
    return marketKeywords;
  }

  public AnalysisConfig withOverrides(Integer minScore, Integer topN) {
    int resolvedMinScore = minScore == null ? this.minScore : minScore;
    int resolvedTopN = topN == null ? this.topN : topN;
    return new AnalysisConfig(positiveKeywords, negativeKeywords, resolvedMinScore, resolvedTopN, marketKeywords);
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
