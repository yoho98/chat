# Sokind Backend 사전 과제 — 1:1 실시간 채팅 + 이벤트 기반 상태 복원

> 평가 비중: 동작하는 구현 + 운영 설계 역량 (정합성·확장성·장애대응·관측가능성). 코드 + 문서.
>
> **모든 설계 근거·트레이드오프·런북은 단일 통합 문서 [`docs/design.md`](./docs/design.md)** 에 있음. 분리 문서들은 드리프트 방지를 위해 제거됨.

## 한 줄 요약

```bash
docker compose up -d mysql            # MySQL 8.0 on :3307
./gradlew bootRun                     # Spring Boot on :8090

# REST 직접 호출
curl -X POST http://localhost:8090/sessions

# Demo UI (두 탭으로 실시간 송수신 시연 + 운영/디버깅 카드 포함)
open http://localhost:8090/demo

# OpenAPI / Swagger UI
open http://localhost:8090/swagger-ui/index.html

# 커스텀 메트릭
curl -s http://localhost:8090/actuator/prometheus | grep '^sokind_'
```

## 주요 의사결정

| 항목 | 선택 | 근거 |
|---|---|---|
| 언어/프레임워크 | **Spring Boot 4.0.6 + Kotlin 2.2.21**, Java 21 | 트랜잭션 명확성·디버깅 용이. 1:1 채팅 규모에서 WebFlux 의 backpressure 이점 < 운영 친화성 |
| 실시간 | **native Spring WebSocket** (STOMP 없음) + JSON | 1:1 단순 스키마. STOMP 오버헤드 불필요 |
| DB | **MySQL 8.0** + `VARCHAR(36) CHARACTER SET ascii` UUID | RDS/Aurora 보편성, `INSERT IGNORE` + UNIQUE 멱등성. 운영 가시성 위해 UUID 는 사람이 읽을 수 있는 VARCHAR |
| ORM | JPA(Hibernate 7) + `@JdbcTypeCode(SqlTypes.VARCHAR)` + Hibernate native JSON | Hibernate 7 가 `@Id + AttributeConverter` 금지 → JdbcTypeCode 통일 |
| Jackson | **`tools.jackson`** (Spring Boot 4 의 Jackson 3.x) + `jackson-module-kotlin` | Spring Boot 4 표준 |
| 비동기 | **Hybrid Outbox** — `@TransactionalEventListener(AFTER_COMMIT) + @Async` (immediate) + `@Scheduled(5s)` (catch-up) + DLQ (status machine) | latency ~0 + 견고성 동시 충족. 외부 큐 없음. 자세히 [`docs/design.md §4`](./docs/design.md#4-비동기-파이프라인-hybrid-outbox) |
| 인증 | `X-User-Id` 헤더 | 비목표 (인증 완결성). 운영 시 OIDC/JWT 도입 경로는 [`docs/design.md §12`](./docs/design.md#12-명시적-트레이드오프) |
| 패키지 | 단일 모듈 + 계층 (`domain / api / realtime / projection / infrastructure / support`) | 빠른 구현 + 경계 가시성 |
| 복원 | **Snapshot + Delta** (자동 선택, full replay fallback) | `TimelineService.replay` 가 두 모드 자동 선택. 정합성 + 성능 동시 |
| 관측 | Spring Actuator + Micrometer + **Prometheus** + 커스텀 메트릭 5종 (`sokind.*`) | `/actuator/prometheus` 노출. 자세히 [`docs/design.md §9`](./docs/design.md#9-관측-가능성) |
| 부하 테스트 | **k6** + docker-compose | 결과 [`docs/design.md §11`](./docs/design.md#11-성능-측정-k6) |
| 테스트 | JUnit 5 + **mockk** (단위) + **Testcontainers MySQL** (통합) | controller 테스트는 의도적으로 제외 |

## 디렉토리 한 눈에

```
.
├── ASSIGNMENT.md                # 원본 과제 (불변)
├── README.md                    # ← 여기 (entry point)
├── build.gradle                 # Spring Boot 4 + Kotlin + Actuator + Prometheus
├── docker-compose.yml           # MySQL 8.0 (port 3307)
├── docs/
│   ├── design.md                # ★ 통합 설계 문서 (12 섹션)
│   ├── openapi.yaml             # OpenAPI 스펙
│   └── openapi.json
├── http/
│   ├── http-client.env.json     # IntelliJ HTTP Client env
│   ├── day1.http                # 시나리오 검증 (REST, EDIT/DELETE/404/409 포함)
│   └── k6/load-ingest.js        # k6 부하 시나리오
└── src/
    ├── main/kotlin/com/sokind/chat/
    │   ├── ChatApplication.kt
    │   ├── domain/
    │   │   ├── session/         # Session, Status, Service, Repository
    │   │   ├── participant/     # Participant, Presence, Repository
    │   │   ├── event/           # DomainEvent, EventType, EventIngestService, Repository
    │   │   └── timeline/        # Timeline, TimelineService (fold)
    │   ├── api/
    │   │   ├── session/         # SessionController, ParticipantSummary
    │   │   ├── event/           # EventController (ingest + list), EventListResponse
    │   │   ├── timeline/        # TimelineController
    │   │   ├── snapshot/        # SnapshotController, SnapshotResponse
    │   │   └── demo/            # DemoController + templates/chat.html
    │   ├── realtime/
    │   │   ├── WebSocketConfig.kt
    │   │   ├── ChatWebSocketHandler.kt
    │   │   └── presence/PresenceTracker.kt
    │   ├── projection/
    │   │   ├── outbox/          # OutboxEntry, Status, Repository, OutboxAppended
    │   │   ├── worker/          # ProjectionService, ProjectionWorker, ProjectionEventListener (Hybrid)
    │   │   └── snapshot/        # Snapshot, Repository, SnapshotService, SnapshotEventListener
    │   ├── infrastructure/
    │   │   ├── jpa/UuidBytes.kt
    │   │   ├── async/AsyncConfig.kt           # projectionExecutor (proj-*)
    │   │   ├── metrics/ProjectionMetrics.kt   # sokind.* 5종
    │   │   └── openapi/OpenApiConfig.kt
    │   └── support/error/       # ApiError, GlobalExceptionHandler
    └── test/kotlin/com/sokind/chat/
        ├── domain/timeline/TimelineServiceUnitTest.kt   # mockk 단위
        └── integration/Persistence.kt                    # Testcontainers MySQL 통합
```

## 실행

### 1) MySQL

```bash
docker compose up -d mysql
docker compose ps    # sokind-mysql 가 healthy 까지 대기
```

### 2) 앱

```bash
./gradlew bootRun                                 # 기본 port 8090
SERVER_PORT=9090 ./gradlew bootRun                # port override
```

`application.yml` 주요 기본값:
- DB: `localhost:3307`, user `sokind`, db `sokind_chat`
- Snapshot 임계값: 5 events (데모용. 운영은 50 권장)
- **Projection**: Hybrid — `tick-ms=5000` (catch-up) + `immediate=true` (AFTER_COMMIT listener)

### 3) 테스트 + 커버리지

```bash
./gradlew test                  # unit + integration (Testcontainers MySQL 자동 기동)
./gradlew jacocoTestReport      # build/reports/jacoco/test/html/index.html
SONAR_HOST_URL=... SONAR_TOKEN=... ./gradlew sonar
```

- **단위**: `TimelineServiceUnitTest` (mockk) — fold 결정성, 이벤트별 규칙, 순서 규칙
- **통합**: `Persistence` (Testcontainers MySQL 8.0) — idempotency, outbox→projection 드레인, snapshot 임계값, 트랜잭션 원자성, 세션 필터 등 7 케이스
- 컨트롤러 테스트는 의도적 제외

핵심 도메인 70~100% 커버. 컨트롤러/WS 외곽은 의도적 미커버.

### 4) 검증 경로

| 도구 | 용도 |
|---|---|
| `http/day1.http` | IntelliJ/VS Code REST Client. idempotency·EDIT·DELETE·404·409 |
| Swagger UI (`/swagger-ui/index.html`) | 브라우저 클릭 호출 |
| Demo UI (`/demo`) | 두 탭 실시간 송수신 시연 + WS resume + DISCONNECT presence + 운영/디버깅 카드 (스냅샷·events·메트릭) |
| `k6 run http/k6/load-ingest.js` | 20s 부하 |

## REST API

| Method | Path | 설명 |
|---|---|---|
| POST | `/sessions` | 세션 생성 |
| GET | `/sessions?status=&from=&to=&userId=` | 목록 (status / 기간 / 참여자 필터) |
| GET | `/sessions/{id}` | 단건 |
| GET | `/sessions/{id}/participants` | 참여자 read-model 캐시 (Hybrid 정상 경로면 ~0ms lag) |
| POST | `/sessions/{id}/join` | 참여 (`X-User-Id` 헤더 필수, JOIN 이벤트) |
| POST | `/sessions/{id}/leave` | 퇴장 (LEAVE 이벤트) |
| POST | `/sessions/{id}/end` | 세션 종료 |
| POST | `/sessions/{id}/snapshots` | **수동 스냅샷 트리거** (멱등 — 같은 boundary 면 `created=false`) |
| POST | `/sessions/{id}/events` | 이벤트 수집 (`clientEventId` 필수, idempotent) |
| GET | `/sessions/{id}/events?fromSeq=&toSeq=&from=&to=&limit=` | **디버깅 조회** (cursor 페이지네이션, `nextFromSeq`) |
| GET | `/sessions/{id}/timeline?at=...` | 시점 복원 (snapshot+delta 자동) |
| WS | `/ws/chat?session=&user=&since=` | 실시간 송수신 + resume + presence |
| GET | `/actuator/health` | Liveness/Readiness |
| GET | `/actuator/prometheus` | Prometheus scrape (JVM + HTTP + `sokind.*` 5종) |
| GET | `/v3/api-docs(.yaml)` | OpenAPI |

자세한 스펙: [`docs/openapi.yaml`](./docs/openapi.yaml) 또는 `/swagger-ui`.

## 평가 항목 ↔ 산출물

모든 설계 결정은 [`docs/design.md`](./docs/design.md) 의 해당 섹션 참조.

| 평가 항목 | 산출물 |
|---|---|
| 4.1 필수 구현 | `src/main/kotlin/...` 전체 |
| 4.2 DB 설계 (ERD/DDL/인덱스) | [`design.md §2`](./docs/design.md#2-도메인-모델--erd) + [`V1__init_schema.sql`](./src/main/resources/db/migration/V1__init_schema.sql) |
| 4.2 REST API (OpenAPI) | [`docs/openapi.yaml`](./docs/openapi.yaml), 런타임 `/v3/api-docs` |
| 4.2 설계 문서 (재연결/중복/순서/확장/관측/장애) | [`design.md §3, §4, §7, §8, §9, §10`](./docs/design.md) |
| 4.3 상태 복원 | `TimelineService` + [`design.md §5`](./docs/design.md#5-상태-복원-replay) |
| 4.4 쿼리/인덱스/장애 | [`design.md §6, §10`](./docs/design.md#6-핫패스-쿼리--인덱스) |
| 가산점 — Snapshot 자동화 + 수동 트리거 | `SnapshotService` (every-N=5 데모) + `POST /sessions/{id}/snapshots` |
| 가산점 — Projection 비동기 (Hybrid) | `ProjectionService` + `ProjectionWorker` + `ProjectionEventListener` |
| 가산점 — 부하 테스트 | k6 + [`design.md §11`](./docs/design.md#11-성능-측정-k6) |
| 가산점 — 관측 가능성 | Actuator + Prometheus + 커스텀 메트릭 5종 [`design.md §9`](./docs/design.md#9-관측-가능성) |
| 가산점 — 테스트 전략 | 단위 (mockk) + 통합 (Testcontainers MySQL) |
