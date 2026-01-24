package com.infoanalyse.analysis;

import com.infoanalyse.model.Article;
import java.util.List;

public class Opportunity {
  private final Article article;
  private final int score;
  private final String market;
  private final List<String> positiveHits;
  private final List<String> negativeHits;

  public Opportunity(Article article, int score, String market, List<String> positiveHits, List<String> negativeHits) {
    this.article = article;
    this.score = score;
    this.market = market;
    this.positiveHits = positiveHits;
    this.negativeHits = negativeHits;
  }

  public Article getArticle() {
    return article;
  }

  public int getScore() {
    return score;
  }

  public String getMarket() {
    return market;
  }

  public List<String> getPositiveHits() {
    return positiveHits;
  }

  public List<String> getNegativeHits() {
    return negativeHits;
  }
}
