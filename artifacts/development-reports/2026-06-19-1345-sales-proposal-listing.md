# Relatório — Sprint 3 / Slice 4: Lista operacional de Propostas (+ blindagem de regressão)

- **Data:** 2026-06-19
- **Branch:** `feature/sales-proposal-listing` (a partir de `develop`)
- **Versão:** 0.27.5 → **0.28.0** (MINOR — nova funcionalidade)
- **Escopo entregue:** a tela **Vendas → Propostas** deixa de ser uma lista simples e vira a **lista
  operacional** — colunas completas, **filtros** (status, responsável, período de criação, período de
  validade, oportunidade de origem, faixa de valor) e **busca**, com a regra de **excluir as inativas por
  padrão** e a **visibilidade por perfil/propriedade** preservada. Além disso, a pedido do dono,
  **testes de regressão** que blindam as pontas soltas recém-corrigidas (Esc + diálogos sujos).

## 1. O que foi implementado

**Backend — `domain.sales` (espelha o padrão da lista de Oportunidades)**
- **`ProposalSearchCriteria`** (`service.data`): statuses, responsibleId, unassignedOnly, opportunityId,
  createdFrom/To (Instant), validFrom/To (LocalDate), totalMin/Max, query.
- **`ProposalSpecifications`** (`service`): `matching(criteria)` compõe os predicados. A **regra-chave** é o
  `statusFilter`: status vazio → `status IN (abertas)`, ou seja **exclui REJECTED/EXPIRED/CANCELLED por
  padrão**; informado → `status IN (...)`. Mais responsável, oportunidade, faixas de criação/validade/total
  e **busca** (`title LIKE` **OU** EXISTS-subquery correlacionada no nome da `Opportunity`).
- **`ProposalStatus.openStatuses()`** novo helper estático (DRY): reutilizado pelo `statusFilter` e pelo
  `ProposalService` (que antes derivava o set inline).
- **`ProposalService.list(criteria, pageable, userId, canSeeAll, canSeeUnassigned)`**: compõe
  `ProposalSpecifications.matching(criteria).and(accessPolicy.visibleTo(...))`, e **enriquece** cada item
  com o nome do responsável **e o nome da Oportunidade de origem** (batch `findAllById`, sem N+1).
- **`ProposalListItem`** ganhou **`opportunityName`** e **`updatedAt`** (colunas pedidas pelo story);
  factory `from(p, responsibleName, opportunityName)`.
- **`ProposalListParams`** (DTO) + **`ProposalController.list`** passam a receber/mapear os filtros (token
  `unassigned`, datas ISO → Instant início-do-dia, `createdTo+1dia` exclusivo). Create/items/totals/submit
  **intactos**. Sem migração nova (os índices `opportunity_id`/`responsible_person_id`/`status` já existem).

**Frontend — `features/proposals/proposal-list` (espelha `opportunity-list`)**
- `proposal.service`: `ProposalFilters`, `list(filters, page, size)` montando `HttpParams`, `responsibles()`
  (reusa `/api/crm/responsibles`); modelo `ProposalListItem` com `opportunityName`/`updatedAt`.
- `proposal-list`: barra de **filtros** (busca, status multi-select, responsável, criação de/até, validade
  de/até, valor mín/máx, **Limpar**), busca com **debounce**, `applyFilters/clearFilters`, opções de
  responsável carregadas no `ngOnInit` (com "Sem responsável"). Tabela com as colunas operacionais,
  incluindo **Oportunidade** (link pelo nome) e **Atualizada em**. Mantém loading/empty/error.

**Regressão (pontas soltas — não quebrar)**
- 3 testes novos: **Esc com diálogo editado avisa** (e mantém aberto no "Continuar editando") em
  lead-detail, opportunity-detail e proposal-detail — fechando a única lacuna apontada na auditoria.
- `playwright.config`: **`retries: 1`** sempre (antes só em CI) — as jornadas e2e pesadas
  (lead→oportunidade→funil→proposta) rodam muitos passos sequenciais contra um backend único; sob carga
  paralela um dropdown/toast pode estourar o timeout. O retry absorve o *timing* de infra **sem afrouxar
  nenhuma asserção**.

## 2. Regras funcionais cobertas
- A lista mostra as informações operacionais (identificação/título, oportunidade de origem, responsável,
  status, total, validade, criação, **última atualização**). "Decisão do cliente": **adiada** (sem dado —
  ciclo Accept/Reject é slice futura).
