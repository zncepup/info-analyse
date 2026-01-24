package com.infoanalyse.analysis;

import com.infoanalyse.config.AnalysisConfig;
import com.infoanalyse.model.Article;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class Analyzer {
  private final AnalysisConfig config;

  public Analyzer(AnalysisConfig config) {
    this.config = config;
  }

  public List<Opportunity> analyze(List<Article> articles) {
    List<Opportunity> results = new ArrayList<>();
    if (articles == null || articles.isEmpty()) {
      return results;
    }

    for (Article article : articles) {
      String text = normalize(article.getTitle() + " " + article.getSummary());
      List<String> positiveHits = findHits(text, config.getPositiveKeywords());
      List<String> negativeHits = findHits(text, config.getNegativeKeywords());
      int score = positiveHits.size() - negativeHits.size();

      if (score < config.getMinScore()) {
        continue;
      }

      String market = detectMarket(text, config.getMarketKeywords());
      results.add(new Opportunity(article, score, market, positiveHits, negativeHits));
    }

    results.sort(Comparator.comparingInt(Opportunity::getScore).reversed());
    if (results.size() > config.getTopN()) {
      return results.subList(0, config.getTopN());
    }
    return results;
  }

  private static List<String> findHits(String text, List<String> keywords) {
    List<String> hits = new ArrayList<>();
    if (keywords == null || keywords.isEmpty()) {
      return hits;
    }
    for (String keyword : keywords) {
      if (keyword.isEmpty()) {
        continue;
      }
      String normalized = normalize(keyword);
      if (!normalized.isEmpty() && text.contains(normalized)) {
        hits.add(keyword);
      }
    }
    return hits;
  }

  private static String detectMarket(String text, Map<String, List<String>> markets) {
    for (Map.Entry<String, List<String>> entry : markets.entrySet()) {
      for (String keyword : entry.getValue()) {
        if (keyword.isEmpty()) {
          continue;
        }
        String normalized = normalize(keyword);
        if (!normalized.isEmpty() && text.contains(normalized)) {
          return entry.getKey();
        }
      }
    }
    return "unknown";
  }

  private static String normalize(String text) {
    if (text == null) {
      return "";
    }
    return text.toLowerCase();
  }
}
