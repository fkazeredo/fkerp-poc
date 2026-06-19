# Relatório — Sprint 3 / Slice 7: Aprovação / rejeição interna da Proposta

- **Data:** 2026-06-19
- **Branch:** `feature/sales-proposal-approval` (a partir de `develop`)
- **Versão:** 0.30.0 → **0.31.0** (MINOR — nova funcionalidade)
- **Escopo entregue:** uma Proposta em *Pronta para revisão* pode ser **aprovada** (→ Aprovada) ou
  **rejeitada** com motivo (→ Rejeitada) por um **aprovador** (o Gerente). As transições entram no Histórico
  de status (quem/quando); a rejeição guarda o **motivo** (lista fixa) + anotação opcional.

## 1. O que foi implementado
**Backend**
- **`ProposalRejectionReason`** (enum fixo: PRICE_TOO_HIGH, DISCOUNT_OUT_OF_POLICY, INCOMPLETE_INFORMATION,
  TERMS_NOT_ACCEPTABLE, VALIDITY_TOO_SHORT, DUPLICATE, OTHER) — espelha `OpportunityLossReason`.
- **`Proposal.approve(byUser)`** e **`reject(byUser, reason, note)`** com `requireUnderReview()`; ambos
  registram um `ProposalStatusChange`; a rejeição grava `rejectionReason`/`rejectionNote` (campos novos) e é
  terminal (libera a Oportunidade). Exceptions novas: `ProposalNotUnderReviewException`
  (`proposal.not-under-review`) e `ProposalRejectionReasonRequiredException` (`proposal.rejection-reason-required`),
  ambas **422**.
- **`ProposalDetail`** ganhou `rejectionReason`/`rejectionNote`. **`ProposalService`** ganhou `approve(...)` e
  `reject(...)`. **`ProposalController`**: `POST /{id}/approve` e `POST /{id}/reject` (DTO `RejectProposalRequest`
  com `@NotNull reason`). **`SecurityConfig`**: os dois endpoints exigem `SCOPE_sales:proposal:approve`.
- **Flyway `V24__proposal_approval.sql`**: colunas `rejection_reason`/`rejection_note` + CHECK do enum; e
  **seed** do escopo `sales:proposal:approve` para o **Gerente** (user 001).

**Frontend**
- `auth.service`: `canApproveProposal()` (`sales:proposal:approve`). `proposal.service`: `approve(id)` e
  `reject(id, reason, note)` + tipo `ProposalRejectionReason` + campos no `ProposalDetail`.
- `proposal-detail`: botões **Aprovar** e **Rejeitar** (quando `canApprove()` = aprovador + Pronta para revisão);
  **diálogo de rejeição** (p-select de motivo obrigatório + anotação opcional, com o guard de alterações não
  salvas); linha **"Motivo da rejeição"** no resumo quando *Rejeitada*; o Histórico de status (Slice 5) mostra
  a transição. Atalhos **`a`** (aprovar) e **`r`** (rejeitar) + overlay `?`.

## 2. Regras funcionais cobertas
- Só *Pronta para revisão* pode ser aprovada/rejeitada (senão **422**). Aprovação registra **quem/quando**
  (Histórico). Rejeição registra **quem/quando** (Histórico) + **motivo** (obrigatório) + anotação.
- Aprovada → *Aprovada*; rejeitada → *Rejeitada* (terminal, **não** enviada ao cliente). Quem não é aprovador
  (vendedor/representante/diretor/financeiro) **não** aprova/rejeita (**403**) — logo não aprova as próprias.
- Aprovar/rejeitar **não** cria Order/Booking/Finance/Commission (só muda status + grava motivo/histórico).

## 3. Critérios de aceite cobertos
Aprovador aprova em revisão ✓ · vira Aprovada ✓ · registra aprovador+data ✓ · aprovador rejeita em revisão ✓ ·
rejeição exige motivo ✓ · rejeitada não vai ao cliente ✓ · não-autorizados não aprovam/rejeitam ✓ · nenhum
Commercial Order/Booking/Finance/Commission ✓ · comportamento existente preservado ✓.

