-- ------------------------------------------------------------
-- interview_report.session_id 唯一索引
--
-- 目的：一条会话只允许一份报告。
-- 全新库由 deploy/schema.sql 建表时已带 uk_session_id，这个脚本用于老库。
--
-- 注意：
-- 1) 先清理历史重复（保留 id 最小的一条），否则加索引会失败；
-- 2) 如果索引已经存在，第 2 条 ALTER 会报 "Duplicate key name"，可以忽略。
-- ------------------------------------------------------------

DELETE r1
FROM `interview_report` r1
         JOIN `interview_report` r2
              ON r1.`session_id` = r2.`session_id`
                  AND r1.`id` > r2.`id`;

ALTER TABLE `interview_report`
    ADD UNIQUE KEY `uk_session_id` (`session_id`);
