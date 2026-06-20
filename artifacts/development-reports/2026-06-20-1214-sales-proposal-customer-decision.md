# Relatório — Sprint 3 / Slice 9: Registrar aceite ou recusa do cliente

- **Data:** 2026-06-20
- **Branch:** `feature/sales-proposal-customer-decision` (a partir de `develop`)
- **Versão:** 0.32.0 → **0.33.0** (MINOR — nova funcionalidade)
- **Escopo entregue:** uma Proposta **Enviada** pode ter a **decisão do cliente** registrada: **aceite**
  (Enviada → **Aceita**, com nota de confirmação opcional) ou **recusa do cliente** (Enviada → **Rejeitada**,
  com motivo obrigatório de um novo enum + nota opcional). Ambas registram **quem/quando** no Histórico de
  status. *Aceita* segue *open* (oferta vencedora; prepara o pedido comercial da próxima slice); *Rejeitada* é
  terminal (libera a Oportunidade).

## 1. O que foi implementado
**Backend**
- **`CustomerRejectionReason`** (enum fixo: PRICE_TOO_HIGH, CHOSE_COMPETITOR, TRAVEL_POSTPONED,
  TRAVEL_CANCELLED, CHANGED_DESTINATION, NO_RESPONSE, PRODUCT_MISMATCH, OTHER) — **distinto** do
  `ProposalRejectionReason` (rejeição interna).
- **`Proposal.acceptByCustomer(byUser, note)`** e **`declineByCustomer(byUser, reason, note)`** com
  `requireSent()`; ambos registram um `ProposalStatusChange` (SENT→ACCEPTED / SENT→REJECTED); o aceite grava
  `acceptanceNote`, a recusa grava `customerRejectionReason`/`customerRejectionNote` (campos novos) e é terminal.
  Exception nova: **`ProposalNotSentException`** (`proposal.not-sent`, **422**); a recusa **reusa**
  `ProposalRejectionReasonRequiredException` (motivo obrigatório, defesa em profundidade).
- **`ProposalDetail`** ganhou `acceptanceNote`/`customerRejectionReason`/`customerRejectionNote`.
  **`ProposalService`** ganhou `acceptByCustomer(...)`/`declineByCustomer(...)`. **`ProposalController`**:
  `POST /{id}/accept` (DTO `AcceptProposalRequest{note?}`) e `POST /{id}/decline`
  (DTO `DeclineProposalRequest{@NotNull reason, note?}`). **`SecurityConfig`**: os dois endpoints exigem
  `SCOPE_sales:proposal:update` (reusa o escopo de operação — **sem escopo novo**).
- **Flyway `V26__proposal_customer_decision.sql`**: colunas `acceptance_note`, `customer_rejection_reason`
  (+ CHECK do enum), `customer_rejection_note`. Sem seed de escopo.

**Frontend**
- `proposal.service`: tipo `CustomerRejectionReason`, métodos `accept(id, note)` / `decline(id, reason, note)` +
  campos no `ProposalDetail`.
- `proposal-detail`: botões **Registrar aceite** / **Registrar recusa** (quando `canDecide()` = operador +
  *Enviada*); **diálogo de aceite** (nota opcional) e **diálogo de recusa** (p-select de motivo obrigatório +
  anotação opcional, com o guard de alterações não salvas); no resumo, linha **"Nota do aceite"** (quando
  *Aceita*) e **"Motivo da recusa do cliente"** (quando *Rejeitada* pelo cliente, distinta da rejeição interna);
  o Histórico de status mostra a transição. Atalhos **`c`** (aceite) e **`x`** (recusa) + overlay `?`.

## 2. Regras funcionais cobertas
- Só *Enviada* pode ser aceita/recusada (senão **422** `proposal.not-sent`). Aceite registra **quem/quando**
  (Histórico) + nota opcional. Recusa registra **quem/quando** (Histórico) + **motivo** (obrigatório) + nota.
- Aceita → *Aceita* (segue *open*, prepara o pedido comercial); recusada → *Rejeitada* (terminal). Quem não
  opera a proposta (diretor consultivo / financeiro) **não** registra a decisão (**403**).
- Registrar a decisão **não** cria reserva (Booking), financeiro, comissão **nem pedido comercial** (só muda
  status + grava nota/motivo + histórico).

## 3. Critérios de aceite cobertos
Autorizado aceita uma enviada ✓ · aceite registra quem/quando ✓ · autorizado recusa uma enviada ✓ · recusa exige
motivo ✓ · Rascunho/Revisão/Aprovada/Rejeitada não podem ser aceitas diretamente ✓ · recusada não cria pedido
comercial ✓ · aceita não cria reserva/financeiro ✓ · comportamento existente preservado ✓.

