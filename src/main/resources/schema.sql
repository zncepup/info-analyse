USE info_analyse;

-- 知乎回答
CREATE TABLE IF NOT EXISTS zhihu_answer (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    answer_id       BIGINT          NOT NULL COMMENT '知乎回答ID',
    question_id     BIGINT          COMMENT '问题ID',
    question_title  VARCHAR(500)    COMMENT '问题标题',
    author_name     VARCHAR(100)    COMMENT '作者昵称',
    author_id       VARCHAR(100)    COMMENT '作者ID',
    content         MEDIUMTEXT      COMMENT '纯文本内容',
    html_content    MEDIUMTEXT      COMMENT 'HTML原始内容',
    voteup_count    INT DEFAULT 0   COMMENT '赞同数',
    comment_count   INT DEFAULT 0   COMMENT '评论数',
    url             VARCHAR(500)    COMMENT '原文链接',
    created_time    DATETIME        COMMENT '回答创建时间',
    updated_time    DATETIME        COMMENT '回答修改时间',
    crawl_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',
    UNIQUE KEY uk_answer_id (answer_id),
    INDEX idx_question_id (question_id),
    INDEX idx_author_id (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知乎回答';

-- 知乎文章
CREATE TABLE IF NOT EXISTS zhihu_article (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    article_id      BIGINT          NOT NULL COMMENT '知乎文章ID',
    title           VARCHAR(500)    COMMENT '文章标题',
    author_name     VARCHAR(100)    COMMENT '作者昵称',
    author_id       VARCHAR(100)    COMMENT '作者ID',
    content         MEDIUMTEXT      COMMENT '纯文本内容',
    html_content    MEDIUMTEXT      COMMENT 'HTML原始内容',
    voteup_count    INT DEFAULT 0   COMMENT '赞同数',
    comment_count   INT DEFAULT 0   COMMENT '评论数',
    url             VARCHAR(500)    COMMENT '原文链接',
    created_time    DATETIME        COMMENT '文章创建时间',
    updated_time    DATETIME        COMMENT '文章修改时间',
    crawl_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',
    UNIQUE KEY uk_article_id (article_id),
    INDEX idx_author_id (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知乎文章';

-- 知乎评论
CREATE TABLE IF NOT EXISTS zhihu_comment (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    comment_id          BIGINT          NOT NULL COMMENT '知乎评论ID',
    target_id           BIGINT          NOT NULL COMMENT '所属回答/文章ID',
    target_type         TINYINT         NOT NULL DEFAULT 1 COMMENT '目标类型: 1=回答 2=文章',
    author_name         VARCHAR(100)    COMMENT '评论者昵称',
    author_id           VARCHAR(100)    COMMENT '评论者ID',
    content             TEXT            COMMENT '评论内容',
    like_count          INT DEFAULT 0   COMMENT '点赞数',
    parent_comment_id   BIGINT          COMMENT '父评论ID（根评论为NULL）',
    reply_comment_id    BIGINT          COMMENT '回复的评论ID',
    reply_to_author     VARCHAR(100)    COMMENT '回复目标作者昵称',
    created_time        DATETIME        COMMENT '评论时间',
    crawl_time          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',
    UNIQUE KEY uk_comment_id (comment_id),
    INDEX idx_target (target_id, target_type),
    INDEX idx_parent (parent_comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知乎评论';

-- 股吧帖子
CREATE TABLE IF NOT EXISTS guba_post (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    post_id         BIGINT          NOT NULL COMMENT '东方财富帖子ID',
    stock_code      VARCHAR(20)     NOT NULL COMMENT '股票代码',
    stock_name      VARCHAR(50)     COMMENT '股票名称',
    title           VARCHAR(500)    COMMENT '帖子标题',
    content         MEDIUMTEXT      COMMENT '纯文本内容',
    html_content    MEDIUMTEXT      COMMENT 'HTML原始内容',
    author_name     VARCHAR(100)    COMMENT '作者昵称',
    author_id       VARCHAR(100)    COMMENT '作者ID',
    read_count      INT DEFAULT 0   COMMENT '阅读数',
    comment_count   INT DEFAULT 0   COMMENT '评论数',
    like_count      INT DEFAULT 0   COMMENT '点赞数',
    url             VARCHAR(500)    COMMENT '原文链接',
    publish_time    DATETIME        COMMENT '发布时间',
    crawl_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',
    UNIQUE KEY uk_post_id (post_id),
    INDEX idx_stock_code (stock_code),
    INDEX idx_author_id (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股吧帖子';

-- 股吧评论
CREATE TABLE IF NOT EXISTS guba_comment (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
    comment_id          BIGINT          NOT NULL COMMENT '东方财富评论ID',
    post_id             BIGINT          NOT NULL COMMENT '所属帖子ID',
    author_name         VARCHAR(100)    COMMENT '评论者昵称',
    author_id           VARCHAR(100)    COMMENT '评论者ID',
    content             TEXT            COMMENT '评论内容',
    like_count          INT DEFAULT 0   COMMENT '点赞数',
    reply_to_comment_id BIGINT          COMMENT '回复的评论ID（根评论为NULL）',
    reply_to_user       VARCHAR(100)    COMMENT '回复目标用户昵称',
    publish_time        DATETIME        COMMENT '评论时间',
    crawl_time          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',
    UNIQUE KEY uk_comment_id (comment_id),
    INDEX idx_post_id (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股吧评论';

-- 爬取图片
CREATE TABLE IF NOT EXISTS crawl_image (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    source          VARCHAR(20)     NOT NULL COMMENT '来源: zhihu / guba',
    target_id       BIGINT          NOT NULL COMMENT '所属回答/文章/帖子ID（业务ID）',
    target_type     VARCHAR(20)     NOT NULL COMMENT '目标类型: answer / article / post',
    original_url    VARCHAR(1000)   NOT NULL COMMENT '原始图片URL',
    local_path      VARCHAR(500)    COMMENT '本地存储路径（相对路径）',
    file_size       INT             COMMENT '文件大小（字节）',
    sort_order      INT DEFAULT 0   COMMENT '图片在内容中的顺序',
    download_status TINYINT DEFAULT 0 COMMENT '0=未下载 1=已下载 2=下载失败',
    crawl_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_source_url (source, original_url(200)),
    INDEX idx_target (source, target_id, target_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬取图片';

-- 抓取任务记录
CREATE TABLE IF NOT EXISTS crawl_task (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    source          VARCHAR(20)     NOT NULL COMMENT '来源: zhihu / guba',
    task_type       VARCHAR(50)     NOT NULL COMMENT '任务类型',
    target_id       VARCHAR(100)    COMMENT '目标ID',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED',
    result_count    INT DEFAULT 0   COMMENT '抓取结果数量',
    message         TEXT            COMMENT '结果描述或错误信息',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_time   DATETIME        COMMENT '完成时间',
    INDEX idx_source_status (source, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抓取任务记录';

-- AI分析结果
CREATE TABLE IF NOT EXISTS ai_analysis (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    source          VARCHAR(20)     NOT NULL COMMENT '来源: zhihu / guba',
    target_id       BIGINT          NOT NULL COMMENT '所属回答/文章/帖子的业务ID',
    target_type     VARCHAR(20)     NOT NULL COMMENT '目标类型: answer / article / post',
    ai_model        VARCHAR(100)    NOT NULL COMMENT 'AI模型名称',
    analysis_type   VARCHAR(50)     NOT NULL DEFAULT 'investment_clue' COMMENT '分析类型: investment_clue / summary / sentiment 等',
    prompt_digest   VARCHAR(64)     COMMENT '提示词摘要(用于去重)',
    result          MEDIUMTEXT      COMMENT '分析结果',
    status          VARCHAR(20)     NOT NULL DEFAULT 'COMPLETED' COMMENT 'PENDING/RUNNING/COMPLETED/FAILED',
    error_message   TEXT            COMMENT '失败原因',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分析时间',
    INDEX idx_target (source, target_id, target_type),
    INDEX idx_model_type (ai_model, analysis_type),
    UNIQUE KEY uk_target_model_type (source, target_id, target_type, ai_model, analysis_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析结果';
