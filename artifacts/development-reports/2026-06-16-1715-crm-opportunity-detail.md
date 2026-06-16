# Relatório — Sprint 2 / Slice 4 (CRM2-004): Detalhe da Oportunidade (+ captura de perda)

- **Data:** 2026-06-16
- **Branch:** `feature/crm-opportunity-detail` (a partir de `develop`)
- **Versão:** **0.16.0** (feature nova → MINOR; bump por entrega, `CLAUDE.md` §14). **Sem release note**
  (regra de cadência: só no fim de uma entrega completa/sprint).
- **Escopo entregue:** a **consulta de detalhe** de uma Oportunidade (somente leitura) — resumo
  comercial, **Lead de origem rastreável** e **perda** — mais uma transição mínima **"marcar como
  perdida"** (decisão do dono nesta fatia) gated por um novo escopo `crm:opportunity:update`. **Não**
  entrega histórico de atividades/estágio (reservados, fatias futuras), nem proposta/venda/booking/
  financeiro/comissão/customer-care.

## 1. O que foi implementado

- **Domínio (`domain.crm`):**
  - `OpportunityAccessPolicy.canSee(opportunity, userId, canSeeAll, canSeeUnassigned)` — checagem por
    registro (own / +unassigned / all), espelhando `LeadAccessPolicy.canSee`.
  - Entidade `Opportunity`: campos de perda inline (`lostAt`, `lostBy`, `lossReason` → `loss_reason_id`,
    `lossNote`) e método de negócio `markLost(reason, byUser, note)` (lança
    `OpportunityCannotBeMarkedLostException` se já `LOST`; senão move para `LOST` e grava a perda). O Lead
    de origem **não** é tocado.
  - Read model `service.data.OpportunityDetail` (+ `SourceLead`, `LossInfo`) com factory
    `from(opportunity, lead, responsibleName, lostByName)`; `loss` presente só quando `LOST`;
    `activities`/`stageHistory` reservados (vazios) e `nextActionDate` nulo — fatias futuras.
  - `OpportunityService.detail(...)` (carrega visível → 404/403 → monta o detalhe com o Lead de origem e
    nomes resolvidos) e `markLost(...)` (resolve a `LossReason` ativa — reuso de `LossReasonRepository` +
    `LossReasonNotAvailableException` — aplica a transição e devolve o detalhe refrescado).
  - Exceções novas: `OpportunityNotFoundException` (`opportunity.not-found`),
    `OpportunityAccessDeniedException` (`opportunity.access-denied`),
    `OpportunityCannotBeMarkedLostException` (`opportunity.cannot-mark-lost`).
- **Delivery:** `GET /api/opportunities/{id}` → `OpportunityDetail`; `POST /api/opportunities/{id}/lose`
  (`@Valid LoseRequest` reusado) → detalhe refrescado.
- **Infra/segurança:** `HttpErrorMapping` mapeia as 3 exceções (404/403/422 — o teste de completude de
  apresentação exige o registro); i18n em `messages.properties`. `SecurityConfig`: `POST
  /api/opportunities/*/lose` exige `SCOPE_crm:opportunity:update` (o GET de detalhe já cai nos read tiers).
- **Persistência:** `V16__opportunity_loss.sql` — colunas de perda + `CHECK (stage <> 'LOST' OR
  loss_reason_id IS NOT NULL)` (espelha o Lead) + seed do escopo `crm:opportunity:update` por perfil
  (manager/vendedor/representante).
- **Frontend:** `opportunity.service` (`OpportunityDetail`/`OpportunitySourceLead`/`OpportunityLoss` +
  `detail(id)` + `lose(id, reason, note)`); `AuthService.canOperateOpportunity()`; tela
  `features/opportunities/opportunity-detail` (resumo + card do Lead de origem com link `Ver lead de
  origem` + card de Perda quando `LOST` + diálogo "Marcar como perdida" reusando os motivos de perda),
  estados loading/erro (403/404), atalhos `p`/`Esc`; rota `oportunidades/:id`; o **título da lista** passa
  a apontar para `['/oportunidades', id]`.

## 2. Regras funcionais cobertas

Lead de origem **rastreável** (card + link). A Oportunidade **preserva sua própria perda** (registro
separado do Lead). **Perda visível** quando `LOST` (motivo/quando/quem/nota). Só abre/encerra quem **pode
ver** a Oportunidade (`canSee` aplicado no detalhe e na transição). O detalhe **não** mostra
proposta/venda/booking/financeiro/comissão/customer-care. As seções de **histórico de atividades** e
**movimentação de estágio** aparecem reservadas (vazias) — preenchidas em fatias futuras.

## 3. Critérios de aceite cobertos (→ teste que trava)

- Usuário autorizado abre o detalhe → `opensDetailWithSourceLeadAndCommercialData`,
  `representativeOpensOwnOpportunity`.
