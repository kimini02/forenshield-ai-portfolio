"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
    CheckCircle2,
    ChevronDown,
    Loader2,
    Search,
    SlidersHorizontal,
    UserRoundCheck,
    X,
} from "lucide-react";
import { AdminPageHeader } from "@/app/admin/_components/admin-page-header";
import { useAdminToast } from "@/app/admin/_components/admin-toast-provider";
import type { CaseSummary } from "@/app/mypage/_types/case";
import { ORG_TYPES, type OrgType } from "@/app/signup/organizationData";
import { Button } from "@/components/ui/button";
import {
    assignAdminCaseReviewer,
    fetchAdminReviewers,
    type AdminReviewer,
} from "@/lib/api/admin";
import { fetchMyAnalysisHistory } from "@/lib/api/mypage";
import { getApiErrorMessage } from "@/lib/api/errors";
import { cn } from "@/lib/utils";
import { mockUsers } from "@/lib/permissions";
import { fetchDepartments } from "@/lib/signup-api";

const REQUESTED_REVIEW_STATUS = "REVIEW_REQUESTED" as const;
const ASSIGNED_REVIEW_STATUS = "REVIEW_ASSIGNED" as const;
const SUPPLEMENT_REQUESTED_REVIEW_STATUSES = [
    "REVIEW_SUPPLEMENT_REQUESTED",
    "SUPPLEMENT_REQUESTED",
    "REVIEW_REVISION_REQUESTED",
    "REVISION_REQUESTED",
    "REVIEW_NEEDS_CHANGES",
] as const;
const COMPLETED_REVIEW_STATUSES = [
    "REVIEW_COMPLETED",
    "REPORT_APPROVED",
] as const;
const ACTIVE_REVIEW_STATUSES = [
    REQUESTED_REVIEW_STATUS,
    ASSIGNED_REVIEW_STATUS,
] as const;
const REVIEW_QUEUE_STATUSES = [
    ...ACTIVE_REVIEW_STATUSES,
    ...SUPPLEMENT_REQUESTED_REVIEW_STATUSES,
    ...COMPLETED_REVIEW_STATUSES,
] as const;

const FETCH_SIZE = 100;
const CASES_PER_PAGE = 10;

type QueueTab = "REQUESTED" | "ASSIGNED" | "SUPPLEMENT_REQUESTED" | "COMPLETED";
type RiskFilter = "ALL" | "HIGH" | "NORMAL";
type SortMode = "DELAYED" | "REQUESTED_DESC" | "RISK_DESC";
type OrganizationFilter = OrgType | "ALL";
type ReviewerOption = AdminReviewer;

type QueueFilters = {
    organizationType: OrganizationFilter;
    department: string;
    risk: RiskFilter;
    delayedOnly: boolean;
};

const defaultFilters: QueueFilters = {
    organizationType: "ALL",
    department: "ALL",
    risk: "ALL",
    delayedOnly: false,
};

const sortLabels: Record<SortMode, string> = {
    DELAYED: "지연순",
    REQUESTED_DESC: "요청 최신순",
    RISK_DESC: "위험도순",
};

type ReviewerStat = ReviewerOption & {
    assignedCount: number;
    delayedCount: number;
};

const orgTypeLabelMap = new Map<string, string>(
    ORG_TYPES.map((organization) => [organization.value, organization.label]),
);

function uniqueSortedDepartments(departments: string[]) {
    return Array.from(
        new Set(
            departments
                .map((department) => department.trim())
                .filter(Boolean),
        ),
    ).sort((first, second) => first.localeCompare(second, "ko"));
}

function formatCompactDateTime(value?: string | null) {
    if (!value) return "-";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    const hour = String(date.getHours()).padStart(2, "0");
    const minute = String(date.getMinutes()).padStart(2, "0");
    return `${month}.${day} ${hour}:${minute}`;
}

function normalizeRiskScore(score?: number | null) {
    if (score == null) return null;
    return Math.round(score <= 1 ? score * 100 : score);
}

function getRequestAgeHours(value?: string | null) {
    if (!value) return 0;
    const requestedAt = new Date(value).getTime();
    if (Number.isNaN(requestedAt)) return 0;
    return Math.max(0, Math.floor((Date.now() - requestedAt) / (1000 * 60 * 60)));
}

function hasReviewStatus(caseItem: CaseSummary, statuses: readonly string[]) {
    return statuses.includes(String(caseItem.reviewStatus ?? "NONE"));
}

function isPendingReviewAssignment(caseItem: CaseSummary) {
    return (
        (!caseItem.reviewStatus || caseItem.reviewStatus === "NONE")
    );
}

function isCompletedReview(caseItem: CaseSummary) {
    return hasReviewStatus(caseItem, COMPLETED_REVIEW_STATUSES);
}

function isSupplementRequestedReview(caseItem: CaseSummary) {
    return hasReviewStatus(caseItem, SUPPLEMENT_REQUESTED_REVIEW_STATUSES);
}

function isActiveReview(caseItem: CaseSummary) {
    return hasReviewStatus(caseItem, ACTIVE_REVIEW_STATUSES);
}

function isDelayedReview(caseItem: CaseSummary) {
    return (
        isActiveReview(caseItem) &&
        getRequestAgeHours(caseItem.reviewRequestedAt) >= 24
    );
}

function getRiskLevel(caseItem: CaseSummary): "HIGH" | "NORMAL" {
    const score = normalizeRiskScore(caseItem.riskScore);
    if (score != null) return score >= 70 ? "HIGH" : "NORMAL";
    return caseItem.aiResult === "위험" ? "HIGH" : "NORMAL";
}

function getRiskText(caseItem: CaseSummary) {
    const score = normalizeRiskScore(caseItem.riskScore);
    if (score != null) return `${score}점`;
    return caseItem.aiResult ?? "-";
}

