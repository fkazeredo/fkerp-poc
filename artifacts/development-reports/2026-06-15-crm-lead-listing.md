# Relatório — Sprint 1 / Slice 2: Listagem operacional de Leads (busca e filtros)

- **Data:** 2026-06-15
- **Branch:** `feature/crm-lead-listing` (a partir de `develop`)
- **Escopo entregue:** listagem operacional de Leads ponta a ponta — backend (`GET /api/leads`
  paginado, filtrado e buscável, com visibilidade por dono) e frontend (tela `/leads` com filtros,
  busca e atribuição de responsável na criação).

## 1. O que foi implementado

- **Visibilidade por dono (regra de negócio confirmada):** um usuário vê os Leads em que é o
  **responsável** OU que estão **sem responsável**; um **gestor** (escopo `crm:lead:read:all`) vê
  todos. Aplicado como **predicado de query** (`LeadAccessPolicy`), nunca pós-filtrado — busca e
  filtros não conseguem expor Leads proibidos.
- **Escopos novos:** `crm:lead:read` (listar) e `crm:lead:read:all` (gestor). `UserContextProvider.hasScope`
  permite ao controller decidir sem o serviço tocar no `SecurityContextHolder`. Seed dev recebe ambos (V6).
- **Read model:** `LeadRepository` (`JpaSpecificationExecutor`) + `LeadSpecifications` (status —
  **LOST excluído por padrão**, incluído só quando filtrado explicitamente —, origem,
  responsável/sem-responsável, período de criação, busca por nome/contatos, *fetch-join* da origem).
  `LeadService.list` enriquece cada página com **duas queries em lote** (sem N+1): última interação
  por lead (Postgres `DISTINCT ON`) e nomes dos responsáveis. `Lead` ganhou `nextContactAt`
  (somente leitura nesta fatia).
- **API:** `GET /api/leads` → `PageResponse<LeadListItemResponse>` (default `createdAt desc`, page
  size 20) e `GET /api/crm/responsibles`. Envelope `infra.web.PageResponse`. DTOs entity-free.
- **Atribuição:** o form de criação ganhou um seletor opcional de **Responsável** (reaproveita
  `/api/crm/responsibles`), enviado como `responsiblePersonId` (o backend já validava).
- **Frontend `/leads`:** `p-table` com paginação server-side (lazy) + barra de filtros (status
  multi-select, origem, responsável com opção “Sem responsável”, período, busca com debounce).
  Colunas: nome, contato principal, origem, status (tag pt-BR), responsável (ou “Sem responsável”),
  criado em, última interação (data + tipo) e próximo contato. Estados loading/vazio/erro.
  Rota, menu, command palette, atalho `g` `l` e link na home.

## 2. Regras funcionais cobertas

- Leads perdidos **não** aparecem por padrão; aparecem **apenas** quando o filtro de status inclui LOST.
- Leads **sem responsável** são identificáveis (flag `unassigned`, badge “Sem responsável”, filtro próprio).
- Busca e filtros **não expõem** Leads que o usuário não pode ver (visibilidade no nível da query).
- Nenhum comportamento de Opportunity/Customer/Sale/Booking/Finance/Commission foi criado.

## 3. Critérios de aceite cobertos

Listar (autorizado); colunas operacionais exibidas; não-atribuídos visíveis; LOST fora por padrão e
dentro quando filtrado; filtrar por status, origem, responsável e período; buscar por nome ou
contato; criação existente continua funcionando (regressões verdes). **Todos cobertos.**

## 4. Arquivos alterados (principais)

- Backend: `infra/security` (SecurityConfig, UserContextProvider), `domain/crm` (Lead, LeadRepository,
  LeadSpecifications, LeadAccessPolicy, LeadSearchCriteria, LeadListView, LatestInteractionRow,
  LeadService), `domain/identity` (ResponsibleView, UserRepository), `infra/web/PageResponse`,
  `application/api` (LeadController GET, ResponsibleController, dtos), `db/migration/V6__lead_listing.sql`.
- Frontend: `core/api/lead.service.ts`, `features/leads/lead-list/*`, `features/leads/lead-create/*`,
  `app.routes.ts`, `core/layout/shell.ts`, `features/home/home.html`.

## 5. Testes / validações adicionados

- **Backend unit:** `LeadServiceTest.list` (contato principal, nome do responsável, última interação,
  unassigned).
- **Backend integração (Postgres real):** `LeadListApiIntegrationTest` (15) — lost-by-default, cada
  filtro, busca por nome/contato, visibilidade dono vs gestor, 403 sem escopo, 401 sem auth, envelope
  de paginação, responsáveis, round-trip criar-com-responsável. `./mvnw verify` **verde: 55 testes**.
- **Frontend unit (Vitest, 34):** `LeadService` (montagem de params), `LeadList` (default exclui LOST,
  re-query por filtro, opção “Sem responsável”, limpar), `LeadCreate` (envia responsável).
- **E2E (Playwright, 5):** lista carrega com colunas; criar lead atribuído → achar por busca. Verde
  contra o stack vivo.

## 6. Gaps conhecidos

- `nextContactAt` ainda **não tem setter** (coluna/exibição prontas; uma fatia de follow-up a
  preencherá).
- “Responsável” é exibido pelo **username** (User não tem display name dedicado).
- PrimeNG 21 em Angular 22 segue via `--legacy-peer-deps` (combinação não-oficial, validada).
- Sem filtros salvos, segmentação, analytics, ranking, scoring ou gráficos — fora de escopo por design.
- OpenAPI: gerado pelo springdoc a partir das anotações; não houve contrato manual.

## 7. Próximo prompt de implementação recomendado

> *Sprint 1 / Slice 3: transições de status do Lead e follow-up. Permitir mover um Lead entre
> NEW → CONTACTED → QUALIFIED → LOST (com motivo de perda obrigatório ao perder), registrar
> interações no histórico e **agendar o próximo contato** (preenchendo `nextContactAt`, hoje só
> leitura). Sem Opportunity/Customer/Sale. Incluir testes (unit + integração real + e2e) e relatório.*
