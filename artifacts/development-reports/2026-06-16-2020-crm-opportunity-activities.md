# Relatório — Sprint 2 / Slice 7 (CRM2-007): Atividades comerciais da Oportunidade

- **Data:** 2026-06-16
- **Branch:** `feature/crm-opportunity-activities` (a partir de `develop`)
- **Versão:** **0.19.0** (feature nova → MINOR; bump por entrega, `CLAUDE.md` §14). **Sem release note**
  (regra de cadência: só no fim de uma entrega completa/sprint).
- **Escopo entregue:** registrar **atividades comerciais** na Oportunidade (histórico confiável de
  negociação), preenchendo as seções que detalhe e lista já reservavam (`activities`/`nextActionDate` no
  detalhe; `lastActivityAt`/`nextActionDate` na lista). Espelha o padrão de **interação do Lead**.
  **Decisões do dono:** tipo/resultado são **enums fixos** (não cadastros); a atividade **não move o
  estágio** (mover segue só pela ação "Avançar estágio"). **Não** cria Proposta/Venda/Booking/Financeiro;
  sem integração WhatsApp/e-mail, gestão documental, geração de proposta, booking ou transcrição.

## 1. O que foi implementado

- **Domínio:**
  - Enums `OpportunityActivityType` (9) e `OpportunityActivityResult` (8) — conjunto fixo da Sprint 2.
  - `OpportunityActivity` (novo `@Entity`, filho do agregado, espelha `LeadInteraction`): tipo, resultado,
    descrição (≤4000), `occurredAt`, `nextActionDate` (opcional), `registeredBy`.
  - `Opportunity`: coleção `activities` (`@OneToMany` cascade ALL + orphanRemoval por `opportunity_id`) +
    campo denormalizado `nextActionDate` (como `Lead.nextContactAt`); método
    `recordActivity(...)` anexa a atividade e atualiza `nextActionDate` quando informado. **Sem** efeito
    no estágio.
- **Read models / repositório:**
  - `OpportunityDetail.activities` deixa de ser reservado → `List<ActivityItem>` (tipo/resultado como
    valor do enum, descrição, data, próxima ação, autor), mais recente primeiro; `nextActionDate` vem de
    `o.nextActionDate()`. A factory resolve também os autores das atividades.
  - `OpportunityListItem.from(o, responsibleName, lastActivityAt)` — `lastActivityAt` da projeção;
    `nextActionDate` de `o.nextActionDate()`.
  - Projeção `OpportunityLastActivityRow` + `OpportunityRepository.findLastActivityAt(ids)` (native
    `MAX(occurred_at) GROUP BY opportunity_id`, sem N+1, espelha `findLatestInteractions`).
- **Serviço / delivery / segurança / persistência:**
  - `OpportunityService.recordActivity(id, RecordActivityCommand, ...)` (`@Transactional`): `loadVisible`
    → `recordActivity` → `saveAndFlush` → `toDetail`. `list` busca o `lastActivityAt` em lote; `toDetail`
    resolve também os autores das atividades.
  - `RegisterOpportunityActivityRequest` (`@NotNull type/result`, `@NotBlank @Size(4000) description`,
    `@NotNull @PastOrPresent occurredAt`, `nextActionDate` opcional). `POST
    /api/opportunities/{id}/activities` → `OpportunityDetail`.
  - `SecurityConfig`: o matcher de `update` passa a cobrir `/lose`, `/stage` **e** `/activities`
    (`SCOPE_crm:opportunity:update`). Sem escopo novo.
  - `V18__opportunity_activities.sql`: `opportunities.next_action_date` + tabela `opportunity_activities`
    (type/result com CHECK nos conjuntos, description, occurred_at, next_action_date, registered_by) +
    índice `(opportunity_id, occurred_at DESC)`.
- **Frontend:** tipos `OpportunityActivity*` + `registerActivity(id, payload)`; no detalhe, ação
  **"Registrar atividade"** (atalho `a`, gated por operação, qualquer estágio) com diálogo
  (selects de tipo/resultado, `p-datepicker` de data com `maxDate=now`, descrição, próxima ação opcional)
  e a seção **Histórico de atividades comerciais** renderizada (tipo/resultado via mapas pt-BR). As
  colunas **Última atividade/Próxima ação** da lista passam a exibir dados (sem mudança de template).

## 2. Regras funcionais cobertas