function reviewerName(reviewerId?: string | null, reviewerOptions: readonly ReviewerOption[] = []) {
    if (!reviewerId) return "미배정";
    return (
        reviewerOptions.find((reviewer) => reviewer.id === reviewerId)?.name ?? reviewerId
    );
}

const organizationPrefixPatterns = [
    /^(.*?(?:경찰청|검찰청|과학수사연구원|공공안전기관|감정기관|보안기관|연구원|기관|청))\s+/,
];

function normalizeText(value?: string | null) {
    const text = value?.trim();
    return text && text !== "기관 미지정" ? text : null;
}

function inferOrganizationNameFromDepartment(department?: string | null) {
    const text = normalizeText(department);
    if (!text) return null;

    for (const pattern of organizationPrefixPatterns) {
        const match = text.match(pattern);
        if (match?.[1]) return match[1].trim();
    }

    return null;
}

function getOrganizationName(caseItem: CaseSummary) {
    const explicitName = normalizeText(caseItem.organizationName);
    if (explicitName) return explicitName;

    const organizationId = normalizeText(caseItem.organizationId);
    if (organizationId) {
        const mockName = mockUsers.find((user) => user.organizationId === organizationId)?.organizationName;
        if (mockName) return mockName;

        if (orgTypeLabelMap.has(organizationId.toUpperCase())) {
            return orgTypeLabelMap.get(organizationId.toUpperCase()) ?? null;
        }
    }

    return inferOrganizationNameFromDepartment(caseItem.department);
}

function getOrganizationTypeLabel(organizationType: OrganizationFilter) {
    if (organizationType === "ALL") return "전체 기관";
    return orgTypeLabelMap.get(organizationType) ?? "기관 미지정";
}

function getCaseOrganizationType(caseItem: CaseSummary): OrgType {
    const explicitType = normalizeText(caseItem.organizationType)?.toUpperCase();
    if (explicitType && orgTypeLabelMap.has(explicitType)) return explicitType as OrgType;

    const normalizedId = (caseItem.organizationId ?? "").trim().toUpperCase();
    if (orgTypeLabelMap.has(normalizedId)) return normalizedId as OrgType;

    const organizationName = getOrganizationName(caseItem);
    const scope = `${caseItem.organizationId ?? ""} ${organizationName} ${
        caseItem.department ?? ""
    }`.toLowerCase();

    if (scope.includes("검찰") || scope.includes("prosecution")) return "PROSECUTION";
    if (scope.includes("국과수") || scope.includes("감정") || scope.includes("nfs")) return "NFS";
    if (
        scope.includes("공공") ||
        scope.includes("감사") ||
        scope.includes("보안") ||
        scope.includes("public") ||
        scope.includes("security")
    ) {
        return "PUBLIC_SECURITY";
    }
    if (
        scope.includes("경찰") ||
        scope.includes("police") ||
        scope.includes("수사") ||
        scope.includes("청")
    ) {
        return "POLICE";
    }

    return "ETC";
}

function isReviewerInCaseScope(reviewer: ReviewerOption, caseItem: CaseSummary) {
    const caseDepartment = normalizeText(caseItem.department)?.toLowerCase();
    const reviewerDepartment = normalizeText(reviewer.department)?.toLowerCase();
    if (!caseDepartment || caseDepartment !== reviewerDepartment) return false;

    const caseOrganizationId = normalizeText(caseItem.organizationId)?.toLowerCase();
    const reviewerOrganizationId = normalizeText(reviewer.organizationId)?.toLowerCase();
    if (caseOrganizationId && reviewerOrganizationId) {
        return caseOrganizationId === reviewerOrganizationId;
    }

    const reviewerOrganizationType = normalizeText(reviewer.organizationType)?.toUpperCase();
    return !reviewerOrganizationType || reviewerOrganizationType === getCaseOrganizationType(caseItem);
}

function getScopeLabel(caseItem: CaseSummary) {
    const organizationName = getOrganizationName(caseItem);
    const rawDepartment = normalizeText(caseItem.department);
    const department =
        rawDepartment && organizationName && rawDepartment.startsWith(organizationName)
            ? normalizeText(rawDepartment.slice(organizationName.length))
            : rawDepartment;

    if (organizationName && department) return `${organizationName} · ${department}`;
    return organizationName ?? department ?? "소속 미지정";
}

function riskTextClass(caseItem: CaseSummary) {
    const score = normalizeRiskScore(caseItem.riskScore);
    if (score != null && score >= 70) return "font-bold text-red-600";
    if (score != null && score < 50) return "text-slate-500";
    if (caseItem.aiResult === "위험") return "font-bold text-red-600";
    return "font-semibold text-slate-800";
}

