-- 文档处理管道稳定性重构 - DDL 迁移脚本
-- 执行前请先备份数据库

-- 1. 新增 retry_count 字段（补偿任务使用）
ALTER TABLE knowledge_document ADD COLUMN retry_count INT DEFAULT 0 COMMENT '补偿重试次数（默认0，上限3）';

-- 2. 为补偿定时任务添加联合索引（加速 status + update_time 查询）
CREATE INDEX idx_doc_status_update_time ON knowledge_document(status, update_time);
