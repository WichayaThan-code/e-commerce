import { useCallback, useEffect, useRef, useState } from 'react'
import { FileText, X } from 'lucide-react'
import { getDocument, GlobalWorkerOptions } from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { useDocumentPreview } from '../../hooks/useDocumentPreview'
import type { DocumentType } from '../../services/api'
import i18n from '../../i18n'

GlobalWorkerOptions.workerSrc = workerUrl

export type PreviewKind = 'id' | 'home' | 'business'

type Props = {
  type: DocumentType
  kind: PreviewKind
  exampleTitle: string
  fileName: string
  uploading: boolean
  /** In-memory file after a successful pick+upload in this session. */
  previewFile: File | null
  /** Decorative example shown when there is no successful upload to preview. */
  example: React.ReactNode
}

function PdfPageCanvas({
  url,
  alt,
  onPages,
  onFail,
}: {
  url: string
  alt: string
  onPages: (n: number) => void
  onFail: () => void
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const frameRef = useRef<HTMLDivElement>(null)
  const onPagesRef = useRef(onPages)
  const onFailRef = useRef(onFail)
  onPagesRef.current = onPages
  onFailRef.current = onFail

  useEffect(() => {
    let cancelled = false
    let loadingTask: ReturnType<typeof getDocument> | null = null
    ;(async () => {
      try {
        loadingTask = getDocument({ url })
        const pdf = await loadingTask.promise
        if (cancelled) return
        onPagesRef.current(pdf.numPages)
        const page = await pdf.getPage(1)
        const canvas = canvasRef.current
        const frame = frameRef.current
        if (!canvas || !frame) return
        const maxW = Math.max(frame.clientWidth, 1)
        const maxH = Math.max(frame.clientHeight, 1)
        const base = page.getViewport({ scale: 1 })
        const scale = Math.min(maxW / base.width, maxH / base.height)
        const viewport = page.getViewport({ scale: Math.max(scale, 0.1) })
        canvas.width = viewport.width
        canvas.height = viewport.height
        const ctx = canvas.getContext('2d')
        if (!ctx) {
          onFailRef.current()
          return
        }
        await page.render({ canvasContext: ctx, viewport, canvas }).promise
      } catch {
        if (!cancelled) onFailRef.current()
      }
    })()
    return () => {
      cancelled = true
      void loadingTask?.destroy()
    }
  }, [url])

  return (
    <div ref={frameRef} className="document-preview-pdf-frame">
      <canvas ref={canvasRef} aria-label={alt} />
    </div>
  )
}

export default function DocumentFilePreview({
  type,
  kind,
  exampleTitle,
  fileName,
  uploading,
  previewFile,
  example,
}: Props) {
  const preview = useDocumentPreview(type, previewFile, fileName)
  const [pdfFailed, setPdfFailed] = useState(false)
  const [pageCount, setPageCount] = useState(0)
  const [modalOpen, setModalOpen] = useState(false)

  const uploadedTitle = i18n.t('documents.uploadedTitle', { ns: 'merchant' })
  const previewAlt = i18n.t('documents.previewAlt', { ns: 'merchant' })
  const previewOpen = i18n.t('documents.previewOpen', { ns: 'merchant' })
  const previewClose = i18n.t('documents.previewClose', { ns: 'merchant' })

  useEffect(() => {
    setPdfFailed(false)
    setPageCount(0)
  }, [preview.url])

  useEffect(() => {
    if (!modalOpen) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setModalOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [modalOpen])

  const onPages = useCallback((n: number) => setPageCount(n), [])
  const onPdfFail = useCallback(() => setPdfFailed(true), [])

  const busy = uploading || preview.loading
  const hasLivePreview = Boolean(preview.url) && !preview.failed
  const hasNamedOnly = Boolean(fileName) && !hasLivePreview && !busy
  const title = hasLivePreview || hasNamedOnly || (busy && Boolean(fileName)) ? uploadedTitle : exampleTitle
  const showImage = hasLivePreview && preview.isImage
  const showPdf = hasLivePreview && preview.isPdf && !pdfFailed
  const showFallbackMedia = hasLivePreview && !showImage && !showPdf

  const openFull = () => {
    if (!preview.url) return
    setModalOpen(true)
  }

  return (
    <>
      <div className={`document-example ${kind}${busy ? ' is-uploading' : ''}${hasLivePreview || hasNamedOnly || Boolean(fileName) ? ' has-file' : ''}`}>
        <p>{title}</p>
        {busy ? (
          <div className="document-preview-skeleton" aria-busy="true" aria-label={i18n.t('validation.checking', { ns: 'merchant' })} />
        ) : showImage ? (
          <button type="button" className="document-preview-media" onClick={openFull} aria-label={previewOpen}>
            <img src={preview.url} alt={previewAlt} />
          </button>
        ) : showPdf ? (
          <button type="button" className="document-preview-media is-pdf" onClick={openFull} aria-label={previewOpen}>
            <PdfPageCanvas url={preview.url} alt={previewAlt} onPages={onPages} onFail={onPdfFail} />
            {pageCount > 0 ? (
              <span className="document-preview-badge">
                {i18n.t('documents.pdfBadge', { ns: 'merchant', pages: pageCount })}
              </span>
            ) : null}
          </button>
        ) : showFallbackMedia ? (
          <button type="button" className="document-preview-named" onClick={openFull} aria-label={previewOpen}>
            <FileText aria-hidden="true" />
            <span>{fileName}</span>
          </button>
        ) : hasNamedOnly || preview.failed ? (
          <div className="document-preview-named" aria-label={previewAlt}>
            <FileText aria-hidden="true" />
            <span>{i18n.t('documents.uploadedNamed', { ns: 'merchant', name: fileName })}</span>
          </div>
        ) : (
          example
        )}
      </div>

      {modalOpen && preview.url ? (
        <div
          className="document-preview-modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setModalOpen(false)
          }}
        >
          <div className="document-preview-modal" role="dialog" aria-modal="true" aria-label={previewAlt}>
            <header>
              <b>{fileName}</b>
              <button type="button" onClick={() => setModalOpen(false)} aria-label={previewClose}>
                <X />
              </button>
            </header>
            <div className="document-preview-modal-body">
              {preview.isPdf ? (
                <iframe title={previewAlt} src={preview.url} />
              ) : (
                <img src={preview.url} alt={previewAlt} />
              )}
            </div>
          </div>
        </div>
      ) : null}
    </>
  )
}
