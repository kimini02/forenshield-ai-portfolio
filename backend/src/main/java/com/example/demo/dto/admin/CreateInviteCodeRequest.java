package com.example.demo.dto.admin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateInviteCodeRequest {

    private Integer expiresInDays = 30;
}
