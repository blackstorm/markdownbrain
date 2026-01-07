/**
 * Obsidian HTTP Client Adapter
 * 
 * Implements HttpClient interface using Obsidian's requestUrl.
 */

import { requestUrl } from 'obsidian';
import type { HttpClient, HttpRequest, HttpResponse } from './sync-api';

export class ObsidianHttpClient implements HttpClient {
  async request(req: HttpRequest): Promise<HttpResponse> {
    const response = await requestUrl({
      url: req.url,
      method: req.method,
      headers: req.headers,
      body: req.body,
      throw: false
    });

    return {
      status: response.status,
      json: response.json,
      text: response.text
    };
  }
}
