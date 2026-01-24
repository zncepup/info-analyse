package com.infoanalyse.config;

import java.util.Properties;

public class SourceConfig {
  private final String id;
  private final String name;
  private final String url;
  private final String articleSelector;
  private final String titleSelector;
  private final String linkSelector;
  private final String summarySelector;

  private SourceConfig(
      String id,
      String name,
      String url,
      String articleSelector,
      String titleSelector,
      String linkSelector,
      String summarySelector) {
    this.id = id;
    this.name = name;
    this.url = url;
    this.articleSelector = articleSelector;
    this.titleSelector = titleSelector;
    this.linkSelector = linkSelector;
    this.summarySelector = summarySelector;
  }

  public static SourceConfig fromProperties(Properties props, String id) {
    String prefix = "source." + id + ".";
    String name = props.getProperty(prefix + "name");
    String url = props.getProperty(prefix + "url");
    String articleSelector = props.getProperty(prefix + "articleSelector");
    String titleSelector = props.getProperty(prefix + "titleSelector");
    String linkSelector = props.getProperty(prefix + "linkSelector");
    String summarySelector = props.getProperty(prefix + "summarySelector");

    if (url == null || url.trim().isEmpty() || articleSelector == null || articleSelector.trim().isEmpty()) {
      return null;
    }

    return new SourceConfig(
        id,
        name == null ? id : name,
        url,
        articleSelector,
        titleSelector,
        linkSelector,
        summarySelector);
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getUrl() {
    return url;
  }

  public String getArticleSelector() {
    return articleSelector;
  }

  public String getTitleSelector() {
    return titleSelector;
  }

  public String getLinkSelector() {
    return linkSelector;
  }

  public String getSummarySelector() {
    return summarySelector;
  }
}
