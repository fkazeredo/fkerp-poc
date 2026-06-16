# Relatório — Sprint 2 / Slice 3 (CRM2-003): Busca e filtros de Oportunidades

- **Data:** 2026-06-16
- **Branch:** `feature/crm-opportunity-filters` (a partir de `develop`)
- **Versão:** **0.15.0** (feature nova → MINOR; bump por entrega, `CLAUDE.md` §14). **Sem release note**
  (regra de cadência: nota só no fim de uma entrega completa/sprint).
- **Escopo entregue:** **enriquece a mesma listagem operacional de Oportunidades** (Slice 2) com os
  filtros que faltavam — estágio (já existia), responsável, origem do Lead, período de criação, período
  de previsão de fechamento, faixa de valor estimado — e amplia a **busca** para incluir **o nome e os
  contatos do Lead de origem** (telefone, WhatsApp, e-mail) além dos campos da Oportunidade. **Não**
  entrega detalhe de Oportunidade, transições, atividades, perda, filtros salvos, forecast, IA,
  probabilidade nem ROI de campanha — e nada de proposta/venda/booking/financeiro.

## 1. O que foi implementado

- **Domínio (`domain.crm`):**
  - `service.data.OpportunitySearchCriteria` expandido para 11 campos: `stages`, `responsibleId`,
    `unassignedOnly`, `originId`, `createdFrom`/`createdTo` (`Instant`), `expectedCloseFrom`/
    `expectedCloseTo` (`LocalDate`), `estimatedValueMin`/`estimatedValueMax` (`BigDecimal`), `query`.
  - `service.OpportunitySpecifications.matching` = `allOf(stageFilter, responsibleFilter, originFilter,
    createdRange, expectedCloseRange, estimatedValueRange, search)`, espelhando o estilo de
    `LeadSpecifications`. `responsibleFilter` trata `unassignedOnly` (→ `responsible_person_id IS NULL`)
    com precedência sobre `responsibleId`; `originFilter` casa `origin.id`; os `*Range` montam `>=`/`<`
    (criação, limite superior exclusivo) e `>=`/`<=` (fechamento e valor, inclusivos). A **busca**
    casa os campos próprios da Oportunidade (`name`, `productType`, `mainInterest`) **OU** um
    `EXISTS` correlacionado à `Lead` (`lead.id = opportunity.leadId`) sobre `name`/`phone`/`whatsapp`/
    `email` — a Oportunidade guarda só `leadId` (sem relacionamento mapeado), por isso a subconsulta.
  - `OpportunityService.list` permanece o único ponto: combina `matching(criteria)` **com** a
    `OpportunityAccessPolicy.visibleTo(...)` na própria consulta (`matching.and(visibleTo)`), então
    **nenhum filtro fura a visibilidade**.
- **Delivery:** `OpportunityController.list` ganhou os `@RequestParam` opcionais `responsible` (id ou o
  literal `unassigned`), `originId` (UUID), `createdFrom`/`createdTo`/`closeFrom`/`closeTo` (ISO date,
  `@DateTimeFormat`), `valueMin`/`valueMax` (`BigDecimal`); `stage`/`q` já existiam. Datas viram
  `Instant` em UTC (`createdTo` recebe `plusDays(1)` → limite exclusivo, idêntico ao `LeadController`).
  Retorno inalterado: `PageResponse<OpportunityListItem>`.
- **Persistência:** `V15__opportunity_filter_indexes.sql` adiciona índices para as colunas dos novos
  ranges (`created_at`, `expected_close_date`, `estimated_value`); V13 já indexava
  `stage`/`responsible_person_id`/`origin_id`. Nenhuma mudança estrutural de schema.
- **Frontend:**
  - `core/api/opportunity.service.ts`: `OpportunityFilters` expandido; `list()` monta os params (datas
    ISO `yyyy-MM-dd`, valores numéricos); novos `origins()`/`responsibles()` reusando os endpoints
    `/api/crm/origins` e `/api/crm/responsibles` e os tipos `Origin`/`Responsible` do `lead.service`.
  - `features/opportunities/opportunity-list`: barra de filtros espelhando `lead-list` — **responsável**
    (`p-select`, com *Sem responsável*), **origem** (`p-select`), **criado de/até** e **fechamento
    de/até** (`p-datepicker`), **valor mín./máx.** (`p-inputNumber`, moeda BRL), além de **estágio** e
    **busca** já existentes e do botão **Limpar**. `ngOnInit` carrega origens + responsáveis;
    `applyFilters()` reconsulta da página 0; `clearFilters()` zera tudo.

## 2. Regras funcionais cobertas

Os filtros **respeitam a visibilidade**: aplicados sobre o conjunto que o perfil já pode ver (a policy
entra na query junto com os filtros), então um representante filtrando pelo responsável de outro **não**
passa a ver Oportunidades alheias. **Perdidas** continuam fora por padrão e só aparecem com `stage=LOST`
explícito. A **busca** encontra por nome/contato do Lead (telefone, WhatsApp, e-mail) **e** por
título/produto/interesse da Oportunidade. Filtros se combinam (AND); valor com `min`/`max` exclui
naturalmente Oportunidades sem valor estimado. Nada de proposta/venda/booking/financeiro.

