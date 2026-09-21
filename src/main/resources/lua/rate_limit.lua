-- KEYS[1]: 限流 key
-- ARGV[1]: 窗口大小（毫秒）
-- ARGV[2]: 最大请求数
-- ARGV[3]: 当前时间戳（毫秒）
local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- 删除窗口外的记录
redis.call('zremrangebyscore', key, 0, now - window)
-- 统计窗口内数量
local count = redis.call('zcard', key)
if count >= limit then
    return 0
end
-- 加入当前请求
redis.call('zadd', key, now, now)
redis.call('expire', key, window / 1000 + 1)
return count + 1