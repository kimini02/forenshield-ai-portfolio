package com.example.demo.controller;

import com.example.demo.domain.enums.OrgType;
import com.example.demo.dto.signup.DepartmentsResponse;
import com.example.demo.service.user.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping("/departments")
    public DepartmentsResponse departments(@RequestParam OrgType organizationType) {
        return DepartmentsResponse.builder()
                .departments(organizationService.findDepartments(organizationType))
                .build();
    }
}
