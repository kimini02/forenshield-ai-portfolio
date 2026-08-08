package com.example.demo.domain;

import com.example.demo.domain.enums.DateDisplayFormat;
import com.example.demo.domain.enums.ListViewMode;
import com.example.demo.domain.enums.ListSortMode;
import com.example.demo.domain.enums.ThemeMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
public class UserSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "setting_id")
    private Long settingId;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_display_format", nullable = false, length = 10)
    private DateDisplayFormat dateDisplayFormat = DateDisplayFormat.ISO;

    @Column(name = "analysis_complete_notification_enabled", nullable = false)
    private boolean analysisCompleteNotificationEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "list_view_mode", nullable = false, length = 10)
    private ListViewMode listViewMode = ListViewMode.TABLE;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "list_sort_mode",
            nullable = false,
            length = 20,
            columnDefinition = "varchar(20) default 'NEWEST'"
    )
    private ListSortMode listSortMode = ListSortMode.NEWEST;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", nullable = false, length = 10)
    private ThemeMode themeMode = ThemeMode.SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
