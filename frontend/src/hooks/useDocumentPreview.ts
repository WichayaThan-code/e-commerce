import { useEffect, useState } from 'react'
import { merchantApi, type DocumentType } from '../services/api'

type CacheEntry = { url: string; blob: Blob; contentType: string }

const previewCache = new Map<DocumentType, CacheEntry>()

function putCache(type: DocumentType, blob: Blob): CacheEntry {
  const previous = previewCache.get(type)
  if (previous) URL.revokeObjectURL(previous.url)
  const entry: CacheEntry = {
    url: URL.createObjectURL(blob),
    blob,
    contentType: blob.type || '',
  }
  previewCache.set(type, entry)
  return entry
}

export function clearDocumentPreviewCache(type?: DocumentType) {
  if (type) {
    const previous = previewCache.get(type)
    if (previous) URL.revokeObjectURL(previous.url)
    previewCache.delete(type)
    return
  }
  for (const key of [...previewCache.keys()]) {
    const previous = previewCache.get(key)
    if (previous) URL.revokeObjectURL(previous.url)
    previewCache.delete(key)
  }
}

export type DocumentPreviewState = {
  url: string
  contentType: string
  loading: boolean
  failed: boolean
  isImage: boolean
  isPdf: boolean
}

function classify(contentType: string, fileName?: string) {
  const type = (contentType || '').toLowerCase()
  const name = (fileName || '').toLowerCase()
  const isPdf = type.includes('pdf') || name.endsWith('.pdf')
  const isImage =
    /^image\/(jpeg|png|webp)$/.test(type) ||
    /\.(jpe?g|png|webp)$/.test(name)
  return { isImage, isPdf }
}

/**
 * Resolves a previewable object URL for a merchant draft document.
 * Prefers an in-memory File; otherwise loads from the authenticated draft document API when a remote name exists.
 * Object URLs are cached per type across route changes.
 */
export function useDocumentPreview(
  type: DocumentType,
  localFile?: File | null,
  remoteFileName?: string,
): DocumentPreviewState {
  const hasRemote = Boolean(remoteFileName?.trim())
  const [url, setUrl] = useState('')
  const [contentType, setContentType] = useState('')
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false

    if (localFile) {
      const entry = putCache(type, localFile)
      if (!cancelled) {
        setUrl(entry.url)
        setContentType(entry.contentType || localFile.type || '')
        setLoading(false)
        setFailed(false)
      }
      return () => {
        cancelled = true
      }
    }

    if (!hasRemote) {
      if (!cancelled) {
        setUrl('')
        setContentType('')
        setLoading(false)
        setFailed(false)
      }
      return () => {
        cancelled = true
      }
    }

    const cached = previewCache.get(type)
    if (cached) {
      if (!cancelled) {
        setUrl(cached.url)
        setContentType(cached.contentType)
        setLoading(false)
        setFailed(false)
      }
      return () => {
        cancelled = true
      }
    }

    setLoading(true)
    setFailed(false)
    merchantApi
      .documentBlob(type)
      .then((blob) => {
        if (cancelled) return
        const entry = putCache(type, blob)
        setUrl(entry.url)
        setContentType(entry.contentType || blob.type || '')
        setLoading(false)
      })
      .catch(() => {
        if (cancelled) return
        setUrl('')
        setContentType('')
        setLoading(false)
        setFailed(true)
      })

    return () => {
      cancelled = true
    }
  }, [type, localFile, hasRemote, remoteFileName])

  const kind = classify(contentType, remoteFileName || localFile?.name)
  return {
    url,
    contentType,
    loading,
    failed,
    isImage: kind.isImage,
    isPdf: kind.isPdf,
  }
}
