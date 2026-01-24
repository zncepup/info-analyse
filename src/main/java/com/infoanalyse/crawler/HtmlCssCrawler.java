package com.infoanalyse.crawler;

import com.infoanalyse.config.AppConfig;
import com.infoanalyse.config.SourceConfig;
import com.infoanalyse.model.Article;
import com.infoanalyse.util.Hashing;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HtmlCssCrawler {
  private static final Logger logger = LoggerFactory.getLogger(HtmlCssCrawler.class);

  private final AppConfig config;

  public HtmlCssCrawler(AppConfig config) {
    this.config = config;
  }

  public List<Article> crawl(SourceConfig source, int maxPerSource) throws IOException {
    List<Article> articles = new ArrayList<>();

    Document doc = Jsoup.connect(source.getUrl())
        .userAgent(config.getUserAgent())
        .timeout(config.getTimeoutMs())
        .get();

    Elements items = doc.select(source.getArticleSelector());
    int count = 0;
    for (Element item : items) {
      if (count >= maxPerSource) {
        break;
      }
      String title = textOf(selectFirst(item, source.getTitleSelector()));
      if (title.isEmpty()) {
        continue;
      }

      Element linkEl = selectFirst(item, source.getLinkSelector());
      String url = linkEl != null ? linkEl.absUrl("href") : "";
      if (url.isEmpty() && linkEl != null) {
        url = linkEl.attr("href");
      }

      String summary = textOf(selectFirst(item, source.getSummarySelector()));

      Article article = new Article();
      article.setSourceId(source.getId());
      article.setSourceName(source.getName());
      article.setTitle(title);
      article.setUrl(url);
      article.setSummary(summary);
      article.setFetchedAt(Instant.now().toString());
      article.setId(Hashing.sha1(source.getId() + "|" + url + "|" + title));

      articles.add(article);
      count++;
    }

    logger.info("Fetched {} items from {}", articles.size(), source.getName());
    return articles;
  }

  private static String textOf(Element element) {
    return element == null ? "" : element.text().trim();
  }

  private static Element selectFirst(Element element, String selector) {
    if (element == null || selector == null || selector.trim().isEmpty()) {
      return null;
    }
    return element.selectFirst(selector);
  }
}
