#!/usr/bin/env python3
"""Move flat service/*.java into role-based subpackages and fix imports."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN_SERVICE = ROOT / "src/main/java/com/example/demo/service"
TEST_SERVICE = ROOT / "src/test/java/com/example/demo/service"

# class/file stem -> subpackage
PACKAGE_MAP: dict[str, str] = {
    # admin
    "AdminAnalysisStatsService": "admin",
    "AdminDashboardService": "admin",
    "AdminEvidenceService": "admin",
    "AdminInviteCodeService": "admin",
    "AdminLogService": "admin",
    "AdminProfileService": "admin",
    "AdminUserService": "admin",
    "LogCategoryMapper": "admin",
    # analysis
    "AnalysisCancelService": "analysis",
    "AnalysisDetailFormatters": "analysis",
    "AnalysisInfoAssembler": "analysis",
    "AnalysisJobEnqueuer": "analysis",
    "AnalysisJobMessageFactory": "analysis",
    "AnalysisQueueMetricsResolver": "analysis",
    "AnalysisResponseResolver": "analysis",
    "AnalysisResultPersistenceService": "analysis",
    "AnalysisService": "analysis",
    "AnalysisStatusService": "analysis",
    "AnalysisWorkerService": "analysis",
    "S3AnalysisAccessService": "analysis",
    "SuspiciousSegmentCalculator": "analysis",
    "VideoAnalysisDetailsBuilder": "analysis",
    "VideoAnalysisModuleWriter": "analysis",
    "VideoFrameExtractionService": "analysis",
    "VideoModuleDetailsReader": "analysis",
    # auth
    "AuthService": "auth",
    "InviteCodeService": "auth",
    "RefreshTokenRedisService": "auth",
    "SignupService": "auth",
    # blockchain
    "BlockchainAnchorService": "blockchain",
    # compare
    "CompareVerificationService": "compare",
    # custody (CoC · integrity audit)
    "AnalysisCustodyLogService": "custody",
    "CocChainVerificationService": "custody",
    "CustodyLogService": "custody",
    "RecoveryScoreService": "custody",
    "ReportCustodyLogService": "custody",
    # dashboard
    "DashboardIntroService": "dashboard",
    "DashboardStatsCache": "dashboard",
    "EvidenceStatsService": "dashboard",
    # evidence
    "EvidenceAccessService": "evidence",
    "EvidenceCancelService": "evidence",
    "EvidenceCopyService": "evidence",
    "EvidenceDetailService": "evidence",
    "EvidenceMetadataService": "evidence",
    "EvidenceStoragePaths": "evidence",
    "FileService": "evidence",
    "FileValidationService": "evidence",
    "HashService": "evidence",
    "MediaService": "evidence",
    # integrity (verification facade)
    "EvidenceIntegrityResult": "integrity",
    "IntegrityVerificationService": "integrity",
    # manifest
    "EvidenceManifestService": "manifest",
    "ManifestSignatureService": "manifest",
    "ManifestSigningKeyLoader": "manifest",
    "ManifestSigningKeyMaterial": "manifest",
    "Pkcs8ManifestSignatureService": "manifest",
    # notification
    "NotificationService": "notification",
    # report
    "ReportPdfService": "report",
    # user
    "MyPageService": "user",
    "OrganizationService": "user",
    "UserService": "user",
    "UserSettingsService": "user",
}

TEST_MAP: dict[str, str] = {
    "AnalysisAiResultIntegrationTest": "analysis",
    "AnalysisJobMessageFactoryTest": "analysis",
    "AnalysisStatusServiceTest": "analysis",
    "AnalysisWorkerServiceTest": "analysis",
    "S3AnalysisAccessServiceTest": "analysis",
    "SuspiciousSegmentCalculatorTest": "analysis",
    "VideoFrameExtractionServiceTest": "analysis",
    "BlockchainAnchorServiceTest": "blockchain",
    "CustodyLogServiceTest": "custody",
    "RecoveryScoreServiceTest": "custody",
    "EvidenceManifestServiceTest": "manifest",
    "Pkcs8ManifestSignatureServiceTest": "manifest",
    "IntegrityVerificationServiceTest": "integrity",
    "FileValidationServiceTest": "evidence",
    "HashServiceTest": "evidence",
    "MediaServiceManualTest": "evidence",
}


def move_java_files(base_dir: Path, mapping: dict[str, str], is_test: bool) -> list[Path]:
    moved: list[Path] = []
    if not base_dir.exists():
        return moved
    for java_file in list(base_dir.glob("*.java")):
        stem = java_file.stem
        sub = mapping.get(stem)
        if sub is None:
            continue
        target_dir = base_dir / sub
        target_dir.mkdir(parents=True, exist_ok=True)
        target = target_dir / java_file.name
        content = java_file.read_text(encoding="utf-8")
        if is_test:
            new_package = f"com.example.demo.service.{sub}"
        else:
            new_package = f"com.example.demo.service.{sub}"
        content = re.sub(
            r"^package com\.example\.demo\.service(?:\.[\w.]+)?;",
            f"package {new_package};",
            content,
            count=1,
            flags=re.MULTILINE,
        )
        target.write_text(content, encoding="utf-8")
        java_file.unlink()
        moved.append(target)
    return moved


def rewrite_imports(java_roots: list[Path]) -> None:
    # Longest names first to avoid partial replacements
    for class_name in sorted(PACKAGE_MAP.keys(), key=len, reverse=True):
        sub = PACKAGE_MAP[class_name]
        old = f"com.example.demo.service.{class_name}"
        new = f"com.example.demo.service.{sub}.{class_name}"
        for root in java_roots:
            for path in root.rglob("*.java"):
                text = path.read_text(encoding="utf-8")
                if old not in text:
                    continue
                updated = text.replace(old, new)
                if updated != text:
                    path.write_text(updated, encoding="utf-8")


def main() -> None:
    moved_main = move_java_files(MAIN_SERVICE, PACKAGE_MAP, is_test=False)
    moved_test = move_java_files(TEST_SERVICE, TEST_MAP, is_test=True)
    java_roots = [
        ROOT / "src/main/java",
        ROOT / "src/test/java",
    ]
    rewrite_imports(java_roots)
    print(f"Moved {len(moved_main)} main service classes")
    print(f"Moved {len(moved_test)} test classes")
    remaining = list(MAIN_SERVICE.glob("*.java"))
    if remaining:
        print("WARNING: unmoved main service files:", [p.name for p in remaining])


if __name__ == "__main__":
    main()
