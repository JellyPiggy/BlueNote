import { confirmUpload, createUploadToken } from '@/api/file'
import type { ConfirmUploadResult } from '@/api/types'

export interface PickedImage {
  id: string
  path: string
  name: string
  mimeType: string
  size: number
  file?: File
}

export interface UploadedImage extends PickedImage {
  fileId: string
  accessUrl: string
}

export async function pickNoteImages(limit = 9): Promise<PickedImage[]> {
  const result = await uni.chooseImage({
    count: limit,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera']
  })
  const tempFiles = ((result as unknown as { tempFiles?: Array<Record<string, unknown>> }).tempFiles ?? []) as Array<
    Record<string, unknown>
  >
  const paths = ((result as unknown as { tempFilePaths?: string[] }).tempFilePaths ?? []) as string[]

  return tempFiles.map((file, index) => {
    const path = String(file.path ?? file.tempFilePath ?? paths[index] ?? '')
    const name = String(file.name ?? filenameFromPath(path, index))
    const mimeType = String(file.type ?? mimeTypeFromName(name))
    const size = Number(file.size ?? 0)
    return {
      id: `${Date.now()}-${index}-${Math.random().toString(16).slice(2)}`,
      path,
      name,
      mimeType,
      size,
      file: file.file as File | undefined
    }
  })
}

export async function uploadNoteImage(image: PickedImage): Promise<UploadedImage> {
  const token = await createUploadToken({
    scene: 'NOTE_IMAGE',
    filename: image.name,
    mimeType: image.mimeType,
    fileSize: image.size
  })
  await putObject(token.uploadUrl, image, token.headers)
  const confirmed = await confirmUpload(token.fileId, {
    fileSize: image.size
  })
  return {
    ...image,
    fileId: confirmed.fileId,
    accessUrl: confirmed.accessUrl
  }
}

export async function uploadNoteImages(images: PickedImage[]): Promise<UploadedImage[]> {
  const uploaded: UploadedImage[] = []
  for (const image of images) {
    uploaded.push(await uploadNoteImage(image))
  }
  return uploaded
}

async function putObject(uploadUrl: string, image: PickedImage, headers: Record<string, string>) {
  // #ifdef H5
  const body = image.file ?? (await fetch(image.path).then((response) => response.blob()))
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    headers,
    body
  })
  if (!response.ok) {
    throw new Error(`upload failed: ${response.status}`)
  }
  return
  // #endif

  // #ifndef H5
  const data = await readFileAsArrayBuffer(image.path)
  const result = await uni.request({
    url: uploadUrl,
    method: 'PUT',
    header: headers,
    data
  })
  if (result.statusCode < 200 || result.statusCode >= 300) {
    throw new Error(`upload failed: ${result.statusCode}`)
  }
  // #endif
}

function readFileAsArrayBuffer(path: string): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    const manager = uni.getFileSystemManager?.()
    if (!manager) {
      reject(new Error('当前平台暂不支持本地文件读取'))
      return
    }
    manager.readFile({
      filePath: path,
      success: (result) => resolve(result.data as ArrayBuffer),
      fail: reject
    })
  })
}

function filenameFromPath(path: string, index: number) {
  const segment = path.split(/[\\/]/).pop()
  return segment || `note-image-${index + 1}.jpg`
}

function mimeTypeFromName(filename: string) {
  const lower = filename.toLowerCase()
  if (lower.endsWith('.png')) {
    return 'image/png'
  }
  if (lower.endsWith('.webp')) {
    return 'image/webp'
  }
  return 'image/jpeg'
}

export function confirmedMediaFiles(files: ConfirmUploadResult[]) {
  return files.map((file, index) => ({
    fileId: file.fileId,
    mediaType: 'IMAGE' as const,
    sortOrder: index + 1,
    cover: index === 0
  }))
}

