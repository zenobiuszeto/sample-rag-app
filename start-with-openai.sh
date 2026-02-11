#!/bin/bash

# Banking RAG Application Startup Script
# This script loads environment variables and starts the Spring Boot application

# Load environment variables from .env file if it exists
if [ -f .env ]; then
    echo "Loading environment variables from .env file..."
    export $(cat .env | grep -v '^#' | xargs)
else
    echo "Warning: .env file not found!"
    echo "Please create a .env file with your OPENAI_API_KEY"
    echo "Example: cp .env.example .env"
    echo ""
fi

# Check if OpenAI API key is set
if [ -z "$OPENAI_API_KEY" ]; then
    echo "ERROR: OPENAI_API_KEY is not set!"
    echo ""
    echo "To configure OpenAI:"
    echo "1. Get your API key from: https://platform.openai.com/api-keys"
    echo "2. Create a .env file: cp .env.example .env"
    echo "3. Edit .env and add your API key: OPENAI_API_KEY=sk-proj-..."
    echo "4. Run this script again"
    echo ""
    exit 1
fi

echo "Starting Banking RAG Application with OpenAI LLM..."
echo "OpenAI API Key: ${OPENAI_API_KEY:0:10}...${OPENAI_API_KEY: -4}"
echo ""

# Start the Spring Boot application
mvn spring-boot:run

