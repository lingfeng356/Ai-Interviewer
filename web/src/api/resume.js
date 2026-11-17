import request from './request'

/**
 * 上传简历  POST /resume/upload
 * form-data: file（只支持 .pdf）
 * data: { resumeId, fileName, filePath }
 */
export function uploadResume(file, onProgress) {
  const formData = new FormData()
  formData.append('file', file)

  return request.post('/resume/upload', formData, {
    timeout: 120000,
    onUploadProgress: (e) => {
      if (typeof onProgress === 'function' && e.total) {
        onProgress(Math.round((e.loaded / e.total) * 100))
      }
    }
  })
}
