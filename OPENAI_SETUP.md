# OpenAI Configuration Guide

This guide explains how to configure OpenAI as the LLM provider for natural language answers in the Banking RAG system.

## Quick Setup

### 1. Get Your OpenAI API Key

1. Visit [OpenAI Platform](https://platform.openai.com/api-keys)
2. Sign in or create an account
3. Click "Create new secret key"
4. Copy the key (it starts with `sk-proj-...`)

### 2. Configure the API Key

**Option A: Using .env file (Recommended)**

```bash
# Copy the example file
cp .env.example .env

# Edit .env and add your API key
nano .env  # or use your preferred editor
```

Add this line to `.env`:
```
OPENAI_API_KEY=sk-proj-your-actual-key-here
```

**Option B: Set environment variable directly**

```bash
export OPENAI_API_KEY="sk-proj-your-actual-key-here"
```

### 3. Start the Application

**With the startup script (loads .env automatically):**
```bash
./start-with-openai.sh
```

**Or manually:**
```bash
export OPENAI_API_KEY="sk-proj-your-key"
mvn spring-boot:run
```

## Configuration Details

The OpenAI LLM configuration is in `src/main/resources/application.yml`:

```yaml
rag:
  llm:
    provider: openai              # LLM provider (openai, ollama, or mock)
    openai-api-key: ${OPENAI_API_KEY:}  # API key from environment
    openai-model: gpt-4o-mini     # OpenAI model to use
```

### Available Models

- `gpt-4o-mini` (default) - Fast, cost-effective, high quality
- `gpt-4o` - Most capable, higher cost
- `gpt-4-turbo` - Good balance of speed and capability
- `gpt-3.5-turbo` - Fastest, lowest cost

### Cost Estimates (as of Feb 2026)

**gpt-4o-mini:**
- Input: $0.150 per 1M tokens
- Output: $0.600 per 1M tokens
- ~$0.001 per query (typical banking RAG query)

**gpt-4o:**
- Input: $5.00 per 1M tokens
- Output: $15.00 per 1M tokens
- ~$0.02 per query

## Testing the Configuration

Once configured, test with a query:

```bash
curl -X POST http://localhost:8080/api/rag/query \
  -H "Content-Type: application/json" \
  -d '{"query": "What is the overdraft protection policy?"}'
```

You should see natural language responses instead of the mock format.

## Switching Between Providers

Edit `application.yml`:

```yaml
# Use OpenAI
provider: openai

# Use local Ollama (free, runs locally)
provider: ollama

# Use mock (no LLM, just formatted context)
provider: mock
```

## Troubleshooting

### "Error generating response: Unauthorized"
- Check your API key is correct
- Verify the key has sufficient credits
- Ensure the key hasn't expired

### "Error generating response: Rate limit exceeded"
- You've exceeded OpenAI's rate limits
- Wait a moment and try again
- Consider upgrading your OpenAI plan

### "Error generating response: Insufficient credits"
- Add credits to your OpenAI account
- Go to [OpenAI Billing](https://platform.openai.com/account/billing)

## Security Best Practices

1. **Never commit API keys to git**
   - `.env` is in `.gitignore`
   - Use environment variables in production

2. **Rotate keys regularly**
   - Generate new keys every 90 days
   - Revoke old keys

3. **Use separate keys for dev/prod**
   - Different keys for different environments
   - Easier to track usage and rotate

4. **Monitor usage**
   - Check [OpenAI Usage](https://platform.openai.com/account/usage)
   - Set up billing alerts

## Alternative: Use Ollama (Free Local LLM)

If you don't want to use OpenAI, you can use Ollama for free:

```bash
# Install Ollama
brew install ollama

# Pull a model
ollama pull llama3

# Start Ollama service
ollama serve

# Update application.yml
provider: ollama
```

No API key needed - runs entirely on your machine!

