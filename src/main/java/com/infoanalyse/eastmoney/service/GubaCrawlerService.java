package com.infoanalyse.eastmoney.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infoanalyse.eastmoney.model.GubaComment;
import com.infoanalyse.eastmoney.model.GubaPost;
import com.microsoft.playwright.*;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 东方财富股吧爬虫服务
 * 基于 Playwright 浏览器自动化，通过 page.evaluate(fetch) 调用股吧内部 API 获取帖子与评论数据。
 *
 * <p>股吧帖子列表页地址格式：https://guba.eastmoney.com/list,{stockCode}.html
 * <p>帖子详情页地址格式：https://guba.eastmoney.com/news,{stockCode},{postId}.html
 * <p>评论 API 通过页面代理调用：POST /api/getData?code={stockCode}&path=reply/api/Reply/ArticleNewReplyList
 */
@Service
public class GubaCrawlerService {

    private static final Logger logger = LoggerFactory.getLogger(GubaCrawlerService.class);

    private static final String GUBA_LIST_URL = "https://guba.eastmoney.com/list,%s.html";
    private static final String GUBA_POST_URL = "https://guba.eastmoney.com/news,%s,%s.html";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern DATETIME_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    private static final Pattern DATE_ONLY_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final ObjectMapper objectMapper;
    private Playwright playwright;
    private Browser browser;

    public GubaCrawlerService() {
        this.objectMapper = new ObjectMapper();
    }

