# Relatório de fechamento — Sprint 2 (Comercial / CRM: Oportunidades) + handoff p/ Sprint 3

- **Data:** 2026-06-17
- **Branch:** `feature/crm-sprint2-handoff` (a partir de `develop`)
- **Versão:** 0.23.1 → **0.23.2** (PATCH — fechamento: documentação de handoff + correção de comentários
  obsoletos; **sem** mudança de comportamento/contrato/esquema/UI)
- **Natureza:** **fechamento** da Sprint 2 com foco no **handoff funcional Opportunity → Proposal**.
  **Não** implementa nada da Sprint 3. Entregas: `artifacts/opportunity-to-proposal-handoff.md` (novo),
  correção de 2 comentários obsoletos em `opportunity.service.ts`, e este relatório. A validação ponta a
  ponta e o release note da Sprint 2 já foram entregues na Slice 12
  (`…1407-crm-sprint2-validation.md`, `…1408-sprint2-closeout.md`, `release-notes/v0.23.1.md`).

## 1. Status do objetivo do Sprint

**ATINGIDO.** A Sprint 2 entregou o ciclo operacional de **Oportunidades comerciais** — da criação a
partir de um Lead **qualificado** até **Pronta para proposta** (ou **Perda**), com pipeline em funil
estrito, atividades comerciais, edição de valor/fechamento, lista/filtros, pendências e indicadores —
tudo com visibilidade por perfil e **validado ponta a ponta** (Slice 12). Uma Opportunity em
`READY_FOR_PROPOSAL` carrega tudo que a Sprint 3 precisa para originar a **Proposal**, sem recapturar
dados, e **nada** de Proposal/Sale/Sales Order/Booking/Finance/Commission/Customer foi implementado.

## 2. Capacidades concluídas (escopo entregue da Sprint 2)

| Capacidade | Onde vive | Teste que trava |
|---|---|---|
| Criar Opportunity de um Lead qualificado | `POST /api/opportunities` | `OpportunityCreationApiIntegrationTest` |
| Listagem operacional (exclui LOST por padrão) | `GET /api/opportunities` | `OpportunityListApiIntegrationTest` |
| Busca e filtros | `OpportunitySpecifications` | `OpportunityListApiIntegrationTest` |
| Detalhe da Opportunity | `GET /api/opportunities/{id}` → `OpportunityDetail` | `OpportunityDetailApiIntegrationTest` |
| Pipeline mínimo + movimentação (funil estrito) | `POST /{id}/stage`, `OpportunityStage.canAdvanceTo` | `OpportunityStageApiIntegrationTest` |
| Histórico de atividades comerciais | `POST /{id}/activities` | `OpportunityActivityApiIntegrationTest` |
| Valor estimado e previsão de fechamento | `PUT /{id}` | `OpportunityDetailsUpdateApiIntegrationTest` |
| Fluxo de Opportunity **Perdida** (motivo obrigatório) | `POST /{id}/lose`, `OpportunityLossReason` | `OpportunityDetailApiIntegrationTest` |
| Pendências operacionais | `GET /api/opportunities/pending` | `OpportunityPendingApiIntegrationTest` |
| Indicadores mínimos | `GET /api/opportunities/indicators` | `OpportunityIndicatorsApiIntegrationTest` |
| Validação ponta a ponta | jornadas | `OpportunitySprint2JourneyApiIntegrationTest` + `opportunity-journey.spec.ts` |
| **Handoff funcional p/ Sprint 3** | `artifacts/opportunity-to-proposal-handoff.md` | — (contrato documental) |

### A Opportunity "Pronta para proposta" preserva (12/12 itens exigidos)

Todos verificados no read model `OpportunityDetail` (exposto por `GET /api/opportunities/{id}`) e
asseridos na jornada `…mainFlow_…readyForProposal`:

| Item exigido | Campo |
|---|---|
| Lead de origem | `leadId` + `sourceLead.id` |
| Origem do Lead | `origin` |
| Responsável | `responsibleId` / `responsibleName` |
| Informações de contato | `sourceLead.phone/whatsapp/email` |
| Interesse principal | `mainInterest` |
| Interesse estimado em produto/serviço | `productType` |
| Valor estimado | `estimatedValue` |
| Data prevista de fechamento | `expectedCloseDate` |
| Histórico de atividades comerciais | `activities[]` |
| Histórico de movimentação de estágio | `stageHistory[]` |
| Notas relevantes | `notes` (+ `nextActionDate`) |
| Prontidão para proposta | `stage == READY_FOR_PROPOSAL` |

