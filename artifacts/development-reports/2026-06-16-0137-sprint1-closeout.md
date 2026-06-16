# Relatório de encerramento — Sprint 1 (Comercial / CRM: captação de Leads)

- **Data:** 2026-06-16
- **Branch:** `feature/crm-sprint1-closeout` (a partir de `develop`)
- **Natureza:** encerramento (revisão + documentação). **Sem código de Sprint 2**, sem mudança de
  código de produção. Entregas: doc de handoff Lead→Opportunity, correção de inconsistência no manual
  e este relatório de fechamento.

## 1. Status do objetivo do Sprint

**ATINGIDO.** O Sprint 1 entregou um contexto delimitado **Comercial / CRM** coeso para a captação e
o trabalho operacional de **Leads**, do registro à qualificação/perda, com visibilidade por perfil,
pendências e indicadores — validado ponta a ponta (Slice 11). Um Lead **qualificado** já carrega tudo
que o Sprint 2 precisa para originar uma Opportunity, **sem recapturar dados**.

## 2. Capacidades concluídas (escopo entregue → onde vive + testes que travam)

| # | Capacidade | Onde vive | Testes que travam |
|---|---|---|---|
| 1 | Contexto Comercial/CRM para captação de Lead | `domain/crm` (Lead, Origin, refs) | suíte CRM |
| 2 | Criação de Lead | `POST /api/leads`, `Lead.register` | `LeadApiIntegrationTest` |
| 3 | Listagem operacional | `GET /api/leads` (paginado) | `LeadListApiIntegrationTest` |
| 4 | Busca e filtros | `LeadSearchCriteria` + specs | `LeadListApiIntegrationTest` |
| 5 | Detalhe do Lead | `GET /api/leads/{id}` | `LeadDetailApiIntegrationTest` |
| 6 | Atribuição de responsável | `POST /{id}/reassign`, `LeadAssignmentPolicy` | `LeadAssignmentApiIntegrationTest` |
| 7 | Histórico de interações | `POST /{id}/interactions`, `LeadInteraction` | `LeadInteractionApiIntegrationTest` |
| 8 | Regra de status **Contacted** | `Lead.recordInteraction` + `InteractionResult.isEffectiveContact()` | `LeadInteractionApiIntegrationTest` |
| 9 | Qualificação do Lead | `POST /{id}/qualify`, `Lead.qualify` | `LeadQualificationApiIntegrationTest` |
| 10 | Fluxo de Lead **Perdido** | `POST /{id}/lose`, `Lead.markLost` | `LeadLossApiIntegrationTest` |
| 11 | Visibilidade por perfil (tiers) | `LeadAccessPolicy`, `SecurityConfig` | `LeadVisibilityApiIntegrationTest` |
| 12 | Pendências operacionais | `GET /api/leads/pending` | `LeadPendingApiIntegrationTest` |
| 13 | Indicadores mínimos | `GET /api/leads/indicators`, `LeadIndicatorQueries` | `LeadIndicatorsApiIntegrationTest` |
| 14 | Validação ponta a ponta | jornadas multi-ator | `LeadSprint1JourneyApiIntegrationTest` + `lead-journey.spec.ts` |
| 15 | Handoff funcional p/ Sprint 2 | `artifacts/lead-to-opportunity-handoff.md` (+ snapshot no Lead) | — (contrato documental) |

Frontend correspondente: páginas de lista/criação/detalhe, atribuição, interação/qualificação/perda,
**Pendências** e **Indicadores**, com gating por escopo; coberto por Vitest + Playwright.

## 3. Status dos critérios de aceite

