-- ------------------------------------------------------------
-- 给简历补归属字段（修复 IDOR：拿到别人的 resumeId 就能开面试）
--
-- 背景：
-- resume 表早期没有 user_id，InterviewServiceImpl#doStart 也就无法校验
-- "这份简历是不是当前用户的"。代码侧已改成上传时写 user_id、
-- 开始面试时校验；对 user_id 为空的老数据暂时放行并打日志。
--
-- 执行顺序：先加列 → 再回填 → 之后可以把校验收紧成"必须匹配"。
-- ------------------------------------------------------------

ALTER TABLE `resume`
    ADD COLUMN `user_id` BIGINT DEFAULT NULL COMMENT '所属用户ID（历史数据可能为空）' AFTER `status`;

CREATE INDEX `idx_resume_user` ON `resume` (`user_id`);

-- 历史数据回填：从面试会话反推（同一份简历可能被同一个人开过多次面试）
UPDATE `resume` r
    JOIN (
        SELECT `resume_id`, MIN(`user_id`) AS `user_id`
        FROM `interview_session`
        WHERE `resume_id` IS NOT NULL
          AND `user_id` IS NOT NULL
        GROUP BY `resume_id`
    ) s ON s.`resume_id` = r.`id`
SET r.`user_id` = s.`user_id`
WHERE r.`user_id` IS NULL;

-- 回填后检查还有多少没归属（正常应该为 0；不为 0 的简历建议让用户重新上传）
-- SELECT COUNT(*) FROM `resume` WHERE `user_id` IS NULL;
