import { API_BASE_URL } from './client';

/**
 * Files (backend /api/files): upload first, then give the returned fileId to what uses it (a document, an attachment,
 * a profile photo, a category image). Public files (images) have a fixed address; private ones open through a link
 * that works for a few minutes.
 */

/** Limits per purpose, as the backend checks them (its FILE_PURPOSE lookup). */
export const FILE_RULES = {
  PROFILE_PHOTO: { types: ['image/jpeg', 'image/png', 'image/webp'], maxMb: 2, accept: '.jpg,.jpeg,.png,.webp' },
  CATEGORY_IMAGE: { types: ['image/jpeg', 'image/png', 'image/webp'], maxMb: 2, accept: '.jpg,.jpeg,.png,.webp' },
  PARTNER_DOCUMENT: { types: ['application/pdf', 'image/jpeg', 'image/png'], maxMb: 10, accept: '.pdf,.jpg,.jpeg,.png' },
  SUPPORT_ATTACHMENT: {
    types: ['application/pdf', 'image/jpeg', 'image/png', 'image/webp'],
    maxMb: 10,
    accept: '.pdf,.jpg,.jpeg,.png,.webp'
  }
};

/** A file address from the backend as the browser can load it: the API's own paths get the server in front. */
export const fileUrl = (url) => (url && url.startsWith('/') ? `${API_BASE_URL}${url}` : url);

/** "1.2 MB" / "340 KB". */
export const formatFileSize = (bytes) =>
  bytes == null ? '' : bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;

/** The reason a picked file cannot be uploaded for `purpose`, or '' when it can. */
export function fileProblem(file, purpose) {
  const rule = FILE_RULES[purpose];
  if (!file || !rule) return '';
  if (file.type && !rule.types.includes(file.type)) {
    return purpose === 'PARTNER_DOCUMENT' || purpose === 'SUPPORT_ATTACHMENT'
      ? `Choose a PDF${purpose === 'SUPPORT_ATTACHMENT' ? ', JPEG, PNG or WebP' : ', JPEG or PNG'} file.`
      : 'Choose a JPEG, PNG or WebP image.';
  }
  if (file.size > rule.maxMb * 1024 * 1024) return `The file is ${formatFileSize(file.size)}; choose one of up to ${rule.maxMb} MB.`;
  return '';
}

/** Uploads a picked File for `purpose`; resolves to the backend's file (fileId, originalName, url, ...). */
export function uploadFile(authRequest, file, purpose) {
  const body = new FormData();
  body.append('purpose', purpose);
  body.append('file', file);
  return authRequest('/api/files/upload', { method: 'POST', body });
}

/** Uploads a profile photo and makes it the account's (PUT /api/users/photo/update); resolves to the updated user. */
export async function savePhoto(authRequest, userId, file) {
  const uploaded = await uploadFile(authRequest, file, 'PROFILE_PHOTO');
  return authRequest('/api/users/photo/update', { method: 'PUT', body: { userId, fileId: uploaded.fileId } });
}

/**
 * Opens a stored file in a new tab. The tab is opened at once (browsers block tabs opened after a wait) and sent to
 * the short-lived link when it arrives; on failure it closes and the error is thrown.
 */
export async function openFile(authRequest, fileId) {
  const tab = window.open('', '_blank');
  try {
    const { url } = await authRequest(`/api/files/download-url/${fileId}`);
    if (tab) tab.location.href = fileUrl(url);
    else window.location.assign(fileUrl(url));
  } catch (err) {
    tab?.close();
    throw err;
  }
}
