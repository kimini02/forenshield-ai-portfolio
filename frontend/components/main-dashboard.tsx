"use client"

import { useEffect, useState } from "react"
import { UploadPanel } from "@/components/upload-panel"
import { CapabilitiesSection } from "@/components/capabilities-section"
import { RecentAnalyses } from "@/components/recent-analyses"
import { MetadataInfo } from "@/components/metadata-info"
import type { UploadResult } from "@/lib/evidence-api"
import type { MetadataDisplayItem } from "@/lib/metadata-types"
import {
  loadRecentUploads,
  mergeRecentUploads,
  saveRecentUploads,
} from "@/lib/recent-uploads-storage"

export function MainDashboard() {
  const [uploads, setUploads] = useState<UploadResult[]>([])
  const [metadataItems, setMetadataItems] = useState<MetadataDisplayItem[]>([])
  const [statsRefreshKey, setStatsRefreshKey] = useState(0)
  const [hydrated, setHydrated] = useState(false)

  useEffect(() => {
    setUploads(loadRecentUploads())
    setHydrated(true)
  }, [])

  function handleAnalyzeComplete(results: UploadResult[], startedCount: number) {
    if (results.length === 0 || startedCount === 0) return

    const uniqueIncoming = dedupeBatchByHash(results)
    const { merged, added } = mergeRecentUploads(uploads, uniqueIncoming)

    setUploads(merged)
    saveRecentUploads(merged)
    setStatsRefreshKey((prev) => prev + 1)
  }

  return (
    <div className="grid gap-8 lg:grid-cols-3">
      <div className="space-y-8 lg:col-span-2">
        <UploadPanel
          onMetadataChange={setMetadataItems}
          onAnalyzeComplete={handleAnalyzeComplete}
        />
        <CapabilitiesSection refreshKey={statsRefreshKey} />
      </div>

      <div className="space-y-6 lg:col-span-1">
        <MetadataInfo items={hydrated ? metadataItems : []} />
        <RecentAnalyses uploads={hydrated ? uploads : []} />
      </div>
    </div>
  )
}

function dedupeBatchByHash(results: UploadResult[]): UploadResult[] {
  const seen = new Set<string>()
  return results.filter((item) => {
    if (seen.has(item.hashValue)) return false
    seen.add(item.hashValue)
    return true
  })
}
