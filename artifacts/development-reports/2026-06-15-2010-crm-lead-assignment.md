# Relatório — Sprint 1 / Slice 4: Atribuir um responsável a um Lead (autoridade)

- **Data:** 2026-06-15
- **Branch:** `feature/crm-lead-assignment` (a partir de `develop`)
- **Escopo entregue:** a **camada de autorização** sobre a mecânica de atribuição/reatribuição que
  já existia (Slice 3). Gestores comerciais e administradores atribuem/reatribuem a **qualquer**
  usuário; um representante/vendedor só pode **assumir** (autoatribuir) um Lead, nunca atribuí-lo a
  outra pessoa nem desatribuir. A UI reflete a autoridade.

## 1. O que foi implementado

- **Autoridade por escopo:** novo escopo OAuth **`crm:lead:assign`** = autoridade plena de
  atribuição (gestores/admins). Consistente com a autorização por escopos já existente
  (CLAUDE.md §10, "sem um segundo mecanismo de autorização") — **sem** enum de papéis novo.
- **Política de domínio `LeadAssignmentPolicy`** (`domain.crm`): `canAssign(actorId,
  toResponsibleId, hasAssignScope)` → com `crm:lead:assign`, qualquer alvo (inclusive `null` =
  desatribuir); sem o escopo, o alvo precisa ser **o próprio ator** (`toResponsibleId != null &&
  equals(actorId)`). Combinada com a visibilidade (Slice 3), o vendedor assume Leads não-atribuídos
  e nada além disso.
- **`POST /api/leads/{id}/reassign`** segue **gated em `crm:lead:update`** (para o vendedor alcançar
  o endpoint e se autoatribuir); a regra fina vive na política, no serviço. Violação →
  **`LeadAssignmentNotAllowedException`** (código `lead.assignment-not-allowed`) → **403**.
- **Migração V8** (`V8__lead_assignment_authority.sql`): concede `crm:lead:assign` ao gestor dev
  `…-001` e cria um usuário representante **`vendedor`** (`…-002`, ativo, BCrypt de `vendedor123`)
  com `crm:lead:read` + `crm:lead:create` + `crm:lead:update` (sem `read:all`, sem `assign`).
- **Frontend:** `AuthService` decodifica o JWT em memória uma vez (`scopes()`, `hasScope()`,
  `userId()` a partir de `sub`) — apenas para gating de UX; o backend continua a fonte da verdade.
  Em `lead-detail`: com `crm:lead:assign` aparece **Reatribuir** (select completo); sem o escopo, um
  Lead não-atribuído (e não perdido) mostra **Assumir**, que chama `reassign(id, currentUserId)`.

## 2. Regras funcionais cobertas

- Gestor/admin (com `crm:lead:assign`) **atribui e reatribui a qualquer** usuário ativo, e
  desatribui (`null`).
- Representante **só autoatribui** (assumir): atribuir a outro usuário ou desatribuir → **403**
  `lead.assignment-not-allowed`.
- A **visibilidade** (Slice 3) ainda se aplica primeiro: ausente → 404; existe-mas-não-visível →
  403. O alvo da atribuição precisa ser um usuário **ativo** (caso contrário 422, regra Slice 3).
- O **histórico de atribuições** continua sendo registrado a cada mudança; o responsável aparece na
  lista e no detalhe.
- Nenhum comportamento de Opportunity/Customer/Sale/Booking/Finance/Customer Care.

## 3. Critérios de aceite cobertos

Gestor atribui Lead não-atribuído a outro usuário (200) e reatribui (200); representante assume um
Lead não-atribuído (200) mas não atribui a outro (403) nem desatribui (403); alvo inativo/inexistente
→ 422; responsável aparece em lista e detalhe; histórico preservado; UI mostra **Reatribuir** ao
gestor e **Assumir** ao representante. Slices 1–3 seguem verdes. **Todos cobertos.**

## 4. Arquivos alterados (principais)

- **Backend:** `domain/crm/LeadAssignmentPolicy.java` (novo),
  `domain/crm/LeadAssignmentNotAllowedException.java` (novo), `domain/crm/LeadService.java`
  (parâmetro `canAssign` no `reassign` + enforcement), `application/api/LeadController.java`
  (passa `hasScope("crm:lead:assign")`), `infra/web/HttpErrorMapping.java` (403),
  `resources/messages.properties` (i18n), `db/migration/V8__lead_assignment_authority.sql` (novo).
- **Frontend:** `core/auth/auth.service.ts` (decode de scopes/sub + `hasScope`/`userId`),
  `features/leads/lead-detail/lead-detail.ts` + `.html` (gating Reatribuir/Assumir + `claim()`).

## 5. Testes / validações adicionados

- **Backend unit:** `LeadAssignmentPolicyTest` (3) — autoridade plena atribui qualquer um (inclusive
  `null`); sem autoridade só autoatribui; sem autoridade não atribui a outro nem `null`.
  `LeadServiceTest.reassignRejectsWhenAssignmentNotAllowed` (visível mas sem autoridade → exceção).
- **Backend integração (Postgres real):** `LeadAssignmentApiIntegrationTest` (7) — login com os dois
  seed users: gestor atribui não-atribuído → vendedor (200, `responsibleName`), gestor reatribui
  (200), vendedor autoatribui (200), vendedor → outro (403), vendedor desatribui (403), alvo
  inativo/inexistente (422), responsável no detalhe. `./mvnw verify` **verde: 87 testes**.
- **Frontend unit (Vitest, 54):** `auth.service.spec` (decode de `scope`/`sub`; `hasScope`; token
  ausente/malformado degrada sem erro); `lead-detail.spec` (gestor vê Reatribuir e não Assumir; rep
  vê Assumir só em Lead não-atribuído e não perdido; `claim()` chama `reassign(id, ownId)` e
  notifica; sem `userId` não chama).
- **E2E (Playwright, 10):** gestor (`comercial`) reatribui um Lead não-atribuído a `vendedor`;
  representante (`vendedor`) abre um Lead não-atribuído → sem **Reatribuir**, com **Assumir** →
  assume → responsável vira o representante e **Assumir** some. Verde contra o stack vivo.

## 6. Gaps conhecidos

- O representante decide a autoria por **escopos no token** (UX); a autoridade real é sempre
  reavaliada no backend — sem endpoint `/me` (decisão consciente).
- Reatribuição segue indisponível para Leads **perdidos** (LOST terminal, Slice 3).
- Nomes (responsável/atores) exibidos por **username**; sem cadastro de pessoas ainda.
- `vendedor` / `vendedor123` é usuário **dev** (seed via Flyway); não é gestão de usuários real.
- OpenAPI gerado pelo springdoc (sem contrato manual).

## 7. Próximo prompt de implementação recomendado

> *Sprint 1 / Slice 5: Follow-up & agendamento. Permitir registrar novas interações no histórico
> (ligação, WhatsApp, e-mail, nota, com resultado) e **agendar o próximo contato** (preenchendo
> `nextContactAt`, hoje só leitura), opcionalmente um passo CONTACTED. Alternativa: uma tela de
> administração de usuários/escopos para conceder `crm:lead:assign` sem migração. Incluir testes
> (unit + integração real + e2e) e relatório.*
