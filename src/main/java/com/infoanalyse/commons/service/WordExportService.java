package com.infoanalyse.commons.service;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WordExportService {
    
    private static final Logger logger = LoggerFactory.getLogger(WordExportService.class);
    
    // Markdown 图片正则: ![alt](path)
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");
    // Markdown 链接正则: [text](url)
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
    // Markdown 粗体: **text** 或 __text__
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+)\\*\\*|__([^_]+)__");
    
    /**
     * 将 Markdown 文件导出为 Word 文档
     */
    public Path exportToWord(Path mdFile) throws Exception {
        return exportToWord(mdFile, null);
    }
    
    /**
     * 将 Markdown 文件导出为 Word 文档到指定目录
     */
    public Path exportToWord(Path mdFile, Path outputDir) throws Exception {
        String content = Files.readString(mdFile);
        Path parentDir = mdFile.getParent();
        String fileName = mdFile.getFileName().toString().replace(".md", ".docx");
        
        Path outputPath;
        if (outputDir != null) {
            Files.createDirectories(outputDir);
            outputPath = outputDir.resolve(fileName);
        } else {
            outputPath = parentDir.resolve(fileName);
        }
        
        try (XWPFDocument document = new XWPFDocument()) {
            String[] lines = content.split("\n");
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                
                if (line.isEmpty()) {
                    document.createParagraph();
                    continue;
                }
                
                if (line.startsWith("# ")) {
                    addHeading(document, line.substring(2), 1);
                } else if (line.startsWith("## ")) {
                    addHeading(document, line.substring(3), 2);
                } else if (line.startsWith("### ")) {
                    addHeading(document, line.substring(4), 3);
                } else if (line.startsWith("#### ")) {
                    addHeading(document, line.substring(5), 4);
                } else if (line.equals("---") || line.equals("***") || line.equals("___")) {
                    addHorizontalLine(document);
                } else if (line.startsWith("- ") || line.startsWith("* ")) {
                    addListItem(document, line.substring(2), parentDir);
                } else if (line.startsWith("![")) {
                    addImageLine(document, line, parentDir);
                } else if (line.startsWith("|") && line.endsWith("|")) {
                    java.util.List<String> tableLines = new java.util.ArrayList<>();
                    tableLines.add(line);
                    while (i + 1 < lines.length && lines[i + 1].trim().startsWith("|")) {
                        i++;
                        tableLines.add(lines[i].trim());
                    }
                    addTable(document, tableLines);
                } else if (line.startsWith("> ")) {
                    addQuote(document, line.substring(2));
                } else {
                    addParagraph(document, line, parentDir);
                }
            }
            
            try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
                document.write(out);
            }
        }
        
        return outputPath;
    }

    private void addHeading(XWPFDocument document, String text, int level) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle("Heading" + level);
        XWPFRun run = paragraph.createRun();
        run.setText(cleanMarkdown(text));
        run.setBold(true);
        switch (level) {
            case 1 -> run.setFontSize(24);
            case 2 -> run.setFontSize(18);
            case 3 -> run.setFontSize(14);
            default -> run.setFontSize(12);
        }
    }
    
    private void addParagraph(XWPFDocument document, String text, Path parentDir) {
        XWPFParagraph paragraph = document.createParagraph();
        Matcher imgMatcher = IMAGE_PATTERN.matcher(text);
        if (imgMatcher.find()) {
            int lastEnd = 0;
            imgMatcher.reset();
            while (imgMatcher.find()) {
                if (imgMatcher.start() > lastEnd) {
                    addFormattedText(paragraph, text.substring(lastEnd, imgMatcher.start()));
                }
                addImage(paragraph, imgMatcher.group(2), parentDir);
                lastEnd = imgMatcher.end();
            }
            if (lastEnd < text.length()) {
                addFormattedText(paragraph, text.substring(lastEnd));
            }
        } else {
            addFormattedText(paragraph, text);
        }
    }
    
    private void addFormattedText(XWPFParagraph paragraph, String text) {
        Matcher boldMatcher = BOLD_PATTERN.matcher(text);
        int lastEnd = 0;
        while (boldMatcher.find()) {
            if (boldMatcher.start() > lastEnd) {
                XWPFRun run = paragraph.createRun();
                run.setText(cleanMarkdown(text.substring(lastEnd, boldMatcher.start())));
            }
            XWPFRun boldRun = paragraph.createRun();
            String boldText = boldMatcher.group(1) != null ? boldMatcher.group(1) : boldMatcher.group(2);
            boldRun.setText(boldText);
            boldRun.setBold(true);
            lastEnd = boldMatcher.end();
        }
        if (lastEnd < text.length()) {
            XWPFRun run = paragraph.createRun();
            run.setText(cleanMarkdown(text.substring(lastEnd)));
        } else if (lastEnd == 0) {
            XWPFRun run = paragraph.createRun();
            run.setText(cleanMarkdown(text));
        }
    }
    
    private void addImage(XWPFParagraph paragraph, String imagePath, Path parentDir) {
        try {
            if (imagePath.startsWith("http")) {
                XWPFRun run = paragraph.createRun();
                run.setText("[图片: " + imagePath + "]");
                return;
            }
            Path imgFile = parentDir.resolve(imagePath);
            if (!Files.exists(imgFile)) {
                XWPFRun run = paragraph.createRun();
                run.setText("[图片不存在: " + imagePath + "]");
                return;
            }
            BufferedImage img = ImageIO.read(imgFile.toFile());
            int width = img.getWidth();
            int height = img.getHeight();
            int maxWidth = 500;
            if (width > maxWidth) {
                height = (int) ((double) height * maxWidth / width);
                width = maxWidth;
            }
            XWPFRun run = paragraph.createRun();
            try (InputStream is = Files.newInputStream(imgFile)) {
                String ext = imagePath.substring(imagePath.lastIndexOf('.') + 1).toLowerCase();
                int imgType = switch (ext) {
                    case "png" -> XWPFDocument.PICTURE_TYPE_PNG;
                    case "gif" -> XWPFDocument.PICTURE_TYPE_GIF;
                    default -> XWPFDocument.PICTURE_TYPE_JPEG;
                };
                run.addPicture(is, imgType, imgFile.getFileName().toString(),
                        Units.toEMU(width), Units.toEMU(height));
            }
        } catch (Exception e) {
            logger.warn("添加图片失败: {}", imagePath, e);
            XWPFRun run = paragraph.createRun();
            run.setText("[图片加载失败: " + imagePath + "]");
        }
    }
    
    private void addImageLine(XWPFDocument document, String line, Path parentDir) {
        Matcher matcher = IMAGE_PATTERN.matcher(line);
        if (matcher.find()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setAlignment(ParagraphAlignment.CENTER);
            addImage(paragraph, matcher.group(2), parentDir);
        }
    }
    
    private void addListItem(XWPFDocument document, String text, Path parentDir) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun bullet = paragraph.createRun();
        bullet.setText("• ");
        addFormattedText(paragraph, text);
    }
    
    private void addQuote(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(720);
        XWPFRun run = paragraph.createRun();
        run.setText(cleanMarkdown(text));
        run.setItalic(true);
        run.setColor("666666");
    }
    
    private void addHorizontalLine(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setBorderBottom(Borders.SINGLE);
    }
    
    private void addTable(XWPFDocument document, java.util.List<String> lines) {
        if (lines.size() < 2) return;
        String[] headers = parseTableRow(lines.get(0));
        int cols = headers.length;
        int dataStart = 1;
        if (lines.size() > 1 && lines.get(1).contains("---")) {
            dataStart = 2;
        }
        XWPFTable table = document.createTable(lines.size() - dataStart + 1, cols);
        XWPFTableRow headerRow = table.getRow(0);
        for (int i = 0; i < cols; i++) {
            XWPFTableCell cell = headerRow.getCell(i);
            cell.setText(headers[i].trim());
            cell.setColor("E0E0E0");
        }
        for (int r = dataStart; r < lines.size(); r++) {
            String[] cells = parseTableRow(lines.get(r));
            XWPFTableRow row = table.getRow(r - dataStart + 1);
            for (int c = 0; c < Math.min(cols, cells.length); c++) {
                row.getCell(c).setText(cells[c].trim());
            }
        }
    }
    
    private String[] parseTableRow(String line) {
        if (line.startsWith("|")) line = line.substring(1);
        if (line.endsWith("|")) line = line.substring(0, line.length() - 1);
        return line.split("\\|");
    }
    
    private String cleanMarkdown(String text) {
        text = LINK_PATTERN.matcher(text).replaceAll("$1");
        text = text.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        text = text.replaceAll("__([^_]+)__", "$1");
        text = text.replaceAll("\\*([^*]+)\\*", "$1");
        text = text.replaceAll("_([^_]+)_", "$1");
        text = text.replaceAll("`([^`]+)`", "$1");
        return text;
    }
}
