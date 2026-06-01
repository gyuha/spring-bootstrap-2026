package com.example.bootstrap.domain.board.controller;

import com.example.bootstrap.domain.board.dto.BoardCreateRequest;
import com.example.bootstrap.domain.board.dto.BoardResponse;
import com.example.bootstrap.domain.board.service.BoardService;
import com.example.bootstrap.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시판 생성/조회 API (BOARD-01).
 *
 * <p>생성은 ADMIN 전용 — 경로 매처(SecurityConfig)가 아닌 {@code @PreAuthorize("hasRole('ADMIN')")}로
 * 인가한다(D-75/D-80). 따라서 SecurityConfig는 변경하지 않으며 USER 호출은 403
 * ({@code GlobalExceptionHandler.handleAuthorizationDenied})이 된다. 조회는 인증 USER면 충분
 * (SecurityConfig {@code anyRequest().authenticated()}). board는 생성+단건 조회만 제공한다
 * (수정/삭제 엔드포인트는 범위 외 — D-26 Deferred).
 */
@RestController
@RequestMapping("/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BoardResponse> create(@Valid @RequestBody BoardCreateRequest request) {
        return ApiResponse.ok(boardService.create(request.name()));
    }

    @GetMapping("/{boardId}")
    public ApiResponse<BoardResponse> get(@PathVariable UUID boardId) {
        return ApiResponse.ok(boardService.findOne(boardId));
    }
}
