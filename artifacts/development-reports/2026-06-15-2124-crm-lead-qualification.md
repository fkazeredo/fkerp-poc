# Relatório — Sprint 1 / Slice 6: Qualificar um Lead

- **Data:** 2026-06-15
- **Branch:** `feature/crm-lead-qualification` (a partir de `develop`)
- **Escopo entregue:** transformar o `qualify` existente (Slice 3, nota opcional, a partir de
  NEW/CONTACTED) no caso de uso real de **qualificação**: registra o **interesse principal**
  (obrigatório), exige um **responsável**, exige o lead **CONTACTED** e mantém quem/quando + notas
  comerciais. **Não** cria Opportunity/Customer/Sale/Booking/Finance (não existem no Sprint 1).

## 1. O que foi implementado

- **`Lead.qualify(byUser, mainInterest, note)`**: agora só a partir de **CONTACTED**
  (`status != CONTACTED` → `LeadCannotBeQualifiedException`, 422 — cobre NEW, re-qualificar e LOST);
  exige responsável (`responsiblePersonId == null` → nova `LeadQualificationRequiresResponsibleException`,
  422); grava `main_interest`, quem/quando e a nota.
- **Interesse principal** = texto livre obrigatório (`@NotBlank`, máx. 500) no `QualifyRequest`;
  aparece no detalhe (`QualificationView`/`QualificationInfo`) e no cartão **Qualificação**.
- **Contato válido**: garantido pela invariante de criação (`Lead.register` exige ≥1 contato + CHECK
  `chk_leads_at_least_one_contact`); estruturalmente impossível de violar na qualificação — sem
  checagem redundante (documentado).
- **V10**: coluna `main_interest`, backfill de leads já qualificados e CHECK
  `chk_leads_qualified_has_interest` (qualificado ⇒ interesse presente).
- **Frontend**: diálogo de qualificação com **Interesse principal** obrigatório + nota opcional;
  `canQualify()` = `CONTACTED && !unassigned` (o botão some para NEW/sem responsável); o cartão
  Qualificação exibe o Interesse.

## 2. Regras funcionais cobertas

- Lead deve ter contato válido (invariante), responsável e **não** estar perdido.
- Qualificação **registra o interesse principal** (obrigatório) e **pode** registrar notas comerciais.
- Registra **quem** qualificou e **quando**.
- Lead qualificado permanece **rastreável e visível** (continua nas listas; histórico preservado) e
  guarda o suficiente (interesse/notas/responsável) para virar Opportunity no Sprint 2.
- Qualificar **não** cria Opportunity/Customer/Sale/Booking/Finance/Commission.

## 3. Critérios de aceite cobertos

Lead CONTACTED com responsável e contato válido pode ser qualificado; exige interesse principal;
pode incluir notas; registra quem/quando; lead **sem responsável** não qualifica (422); lead
**perdido** não qualifica pelo fluxo comum (422); lead **NEW** não qualifica (422 — precisa de contato
efetivo antes); qualificação aparece no detalhe; lead qualificado segue visível na lista; nenhuma
Opportunity/Customer criada (inexistentes); criação/listagem/filtros/detalhe/atribuição/interações
seguem funcionando. **Todos cobertos.**

## 4. Arquivos alterados (principais)

- **Backend:** `domain/crm/Lead.java` (campo `mainInterest` + regras em `qualify`),
  `LeadQualificationRequiresResponsibleException` (novo), `QualificationView` (+`mainInterest`),
  `LeadService.qualify` + mapeamento, `application/api/dto/QualifyRequest` (+`mainInterest`),
  `LeadController.qualify` (body obrigatório), `LeadDetailResponse.QualificationInfo` (+`mainInterest`),
  `infra/web/HttpErrorMapping` (+422), `messages.properties` (+chave),
  `db/migration/V10__lead_qualification_interest.sql` (novo).
- **Frontend:** `core/api/lead.service.ts` (`qualify(id, mainInterest, note)` + `QualificationInfo`),
  `features/leads/lead-detail/lead-detail.ts` + `.html` (campo/guard/gating + cartão).

## 5. Testes / validações adicionados

- **Backend unit:** `LeadTest` — qualifica CONTACTED+atribuído (preserva interesse/nota/quem/quando);
  rejeita NEW, sem responsável e LOST. `LeadServiceTest` — mapeia `mainInterest` no detalhe.
- **Backend integração (Postgres real):** `LeadQualificationApiIntegrationTest` (6) — caminho feliz +
  segue na lista; NEW → 422; LOST → 422; CONTACTED sem responsável → 422
  `lead.qualification-requires-responsible`; sem interesse → **400** com campo `mainInterest`; sem
  `crm:lead:update` → 403. `LeadDetailApiIntegrationTest` — qualify atualizado para o novo fluxo
  (helper `contact(id)`). `./mvnw verify` **verde: 119 testes**.
- **Frontend unit (Vitest, 59):** `lead.service` (`qualify` posta `{mainInterest, note}`);
  `lead-detail` (gating CONTACTED+atribuído; qualifica com interesse; guard sem interesse).
- **E2E (Playwright):** `lead-detail` — criar com responsável → registrar contato efetivo (Em
  contato) → Qualificar com interesse → cartão Qualificação aparece (NEW não mostra Qualificar).

## 6. Gaps conhecidos

- **Interesse principal é texto livre** (sem catálogo de produtos/serviços ainda); Sprint 2 mapeia.
- Sem **edição/correção** da qualificação (imutável após registrada) nem desqualificação.
- "Contato válido" é invariante de criação, não re-checado na qualificação (impossível violar hoje).
- Nomes por **username**; datas no fuso do navegador; OpenAPI via springdoc.

## 7. Próximo prompt de implementação recomendado

> *Sprint 2 / Slice 1: Criar uma Opportunity a partir de um Lead qualificado — semear a Opportunity
> com o interesse principal, o responsável e o histórico do Lead, sem duplicar dados; manter o Lead
> rastreável e ligado à Opportunity. Alternativa Sprint 1: edição/correção de qualificação com
> auditoria, ou uma visão de follow-up/agenda sobre `nextContactAt`. Incluir testes (unit +
> integração real + e2e) e relatório.*
