-- =====================================================
-- Chinan 数据库迁移脚本 V5: Android 离线补交（服务端幂等 + 迟到标记）
-- 适用数据库: PostgreSQL / H2 (MODE=PostgreSQL)
-- MySQL 8 兼容写法见文件末尾说明
-- =====================================================

-- 1. 客户端幂等键：客户端每次提交生成的 UUID 字符串
--    同一 clientOpId 重复提交时，服务端直接返回首次落库的记录，不重复写入、不重复审计
--    可空：兼容未携带该字段的旧客户端与历史数据
ALTER TABLE check_record ADD COLUMN IF NOT EXISTS client_op_id VARCHAR(64);

-- 2. 迟到标记：taskDate 早于服务器接收日期（补交）时为 TRUE
--    taskDate 本身不可变，报表口径仍按 taskDate 入账；submitted_at 为服务器接收时间
--    历史数据统一回填 FALSE（非补交）
ALTER TABLE check_record ADD COLUMN IF NOT EXISTS late BOOLEAN DEFAULT FALSE;

-- 3. 幂等唯一索引
--    注意：一次房间提交会为房间内每个学生各写一行 check_record（同一 client_op_id 对应 N 行），
--    因此唯一键必须带上 student_id；单列 (client_op_id) 唯一索引会让同一 op 的第 2 个学生插入失败。
--    该索引的语义仍是“同一 clientOpId 不产生重复行”，并作为并发重复提交的兜底约束。
CREATE UNIQUE INDEX IF NOT EXISTS idx_check_record_client_op_student
    ON check_record(client_op_id, student_id);

-- =====================================================
-- MySQL 8 兼容说明：MySQL 8 不支持 ADD COLUMN/CREATE INDEX 的 IF NOT EXISTS，
-- 请改用下列等价语句（重复执行会报“已存在”，手工确认后忽略即可）：
--   ALTER TABLE check_record ADD COLUMN client_op_id VARCHAR(64) NULL;
--   ALTER TABLE check_record ADD COLUMN late TINYINT(1) NOT NULL DEFAULT 0;
--   CREATE UNIQUE INDEX idx_check_record_client_op_student ON check_record(client_op_id, student_id);
-- =====================================================
