# Relatório — Sprint 1 / Slice 9: Visão operacional de pendências de Leads

- **Data:** 2026-06-15
- **Branch:** `feature/crm-lead-pending` (a partir de `develop`)
- **Escopo entregue:** um **worklist operacional** (somente leitura) dos Leads que precisam de ação,
  para a empresa não perder oportunidades por falta de follow-up. Reaproveita as **tiers de
  visibilidade da Slice 8** — sem nova autorização. Não é dashboard executivo; sem motor de
  notificação/e-mail/SLA/workflow.

## 1. O que foi implementado

- **Endpoint** `GET /api/leads/pending` (paginado), coberto pelo gate de leitura existente
  (`/api/leads/**` → qualquer tier de leitura; Finance/HR/IT → 403). Sem migração, sem DTO de entrada,
  sem i18n no backend.
- **Categorias de pendência** (um Lead aparece uma vez com todos os motivos que se aplicam), em SQL
  (OR) **E** com a Specification de visibilidade — filtros/visibilidade não furam:
  1. `UNASSIGNED` — sem responsável (e não perdido);
  2. `NEW_WITHOUT_INTERACTION` — Novo sem nenhuma interação (`NOT EXISTS` em interações);
  3. `OVERDUE_NEXT_CONTACT` — próximo contato vencido (ativo);
  4. `CONTACTED_WITHOUT_OUTCOME` — Em contato sem follow-up agendado (decisão: "sem próximo contato",
     sem limiar de tempo; complementa a #3).
  QUALIFIED e LOST nunca casam, então saem naturalmente.
- **`PendingLeadReasons.of(lead, now, hasInteractions)`** é a fonte única dos motivos (unit-testada);
  `LeadPendingSpecifications.pending(now)` espelha os mesmos predicados na query.
- **`LeadInteraction.leadId`** (mapeamento read-only do FK) habilita o subquery `NOT EXISTS` da
  categoria 2 (sem mudança de schema).
- **Frontend**: página `/pendencias` (guarda `crmReadGuard`) com tabela em `.surface` — nome (link
  ao detalhe), responsável, **chips de motivo**, status, próximo contato, criado; estados de
  loading/empty/erro. Entrada na **sidebar** ("Pendências") e **tile na home**, ambos restritos a
  quem tem leitura. Rótulos pt-BR: Sem responsável / Sem interação / Contato atrasado / Sem desfecho.

## 2. Regras funcionais cobertas

Pendências respeitam visibilidade (representante/seller veem só as suas; gerente vê todas); é
operacional, não executivo; ajuda a decidir o próximo passo (motivos explícitos); sem
notificação/e-mail/SLA/workflow.

## 3. Critérios de aceite cobertos (→ categoria + teste que trava)

- Gerente vê leads sem responsável como pendência → `UNASSIGNED`;
  `LeadPendingApiIntegrationTest.managerSeesEveryPendingCategory…` + `…reasonsAreReportedPerLead`.
- Usuários responsáveis veem seus próprios contatos atrasados → `OVERDUE_NEXT_CONTACT` + visibilidade;
  idem.
- Novos leads sem interação aparecem → `NEW_WITHOUT_INTERACTION`; idem (e `NewWithInt` é excluído).
- Em contato sem qualificação/perda podem aparecer → `CONTACTED_WITHOUT_OUTCOME`; idem
  (`Future`/`Qualified`/`Lost` excluídos).
- Representantes veem só as próprias pendências → `…representativeSeesOnlyOwnPending` (não vê as do
  gerente nem as não-atribuídas).
- A visão não expõe leads não autorizados → visibilidade na query + `…financeHasNoAccessToPending`
  (403).
- Sprint 1 anterior segue funcionando → suíte completa verde.

## 4. Arquivos alterados (principais)

- **Backend:** `domain/crm` — `PendingReason`, `PendingLeadReasons`, `LeadPendingSpecifications`,
  `PendingLeadView` (novos), `LeadInteraction` (+`leadId`), `LeadService` (+`pending`);
  `application/api` — `LeadController` (+`GET /pending`), `dto/PendingLeadResponse` (novo).
  **Sem migração, sem endpoint de escrita, sem nova exceção.**
- **Frontend:** `core/api/lead.service.ts` (`pending` + tipos), `features/leads/lead-pending/*`
  (novo), `app.routes.ts` (rota guardada), `core/layout/shell.ts` (nav), `features/home/*` (tile).

## 5. Testes / validações adicionados

- **Backend unit `PendingLeadReasonsTest`** (8): cada categoria isolada e combinada; futuro/qualificado/
  perdido não pendentes.
- **Backend integração (Postgres real) `LeadPendingApiIntegrationTest`** (4): gerente vê todas as
  categorias com `reasons` corretos e exclui não-pendentes; representante só as próprias; finance 403.
  `./mvnw verify` **verde: 151 testes**.
- **Frontend unit (Vitest, 76):** `lead.service` (`pending` GET); `lead-pending` (carrega, rótulos de
  motivo/status, erro).
- **E2E (Playwright, stack isolada):** `lead-pending` — criar lead sem responsável/nota → **Pendências**
  → o lead aparece com o chip "Sem interação" e leva ao detalhe.

## 6. Gaps conhecidos

- **Sem limiares de tempo (sem SLA):** um lead recém-criado já entra como "sem interação"; "atrasado"
  é apenas `nextContactAt < agora`.
- **Sem filtros/ordenção por motivo** na tela (lista simples, ordenada por criação desc); sem
  notificações/alertas.
- Motivos são **ponto-no-tempo** (recalculados a cada carregamento), não persistidos.
- Admin == Gerente para Leads; sem indicadores/análise de origem (Marketing) ainda.

## 7. Próximo prompt de implementação recomendado

> *Sprint 2 / Slice 1: criar uma Opportunity a partir de um Lead qualificado, honrando as tiers de
> visibilidade. Alternativa Sprint 1: indicadores/análise por origem (Marketing) como superfície de
> leitura respeitando as tiers; ou enriquecer as Pendências com filtro por motivo e ordenação por
> urgência. Incluir testes (unit + integração real + e2e) e relatório.*
