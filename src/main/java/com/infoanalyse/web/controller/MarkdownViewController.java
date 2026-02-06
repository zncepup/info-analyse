package com.infoanalyse.web.controller;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
public class MarkdownViewController {
    private final Parser parser;
    private final HtmlRenderer renderer;
    private final Path outputDir = Path.of("output").toAbsolutePath().normalize();

    public MarkdownViewController() {
        this.parser = Parser.builder().build();
        this.renderer = HtmlRenderer.builder().escapeHtml(false).build();
    }

    @GetMapping(value = "/view/{author}/{file:.+}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> view(@PathVariable("author") String author, @PathVariable("file") String file) {
        if (file.contains("/") || file.contains("\\")) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid file path");
        }
        Path authorDir = outputDir.resolve(author).normalize();
        if (!authorDir.startsWith(outputDir)) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid author path");
        }
        Path mdPath = authorDir.resolve(file).normalize();
        if (!mdPath.startsWith(authorDir)) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid file path");
        }
        if (!Files.exists(mdPath) || !mdPath.getFileName().toString().endsWith(".md")) {
            throw new ResponseStatusException(NOT_FOUND, "file not found");
        }

        try {
            String markdown = Files.readString(mdPath);
            Node document = parser.parse(markdown);
            String html = renderer.render(document);
            String adjusted = adjustLinks(html, author);
            String page = wrapHtml(file, adjusted);
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(page);
        } catch (Exception e) {
            throw new ResponseStatusException(NOT_FOUND, "failed to render content");
        }
    }

    private String adjustLinks(String html, String author) {
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element img : doc.select("img[src]")) {
            String src = img.attr("src");
            if (isRelative(src)) {
                img.attr("src", "/output/" + author + "/" + src);
            }
        }
        for (Element link : doc.select("a[href]")) {
            String href = link.attr("href");
            if (href.endsWith(".md") && isRelative(href)) {
                link.attr("href", "/view/" + author + "/" + href);
            } else if (isRelative(href)) {
                link.attr("href", "/output/" + author + "/" + href);
            }
            link.attr("target", "_blank");
            link.attr("rel", "noopener noreferrer");
        }
        return doc.body().html();
    }

    private boolean isRelative(String link) {
        if (link == null || link.isBlank()) {
            return false;
        }
        String lower = link.toLowerCase();
        return !(lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:")
                || lower.startsWith("tel:") || lower.startsWith("#") || lower.startsWith("data:"));
    }

    private String wrapHtml(String title, String content) {
        String header = """
                <!doctype html>
                <html lang=\"zh-CN\">
                <head>
                  <meta charset=\"UTF-8\">
                  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">
                  <title>阅读 - __TITLE__</title>
                  <style>
                    :root {
                      color-scheme: light;
                      --bg: #f4f5f1;
                      --ink: #0c1f23;
                      --muted: #516068;
                      --card: #ffffff;
                      --accent: #0f766e;
                    }
                    body {
                      margin: 0;
                      font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", \"PingFang SC\", \"Microsoft YaHei\", sans-serif;
                      color: var(--ink);
                      background: var(--bg);
                    }
                    header {
                      position: sticky;
                      top: 0;
                      background: rgba(255, 255, 255, 0.9);
                      backdrop-filter: blur(8px);
                      border-bottom: 1px solid rgba(12, 31, 35, 0.08);
                      padding: 12px 16px;
                      display: flex;
                      align-items: center;
                      gap: 12px;
                      z-index: 10;
                    }
                    header a {
                      color: var(--accent);
                      font-weight: 600;
                      text-decoration: none;
                    }
                    main {
                      max-width: 860px;
                      margin: 0 auto;
                      padding: 20px 16px 48px;
                    }
                    article {
                      background: var(--card);
                      border-radius: 16px;
                      padding: 22px;
                      box-shadow: 0 14px 40px rgba(10, 27, 31, 0.08);
                    }
                    h1, h2, h3, h4 {
                      font-family: \"Fraunces\", \"Noto Serif SC\", serif;
                    }
                    img {
                      max-width: 100%;
                      height: auto;
                      border-radius: 10px;
                    }
                    pre {
                      overflow: auto;
                      background: #0c1f23;
                      color: #e4ecef;
                      padding: 12px 14px;
                      border-radius: 12px;
                    }
                    code {
                      background: rgba(12, 31, 35, 0.08);
                      padding: 0 6px;
                      border-radius: 6px;
                    }
                    blockquote {
                      border-left: 4px solid rgba(15, 118, 110, 0.4);
                      padding-left: 12px;
                      color: var(--muted);
                    }
                    a { color: var(--accent); }
                  </style>
                </head>
                <body>
                  <header>
                    <a href=\"/\">返回首页</a>
                    <span>内容浏览</span>
                  </header>
                  <main>
                    <article>
                """;

        String footer = """
                    </article>
                  </main>
                </body>
                </html>
                """;

        return header.replace("__TITLE__", escape(title)) + content + footer;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