## 4. Arquivos alterados (principais)
- **Backend (novos):** `model/ProposalRejectionReason.java`, `exception/ProposalNotUnderReviewException.java`,
  `exception/ProposalRejectionReasonRequiredException.java`, `api/dto/RejectProposalRequest.java`,
  `db/migration/V24__proposal_approval.sql`, `test/.../ProposalApprovalApiIntegrationTest.java`.
- **Backend (editados):** `Proposal.java`, `service/data/ProposalDetail.java`, `service/ProposalService.java`,
  `api/ProposalController.java`, `infra/security/SecurityConfig.java`, `infra/web/HttpErrorMapping.java`,
  `messages.properties`, `application.yml` (0.31.0); testes `ProposalTest`, `ProposalCreationApiIntegrationTest`
  (contrato +rejectionReason/rejectionNote).
- **Frontend:** `core/auth/auth.service.ts` (+spec), `core/api/proposal.service.ts`, `proposal-detail.{ts,html,css,spec}`,
  `core/layout/shell.html` (atalhos `?`); e2e `proposal-creation.spec.ts` (aprovar) + `proposal-rejection.spec.ts` (novo).
- **Docs:** manual en-US + pt-BR (9.5), este relatório.

## 5. Testes / validações
- **Backend `./mvnw verify`: 398 verdes** (ArchUnit/Modulith + Testcontainers + V24 + `HttpErrorMappingTest`).
  Novo `ProposalApprovalApiIntegrationTest` (10): aprovar → APPROVED + histórico; rejeitar com motivo → REJECTED
  + reason/note + histórico; rejeitar sem motivo → **400**; aprovar fora de revisão → **422**; vendedor/
  representante/diretor/financeiro → **403**; não autenticado → **401**; rejeição libera a Oportunidade.
  Domínio (`ProposalTest`): approve/reject + guards.
- **Frontend `ng test`: 268 verdes** (`canApprove` por escopo+status; approve; reject exige motivo; DOM dos
  botões e do motivo). **`ng build`** verde.
- **E2E `npm run e2e`: 38 verdes** — `proposal-creation` agora **aprova** ao fim; novo `proposal-rejection`
  rejeita com motivo e confere o motivo + histórico. (As duas jornadas pesadas de proposta ficaram *flaky* sob
  carga paralela e passaram no retry — política `retries:1`.)
- Pilha dev recriada; **V24 aplicada**; `/api/version` = 0.31.0.

## 6. Gaps conhecidos
- **Aprovador = só Gerente** (escopo `sales:proposal:approve` no user 001). O Gerente pode aprovar a própria
  proposta (permitido pelo modelo); **separação estrita de funções** fica como evolução futura.
- **Sem multinível / por valor / jurídico / assinatura / aceite do cliente / pedido** (fora de escopo).
- **Revisar uma rejeitada = nova Proposta** (REJECTED é terminal e libera a Oportunidade); não há "voltar para
  rascunho".
- Motivo da rejeição é **enum fixo** (não cadastro gerenciável em runtime) — escolha do dono ("lista fixa, como
  os motivos de perda da Oportunidade"); virar cadastro é evolução futura.
- As jornadas e2e de proposta são pesadas e *flaky* sob paralelismo (absorvido por `retries:1`, sem afrouxar
  asserções) — candidato a estabilização futura (menos workers ou seed direto).

## 7. Próximo prompt recomendado
> **SLICE 8: Enviar a Proposta aprovada ao cliente.** Implemente `APPROVED → SENT` (registrar quem/quando no
> histórico, exigir validade não vencida se desejado), com gating por escopo (decidir com o dono: reusar
> `sales:proposal:approve`/`update` ou novo `sales:proposal:send`), e as telas/validações. **Sem** comunicação
> externa real / PDF / assinatura (apenas marcar como enviada). Sem criar Sale/Booking/Financial; cobrir feliz
> + tristes (sem permissão, fora de APPROVED) e manter aprovar/rejeitar/lista/detalhe funcionando.
