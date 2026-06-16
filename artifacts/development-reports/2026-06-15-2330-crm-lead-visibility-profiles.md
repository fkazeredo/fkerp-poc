# Relatório — Sprint 1 / Slice 8: Visibilidade e operação de Leads por perfil

- **Data:** 2026-06-15
- **Branch:** `feature/crm-lead-visibility` (a partir de `develop`)
- **Escopo entregue:** regras de **visibilidade e operação por perfil**, modeladas como **escopos
  OAuth + política** (não um enum de papéis — CLAUDE.md §10), seguindo o padrão de mercado que separa
  o **nível de leitura** (próprios → próprios+pool → todos) das **permissões de operação**.

## 1. O que foi implementado

- **Terceiro nível de leitura**: novo escopo `crm:lead:read:unassigned`. Os três níveis (aditivos):
  `crm:lead:read` (só próprios), `+ crm:lead:read:unassigned` (também o pool de não-atribuídos),
  `crm:lead:read:all` (todos). `LeadAccessPolicy.canSee/visibleTo` ganharam o parâmetro
  `canSeeUnassigned`; o `LeadService` (list/detail/qualify/lose/reassign/recordInteraction →
  `loadVisible`) e o `LeadController` propagam o nível.
- **Gate de leitura hierárquico** no `SecurityConfig`: qualquer nível de leitura (read / read:unassigned
  / read:all) passa no GET de lista/detalhe; a **política** decide *quais* leads — filtros e detalhe
  não furam a visibilidade (Specification no nível da query + `canSee`).
- **Consulta-apenas** e **sem acesso** já eram garantidos pelos gates de escopo existentes: operar
  exige create/update/assign; sem nenhum escopo `crm:lead:*` → 403 em tudo.
- **Migração V11**: `vendedor` (inside-seller) ganha `read:unassigned` (preserva o self-claim da
  Slice 4); seeds `representante` (só próprios), `diretor` (consulta: read:all), `financeiro` (sem
  escopos = sem acesso, representa Finance/HR/IT).
- **Frontend (gating, backend continua a autoridade)**: `AuthService` ganhou `canSeeLeads()`
  (qualquer nível de leitura), `canCreateLead()`, `canOperateLead()`. O detalhe esconde toda a barra
  de ações para consulta-apenas (gate em `crm:lead:update`); "Novo lead" some sem `create`; um
  `crmReadGuard` bloqueia as rotas `/leads*` e a sidebar/home escondem o que o usuário não pode
  acessar, com aviso de "sem acesso".

## 2. Regras funcionais cobertas

Admin/Gerente Comercial: veem e operam todos. Diretoria/Marketing: veem todos (consulta, sem operar).
Call Center/Sellers: próprios + pool. Representantes: só os próprios — nunca veem leads de outros.
Finance/HR/IT: sem acesso. Filtros e detalhe **não furam** a visibilidade. Usuários de departamentos
não relacionados não acessam. Consulta-apenas não executa ações.

## 3. Critérios de aceite cobertos (→ onde + teste que trava)

- Representante vê só os próprios → `LeadAccessPolicy`/`visibleTo`; `LeadVisibilityApiIntegrationTest.
  representativeSeesOnlyOwnLeads` + `LeadListApiIntegrationTest.representativeSeesOnlyOwnNotThePool`.
- Representante não opera leads de outros → `loadVisible`; `...representativeCannotOperateOthersButOperatesOwn`
  (403 em outro, 200 no próprio) + `...representativeCannotOpenOthersOrThePool` (detalhe 403).
- Gerente vê todos / Admin opera todos → read:all + operate; `...managerSeesEveryLead`.
- Diretoria consulta mas não opera → read:all sem operate; `...directorConsultsEverythingButCannotOperate`
  (lista/detalhe 200; create/qualify/lose/reassign/interaction → 403).
- Finance/HR/IT sem acesso → sem escopos; `...financeHasNoAccessToLeads` (403 em lista/detalhe/criar).
- Visibilidade em listas/detalhe/filtros/ações → `...filtersDoNotBypassVisibility` + os acima.
- Sprint 1 anterior segue funcionando → suíte completa verde (vendedor com read:unassigned mantém
  Slice 4).

## 4. Arquivos alterados (principais)

- **Backend:** `domain/crm/LeadAccessPolicy`, `LeadService`, `application/api/LeadController`,
  `infra/security/SecurityConfig` (gate read-tier), `db/migration/V11__lead_visibility_profiles.sql`
  (novo). **Sem novo endpoint, sem DTO novo.**
- **Frontend:** `core/auth/auth.service.ts` (helpers), `core/auth/crm-read.guard.ts` (novo),
  `app.routes.ts` (guard), `core/layout/shell.{ts,html}` (nav/CTA), `features/home/*`,
  `features/leads/lead-list/*` (Novo gate), `features/leads/lead-detail/*` (ações gate).

## 5. Testes / validações adicionados

- **Backend unit:** `LeadAccessPolicyTest` (3 níveis); `LeadServiceTest` (assinaturas atualizadas).
- **Backend integração (Postgres real):** `LeadVisibilityApiIntegrationTest` (7) + `LeadListApiIntegrationTest`
  (own-only vs pool). `./mvnw verify` **verde: 139 testes**.
- **Frontend unit (Vitest, 72):** `crm-read.guard.spec`, `lead-detail.spec` (consulta-apenas esconde
  ações), `auth.service.spec` (níveis de leitura/operação).
- **E2E (Playwright, stack isolada):** `lead-visibility.spec` — diretor consulta um lead **read-only**
  (sem botões de ação, sem Novo lead); `financeiro` é bloqueado (sem nav de Leads, guard redireciona,
  aviso de sem acesso).

## 6. Gaps conhecidos

- **Consultar pendências / indicadores** e **análise de origem (Marketing)** não existem como telas
  ainda — o modelo de visibilidade já está pronto para quando forem construídos.
- **Admin == Gerente Comercial** para Leads (mesmo bundle de escopos; `comercial` representa ambos).
- `read:unassigned` é sempre pareado com `read` (não é um nível isolado); a hierarquia é por convenção
  de seed, não imposta no banco.
- Sem tela de administração de usuários/escopos (perfis são semeados via Flyway, DEV-only).

## 7. Próximo prompt de implementação recomendado

> *Sprint 2 / Slice 1: criar uma Opportunity a partir de um Lead qualificado, honrando estes níveis
> de visibilidade. Alternativa Sprint 1: uma visão de "pendências/agenda" (follow-ups por
> `nextContactAt`) ou indicadores por origem, ambos respeitando as tiers de leitura; ou uma tela de
> administração de usuários/escopos. Incluir testes (unit + integração real + e2e) e relatório.*
