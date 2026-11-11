// back(Spring Boot) API 공통 호출 함수입니다. 모든 API 파일이 이 함수를 사용합니다.
// - JSON으로 보내고 JSON으로 받습니다.
// - 오류 응답 {"error": "코드", "message": "문구"}는 ApiError로 바꿔 던집니다 (화면은 message를 표시).
// - 서버에 연결하지 못했거나 응답이 JSON이 아니면 error: 'NETWORK_ERROR'로 구분합니다.

export const NETWORK_ERROR = 'NETWORK_ERROR';

export class ApiError extends Error {
  constructor(code, message, status) {
    super(message);
    this.name = 'ApiError';
    this.code = code; // 서버 오류 코드 (예: DUPLICATE_LOGIN_ID)
    this.status = status; // HTTP 상태 코드 (연결 실패는 0)
  }
}

const CONNECTION_MESSAGE = '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.';

export async function apiRequest(
  path,
  { method = 'GET', body } = {},
  fetchImpl = globalThis.fetch,
) {
  let response;
  try {
    response = await fetchImpl(path, {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(NETWORK_ERROR, CONNECTION_MESSAGE, 0);
  }

  let data = null;
  try {
    data = await response.json();
  } catch {
    data = null; // 응답이 JSON이 아님 (프록시 오류 · 서버 꺼짐 등)
  }

  if (!response.ok) {
    if (data && typeof data.error === 'string') {
      throw new ApiError(data.error, data.message || '요청을 처리하지 못했어요.', response.status);
    }
    throw new ApiError(NETWORK_ERROR, CONNECTION_MESSAGE, response.status);
  }
  if (data === null) throw new ApiError(NETWORK_ERROR, CONNECTION_MESSAGE, response.status);
  return data;
}
