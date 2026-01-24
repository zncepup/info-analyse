package com.infoanalyse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infoanalyse.model.ZhihuAnswer;
import com.infoanalyse.model.ZhihuComment;
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
        
        // 确保使用非 headless 模式
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
            // 保存 storage state（包含 cookies 和 localStorage）
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
        
        // 创建上下文，如果有保存的登录状态则加载
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
            // 监听 API 响应
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
            
            // 访问用户主页
            String userUrl = "https://www.zhihu.com/people/" + userId + "/answers";
            logger.info("访问用户主页: {}", userUrl);
            
            page.navigate(userUrl);
            
            // 等待页面加载
            page.waitForTimeout(5000);
            
            // 检查是否需要登录验证
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
                    // 非 headless 模式，等待用户手动登录
                    System.out.println();
                    System.out.println("========================================");
                    System.out.println("检测到知乎需要登录验证！");
                    System.out.println("请在打开的浏览器窗口中完成登录。");
                    System.out.println("登录成功后，程序将自动继续...");
                    System.out.println("（最多等待 120 秒）");
                    System.out.println("========================================");
                    System.out.println();
                    
                    // 等待用户登录，最多等待 120 秒
                    int waitCount = 0;
                    int maxWait = 120; // 120 秒
                    while (waitCount < maxWait) {
                        page.waitForTimeout(2000);
                        waitCount += 2;
                        
                        // 检查是否已经登录成功（页面标题变化）
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
                    
                    // 再次检查
                    pageTitle = page.title();
                    if (pageTitle.contains("安全验证") || pageTitle.contains("登录")) {
                        throw new RuntimeException("登录超时，请重试");
                    }
                    
                    // 登录成功后，重新导航到目标页面
                    page.navigate(userUrl);
                    page.waitForTimeout(3000);
                    
                } else {
                    // headless 模式，无法手动登录
                    logger.error("知乎需要登录才能访问此页面。请使用 --show-browser 参数手动登录。");
                    throw new RuntimeException("知乎需要登录验证。请使用 --show-browser 参数打开浏览器手动登录。");
                }
            }
            
            // 滚动页面加载更多内容
            int scrollCount = 0;
            int maxScrolls = Math.min(5, (limit / 10) + 1); // 每次滚动大约加载10条
            
            while (allAnswers.size() < finalLimit && scrollCount < maxScrolls) {
                page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                page.waitForTimeout(2000);
                scrollCount++;
                logger.debug("滚动页面 {}/{}, 已获取 {} 条回答", scrollCount, maxScrolls, allAnswers.size());
            }
            
            // 如果通过 API 监听没有获取到数据，尝试从页面 HTML 解析
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
            
            // 保存 HTML 用于调试
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("debug_page.html"), html);
                logger.info("已保存页面 HTML 到 debug_page.html");
            } catch (Exception e) {
                logger.warn("保存调试 HTML 失败: {}", e.getMessage());
            }
            
            // 尝试多种选择器
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
                    
                    // 提取问题标题 - 尝试多种选择器
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
                    
                    // 提取内容 - 尝试多种选择器
                    org.jsoup.nodes.Element contentElement = element.selectFirst(".RichContent-inner");
                    if (contentElement == null) {
                        contentElement = element.selectFirst(".RichText");
                    }
                    if (contentElement == null) {
                        contentElement = element.selectFirst(".content");
                    }
                    if (contentElement != null) {
                        answer.setHtmlContent(contentElement.html());  // 保存原始 HTML
                        answer.setContent(contentElement.text());
                    }
                    
                    // 提取点赞数 - 尝试多种选择器
                    org.jsoup.nodes.Element voteElement = element.selectFirst(".VoteButton--up");
                    if (voteElement == null) {
                        voteElement = element.selectFirst("button[aria-label*='赞同']");
                    }
                    if (voteElement != null) {
                        String voteText = voteElement.text().replaceAll("[^0-9KkWw万]", "");
                        if (!voteText.isEmpty()) {
                            try {
                                // 处理 K/W/万 等单位
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
        
        // 解析问题信息
        JsonNode question = node.get("question");
        if (question != null) {
            answer.setQuestionId(question.get("id").asText());
            answer.setQuestionTitle(question.get("title").asText());
        }
        
        // 解析作者信息
        JsonNode author = node.get("author");
        if (author != null) {
            answer.setAuthorId(author.get("id").asText());
            answer.setAuthorName(author.get("name").asText());
        }
        
        // 解析内容
        if (node.has("content")) {
            String htmlContent = node.get("content").asText();
            answer.setHtmlContent(htmlContent);  // 保存原始 HTML
            String plainText = Jsoup.parse(htmlContent).text();
            answer.setContent(plainText);
        }
        
        // 解析时间
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
        
        // 构建URL
        if (answer.getQuestionId() != null && answer.getId() != null) {
            answer.setUrl(String.format("https://www.zhihu.com/question/%s/answer/%s", 
                    answer.getQuestionId(), answer.getId()));
        }
        
        return answer;
    }
    
    /**
     * 抓取回答的评论（只保留作者参与的对话）
     * 使用滚动加载方式，完全模拟人工操作
     * @param answerId 回答ID
     * @param authorId 回答作者ID（用于筛选作者参与的评论）
     * @return 作者参与的评论列表
     */
    public List<ZhihuComment> crawlAnswerComments(String answerId, String authorId) {
        System.out.println("[评论抓取] 开始抓取回答 " + answerId + " 的评论（滚动模式）...");
        logger.info("开始抓取回答 {} 的评论，筛选作者ID: {}", answerId, authorId);
        
        final List<ZhihuComment> allComments = new ArrayList<>();
        final Map<String, ZhihuComment> commentMap = new HashMap<>();
        
        initBrowser();
        BrowserContext context = createContext();
        Page page = context.newPage();
        
        try {
            // 访问回答页面
            String answerUrl = "https://www.zhihu.com/question/0/answer/" + answerId;
            System.out.println("[评论抓取] 访问回答页面: " + answerUrl);
            page.navigate(answerUrl);
            page.waitForTimeout(3000);
            
            // 点击"查看全部评论"按钮打开评论区
            System.out.println("[评论抓取] 查找评论按钮...");
            
            // 尝试点击评论按钮
            String[] commentButtonSelectors = {
                "button:has-text('条评论')",
                "[class*='ContentItem-action']:has-text('评论')",
                "button[class*='Button']:has-text('评论')"
            };
            
            boolean clickedComment = false;
            for (String selector : commentButtonSelectors) {
                try {
                    Locator btn = page.locator(selector).first();
                    if (btn.isVisible()) {
                        System.out.println("[评论抓取] 点击评论按钮...");
                        btn.click();
                        clickedComment = true;
                        page.waitForTimeout(2000);
                        break;
                    }
                } catch (Exception e) {
                    logger.debug("选择器 {} 未找到: {}", selector, e.getMessage());
                }
            }
            
            if (!clickedComment) {
                System.out.println("[评论抓取] 未找到评论按钮，尝试直接查找评论区...");
            }
            
            // 等待评论区加载
            page.waitForTimeout(2000);
            
            // 查找评论容器并滚动加载
            System.out.println("[评论抓取] 开始滚动加载评论...");
            
            java.util.Random random = new java.util.Random();
            int lastCommentCount = 0;
            int noNewCommentRounds = 0;
            int maxRounds = 50;  // 最多滚动50次
            
            for (int round = 0; round < maxRounds; round++) {
                // 在评论区内滚动（如果有的话），否则滚动整个页面
                page.evaluate("() => {" +
                    "const commentModal = document.querySelector('.css-1xkhpyz, .Comments-container, [class*=\"CommentList\"]');" +
                    "if (commentModal) {" +
                    "  commentModal.scrollTop = commentModal.scrollHeight;" +
                    "} else {" +
                    "  window.scrollTo(0, document.body.scrollHeight);" +
                    "}" +
                    "}");
                
                // 随机等待 1-2 秒，模拟人工
                int delay = 1000 + random.nextInt(1000);
                page.waitForTimeout(delay);
                
                // 从页面提取评论
                List<ZhihuComment> pageComments = extractCommentsFromPage(page, answerId);
                
                // 合并新评论
                int newCount = 0;
                for (ZhihuComment comment : pageComments) {
                    if (!commentMap.containsKey(comment.getId())) {
                        commentMap.put(comment.getId(), comment);
                        allComments.add(comment);
                        newCount++;
                    }
                }
                
                System.out.println("[评论抓取] 第 " + (round + 1) + " 轮滚动，新增 " + newCount + " 条，累计: " + allComments.size());
                
                // 检查是否有新评论
                if (allComments.size() == lastCommentCount) {
                    noNewCommentRounds++;
                    if (noNewCommentRounds >= 3) {
                        System.out.println("[评论抓取] 连续3轮无新评论，停止滚动");
                        break;
                    }
                } else {
                    noNewCommentRounds = 0;
                    lastCommentCount = allComments.size();
                }
                
                // 尝试点击"展开更多"按钮
                try {
                    Locator moreBtn = page.locator("button:has-text('展开'), button:has-text('查看更多'), [class*='more']").first();
                    if (moreBtn.isVisible()) {
                        moreBtn.click();
                        page.waitForTimeout(1000);
                    }
                } catch (Exception e) {
                    // 没有更多按钮，继续
                }
            }
            
            System.out.println("[评论抓取] 滚动完成，共 " + allComments.size() + " 条评论");
            
            // 筛选作者参与的评论
            System.out.println("[评论抓取] 筛选作者参与的评论...");
            List<ZhihuComment> authorComments = filterAuthorComments(allComments, authorId);
            
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
     * 从页面 DOM 中提取评论
     */
    private List<ZhihuComment> extractCommentsFromPage(Page page, String answerId) {
        List<ZhihuComment> comments = new ArrayList<>();
        
        try {
            // 使用 JavaScript 提取评论数据
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> commentData = (List<Map<String, Object>>) page.evaluate(
                "() => {" +
                "  const comments = [];" +
                "  // 查找所有评论元素" +
                "  const commentEls = document.querySelectorAll('[class*=\"CommentItem\"], [class*=\"comment-item\"], div[data-id]');" +
                "  commentEls.forEach(el => {" +
                "    try {" +
                "      const id = el.getAttribute('data-id') || el.id || '';" +
                "      if (!id) return;" +
                "      " +
                "      // 提取作者信息" +
                "      const authorEl = el.querySelector('a[href*=\"/people/\"]');" +
                "      const authorName = authorEl ? authorEl.textContent.trim() : '';" +
                "      const authorHref = authorEl ? authorEl.getAttribute('href') : '';" +
                "      const authorId = authorHref ? authorHref.split('/people/')[1]?.split('/')[0]?.split('?')[0] : '';" +
                "      " +
                "      // 提取内容" +
                "      const contentEl = el.querySelector('[class*=\"CommentContent\"], [class*=\"comment-content\"], .RichText');" +
                "      const content = contentEl ? contentEl.textContent.trim() : '';" +
                "      " +
                "      // 提取点赞数" +
                "      const likeEl = el.querySelector('button[class*=\"like\"], [class*=\"vote\"]');" +
                "      const likeText = likeEl ? likeEl.textContent.replace(/[^0-9]/g, '') : '0';" +
                "      const likeCount = parseInt(likeText) || 0;" +
                "      " +
                "      // 提取回复对象" +
                "      const replyEl = el.querySelector('[class*=\"reply-to\"], [class*=\"ReplyTo\"]');" +
                "      const replyTo = replyEl ? replyEl.textContent.trim() : '';" +
                "      " +
                "      // 检查是否是作者" +
                "      const isAuthor = el.querySelector('[class*=\"author-tag\"], [class*=\"AuthorTag\"]') !== null;" +
                "      " +
                "      if (id && content) {" +
                "        comments.push({ id, authorId, authorName, content, likeCount, replyTo, isAuthor });" +
                "      }" +
                "    } catch (e) {}" +
                "  });" +
                "  return comments;" +
                "}"
            );
            
            if (commentData != null) {
                for (Map<String, Object> data : commentData) {
                    ZhihuComment comment = new ZhihuComment();
                    comment.setId(String.valueOf(data.get("id")));
                    comment.setAnswerId(answerId);
                    comment.setAuthorId(String.valueOf(data.getOrDefault("authorId", "")));
                    comment.setAuthorName(String.valueOf(data.getOrDefault("authorName", "")));
                    comment.setContent(String.valueOf(data.getOrDefault("content", "")));
                    comment.setLikeCount(((Number) data.getOrDefault("likeCount", 0)).intValue());
                    
                    String replyTo = String.valueOf(data.getOrDefault("replyTo", ""));
                    if (!replyTo.isEmpty() && !"null".equals(replyTo)) {
                        comment.setReplyToAuthor(replyTo);
                    }
                    
                    comments.add(comment);
                }
            }
            
        } catch (Exception e) {
            logger.debug("从页面提取评论失败: {}", e.getMessage());
        }
        
        return comments;
    }
    
    /**
     * 获取某条评论的所有子评论
     */
    private void fetchChildComments(Page page, String answerId, String rootCommentId, String authorId,
                                    Map<String, ZhihuComment> commentMap, List<ZhihuComment> allComments) {
        System.out.println("[评论抓取]   获取评论 " + rootCommentId + " 的子评论...");
        
        int offset = 0;
        int limit = 20;
        boolean hasMore = true;
        java.util.Random random = new java.util.Random();
        
        while (hasMore) {
            try {
                // 子评论 API: /api/v4/comment_v5/comment/{rootCommentId}/child_comment
                String apiUrl = String.format(
                    "https://www.zhihu.com/api/v4/comment_v5/comment/%s/child_comment?order_by=ts&limit=%d&offset=%d",
                    rootCommentId, limit, offset
                );
                
                String responseJson = (String) page.evaluate(
                    "(url) => fetch(url, {credentials: 'include'}).then(r => r.text())",
                    apiUrl
                );
                
                if (responseJson == null || responseJson.isEmpty()) {
                    break;
                }
                
                JsonNode root = objectMapper.readTree(responseJson);
                
                if (root.has("error")) {
                    System.out.println("[评论抓取]     子评论 API 返回错误，停止");
                    break;
                }
                
                JsonNode dataArray = root.get("data");
                if (dataArray != null && dataArray.isArray()) {
                    int newCount = 0;
                    for (JsonNode commentNode : dataArray) {
                        ZhihuComment comment = parseComment(commentNode, answerId);
                        if (comment != null && !commentMap.containsKey(comment.getId())) {
                            comment.setParentCommentId(rootCommentId);
                            commentMap.put(comment.getId(), comment);
                            allComments.add(comment);
                            newCount++;
                        }
                    }
                    if (newCount > 0) {
                        System.out.println("[评论抓取]     获取到 " + newCount + " 条子评论");
                    }
                }
                
                // 检查是否还有更多
                JsonNode paging = root.get("paging");
                if (paging != null && paging.has("is_end")) {
                    hasMore = !paging.get("is_end").asBoolean();
                } else {
                    hasMore = dataArray != null && dataArray.size() >= limit;
                }
                
                offset += limit;
                
                // 添加随机延迟 (0.5-1秒)
                if (hasMore) {
                    page.waitForTimeout(500 + random.nextInt(500));
                }
                
            } catch (Exception e) {
                logger.debug("获取子评论失败: {}", e.getMessage());
                break;
            }
        }
    }
    
    /**
     * 解析子评论
     */
    private void parseChildComments(JsonNode parentNode, String answerId, String parentId,
                                    Map<String, ZhihuComment> commentMap, List<ZhihuComment> allComments) {
        // 尝试不同的子评论字段名
        String[] childFields = {"child_comments", "child_comment_list", "replies"};
        
        for (String field : childFields) {
            JsonNode childComments = parentNode.get(field);
            if (childComments != null && childComments.isArray()) {
                for (JsonNode childNode : childComments) {
                    ZhihuComment childComment = parseComment(childNode, answerId);
                    if (childComment != null && !commentMap.containsKey(childComment.getId())) {
                        childComment.setParentCommentId(parentId);
                        commentMap.put(childComment.getId(), childComment);
                        allComments.add(childComment);
                    }
                }
            }
        }
    }
    
    /**
     * 从 HTML 解析评论（备用方案）
     * 根据知乎评论区结构：
     * - 评论容器: .Comments-container
     * - 单条评论: div[data-id]
     * - 作者链接: a 包含 /people/用户ID
     * - 评论内容: .CommentContent
     */
    private List<ZhihuComment> parseCommentsFromHtml(String html, String answerId, String authorId) {
        List<ZhihuComment> comments = new ArrayList<>();
        
        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(html);
            
            // 选择所有带 data-id 的评论元素
            org.jsoup.select.Elements commentElements = doc.select("div[data-id]");
            
            logger.debug("从 HTML 找到 {} 个评论元素", commentElements.size());
            
            for (org.jsoup.nodes.Element element : commentElements) {
                try {
                    ZhihuComment comment = new ZhihuComment();
                    
                    // 获取评论 ID
                    String id = element.attr("data-id");
                    if (id.isEmpty()) continue;
                    
                    comment.setId(id);
                    comment.setAnswerId(answerId);
                    
                    // 获取作者信息（从链接中提取）
                    org.jsoup.nodes.Element authorLink = element.selectFirst("a[href*='/people/']");
                    if (authorLink != null) {
                        comment.setAuthorName(authorLink.text());
                        String href = authorLink.attr("href");
                        if (href.contains("/people/")) {
                            String userId = href.substring(href.lastIndexOf("/people/") + 8);
                            comment.setAuthorId(userId);
                        }
                    }
                    
                    // 获取评论内容
                    org.jsoup.nodes.Element contentEl = element.selectFirst(".CommentContent");
                    if (contentEl != null) {
                        comment.setContent(contentEl.text());
                    }
                    
                    // 获取点赞数（从按钮文本中提取数字）
                    org.jsoup.nodes.Element likeBtn = element.selectFirst("button:contains(赞), button svg + span");
                    if (likeBtn != null) {
                        String likeText = likeBtn.text().replaceAll("[^0-9]", "");
                        if (!likeText.isEmpty()) {
                            comment.setLikeCount(Integer.parseInt(likeText));
                        }
                    }
                    
                    if (comment.getAuthorName() != null && comment.getContent() != null) {
                        comments.add(comment);
                    }
                    
                } catch (Exception e) {
                    logger.debug("解析单个评论元素失败: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("从 HTML 解析评论失败", e);
        }
        
        return comments;
    }
    
    /**
     * 筛选作者参与的评论（作者的回复 + 被作者回复的评论）
     */
    private List<ZhihuComment> filterAuthorComments(List<ZhihuComment> allComments, String authorId) {
        List<ZhihuComment> result = new ArrayList<>();
        Map<String, ZhihuComment> commentMap = new HashMap<>();
        
        // 建立评论索引
        for (ZhihuComment comment : allComments) {
            commentMap.put(comment.getId(), comment);
        }
        
        // 找出作者的所有评论
        for (ZhihuComment comment : allComments) {
            if (authorId.equals(comment.getAuthorId())) {
                // 这是作者的评论
                result.add(comment);
                
                // 如果是回复别人的，把被回复的评论也加入
                if (comment.getParentCommentId() != null) {
                    ZhihuComment parent = commentMap.get(comment.getParentCommentId());
                    if (parent != null && !result.contains(parent)) {
                        result.add(0, parent); // 父评论放前面
                    }
                }
            }
        }
        
        // 按时间排序
        result.sort((a, b) -> {
            if (a.getCreatedTime() == null) return -1;
            if (b.getCreatedTime() == null) return 1;
            return a.getCreatedTime().compareTo(b.getCreatedTime());
        });
        
        return result;
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
            
            // 解析作者
            JsonNode author = node.get("author");
            if (author != null) {
                comment.setAuthorId(author.get("id").asText());
                comment.setAuthorName(author.get("name").asText());
            }
            
            // 解析回复对象
            JsonNode replyTo = node.get("reply_to_author");
            if (replyTo != null && !replyTo.isNull()) {
                comment.setReplyToAuthor(replyTo.get("name").asText());
            }
            
            // 解析时间
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
}
