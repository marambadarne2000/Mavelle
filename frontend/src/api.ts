export class ApiError extends Error {
  constructor(message: string, public status: number) { super(message); }
}

export async function check(response: Response): Promise<void> {
  if (response.ok) return;
  const problem = await response.json().catch(() => null) as { detail?: string } | null;
  throw new ApiError(problem?.detail || (response.status === 400 ? 'Check the required fields and try again.' : 'The request failed. Please try again.'), response.status);
}

export async function write(path: string, body?: BodyInit, contentType?: string, method = 'POST'): Promise<Response> {
  const tokenResponse = await fetch('/api/auth/csrf', { signal: AbortSignal.timeout(15000) });
  await check(tokenResponse);
  const csrf: { token: string; headerName: string } = await tokenResponse.json();
  const headers: Record<string, string> = { [csrf.headerName]: csrf.token };
  if (contentType) headers['Content-Type'] = contentType;
  return fetch(path, { method, body, headers, signal: AbortSignal.timeout(15000) });
}
