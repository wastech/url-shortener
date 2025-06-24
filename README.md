# URL Shortener 

A high-performance, scalable URL shortening service built with Spring Boot. This service provides fast URL shortening and retrieval with Redis caching, Kafka-based asynchronous processing, and PostgreSQL persistence.

## 🚀 Features

- **Fast URL Shortening**: Generate short codes for long URLs with sub-second response times
- **Redis Caching**: Intelligent caching layer for optimal performance
- **Duplicate Detection**: Prevents duplicate entries for the same long URL
- **Asynchronous Processing**: Kafka-based event-driven architecture for scalability
- **Retry Logic**: Robust error handling with exponential backoff
- **Transaction Support**: ACID compliance with PostgreSQL
- **Docker Support**: Fully containerized deployment

## 🏗️ Architecture

The service follows a layered architecture with the following components:

```
┌─────────────────┐    ┌──────────────┐    ┌─────────────────┐
│   Client API    │───▶│ Spring Boot  │───▶│ Key Generation  │
└─────────────────┘    │   Service    │    │    Service      │
                       └──────┬───────┘    └─────────────────┘
                              │
                    ┌─────────┼─────────┐
                    ▼         ▼         ▼
            ┌──────────┐ ┌─────────┐ ┌──────────┐
            │  Redis   │ │  Kafka  │ │PostgreSQL│
            │  Cache   │ │ Message │ │ Database │
            └──────────┘ │  Queue  │ └──────────┘
                         └─────────┘
```

## 🛠️ Technology Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Backend** | Java 17 + Spring Boot | Main application framework |
| **Database** | PostgreSQL | Persistent storage for URL mappings |
| **Cache** | Redis | High-speed caching layer |
| **Message Queue** | Apache Kafka | Asynchronous processing |
| **Coordination** | Apache ZooKeeper | Kafka cluster coordination |
| **Containerization** | Docker | Application packaging and deployment |

## 📋 Prerequisites

- Docker and Docker Compose
- Java 17 or higher (for local development)
- Maven 3.6+ (for local development)

## 🚀 Quick Start

### Using Docker Compose (Recommended)

1. **Clone the repository**
   ```bash
   git clone https://github.com/wastech/url-shortener.git
   cd url-shortener
   ```

2. **Start all services**
   ```bash
   docker-compose up -d
   ```

3. **Verify services are running**
   ```bash
   docker-compose ps
   ```

The application will be available at `http://localhost:8080`

### Local Development Setup

1. **Start infrastructure services**
   ```bash
   docker-compose up -d postgres redis kafka zookeeper
   ```

2. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

## 🔧 Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/urlshortener` | PostgreSQL connection URL |
| `REDIS_HOST` | `localhost` | Redis server host |
| `REDIS_PORT` | `6379` | Redis server port |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker addresses |
| `CACHE_TTL_SECONDS` | `3600` | Redis cache TTL in seconds |

### Docker Compose Services

```yaml
services:
  - app: Spring Boot application (port 8080)
  - postgres: PostgreSQL database (port 5432)
  - redis: Redis cache (port 6379)
  - kafka: Apache Kafka (port 9092)
  - zookeeper: ZooKeeper coordination (port 2181)
```

## 📡 API Endpoints

### Shorten URL
```http
POST /api/shorten
Content-Type: application/json

{
  "longUrl": "https://www.example.com"
}
```

**Response:**
```json
{
  "shortCode": "00008Ck",
}
```

### Retrieve Original URL
```http
GET /{shortCode}
```

**Response:** Redirects to the original long URL

### Get URL Information
```http
GET /api/url/{shortCode}
```

**Response:**
```json
{
  "shortCode": "00008Ck",
  "longUrl": "https://example/00008Ck",
  "createdAt": "2025-06-13T10:30:00Z"
}
```

## 🔄 How It Works

### URL Shortening Flow

1. **Duplicate Check**: Service checks if the long URL already exists
2. **Key Generation**: Unique short code generated via Key Generation Service
3. **Async Persistence**: URL mapping published to Kafka topic
4. **Caching**: Mapping cached in Redis for fast retrieval
5. **Response**: Short code returned to client

### URL Retrieval Flow

1. **Cache Check**: Redis cache checked first for O(1) lookup
2. **Database Fallback**: If cache miss, query PostgreSQL database
3. **Cache Population**: Retrieved data cached for future requests
4. **Redirect**: Client redirected to original URL

### Resilience Features

- **Retry Logic**: Failed Kafka operations retried with exponential backoff
- **Circuit Breaker**: Graceful degradation on service failures
- **Transaction Management**: ACID compliance for data consistency
- **Caching Strategy**: Multi-level caching for performance optimization

## 🧪 Testing

### Run Tests
```bash
./mvnw test
```

### Integration Tests
```bash
./mvnw integration-test
```

### Load Testing
```bash
# Using curl for basic testing
curl -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://example.com"}'
```

## 📊 Monitoring

### Health Checks
- Application: `http://localhost:8080/actuator/health`
- Database: `http://localhost:8080/actuator/health/db`
- Redis: `http://localhost:8080/actuator/health/redis`

### Metrics
- Prometheus metrics: `http://localhost:8080/actuator/prometheus`
- Application metrics: `http://localhost:8080/actuator/metrics`

## 🐳 Docker Commands

```bash
# Build application image
docker build -t url-shortener:latest .

# Start all services
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop services
docker-compose down

# Rebuild and restart
docker-compose up -d --build
```

## 🔧 Development

### Project Structure
```
src/
├── main/java/com/wastech/url_shortener/
│   ├── controller/     # REST controllers
│   ├── service/        # Business logic
│   ├── repository/     # Data access layer
│   ├── model/          # Entity classes
│   └── config/         # Configuration classes
├── main/resources/
│   ├── application.yml # Application configuration
│   └── db/migration/   # Database migrations
└── test/               # Unit and integration tests
```

### Building from Source
```bash
# Clean build
./mvnw clean package

# Skip tests
./mvnw clean package -DskipTests

# Build Docker image
docker build -t url-shortener .
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Spring Boot team for the excellent framework
- Redis Labs for the blazing-fast cache
- Apache Kafka for reliable messaging
- PostgreSQL for robust data persistence

## 📞 Support

For questions and support:
- Create an issue on GitHub
- Contact: [fataiwasiu2@gmail.com]
- Documentation: [Wiki](https://github.com/wastech/url-shortener)

---

⭐ **Star this repository if you find it helpful!**
