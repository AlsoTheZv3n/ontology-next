/**
 * NEXO Ontology Fabric TypeScript client.
 *
 * Uses the native fetch API — no runtime dependencies. Works in Node 18+
 * and modern browsers. GraphQL operations go through a single /graphql
 * POST; REST calls hit /api/v1/**.
 *
 * Authentication: obtain a JWT via `NexoClient.login(...)` (email/password
 * against /api/auth/login), or construct with an existing bearer token.
 * API keys created through the UI/API also work — pass them as the token.
 */
export interface NexoClientOptions {
  baseUrl: string;
  token?: string;
  fetch?: typeof fetch;
}

export interface OntologyObject {
  id: string;
  objectTypeName?: string;
  properties: Record<string, unknown>;
}

export interface SemanticSearchHit {
  id: string;
  score: number;
  properties: Record<string, unknown>;
}

export class NexoApiError extends Error {
  constructor(public readonly status: number, message: string, public readonly body?: unknown) {
    super(message);
    this.name = "NexoApiError";
  }
}

export class NexoClient {
  private readonly baseUrl: string;
  private readonly fetchImpl: typeof fetch;
  private token: string | undefined;

  constructor(opts: NexoClientOptions) {
    if (!opts.baseUrl) throw new Error("baseUrl is required");
    this.baseUrl = opts.baseUrl.replace(/\/$/, "");
    this.token = opts.token;
    this.fetchImpl = opts.fetch ?? fetch;
  }

  static async login(baseUrl: string, email: string, password: string,
                      fetchImpl: typeof fetch = fetch): Promise<NexoClient> {
    const resp = await fetchImpl(`${baseUrl.replace(/\/$/, "")}/api/auth/login`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    if (!resp.ok) {
      throw new NexoApiError(resp.status, `login failed (${resp.status})`);
    }
    const data = (await resp.json()) as { token: string };
    return new NexoClient({ baseUrl, token: data.token, fetch: fetchImpl });
  }

  setToken(token: string) { this.token = token; }

  private headers(): Record<string, string> {
    const h: Record<string, string> = { "content-type": "application/json" };
    if (this.token) h.authorization = `Bearer ${this.token}`;
    return h;
  }

  async gql<T = unknown>(query: string, variables?: Record<string, unknown>): Promise<T> {
    const resp = await this.fetchImpl(`${this.baseUrl}/graphql`, {
      method: "POST",
      headers: this.headers(),
      body: JSON.stringify({ query, variables }),
    });
    const body = await resp.json() as { data?: T; errors?: Array<{ message: string }> };
    if (!resp.ok || body.errors?.length) {
      const msg = body.errors?.map(e => e.message).join(", ") ?? `HTTP ${resp.status}`;
      throw new NexoApiError(resp.status, msg, body);
    }
    return body.data as T;
  }

  async rest<T = unknown>(path: string, init?: RequestInit): Promise<T> {
    const resp = await this.fetchImpl(`${this.baseUrl}${path}`, {
      ...init,
      headers: { ...this.headers(), ...(init?.headers ?? {}) },
    });
    if (!resp.ok) {
      const txt = await resp.text().catch(() => "");
      throw new NexoApiError(resp.status, `REST ${path} failed (${resp.status})`, txt);
    }
    return resp.json() as Promise<T>;
  }

  // --- Convenience wrappers ---

  async listObjectTypes(): Promise<Array<{ id: string; apiName: string; displayName: string }>> {
    return this.rest("/api/v1/ontology/object-types");
  }

  async upsertObject(objectTypeApiName: string, sourceId: string,
                      properties: Record<string, unknown>): Promise<OntologyObject> {
    return this.rest(`/api/v1/objects`, {
      method: "POST",
      body: JSON.stringify({ objectTypeApiName, sourceId, properties }),
    });
  }

  async semanticSearch(query: string, limit = 5): Promise<SemanticSearchHit[]> {
    const data = await this.gql<{ semanticSearch: SemanticSearchHit[] }>(
      `query($q:String!,$l:Int!){ semanticSearch(query:$q,limit:$l){ id score properties } }`,
      { q: query, l: limit }
    );
    return data.semanticSearch;
  }

  async agentChat(message: string): Promise<{ message: string; toolCalls: Array<{ tool: string }> }> {
    const data = await this.gql<{ agentChat: { message: string; toolCalls: Array<{ tool: string }> } }>(
      `mutation($m:String!){ agentChat(input:{message:$m}){ message toolCalls{ tool } } }`,
      { m: message }
    );
    return data.agentChat;
  }
}
