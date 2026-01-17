/**
 * HTTP Client Interface
 * 
 * Abstract interface for HTTP requests, allowing dependency injection
 * for testability (can mock Obsidian's requestUrl).
 */

export interface HttpResponse {
  status: number;
  json: unknown;
  text: string;
}

export interface HttpRequest {
  url: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  headers: Record<string, string>;
  body?: string;
}

export interface HttpClient {
  request(req: HttpRequest): Promise<HttpResponse>;
}
