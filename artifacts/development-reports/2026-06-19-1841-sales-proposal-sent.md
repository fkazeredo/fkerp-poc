# Relatório — Sprint 3 / Slice 8: Registrar Proposta enviada ao cliente

- **Data:** 2026-06-19
- **Branch:** `feature/sales-proposal-sent` (a partir de `develop`)
- **Versão:** 0.31.0 → **0.32.0** (MINOR — nova funcionalidade)
- **Escopo entregue:** uma Proposta **Aprovada** pode ser **marcada como enviada** ao cliente
  (Aprovada → **Enviada**), registrando **quem/quando** (Histórico de status) e um **canal de envio**
  **opcional** (lista fixa). *Enviada* é não-terminal: a Proposta **continua disponível** para a decisão do
  cliente (slice futura).

## 1. O que foi implementado
**Backend**
- **`SendingChannel`** (enum fixo: EMAIL, WHATSAPP, PHONE_PRESENTATION, IN_PERSON_PRESENTATION, OTHER) —
  espelha `ProposalRejectionReason`.
- **`Proposal.markAsSent(byUser, channel)`** com `requireApproved()` (status == APPROVED); grava
  `sendingChannel` (campo novo, **nullable** — canal opcional), registra um `ProposalStatusChange`
  (APPROVED → SENT) e move para *Enviada*. Exception nova: **`ProposalNotApprovedException`**
  (`proposal.not-approved`) → **422**.
- **`ProposalDetail`** ganhou `sendingChannel`. **`ProposalService.markAsSent(...)`** (espelha `approve`).
  **`ProposalController`**: `POST /{id}/send` (DTO `MarkProposalSentRequest{ channel }` — canal **sem**
  `@NotNull`). **`SecurityConfig`**: `POST /api/proposals/*/send` exige `SCOPE_sales:proposal:update`
  (reusa o escopo de operação — **sem escopo novo**).
- **Flyway `V25__proposal_sent.sql`**: coluna `sending_channel` + CHECK do enum. Sem seed de escopo.

**Frontend**
- `proposal.service`: tipo `SendingChannel`, método `markSent(id, channel)` e campo `sendingChannel` no
  `ProposalDetail`.
- `proposal-detail`: botão **Marcar como enviada** (quando `canSend()` = operador + *Aprovada*); **diálogo de
  envio** (p-select de canal **opcional**, com clear, e o guard de alterações não salvas); linha **"Canal de
  envio"** no resumo quando enviada; o Histórico de status mostra *Aprovada → Enviada*. Atalho **`m`** + overlay
  `?`.

## 2. Regras funcionais cobertas
- Só **Aprovada** pode ser marcada como enviada (senão **422** `proposal.not-approved`). Registra a **data de
  envio** e o **usuário** (no `ProposalStatusChange` APPROVED→SENT do Histórico). O **canal** pode ser
  registrado (opcional) — ou deixado em branco.
- Rascunho / Pronta para revisão / Rejeitada **não** podem ser marcadas como enviadas. A *Enviada* **continua
  disponível** para a decisão do cliente (`isOpen()` permanece verdadeiro → a Oportunidade segue com proposta
  ativa).
- Marcar como enviada **não** dispara e-mail/WhatsApp/ligação reais, **não** gera PDF/assinatura e **não** cria
  aceite do cliente, pedido comercial, reserva, financeiro nem comissão.

## 3. Critérios de aceite cobertos
Autorizado marca Aprovada→Enviada ✓ · data de envio registrada ✓ · usuário do envio registrado ✓ · canal pode
ser registrado ✓ · Rascunho/Revisão/Rejeitada não podem ser enviadas ✓ · Enviada segue disponível para a
decisão do cliente ✓ · nenhum pedido comercial criado ✓ · comportamento existente preservado ✓.

## 4. Arquivos alterados (principais)
- **Backend (novos):** `model/SendingChannel.java`, `exception/ProposalNotApprovedException.java`,
  `api/dto/MarkProposalSentRequest.java`, `db/migration/V25__proposal_sent.sql`.
