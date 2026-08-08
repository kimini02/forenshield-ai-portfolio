package com.example.demo.dto.user;

import com.example.demo.domain.enums.DateDisplayFormat;
import com.example.demo.domain.enums.ListViewMode;
import com.example.demo.domain.enums.ListSortMode;
import com.example.demo.domain.enums.ThemeMode;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class UpdateUserSettingsRequest {

    private DateDisplayFormat dateDisplayFormat;

    private Boolean analysisCompleteNotificationEnabled;

    private ListViewMode listViewMode;

    private ListSortMode listSortMode;

    private ThemeMode themeMode;
}