## 3. Critérios de aceite cobertos (→ teste que trava)

- Filtrar por **responsável** (id e pool não atribuído) → `filtersByResponsible`.
- Filtrar por **origem** do Lead → `filtersByOrigin`.
- Filtrar por **período de criação** → `filtersByCreationPeriod`.
- Filtrar por **período de previsão de fechamento** → `filtersByExpectedClosePeriod`.
- Filtrar por **faixa de valor estimado** → `filtersByEstimatedValueRange`.
- **Buscar** por nome do Lead, por **contato** do Lead (subquery) e por **resumo** da Oportunidade →
  `searchesByLeadNameContactAndSummary`.
- Filtros **não furam visibilidade** → `responsibleFilterDoesNotBypassVisibility` (representante
  filtrando pelo gerente não vê) + `filtersDoNotBypassVisibility` (LOST, da Slice 2).
- **Slice 2 revalidada:** os 12 testes anteriores de `OpportunityListApiIntegrationTest` seguem verdes
  (lista/LOST/visibilidade por perfil/403/campos exatos/paginação).
- Frontend: aplicar responsável/origem/valor/datas reconsulta da página 0; `clearFilters` zera tudo;
  `ngOnInit` carrega origens e prepende *Sem responsável* → `opportunity-list.spec`.

## 4. Arquivos alterados

- **Backend (novos):** `db/migration/V15__opportunity_filter_indexes.sql`,
  `application/api/dto/OpportunityListParams` (objeto de parâmetros da listagem, ligado pelo Spring MVC).
- **Backend (editados):** `domain/crm/service/data/OpportunitySearchCriteria` (11 campos),
  `domain/crm/service/OpportunitySpecifications` (filtros + busca com subquery ao Lead),
  `application/api/OpportunityController` (recebe o `OpportunityListParams` e mapeia para o criteria —
  agrupar em um objeto mantém o endpoint dentro do limite de parâmetros do Checkstyle e isola a forma de
  transporte da entrada do caso de uso), teste `OpportunityListApiIntegrationTest` (+7 testes, +helper
  `search`).
- **Frontend (editados):** `core/api/opportunity.service.ts` (`OpportunityFilters` + `list()` params +
  `origins()`/`responsibles()`), `features/opportunities/opportunity-list/opportunity-list.ts` + `.html`
  (UI de filtros), `opportunity-list.spec.ts` (+3 testes).
- **Docs:** `application.yml` + `compose.yaml` (0.14.2 → 0.15.0); manuais `en-US`/`pt-BR` (seção 8.2,
  filtros da lista); este relatório. **`.env`/`.env.example`:** `APP_VERSION=0.15.0` a ser ajustado
  pelo dono (arquivos sob gestão manual); os defaults de `application.yml`/`compose.yaml` já resolvem
  0.15.0.

## 5. Testes / validações

- **Backend:** `./mvnw verify` **BUILD SUCCESS — 197 testes, verde** (Postgres real via
  Testcontainers), incluindo `OpportunityListApiIntegrationTest` (19: 12 da Slice 2 + 7 da Slice 3),
  `ArchitectureTest` (ArchUnit), `ModularityTests` (Modulith) e a completude do `HttpErrorMapping`;
  Spotless + Checkstyle passam (a listagem agora cabe no limite de parâmetros via `OpportunityListParams`).
- **Frontend:** `ng test` **verde (97)** — +3 em `opportunity-list` (filtros combinados, `ngOnInit`,
  `clearFilters` total).
- **E2E:** **não reexecutado nesta fatia** — não houve mudança de spec E2E (o `opportunity-listing.spec`
  existente cobre a listagem; os filtros são cobertos por integração + testes de componente). Rodar o
  stack isolado (porta 4201, Postgres efêmero) permanece o caminho para o ciclo completo, sem tocar o
  DB de dev.

## 6. Lacunas conhecidas

- A lista **não** ganha coluna de origem (não está nos requisitos da tela); origem é só filtro.
- `lastActivityAt`/`nextActionDate` seguem nulos (fatia de atividades é futura) — não filtráveis.
- Filtro por valor exclui Oportunidades com `estimatedValue` nulo quando há `min`/`max` (comportamento
  natural de faixa).
- Sem filtros salvos, forecast, scoring/IA, probabilidade, busca full-text sofisticada nem ROI de
  campanha (fora de escopo desta fatia — Rule Zero).
- Ainda **sem detalhe de Oportunidade**: o título linka para o Lead de origem.

## 7. Próximo prompt recomendado

> **Sprint 2 / Slice 4 (CRM2-004): Detalhe da Oportunidade.** `GET /api/opportunities/{id}` (visibilidade
> por perfil via `OpportunityAccessPolicy.canSee`, 404/403 no padrão `{code,message,fields}`) + tela de
> detalhe (dados comerciais, link ao Lead de origem). Sem transições/atividades ainda. Bump → 0.16.0;
> sem release note (só no fim da Sprint 2); manuais atualizados.
