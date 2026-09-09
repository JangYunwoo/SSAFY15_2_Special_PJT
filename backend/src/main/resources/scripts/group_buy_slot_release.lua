-- KEYS[1] = slot-gate key
-- Decrements the counter, floored at zero. The floor is defensive only (correct
-- operation always balances grant/release) -- it exists so a bug drives the gate
-- too permissive rather than corrupting the key into a negative value.
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
if current > 0 then
    redis.call('DECR', KEYS[1])
end
return current
