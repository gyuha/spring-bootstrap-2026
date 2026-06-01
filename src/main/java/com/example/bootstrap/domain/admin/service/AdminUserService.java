package com.example.bootstrap.domain.admin.service;

import com.example.bootstrap.domain.admin.dto.AdminUserResponse;
import com.example.bootstrap.domain.admin.mapper.UserQueryMapper;
import com.example.bootstrap.domain.user.entity.User;
import com.example.bootstrap.domain.user.repository.UserRepository;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import com.example.bootstrap.global.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 사용자 조회 비즈니스 로직 + 트랜잭션 경계 (ADMIN-01/02, D-08).
 *
 * <p>목록 페이징은 MyBatis {@link UserQueryMapper}(D-46/47), 단건은 JPA
 * {@link UserRepository#findById}({@code @SQLRestriction}이 soft-delete 자동 제외, D-50)로
 * 처리한다. 둘은 단일 JpaTransactionManager를 공유한다.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserQueryMapper userQueryMapper;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> findPage(int page, int size) {
        long offset = (long) page * size;
        return PageResponse.of(
                userQueryMapper.findPage(offset, size),
                userQueryMapper.countActive(),
                page,
                size);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse findOne(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new AdminUserResponse(
                user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