    private synchronized void initBrowser() {
        if (playwright == null) {
            logger.info("初始化股吧爬虫 Playwright 浏览器...");
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(List.of(
                            "--disable-blink-features=AutomationControlled",
                            "--disable-features=IsolateOrigins,site-per-process"
                    )));
            logger.info("股吧爬虫浏览器初始化完成");
        }
    }

    public synchronized void closeBrowser() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
        logger.info("股吧爬虫浏览器已关闭");
    }

    /**
     * 抓取指定股票的股吧帖子列表
     */
    public List<GubaPost> crawlPostList(String stockCode, int pages) {
        logger.info("开始抓取股吧帖子列表: stockCode={}, pages={}", stockCode, pages);
        initBrowser();

        List<GubaPost> allPosts = new ArrayList<>();

        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(USER_AGENT)
                .setViewportSize(1280, 900));
        context.addInitScript("Object.defineProperty(navigator, 'webdriver', { get: () => undefined });");
        Page page = context.newPage();

        try {
            // 拦截 API 响应获取帖子数据
            final List<JsonNode> apiDataList = new ArrayList<>();
            page.onResponse(response -> {
                String url = response.url();
                if (url.contains("/api/getData") && url.contains("webarticlelist")) {
                    try {
                        String text = response.text();
                        JsonNode root = objectMapper.readTree(text);
                        apiDataList.add(root);
                    } catch (Exception e) {
                        logger.debug("解析股吧 API 响应失败: {}", e.getMessage());
                    }
                }
            });

            for (int p = 1; p <= pages; p++) {
                String listUrl = String.format(GUBA_LIST_URL, stockCode);
                if (p > 1) {
                    listUrl = String.format("https://guba.eastmoney.com/list,%s,f_%d.html", stockCode, p);
                }
                logger.info("访问股吧列表页: {}", listUrl);
                page.navigate(listUrl);
                page.waitForTimeout(3000);

                if (!apiDataList.isEmpty()) {
                    for (JsonNode apiData : apiDataList) {
                        List<GubaPost> posts = parsePostsFromApi(apiData, stockCode);
                        allPosts.addAll(posts);
                    }
                    apiDataList.clear();
                }

                if (allPosts.isEmpty() || (p > 1 && allPosts.size() < (p - 1) * 30)) {
                    List<GubaPost> htmlPosts = parsePostsFromHtml(page.content(), stockCode);
                    for (GubaPost post : htmlPosts) {
                        if (allPosts.stream().noneMatch(existing -> existing.getPostId().equals(post.getPostId()))) {
                            allPosts.add(post);
                        }
                    }
                }
            }

            logger.info("成功抓取 {} 条股吧帖子", allPosts.size());
            return allPosts;
        } catch (Exception e) {
            logger.error("抓取股吧帖子列表失败", e);
            throw new RuntimeException("抓取股吧帖子列表失败: " + e.getMessage(), e);
        } finally {
            context.close();
        }
    }

    /**
     * 抓取单个帖子的详情和评论。
     * 策略：先导航到帖子页面获取 window.post_article，再通过 page.evaluate(fetch) 调用评论 API。
     * 这种方式绕过了 headless 模式下 Vue 评论组件不渲染的问题。
     */
    public GubaPost crawlPostDetail(String stockCode, String postId) {
        logger.info("开始抓取帖子详情: stockCode={}, postId={}", stockCode, postId);
        initBrowser();

        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(USER_AGENT)
                .setViewportSize(1280, 900));
        context.addInitScript("Object.defineProperty(navigator, 'webdriver', { get: () => undefined });");
        Page page = context.newPage();

        try {
            String postUrl = String.format(GUBA_POST_URL, stockCode, postId);
            logger.info("访问帖子详情页: {}", postUrl);
            page.navigate(postUrl);
            page.waitForTimeout(3000);

            // 1. 从 window.post_article 获取帖子信息
            GubaPost post = parsePostFromWindowData(page, stockCode, postId);
            post.setUrl(postUrl);

            // 2. 通过 page.evaluate(fetch) 调用评论 API
            List<GubaComment> allComments = fetchCommentsViaApi(page, stockCode, postId);
            post.setComments(allComments);

            logger.info("帖子 {} 共获取 {} 条评论", postId, allComments.size());
            return post;
        } catch (Exception e) {
            logger.error("抓取帖子详情失败: postId={}", postId, e);
            throw new RuntimeException("抓取帖子详情失败: " + e.getMessage(), e);
        } finally {
            context.close();
        }
    }

    /**
     * 从 window.post_article 全局变量解析帖子信息（比 HTML 解析更可靠）
     */
    private GubaPost parsePostFromWindowData(Page page, String stockCode, String postId) {
        GubaPost post = new GubaPost();
        post.setStockCode(stockCode);
        post.setPostId(postId);

        try {
            String json = (String) page.evaluate("() => { " +
                    "const pa = window.post_article; " +
                    "if (!pa) return null; " +
                    "return JSON.stringify({ " +
                    "  title: pa.post_title || pa.cnTitle || '', " +
                    "  content: pa.post_abstract || '', " +
                    "  htmlContent: pa.post_content || '', " +
                    "  authorName: pa.post_user ? pa.post_user.user_nickname : '', " +
                    "  authorId: pa.post_user ? pa.post_user.user_id : '', " +
                    "  readCount: pa.post_click_count || 0, " +
                    "  commentCount: pa.post_comment_count || 0, " +
                    "  likeCount: pa.post_like_count || 0, " +
                    "  publishTime: pa.post_publish_time || '', " +
                    "  stockName: pa.post_guba ? pa.post_guba.stockbar_name : '', " +
                    "  stockCode: pa.post_guba ? pa.post_guba.stockbar_code : '' " +
                    "}); }");

            if (json != null) {
                JsonNode data = objectMapper.readTree(json);
                post.setTitle(data.get("title").asText());
                post.setContent(data.get("content").asText());
                post.setHtmlContent(data.get("htmlContent").asText());
                post.setAuthorName(data.get("authorName").asText());
                post.setAuthorId(data.get("authorId").asText());
                post.setReadCount(data.get("readCount").asInt());
                post.setCommentCount(data.get("commentCount").asInt());
                post.setLikeCount(data.get("likeCount").asInt());
                post.setPublishTime(parseDateTime(data.get("publishTime").asText()));
                String name = data.get("stockName").asText();
                if (name != null && !name.isEmpty()) {
                    post.setStockName(name);
                }
                logger.info("从 window.post_article 解析帖子: {} (评论数: {})", post.getTitle(), post.getCommentCount());
            } else {
                logger.warn("window.post_article 不存在，回退到 HTML 解析");
                return parsePostDetailFromHtml(page.content(), stockCode, postId);
            }
        } catch (Exception e) {
            logger.warn("解析 window.post_article 失败: {}，回退到 HTML 解析", e.getMessage());
            return parsePostDetailFromHtml(page.content(), stockCode, postId);
        }
        return post;
    }

    /**
     * 通过 page.evaluate(fetch) 调用股吧评论 API。
     * 在页面上下文中执行 fetch，自动携带 cookies 和正确的 origin，绕过反爬。
     * API 格式：POST /api/getData?code={stockCode}&path=reply/api/Reply/ArticleNewReplyList
     * Body (form-encoded): param=postid={id}&sort=1&sorttype=1&p={page}&ps=30 + plat/path/env/version/product
     */
    private List<GubaComment> fetchCommentsViaApi(Page page, String stockCode, String postId) {
        List<GubaComment> allComments = new ArrayList<>();
        int pageNum = 1;
        int maxPages = 10;

        while (pageNum <= maxPages) {
            logger.debug("获取评论第 {} 页...", pageNum);
            try {
                String jsCode = String.format(
                        "(async () => { " +
                        "  const path = 'reply/api/Reply/ArticleNewReplyList'; " +
                        "  const paramStr = 'postid=%s&sort=1&sorttype=1&p=%d&ps=30'; " +
                        "  const bodyParts = [ " +
                        "    'param=' + encodeURIComponent(paramStr), " +
                        "    'plat=Web', " +
                        "    'path=' + encodeURIComponent(path), " +
                        "    'env=1', " +
                        "    'origin=', " +
                        "    'version=2022', " +
                        "    'product=Guba' " +
                        "  ]; " +
                        "  const url = '/api/getData?code=%s&path=' + path; " +
                        "  const resp = await fetch(url, { " +
                        "    method: 'POST', " +
                        "    headers: {'Content-Type': 'application/x-www-form-urlencoded'}, " +
                        "    body: bodyParts.join('&'), " +
                        "    credentials: 'include' " +
                        "  }); " +
                        "  return await resp.text(); " +
                        "})()",
                        postId, pageNum, stockCode);

                String responseText = (String) page.evaluate(jsCode);

                if (responseText == null || responseText.isEmpty() || "less of data".equals(responseText)) {
                    logger.debug("评论 API 返回空数据，停止翻页");
                    break;
                }

                JsonNode root = objectMapper.readTree(responseText);
                JsonNode reList = root.get("re");
                if (reList == null || !reList.isArray() || reList.isEmpty()) {
                    break;
                }

                int newCount = 0;
                for (JsonNode item : reList) {
                    // 解析根评论
                    GubaComment comment = parseReplyNode(item, postId);
                    if (comment != null) {
                        allComments.add(comment);
                        newCount++;
                    }
                    // 解析子评论（回复的回复）
                    JsonNode childReplys = item.get("child_replys");
                    if (childReplys != null && childReplys.isArray()) {
                        for (JsonNode child : childReplys) {
                            GubaComment childComment = parseReplyNode(child, postId);
                            if (childComment != null) {
                                childComment.setReplyToCommentId(String.valueOf(item.get("reply_id").asLong()));
                                // 提取回复目标用户
                                JsonNode replyToUser = child.get("reply_to_user");
                                if (replyToUser != null && replyToUser.has("user_nickname")) {
                                    childComment.setReplyToUser(replyToUser.get("user_nickname").asText());
                                }
                                allComments.add(childComment);
                                newCount++;
                            }
                        }
                    }
                }

                logger.debug("第 {} 页获取 {} 条评论，累计 {}", pageNum, newCount, allComments.size());

                if (newCount == 0) {
                    break;
                }
                pageNum++;
                // 请求间隔
                page.waitForTimeout(500);

            } catch (Exception e) {
                logger.warn("获取评论第 {} 页失败: {}", pageNum, e.getMessage());
                break;
            }
        }

        return allComments;
    }

    /**
     * 解析单条评论 JSON 节点
     */
    private GubaComment parseReplyNode(JsonNode item, String postId) {
        try {
            GubaComment comment = new GubaComment();
            comment.setPostId(postId);
            comment.setCommentId(String.valueOf(item.get("reply_id").asLong()));

            String text = item.has("reply_text") ? item.get("reply_text").asText("") : "";
            if (text.isEmpty()) return null;
            comment.setContent(text);

            comment.setPublishTime(parseDateTime(
                    item.has("reply_publish_time") ? item.get("reply_publish_time").asText() : null));
            comment.setLikeCount(item.has("reply_like_count") ? item.get("reply_like_count").asInt() : 0);

            JsonNode replyUser = item.get("reply_user");
            if (replyUser != null) {
                comment.setAuthorName(replyUser.has("user_nickname") ? replyUser.get("user_nickname").asText() : "");
                comment.setAuthorId(replyUser.has("user_id") ? replyUser.get("user_id").asText() : "");
            }

            return comment;
        } catch (Exception e) {
            logger.debug("解析评论节点失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 批量抓取帖子列表中每个帖子的评论
     */
    public List<GubaPost> crawlPostsWithComments(String stockCode, int pages, boolean withComments) {
        List<GubaPost> posts = crawlPostList(stockCode, pages);
        if (!withComments) {
            return posts;
        }

        logger.info("开始批量抓取 {} 条帖子的评论...", posts.size());
        List<GubaPost> detailedPosts = new ArrayList<>();
        for (GubaPost post : posts) {
            if (post.getCommentCount() > 0) {
                try {
                    GubaPost detailed = crawlPostDetail(stockCode, post.getPostId());
                    detailed.setReadCount(post.getReadCount());
                    if (detailed.getStockName() == null) {
                        detailed.setStockName(post.getStockName());
                    }
                    detailedPosts.add(detailed);
                    Thread.sleep(1500);
                } catch (Exception e) {
                    logger.warn("抓取帖子 {} 评论失败: {}", post.getPostId(), e.getMessage());
                    detailedPosts.add(post);
                }
            } else {
                detailedPosts.add(post);
            }
        }
        return detailedPosts;
    }

    // ==================== 解析方法 ====================

    private List<GubaPost> parsePostsFromApi(JsonNode root, String stockCode) {
        List<GubaPost> posts = new ArrayList<>();
        try {
            JsonNode reList = findArrayNode(root, "re");
            if (reList == null) return posts;

            for (JsonNode item : reList) {
                try {
                    GubaPost post = new GubaPost();
                    post.setStockCode(stockCode);
                    post.setPostId(getTextSafe(item, "post_id"));
                    post.setTitle(getTextSafe(item, "post_title"));
                    post.setAuthorName(getTextSafe(item, "post_user", "user_nickname"));
                    post.setAuthorId(getTextSafe(item, "user_id"));
                    post.setReadCount(getIntSafe(item, "post_click_count"));
                    post.setCommentCount(getIntSafe(item, "post_comment_count"));
                    post.setLikeCount(getIntSafe(item, "post_like_count"));
                    post.setStockName(getTextSafe(item, "stockbar_name"));
                    post.setPublishTime(parseDateTime(getTextSafe(item, "post_publish_time")));

                    if (post.getPostId() != null && !post.getPostId().isEmpty()) {
                        post.setUrl(String.format(GUBA_POST_URL, stockCode, post.getPostId()));
                        posts.add(post);
                    }
                } catch (Exception e) {
                    logger.debug("解析单条帖子失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("解析帖子 API 数据失败: {}", e.getMessage());
        }
        return posts;
    }

    private List<GubaPost> parsePostsFromHtml(String html, String stockCode) {
        List<GubaPost> posts = new ArrayList<>();
        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(html);
            org.jsoup.select.Elements rows = doc.select(".listitem, .articleh, .normal_post");
            if (rows.isEmpty()) {
                rows = doc.select("[class*=list] [class*=item]");
            }

            for (org.jsoup.nodes.Element row : rows) {
                try {
                    GubaPost post = new GubaPost();
                    post.setStockCode(stockCode);

                    org.jsoup.nodes.Element titleLink = row.selectFirst("a[href*=/news,]");
                    if (titleLink == null) titleLink = row.selectFirst(".title a, .post_title a");
                    if (titleLink != null) {
                        post.setTitle(titleLink.text().trim());
                        String href = titleLink.attr("href");
                        Matcher m = Pattern.compile("news,\\w+,(\\d+)").matcher(href);
                        if (m.find()) post.setPostId(m.group(1));
                        post.setUrl(href.startsWith("/") ? "https://guba.eastmoney.com" + href : href);
                    }

                    org.jsoup.nodes.Element authorEl = row.selectFirst(".author a, .user a, [class*=author]");
                    if (authorEl != null) post.setAuthorName(authorEl.text().trim());

                    org.jsoup.nodes.Element readEl = row.selectFirst(".read, [class*=read]");
                    if (readEl != null) post.setReadCount(parseCount(readEl.text()));

                    org.jsoup.nodes.Element commentEl = row.selectFirst(".reply, [class*=comment], [class*=reply]");
                    if (commentEl != null) post.setCommentCount(parseCount(commentEl.text()));

                    org.jsoup.nodes.Element timeEl = row.selectFirst(".time, .update, [class*=time]");
                    if (timeEl != null) post.setPublishTime(parseDateTime(timeEl.text().trim()));

                    if (post.getPostId() != null && post.getTitle() != null && !post.getTitle().isEmpty()) {
                        posts.add(post);
                    }
                } catch (Exception e) {
                    logger.debug("HTML 解析单条帖子失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("从 HTML 解析帖子列表失败: {}", e.getMessage());
        }
        return posts;
    }

    /**
     * 从帖子详情页 HTML 解析帖子正文（备用方案，当 window.post_article 不可用时使用）
     */
    private GubaPost parsePostDetailFromHtml(String html, String stockCode, String postId) {
        GubaPost post = new GubaPost();
        post.setStockCode(stockCode);
        post.setPostId(postId);

        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(html);

            String pageTitle = doc.title();
            if (pageTitle != null && pageTitle.contains("_")) {
                post.setTitle(pageTitle.substring(0, pageTitle.indexOf("_")).trim());
            }

            org.jsoup.nodes.Element contentEl = doc.selectFirst("#zw_body");
            if (contentEl == null) contentEl = doc.selectFirst(".newstext");
            if (contentEl != null) {
                post.setHtmlContent(contentEl.html());
                post.setContent(contentEl.text().trim());
            }

            org.jsoup.nodes.Element sourceEl = doc.selectFirst("#zw_header .source");
            if (sourceEl != null) {
                post.setAuthorName(sourceEl.text().trim().replaceFirst("^(来源|作者)[：:]\\s*", ""));
            }
            if (post.getAuthorName() == null || post.getAuthorName().isEmpty()) {
                org.jsoup.nodes.Element userEl = doc.selectFirst(".newstext_user a, .user_info a");
                if (userEl != null) {
                    post.setAuthorName(userEl.text().trim());
                    Matcher userMatcher = Pattern.compile("/(\\d+)$").matcher(userEl.attr("href"));
                    if (userMatcher.find()) post.setAuthorId(userMatcher.group(1));
                }
            }

            org.jsoup.nodes.Element timeEl = doc.selectFirst(".zwfbtime, .pubtime");
            if (timeEl != null) post.setPublishTime(parseDateTime(timeEl.text().trim()));

            if (pageTitle != null) {
                Matcher stockMatcher = Pattern.compile("_(.+?)\\(" + stockCode + "\\)").matcher(pageTitle);
                if (stockMatcher.find()) post.setStockName(stockMatcher.group(1));
            }
        } catch (Exception e) {
            logger.warn("解析帖子详情 HTML 失败: {}", e.getMessage());
        }
        return post;
    }

    // ==================== 工具方法 ====================

    private JsonNode findArrayNode(JsonNode root, String... fieldNames) {
        if (root == null) return null;
        for (String name : fieldNames) {
            if (root.has(name) && root.get(name).isArray()) return root.get(name);
        }
        var fields = root.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            JsonNode value = entry.getValue();
            if (value.isObject()) {
                for (String name : fieldNames) {
                    if (value.has(name) && value.get(name).isArray()) return value.get(name);
                }
            }
        }
        return null;
    }

    private String getTextSafe(JsonNode node, String... fieldNames) {
        for (String name : fieldNames) {
            if (node.hasNonNull(name)) return node.get(name).asText("");
        }
        return null;
    }

    private int getIntSafe(JsonNode node, String... fieldNames) {
        for (String name : fieldNames) {
            if (node.hasNonNull(name)) return node.get(name).asInt(0);
        }
        return 0;
    }

    private LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            Matcher m = DATETIME_PATTERN.matcher(text);
            if (m.find()) return LocalDateTime.parse(m.group(), DATE_FORMATTER);
            Matcher dm = DATE_ONLY_PATTERN.matcher(text);
            if (dm.find()) return LocalDateTime.parse(dm.group() + " 00:00:00", DATE_FORMATTER);
            Pattern shortPattern = Pattern.compile("(\\d{2}-\\d{2} \\d{2}:\\d{2})");
            Matcher sm = shortPattern.matcher(text);
            if (sm.find()) {
                return LocalDateTime.parse(LocalDateTime.now().getYear() + "-" + sm.group(1) + ":00", DATE_FORMATTER);
            }
        } catch (Exception e) {
            logger.debug("日期解析失败: {}", text);
        }
        return null;
    }

    private int parseCount(String text) {
        if (text == null || text.isBlank()) return 0;
        text = text.replaceAll("[^0-9万wWkK.]", "");
        if (text.isEmpty()) return 0;
        try {
            if (text.contains("万") || text.toLowerCase().contains("w")) {
                return (int) (Double.parseDouble(text.replaceAll("[万wW]", "")) * 10000);
            }
            if (text.toLowerCase().contains("k")) {
                return (int) (Double.parseDouble(text.replaceAll("[kK]", "")) * 1000);
            }
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
