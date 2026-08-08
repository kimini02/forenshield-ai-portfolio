package com.example.demo.util;

import com.example.demo.domain.enums.OrgType;
import java.util.Locale;

public final class OrganizationIdResolver {

    private OrganizationIdResolver() {
    }

    public static String resolve(OrgType organizationType) {
        if (organizationType == null) {
            return "org-unknown";
        }
        return "org-" + organizationType.name().toLowerCase(Locale.ROOT);
    }

    public static String displayName(OrgType organizationType) {
        if (organizationType == null) {
            return "기관 미지정";
        }
        return switch (organizationType) {
            case POLICE -> "경찰기관";
            case PROSECUTION -> "검찰기관";
            case NFS -> "국과수/감정기관";
            case PUBLIC_SECURITY -> "공공기관 감사/보안";
            case ETC -> "기타";
        };
    }
}
