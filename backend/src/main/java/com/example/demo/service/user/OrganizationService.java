package com.example.demo.service.user;

import com.example.demo.domain.enums.OrgType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OrganizationService {

    private static final Map<OrgType, List<String>> DEPARTMENTS = Map.of(
            OrgType.POLICE, List.of(
                    "서울경찰청 사이버수사과",
                    "경기남부경찰청 사이버수사팀",
                    "부산경찰청 사이버수사대"
            ),
            OrgType.PROSECUTION, List.of(
                    "대검찰청 사이버수사과",
                    "서울중앙지검 디지털포렌식팀"
            ),
            OrgType.NFS, List.of(
                    "국립과학수사연구원 디지털과",
                    "국립과학수사연구원 영상분석실"
            ),
            OrgType.PUBLIC_SECURITY, List.of(
                    "공공안전기관 디지털증거분석팀"
            ),
            OrgType.ETC, List.of(
                    "기타 기관"
            )
    );

    public List<String> findDepartments(OrgType organizationType) {
        return DEPARTMENTS.getOrDefault(organizationType, List.of());
    }
}
