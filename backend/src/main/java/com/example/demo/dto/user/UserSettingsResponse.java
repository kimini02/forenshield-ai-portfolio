package com.example.demo.dto.user;

import com.example.demo.domain.enums.DateDisplayFormat;
import com.example.demo.domain.enums.ListViewMode;
import com.example.demo.domain.enums.ListSortMode;
import com.example.demo.domain.enums.ThemeMode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserSettingsResponse {

    private DateDisplayFormat dateDisplayFormat;
    private boolean analysisCompleteNotificationEnabled;
    private ListViewMode listViewMode;
    private ListSortMode listSortMode;
    private ThemeMode themeMode;
    private String updatedAt;
}
