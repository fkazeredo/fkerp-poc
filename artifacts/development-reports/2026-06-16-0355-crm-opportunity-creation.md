# Relatório — Sprint 2 / Slice 1 (CRM2-001): Criar Oportunidade a partir de Lead Qualificado

- **Data:** 2026-06-16
- **Branch:** `feature/crm-opportunity-creation` (a partir de `develop`)
- **Versão:** **0.13.0** (feature nova → MINOR; bump por entrega, conforme a regra do `CLAUDE.md` §14)
- **Escopo entregue:** criação de uma **Oportunidade comercial** a partir de um Lead **QUALIFIED**,
  reaproveitando o contrato de `artifacts/lead-to-opportunity-handoff.md`. **Não** cria Proposal/Sale/
  Sales Order/Booking/Finance/Commission/Customer; sem pipeline/atividades/perda/indicadores (próximas
  fatias). O Lead **não** é alterado (permanecem separados).

## 1. O que foi implementado

- **Domínio (`domain.crm`):** agregado `Opportunity` (referencia `leadId`; semeia `origin`, `name`,
  `responsiblePersonId` e `mainInterest` do lead; campos novos opcionais `productType`,
  `estimatedValue`, `expectedCloseDate`, `notes`; `stage`; `@Version`; auditoria). Fábrica
  `Opportunity.createFromLead(...)` exige lead QUALIFIED e define `stage = NEW_OPPORTUNITY`. Enum
  `OpportunityStage` (NEW_OPPORTUNITY, DISCOVERY, PRODUCT_FIT, READY_FOR_PROPOSAL, LOST — só
  NEW_OPPORTUNITY usado nesta fatia). `OpportunityRepository.findByLeadId`. `CreateOpportunityCommand`.
  Exceções `LeadNotQualifiedForOpportunityException` (422), `OpportunityAlreadyExistsForLeadException`
  (409, com o id existente). Evento `OpportunityCreated`.
- **Application service `OpportunityService.create`:** lead existe (404) → caller pode ver o lead
  (`LeadAccessPolicy.canSee`, 403) → lead QUALIFIED (422) → lead ainda sem oportunidade (409) →
  responsável (override validado; default = o do lead) → cria, salva, publica evento.
- **Delivery:** `POST /api/opportunities` (`OpportunityController`) → **201** `{id, stage}` +
  `Location`. DTOs `OpportunityCreateRequest`/`OpportunityResponse`.
- **Segurança/i18n/persistência:** novo escopo `crm:opportunity:create` (gate no `SecurityConfig`);
  `HttpErrorMapping` (422/409); chaves i18n `opportunity.lead-not-qualified` /
  `opportunity.already-exists-for-lead`; migração `V13__opportunities.sql` (tabela com `lead_id UNIQUE`,
  CHECKs de stage/valor, índices) + seed do escopo p/ comercial/vendedor/representante.
- **Frontend:** `opportunity.service.create`; `AuthService.canCreateOpportunity()`; botão **"Criar
  oportunidade"** no detalhe de um lead **QUALIFIED** + diálogo (tipo de produto, valor estimado,
  previsão, responsável opcional, nota) → toast de sucesso; atalho **`o`**. O lead não é alterado.

## 2. Regras funcionais cobertas

Só QUALIFIED origina; New/Contacted/Lost não; preserva origem, responsável (default) e interesse
principal do lead; começa em NEW_OPPORTUNITY; **uma oportunidade por lead**; não cria Proposal/Customer/
Sale/Booking/Finance; criador precisa do escopo e de **ver o lead**.

## 3. Critérios de aceite cobertos (→ teste que trava)

- Usuário autorizado cria a partir de QUALIFIED → `OpportunityCreationApiIntegrationTest.createsAnOpportunityFromAQualifiedLead` + e2e.
- New/Contacted/Lost **não** originam → `rejectsCreatingFromANewLead` / `…ContactedLead` / `…LostLead` (422).
- QUALIFIED origina; começa em NEW_OPPORTUNITY → idem (asserção `$.stage` + repositório).
- Preserva origem/responsável (default) → `OpportunityServiceTest` (`saved.origin`/`responsiblePersonId`) + integração (responsável/mainInterest).
- Nenhuma Proposal/Customer/Sale/Booking/Finance/Commission → estrutural (não existem) + escopo restrito.
- Sprint 1 segue funcionando → suíte completa verde.
- (Extras) duplicada → 409 com id; sem escopo → 403; representante em lead de outro → 403.

## 4. Arquivos alterados (principais)

- **Backend (novos):** `domain/crm/{Opportunity, OpportunityStage, OpportunityRepository,
  CreateOpportunityCommand, OpportunityService, OpportunityCreated, LeadNotQualifiedForOpportunityException,
  OpportunityAlreadyExistsForLeadException}`; `application/api/OpportunityController` +
  `dto/{OpportunityCreateRequest, OpportunityResponse}`; `db/migration/V13__opportunities.sql`; testes
  `OpportunityServiceTest`, `OpportunityCreationApiIntegrationTest`. **Editados:** `SecurityConfig`,
  `HttpErrorMapping`, `messages.properties`, `application.yml` (versão).
- **Frontend (novos):** `core/api/opportunity.service.ts` + `.spec`, `e2e/opportunity-creation.spec.ts`.
  **Editados:** `auth.service.ts`, `lead-detail.{ts,html}`, `shell.html` (atalho), `version.service.spec`.
- **Infra/docs:** `compose.yaml` (versão), `CLAUDE.md` §14 (regra de versionamento por entrega),
  `artifacts/release-notes/v0.13.0.md` (nova).

## 5. Testes / validações

- **`./mvnw verify` verde: 178** (Postgres real) — `OpportunityServiceTest` (5) + `OpportunityCreationApiIntegrationTest` (7).
- **`npm test` verde: 88** (+`opportunity.service.spec`). **E2E (stack isolada) verde: 26**
  (+`opportunity-creation`). Dev reconstruído; `GET /api/version` = **0.13.0**.

## 6. Suposições

- **Moeda** do valor estimado assumida BRL (sem VO de Money por ora). **`name`** da oportunidade = nome
  do lead. **Contato/histórico** do lead acessíveis via `leadId` (não duplicados), conforme o handoff.
  O responsável default (do lead) é confiado sem revalidação; só o override é validado (como na criação
  de lead).

## 7. Gaps conhecidos

- **Sem GET de Oportunidade** (lista/detalhe = CRM2-002/004): criada via UI, ainda não navegável; a
  integração valida pelo repositório. Pipeline/atividades/perda/indicadores nas próximas fatias.
- **`.env`/`.env.example`** fixam `APP_VERSION=0.12.0` (você adicionou) e são protegidos de leitura
  aqui — **atualizar para `0.13.0`** para o runtime refletir (os defaults em `compose.yaml`/
  `application.yml` já estão em 0.13.0; o smoke do dev usou `APP_VERSION=0.13.0` para sobrepor o pin).

## 8. Próximo prompt recomendado

> *Sprint 2 / Slice 2 (CRM2-002): **Consultar lista operacional de Oportunidades** — `GET /api/opportunities`
> paginado, com visibilidade por perfil (própria política de acesso da Oportunidade, espelhando as tiers
> do Lead), e a tela de lista no frontend. Incluir testes (unit + integração real + e2e), bumpar a versão
> (→ 0.14.0) com release note, e relatório.*
