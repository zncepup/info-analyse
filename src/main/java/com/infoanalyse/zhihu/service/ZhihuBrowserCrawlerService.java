package com.infoanalyse.zhihu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infoanalyse.zhihu.model.ZhihuAnswer;
import com.infoanalyse.zhihu.model.ZhihuArticle;
import com.infoanalyse.zhihu.model.ZhihuComment;
import com.microsoft.playwright.*;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Playwright 的知乎数据抓取服务
 * 使用真实浏览器模拟，可以绕过反爬虫机制
 */
@Service
public class ZhihuBrowserCrawlerService {
    
    private static final Logger logger = LoggerFactory.getLogger(ZhihuBrowserCrawlerService.class);
    
    private final ObjectMapper objectMapper;
    private Playwright playwright;
    private Browser browser;
    private boolean headless = true; // 默认无头模式
    
    public ZhihuBrowserCrawlerService() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 设置是否使用无头模式
     */
    public void setHeadless(boolean headless) {
        this.headless = headless;
        // 如果浏览器已经初始化，需要重新初始化
        if (browser != null) {
            closeBrowser();
        }
    }
    
    /**
     * 初始化浏览器
     */
    private void initBrowser() {
        if (playwright == null) {
            logger.info("初始化 Playwright 浏览器... (headless={})", headless);
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(headless) // 可配置的无头模式
                    .setArgs(List.of("--disable-blink-features=AutomationControlled"))); // 隐藏自动化特征
            logger.info("浏览器初始化完成");
        }
    }

    /**
     * 关闭浏览器
     */
    public void closeBrowser() {
        loggedInPage = null;
        loggedInContext = null;
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
        logger.info("浏览器已关闭");
    }
    
    // 保存登录后的浏览器上下文和页面
    private BrowserContext loggedInContext;
    private Page loggedInPage;
    
    private static final String COOKIES_FILE = "zhihu_cookies.json";
    
    /**
     * 打开浏览器让用户登录，登录后保存 cookies
     */
    public void openBrowserForLogin() {
        logger.info("打开浏览器进行登录...");
        
        headless = false;
        closeBrowser();
        initBrowser();
        
        loggedInContext = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080));
        
        loggedInPage = loggedInContext.newPage();
        loggedInPage.navigate("https://www.zhihu.com/signin");
        
        System.out.println();
        System.out.println("========================================");
        System.out.println("浏览器已打开知乎登录页面！");
        System.out.println("请在浏览器中完成登录。");
        System.out.println("登录成功后，输入 zhihu-save-cookies 保存登录状态。");
        System.out.println("========================================");
        System.out.println();
    }
    
    /**
     * 保存当前 cookies 到文件
     */
    public void saveCookies() {
        if (loggedInContext == null) {
            throw new RuntimeException("请先使用 zhihu-login 登录");
        }
        
        try {
            loggedInContext.storageState(new BrowserContext.StorageStateOptions()
                    .setPath(java.nio.file.Path.of(COOKIES_FILE)));
            logger.info("登录状态已保存到 {}", COOKIES_FILE);
        } catch (Exception e) {
            throw new RuntimeException("保存登录状态失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查 cookies 文件是否存在
     */
    public boolean hasSavedCookies() {
        return java.nio.file.Files.exists(java.nio.file.Path.of(COOKIES_FILE));
    }
    
    /**
     * 检查是否已登录
     */
    public boolean isLoggedIn() {
        return loggedInPage != null && loggedInContext != null;
    }
    
    /**
     * 抓取用户回答列表
     */
    public List<ZhihuAnswer> crawlUserAnswers(String userId, int limit) {
        logger.info("开始使用浏览器抓取用户 {} 的回答，限制数量: {}", userId, limit);
        
        final List<ZhihuAnswer> allAnswers = new ArrayList<>();
        final int finalLimit = limit;
        
        initBrowser();
        
        BrowserContext context;
        java.nio.file.Path cookiesPath = java.nio.file.Path.of(COOKIES_FILE);
        
        if (java.nio.file.Files.exists(cookiesPath)) {
            logger.info("加载已保存的登录状态...");
            System.out.println("已加载保存的登录状态");
            context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080)
                    .setStorageStatePath(cookiesPath));
        } else {
            logger.info("未找到登录状态，创建新的上下文");
            System.out.println("未找到保存的登录状态，请先执行 zhihu-login 登录");
            context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080));
        }
        
        Page page = context.newPage();
        
        try {
            page.onResponse(response -> {
                String url = response.url();
                if (url.contains("/api/v4/members/") && url.contains("/answers")) {
                    try {
                        String responseText = response.text();
                        logger.debug("捕获到 API 响应: {}", url);
                        
                        JsonNode root = objectMapper.readTree(responseText);
                        JsonNode dataArray = root.get("data");
                        
                        if (dataArray != null && dataArray.isArray()) {
                            for (JsonNode answerNode : dataArray) {
                                if (allAnswers.size() >= finalLimit) {
                                    break;
                                }
                                ZhihuAnswer answer = parseAnswer(answerNode);
                                allAnswers.add(answer);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("解析 API 响应失败: {}", e.getMessage());
                    }
                }
            });
            
            String userUrl = "https://www.zhihu.com/people/" + userId + "/answers";
            logger.info("访问用户主页: {}", userUrl);
            
            page.navigate(userUrl);
            page.waitForTimeout(5000);
            
            String pageTitle = "";
            try {
                pageTitle = page.title();
            } catch (Exception e) {
                logger.debug("获取页面标题失败，可能正在导航: {}", e.getMessage());
                page.waitForTimeout(2000);
                try {
                    pageTitle = page.title();
                } catch (Exception e2) {
                    pageTitle = "";
                }
            }
            
            if (pageTitle.contains("安全验证") || pageTitle.contains("登录")) {
                logger.warn("知乎要求登录验证...");
                
                if (!headless) {
                    System.out.println();
                    System.out.println("========================================");
                    System.out.println("检测到知乎需要登录验证！");
                    System.out.println("请在打开的浏览器窗口中完成登录。");
                    System.out.println("登录成功后，程序将自动继续...");
                    System.out.println("（最多等待 120 秒）");
                    System.out.println("========================================");
                    System.out.println();
                    
                    int waitCount = 0;
                    int maxWait = 120;
                    while (waitCount < maxWait) {
                        page.waitForTimeout(2000);
                        waitCount += 2;
                        
                        String currentTitle = page.title();
                        String currentUrl = page.url();
                        
                        if (!currentTitle.contains("安全验证") && !currentTitle.contains("登录") 
                            && currentUrl.contains("/people/")) {
                            logger.info("登录成功！继续抓取...");
                            System.out.println("登录成功！继续抓取数据...");
                            break;
                        }
                        
                        if (waitCount % 10 == 0) {
                            System.out.println("等待登录中... (" + waitCount + "/" + maxWait + " 秒)");
                        }
                    }
                    
                    pageTitle = page.title();
                    if (pageTitle.contains("安全验证") || pageTitle.contains("登录")) {
                        throw new RuntimeException("登录超时，请重试");
                    }
                    
                    page.navigate(userUrl);
                    page.waitForTimeout(3000);
                    
                } else {
                    logger.error("知乎需要登录才能访问此页面。请使用 --show-browser 参数手动登录。");
                    throw new RuntimeException("知乎需要登录验证。请使用 --show-browser 参数打开浏览器手动登录。");
                }
            }
            
            int scrollCount = 0;
            int maxScrolls = Math.min(5, (limit / 10) + 1);
            
            while (allAnswers.size() < finalLimit && scrollCount < maxScrolls) {
                page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                page.waitForTimeout(2000);
                scrollCount++;
                logger.debug("滚动页面 {}/{}, 已获取 {} 条回答", scrollCount, maxScrolls, allAnswers.size());
            }
            
            if (allAnswers.isEmpty()) {
                logger.info("API 监听未获取到数据，尝试从页面 HTML 解析...");
                List<ZhihuAnswer> htmlAnswers = parseAnswersFromHtml(page.content(), finalLimit);
                allAnswers.addAll(htmlAnswers);
            }
            
            logger.info("成功抓取 {} 条回答", allAnswers.size());
            return allAnswers.subList(0, Math.min(allAnswers.size(), finalLimit));
            
        } catch (Exception e) {
            logger.error("抓取用户回答失败", e);
            throw new RuntimeException("抓取失败: " + e.getMessage(), e);
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    /**
     * 从 HTML 页面解析回答列表（备用方案）
     */
    private List<ZhihuAnswer> parseAnswersFromHtml(String html, int limit) {
        List<ZhihuAnswer> answers = new ArrayList<>();
        
        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(html);
            
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("debug_page.html"), html);
                logger.info("已保存页面 HTML 到 debug_page.html");
            } catch (Exception e) {
                logger.warn("保存调试 HTML 失败: {}", e.getMessage());
            }
            
            org.jsoup.select.Elements answerElements = doc.select(".List-item");
            if (answerElements.isEmpty()) {
                answerElements = doc.select(".ContentItem");
            }
            if (answerElements.isEmpty()) {
                answerElements = doc.select("[data-za-detail-view-path-module='AnswerItem']");
            }
            if (answerElements.isEmpty()) {
                answerElements = doc.select(".AnswerItem");
            }
            
            logger.info("从 HTML 中找到 {} 个回答元素", answerElements.size());
            
            for (org.jsoup.nodes.Element element : answerElements) {
                if (answers.size() >= limit) {
                    break;
                }
                
                try {
                    ZhihuAnswer answer = new ZhihuAnswer();
                    
                    org.jsoup.nodes.Element questionLink = element.selectFirst("h2.ContentItem-title a");
                    if (questionLink == null) {
                        questionLink = element.selectFirst("h2 a");
                    }
                    if (questionLink == null) {
                        questionLink = element.selectFirst(".ContentItem-title a");
                    }
                    
                    if (questionLink != null) {
                        answer.setQuestionTitle(questionLink.text());
                        String href = questionLink.attr("href");
                        if (href.contains("/question/")) {
                            String[] parts = href.split("/");
                            for (int i = 0; i < parts.length - 1; i++) {
                                if (parts[i].equals("question")) {
                                    answer.setQuestionId(parts[i + 1]);
                                }
                                if (parts[i].equals("answer")) {
                                    answer.setId(parts[i + 1]);
                                }
                            }
                        }
                        answer.setUrl("https://www.zhihu.com" + href);
                    }
                    
                    org.jsoup.nodes.Element contentElement = element.selectFirst(".RichContent-inner");
                    if (contentElement == null) {
                        contentElement = element.selectFirst(".RichText");
                    }
                    if (contentElement == null) {
                        contentElement = element.selectFirst(".content");
                    }
                    if (contentElement != null) {
                        answer.setHtmlContent(contentElement.html());
                        answer.setContent(contentElement.text());
                    }
                    
                    org.jsoup.nodes.Element voteElement = element.selectFirst(".VoteButton--up");
                    if (voteElement == null) {
                        voteElement = element.selectFirst("button[aria-label*='赞同']");
                    }
                    if (voteElement != null) {
                        String voteText = voteElement.text().replaceAll("[^0-9KkWw万]", "");
                        if (!voteText.isEmpty()) {
                            try {
                                if (voteText.contains("K") || voteText.contains("k")) {
                                    answer.setVoteupCount((int)(Double.parseDouble(voteText.replaceAll("[KkWw万]", "")) * 1000));
                                } else if (voteText.contains("W") || voteText.contains("w") || voteText.contains("万")) {
                                    answer.setVoteupCount((int)(Double.parseDouble(voteText.replaceAll("[KkWw万]", "")) * 10000));
                                } else {
                                    answer.setVoteupCount(Integer.parseInt(voteText));
                                }
                            } catch (NumberFormatException e) {
                                logger.debug("解析点赞数失败: {}", voteText);
                            }
                        }
                    }
                    
                    if (answer.getQuestionTitle() != null && !answer.getQuestionTitle().isEmpty()) {
                        answers.add(answer);
                    }
                    
                } catch (Exception e) {
                    logger.warn("解析单个回答失败: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("从 HTML 解析回答失败", e);
        }
        
        return answers;
    }
    
    /**
     * 解析回答 JSON 数据
     */
    private ZhihuAnswer parseAnswer(JsonNode node) {
        ZhihuAnswer answer = new ZhihuAnswer();
        
        answer.setId(node.get("id").asText());
        answer.setVoteupCount(node.get("voteup_count").asInt());
        answer.setCommentCount(node.get("comment_count").asInt());
        
        JsonNode question = node.get("question");
        if (question != null) {
            answer.setQuestionId(question.get("id").asText());
            answer.setQuestionTitle(question.get("title").asText());
        }
        
        JsonNode author = node.get("author");
        if (author != null) {
            answer.setAuthorId(author.get("id").asText());
            answer.setAuthorName(author.get("name").asText());
        }
        
        if (node.has("content")) {
            String htmlContent = node.get("content").asText();
            answer.setHtmlContent(htmlContent);
            String plainText = Jsoup.parse(htmlContent).text();
            answer.setContent(plainText);
        }
        
        if (node.has("created_time")) {
            long createdTimestamp = node.get("created_time").asLong();
            answer.setCreatedTime(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(createdTimestamp), 
                    ZoneId.systemDefault()));
        }
        
        if (node.has("updated_time")) {
            long updatedTimestamp = node.get("updated_time").asLong();
            answer.setUpdatedTime(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(updatedTimestamp), 
                    ZoneId.systemDefault()));
        }
        
        if (answer.getQuestionId() != null && answer.getId() != null) {
            answer.setUrl(String.format("https://www.zhihu.com/question/%s/answer/%s", 
                    answer.getQuestionId(), answer.getId()));
        }
        
        return answer;
    }

    /**
     * 抓取回答的评论（只保留作者参与的对话）
     * 使用游标分页 API 方式获取评论
     */
    public List<ZhihuComment> crawlAnswerComments(String answerId, String authorId) {
        System.out.println("[评论抓取] 开始抓取回答 " + answerId + " 的评论...");
        logger.info("开始抓取回答 {} 的评论，筛选作者ID: {}", answerId, authorId);
        
        final List<ZhihuComment> allComments = new ArrayList<>();
        final Map<String, ZhihuComment> commentMap = new HashMap<>();
        final List<String[]> pendingChildComments = new ArrayList<>();
        
        initBrowser();
        BrowserContext context = createContext();
        Page page = context.newPage();
        
        try {
            String answerUrl = "https://www.zhihu.com/answer/" + answerId;
            System.out.println("[评论抓取] 访问回答页面: " + answerUrl);
            page.navigate(answerUrl);
            page.waitForTimeout(2000);
            
            System.out.println("[评论抓取] === 第一阶段：获取根评论 ===");
            
            String nextUrl = String.format(
                "https://www.zhihu.com/api/v4/comment_v5/answers/%s/root_comment?order_by=score&limit=20",
                answerId
            );
            
            java.util.Random random = new java.util.Random();
            int pageNum = 0;
            
            while (nextUrl != null) {
                pageNum++;
                System.out.println("[评论抓取] 根评论第 " + pageNum + " 页...");
                
                String responseJson = (String) page.evaluate(
                    "(url) => fetch(url, {credentials: 'include'}).then(r => r.text())",
                    nextUrl
                );
                
                if (responseJson == null || responseJson.isEmpty()) {
                    break;
                }
                
                try {
                    JsonNode root = objectMapper.readTree(responseJson);
                    
                    if (root.has("error")) {
                        System.out.println("[评论抓取] API 错误: " + root.get("error"));
                        break;
                    }
                    
                    JsonNode dataArray = root.get("data");
                    int newCount = 0;
                    
                    if (dataArray != null && dataArray.isArray()) {
                        for (JsonNode commentNode : dataArray) {
                            ZhihuComment comment = parseCommentV5(commentNode, answerId);
                            if (comment != null && !commentMap.containsKey(comment.getId())) {
                                commentMap.put(comment.getId(), comment);
                                allComments.add(comment);
                                newCount++;
                                
                                JsonNode childComments = commentNode.get("child_comments");
                                int loadedChildCount = 0;
                                if (childComments != null && childComments.isArray()) {
                                    for (JsonNode childNode : childComments) {
                                        ZhihuComment childComment = parseCommentV5(childNode, answerId);
                                        if (childComment != null && !commentMap.containsKey(childComment.getId())) {
                                            childComment.setParentCommentId(comment.getId());
                                            commentMap.put(childComment.getId(), childComment);
                                            allComments.add(childComment);
                                            newCount++;
                                            loadedChildCount++;
                                        }
                                    }
                                }
                                
                                int totalChildCount = commentNode.has("child_comment_count") 
                                    ? commentNode.get("child_comment_count").asInt() : 0;
                                if (totalChildCount > loadedChildCount) {
                                    pendingChildComments.add(new String[]{
                                        comment.getId(), 
                                        String.valueOf(totalChildCount), 
                                        String.valueOf(loadedChildCount)
                                    });
                                }
                            }
                        }
                    }
                    
                    System.out.println("[评论抓取]   新增 " + newCount + " 条，累计: " + allComments.size());
                    
                    JsonNode paging = root.get("paging");
                    if (paging != null) {
                        boolean isEnd = paging.has("is_end") && paging.get("is_end").asBoolean();
                        if (isEnd) {
                            System.out.println("[评论抓取] 根评论已全部获取");
                            nextUrl = null;
                        } else if (paging.has("next")) {
                            nextUrl = paging.get("next").asText().replace("\\u0026", "&");
                        } else {
                            nextUrl = null;
                        }
                    } else {
                        nextUrl = null;
                    }
                    
                    if (nextUrl != null) {
                        page.waitForTimeout(1000 + random.nextInt(1000));
                    }
                    
                } catch (Exception e) {
                    System.out.println("[评论抓取] 解析失败: " + e.getMessage());
                    break;
                }
            }
            
            if (!pendingChildComments.isEmpty()) {
                System.out.println("[评论抓取] === 第二阶段：获取完整子评论 ===");
                System.out.println("[评论抓取] 有 " + pendingChildComments.size() + " 条根评论需要获取更多子评论");
                
                int processed = 0;
                for (String[] pending : pendingChildComments) {
                    String rootCommentId = pending[0];
                    int totalChild = Integer.parseInt(pending[1]);
                    int loadedChild = Integer.parseInt(pending[2]);
                    
                    processed++;
                    System.out.println("[评论抓取] 获取子评论 " + processed + "/" + pendingChildComments.size() 
                        + " (已有" + loadedChild + "/" + totalChild + ")");
                    
                    fetchAllChildComments(page, rootCommentId, answerId, commentMap, allComments, random);
                    
                    page.waitForTimeout(800 + random.nextInt(700));
                }
            }
            
            System.out.println("[评论抓取] 全部获取完成，共 " + allComments.size() + " 条评论");
            
            System.out.println("[评论抓取] 筛选作者参与的评论...");
            List<ZhihuComment> authorComments = filterAuthorCommentsWithHierarchy(allComments, authorId, commentMap);
            
            System.out.println("[评论抓取] 完成! 共 " + allComments.size() + " 条评论，作者参与 " + authorComments.size() + " 条");
            logger.info("共抓取 {} 条评论，其中作者参与 {} 条", allComments.size(), authorComments.size());
            return authorComments;
            
        } catch (Exception e) {
            System.out.println("[评论抓取] 错误: " + e.getMessage());
            logger.error("抓取评论失败", e);
            return new ArrayList<>();
        } finally {
            context.close();
        }
    }
    
    /**
     * 获取某条根评论的所有子评论
     */
    private void fetchAllChildComments(Page page, String rootCommentId, String answerId,
                                       Map<String, ZhihuComment> commentMap, List<ZhihuComment> allComments,
                                       java.util.Random random) {
        String nextUrl = String.format(
            "https://www.zhihu.com/api/v4/comment_v5/comment/%s/child_comment?order_by=ts&limit=20",
            rootCommentId
        );
        
        while (nextUrl != null) {
            try {
                String responseJson = (String) page.evaluate(
                    "(url) => fetch(url, {credentials: 'include'}).then(r => r.text())",
                    nextUrl
                );
                
                if (responseJson == null || responseJson.isEmpty()) {
                    break;
                }
                
                JsonNode root = objectMapper.readTree(responseJson);
                
                if (root.has("error")) {
                    break;
                }
                
                JsonNode dataArray = root.get("data");
                int newCount = 0;
                
                if (dataArray != null && dataArray.isArray()) {
                    for (JsonNode childNode : dataArray) {
                        ZhihuComment childComment = parseCommentV5(childNode, answerId);
                        if (childComment != null && !commentMap.containsKey(childComment.getId())) {
                            childComment.setParentCommentId(rootCommentId);
                            commentMap.put(childComment.getId(), childComment);
                            allComments.add(childComment);
                            newCount++;
                        }
                    }
                }
                
                if (newCount > 0) {
                    System.out.println("[评论抓取]     新增 " + newCount + " 条子评论");
                }
                
                JsonNode paging = root.get("paging");
                if (paging != null) {
                    boolean isEnd = paging.has("is_end") && paging.get("is_end").asBoolean();
                    if (isEnd) {
                        nextUrl = null;
                    } else if (paging.has("next")) {
                        nextUrl = paging.get("next").asText().replace("\\u0026", "&");
                        page.waitForTimeout(500 + random.nextInt(500));
                    } else {
                        nextUrl = null;
                    }
                } else {
                    nextUrl = null;
                }
                
            } catch (Exception e) {
                logger.debug("获取子评论失败: {}", e.getMessage());
                break;
            }
        }
    }

    /**
     * 筛选作者参与的评论，保留完整的对话层级
     */
    private List<ZhihuComment> filterAuthorCommentsWithHierarchy(List<ZhihuComment> allComments, 
                                                                  String authorId,
                                                                  Map<String, ZhihuComment> commentMap) {
        java.util.Set<String> authorCommentIds = new java.util.HashSet<>();
        for (ZhihuComment comment : allComments) {
            if (authorId.equals(comment.getAuthorId())) {
                authorCommentIds.add(comment.getId());
            }
        }
        
        java.util.Set<String> keepIds = new java.util.HashSet<>();
        
        for (ZhihuComment comment : allComments) {
            if (authorId.equals(comment.getAuthorId())) {
                keepIds.add(comment.getId());
                if (comment.getParentCommentId() != null) {
                    keepIds.add(comment.getParentCommentId());
                }
                if (comment.getReplyCommentId() != null && 
                    !comment.getReplyCommentId().equals(comment.getParentCommentId())) {
                    keepIds.add(comment.getReplyCommentId());
                }
            }
            else {
                if (comment.getReplyCommentId() != null && authorCommentIds.contains(comment.getReplyCommentId())) {
                    keepIds.add(comment.getId());
                    keepIds.add(comment.getReplyCommentId());
                    if (comment.getParentCommentId() != null) {
                        keepIds.add(comment.getParentCommentId());
                    }
                }
                else if (comment.getParentCommentId() != null && authorCommentIds.contains(comment.getParentCommentId())) {
                    keepIds.add(comment.getId());
                    keepIds.add(comment.getParentCommentId());
                }
            }
        }
        
        List<ZhihuComment> result = new ArrayList<>();
        for (ZhihuComment comment : allComments) {
            if (keepIds.contains(comment.getId())) {
                result.add(comment);
            }
        }
        
        result.sort((a, b) -> {
            if (a.getCreatedTime() == null) return -1;
            if (b.getCreatedTime() == null) return 1;
            return a.getCreatedTime().compareTo(b.getCreatedTime());
        });
        
        return result;
    }
    
    /**
     * 解析 v5 版本的评论 JSON
     */
    private ZhihuComment parseCommentV5(JsonNode node, String answerId) {
        try {
            ZhihuComment comment = new ZhihuComment();
            
            comment.setId(node.get("id").asText());
            comment.setAnswerId(answerId);
            comment.setContent(node.get("content").asText());
            
            if (node.has("like_count")) {
                comment.setLikeCount(node.get("like_count").asInt());
            }
            
            boolean isAuthor = node.has("is_author") && node.get("is_author").asBoolean();
            
            JsonNode author = node.get("author");
            if (author != null) {
                // 使用 url_token 作为 authorId（如 mr-dang-77），而不是哈希 id
                if (author.has("url_token")) {
                    comment.setAuthorId(author.get("url_token").asText());
                } else {
                    comment.setAuthorId(author.get("id").asText());
                }
                comment.setAuthorName(author.get("name").asText());
            }
            
            if (node.has("created_time")) {
                long timestamp = node.get("created_time").asLong();
                comment.setCreatedTime(LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(timestamp),
                        ZoneId.systemDefault()));
            }
            
            if (node.has("reply_comment_id")) {
                String replyId = node.get("reply_comment_id").asText();
                if (!"0".equals(replyId)) {
                    comment.setReplyCommentId(replyId);
                }
            }
            if (node.has("reply_root_comment_id")) {
                String rootId = node.get("reply_root_comment_id").asText();
                if (!rootId.equals(comment.getId())) {
                    comment.setParentCommentId(rootId);
                }
            }
            
            return comment;
        } catch (Exception e) {
            logger.warn("解析评论失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 解析评论 JSON
     */
    private ZhihuComment parseComment(JsonNode node, String answerId) {
        try {
            ZhihuComment comment = new ZhihuComment();
            
            comment.setId(node.get("id").asText());
            comment.setAnswerId(answerId);
            comment.setContent(node.get("content").asText());
            
            if (node.has("like_count")) {
                comment.setLikeCount(node.get("like_count").asInt());
            }
            
            JsonNode author = node.get("author");
            if (author != null) {
                // 使用 url_token 作为 authorId（如 mr-dang-77），而不是哈希 id
                if (author.has("url_token")) {
                    comment.setAuthorId(author.get("url_token").asText());
                } else {
                    comment.setAuthorId(author.get("id").asText());
                }
                comment.setAuthorName(author.get("name").asText());
            }
            
            JsonNode replyTo = node.get("reply_to_author");
            if (replyTo != null && !replyTo.isNull()) {
                comment.setReplyToAuthor(replyTo.get("name").asText());
            }
            
            if (node.has("created_time")) {
                long timestamp = node.get("created_time").asLong();
                comment.setCreatedTime(LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(timestamp),
                        ZoneId.systemDefault()));
            }
            
            return comment;
        } catch (Exception e) {
            logger.warn("解析评论失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 创建浏览器上下文（带 cookies）
     */
    private BrowserContext createContext() {
        java.nio.file.Path cookiesPath = java.nio.file.Path.of(COOKIES_FILE);
        
        if (java.nio.file.Files.exists(cookiesPath)) {
            return browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080)
                    .setStorageStatePath(cookiesPath));
        } else {
            return browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080));
        }
    }
    
    /**
     * 解析知乎链接，返回类型和ID
     * @return [type, id] 其中 type 为 "answer" 或 "article"
     */
    public String[] parseZhihuUrl(String url) {
        // 回答链接格式:
        // https://www.zhihu.com/question/xxx/answer/yyy
        // https://www.zhihu.com/answer/yyy
        if (url.contains("/answer/")) {
            String[] parts = url.split("/answer/");
            if (parts.length > 1) {
                String answerId = parts[1].split("[?#]")[0];
                return new String[]{"answer", answerId};
            }
        }
        
        // 文章链接格式:
        // https://zhuanlan.zhihu.com/p/xxx
        // https://www.zhihu.com/p/xxx
        if (url.contains("/p/")) {
            String[] parts = url.split("/p/");
            if (parts.length > 1) {
                String articleId = parts[1].split("[?#]")[0];
                return new String[]{"article", articleId};
            }
        }
        
        return null;
    }
    
    /**
     * 通过链接抓取单个回答
     */
    public ZhihuAnswer crawlAnswerByUrl(String url) {
        String[] parsed = parseZhihuUrl(url);
        if (parsed == null || !"answer".equals(parsed[0])) {
            throw new RuntimeException("无效的回答链接: " + url);
        }
        return crawlAnswerById(parsed[1]);
    }
    
    /**
     * 通过ID抓取单个回答
     */
    public ZhihuAnswer crawlAnswerById(String answerId) {
        logger.info("抓取回答: {}", answerId);
        System.out.println("正在抓取回答 " + answerId + "...");
        
        initBrowser();
        BrowserContext context = createContext();
        Page page = context.newPage();
        
        try {
            // 使用 API 获取回答详情
            String apiUrl = "https://www.zhihu.com/api/v4/answers/" + answerId + 
                "?include=content,voteup_count,comment_count,created_time,updated_time," +
                "author.name,question.title";
            
            page.navigate("https://www.zhihu.com/answer/" + answerId);
            page.waitForTimeout(2000);
            
            String responseJson = (String) page.evaluate(
                "(url) => fetch(url, {credentials: 'include'}).then(r => r.text())",
                apiUrl
            );
            
            if (responseJson != null && !responseJson.isEmpty()) {
                JsonNode node = objectMapper.readTree(responseJson);
                
                if (!node.has("error")) {
                    ZhihuAnswer answer = new ZhihuAnswer();
                    answer.setId(answerId);
                    
                    if (node.has("voteup_count")) {
                        answer.setVoteupCount(node.get("voteup_count").asInt());
                    }
                    if (node.has("comment_count")) {
                        answer.setCommentCount(node.get("comment_count").asInt());
                    }
                    
                    JsonNode question = node.get("question");
                    if (question != null) {
                        answer.setQuestionId(question.get("id").asText());
                        answer.setQuestionTitle(question.get("title").asText());
                    }
                    
                    JsonNode author = node.get("author");
                    if (author != null) {
                        answer.setAuthorId(author.get("id").asText());
                        answer.setAuthorName(author.get("name").asText());
                    }
                    
                    if (node.has("content")) {
                        String htmlContent = node.get("content").asText();
                        answer.setHtmlContent(htmlContent);
                        answer.setContent(Jsoup.parse(htmlContent).text());
                    }
                    
                    if (node.has("created_time")) {
                        answer.setCreatedTime(LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(node.get("created_time").asLong()),
                            ZoneId.systemDefault()));
                    }
                    if (node.has("updated_time")) {
                        answer.setUpdatedTime(LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(node.get("updated_time").asLong()),
                            ZoneId.systemDefault()));
                    }
                    
                    answer.setUrl("https://www.zhihu.com/question/" + answer.getQuestionId() + "/answer/" + answerId);
                    
                    System.out.println("回答抓取成功: " + answer.getQuestionTitle());
                    return answer;
                }
            }
            
            throw new RuntimeException("无法获取回答数据");
            
        } catch (Exception e) {
            logger.error("抓取回答失败", e);
            throw new RuntimeException("抓取回答失败: " + e.getMessage(), e);
        } finally {
            context.close();
        }
    }
    
    /**
     * 通过链接抓取文章
     */
    public ZhihuArticle crawlArticleByUrl(String url) {
        String[] parsed = parseZhihuUrl(url);
        if (parsed == null || !"article".equals(parsed[0])) {
            throw new RuntimeException("无效的文章链接: " + url);
        }
        return crawlArticleById(parsed[1]);
    }
    
    /**
     * 通过ID抓取文章
     */
    public ZhihuArticle crawlArticleById(String articleId) {
        logger.info("抓取文章: {}", articleId);
        System.out.println("正在抓取文章 " + articleId + "...");
        
        initBrowser();
        BrowserContext context = createContext();
        Page page = context.newPage();
        
        try {
            // 先访问文章页面
            String articleUrl = "https://zhuanlan.zhihu.com/p/" + articleId;
            System.out.println("访问文章页面: " + articleUrl);
            page.navigate(articleUrl);
            page.waitForTimeout(3000);
            
            // 尝试从页面 HTML 解析文章内容（更可靠的方式）
            ZhihuArticle article = parseArticleFromHtml(page, articleId);
            
            if (article != null && article.getTitle() != null) {
                System.out.println("文章抓取成功: " + article.getTitle());
                return article;
            }
            
            // 备用方案：使用 API
            String apiUrl = "https://www.zhihu.com/api/v4/articles/" + articleId;
            System.out.println("尝试 API: " + apiUrl);
            
            String responseJson = (String) page.evaluate(
                "(url) => fetch(url, {credentials: 'include'}).then(r => r.text())",
                apiUrl
            );
            
            System.out.println("API 响应长度: " + (responseJson != null ? responseJson.length() : 0));
            
            if (responseJson != null && !responseJson.isEmpty()) {
                // 打印前500字符用于调试
                System.out.println("API 响应预览: " + responseJson.substring(0, Math.min(500, responseJson.length())));
                
                JsonNode node = objectMapper.readTree(responseJson);
                
                if (!node.has("error")) {
                    article = new ZhihuArticle();
                    article.setId(articleId);
                    
                    if (node.has("title")) {
                        article.setTitle(node.get("title").asText());
                    }
                    if (node.has("voteup_count")) {
                        article.setVoteupCount(node.get("voteup_count").asInt());
                    }
                    if (node.has("comment_count")) {
                        article.setCommentCount(node.get("comment_count").asInt());
                    }
                    
                    JsonNode author = node.get("author");
                    if (author != null) {
                        article.setAuthorId(author.get("id").asText());
                        article.setAuthorName(author.get("name").asText());
                    }
                    
                    if (node.has("content")) {
                        String htmlContent = node.get("content").asText();
                        article.setHtmlContent(htmlContent);
                        article.setContent(Jsoup.parse(htmlContent).text());
                    }
                    
                    if (node.has("created")) {
                        article.setCreatedTime(LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(node.get("created").asLong()),
                            ZoneId.systemDefault()));
                    }
                    if (node.has("updated")) {
                        article.setUpdatedTime(LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(node.get("updated").asLong()),
                            ZoneId.systemDefault()));
                    }
                    
                    article.setUrl("https://zhuanlan.zhihu.com/p/" + articleId);
                    
                    System.out.println("文章抓取成功: " + article.getTitle());
                    return article;
                } else {
                    System.out.println("API 返回错误: " + node.get("error"));
                }
            }
            
            throw new RuntimeException("无法获取文章数据");
            
        } catch (Exception e) {
            logger.error("抓取文章失败", e);
            throw new RuntimeException("抓取文章失败: " + e.getMessage(), e);
        } finally {
            context.close();
        }
    }
    
    /**
     * 从页面 HTML 解析文章内容
     */
    private ZhihuArticle parseArticleFromHtml(Page page, String articleId) {
        try {
            String html = page.content();
            org.jsoup.nodes.Document doc = Jsoup.parse(html);
            
            ZhihuArticle article = new ZhihuArticle();
            article.setId(articleId);
            article.setUrl("https://zhuanlan.zhihu.com/p/" + articleId);
            
            // 解析标题
            org.jsoup.nodes.Element titleElement = doc.selectFirst("h1.Post-Title");
            if (titleElement == null) {
                titleElement = doc.selectFirst("article h1");
            }
            if (titleElement == null) {
                titleElement = doc.selectFirst(".Post-RichTextContainer h1");
            }
            if (titleElement != null) {
                article.setTitle(titleElement.text());
            }
            
            // 解析作者
            org.jsoup.nodes.Element authorElement = doc.selectFirst(".AuthorInfo-name .UserLink-link");
            if (authorElement == null) {
                authorElement = doc.selectFirst(".Post-Author .UserLink-link");
            }
            if (authorElement == null) {
                authorElement = doc.selectFirst("a[class*='UserLink']");
            }
            if (authorElement != null) {
                article.setAuthorName(authorElement.text());
                String authorHref = authorElement.attr("href");
                if (authorHref.contains("/people/")) {
                    String[] parts = authorHref.split("/people/");
                    if (parts.length > 1) {
                        article.setAuthorId(parts[1].split("[?#/]")[0]);
                    }
                }
            }
            
            // 解析内容
            org.jsoup.nodes.Element contentElement = doc.selectFirst(".Post-RichTextContainer");
            if (contentElement == null) {
                contentElement = doc.selectFirst("article .RichText");
            }
            if (contentElement == null) {
                contentElement = doc.selectFirst(".Post-RichText");
            }
            if (contentElement != null) {
                article.setHtmlContent(contentElement.html());
                article.setContent(contentElement.text());
            }
            
            // 解析点赞数
            org.jsoup.nodes.Element voteElement = doc.selectFirst("button[class*='VoteButton'] .VoteButton-UpCount");
            if (voteElement == null) {
                voteElement = doc.selectFirst(".VoteButton--up");
            }
            if (voteElement != null) {
                String voteText = voteElement.text().replaceAll("[^0-9KkWw万]", "");
                if (!voteText.isEmpty()) {
                    try {
                        if (voteText.contains("K") || voteText.contains("k")) {
                            article.setVoteupCount((int)(Double.parseDouble(voteText.replaceAll("[KkWw万]", "")) * 1000));
                        } else if (voteText.contains("W") || voteText.contains("w") || voteText.contains("万")) {
                            article.setVoteupCount((int)(Double.parseDouble(voteText.replaceAll("[KkWw万]", "")) * 10000));
                        } else {
                            article.setVoteupCount(Integer.parseInt(voteText));
                        }
                    } catch (NumberFormatException e) {
                        logger.debug("解析点赞数失败: {}", voteText);
                    }
                }
            }
            
            // 解析评论数
            org.jsoup.nodes.Element commentElement = doc.selectFirst("button[class*='ContentItem-action'] .ContentItem-actions--text");
            if (commentElement == null) {
                // 尝试从页面中查找评论数
                org.jsoup.select.Elements buttons = doc.select("button");
                for (org.jsoup.nodes.Element btn : buttons) {
                    String text = btn.text();
                    if (text.contains("条评论")) {
                        String numStr = text.replaceAll("[^0-9]", "");
                        if (!numStr.isEmpty()) {
                            article.setCommentCount(Integer.parseInt(numStr));
                            break;
                        }
                    }
                }
            }
            
            // 验证是否成功解析
            if (article.getTitle() != null && !article.getTitle().isEmpty()) {
                System.out.println("从 HTML 解析成功: " + article.getTitle());
                return article;
            }
            
            return null;
            
        } catch (Exception e) {
            logger.warn("从 HTML 解析文章失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 抓取文章的评论（只保留作者参与的对话）
     */
    public List<ZhihuComment> crawlArticleComments(String articleId, String authorId) {
        System.out.println("[评论抓取] 开始抓取文章 " + articleId + " 的评论...");
        logger.info("开始抓取文章 {} 的评论，筛选作者ID: {}", articleId, authorId);
        
        final List<ZhihuComment> allComments = new ArrayList<>();
        final Map<String, ZhihuComment> commentMap = new HashMap<>();
        final List<String[]> pendingChildComments = new ArrayList<>();
        
        initBrowser();
        BrowserContext context = createContext();
        Page page = context.newPage();
        
        try {
            String articleUrl = "https://zhuanlan.zhihu.com/p/" + articleId;
            System.out.println("[评论抓取] 访问文章页面: " + articleUrl);
            page.navigate(articleUrl);
            page.waitForTimeout(2000);
            
            System.out.println("[评论抓取] === 第一阶段：获取根评论 ===");
            
            // 文章评论 API
            String nextUrl = String.format(
                "https://www.zhihu.com/api/v4/comment_v5/articles/%s/root_comment?order_by=score&limit=20",
                articleId
            );
            
            java.util.Random random = new java.util.Random();
            int pageNum = 0;
            
            while (nextUrl != null) {
                pageNum++;
                System.out.println("[评论抓取] 根评论第 " + pageNum + " 页...");
                
                String responseJson = (String) page.evaluate(
                    "(url) => fetch(url, {credentials: 'include'}).then(r => r.text())",
                    nextUrl
                );
                
                if (responseJson == null || responseJson.isEmpty()) {
                    break;
                }
                
                try {
                    JsonNode root = objectMapper.readTree(responseJson);
                    
                    if (root.has("error")) {
                        System.out.println("[评论抓取] API 错误: " + root.get("error"));
                        break;
                    }
                    
                    JsonNode dataArray = root.get("data");
                    int newCount = 0;
                    
                    if (dataArray != null && dataArray.isArray()) {
                        for (JsonNode commentNode : dataArray) {
                            ZhihuComment comment = parseCommentV5(commentNode, articleId);
                            if (comment != null && !commentMap.containsKey(comment.getId())) {
                                commentMap.put(comment.getId(), comment);
                                allComments.add(comment);
                                newCount++;
                                
                                JsonNode childComments = commentNode.get("child_comments");
                                int loadedChildCount = 0;
                                if (childComments != null && childComments.isArray()) {
                                    for (JsonNode childNode : childComments) {
                                        ZhihuComment childComment = parseCommentV5(childNode, articleId);
                                        if (childComment != null && !commentMap.containsKey(childComment.getId())) {
                                            childComment.setParentCommentId(comment.getId());
                                            commentMap.put(childComment.getId(), childComment);
                                            allComments.add(childComment);
                                            newCount++;
                                            loadedChildCount++;
                                        }
                                    }
                                }
                                
                                int totalChildCount = commentNode.has("child_comment_count") 
                                    ? commentNode.get("child_comment_count").asInt() : 0;
                                if (totalChildCount > loadedChildCount) {
                                    pendingChildComments.add(new String[]{
                                        comment.getId(), 
                                        String.valueOf(totalChildCount), 
                                        String.valueOf(loadedChildCount)
                                    });
                                }
                            }
                        }
                    }
                    
                    System.out.println("[评论抓取]   新增 " + newCount + " 条，累计: " + allComments.size());
                    
                    JsonNode paging = root.get("paging");
                    if (paging != null) {
                        boolean isEnd = paging.has("is_end") && paging.get("is_end").asBoolean();
                        if (isEnd) {
                            System.out.println("[评论抓取] 根评论已全部获取");
                            nextUrl = null;
                        } else if (paging.has("next")) {
                            nextUrl = paging.get("next").asText().replace("\\u0026", "&");
                        } else {
                            nextUrl = null;
                        }
                    } else {
                        nextUrl = null;
                    }
                    
                    if (nextUrl != null) {
                        page.waitForTimeout(1000 + random.nextInt(1000));
                    }
                    
                } catch (Exception e) {
                    System.out.println("[评论抓取] 解析失败: " + e.getMessage());
                    break;
                }
            }
            
            if (!pendingChildComments.isEmpty()) {
                System.out.println("[评论抓取] === 第二阶段：获取完整子评论 ===");
                System.out.println("[评论抓取] 有 " + pendingChildComments.size() + " 条根评论需要获取更多子评论");
                
                int processed = 0;
                for (String[] pending : pendingChildComments) {
                    String rootCommentId = pending[0];
                    int totalChild = Integer.parseInt(pending[1]);
                    int loadedChild = Integer.parseInt(pending[2]);
                    
                    processed++;
                    System.out.println("[评论抓取] 获取子评论 " + processed + "/" + pendingChildComments.size() 
                        + " (已有" + loadedChild + "/" + totalChild + ")");
                    
                    fetchAllChildComments(page, rootCommentId, articleId, commentMap, allComments, random);
                    
                    page.waitForTimeout(800 + random.nextInt(700));
                }
            }
            
            System.out.println("[评论抓取] 全部获取完成，共 " + allComments.size() + " 条评论");
            
            System.out.println("[评论抓取] 筛选作者参与的评论...");
            List<ZhihuComment> authorComments = filterAuthorCommentsWithHierarchy(allComments, authorId, commentMap);
            
            System.out.println("[评论抓取] 完成! 共 " + allComments.size() + " 条评论，作者参与 " + authorComments.size() + " 条");
            logger.info("共抓取 {} 条评论，其中作者参与 {} 条", allComments.size(), authorComments.size());
            return authorComments;
            
        } catch (Exception e) {
            System.out.println("[评论抓取] 错误: " + e.getMessage());
            logger.error("抓取评论失败", e);
            return new ArrayList<>();
        } finally {
            context.close();
        }
    }
}
