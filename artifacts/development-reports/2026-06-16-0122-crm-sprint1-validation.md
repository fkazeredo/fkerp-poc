# Relatório — Sprint 1 / Slice 11: Validação ponta-a-ponta do Sprint 1

- **Data:** 2026-06-16
- **Branch:** `feature/crm-sprint1-validation` (a partir de `develop`)
- **Escopo entregue:** validar o Sprint 1 como **fluxo de negócio coerente entre atores** (não telas
  isoladas) e corrigir **apenas** defeitos ligados aos critérios de aceite do Sprint 1. **Nenhum
  escopo novo** (sem Opportunity / Proposal / Sale / Booking / Finance / conversão de Customer).

## 1. Validação realizada

A exploração confirmou que ambos os fluxos já eram **suportados de ponta a ponta** pelas Slices 1–10
(regras de domínio, escopos e dados de referência presentes: origens **INSTAGRAM**/**REFERRAL**, tipo
de interação **WHATSAPP**, resultado efetivo **CONTACT_MADE** / **NOT_INTERESTED**, motivos de perda
incl. **NO_INTEREST**; usuários-semente `comercial`/`vendedor`/`representante`/`diretor`/`financeiro`).
A lacuna era de **cobertura**: os testes exercitavam cada operação isoladamente, sem encadear o fluxo
completo entre atores. Foram adicionados **testes de jornada** que travam essa coerência, e a suíte
inteira foi re-executada:

- **Backend (Postgres real, MockMvc, multi-ator com tokens reais)** — novo
  `LeadSprint1JourneyApiIntegrationTest` (2 jornadas). `./mvnw verify` **verde: 159 testes**
  (157 anteriores + 2), Spotless + Checkstyle + ArchUnit + Modulith ok.
- **Frontend unit (Vitest):** **85 testes verdes** (sem regressão).
- **E2E (Playwright, stack isolada 4201, DB efêmero)** — novo `lead-journey.spec.ts` (2 jornadas pela
  UI, trocando de usuário no meio do fluxo). Suíte completa **23 testes verdes** (21 + 2). Dev DB
  (4200) intocado.

## 2. Resultados dos fluxos

**Fluxo principal — Instagram → não atribuído → gerente atribui → vendedor (WhatsApp) → Qualificado**
(`mainFlow_instagramLead_unassigned_assigned_contacted_qualified` + `lead-journey` "main journey") —
**PASSOU**, passo a passo:
1. `comercial` cria Lead origem **Instagram**, sem responsável → `NEW`, `unassigned=true`. ✅
2. Gerente identifica como pendência/não atribuído (`/leads/pending` motivo `UNASSIGNED` +
   `/leads?responsible=unassigned`). ✅
3. Gerente **atribui** ao `vendedor` (`/reassign`) → responsável = vendedor. ✅
4. `vendedor` registra interação **WhatsApp** com resultado efetivo → Lead vira **CONTACTED**. ✅
5–7. `vendedor` informa o interesse e **qualifica** → **QUALIFIED**; payload `qualification`
   (`mainInterest`, `qualifiedBy=vendedor`, `qualifiedAt`) presente — **suficiente para o Sprint 2**. ✅
+ Visibilidade durante o fluxo: `financeiro` → 403; outro `representante` (não dono) → 403
  `lead.access-denied`; `comercial` continua vendo. ✅

**Fluxo alternativo — Indicação → sem interesse → Perdido → acessível por filtro**
(`alternativeFlow_referralLead_attempted_lost_historicallyAccessible` + `lead-journey` "lost journey")
— **PASSOU**:
1. Lead origem **Indicação**, responsável = `representante`. ✅
2. Responsável tenta contato com resultado **Não interessado**. ✅
3. **Marcar como perdido exige motivo**: requisição sem `lossReasonId` → **400**
   (`validation.failed`, campo `lossReasonId`); com motivo válido → **LOST**. ✅
4. Lead perdido **sai da lista operacional padrão**. ✅
5. Gerente **encontra pelo filtro Perdido** (`?status=LOST`). ✅
6. Detalhe permanece **historicamente acessível** com o motivo da perda. ✅

## 3. Defeitos encontrados e corrigidos

**Nenhum.** As duas jornadas passaram na primeira execução, em ambos os níveis (API e UI). O Sprint 1
se comporta como um fluxo de negócio coerente; nenhuma correção de produção foi necessária. (Único
ajuste não-funcional: reformatação Spotless do novo teste — sem mudança de comportamento.)

## 4. Critérios de aceite cobertos (→ asserção que trava)

- Fluxo principal ponta a ponta → `mainFlow_…` (backend) + `lead-journey` "main journey" (e2e).
- Fluxo alternativo (Perdido) ponta a ponta → `alternativeFlow_…` + `lead-journey` "lost journey".
- Nenhum passo depende de planilhas/dados manuais externos → todo o fluxo via API/UI reais
  (login, criação, atribuição, interação, qualificação, perda, filtros).
- Regras de visibilidade valem durante o fluxo → asserções de `financeiro` 403 e `representante`
  não-dono 403 `lead.access-denied` no `mainFlow_…`; troca de usuário nas jornadas e2e.
- Lead qualificado tem informação suficiente para o Sprint 2 → asserção do bloco `qualification`
  (`mainInterest` + `qualifiedBy` + `qualifiedAt`).
- Lead perdido permanece histórico mas fora da lista padrão → asserções "sai do padrão" + "aparece em
  `?status=LOST`" + detalhe `LOST` com motivo.
- Nenhuma funcionalidade de escopo futuro introduzida → apenas testes adicionados; zero produção.

## 5. Riscos remanescentes

- Indicadores são **ponto-no-tempo** e o período é por `createdAt` (já documentado na Slice 10).
- **Admin == Gerente** para Leads; Board/Marketing leem via tier `read:all`.
- Não há ainda superfícies de Sprint 2 — a "prontidão para Opportunity" é validada pelo **payload** do
  Lead qualificado, não por uma criação real de Opportunity (que é, propositalmente, fora de escopo).
- Jornadas e2e trocam de usuário recarregando o SPA (login real); dependem do stack isolado no ar.

## 6. Próximo prompt de implementação recomendado

> *Sprint 2 / Slice 1: criar uma **Opportunity** a partir de um Lead **QUALIFIED**, reaproveitando
> `qualification.mainInterest` (e `qualifiedBy`/`qualifiedAt`) como semente — entidade Opportunity +
> transição a partir do Lead qualificado, com migração Flyway, regras de domínio, validação em
> profundidade (§5.5), erros/i18n padronizados e honrando as tiers de visibilidade. Incluir testes
> (unit + integração real + e2e) e relatório.*