export default function AdminReviewAssignmentPage() {
    const [cases, setCases] = useState<CaseSummary[]>([]);
    const [activeTab, setActiveTab] = useState<QueueTab>("REQUESTED");
    const [filters, setFilters] = useState<QueueFilters>(defaultFilters);
    const [showFilters, setShowFilters] = useState(false);
    const [sortMode, setSortMode] = useState<SortMode>("DELAYED");
    const [query, setQuery] = useState("");
    const [casePage, setCasePage] = useState(1);
    const [reviewers, setReviewers] = useState<ReviewerOption[]>([]);
    const [assignmentCase, setAssignmentCase] = useState<CaseSummary | null>(
        null,
    );
    const [assignmentReviewerOptions, setAssignmentReviewerOptions] = useState<
        ReviewerOption[] | null
    >(null);
    const [assignmentReviewersLoading, setAssignmentReviewersLoading] =
        useState(false);
    const [selectedReviewerId, setSelectedReviewerId] = useState("");
    const [loading, setLoading] = useState(true);
    const [signupDepartments, setSignupDepartments] = useState<string[]>([]);
    const [processingCaseId, setProcessingCaseId] = useState<string | null>(null);
    const { toast } = useAdminToast();

    const loadCases = useCallback(async () => {
        setLoading(true);
        try {
            const response = await fetchMyAnalysisHistory({
                page: 0,
                size: FETCH_SIZE,
            });
            setCases(response.content);
        } catch (error) {
            const message = getApiErrorMessage(
                error,
                "검토 배정 목록을 불러오지 못했습니다.",
            );
            toast({ title: "조회 실패", description: message });
        } finally {
            setLoading(false);
        }
    }, [toast]);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            void loadCases();
        }, 0);

        return () => window.clearTimeout(timer);
    }, [loadCases]);

    useEffect(() => {
        let cancelled = false;

        fetchAdminReviewers()
            .then((items) => {
                if (!cancelled) setReviewers(items);
            })
            .catch(() => {
                if (!cancelled) setReviewers([]);
            });

        return () => {
            cancelled = true;
        };
    }, []);

    useEffect(() => {
        if (!assignmentCase) {
            setAssignmentReviewerOptions(null);
            setAssignmentReviewersLoading(false);
            return;
        }

        let cancelled = false;
        setAssignmentReviewersLoading(true);
        fetchAdminReviewers({
            uploaderId: assignmentCase.createdBy,
            department: assignmentCase.department,
        })
            .then((items) => {
                if (!cancelled) setAssignmentReviewerOptions(items);
            })
            .catch(() => {
                if (!cancelled) setAssignmentReviewerOptions([]);
            })
            .finally(() => {
                if (!cancelled) setAssignmentReviewersLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, [assignmentCase]);

    const reviewCases = useMemo(
        () =>
            cases.filter(
                (item) =>
                    hasReviewStatus(item, REVIEW_QUEUE_STATUSES) ||
                    isPendingReviewAssignment(item),
            ),
        [cases],
    );

    useEffect(() => {
        let cancelled = false;
        const organizationTypes =
            filters.organizationType === "ALL"
                ? ORG_TYPES.map((organization) => organization.value)
                : [filters.organizationType];

        Promise.all(organizationTypes.map((organizationType) => fetchDepartments(organizationType)))
            .then((responses) => {
                if (cancelled) return;
                setSignupDepartments(
                    uniqueSortedDepartments([
                        ...responses.flatMap((response) => response.departments),
                        "기타",
                    ]),
                );
            })
            .catch(() => {
                if (!cancelled) setSignupDepartments([]);
            });

        return () => {
            cancelled = true;
        };
    }, [filters.organizationType]);

    const fallbackDepartmentOptions = useMemo(() => {
        const departments: string[] = [];

        reviewCases.forEach((caseItem) => {
            if (
                filters.organizationType !== "ALL" &&
                getCaseOrganizationType(caseItem) !== filters.organizationType
            ) {
                return;
            }

            if (caseItem.department) departments.push(caseItem.department);
        });

        return uniqueSortedDepartments(departments);
    }, [filters.organizationType, reviewCases]);

    const departmentOptions = useMemo(
        () =>
            signupDepartments.length > 0
                ? signupDepartments
                : fallbackDepartmentOptions,
        [fallbackDepartmentOptions, signupDepartments],
    );

    const scopedReviewCases = useMemo(
        () =>
            reviewCases
                .filter(
                    (caseItem) =>
                        filters.organizationType === "ALL" ||
                        getCaseOrganizationType(caseItem) === filters.organizationType,
                )
                .filter(
                    (caseItem) =>
                        filters.department === "ALL" || caseItem.department === filters.department,
                ),
        [filters.department, filters.organizationType, reviewCases],
    );

    const requestedCases = useMemo(
        () =>
            scopedReviewCases.filter((caseItem) =>
                hasReviewStatus(caseItem, [REQUESTED_REVIEW_STATUS]) ||
                isPendingReviewAssignment(caseItem),
            ),
        [scopedReviewCases],
    );
    const assignedCases = useMemo(
        () =>
            scopedReviewCases.filter((caseItem) =>
                hasReviewStatus(caseItem, [ASSIGNED_REVIEW_STATUS]),
            ),
        [scopedReviewCases],
    );
    const supplementRequestedCases = useMemo(
        () => scopedReviewCases.filter(isSupplementRequestedReview),
        [scopedReviewCases],
    );
    const activeCases = useMemo(
        () => scopedReviewCases.filter(isActiveReview),
        [scopedReviewCases],
    );
    const completedCases = useMemo(
        () => scopedReviewCases.filter(isCompletedReview),
        [scopedReviewCases],
    );
    const baseCases =
        activeTab === "REQUESTED"
            ? requestedCases
            : activeTab === "ASSIGNED"
                ? assignedCases
                : activeTab === "SUPPLEMENT_REQUESTED"
                    ? supplementRequestedCases
                    : completedCases;

    const tabOptions = useMemo(
        () => [
            {
                value: "REQUESTED" as const,
                label: "배정대기",
                count: requestedCases.length,
            },
            {
                value: "ASSIGNED" as const,
                label: "검토중",
                count: assignedCases.length,
            },
            {
                value: "SUPPLEMENT_REQUESTED" as const,
                label: "재검토",
                count: supplementRequestedCases.length,
            },
            {
                value: "COMPLETED" as const,
                label: "승인",
                count: completedCases.length,
            },
        ],
        [
            assignedCases.length,
            completedCases.length,
            requestedCases.length,
            supplementRequestedCases.length,
        ],
    );
    const activeTabIndex = Math.max(
        0,
        tabOptions.findIndex((option) => option.value === activeTab),
    );

    const reviewerStats = useMemo<ReviewerStat[]>(
        () =>
            reviewers.map((reviewer) => {
                const assignedCases = activeCases.filter(
                    (caseItem) =>
                        caseItem.reviewerId === reviewer.id &&
                        caseItem.reviewStatus === "REVIEW_ASSIGNED",
                );

                return {
                    ...reviewer,
                    assignedCount: assignedCases.length,
                    delayedCount: assignedCases.filter(isDelayedReview).length,
                };
            }),
        [activeCases, reviewers],
    );

    const reviewerStatsById = useMemo(
        () => new Map(reviewerStats.map((reviewer) => [reviewer.id, reviewer])),
        [reviewerStats],
    );

    const queueStats = useMemo(
        () => ({
            requested: requestedCases.length,
            assigned: assignedCases.length,
            supplementRequested: supplementRequestedCases.length,
            delayed: activeCases.filter(isDelayedReview).length,
            completed: completedCases.length,
        }),
        [
            activeCases,
            assignedCases.length,
            completedCases.length,
            requestedCases.length,
            supplementRequestedCases.length,
        ],
    );

    const scopeLabel = useMemo(() => {
        const organizationLabel = getOrganizationTypeLabel(filters.organizationType);

        if (filters.department === "ALL") return organizationLabel;
        return `${organizationLabel} · ${filters.department}`;
    }, [filters.department, filters.organizationType]);

    const filteredCases = useMemo(() => {
        const normalizedQuery = query.trim().toLowerCase();

        return baseCases
            .filter(
                (item) => filters.risk === "ALL" || getRiskLevel(item) === filters.risk,
            )
            .filter(
                (item) =>
                    activeTab === "COMPLETED" ||
                    activeTab === "SUPPLEMENT_REQUESTED" ||
                    !filters.delayedOnly ||
                    isDelayedReview(item),
            )
            .filter((item) => {
                if (!normalizedQuery) return true;
                return [
                    item.caseName,
                    item.caseId,
                    getScopeLabel(item),
                    item.department ?? "",
                    item.representativeFileName ?? "",
                    reviewerName(item.reviewerId, reviewers),
                ]
                    .join(" ")
                    .toLowerCase()
                    .includes(normalizedQuery);
            })
            .sort((first, second) => {
                if (sortMode === "RISK_DESC") {
                    return (
                        (normalizeRiskScore(second.riskScore) ?? 0) -
                        (normalizeRiskScore(first.riskScore) ?? 0)
                    );
                }

                if (sortMode === "REQUESTED_DESC") {
                    return (
                        new Date(second.reviewRequestedAt ?? second.createdAt).getTime() -
                        new Date(first.reviewRequestedAt ?? first.createdAt).getTime()
                    );
                }

                const delayedGap =
                    Number(isDelayedReview(second)) - Number(isDelayedReview(first));
                if (delayedGap !== 0) return delayedGap;
                return (
                    getRequestAgeHours(second.reviewRequestedAt) -
                    getRequestAgeHours(first.reviewRequestedAt)
                );
            });
    }, [activeTab, baseCases, filters, query, reviewers, sortMode]);

    const totalCaseCount = filteredCases.length;
    const totalCasePages = Math.max(
        1,
        Math.ceil(totalCaseCount / CASES_PER_PAGE),
    );
    const currentCasePage = Math.min(casePage, totalCasePages);
    const paginatedCases = useMemo(() => {
        const startIndex = (currentCasePage - 1) * CASES_PER_PAGE;
        return filteredCases.slice(startIndex, startIndex + CASES_PER_PAGE);
    }, [currentCasePage, filteredCases]);

    const assignmentReviewers = useMemo(() => {
        if (!assignmentCase || assignmentReviewerOptions === null) return [];

        return assignmentReviewerOptions
            .filter((reviewer) => isReviewerInCaseScope(reviewer, assignmentCase))
            .map((reviewer) => {
                const stats = reviewerStatsById.get(reviewer.id);
                return {
                    ...reviewer,
                    assignedCount: stats?.assignedCount ?? 0,
                    delayedCount: stats?.delayedCount ?? 0,
                };
            })
            .sort((first, second) => {
                const currentReviewerGap =
                    Number(second.id === assignmentCase.reviewerId) -
                    Number(first.id === assignmentCase.reviewerId);
                if (currentReviewerGap !== 0) return currentReviewerGap;

                const delayedGap = first.delayedCount - second.delayedCount;
                if (delayedGap !== 0) return delayedGap;

                const assignedGap = first.assignedCount - second.assignedCount;
                if (assignedGap !== 0) return assignedGap;

                return first.name.localeCompare(second.name, "ko");
            });
    }, [assignmentCase, assignmentReviewerOptions, reviewerStatsById]);

    const selectedReviewer = assignmentReviewers.find(
        (reviewer) => reviewer.id === selectedReviewerId,
    );
    const processing = Boolean(
        assignmentCase && processingCaseId === assignmentCase.caseId,
    );
    const isReassignment = Boolean(assignmentCase?.reviewerId);
    const selectedIsCurrentReviewer =
        Boolean(assignmentCase?.reviewerId) &&
        assignmentCase?.reviewerId === selectedReviewerId;
    const canSubmitAssignment =
        Boolean(assignmentCase) &&
        Boolean(selectedReviewer) &&
        !selectedIsCurrentReviewer &&
        !assignmentReviewersLoading &&
        !processing;

    const assignmentButtonLabel = (() => {
        if (!assignmentCase) return "담당자 선택";
        if (assignmentReviewersLoading) return "검토자 목록을 불러오는 중";
        if (!selectedReviewer)
            return isReassignment
                ? "변경할 담당자를 선택하세요"
                : "담당자를 선택하세요";
        return isReassignment
            ? `${selectedReviewer.name}로 변경`
            : `${selectedReviewer.name}에게 배정`;
    })();

    useEffect(() => {
        setCasePage(1);
    }, [activeTab, filters, query, sortMode]);

    useEffect(() => {
        if (casePage > totalCasePages) setCasePage(totalCasePages);
    }, [casePage, totalCasePages]);

    function resetFilters() {
        setFilters(defaultFilters);
        setShowFilters(false);
    }

    function openAssignmentModal(caseItem: CaseSummary) {
        setAssignmentCase(caseItem);
        setAssignmentReviewerOptions(null);
        setAssignmentReviewersLoading(true);
        setSelectedReviewerId("");
        setShowFilters(false);
    }

    function closeAssignmentModal() {
        if (processing) return;
        setAssignmentCase(null);
        setAssignmentReviewerOptions(null);
        setAssignmentReviewersLoading(false);
        setSelectedReviewerId("");
    }

    async function handleAssignSelectedCase() {
        if (!assignmentCase || !selectedReviewer) return;

        setProcessingCaseId(assignmentCase.caseId);
        try {
            await assignAdminCaseReviewer(
                assignmentCase.caseId,
                selectedReviewer.id,
                assignmentCase.createdBy,
            );
            toast({
                title: isReassignment ? "담당자 변경 완료" : "담당자 배정 완료",
                description: `${assignmentCase.caseName} 사건이 ${selectedReviewer.name} 검토자에게 ${
                    isReassignment ? "변경" : "배정"
                }되었습니다.`,
            });
            closeAssignmentModal();
            await loadCases();
        } catch (error) {
            const message = getApiErrorMessage(
                error,
                "담당자 배정 중 오류가 발생했습니다.",
            );
            toast({ title: "배정 실패", description: message });
        } finally {
            setProcessingCaseId(null);
        }
    }

    return (
        <>
            <AdminPageHeader
                title="검토 배정"
                description="미배정 또는 검토 중인 사건의 담당 검토자를 지정합니다."
            />

            <div className="space-y-4 px-8 py-5">
                <section className="rounded-lg border border-slate-200 bg-white px-5 py-3 shadow-sm">
                    <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
                        <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-slate-700">
                            <span>배정대기 {queueStats.requested}건</span>
                            <span className="h-3 w-px bg-slate-200" />
                            <span>검토중 {queueStats.assigned}건</span>
                            <span className="h-3 w-px bg-slate-200" />
                            <span>재검토 {queueStats.supplementRequested}건</span>
                            <span className="h-3 w-px bg-slate-200" />
                            <span>승인 {queueStats.completed}건</span>
                            <span className="h-3 w-px bg-slate-200" />
                            <span
                                className={
                                    queueStats.delayed > 0
                                        ? "font-semibold text-red-600"
                                        : undefined
                                }
                            >
                지연 위험 {queueStats.delayed}건
              </span>
                        </div>
                        <p className="text-sm text-slate-500">현재 보기: {scopeLabel}</p>
                    </div>
                </section>

                <section className="relative overflow-visible rounded-lg border border-slate-200 bg-white shadow-sm">
                    <div className="relative z-30 border-b border-slate-100 bg-white px-5 py-4">
                        <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                            <div className="relative grid w-full max-w-[520px] grid-cols-4 rounded-lg border border-slate-200 bg-slate-50 p-1">
                <span
                    aria-hidden="true"
                    className="absolute bottom-1 left-1 top-1 rounded-md bg-white shadow-sm transition-transform duration-200 ease-out"
                    style={{
                        width: "calc((100% - 0.5rem) / 4)",
                        transform: `translateX(${activeTabIndex * 100}%)`,
                    }}
                />
                                {tabOptions.map((option) => (
                                    <button
                                        key={option.value}
                                        type="button"
                                        onClick={() => setActiveTab(option.value)}
                                        className={cn(
                                            "relative z-10 rounded-md px-3 py-1.5 text-sm font-semibold transition-colors duration-200",
                                            activeTab === option.value
                                                ? "text-slate-900"
                                                : "text-slate-500 hover:text-slate-700",
                                        )}
                                    >
                                        {option.label} {option.count}
                                    </button>
                                ))}
                            </div>

                            <div className="flex flex-col gap-2 lg:flex-row lg:items-center">
                                <div className="relative">
                                    <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                                    <input
                                        value={query}
                                        onChange={(event) => setQuery(event.target.value)}
                                        placeholder="사건명, 담당자 검색"
                                        className="h-9 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm outline-none placeholder:text-slate-400 focus:border-slate-400 lg:w-[260px]"
                                    />
                                </div>

                                <label className="relative">
                                    <select
                                        value={sortMode}
                                        onChange={(event) =>
                                            setSortMode(event.target.value as SortMode)
                                        }
                                        className="h-9 appearance-none rounded-md border border-slate-200 bg-white px-3 pr-8 text-sm font-medium text-slate-700 outline-none focus:border-slate-400"
                                        aria-label="정렬"
                                    >
                                        {Object.entries(sortLabels).map(([value, label]) => (
                                            <option key={value} value={value}>
                                                {label}
                                            </option>
                                        ))}
                                    </select>
                                    <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                                </label>

                                <div className="relative">
                                    <Button
                                        type="button"
                                        variant="outline"
                                        className="gap-1.5"
                                        onClick={() => setShowFilters((current) => !current)}
                                    >
                                        <SlidersHorizontal className="size-4" />
                                        필터
                                    </Button>

                                    {showFilters ? (
                                        <FilterPopover
                                            activeTab={activeTab}
                                            filters={filters}
                                            departmentOptions={departmentOptions}
                                            onChangeFilters={setFilters}
                                            onReset={resetFilters}
                                        />
                                    ) : null}
                                </div>
                            </div>
                        </div>
                    </div>

                    {loading ? (
                        <div className="flex min-h-[260px] items-center justify-center">
                            <Loader2 className="size-6 animate-spin text-slate-400" />
                        </div>
                    ) : filteredCases.length === 0 ? (
                        <EmptyState activeTab={activeTab} />
                    ) : (
                        <>
                            <div className="overflow-x-auto">
                                <table className="w-full min-w-[820px] table-fixed text-left text-sm">
                                    <thead className="border-b border-slate-100 bg-slate-50 text-xs font-semibold text-slate-500">
                                    <tr>
                                        <th className="px-5 py-3">사건</th>
                                        <th className="w-24 px-5 py-3">위험도</th>
                                        <th className="w-36 px-5 py-3">요청일시</th>
                                        <th className="w-32 px-5 py-3">담당자</th>
                                        <th className="w-24 px-5 py-3 text-right">작업</th>
                                    </tr>
                                    </thead>
                                    <tbody className="divide-y divide-slate-100">
                                    {paginatedCases.map((caseItem, index) => {
                                        const requestAgeHours = getRequestAgeHours(
                                            caseItem.reviewRequestedAt,
                                        );
                                        const completed = isCompletedReview(caseItem);
                                        const supplementRequested =
                                            isSupplementRequestedReview(caseItem);
                                        const actionLabel = hasReviewStatus(caseItem, [
                                            REQUESTED_REVIEW_STATUS,
                                        ]) || isPendingReviewAssignment(caseItem)
                                            ? "배정"
                                            : "변경";

                                        return (
                                            <tr
                                                key={`${activeTab}-${caseItem.caseId}`}
                                                className="review-row-animate hover:bg-slate-50/70"
                                                style={{
                                                    animationDelay: `${Math.min(index, 8) * 24}ms`,
                                                }}
                                            >
                                                <td className="px-5 py-4 align-top">
                                                    <p className="truncate font-semibold text-slate-900">
                                                        {caseItem.caseName}
                                                    </p>
                                                    <p className="mt-1 truncate text-xs text-slate-500">
                                                        {caseItem.caseId} · {getScopeLabel(caseItem)}
                                                    </p>
                                                </td>
                                                <td
                                                    className={cn(
                                                        "px-5 py-4 align-top",
                                                        riskTextClass(caseItem),
                                                    )}
                                                >
                                                    {getRiskText(caseItem)}
                                                </td>
                                                <td className="px-5 py-4 align-top text-slate-600">
                                                    <p>
                                                        {formatCompactDateTime(
                                                            caseItem.reviewRequestedAt ??
                                                            caseItem.createdAt,
                                                        )}
                                                    </p>
                                                    {isDelayedReview(caseItem) ? (
                                                        <p className="mt-1 text-xs font-semibold text-red-600">
                                                            {requestAgeHours}시간 지연
                                                        </p>
                                                    ) : null}
                                                </td>
                                                <td className="px-5 py-4 align-top text-slate-700">
                                                    {reviewerName(caseItem.reviewerId, reviewers)}
                                                </td>
                                                <td className="px-5 py-4 text-right align-top">
                                                    {completed || supplementRequested ? (
                                                        <span className="text-slate-400">-</span>
                                                    ) : (
                                                        <Button
                                                            type="button"
                                                            size="sm"
                                                            variant="outline"
                                                            onClick={() => openAssignmentModal(caseItem)}
                                                        >
                                                            {actionLabel}
                                                        </Button>
                                                    )}
                                                </td>
                                            </tr>
                                        );
                                    })}
                                    </tbody>
                                </table>
                            </div>

                            <div className="border-t border-slate-100 px-5 py-3">
                                <PaginationControls
                                    currentPage={currentCasePage}
                                    totalPages={totalCasePages}
                                    totalCount={totalCaseCount}
                                    pageSize={CASES_PER_PAGE}
                                    onPageChange={setCasePage}
                                />
                            </div>
                        </>
                    )}
                </section>
            </div>

            {assignmentCase ? (
                <AssignmentModal
                    caseItem={assignmentCase}
                    reviewers={assignmentReviewers}
                    reviewersLoading={assignmentReviewersLoading}
                    selectedReviewerId={selectedReviewerId}
                    processing={processing}
                    buttonLabel={assignmentButtonLabel}
                    canSubmit={canSubmitAssignment}
                    onSelectReviewer={setSelectedReviewerId}
                    onClose={closeAssignmentModal}
                    onSubmit={handleAssignSelectedCase}
                />
            ) : null}

            <style jsx global>{`
        @keyframes review-row-enter {
          from {
            opacity: 0;
            transform: translateY(6px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }

        @keyframes review-modal-backdrop-enter {
          from {
            opacity: 0;
          }
          to {
            opacity: 1;
          }
        }

        @keyframes review-modal-panel-enter {
          from {
            opacity: 0;
            transform: translateY(10px) scale(0.98);
          }
          to {
            opacity: 1;
            transform: translateY(0) scale(1);
          }
        }

        .review-row-animate {
          animation: review-row-enter 180ms ease-out both;
        }

        .review-modal-backdrop {
          animation: review-modal-backdrop-enter 140ms ease-out both;
        }

        .review-modal-panel {
          animation: review-modal-panel-enter 180ms ease-out both;
        }
      `}</style>
        </>
    );
}

function FilterPopover({
                           activeTab,
                           filters,
                           departmentOptions,
                           onChangeFilters,
                           onReset,
                       }: {
    activeTab: QueueTab;
    filters: QueueFilters;
    departmentOptions: string[];
    onChangeFilters: (filters: QueueFilters) => void;
    onReset: () => void;
}) {
    return (
        <div className="absolute right-0 z-50 mt-2 w-[320px] max-w-[calc(100vw-3rem)] rounded-lg border border-slate-200 bg-white p-4 shadow-lg">
            <div className="space-y-4">
                <FilterSelect
                    label="기관"
                    value={filters.organizationType}
                    options={[
                        { value: "ALL", label: "전체 기관" },
                        ...ORG_TYPES,
                    ]}
                    onChange={(value) =>
                        onChangeFilters({
                            ...filters,
                            organizationType: value as OrganizationFilter,
                            department: "ALL",
                        })
                    }
                />

                <FilterSelect
                    label="부서"
                    value={filters.department}
                    options={[
                        { value: "ALL", label: "전체 부서" },
                        ...departmentOptions.map((department) => ({
                            value: department,
                            label: department,
                        })),
                    ]}
                    onChange={(value) =>
                        onChangeFilters({ ...filters, department: value })
                    }
                />

                <FilterButtonGroup
                    label="위험도"
                    value={filters.risk}
                    options={[
                        { value: "ALL", label: "전체" },
                        { value: "HIGH", label: "70점 이상" },
                        { value: "NORMAL", label: "70점 미만" },
                    ]}
                    onChange={(value) =>
                        onChangeFilters({ ...filters, risk: value as RiskFilter })
                    }
                />

                {activeTab !== "COMPLETED" && activeTab !== "SUPPLEMENT_REQUESTED" ? (
                    <label className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2 text-sm text-slate-700">
                        지연 사건만 보기
                        <input
                            type="checkbox"
                            checked={filters.delayedOnly}
                            onChange={(event) =>
                                onChangeFilters({
                                    ...filters,
                                    delayedOnly: event.target.checked,
                                })
                            }
                            className="size-4 accent-teal-600"
                        />
                    </label>
                ) : null}
            </div>

            <div className="mt-4 flex justify-end">
                <Button type="button" variant="outline" onClick={onReset}>
                    초기화
                </Button>
            </div>
        </div>
    );
}

function FilterSelect({
                          label,
                          value,
                          options,
                          onChange,
                      }: {
    label: string;
    value: string;
    options: Array<{ value: string; label: string }>;
    onChange: (value: string) => void;
}) {
    return (
        <label className="block">
            <span className="text-xs font-semibold text-slate-500">{label}</span>
            <div className="relative mt-2">
                <select
                    value={value}
                    onChange={(event) => onChange(event.target.value)}
                    className="h-9 w-full appearance-none rounded-md border border-slate-200 bg-white px-3 pr-8 text-sm text-slate-700 outline-none focus:border-slate-400"
                >
                    {options.map((option) => (
                        <option key={option.value} value={option.value}>
                            {option.label}
                        </option>
                    ))}
                </select>
                <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            </div>
        </label>
    );
}

function FilterButtonGroup({
                               label,
                               value,
                               options,
                               onChange,
                           }: {
    label: string;
    value: string;
    options: Array<{ value: string; label: string }>;
    onChange: (value: string) => void;
}) {
    return (
        <div>
            <p className="text-xs font-semibold text-slate-500">{label}</p>
            <div className="mt-2 flex flex-wrap gap-2">
                {options.map((option) => (
                    <button
                        key={option.value}
                        type="button"
                        onClick={() => onChange(option.value)}
                        className={cn(
                            "rounded-md border px-3 py-1.5 text-sm",
                            value === option.value
                                ? "border-teal-600 bg-teal-600 text-white"
                                : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50",
                        )}
                    >
                        {option.label}
                    </button>
                ))}
            </div>
        </div>
    );
}

function AssignmentModal({
                             caseItem,
                             reviewers,
                             reviewersLoading,
                             selectedReviewerId,
                             processing,
                             buttonLabel,
                             canSubmit,
                             onSelectReviewer,
                             onClose,
                             onSubmit,
                         }: {
    caseItem: CaseSummary;
    reviewers: ReviewerStat[];
    reviewersLoading: boolean;
    selectedReviewerId: string;
    processing: boolean;
    buttonLabel: string;
    canSubmit: boolean;
    onSelectReviewer: (reviewerId: string) => void;
    onClose: () => void;
    onSubmit: () => void;
}) {
    const [reviewerQuery, setReviewerQuery] = useState("");
    const normalizedReviewerQuery = reviewerQuery.trim().toLowerCase();
    const currentReviewerId = caseItem.reviewerId;
    const visibleReviewers = reviewers.filter((reviewer) => {
        if (!normalizedReviewerQuery) return true;
        return reviewer.name.toLowerCase().includes(normalizedReviewerQuery);
    });

    return (
        <div className="review-modal-backdrop fixed inset-0 z-50 flex items-center justify-center bg-slate-950/30 px-4 py-6">
            <div
                role="dialog"
                aria-modal="true"
                className="review-modal-panel w-full max-w-[520px] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl"
            >
                <div className="flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
                    <div>
                        <h2 className="text-lg font-bold text-slate-900">
                            {caseItem.reviewerId ? "담당자 변경" : "담당자 배정"}
                        </h2>
                        <p className="mt-1 text-sm text-slate-500">
                            사건 소속 기관/부서의 검토자만 표시됩니다.
                        </p>
                    </div>
                    <button
                        type="button"
                        onClick={onClose}
                        className="rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
                        aria-label="닫기"
                    >
                        <X className="size-5" />
                    </button>
                </div>

                <div className="px-5 py-4">
                    <div className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
                        <p className="truncate font-semibold text-slate-900">
                            {caseItem.caseName}
                        </p>
                        <div className="mt-2 grid gap-1 text-sm text-slate-600">
                            <p>소속: {getScopeLabel(caseItem)}</p>
                            <p>현재 담당자: {reviewerName(caseItem.reviewerId, reviewers)}</p>
                            <p>
                                위험도:{" "}
                                <span className={riskTextClass(caseItem)}>
                  {getRiskText(caseItem)}
                </span>
                            </p>
                        </div>
                    </div>

                    <div className="mt-4">
                        <label
                            className="text-sm font-semibold text-slate-700"
                            htmlFor="reviewer-search"
                        >
                            담당자 선택
                        </label>
                        <div className="relative mt-2">
                            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                            <input
                                id="reviewer-search"
                                value={reviewerQuery}
                                onChange={(event) => setReviewerQuery(event.target.value)}
                                placeholder="검토자 이름 검색"
                                disabled={reviewersLoading}
                                className="h-9 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm outline-none placeholder:text-slate-400 focus:border-slate-400"
                            />
                        </div>

                        <div className="mt-3 max-h-[260px] space-y-2 overflow-y-auto pr-1">
                            {reviewersLoading ? (
                                <div className="flex items-center justify-center gap-2 rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-sm text-slate-500">
                                    <Loader2 className="size-4 animate-spin" />
                                    동일 기관·부서 검토자를 불러오는 중입니다.
                                </div>
                            ) : reviewers.length === 0 ? (
                                <div className="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-sm text-slate-500">
                                    이 사건의 기관/부서에 등록된 검토자가 없습니다.
                                </div>
                            ) : visibleReviewers.length === 0 ? (
                                <div className="rounded-lg border border-dashed border-slate-200 px-4 py-8 text-center text-sm text-slate-500">
                                    검색 결과가 없습니다.
                                </div>
                            ) : (
                                visibleReviewers.map((reviewer) => {
                                    const selected = selectedReviewerId === reviewer.id;
                                    const current = currentReviewerId === reviewer.id;

                                    return (
                                        <button
                                            key={reviewer.id}
                                            type="button"
                                            disabled={current}
                                            onClick={() => onSelectReviewer(reviewer.id)}
                                            className={cn(
                                                "w-full rounded-lg border px-4 py-3 text-left transition-colors",
                                                selected
                                                    ? "border-teal-600 bg-teal-50"
                                                    : "border-slate-200 bg-white hover:bg-slate-50",
                                                current &&
                                                "cursor-default bg-slate-50 text-slate-500 hover:bg-slate-50",
                                            )}
                                        >
                                            <div className="flex items-center justify-between gap-3">
                                                <div className="min-w-0">
                                                    <p className="truncate text-sm font-semibold text-slate-900">
                                                        {reviewer.name}
                                                    </p>
                                                    <p className="mt-1 text-xs text-slate-500">
                                                        담당 {reviewer.assignedCount}건 · 지연{" "}
                                                        {reviewer.delayedCount}건
                                                    </p>
                                                </div>
                                                {current ? (
                                                    <span className="shrink-0 rounded-full bg-slate-200 px-2 py-0.5 text-xs font-semibold text-slate-600">
                            현재 담당자
                          </span>
                                                ) : selected ? (
                                                    <span className="shrink-0 text-sm font-semibold text-teal-700">
                            선택됨
                          </span>
                                                ) : null}
                                            </div>
                                        </button>
                                    );
                                })
                            )}
                        </div>
                    </div>
                </div>

                <div className="flex justify-end gap-2 border-t border-slate-100 px-5 py-4">
                    <Button
                        type="button"
                        variant="outline"
                        onClick={onClose}
                        disabled={processing}
                    >
                        취소
                    </Button>
                    <Button
                        type="button"
                        onClick={onSubmit}
                        disabled={!canSubmit || processing}
                    >
                        {processing ? (
                            <Loader2 className="mr-2 size-4 animate-spin" />
                        ) : (
                            <UserRoundCheck className="mr-2 size-4" />
                        )}
                        {buttonLabel}
                    </Button>
                </div>
            </div>
        </div>
    );
}

function EmptyState({ activeTab }: { activeTab: QueueTab }) {
    const title =
        activeTab === "REQUESTED"
            ? "배정대기 사건이 없습니다."
            : activeTab === "ASSIGNED"
                ? "검토중인 사건이 없습니다."
                : activeTab === "SUPPLEMENT_REQUESTED"
                    ? "재검토 사건이 없습니다."
                    : "승인된 사건이 없습니다.";
    const description =
        activeTab === "REQUESTED"
            ? "담당자 배정이 필요한 사건이 생기면 이곳에 표시됩니다."
            : activeTab === "ASSIGNED"
                ? "담당자가 배정되어 검토 중인 사건이 이곳에 표시됩니다."
                : activeTab === "SUPPLEMENT_REQUESTED"
                    ? "검토자가 재검토로 표시한 사건이 이곳에 표시됩니다."
                    : "승인된 사건이 이곳에 표시됩니다.";

    return (
        <div className="flex min-h-[260px] flex-col items-center justify-center text-center">
            <CheckCircle2 className="size-9 text-slate-300" />
            <p className="mt-3 text-sm font-semibold text-slate-700">{title}</p>
            <p className="mt-1 text-sm text-slate-500">{description}</p>
        </div>
    );
}

function PaginationControls({
                                currentPage,
                                totalPages,
                                totalCount,
                                pageSize,
                                onPageChange,
                            }: {
    currentPage: number;
    totalPages: number;
    totalCount: number;
    pageSize: number;
    onPageChange: (page: number) => void;
}) {
    const start = totalCount === 0 ? 0 : (currentPage - 1) * pageSize + 1;
    const end = Math.min(currentPage * pageSize, totalCount);

    return (
        <div className="flex flex-col gap-2 text-sm sm:flex-row sm:items-center sm:justify-between">
            <p className="text-slate-500">
                {start}-{end} / 총 {totalCount}건
            </p>
            <div className="flex items-center gap-2">
                <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={currentPage <= 1}
                    onClick={() => onPageChange(currentPage - 1)}
                >
                    이전
                </Button>
                <span className="min-w-12 text-center font-semibold text-slate-700">
          {currentPage} / {totalPages}
        </span>
                <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={currentPage >= totalPages}
                    onClick={() => onPageChange(currentPage + 1)}
                >
                    다음
                </Button>
            </div>
        </div>
    );
}
