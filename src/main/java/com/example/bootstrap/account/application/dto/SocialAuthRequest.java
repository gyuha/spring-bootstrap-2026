package com.example.bootstrap.account.application.dto;

import jakarta.validation.constraints.NotBlank;

public record SocialAuthRequest(@NotBlank String accessToken) {
}
