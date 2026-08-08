package com.example.demo.dto.signup;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UsernameCheckResponse {

    private boolean available;
}