Detalhe do contrato em `artifacts/opportunity-to-proposal-handoff.md`.

## 3. Status dos critérios de aceite

| Critério | Status | Evidência |
|---|---|---|
| Comportamento da Sprint 2 coerente ponta a ponta | ✅ | jornadas backend + E2E (Slice 12) |
| "Pronta para proposta" pronta p/ Sprint 3 sem recapturar dados básicos | ✅ | 12/12 itens no `OpportunityDetail`; handoff doc |
| Lead, Opportunity e Proposal permanecem separados | ✅ | Proposal não existe; Opportunity referencia `leadId`, não converte Lead |
| Não existe implementação de Proposal | ✅ | busca no repo (§4); só contrato documental prospectivo |
| Não existe conversão de Customer | ✅ | nenhuma lógica de `convert`/Customer |
| Nenhuma funcionalidade de escopo futuro introduzida | ✅ | só docs + correção de comentário neste fechamento |
| Relatório de fechamento produzido | ✅ | este documento |

### Prova: o que a Sprint 2 **NÃO** implementou (correto)

| Item futuro | Presente no código? |
|---|---|
| Proposal / Proposal approval | **NÃO** |
| Sale / Sales Order | **NÃO** |
| Booking | **NÃO** |
| Finance (módulo) | **NÃO** (só o usuário-semente `financeiro` p/ controle de acesso) |
| Commission | **NÃO** |
| Marketing Campaign | **NÃO** |
| Call Center queue | **NÃO** (existe perfil de acesso, não fila) |
| Customer Care / Conversão de Customer | **NÃO** |

## 4. Defeitos / lacunas encontrados

- **2 comentários obsoletos corrigidos** (pequena inconsistência da Sprint 2): em
  `frontend/src/app/core/api/opportunity.service.ts`, os JSDoc de `OpportunityListItem` e
  `OpportunityDetail` diziam que `lastActivityAt`/`nextActionDate`/`activities`/`stageHistory` estavam
  "reserved for future slices (empty/null for now)" — **obsoleto** desde as Slices 5–7 (pipeline e
  atividades entregues). Atualizados para descrever o comportamento atual. **Sem mudança de
  comportamento.**
- **Nenhum defeito de comportamento de produto.** (O único defeito de comportamento da Sprint 2 — o
  seletor ambíguo do E2E `lead-indicators` causado pelo item de nav da Slice 11 — já foi corrigido na
  Slice 12.)

## 5. Riscos

- A "prontidão para Proposal" é garantida pelo **snapshot/read model** da Opportunity pronta, não por uma
  criação real de Proposal (propositalmente fora de escopo). O contrato está em
  `artifacts/opportunity-to-proposal-handoff.md`.
- Indicadores são **ponto-no-tempo** (sem série histórica) — já documentado.
- A jornada E2E roda como um ator; a visibilidade multi-ator no fluxo é coberta no backend.

## 6. O Sprint 3 pode começar?

**SIM.** A Sprint 2 está coerente e verde, a Opportunity `READY_FOR_PROPOSAL` preserva os 12 itens
exigidos, o contrato de handoff está documentado e não há dívida bloqueante.

**Snapshot de fechamento (gates):** sem mudança de código de produto desde a Slice 12 (esta entrega é
documentação + 2 comentários). Citados da Slice 12: `./mvnw verify` **verde (291, Postgres real)**,
`ng test` **verde (131)**, `ng build` **verde**, **E2E `playwright test` verde (30)**. Nesta entrega,
`ng build` re-executado **verde** (só comentários no `.ts` mudaram).

## 7. Primeira tarefa recomendada para o Sprint 3

> *Sprint 3 / Slice 1: gerar uma **Commercial Proposal** a partir de uma Opportunity `READY_FOR_PROPOSAL`,
> conforme `artifacts/opportunity-to-proposal-handoff.md` — entidade Proposal **separada** (referenciando
> `opportunityId`), precondição de domínio a partir da Opportunity pronta, semeando o snapshot (incl.
> `mainInterest`/`estimatedValue`/`expectedCloseDate`/`productType`) sem recapturar dados, com migração
> Flyway, validação em profundidade (§5.5), erros/i18n padronizados, **novo escopo de operação**
> `crm:proposal:*` e honrando as read tiers. Incluir testes (unit + integração real + e2e) e relatório.
> Ainda **sem** Proposal approval, Sale, Sales Order, Booking, Finance, Commission ou conversão de Customer.*
