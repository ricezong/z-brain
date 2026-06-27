# AGENTS.md — Z-Brain

## Project Overview

Z-Brain is an enterprise RAG knowledge-base Q&A system: Spring Boot 3.4.5 + Spring AI 1.1.8 backend, Vue 3 + Element Plus frontend. Three-path hybrid retrieval (vector/fulltext/fuzzy) with RRF fusion + Rerank reranking.

## Build & Run

```bash
# Backend (JDK 17, Maven 3.8+)
mvn clean package -DskipTests          # produces target/z-brain.jar

# Frontend (Node 18+)
cd frontend && npm install && npm run build

# Docker Compose (full stack, needs .env with API keys)
docker compose up -d --build
```

No `mvn test` by default — tests require live PG + Redis + DashScope API. Use `-DskipTests` unless verifying integration.

## Key Commands

| What | Command |
|------|---------|
| Compile only | `mvn clean compile` |
| Type-check frontend | `cd frontend && npx vue-tsc --noEmit` |
| Lint frontend | `cd frontend && npm run lint` |
| Dev server (frontend) | `cd frontend && npm run dev` (port 3000, proxies /api to :8080) |
| Dev server (backend) | `mvn spring-boot:run` (port 8080, context-path `/api`) |
| Init DB | `psql -d zbrain -f src/main/resources/db/schema.sql` |
| API docs | `http://localhost:8080/api/doc.html` (Knife4j) |
| Health check | `curl http://localhost:8080/api/actuator/health` |

## Architecture

### Backend (`src/main/java/cn/kong/zbrain/`)

- **Package root**: `cn.kong.zbrain` (not `com.zbrain` — pom says `cn.kong`)
- **ORM**: MyBatis with **all SQL in XML** (`src/main/resources/mapper/*.xml`), no annotation-based SQL
- **Mapper interfaces**: `mapper/` — pure interfaces, SQL lives in XML
- **Service pattern**: interface in `service/`, impl in `service/impl/`
- **Retrieval**: pluggable retrievers in `retrieval/` (VectorRetriever, FullTextRetriever, FuzzyRetriever), fused by RRFFusion
- **Chunking**: `chunk/` — parent/child split (1000/200 tokens)
- **LLM**: `llm/` — wraps Spring AI OpenAI chat (backed by DeepSeek)
- **Embedding**: via DashScope SDK directly (not Spring AI), in `service/impl/EmbeddingServiceImpl.java`
- **Cache layer**: `cache/` — Redis wrappers for doc progress, chat context, embedding cache
- **Config**: `config/ZBrainProperties.java` binds `zbrain.*` from application.yml

### Frontend (`frontend/src/`)

- Vue 3 + TypeScript + Element Plus (auto-imported via unplugin)
- Pinia stores, Vue Router, Axios for HTTP
- `@` alias → `src/`
- Vite dev server proxies `/api` to `localhost:8080`

### Database

- PostgreSQL 14+ with extensions: `vector`, `pg_trgm`, `zhparser`, `uuid-ossp`
- Tables prefixed `kb_*` (knowledge_base, document, chunk, chat_session, chat_log, prompt_template)
- `kb_chunk` is the core table: stores parent/child chunks with vector, tsv, content fields
- Chinese full-text search uses `zhparser` → `zh_cn` text search config
- Schema: `src/main/resources/db/schema.sql`

## Environment Variables

Required (see `docs/配置文件清单.md` for full list):

| Variable | Purpose |
|----------|---------|
| `DASHSCOPE_API_KEY` | Alibaba Cloud Bailian (embedding + rerank) |
| `OPENAI_API_KEY` | DeepSeek API key (chat LLM) |
| `OPENAI_BASE_URL` | `https://api.deepseek.com` |
| `PG_HOST`, `PG_PORT`, `PG_DB`, `PG_USER`, `PG_PASSWORD` | PostgreSQL connection |
| `REDIS_HOST`, `REDIS_PORT` | Redis connection |

Profiles: `dev` (default, hardcoded remote PG/Redis), `prod` (env vars). See `application-dev.yml` / `application-prod.yml`.

## Critical Quirks

1. **MyBatis SQL is all XML** — never put SQL in `@Select`/`@Update` annotations; edit `src/main/resources/mapper/*.xml` instead.
2. **Embedding uses DashScope SDK directly**, not Spring AI's vectorstore abstraction. The `spring.ai.vectorstore.pgvector` config exists but the actual embedding writes go through `EmbeddingServiceImpl` → DashScope SDK → manual `ChunkMapper.batchUpdateVector`.
3. **Chunk status lifecycle**: `draft` → `active` (after embedding). Retrieval filters on `status = 'active'`. A known bug: `batchUpdateVector` must also update `status` field, not just `content_vector`. See `docs/常见问题.md` §5.
4. **`isChitchat()` threshold**: keyword-length heuristic for intent detection. Was too aggressive (15 chars), fixed to 6. If retrieval returns empty, check if query is being misclassified as chitchat.
5. **SSE streaming**: chat endpoint uses Server-Sent Events. Nginx proxy must disable buffering (`proxy_buffering off`). Read timeout should be 300s+.
6. **`Map.of()` NPE risk**: `Map.of()` rejects null values. Use `HashMap` when building response maps from retrieval results (score/citationLabel may be null).
7. **`marked` library**: `marked(content)` returns `string | Promise<string>`. Use `marked.parse(content) as string` for sync rendering.
8. **Dual thread pools**: `ThreadPoolConfig` defines separate pools for document parsing and embedding vectorization — don't merge them.
9. **Frontend build**: `npm run build` runs `vue-tsc --noEmit` first — TypeScript errors block the build.
10. **Server context-path**: all backend APIs are under `/api` prefix (configured in `server.servlet.context-path`). Frontend proxies `/api` → backend.

## Code Style

- Java: 4-space indent, 120-char line width, Lombok everywhere (`@Data`, `@Slf4j`, `@RequiredArgsConstructor`)
- All SQL in MyBatis XML mappers — `mapper-locations: classpath:mapper/*.xml`
- Entity package: `cn.kong.zbrain.entity`, DTO: `cn.kong.zbrain.dto.request/response`
- Response wrapper: `cn.kong.zbrain.common.Result<T>`
- Exception: `cn.kong.zbrain.common.BusinessException`
- Git commits: `<type>(<scope>): <subject>` — types: feat, fix, docs, style, refactor, test, chore

## What NOT to Do

- Don't use `@Select`/`@Insert` annotation SQL — all SQL goes in XML mappers
- Don't use `Map.of()` with potentially null values
- Don't assume Spring AI handles embedding — it's DashScope SDK
- Don't change chunk status logic without understanding the `draft`→`active` lifecycle
- Don't commit secrets — `.env`, `application.yml` with keys are in `.gitignore`
- Don't skip `vue-tsc --noEmit` before frontend build — it catches type errors
