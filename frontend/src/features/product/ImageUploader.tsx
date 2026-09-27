import { useEffect, useRef, useState, type ChangeEvent, type DragEvent } from 'react'

/**
 * 대표 1장 + 추가 3장 = 모두 4장. 정사각 칸 네 개를 한 줄로 늘어놓습니다.
 * 다섯 칸이면 판매자센터 본문 폭에서 한 칸이 너무 작아져 사진이 알아보기 어렵습니다.
 */
const MAX_IMAGES = 4
const MAX_BYTES = 10 * 1024 * 1024
const ACCEPTED = ['image/jpeg', 'image/png']

export interface PickedImage {
  file: File
  previewUrl: string
}

interface ImageUploaderProps {
  images: readonly PickedImage[]
  onChange: (images: PickedImage[]) => void
}

/** 대표 이미지 1장 + 추가 이미지 최대 3장. 정사각 칸 네 개를 한 줄로 보여줍니다 (.upload-row) */
export function ImageUploader({ images, onChange }: ImageUploaderProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)

  // createObjectURL 로 만든 미리보기는 직접 해제해야 메모리에 남지 않습니다.
  useEffect(() => {
    return () => {
      for (const image of images) URL.revokeObjectURL(image.previewUrl)
    }
    // 언마운트 시 한 번만 정리합니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const accept = (files: FileList | null) => {
    if (files === null) return

    const picked: PickedImage[] = []
    for (const file of files) {
      if (!ACCEPTED.includes(file.type)) {
        setError('JPG 또는 PNG 파일만 올릴 수 있습니다.')
        continue
      }
      if (file.size > MAX_BYTES) {
        setError('10MB 이하 파일만 올릴 수 있습니다.')
        continue
      }
      picked.push({ file, previewUrl: URL.createObjectURL(file) })
    }

    if (picked.length === 0) return
    setError(null)
    onChange([...images, ...picked].slice(0, MAX_IMAGES))
  }

  const remove = (index: number) => {
    const target = images[index]
    if (target) URL.revokeObjectURL(target.previewUrl)
    onChange(images.filter((_, at) => at !== index))
  }

  const onDrop = (event: DragEvent<HTMLButtonElement>) => {
    event.preventDefault()
    accept(event.dataTransfer.files)
  }

  const onPick = (event: ChangeEvent<HTMLInputElement>) => {
    accept(event.target.files)
    // 같은 파일을 다시 고를 수 있게 값을 비웁니다.
    event.target.value = ''
  }

  const openPicker = () => {
    inputRef.current?.click()
  }

  return (
    <>
      <div className="upload-row">
        {Array.from({ length: MAX_IMAGES }, (_, index) => {
          const image = images[index]
          const isCover = index === 0
          return (
            <button
              key={index}
              type="button"
              className="upload upload-square"
              aria-label={
                image === undefined
                  ? `${isCover ? '대표 이미지' : `추가 이미지 ${String(index)}`} 올리기`
                  : `${isCover ? '대표 이미지' : `추가 이미지 ${String(index)}`} 지우기`
              }
              onClick={() => {
                // 채워진 칸을 누르면 지웁니다. 대표를 지우면 다음 장이 대표가 됩니다.
                if (image) remove(index)
                else openPicker()
              }}
              onDragOver={(event) => {
                event.preventDefault()
              }}
              onDrop={onDrop}
            >
              {image === undefined ? (
                <>
                  {isCover ? '대표 이미지' : `추가 ${String(index)}`}
                  <span className="small">{isCover ? '필수' : '선택'}</span>
                </>
              ) : (
                <>
                  <img src={image.previewUrl} alt="" className="upload-preview" />
                  {isCover && <span className="upload-badge">대표</span>}
                </>
              )}
            </button>
          )
        })}
      </div>
      <p className="small muted mt8" style={{ marginBottom: 0 }}>
        끌어다 놓거나 눌러서 올립니다 · JPG · PNG · 10MB 이하 · 권장 1200×1200 · 올린 사진을 누르면
        지워집니다
      </p>

      <input
        ref={inputRef}
        type="file"
        accept={ACCEPTED.join(',')}
        multiple
        hidden
        onChange={onPick}
      />

      {error !== null && (
        <p className="ai-hint fail" role="alert">
          {error}
        </p>
      )}
    </>
  )
}
