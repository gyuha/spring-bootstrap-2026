package com.example.bootstrap.domain.sample.controller;

import com.example.bootstrap.domain.sample.dto.SampleCreateRequest;
import com.example.bootstrap.domain.sample.dto.SampleResponse;
import com.example.bootstrap.domain.sample.service.SampleService;
import com.example.bootstrap.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample 참조 슬라이스 컨트롤러 (FOUND-01).
 *
 * <p>패키지/레이어 규칙대로 추가하면 별도 배선 없이 동작함을 증명하는 살아있는 예시(D-05/D-06).
 * 표준 성공 응답은 {@link ApiResponse}로 감싼다(D-18).
 */
@RestController
@RequestMapping("/samples")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;

    @GetMapping
    public ApiResponse<List<SampleResponse>> findAll() {
        return ApiResponse.ok(sampleService.findAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SampleResponse> create(@Valid @RequestBody SampleCreateRequest request) {
        return ApiResponse.ok(sampleService.create(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        sampleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
