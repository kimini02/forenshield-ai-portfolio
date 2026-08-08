package com.example.demo.controller;

import com.example.demo.dto.mypage.AnalysisHistoryPageResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.user.MyPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MyPage", description = "마이페이지 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MyPageController {

	private final MyPageService myPageService;
	private final AuthUserResolver authUserResolver;

	@Operation(summary = "내 분석 기록 목록", description = "로그인 사용자의 사건별 분석 기록을 조회합니다.")
	@GetMapping(value = {"/mypage/analysis-history", "/cases/me"}, produces = "application/json;charset=UTF-8")
	public AnalysisHistoryPageResponse getAnalysisHistory(
			@RequestParam(defaultValue = "newest") String sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String q
	) {
		return myPageService.getAnalysisHistory(
				authUserResolver.requireCurrentUser(),
				sort,
				page,
				size,
				status,
				q
		);
	}
}
