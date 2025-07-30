# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot-based WeChat RAG (Retrieval-Augmented Generation) agent system built with Java 21 and LangChain4j. The system consists of three main modules:

- **agent-datasync**: Data synchronization service for processing WeChat chat logs into vector embeddings
- **agent-core**: Core RAG functionality with embedding model management
- **agent-web**: Main web application that orchestrates the entire system

## Build Commands

```bash
# Build the entire project from root
mvn clean compile

# Build specific module
mvn clean compile -pl agent-datasync
mvn clean compile -pl agent-core
mvn clean compile -pl agent-web

# Run the main web application (includes all modules)
mvn spring-boot:run -pl agent-web

# Run the data sync service standalone
mvn spring-boot:run -pl agent-datasync

# Package the application
mvn clean package

# Skip tests during build
mvn clean package -DskipTests

# Run tests
mvn test

# Run from specific module directory
cd agent-web && mvn spring-boot:run
cd agent-datasync && mvn spring-boot:run
```

## Docker Commands

```bash
# Build Docker image
docker build -t wechat-rag-agent .

# Run with Docker Compose (production setup)
docker-compose up -d

# Build and run Docker images locally
cd docker && chmod +x build-docker-images.sh && ./build-docker-images.sh

# View service status
docker-compose ps

# View logs
docker-compose logs -f wechat-rag-agent

# Stop services
docker-compose down

# Stop and remove volumes
docker-compose down --volumes
```

## Multi-Module Architecture

### Module Structure
- **Root POM**: Parent aggregator with dependency management for LangChain4j 1.1.0
- **agent-web**: Main Spring Boot application (port 8080) that orchestrates the entire system
- **agent-datasync**: Standalone data ingestion service for WeChat chatlog vectorization
- **agent-core**: Shared library for embedding models and core RAG functionality

### Key Components

**agent-web module:**
- `WechatRagAgentApplication` - Main Spring Boot application entry point
- Orchestrates both data sync and RAG query functionality
- Depends on both agent-core and agent-datasync modules

**agent-datasync module:**
- `ChatlogApi` - Reactive WebClient for WeChat chatlog APIs with pagination and date filtering
- `VectorStoreFactory` - Factory pattern for Chroma/Elasticsearch vector stores using Java 21 switch expressions
- `ChatlogVectorService` - Reactive vectorization service with parallel processing and batch optimization
- `VectorStoreConfig` - Configuration properties with enum-based provider validation
- `IncrementalSyncService` - Redis-based incremental synchronization service
- `RedisSyncStateService` - Redis state management for sync checkpoints and deduplication
- `ProgressService` - Task progress tracking and monitoring

**agent-core module:**
- `EmbeddingModelFactory` - Factory for multiple embedding providers (HuggingFace, SiliconFlow)
- `SiliconflowEmbeddingModel` - Custom embedding model implementation with WebClient integration
- `EmbeddingConfig` - Configuration properties for embedding providers
- `RerankModelFactory` - Factory for reranking model providers (SiliconFlow, Local)
- `SiliconflowRerankModel` - Custom rerank model implementation
- `Assistant` - Core RAG assistant with content injection and retrieval
- `QueryParser` - Natural language query processing and intent recognition
- `TimeParser` - Temporal expression parsing for time-based queries
- `EnhancedContentRetriever` - Advanced content retrieval with reranking

## Configuration Patterns

### Main Application Configuration (agent-web/src/main/resources/application.yml)
```yaml
server:
  port: 8080

spring:
  application:
    name: wechat-rag-agent
  data:
    redis:
      host: localhost
      port: 6379

wechat:
  chatlog:
    base-url: http://127.0.0.1:5030

rag:
  parse-model:
    api-key: your-openrouter-api-key
    model: google/gemini-2.5-flash
  embedding:
    provider: siliconflow
    model: BAAI/bge-m3
    api-key: your-siliconflow-api-key
    base-url: https://api.siliconflow.cn/v1
  rerank:
    provider: siliconflow
    model: BAAI/bge-reranker-v2-m3
    api-key: your-siliconflow-api-key
    base-url: https://api.siliconflow.cn/v1
  vector-store:
    provider: elasticsearch
    url: http://localhost:9200
    collection-name: wechat_chatlog
```

### Vector Store Configuration (`rag.vector-store`)
```properties
rag.vector-store.provider=chroma|elasticsearch
rag.vector-store.url=http://localhost:8000
rag.vector-store.collection-name=wechat-logs
```

### Embedding Model Configuration (`rag.embedding`)
```properties
rag.embedding.provider=huggingface|siliconflow|local
rag.embedding.base-url=https://api.endpoint.url
rag.embedding.api-key=your-key
rag.embedding.model=model-name
```

### Rerank Model Configuration (`rag.rerank`)
```properties
rag.rerank.provider=siliconflow|local
rag.rerank.base-url=https://api.siliconflow.cn/v1
rag.rerank.api-key=your-key
rag.rerank.model=BAAI/bge-reranker-v2-m3
```

### Redis Configuration (Spring Data Redis)
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
          max-wait: 2000ms
