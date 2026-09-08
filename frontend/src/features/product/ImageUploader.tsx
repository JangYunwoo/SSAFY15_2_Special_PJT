import { useEffect, useRef, useState, type ChangeEvent, type DragEvent } from 'react'

const MAX_EXTRA_IMAGES = 4
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

/** 대표 이미지 1장 + 추가 이미지 최대 4장 (.upload) */
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
    onChange([...images, ...picked].slice(0, MAX_EXTRA_IMAGES + 1))
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

  const cover = images[0]

  return (
    <>
      <button
        type="button"
        className="upload"
        onClick={() => {
          inputRef.current?.click()
        }}
        onDragOver={(event) => {
          event.preventDefault()
        }}
        onDrop={onDrop}
      >
        {cover === undefined ? (
          <>
            대표 이미지를 끌어다 놓거나 클릭해서 업로드
            <br />
            <span className="small">JPG · PNG · 10MB 이하 · 권장 1200×1200</span>
          </>
        ) : (
          <img src={cover.previewUrl} alt="대표 이미지 미리보기" className="upload-preview" />
        )}
      </button>

      <input
        ref={inputRef}
        type="file"
        accept={ACCEPTED.join(',')}
        multiple
        hidden
        onChange={onPick}
      />

      <div className="field-row mt16">
        {Array.from({ length: MAX_EXTRA_IMAGES }, (_, index) => {
          const image = images[index + 1]
          return (
            <button
              key={index}
              type="button"
              className="upload"
              style={{ height: 80 }}
              onClick={() => {
                if (image) remove(index + 1)
                else inputRef.current?.click()
              }}
            >
              {image === undefined ? (
                <>
                  추가 {index + 1}
                  <br />
                  <span className="small">선택</span>
                </>
              ) : (
                <img src={image.previewUrl} alt="" className="upload-preview" />
              )}
            </button>
          )
        })}
      </div>

      {error !== null && (
        <p className="ai-hint fail" role="alert">
          {error}
        </p>
      )}
    </>
  )
}
