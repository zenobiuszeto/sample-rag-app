# Quick Start Guide: Banking RAG with OpenAI

Follow these steps to get your Banking RAG system up and running with OpenAI for natural language responses.

## Prerequisites

- Java 17 or higher
- Maven
- Docker Desktop (for PostgreSQL)
- OpenAI API account

## Step-by-Step Setup

### 1. Start PostgreSQL Database

```bash
cd /Users/zenobiusselvadhason/work/project/Zeto/banking-rag
docker-compose up -d
```

Wait ~10 seconds for PostgreSQL to initialize.

### 2. Get Your OpenAI API Key

1. Go to https://platform.openai.com/api-keys
2. Sign in or create an account
3. Click "Create new secret key"
4. Copy the key (starts with `sk-proj-...`)

### 3. Configure API Key

```bash
# Create .env file from template
cp .env.example .env

# Edit .env and add your key
nano .env
```

Replace `your-openai-api-key-here` with your actual key:
```
OPENAI_API_KEY=sk-proj-xxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

Save and exit (Ctrl+O, Enter, Ctrl+X in nano).

### 4. Start the Application

```bash
# Option A: Use the startup script (loads .env automatically)
./start-with-openai.sh

# Option B: Export key manually
export OPENAI_API_KEY="sk-proj-your-key"
mvn spring-boot:run
```

### 5. Wait for Data Generation

On first startup, the application will:
- Generate 100,000 synthetic customers
- Create ~250,000 accounts
- Generate ~6,800,000 transactions
- Index 100,000 documents into vector store

This takes **5-10 minutes**. Watch the logs for:
```
Synthetic data generated successfully
Indexing complete in XXXms
Application started
```

### 6. Open the Web UI

Open your browser to:
```
http://localhost:8080
```

You should see the Banking RAG Assistant interface with:
- System status showing "Connected"
- Customer/Account/Transaction counts
- Sample queries on the left sidebar

### 7. Test with Queries

Click any sample query or type your own:

**Example queries:**
- "Which customers are from New York?"
- "Show me high-risk customers with low credit scores"
- "What is the overdraft protection policy?"
- "Find accounts with high balances"
- "What are the top spending categories?"

### 8. View Results

After each query, you'll see:
- **Natural language answer** from OpenAI (not mock format!)
- **Source documents** with similarity scores
- **Query metrics** in the right panel (if visible)

## Verification Checklist

✅ PostgreSQL running: `docker ps | grep postgres`  
✅ Application started: Check logs for "Started BankingRagApplication"  
✅ Data generated: `curl http://localhost:8080/api/admin/status`  
✅ OpenAI configured: Response should be natural language, not "Mock LLM"  

## Troubleshooting

### "Port 8080 already in use"
```bash
# Find and kill the process
lsof -i :8080
kill -9 <PID>
```

### "OPENAI_API_KEY is not set"
```bash
# Verify .env file exists and has correct format
cat .env

# Make sure no spaces around = sign
# Correct:   OPENAI_API_KEY=sk-proj-xxx
# Incorrect: OPENAI_API_KEY = sk-proj-xxx
```

### "Error generating response: Unauthorized"
- Your API key is incorrect or expired
- Generate a new key at https://platform.openai.com/api-keys

### Mock responses still appearing
- Restart the application after setting OPENAI_API_KEY
- Check application.yml has `provider: openai` under `rag.llm`

## Next Steps

- **Improve results**: Reindex with better embeddings (see OPENAI_SETUP.md)
- **Query optimization**: Adjust `similarity-threshold` in application.yml
- **Monitor costs**: Check usage at https://platform.openai.com/usage
- **Add more data**: Use `/api/admin/generate` endpoint

## Cost Estimation

With gpt-4o-mini (default):
- ~$0.001 per query
- 1,000 queries ≈ $1.00
- Very cost-effective for development and testing

---

**Need help?** See [OPENAI_SETUP.md](OPENAI_SETUP.md) for detailed configuration options.

