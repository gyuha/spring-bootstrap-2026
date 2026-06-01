-- Refresh 1회용 회전 + 재사용 탐지 (AUTH-03/04, D-24/26)
-- GET → 비교 → SET을 단일 Lua로 원자 실행해 가상 스레드 double-rotate race를 차단한다.
-- KEYS[1] = refresh:{userId}:{familyId}  (현재 유효 Refresh의 SHA-256 해시)
-- KEYS[2] = refresh:idx:{userId}         (familyId 인덱스 Set)
-- ARGV[1] = 제출된 토큰의 해시 (검증 대상)
-- ARGV[2] = 새 토큰의 해시
-- ARGV[3] = familyId
-- ARGV[4] = TTL(초)
-- 반환: 1=회전 성공, 0=재사용 탐지(현재값 불일치 또는 키 없음)
local current = redis.call('GET', KEYS[1])
if current == false or current ~= ARGV[1] then
    -- 재사용/탈취 신호: 이 패밀리 즉시 무효화 + 호출측에 0 반환
    redis.call('DEL', KEYS[1])
    redis.call('SREM', KEYS[2], ARGV[3])
    return 0
end
-- 정상 회전: 현재값을 새 해시로 교체(원자적 compare-and-set)
redis.call('SET', KEYS[1], ARGV[2], 'EX', tonumber(ARGV[4]))
return 1