- **Backend (editados):** `Proposal.java`, `service/data/ProposalDetail.java`, `service/ProposalService.java`,
  `api/ProposalController.java`, `infra/security/SecurityConfig.java`, `infra/web/HttpErrorMapping.java`,
  `messages.properties`, `application.yml` (0.32.0); testes `ProposalTest`, `ProposalApprovalApiIntegrationTest`
  (casos de envio + helper `bringToApproved`), `ProposalCreationApiIntegrationTest` (contrato +`sendingChannel`).
- **Frontend:** `core/api/proposal.service.ts`, `proposal-detail.{ts,html,css,spec}`, `core/layout/shell.html`
  (atalho `m` no overlay `?`); e2e `proposal-creation.spec.ts` (estende a jornada até *Enviada*).
- **Docs:** manual en-US + pt-BR (9.6), este relatório.

## 5. Testes / validações
- **Backend `./mvnw verify`: 410 verdes** (ArchUnit/Modulith + Testcontainers + V25 + `HttpErrorMappingTest`;
  Spotless/Checkstyle limpos). Domínio (`ProposalTest`): markAsSent APPROVED→SENT + canal + transição; canal
  **null** (opcional); fora de APPROVED → `ProposalNotApprovedException`. Integração
  (`ProposalApprovalApiIntegrationTest`): gerente envia → **200** SENT + `sendingChannel` + histórico
  APPROVED→SENT (by=comercial); envio **sem canal** → 200 (canal ausente); **vendedor** envia a **própria**
  aprovada → 200; fora de APPROVED (rascunho / em revisão) → **422** `proposal.not-approved`; **diretor**
  (read:all sem update) → **403**; **financeiro** → **403**; não autenticado → **401**; *Enviada* segue *open*
  (a Oportunidade não origina nova proposta → **409**). Contrato atualizado com `sendingChannel`.
- **Frontend `ng test`: 274 verdes** (`canSend` por escopo+status; `markSent` com e sem canal; DOM do botão e
  da linha "Canal de envio"). **`ng build`** verde.
- **E2E `npm run e2e`: 39 verdes** — `proposal-creation` agora segue até **Marcar como enviada** (abre pelo
  atalho `m`, escolhe *E-mail*, confirma → *Enviada* + canal + histórico). A jornada foi marcada `test.slow()`
  (é longa: qualificar → funil → proposta → itens → submeter → aprovar → enviar). O `proposal-rejection` (pesado,
  pré-existente) ficou *flaky* sob carga e passou no retry (`retries:1`).
- Pilha dev recriada; **V25 aplicada**; `/api/version` = 0.32.0.

## 6. Gaps conhecidos
- **Sem comunicação externa real:** não dispara e-mail/WhatsApp/ligação, não gera PDF/assinatura, não tem portal
  do cliente nem lembrete automático (fora de escopo). É só um **registro** de status + canal.
- **Canal opcional** e **enum fixo** (decisão do dono) — virar cadastro gerenciável/obrigatório é evolução
  futura se necessário.
- **Quem envia = quem tem `sales:proposal:update`** e pode ver a proposta (vendedor/representante/gerente) —
  reusa o escopo de operação (sem escopo novo, decisão do dono).
- **Sem "desfazer envio"** nesta slice. A **decisão do cliente** (aceitar/recusar a proposta enviada) é a
  **próxima** slice; *Enviada* fica *open* para isso.
- As jornadas e2e de proposta são longas/pesadas (uma marcada `test.slow()`; outra absorvida por `retries:1`,
  sem afrouxar asserções) — candidato a estabilização futura (menos workers ou seed direto).

## 7. Próximo prompt recomendado
> **SLICE 9: Registrar a decisão do cliente sobre a Proposta enviada.** Implemente, a partir de *Enviada*, o
> **aceite** (→ ACCEPTED) e a **recusa** do cliente (→ um status terminal-negativo), registrando quem/quando no
> histórico e, na recusa, um motivo (decidir com o dono: reusar o enum de rejeição interna ou um conjunto
> próprio de motivos comerciais). Gating por escopo (decidir: reusar `sales:proposal:update` ou novo
> `sales:proposal:decide`). **Sem** gerar pedido comercial/venda/reserva/financeiro ainda. Cobrir feliz + tristes
> (sem permissão, fora de SENT) e manter enviar/aprovar/rejeitar/lista/detalhe funcionando.