```

## Key Dependencies

- **Spring Boot 3.5.3** with WebFlux for reactive programming
- **LangChain4j 1.1.0** with BOM for RAG functionality and vector operations
- **Java 21** with switch expressions and modern syntax
- **Project Lombok** for reducing boilerplate
- **Spring Data Redis Reactive** for Redis-based state management
- **Apache Commons Lang3** for utility functions
- **Reactor Netty** for HTTP client operations

## Development Patterns

### Reactive Programming
- Uses Mono/Flux throughout for non-blocking operations
- WebClient for HTTP calls with reactive streams
- Parallel processing with explicit Scheduler configuration (boundedElastic)
- Comprehensive parameter validation with meaningful error messages
- Advanced error handling with retry mechanisms and partial failure recovery

### Configuration Management
- `@ConfigurationProperties` pattern for type-safe configuration
- Enum-based provider validation with `fromValue()` methods
- Factory pattern for pluggable implementations

### Error Handling
- Input validation with `IllegalArgumentException` for API parameters
- Date format validation (YYYY-MM-DD or YYYY-MM-DD~YYYY-MM-DD)
- Provider validation with descriptive error messages

### Code Organization
- Clean separation between API clients, services, and configuration
- Factory pattern for multiple provider support
- Comprehensive logging with SLF4J

## Core Service Implementation Details

### ChatlogVectorService - Vectorization Pipeline

The core vectorization service implements a sophisticated reactive pipeline with both full and incremental sync capabilities:

#### Processing Flow
1. **Count Phase**: Gets total chatlog count for the specified talker and time range
2. **Pagination**: Calculates total pages and creates parallel processing streams
3. **Parallel Fetching**: Uses `parallel(4).runOn(Schedulers.boundedElastic())` for concurrent API calls
4. **Filtering**: Validates chatlogs (text messages only, non-empty content)
5. **Text Conversion**: Transforms ChatlogResponse to TextSegment with metadata
6. **Batch Processing**: Buffers segments (50 per batch) for efficient embedding generation
7. **Vector Storage**: Stores embeddings and segments in the configured vector store

#### Performance Optimizations
- **Pagination**: 200 records per page to balance memory usage and API efficiency
- **Concurrency**: 4 parallel streams for optimal throughput
- **Batch Size**: 50 items per embedding batch to optimize vector store writes
- **Error Recovery**: Individual page/batch failures don't stop the entire process
- **Retry Logic**: 3 retries for transient failures with exponential backoff

#### Configuration Constants
```java
private static final int DEFAULT_PAGE_SIZE = 200;
private static final int DEFAULT_BATCH_SIZE = 50;
private static final int DEFAULT_CONCURRENCY = 4;
```

### IncrementalSyncService - Redis-Based Incremental Sync

Built on Redis for high-performance state management and deduplication:

#### Key Features
- **Checkpoint Management**: Tracks last processed sequence number and sync time
- **Distributed Locking**: Prevents concurrent sync conflicts using Redis locks
- **Deduplication**: Uses Redis Sorted Sets to track processed records
- **Time Window Calculation**: Automatically calculates sync time ranges based on last checkpoint
- **Fault Tolerance**: Supports resuming from failures with checkpoint state

#### Redis Data Structures
```redis
sync:checkpoint:{talker}    # Hash: sync checkpoint info
sync:processed:{talker}     # Sorted Set: processed seq numbers
sync:lock:{talker}         # String: distributed lock
```

## Module Dependencies
- **agent-web**: Main application, depends on both agent-core and agent-datasync
- **agent-datasync**: Depends on agent-core for embedding functionality
- **agent-core**: Standalone shared library

## Security & Production Notes

⚠️ **SECURITY ALERT**: 
- API keys should use environment variables (see `.env.example` template)
- Never commit real API keys to version control  
- Docker Compose includes example keys that should be replaced
- Consider using Spring Cloud Config or HashiCorp Vault for production

## API Endpoints

**agent-web** exposes REST endpoints for:

### Vectorization APIs
- `POST /api/vectorization/chatlog/stream` - SSE endpoint for real-time vectorization progress
  - Request body: `VectorizationRequest` with `talker` and `time` fields
  - Returns: Server-Sent Events stream with progress updates
- `DELETE /api/vectorization/progress/{taskId}` - Clean up completed task progress

### Chat APIs  
- `GET /api/chat` - Streaming chat with RAG capabilities
  - Query parameters: `question`, `modelName`, `apiKey`
  - Returns: JSON streaming responses compatible with OpenAI format

## Testing

- **Core Tests**: `agent-web/src/test/java/com/wechat/rag/core/agent/query/TimeParserTest.java` - Comprehensive time expression parsing tests
- **Integration Tests**: `agent-web/src/test/java/com/wechat/rag/web/chatlog/ChatlogApiTest.java` - Currently commented out
- **Run Tests**: `mvn test` (active test suite for TimeParser functionality)
- **Test Coverage**: TimeParser has comprehensive unit tests covering edge cases and boundary conditions

## External Dependencies

- **WeChat API**: WeChat chatlog API at `http://127.0.0.1:5030` (configurable via `wechat.chatlog.base-url`)
- **Vector Store**: Elasticsearch at `localhost:9200` or Chroma at `localhost:8000`
- **Embedding API**: SiliconFlow at `https://api.siliconflow.cn/v1` for embeddings and reranking
- **LLM API**: OpenRouter API for chat completion (configured via `rag.parse-model`)
- **Redis**: Required for incremental sync state management at `localhost:6379`

## Environment Configuration

Use `.env.example` as a template to create your `.env` file:

```bash
cp .env.example .env
```

Key environment variables:
- `OPENROUTER_API_KEY`: OpenRouter API key for LLM services
- `SILICONFLOW_API_KEY`: SiliconFlow API key for embedding/rerank services
- `WECHAT_API_HOST`: WeChat API service URL (default: `http://host.docker.internal:5030`)
- `SPRING_PROFILES_ACTIVE`: Active Spring profile (`docker`/`dev`/`prod`)