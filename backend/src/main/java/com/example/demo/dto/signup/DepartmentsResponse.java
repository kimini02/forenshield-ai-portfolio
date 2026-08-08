package com.example.demo.dto.signup;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DepartmentsResponse {

    private List<String> departments;
}
