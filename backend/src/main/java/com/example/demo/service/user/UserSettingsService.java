package com.example.demo.service.user;

import com.example.demo.domain.UserSetting;
import com.example.demo.domain.enums.DateDisplayFormat;
import com.example.demo.domain.enums.ListViewMode;
import com.example.demo.domain.enums.ListSortMode;
import com.example.demo.domain.enums.ThemeMode;
import com.example.demo.dto.user.UpdateUserSettingsRequest;
import com.example.demo.dto.user.UserSettingsResponse;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.UserSettingRepository;
import com.example.demo.util.ApiDateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingRepository userSettingRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(Long userId) {
        return toResponse(resolveSettings(userId));
    }

    @Transactional
    public UserSettingsResponse updateSettings(Long userId, UpdateUserSettingsRequest request) {
        UserSetting setting = userSettingRepository.findByUserId(userId)
                .orElseGet(() -> createDefault(userId));

        if (request.getDateDisplayFormat() != null) {
            setting.setDateDisplayFormat(request.getDateDisplayFormat());
        }
        if (request.getAnalysisCompleteNotificationEnabled() != null) {
            setting.setAnalysisCompleteNotificationEnabled(request.getAnalysisCompleteNotificationEnabled());
        }
        if (request.getListViewMode() != null) {
            setting.setListViewMode(request.getListViewMode());
        }
        if (request.getListSortMode() != null) {
            setting.setListSortMode(request.getListSortMode());
        }
        if (request.getThemeMode() != null) {
            setting.setThemeMode(request.getThemeMode());
            syncUserDarkMode(userId, request.getThemeMode());
        }
        setting.setUpdatedAt(LocalDateTime.now());

        return toResponse(userSettingRepository.save(setting));
    }

    @Transactional(readOnly = true)
    public boolean isAnalysisNotificationEnabled(Long userId) {
        return resolveSettings(userId).isAnalysisCompleteNotificationEnabled();
    }

    @Transactional(readOnly = true)
    public ThemeMode getThemeMode(Long userId) {
        return resolveSettings(userId).getThemeMode();
    }

    private UserSetting resolveSettings(Long userId) {
        return userSettingRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultTransient(userId));
    }

    private UserSetting createDefault(Long userId) {
        UserSetting setting = createDefaultTransient(userId);
        return userSettingRepository.save(setting);
    }

    private UserSetting createDefaultTransient(Long userId) {
        UserSetting setting = new UserSetting();
        setting.setUserId(userId);
        setting.setDateDisplayFormat(DateDisplayFormat.ISO);
        setting.setAnalysisCompleteNotificationEnabled(true);
        setting.setListViewMode(ListViewMode.TABLE);
        setting.setListSortMode(ListSortMode.NEWEST);
        setting.setThemeMode(resolveInitialTheme(userId));
        setting.setUpdatedAt(LocalDateTime.now());
        return setting;
    }

    private ThemeMode resolveInitialTheme(Long userId) {
        return userRepository.findById(userId)
                .map(user -> Boolean.TRUE.equals(user.getDarkMode()) ? ThemeMode.DARK : ThemeMode.SYSTEM)
                .orElse(ThemeMode.SYSTEM);
    }

    private void syncUserDarkMode(Long userId, ThemeMode themeMode) {
        userRepository.findById(userId).ifPresent(user -> {
            if (themeMode == ThemeMode.DARK) {
                user.updateDarkMode(true);
            } else if (themeMode == ThemeMode.LIGHT) {
                user.updateDarkMode(false);
            }
        });
    }

    private UserSettingsResponse toResponse(UserSetting setting) {
        return UserSettingsResponse.builder()
                .dateDisplayFormat(setting.getDateDisplayFormat())
                .analysisCompleteNotificationEnabled(setting.isAnalysisCompleteNotificationEnabled())
                .listViewMode(setting.getListViewMode())
                .listSortMode(setting.getListSortMode() == null
                        ? ListSortMode.NEWEST
                        : setting.getListSortMode())
                .themeMode(setting.getThemeMode())
                .updatedAt(ApiDateTimeFormatter.formatUtc(setting.getUpdatedAt()))
                .build();
    }
}
