# Banking RAG System

A Retrieval-Augmented Generation (RAG) architecture built with Java 17, Spring Boot 3, PostgreSQL, and pgvector for banking data.

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐     ┌───────────┐
│  REST API   │────▶│  RAG Engine  │────▶│  Vector Store   │────▶│ PostgreSQL│
│  Controller │     │              │     │  (pgvector)     │     │ + pgvector│
└─────────────┘     │  1. Embed    │     └─────────────────┘     └───────────┘
                    │  2. Retrieve │
                    │  3. Generate │     ┌─────────────────┐
                    │              │────▶│  LLM Service    │
                    └──────────────┘     │  (Mock/OpenAI/  │
                                         │   Ollama)       │
                                         └─────────────────┘
```

### Data Flow
1. **Synthetic Data Generation** → 10K customers, ~25K accounts, ~500K+ transactions
2. **Document Indexing** → Customer profiles, account summaries, transaction patterns, and banking policies are converted to embeddings and stored in pgvector
3. **Query Processing** → User query is embedded → cosine similarity search retrieves top-k documents → LLM generates answer from context

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Database | PostgreSQL 16 + pgvector |
| Migrations | Flyway |
| Embeddings | Local (hash-based) or OpenAI |
| LLM | Mock, OpenAI, or Ollama |
| Build | Maven |
| Container | Docker Compose |

## Quick Start

### 1. Start PostgreSQL with pgvector
```bash
docker-compose up -d
```

### 2. Build and run the application
```bash
./mvnw spring-boot:run
```
The app automatically generates 10K customers with accounts/transactions and indexes everything into the vector store on first startup.

### 3. Query the RAG system
```bash
# Simple query
curl "http://localhost:8080/api/rag/ask?q=What+is+the+overdraft+policy"

# Query with JSON body
curl -X POST http://localhost:8080/api/rag/query \
  -H "Content-Type: application/json" \
  -d '{"query": "Show me high-risk customers with credit scores below 600"}'

# Filter by source type
curl "http://localhost:8080/api/rag/ask?q=spending+patterns&sourceType=TRANSACTION_PATTERN"

# Check system status
curl http://localhost:8080/api/admin/status
```

## API Endpoints

### RAG Queries
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/rag/query` | Query with JSON body (query, sessionId, sourceType) |
| GET | `/api/rag/ask?q=...` | Simple query via URL parameter |

### Admin
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/status` | System status and data counts |
| POST | `/api/admin/generate` | Generate synthetic data |
| POST | `/api/admin/index` | Index data into vector store |
| POST | `/api/admin/reindex` | Re-index (clears and rebuilds) |
| POST | `/api/admin/setup` | Full setup (generate + index) |

## Configuration

### LLM Providers

The system supports three LLM providers:

1. **Mock** (default) - Formatted context display, no API needed
2. **OpenAI** - Production-quality natural language responses
3. **Ollama** - Free local LLM

### Using OpenAI (Recommended for Production)

For natural language answers, configure OpenAI:

```bash
# 1. Get API key from https://platform.openai.com/api-keys
# 2. Create .env file
cp .env.example .env

# 3. Add your key to .env
echo "OPENAI_API_KEY=sk-proj-your-key-here" >> .env

# 4. Start with OpenAI enabled
./start-with-openai.sh
```

**See [OPENAI_SETUP.md](OPENAI_SETUP.md) for detailed configuration guide.**

The application.yml is already configured to use OpenAI:
```yaml
rag:
  llm:
    provider: openai
    openai-api-key: ${OPENAI_API_KEY}
    openai-model: gpt-4o-mini
```

### Using OpenAI Embeddings (Optional)

For even better semantic search, use OpenAI embeddings:
```yaml
rag:
  embedding:
    provider: openai
    openai-api-key: ${OPENAI_API_KEY}
    openai-model: text-embedding-3-small
```

Note: This requires re-indexing all data.

### Using Ollama (local LLM)
```bash
# Uncomment ollama in docker-compose.yml, then:
docker-compose up -d
docker exec bankrag-ollama ollama pull llama3
```
```yaml
rag:
  llm:
    provider: ollama
    ollama-model: llama3
```

## Example Queries
- "What is the overdraft protection policy?"
- "Show me customers in the PRIVATE_BANKING segment"
- "What are the interest rates for savings accounts?"
- "Find transaction patterns for high-spending accounts"
- "What credit score is needed for a mortgage?"
- "Explain the wire transfer fees"
- "Show me credit card rewards information"

## Project Structure
```
banking-rag/
├── docker-compose.yml
├── pom.xml
└── src/main/
    ├── java/com/bankrag/
    │   ├── BankingRagApplication.java
    │   ├── config/          # Startup runner
    │   ├── controller/      # REST endpoints
    │   ├── generator/       # Synthetic data generation
    │   ├── model/           # JPA entities
    │   ├── rag/             # RAG engine
    │   ├── repository/      # Data access + vector store
    │   └── service/         # Embedding + LLM services
    └── resources/
        ├── application.yml
        └── db/migration/    # Flyway SQL migrations
```
