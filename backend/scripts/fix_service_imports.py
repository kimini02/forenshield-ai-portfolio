#!/usr/bin/env python3
"""Delete stale flat service/*.java duplicates and add cross-subpackage imports."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN_SERVICE = ROOT / "src/main/java/com/example/demo/service"
TEST_SERVICE = ROOT / "src/test/java/com/example/demo/service"

PACKAGE_MAP: dict[str, str] = {
    "AdminAnalysisStatsService": "admin",
    "AdminDashboardService": "admin",
    "AdminEvidenceService": "admin",
    "AdminInviteCodeService": "admin",
    "AdminLogService": "admin",
    "AdminProfileService": "admin",
    "AdminUserService": "admin",
    "LogCategoryMapper": "admin",
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
    "AuthService": "auth",
    "InviteCodeService": "auth",
    "RefreshTokenRedisService": "auth",
    "SignupService": "auth",
    "BlockchainAnchorService": "blockchain",
    "CompareVerificationService": "compare",
    "AnalysisCustodyLogService": "custody",
    "CocChainVerificationService": "custody",
    "CustodyLogService": "custody",
    "RecoveryScoreService": "custody",
    "ReportCustodyLogService": "custody",
    "DashboardIntroService": "dashboard",
    "DashboardStatsCache": "dashboard",
    "EvidenceStatsService": "dashboard",
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
    "EvidenceIntegrityResult": "integrity",
    "IntegrityVerificationService": "integrity",
    "EvidenceManifestService": "manifest",
    "ManifestSignatureService": "manifest",
    "ManifestSigningKeyLoader": "manifest",
    "ManifestSigningKeyMaterial": "manifest",
    "Pkcs8ManifestSignatureService": "manifest",
    "NotificationService": "notification",
    "ReportPdfService": "report",
    "MyPageService": "user",
    "OrganizationService": "user",
    "UserService": "user",
    "UserSettingsService": "user",
}


def delete_flat_duplicates(base: Path, mapping: dict[str, str]) -> int:
    removed = 0
    for stem in mapping:
        flat = base / f"{stem}.java"
        if flat.is_file():
            flat.unlink()
            removed += 1
    # test files
    test_map = {k + "Test": v for k, v in mapping.items() if (base.parent / "test").exists()}
    return removed


def delete_main_flat_duplicates() -> int:
    removed = 0
    for java_file in MAIN_SERVICE.glob("*.java"):
        java_file.unlink()
        removed += 1
    for java_file in TEST_SERVICE.glob("*.java"):
        java_file.unlink()
        removed += 1
    return removed


def fix_cross_package_imports(java_file: Path) -> bool:
    content = java_file.read_text(encoding="utf-8")
    package_match = re.search(r"^package\s+([\w.]+);", content, re.MULTILINE)
    if not package_match:
        return False
    current_package = package_match.group(1)

    imports_to_add: list[str] = []
    for class_name, sub in PACKAGE_MAP.items():
        target_package = f"com.example.demo.service.{sub}"
        if current_package == target_package:
            continue
        full_import = f"{target_package}.{class_name}"
        if f"import {full_import};" in content:
            continue
        if re.search(r"\b" + re.escape(class_name) + r"\b", content) is None:
            continue
        imports_to_add.append(full_import)

    if not imports_to_add:
        return False

    imports_to_add = sorted(set(imports_to_add))
    import_block = "\n".join(f"import {imp};" for imp in imports_to_add)

    if re.search(r"^import\s+", content, re.MULTILINE):
        # insert before first import
        content = re.sub(
            r"(^import\s+)",
            import_block + "\n\\1",
            content,
            count=1,
            flags=re.MULTILINE,
        )
    else:
        # insert after package
        content = re.sub(
            r"(^package\s+[\w.]+;\s*\n)",
            "\\1\n" + import_block + "\n",
            content,
            count=1,
            flags=re.MULTILINE,
        )

    java_file.write_text(content, encoding="utf-8")
    return True


def main() -> None:
    removed = delete_main_flat_duplicates()
    print(f"Removed {removed} duplicate flat files")
    fixed = 0
    for base in (MAIN_SERVICE, TEST_SERVICE):
        if not base.exists():
            continue
        for java_file in base.rglob("*.java"):
            if fix_cross_package_imports(java_file):
                fixed += 1
    print(f"Fixed imports in {fixed} files")


if __name__ == "__main__":
    main()
