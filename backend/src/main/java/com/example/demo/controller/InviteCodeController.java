package com.example.demo.controller;

import com.example.demo.dto.signup.InviteCodeValidateRequest;
import com.example.demo.dto.signup.InviteCodeValidateResponse;
import com.example.demo.service.auth.InviteCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invite-codes")
@RequiredArgsConstructor
public class InviteCodeController {

    private final InviteCodeService inviteCodeService;

    @PostMapping("/validate")
    public InviteCodeValidateResponse validate(@Valid @RequestBody InviteCodeValidateRequest request) {
        return inviteCodeService.validate(request.getCode());
    }
}
