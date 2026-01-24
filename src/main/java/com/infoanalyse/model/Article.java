package com.infoanalyse.model;

public class Article {
  private String id;
  private String sourceId;
  private String sourceName;
  private String title;
  private String url;
  private String summary;
  private String fetchedAt;

  public Article() {}

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getSourceId() {
    return sourceId;
  }

  public void setSourceId(String sourceId) {
    this.sourceId = sourceId;
  }

  public String getSourceName() {
    return sourceName;
  }

  public void setSourceName(String sourceName) {
    this.sourceName = sourceName;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getFetchedAt() {
    return fetchedAt;
  }

  public void setFetchedAt(String fetchedAt) {
    this.fetchedAt = fetchedAt;
  }
}
