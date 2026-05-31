/**
 * Cinephile TMDb Proxy
 * Edge-deployed API gateway with caching, rate limiting, and analytics.
 * Deploys to Cloudflare Workers (free tier).
 */

export interface Env {
  TMDB_API_KEY: string;
  TMDB_CACHE: KVNamespace;
  RATE_LIMIT: KVNamespace;
  ANALYTICS: AnalyticsEngineDataset;
}

// Configuration
const CONFIG = {
  TMDB_BASE: 'https://api.themoviedb.org/3',
  CACHE_TTL_SECONDS: 6 * 60 * 60, // 6 hours for movie metadata
  SEARCH_CACHE_TTL_SECONDS: 30 * 60, // 30 minutes for search
  RATE_LIMIT_WINDOW_MS: 10_000, // TMDb's window: 10 seconds
  RATE_LIMIT_MAX_REQUESTS: 35, // Conservative: TMDb allows 40, we buffer
  CORS_ORIGINS: ['*'], // Lock this down to your app domain in production
};

interface RateLimitEntry {
  count: number;
  windowStart: number;
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    const clientIp = request.headers.get('CF-Connecting-IP') || 'unknown';
    const startTime = Date.now();

    try {
      // 1. Health check endpoint
      if (url.pathname === '/health') {
        return jsonResponse({ status: 'ok', edge: request.cf?.colo || 'unknown' });
      }

      // 2. Validate path — only proxy /3/* TMDb routes
      if (!url.pathname.startsWith('/3/')) {
        return errorResponse(404, 'Not Found. Use /3/<tmdb-endpoint>');
      }

      // 3. CORS preflight
      if (request.method === 'OPTIONS') {
        return handleCORS(request);
      }

      // 4. Rate limiting (sliding window, per-IP)
      const rateCheck = await checkRateLimit(env.RATE_LIMIT, clientIp);
      if (!rateCheck.allowed) {
        return errorResponse(429, 'Rate limit exceeded. Try again shortly.', {
          'Retry-After': String(Math.ceil(CONFIG.RATE_LIMIT_WINDOW_MS / 1000)),
        });
      }

      // 5. Build TMDb URL with injected API key
      const tmdbUrl = new URL(url.pathname + url.search, CONFIG.TMDB_BASE);
      tmdbUrl.searchParams.set('api_key', env.TMDB_API_KEY);

      // 6. Check KV cache
      const cacheKey = tmdbUrl.toString();
      const cacheTtl = url.pathname.includes('/search/')
        ? CONFIG.SEARCH_CACHE_TTL_SECONDS
        : CONFIG.CACHE_TTL_SECONDS;

      const cached = await env.TMDB_CACHE.get(cacheKey);
      if (cached) {
        // Cache HIT — log and return immediately
        ctx.waitUntil(logAnalytics(env, request, url, clientIp, 200, true, Date.now() - startTime));
        return new Response(cached, {
          status: 200,
          headers: {
            'Content-Type': 'application/json',
            'X-Cache': 'HIT',
            'Access-Control-Allow-Origin': '*',
            'Cache-Control': `public, max-age=${cacheTtl}`,
          },
        });
      }

      // 7. Forward to TMDb
      const tmdbResponse = await fetch(tmdbUrl.toString(), {
        method: request.method,
        headers: {
          'Accept': 'application/json',
          'User-Agent': 'Cinephile-Proxy/1.0',
        },
      });

      const status = tmdbResponse.status;
      const body = await tmdbResponse.text();

      // 8. Cache successful GET responses
      if (request.method === 'GET' && status === 200) {
        ctx.waitUntil(env.TMDB_CACHE.put(cacheKey, body, { expirationTtl: cacheTtl }));
      }

      // 9. Log analytics
      ctx.waitUntil(logAnalytics(env, request, url, clientIp, status, false, Date.now() - startTime));

      // 10. Return response with CORS
      return new Response(body, {
        status,
        headers: {
          'Content-Type': 'application/json',
          'X-Cache': 'MISS',
          'X-RateLimit-Remaining': String(rateCheck.remaining),
          'Access-Control-Allow-Origin': '*',
          'Cache-Control': `public, max-age=${cacheTtl}`,
        },
      });

    } catch (err) {
      console.error('Proxy error:', err);
      return errorResponse(500, 'Internal proxy error');
    }
  },
};

// ─── Rate Limiting ───────────────────────────────────────────────────────────

async function checkRateLimit(kv: KVNamespace, clientIp: string): Promise<{ allowed: boolean; remaining: number }> {
  const key = `ratelimit:${clientIp}`;
  const now = Date.now();
  const windowStart = Math.floor(now / CONFIG.RATE_LIMIT_WINDOW_MS) * CONFIG.RATE_LIMIT_WINDOW_MS;

  const raw = await kv.get(key);
  let entry: RateLimitEntry = raw ? JSON.parse(raw) : { count: 0, windowStart };

  // Reset if window expired
  if (entry.windowStart < windowStart) {
    entry = { count: 0, windowStart };
  }

  entry.count++;
  const ttl = Math.max(60, Math.ceil(CONFIG.RATE_LIMIT_WINDOW_MS / 1000));
  await kv.put(key, JSON.stringify(entry), { expirationTtl: ttl });

  const allowed = entry.count <= CONFIG.RATE_LIMIT_MAX_REQUESTS;
  const remaining = Math.max(0, CONFIG.RATE_LIMIT_MAX_REQUESTS - entry.count);

  return { allowed, remaining };
}

// ─── Analytics ───────────────────────────────────────────────────────────────

async function logAnalytics(
  env: Env,
  request: Request,
  url: URL,
  clientIp: string,
  status: number,
  cacheHit: boolean,
  durationMs: number
): Promise<void> {
  try {
    // Cloudflare Analytics Engine (optional, free)
    if (env.ANALYTICS) {
      env.ANALYTICS.writeDataPoint({
        blobs: [request.method, url.pathname, clientIp, cacheHit ? 'HIT' : 'MISS'],
        doubles: [status, durationMs],
        indexes: [String(Math.floor(Date.now() / 60000))], // minute bucket
      });
    }
  } catch {
    // Best-effort analytics
  }
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

function jsonResponse(data: object, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
    },
  });
}

function errorResponse(status: number, message: string, extraHeaders: Record<string, string> = {}): Response {
  return new Response(JSON.stringify({ error: message, status }), {
    status,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
      ...extraHeaders,
    },
  });
}

function handleCORS(request: Request): Response {
  const origin = request.headers.get('Origin') || '*';
  return new Response(null, {
    status: 204,
    headers: {
      'Access-Control-Allow-Origin': origin,
      'Access-Control-Allow-Methods': 'GET, HEAD, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
      'Access-Control-Max-Age': '86400',
    },
  });
}