Toda atividade tem **autor** (o usuário), **data**, **tipo**, **resultado** e **descrição** (obrigatórios,
validados no DTO + entidade + CHECK); **próxima ação** é opcional. O **histórico não é apagável**
(append-only; não há editar/excluir). A atividade **não move o estágio** nem cria
Proposta/Venda/Booking/Financeiro. Só registra quem **vê** e tem `crm:opportunity:update` (representante
só nas próprias). Aparece no **detalhe** e a última atividade + próxima ação na **lista**. Permitida em
qualquer estágio (inclui LOST), espelhando o Lead.

## 3. Critérios de aceite cobertos (→ teste que trava)

- Usuário autorizado registra atividade → `registersAnActivityAndShowsItInDetail`,
  `allowsRegisteringOnALostOpportunity`.
- Exige tipo/resultado/data/autor/descrição → `rejectsMissingRequiredFields` (400), `rejectsAFutureDate`
  (400), `rejectsAnUnknownEnumValue` (400); autor = usuário autenticado (`registeredBy`=comercial).
- Pode incluir próxima ação / aparece no detalhe → `registersAnActivityAndShowsItInDetail`
  (`$.activities[0].nextActionDate`, `$.nextActionDate`).
- Última atividade + próxima ação na lista → `surfacesLastActivityAndNextActionOnTheList`.
- Histórico preservado → `preservesHistoryNewestFirst`.
- Visibilidade/operação → `rejectsRegisteringWithoutTheUpdateScope` (403),
  `representativeCannotRegisterOnAnotherUsersOpportunity` (403), `returnsNotFoundForUnknownOpportunity`.
- Comportamento existente intacto → suítes de criação/lista/detalhe/estágio verdes.

## 4. Arquivos alterados

- **Backend (novos):** `model/OpportunityActivityType`, `model/OpportunityActivityResult`,
  `model/OpportunityActivity`, `repository/OpportunityLastActivityRow`, `service/data/RecordActivityCommand`,
  `application/api/dto/RegisterOpportunityActivityRequest`, `db/migration/V18__opportunity_activities.sql`,
  teste `OpportunityActivityApiIntegrationTest`.
- **Backend (editados):** `Opportunity` (+`activities`/`nextActionDate`/`recordActivity`),
  `OpportunityDetail` (`activities` real + `ActivityItem`), `OpportunityListItem` (+`lastActivityAt`),
  `OpportunityRepository` (+`findLastActivityAt`), `OpportunityService` (+`recordActivity`; `list`/
  `toDetail`), `OpportunityController` (+`POST /{id}/activities`), `SecurityConfig`, `OpportunityServiceTest`,
  `OpportunityTest`.
- **Frontend (editados):** `core/api/opportunity.service.ts`,
  `features/opportunities/opportunity-detail/` (`.ts`/`.html`/`.css`/`.spec.ts`).
- **Docs/contrato:** `CLAUDE.md` §10; `application.yml` + `compose.yaml` (0.18.0 → 0.19.0); manuais
  `en-US`/`pt-BR` (§8.2/§8.3); este relatório. **`.env`/`.env.example`:** `APP_VERSION=0.19.0` a ajustar
  pelo dono.

## 5. Testes / validações

- **Backend:** `./mvnw verify` **BUILD SUCCESS — 251 testes verdes** (Postgres real via Testcontainers),
  incl. `OpportunityActivityApiIntegrationTest` (11), `OpportunityServiceTest` (13), `OpportunityTest`
  (11), ArchUnit, Modulith e completude do `HttpErrorMapping`; Spotless + Checkstyle.
- **Frontend:** `ng test` **verde (117)** — +5 em `opportunity-detail`. `ng build` (produção) verde.
- **E2E:** **não reexecutado** — sem mudança de spec E2E; coberto por integração + componente.

## 6. Lacunas conhecidas

- Atividade é **append-only** (sem editar/excluir, por design).
- Tipo/resultado são **enums fixos** (sem cadastro/admin nesta sprint).
- A atividade **não** move o estágio (decisão do dono); o resultado é informativo.
- `nextActionDate` reflete a **última** atividade que definiu uma (denormalizado, como o Lead).

## 7. Próximo prompt recomendado

> **Encerramento da Sprint 2 — release note consolidada.** Com a Oportunidade completa para esta sprint
> (criação, lista/filtros, detalhe, perda, pipeline e atividades), escrever a **release note** da entrega
> em `artifacts/release-notes/` (ex.: `v0.19.0.md`), em linguagem de negócio, por capacidade (regra de
> cadência §14: nota só no fim de uma entrega completa). Em paralelo, **Sprint 3** começa pela **Proposta**
> a partir de uma Oportunidade *Ready for Proposal*.
