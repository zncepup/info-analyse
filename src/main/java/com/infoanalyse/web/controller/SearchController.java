package com.infoanalyse.web.controller;

import com.infoanalyse.dao.mapper.SearchMapper;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchMapper searchMapper;

    public SearchController(SearchMapper searchMapper) {
        this.searchMapper = searchMapper;
    }

    @GetMapping
    public Map<String, Object> search(@RequestParam("q") String keyword,
                                      @RequestParam(value = "limit", defaultValue = "50") int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Map.of("results", List.of(), "keyword", "");
        }
        keyword = keyword.trim();
        if (limit > 200) limit = 200;

        List<Map<String, Object>> raw = searchMapper.searchAll(keyword, limit);

        // 按 link (内容维度) 聚合
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> r : raw) {
            String prefix = (String) r.get("linkPrefix");
            Object targetId = r.get("targetId");
            String link = (prefix != null && targetId != null) ? prefix + targetId : null;
            r.remove("linkPrefix");
            r.put("link", link);

            if (link == null) continue;

            String type = (String) r.get("type");
            boolean isContent = Set.of("answer", "article", "pin", "guba_post").contains(type);

            if (!grouped.containsKey(link)) {
                if (isContent) {
                    // 内容本身命中，作为聚合头
                    Map<String, Object> group = new LinkedHashMap<>(r);
                    group.put("matches", new ArrayList<>());
                    grouped.put(link, group);
                } else {
                    // 评论/AI先命中，创建占位头
                    Map<String, Object> group = new LinkedHashMap<>();
                    group.put("link", link);
                    group.put("targetId", targetId);
                    group.put("title", null); // 待填充
                    group.put("authorName", null);
                    group.put("time", null);
                    group.put("type", guessContentType(link));
                    List<Map<String, Object>> matches = new ArrayList<>();
                    matches.add(buildMatch(r));
                    group.put("matches", matches);
                    grouped.put(link, group);
                }
            } else {
                Map<String, Object> group = grouped.get(link);
                if (isContent && group.get("title") == null) {
                    // 填充占位头
                    group.put("title", r.get("title"));
                    group.put("authorName", r.get("authorName"));
                    group.put("time", r.get("time"));
                    group.put("snippet", r.get("snippet"));
                    group.put("type", type);
                } else if (!isContent) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> matches = (List<Map<String, Object>>) group.get("matches");
                    matches.add(buildMatch(r));
                }
                // 如果内容重复命中（不太可能），忽略
            }
        }

        List<Map<String, Object>> results = new ArrayList<>(grouped.values());

        // 填充占位头的标题（评论/AI命中但内容本身没命中的情况）
        for (Map<String, Object> group : results) {
            if (group.get("title") == null) {
                String type = (String) group.get("type");
                Object targetId = group.get("targetId");
                if (type != null && targetId != null) {
                    Map<String, Object> info = searchMapper.findContentTitle(type, targetId);
                    if (info != null) {
                        group.put("title", info.get("title"));
                        group.put("authorName", info.get("authorName"));
                        group.put("time", info.get("time"));
                    }
                }
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("keyword", keyword);
        resp.put("total", results.size());
        resp.put("results", results);
        return resp;
    }

    private Map<String, Object> buildMatch(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", r.get("type"));
        m.put("title", r.get("title"));
        m.put("snippet", r.get("snippet"));
        m.put("authorName", r.get("authorName"));
        return m;
    }

    private String guessContentType(String link) {
        if (link.contains("/answer/")) return "answer";
        if (link.contains("/article/")) return "article";
        if (link.contains("/pin/")) return "pin";
        if (link.contains("/guba/post/")) return "guba_post";
        return "unknown";
    }
}