- Mostra informações do Lead de origem → `opensDetailWithSourceLeadAndCommercialData` (`$.sourceLead.*`).
- Mostra estágio, responsável, interesse principal e valor → idem.
- Suporta histórico de atividades / movimentação de estágio → seções presentes na tela (reservadas);
  contrato expõe `activities`/`stageHistory` (vazios) — `$.activities`/`$.stageHistory` no teste.
- Mostra a perda quando `LOST` → `marksAsLostWithAReasonAndShowsLoss` (`$.loss.*`).
- Não autorizados não acessam → `forbidsDetailOfAnOpportunityTheUserCannotSee`,
  `rejectsDetailWithoutAReadScope`, `rejectsUnauthenticatedDetail`, `returnsNotFoundForUnknownOpportunity`.
- Criação/lista/filtros seguem funcionando → suítes `OpportunityCreation`/`OpportunityList` verdes.
- (Perda) já-perdida → 422 (`rejectsLosingAnAlreadyLostOpportunity`); sem `update` → 403
  (`rejectsLoseWithoutTheUpdateScope`); sem ver → 403 (`representativeCannotLoseAnotherUsersOpportunity`);
  motivo inválido → 422 (`rejectsLoseWithUnknownReason`); sem motivo → 400 (`rejectsLoseWithoutAReason`).

## 4. Arquivos alterados

- **Backend (novos):** `OpportunityDetail`, `OpportunityNotFoundException`,
  `OpportunityAccessDeniedException`, `OpportunityCannotBeMarkedLostException`,
  `db/migration/V16__opportunity_loss.sql`, testes `OpportunityDetailApiIntegrationTest` e `OpportunityTest`.
- **Backend (editados):** `OpportunityAccessPolicy` (+`canSee`), `Opportunity` (+campos de perda +
  `markLost`), `OpportunityService` (+`detail`/`markLost`/`loadVisible`/`toDetail`), `OpportunityController`
  (+`GET {id}`/`POST {id}/lose`), `HttpErrorMapping`, `messages.properties`, `SecurityConfig`,
  `OpportunityServiceTest` (+4), `OpportunityListApiIntegrationTest` (seed `LOST` com `loss_reason_id`).
- **Frontend (novos):** `features/opportunities/opportunity-detail/` (`.ts`/`.html`/`.css`/`.spec.ts`).
- **Frontend (editados):** `core/api/opportunity.service.ts` (tipos + `detail`/`lose`),
  `core/auth/auth.service.ts` (+`canOperateOpportunity`), `app.routes.ts` (rota `oportunidades/:id`),
  `opportunity-list.html` (link do título).
- **Docs/contrato:** `CLAUDE.md` §10 (`crm:opportunity:update`); `application.yml` + `compose.yaml`
  (0.15.0 → 0.16.0); manuais `en-US`/`pt-BR` (§8.3); este relatório. **`.env`/`.env.example`:**
  `APP_VERSION=0.16.0` a ajustar pelo dono (gestão manual; os defaults já resolvem 0.16.0).

## 5. Testes / validações

- **Backend:** `./mvnw verify` **BUILD SUCCESS — 215 testes verdes** (Postgres real via Testcontainers),
  incl. `OpportunityDetailApiIntegrationTest` (12), `OpportunityServiceTest` (9), `OpportunityTest` (2),
  ArchUnit, Modulith e completude do `HttpErrorMapping`; Spotless + Checkstyle.
- **Frontend:** `ng test` **verde (107)** — +10 em `opportunity-detail`. `ng build` (produção) verde.
- **E2E:** **não reexecutado** — sem mudança de spec E2E nesta fatia; o detalhe é coberto por integração +
  testes de componente. O stack isolado (porta 4201, Postgres efêmero) segue como caminho do ciclo
  completo, sem tocar o DB de dev.

## 6. Lacunas conhecidas

- **Histórico de atividades comerciais e movimentação de estágio** ainda **não têm dados** (seções
  reservadas, vazias) — entram com as fatias de atividades/transições; `nextActionDate` segue nulo.
- A transição implementada é **apenas a perda**; outras movimentações de estágio (avançar no pipeline)
  são fatia futura.
- O detalhe é somente leitura fora da perda (sem editar dados comerciais).

## 7. Próximo prompt recomendado

> **Sprint 2 / Slice 5 (CRM2-005): Movimentação de estágio da Oportunidade.** Avançar a Oportunidade no
> pipeline (`NEW_OPPORTUNITY → DISCOVERY → PRODUCT_FIT → READY_FOR_PROPOSAL`) via
> `POST /api/opportunities/{id}/advance` (ou transições explícitas), gated por `crm:opportunity:update` +
> `canSee`, registrando o **histórico de movimentação de estágio** que o detalhe já reserva. Bump → 0.17.0;
> sem release note (só no fim da Sprint 2); manuais atualizados.
