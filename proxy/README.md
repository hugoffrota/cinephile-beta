# Cinephile TMDb Proxy

Edge-deployed API gateway that hides your TMDb API key, caches responses, and enforces rate limits. Built for [Cloudflare Workers](https://workers.cloudflare.com/) (free tier).

## Architecture

```
Cinephile App → Cloudflare Worker → TMDb API
                     │
                     ├── KV Cache (6h TTL for metadata)
                     ├── KV Rate Limit (sliding window per IP)
                     └── Analytics Engine (optional metrics)
```

## Why this exists

- **No API key in the APK**: TMDb keys baked into Android apps are trivial to extract. This proxy holds the secret on the edge.
- **Faster for users**: KV caching at 300+ global PoPs means repeated queries (trending, popular) rarely hit TMDb directly.
- **Rate limit safety**: Even if your app blows up on Hacker News, the proxy throttles per-IP and buffers you from TMDb's 40 req/10s limit.

## Deploy in 5 minutes

### 1. Install Wrangler

```bash
npm install -g wrangler
wrangler login
```

### 2. Create KV namespaces

```bash
wrangler kv:namespace create TMDB_CACHE
wrangler kv:namespace create RATE_LIMIT
```

Copy the returned IDs into `wrangler.toml`. Keep IDs in config, but keep real API keys out of config.

### 3. Set your TMDb API key

```bash
wrangler secret put TMDB_API_KEY
# Paste your key when prompted
```

### 4. Deploy

```bash
npm install
npm run deploy
```

You'll get a URL like `https://cinephile-proxy.your-subdomain.workers.dev`.

### 5. Point the app to it

In `app/src/main/java/com/thalos/cinephile/data/remote/TmdbApi.kt`, change:

```kotlin
// private const val BASE_URL = "https://api.themoviedb.org/3/"
private const val BASE_URL = "https://cinephile-proxy.your-subdomain.workers.dev/3/"
```

This beta keeps the existing Retrofit `apiKey` parameters for minimal Android churn; the Worker overwrites any incoming `api_key` query parameter with the server-side secret.

## Endpoints

| Path | Description |
|---|---|
| `GET /3/<tmdb-path>` | Proxied TMDb API call with caching |
| `GET /health` | Health check + edge colo info |

## Cache Behavior

- **Movie details / trending / popular**: 6 hours
- **Search queries**: 30 minutes
- **Cache hit header**: `X-Cache: HIT`

## Rate Limiting

- Sliding window: 10 seconds
- Max 35 requests per IP per window (conservative buffer under TMDb's 40 limit)
- Returns `429` with `Retry-After` header when exceeded

## Free Tier Limits

Cloudflare Workers free tier:
- **100,000 requests / day**
- **KV**: 100,000 reads, 1,000 writes, 1,000 deletes, 1,000 lists / day
- **Analytics Engine**: included

For a hobby movie app with a few hundred users, this is overkill.

## Monitoring

```bash
# Live logs
npm run tail

# Or view in Cloudflare Dashboard → Workers → cinephile-proxy → Analytics
```
