package com.anchors.baseline.platform.interfaces.rest;

import com.anchors.baseline.platform.application.SampleApplicationService;
import com.anchors.baseline.platform.application.SampleDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * platform 샘플 REST 어댑터. 인바운드 어댑터로서 application 계층만 의존한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/samples")
public class SampleController {

    private final SampleApplicationService sampleApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Long create(@RequestBody CreateSampleRequest request) {
        return sampleApplicationService.save(request.value());
    }

    @GetMapping
    public List<SampleDto> findAll() {
        return sampleApplicationService.findAll();
    }

    public record CreateSampleRequest(String value) {
    }
}