## 4. Arquivos alterados (principais)
- **Backend (novos):** `model/CustomerRejectionReason.java`, `exception/ProposalNotSentException.java`,
  `api/dto/AcceptProposalRequest.java`, `api/dto/DeclineProposalRequest.java`,
  `db/migration/V26__proposal_customer_decision.sql`, `test/.../ProposalCustomerDecisionApiIntegrationTest.java`.
- **Backend (editados):** `Proposal.java`, `service/data/ProposalDetail.java`, `service/ProposalService.java`,
  `api/ProposalController.java`, `infra/security/SecurityConfig.java`, `infra/web/HttpErrorMapping.java`,
  `messages.properties`, `application.yml` (0.33.0); testes `ProposalTest`, `ProposalCreationApiIntegrationTest`
  (contrato +3 campos).
- **Frontend:** `core/api/proposal.service.ts`, `proposal-detail.{ts,html,css,spec}`, `core/layout/shell.html`
  (atalhos `c`/`x` no overlay `?`); e2e `proposal-creation.spec.ts` (estende a jornada até *Aceita*).
- **Docs:** manual en-US + pt-BR (9.7), este relatório.

## 5. Testes / validações
- **Backend `./mvnw verify`: 427 verdes** (ArchUnit/Modulith + Testcontainers + V26 + `HttpErrorMappingTest`;
  Spotless/Checkstyle limpos). Domínio (`ProposalTest`): aceite SENT→ACCEPTED + nota + segue *open*; aceite sem
  nota; recusa SENT→REJECTED + motivo/nota + terminal; recusa sem motivo →
  `ProposalRejectionReasonRequiredException`; aceite/recusa fora de SENT → `ProposalNotSentException`. Novo
  `ProposalCustomerDecisionApiIntegrationTest` (12): aceitar → 200 ACCEPTED + nota + histórico; aceitar sem nota;
  recusar com motivo → 200 REJECTED + motivo/nota; recusar sem motivo → **400**; aceitar/recusar fora de SENT →
  **422** `proposal.not-sent`; vendedor decide a própria enviada → 200; diretor/financeiro → **403**; não
  autenticado → **401**; aceita segue *open* (nova proposta → **409**); recusada libera a Oportunidade (**201**).
- **Frontend `ng test`: 282 verdes** (`canDecide` por escopo+status; aceite com/sem nota; recusa exige motivo;
  DOM dos botões e das linhas de resumo). **`ng build`** verde.
- **E2E `npm run e2e`: 39 verdes** — `proposal-creation` agora segue até **Registrar aceite** (atalho `c`, nota,
  confirma → *Aceita* + "Nota do aceite" + histórico). A jornada continua `test.slow()`. O `proposal-rejection`
  (pesado, pré-existente) permanece *flaky* e passa no retry (`retries:1`).
- Pilha dev recriada; **V26 aplicada**; `/api/version` = 0.33.0.

## 6. Gaps conhecidos
- **Registrar a decisão NÃO** cria reserva, financeiro, comissão **nem pedido comercial** — só muda status +
  grava nota/motivo + histórico. A **aceita** apenas **prepara** o pedido comercial (próxima slice).
- **Recusa do cliente** reusa o status terminal **REJECTED** (como a rejeição interna), distinta pelo campo
  `customerRejectionReason` + o caminho no histórico (SENT→REJECTED vs READY_FOR_REVIEW→REJECTED).
- **Motivos = enum fixo** (não cadastro gerenciável); notas **opcionais** (decisões do dono).
- **Quem decide = quem tem `sales:proposal:update`** e pode ver a proposta — reusa o escopo de operação (sem
  escopo novo).
- **Fora de escopo:** portal do cliente, assinatura digital, expiração/reativação automática, cancelamento de
  reserva/financeiro. Sem "desfazer decisão" nesta slice.
- A jornada e2e de proposta é longa (`test.slow()`); o `proposal-rejection` é *flaky* sob paralelismo (absorvido
  por `retries:1`, sem afrouxar asserções) — candidato a estabilização futura.

## 7. Próximo prompt recomendado
> **SLICE 10: Gerar o pedido comercial a partir de uma Proposta aceita.** A partir de uma Proposta *Aceita*,
> criar um **pedido comercial** (Commercial Order) — provavelmente um novo agregado em `domain.sales`
> (`CommercialOrder` com referência à Proposta/Oportunidade/Lead, itens copiados como snapshot, status inicial)
> — com seu próprio escopo (`sales:order:*`) e tela. Decidir com o dono: numeração do pedido, o que é copiado da
> proposta (snapshot vs referência), e o status inicial. **Ainda sem** reserva/financeiro/comissão reais. Cobrir
> feliz + tristes (proposta não aceita, sem permissão, pedido duplicado) e manter todo o ciclo da proposta
> funcionando.