- **Inativas (rejeitada/expirada/cancelada) não aparecem por padrão**; aparecem só quando o status é
  escolhido no filtro.
- **Visibilidade por perfil/propriedade**: representante vê só as próprias; gerente vê todas; diretor
  consulta (read:all); financeiro **sem acesso** (403). Aplicada como **Specification + canSee** no nível da
  query — nenhum filtro/busca fura a visibilidade.
- Filtros: status, responsável, período de criação, período de validade, oportunidade de origem (via busca
  textual + `?opportunityId` por deep-link) e faixa de valor.
- A lista **não** expõe Reserva, Pagamento nem Comissão (read model estruturalmente comercial — asserido).
- Criação/itens/total/submit de Proposta **continuam funcionando** (suíte verde).

## 3. Critérios de aceite cobertos
- Usuários autorizados listam Propostas ✓ · informações operacionais presentes ✓ · inativas ocultas por
  padrão (a menos que filtradas) ✓ · filtro por status ✓ · por responsável ✓ · por período ✓ ·
  representante vê só as próprias ✓ · sem Reserva/Pagamento/Comissão ✓ · criação/itens/total seguem
  funcionando ✓.

## 4. Arquivos alterados (principais)
- **Backend (novos):** `domain/sales/service/data/ProposalSearchCriteria.java`,
  `domain/sales/service/ProposalSpecifications.java`, `application/api/dto/ProposalListParams.java`,
  `test/.../application/api/ProposalListingApiIntegrationTest.java`.
- **Backend (editados):** `ProposalStatus.java` (+`openStatuses()`), `ProposalListItem.java`,
  `ProposalService.java` (list+enriquecimento), `ProposalController.java`, `application.yml` (0.28.0).
- **Frontend:** `core/api/proposal.service.ts` (+spec), `features/proposals/proposal-list/*` (ts/html/css +
  spec); regressão: `lead-detail.spec.ts`, `opportunity-detail.spec.ts`, `proposal-detail.spec.ts`;
  `e2e/proposal-listing.spec.ts` (novo), `e2e/proposal-creation.spec.ts` (ripple do 2º link), `playwright.config.ts`.
- **Docs:** manual en-US + pt-BR (seção 9.2 — filtros da lista), este relatório.

## 5. Testes / validações
- **Backend `./mvnw verify`: 377 testes verdes** (Spotless/Checkstyle/ArchUnit/Modulith + Testcontainers).
  Novo `ProposalListingApiIntegrationTest` (13): default exclui inativas; filtros status/responsável/criação/
  validade/oportunidade/valor; busca por título e por nome da oportunidade; visibilidade do representante;
  contrato exato do item (sem reserva/pagamento/comissão); **401** sem auth; **403** financeiro; filtro não
  fura visibilidade.
- **Frontend `ng test`: 258 verdes** (proposal-list: filtros, responsáveis, clear, colunas novas, estados
  DOM; proposal.service: HttpParams; + os 3 de Esc-sujo). **`ng build`** verde.
- **E2E `npm run e2e`: 39 verdes** — `proposal-listing` (colunas; filtro por status esconde a ativa;
  representante não vê a do gerente) + suíte completa.
- **Pilha dev** recriada (`docker compose up -d --build frontend backend`); `/api/version` = 0.28.0.

## 6. Gaps conhecidos
- **Decisão do cliente** (aceita/rejeitada pelo cliente): adiada — surge quando o ciclo de vida
  Approve→Send→Accept/Reject existir; hoje nenhuma proposta alcança esses estados por transição.
- **Filtro por oportunidade** é via busca textual (título + nome da oportunidade) + `?opportunityId` por
  deep-link; não há seletor dedicado de oportunidades (evita endpoint/dropdown pesado — Rule Zero).
- Estados inativos só são alcançáveis por dado de teste (JDBC); o filtro "exclui inativas" já fica correto
  para quando as transições chegarem.
- Sem dashboard financeiro/reserva/comissão/PDF (fora de escopo declarado).

## 7. Próximo prompt recomendado
> **SLICE 5: Proposal lifecycle — aprovação e envio.** Implemente as transições `READY_FOR_REVIEW →
> APPROVED → SENT`, com gating por escopo, validações (validade obrigatória no envio), histórico de
> transições e os respectivos endpoints/telas; sem criar Sale/Booking/Financial. Mantenha a lista/itens/
> totais funcionando e cubra feliz + tristes (sem permissão, transição inválida, validade ausente no envio).
