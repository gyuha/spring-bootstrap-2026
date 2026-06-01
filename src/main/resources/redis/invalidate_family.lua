-- userId 단위 Refresh 전량 무효화 (D-27, Phase 3 ADMIN-03 재사용)
-- 인덱스 Set을 순회해 각 패밀리 키를 DEL한다. KEYS * 스캔 금지(운영 Redis 블로킹 방지).
-- KEYS[1] = refresh:idx:{userId}
-- ARGV[1] = "refresh:{userId}:"  (패밀리 키 프리픽스)
-- 반환: 무효화한 패밀리 수
local families = redis.call('SMEMBERS', KEYS[1])
for _, fam in ipairs(families) do
    redis.call('DEL', ARGV[1] .. fam)
end
redis.call('DEL', KEYS[1])
return #families
