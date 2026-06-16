# fkerp-poc

POC do **FKERP**, um sistema ERP. Monorepo com a fundação já no ar: backend, frontend (página de
welcome) e stack completa de observabilidade — tudo via Docker Compose. O domínio do ERP ainda não
foi modelado. As regras de arquitetura e engenharia do projeto estão no [CLAUDE.md](CLAUDE.md).

## Estrutura

```
backend/      # Spring Boot 4 / Java 21 / Maven (pacote com.fksoft.erp); build via ./mvnw
frontend/     # Angular (página inicial = welcome "FKERP")
infra/        # configs de observability (Prometheus, Loki, Alloy, Grafana)
artifacts/    # documentação orientada a humanos
compose.yaml  # orquestra tudo
```

## Pré-requisitos

- Docker + Docker Compose
- (Opcional, para dev local) Java 21 e Node 22 (o backend usa o Maven wrapper `./mvnw`)

## Subir tudo

```bash
cp .env.example .env        # primeira vez
docker compose up --build
```

## Serviços

| Serviço          | URL                                        |
|------------------|--------------------------------------------|
| Frontend         | http://localhost:4200                      |
| Backend          | http://localhost:8080                      |
| Backend health   | http://localhost:8080/actuator/health      |
| Backend métricas | http://localhost:8080/actuator/prometheus  |
| Grafana          | http://localhost:3000 (admin / admin)      |
| Prometheus       | http://localhost:9090                      |
| Loki             | http://localhost:3100                      |
| PostgreSQL       | localhost:5432                             |

No Grafana, os datasources (Prometheus, Loki) e um dashboard de visão geral do backend já vêm
provisionados. Logs aparecem em **Explore → Loki**.

## Desenvolvimento local

Backend (em `backend/`, sempre via wrapper):

```bash
./mvnw spring-boot:run                  # exige um PostgreSQL acessível
./mvnw verify                           # build + gates (Spotless, Checkstyle, ArchUnit, Modulith) + testes (precisa de Docker)
./mvnw test -Dtest=ErpApplicationTests  # um único teste
./mvnw spotless:apply                   # formata o código
```

Frontend (em `frontend/`):

```bash
npm install
npm start        # http://localhost:4200
npm test         # testes unitários (Vitest)
npm run build    # build de produção
```

### Testes E2E (Playwright)

Os testes E2E rodam contra uma **stack isolada e descartável** (`compose.e2e.yaml`) com um Postgres
efêmero (em `tmpfs`), em portas próprias (frontend **4201**), para **nunca tocar no banco de
desenvolvimento**. A partir de `frontend/`:

```bash
npm run e2e:up     # sobe a stack isolada (build) em http://localhost:4201
npm run e2e        # roda os testes (baseURL 4201; sobrescreva com E2E_BASE_URL)
npm run e2e:down   # derruba a stack (o Postgres efêmero é descartado)
```

A stack de desenvolvimento (`compose.yaml`, porta 4200) e a de E2E (4201) podem rodar juntas.
