import { describe, it, expect } from "vitest";
import { NexoClient, NexoApiError } from "../src/NexoClient";

/**
 * Offline tests — no live backend required. We inject a custom fetch
 * implementation that simulates backend responses so CI doesn't need to
 * stand up the JVM + Postgres stack just to run these.
 */

function mockFetch(responses: Array<{
  match?: (url: string, init?: RequestInit) => boolean;
  status: number;
  json?: unknown;
  text?: string;
}>): typeof fetch {
  let i = 0;
  return (async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const url = typeof input === "string" ? input : input.toString();
    const r = responses.find(x => !x.match || x.match(url, init)) ?? responses[i++];
    return {
      ok: r.status >= 200 && r.status < 300,
      status: r.status,
      json: async () => r.json ?? {},
      text: async () => r.text ?? "",
    } as Response;
  }) as typeof fetch;
}

describe("NexoClient", () => {
  it("login posts credentials and stores the returned token", async () => {
    let capturedBody: string | undefined;
    const fetchImpl = (async (input: RequestInfo | URL, init?: RequestInit) => {
      capturedBody = init?.body as string;
      return {
        ok: true, status: 200,
        json: async () => ({ token: "jwt-abc" }),
      } as Response;
    }) as typeof fetch;

    const client = await NexoClient.login("http://localhost:8081",
      "alice@demo.ai", "pw", fetchImpl);

    expect(capturedBody).toBeDefined();
    const parsed = JSON.parse(capturedBody!);
    expect(parsed.email).toBe("alice@demo.ai");
    expect(parsed.password).toBe("pw");
    // Following request should carry the bearer token
    let authHeader: string | undefined;
    (client as any).fetchImpl = (async (_u: unknown, init?: RequestInit) => {
      authHeader = (init?.headers as Record<string, string>)?.authorization;
      return { ok: true, status: 200, json: async () => ({}) } as Response;
    }) as typeof fetch;
    await client.rest("/ping");
    expect(authHeader).toBe("Bearer jwt-abc");
  });

  it("login throws NexoApiError on non-2xx", async () => {
    const fetchImpl = mockFetch([{ status: 401 }]);
    await expect(NexoClient.login("http://h", "a", "b", fetchImpl))
      .rejects.toThrow(NexoApiError);
  });

  it("gql throws NexoApiError when response contains errors", async () => {
    const client = new NexoClient({
      baseUrl: "http://h", token: "t",
      fetch: mockFetch([{ status: 200, json: { errors: [{ message: "boom" }] } }]),
    });
    await expect(client.gql("{ x }")).rejects.toThrow(/boom/);
  });

  it("semanticSearch unwraps the GraphQL envelope", async () => {
    const client = new NexoClient({
      baseUrl: "http://h", token: "t",
      fetch: mockFetch([{
        status: 200,
        json: { data: { semanticSearch: [
          { id: "o1", score: 0.92, properties: { name: "Acme" } },
        ]}},
      }]),
    });
    const hits = await client.semanticSearch("query", 3);
    expect(hits).toHaveLength(1);
    expect(hits[0].id).toBe("o1");
  });

  it("agentChat unwraps the GraphQL envelope", async () => {
    const client = new NexoClient({
      baseUrl: "http://h", token: "t",
      fetch: mockFetch([{
        status: 200,
        json: { data: { agentChat: { message: "42", toolCalls: [{ tool: "countObjects" }] }}},
      }]),
    });
    const r = await client.agentChat("how many?");
    expect(r.message).toBe("42");
    expect(r.toolCalls[0].tool).toBe("countObjects");
  });

  it("rest raises NexoApiError on non-2xx with body text", async () => {
    const client = new NexoClient({
      baseUrl: "http://h", token: "t",
      fetch: mockFetch([{ status: 500, text: "internal boom" }]),
    });
    await expect(client.rest("/nope")).rejects.toMatchObject({
      status: 500,
      body: "internal boom",
    });
  });

  it("baseUrl trailing slash is normalized", () => {
    const client = new NexoClient({ baseUrl: "http://h/", token: "t" });
    expect((client as any).baseUrl).toBe("http://h");
  });

  it("constructor rejects missing baseUrl", () => {
    expect(() => new NexoClient({ baseUrl: "" })).toThrow();
  });
});
