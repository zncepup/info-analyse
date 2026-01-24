package com.infoanalyse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infoanalyse.model.ZhihuAnswer;
import com.microsoft.playwright.*;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.List;

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
            String pageTitle = page.title();
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
        
        // 解析内容（去除HTML标签）
        if (node.has("content")) {
            String htmlContent = node.get("content").asText();
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
}