| Critério | Status | Evidência |
|---|---|---|
| Comportamento do Sprint 1 coerente ponta a ponta | ✅ | `LeadSprint1JourneyApiIntegrationTest` (2 jornadas) + `lead-journey.spec.ts`; Slice 11 |
| Lead qualificado pronto para o Sprint 2 sem recapturar dados | ✅ | 9 campos preservados (`Lead.java` + `LeadDetailResponse`); ver handoff doc |
| Lead e Opportunity permanecem separados | ✅ | Opportunity não existe; qualificar não cria/converte nada |
| Não existe implementação de Opportunity | ✅ | busca no repo (§4); só comentários prospectivos |
| Não existe conversão de Customer | ✅ | nenhuma lógica de `convert`/Customer no código |
| Nenhuma funcionalidade de escopo futuro introduzida | ✅ | só docs alterados neste encerramento |
| Relatório de fechamento produzido | ✅ | este documento |

### Prova: o que o Sprint 1 **NÃO** implementou (correto)

| Item futuro | Presente no código? |
|---|---|
| Opportunity | **NÃO** |
| Proposal | **NÃO** |
| Sale | **NÃO** |
| Sales Order | **NÃO** |
| Booking | **NÃO** |
| Finance (módulo) | **NÃO** (existe só o usuário-semente `financeiro` p/ controle de acesso) |
| Commission | **NÃO** |
| Marketing Campaign | **NÃO** |
| Call Center queue | **NÃO** (existe perfil de acesso, não fila) |
| Customer Care | **NÃO** |
| Conversão de Customer | **NÃO** |

## 4. Defeitos / lacunas encontrados

- **1 inconsistência corrigida (doc):** a seção **"What's next" (§11)** do manual listava recursos
  **já entregues** (Contacted/Qualified/Lost, histórico de interações) como "futuros"; o rodapé dizia
  "first edition (Slice 1)". Ambos foram atualizados para refletir o Sprint 1 fechado e apontar o
  handoff para o Sprint 2.
- **Nenhum defeito de comportamento.** As jornadas ponta a ponta passaram na Slice 11 sem correção de
  produção.
- **Nits de código revisados e considerados intencionais / não-issues** (sem ação): `qualifiedBy` /
  `lostBy` são UUIDs **propositalmente** não-FK (preservam histórico mesmo se o usuário for removido) e
  já comentados no agregado; `INTERNAL_NOTE` **está** semeado em `V3__crm_reference_data.sql`;
  validação de tamanho da nota ocorre na borda/JPA via service. Nada bloqueia o Sprint 2.

## 5. Riscos

- Indicadores são **ponto-no-tempo** e o período é por `createdAt` (sem série histórica) — já
  documentado.
- **Admin == Gerente** para Leads; Board/Marketing leem via tier `read:all`.
- A "prontidão para Opportunity" é garantida pelo **snapshot/payload** do Lead qualificado, não por uma
  criação real de Opportunity (propositalmente fora de escopo). O contrato está em
  `artifacts/lead-to-opportunity-handoff.md`.

## 6. O Sprint 2 pode começar?

**SIM.** O Sprint 1 está coerente e verde, o Lead qualificado preserva os 9 campos necessários e o
contrato de handoff está documentado. Não há dívida bloqueante.

**Snapshot de fechamento (gates):** `./mvnw verify` **verde (159 testes, Postgres real)**; `npm test`
**verde (85)**. O E2E (**23 verdes**, stack isolada) foi confirmado na Slice 11 e nada de código mudou
desde então (esta entrega é só documentação), portanto é **citado**, não re-executado.

## 7. Primeira tarefa recomendada para o Sprint 2

> *Sprint 2 / Slice 1: criar uma **Opportunity** a partir de um Lead **QUALIFIED**, conforme
> `artifacts/lead-to-opportunity-handoff.md` — entidade Opportunity (separada do Lead, referenciando
> `leadId`), transição/precondição de domínio a partir do Lead qualificado, semeando o snapshot
> (incl. `mainInterest`/responsável) sem recapturar dados, com migração Flyway, validação em
> profundidade (§5.5), erros/i18n padronizados e honrando as tiers de visibilidade. Incluir testes
> (unit + integração real + e2e) e relatório. Não converter Customer ainda.*
