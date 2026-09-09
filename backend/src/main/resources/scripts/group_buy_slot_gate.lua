-- KEYS[1] = slot-gate key
-- ARGV[1] = target_participants
-- ARGV[2] = db_truth (reserved_slots + current_participants), used only to hydrate a cold key
-- Returns 1 if a provisional slot was granted, 0 if rejected (full).
--
-- Runs atomically (Redis is single-threaded), so EXISTS -> SETNX -> GET -> compare -> INCR
-- can never be interleaved by concurrent callers. Only the first caller to actually reach
-- this script when the key is absent has its ARGV[2] value persisted (SETNX no-ops otherwise),
-- so concurrent hydration races are safe even though the DB read happens outside this script.
redis.call('SETNX', KEYS[1], ARGV[2])
local current = tonumber(redis.call('GET', KEYS[1]))
local target = tonumber(ARGV[1])
if current >= target then
    return 0
end
redis.call('INCR', KEYS[1])
return 1
