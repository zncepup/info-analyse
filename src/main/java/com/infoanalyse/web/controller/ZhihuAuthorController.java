package com.infoanalyse.web.controller;

import com.infoanalyse.dao.mapper.*;
import com.infoanalyse.dao.model.*;
import com.infoanalyse.zhihu.service.ZhihuBrowserCrawlerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/zhihu/authors")
public class ZhihuAuthorController {

    private static final Pattern PEOPLE_URL = Pattern.compile("zhihu\\.com/people/([^/?#]+)");

    private final ZhihuAuthorDOMapper authorMapper;
    private final ZhihuAnswerDOMapper answerMapper;
    private final ZhihuArticleDOMapper articleMapper;
    private final ZhihuPinDOMapper pinMapper;
    private final ZhihuBrowserCrawlerService crawlerService;

    public ZhihuAuthorController(ZhihuAuthorDOMapper authorMapper,
                                 ZhihuAnswerDOMapper answerMapper,
                                 ZhihuArticleDOMapper articleMapper,
                                 ZhihuPinDOMapper pinMapper,
                                 ZhihuBrowserCrawlerService crawlerService) {
        this.authorMapper = authorMapper;
        this.answerMapper = answerMapper;
        this.articleMapper = articleMapper;
        this.pinMapper = pinMapper;
        this.crawlerService = crawlerService;
    }

    @GetMapping
    public List<AuthorVO> list() {
        List<ZhihuAuthorDO> authors = authorMapper.selectAll();
        List<AuthorVO> result = new ArrayList<>();
        for (ZhihuAuthorDO a : authors) {
            result.add(new AuthorVO(a.getId(), a.getUserId(), a.getAuthorName(),
                    a.getProfileUrl(), a.getCreatedTime()));
        }
        return result;
    }

    @PostMapping
    public AuthorVO add(@RequestBody AddRequest req) {
        if (req.url == null || req.url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入知乎主页链接");
        }
        Matcher m = PEOPLE_URL.matcher(req.url.trim());
        if (!m.find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "链接格式不正确，请输入 https://www.zhihu.com/people/xxx 格式的链接");
        }
        String userId = m.group(1);

        // 检查是否已存在
        ZhihuAuthorDO existing = authorMapper.selectByUserId(userId);
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该作者已存在: " + existing.getAuthorName());
        }

        // 尝试获取昵称
        String name = null;
        try {
            name = crawlerService.fetchAuthorName(userId);
        } catch (Exception e) {
            // 获取失败不阻塞，用 userId 作为临时名称
        }

        ZhihuAuthorDO author = new ZhihuAuthorDO();
        author.setUserId(userId);
        author.setAuthorName(name != null ? name : userId);
        author.setProfileUrl("https://www.zhihu.com/people/" + userId);
        authorMapper.insert(author);

        return new AuthorVO(author.getId(), author.getUserId(), author.getAuthorName(),
                author.getProfileUrl(), author.getCreatedTime());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable("id") Long id) {
        ZhihuAuthorDO author = authorMapper.selectAll().stream()
                .filter(a -> a.getId().equals(id)).findFirst().orElse(null);
        if (author == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "作者不存在");
        }

        // 检查是否有内容
        String authorName = author.getAuthorName();
        boolean hasContent = hasContentByAuthorName(authorName);
        if (hasContent) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "该作者下有已爬取的内容，无法删除。请先删除相关内容。");
        }

        authorMapper.deleteById(id);
        return Map.of("success", true);
    }

    private boolean hasContentByAuthorName(String authorName) {
        // 检查回答
        ZhihuAnswerDOExample ae = new ZhihuAnswerDOExample();
        ae.createCriteria().andAuthorNameEqualTo(authorName);
        if (answerMapper.countByExample(ae) > 0) return true;
        // 检查文章
        ZhihuArticleDOExample are = new ZhihuArticleDOExample();
        are.createCriteria().andAuthorNameEqualTo(authorName);
        if (articleMapper.countByExample(are) > 0) return true;
        // 检查想法
        ZhihuPinDOExample pe = new ZhihuPinDOExample();
        pe.createCriteria().andAuthorNameEqualTo(authorName);
        return pinMapper.countByExample(pe) > 0;
    }

    public static class AddRequest {
        public String url;
    }

    public record AuthorVO(Long id, String userId, String authorName,
                           String profileUrl, Date createdTime) {}
}
