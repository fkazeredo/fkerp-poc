# Relatório — Sprint 1 / Slice 3: Detalhe do Lead (+ transições que produzem o histórico)

- **Data:** 2026-06-15
- **Branch:** `feature/crm-lead-detail` (a partir de `develop`)
- **Escopo entregue:** consulta do **detalhe do Lead** ponta a ponta, mais as ações de escrita que
  produzem o histórico que o detalhe mostra — **Qualificar**, **Marcar como perdido** (com motivo) e
  **Reatribuir** responsável.

## 1. O que foi implementado

- **Detalhe (`GET /api/leads/{id}`):** dados principais, contatos, origem, status, responsável,
  datas de criação/atualização, próximo contato, **histórico de interações**, **histórico de
  atribuições**, e os blocos de **qualificação** e **perda** quando existem. Ausente → **404**;
  existe-mas-não-visível → **403** (reaproveita `LeadAccessPolicy.canSee`).
- **Transições (escopo `crm:lead:update`):** `POST /api/leads/{id}/qualify`, `/lose`
  (motivo obrigatório e ativo) e `/reassign`. O agregado `Lead` aplica a máquina de estados
  (qualificar a partir de NEW/CONTACTED; perder a partir de qualquer não-perdido; reatribuir registra
  histórico; **LOST é terminal**) e preserva os desfechos. **`@Version`** (lock otimista) no Lead;
  edição concorrente → **409**.
- **Modelo:** nova entidade `LeadAssignment` (histórico de atribuições, gravado na criação com
  responsável e em cada reatribuição); colunas de qualificação/perda no `leads` (FK para
  `loss_reasons`); migração **V7** com `CHECK` (perdido exige motivo; qualificado exige data) e o
  escopo `crm:lead:update` no usuário dev.
- **Frontend:** página `/leads/:id` (`LeadDetailPage`) com os cartões/seções, ações condicionais
  (Qualificar/Marcar como perdido/Reatribuir) via diálogos, recarregando o detalhe após cada ação;
  o nome do lead na lista vira link para o detalhe.

## 2. Regras funcionais cobertas

- O detalhe **preserva o histórico comercial** (interações + atribuições; qualificação/perda
  mantidas).
- A **perda** continua visível quando o Lead está Perdido; a **qualificação** continua visível
  quando Qualificado.
- O usuário só abre Leads que pode ver (visibilidade dono + não-atribuído + gestor); não-visível →
  403.
- Nenhum comportamento de Opportunity/Customer/Sale/Booking/Finance/Customer Care.

## 3. Critérios de aceite cobertos

Abrir o detalhe (autorizado); mostrar dados/contato/origem/status/responsável; mostrar interações,
histórico de atribuições, qualificação e perda **quando existem**; usuários não autorizados não
acessam (401/403); criação e listagem continuam funcionando (regressões verdes). **Todos cobertos.**

## 4. Arquivos alterados (principais)

- Backend: `domain/crm` (Lead + `@Version` + qualify/markLost/reassign; LeadAssignment; views
  Detail/Interaction/Assignment/Qualification/Loss; exceptions; LeadAccessPolicy.canSee; LeadService
  detail/qualify/markLost/reassign), `application/api` (LeadController GET/{id} + 3 ações + DTOs),
  `infra/web` (HttpErrorMapping, GlobalExceptionHandler optimistic-lock 409), `infra/security`
  (SecurityConfig matcher), `messages.properties`, `db/migration/V7__lead_detail_transitions.sql`.
- Frontend: `core/api/lead.service.ts`, `features/leads/lead-detail/*`,
  `features/leads/lead-list/*` (link), `app.routes.ts`.

## 5. Testes / validações adicionados

- **Backend unit:** `LeadTest` (qualify/markLost/reassign + transições inválidas + histórico de
  atribuição); `LeadServiceTest` (detail 404/403/mapeamento).
- **Backend integração (Postgres real):** `LeadDetailApiIntegrationTest` (12) — detalhe com
  histórico, 404/403/401, qualify, lose (motivo obrigatório/ativo, terminal), reassign, escopo de
  escrita. `./mvnw verify` **verde: 76 testes**.
- **Frontend unit (Vitest, 45):** `LeadService` (detail/qualify/lose/reassign), `LeadDetailPage`
  (load, 403, gating por status, ações + toast).
- **E2E (Playwright, 8):** abrir detalhe pela lista; qualificar; marcar como perdido com motivo.
  Verde contra o stack vivo.

## 6. Gaps conhecidos

- `nextContactAt` segue **somente leitura** (sem agendamento ainda).
- Não há ação de **Contatar** (mover para CONTACTED) nesta fatia; o enum existe mas só NEW→QUALIFIED/
  LOST e reatribuição são exercidos.
- Reatribuir não está disponível para Leads **perdidos** (LOST terminal).
- Nomes (responsável/atores) exibidos por **username**.
- OpenAPI gerado pelo springdoc (sem contrato manual).

## 7. Próximo prompt de implementação recomendado

> *Sprint 1 / Slice 4: Follow-up & agendamento. Permitir registrar novas interações no histórico
> (ligação, WhatsApp, e-mail, nota, com resultado) e **agendar o próximo contato** (preenchendo
> `nextContactAt`, hoje só leitura); opcionalmente um passo CONTACTED. Sem Opportunity/Customer/Sale.
> Incluir testes (unit + integração real + e2e) e relatório.*
