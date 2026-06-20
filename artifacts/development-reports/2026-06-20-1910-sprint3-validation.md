# Relatório — Sprint 3 / Slice 13: Validação ponta-a-ponta do Sprint 3 (Sales & Proposals)

- **Data:** 2026-06-20
- **Branch:** `feature/sprint3-validation` (a partir de `develop`)
- **Versão:** 0.36.0 → **0.36.1** (PATCH — validação/hardening, sem nova funcionalidade)
- **Escopo:** **validar o Sprint 3 como um fluxo de negócio coerente** (não operações isoladas) e **fechar lacunas de
  cobertura dos critérios de aceite**, corrigindo **apenas** defeitos ligados ao Sprint 3. **Nada** de Booking,
  Financeiro, Receivable, Payment, Commission ou Customer Care — nenhum recurso de escopo futuro introduzido.

## 1. Validação realizada
- **Auditoria de cobertura** dos três fluxos do Sprint 3 (principal + 2 alternativos) contra os critérios de aceite,
  cruzando os testes de integração por etapa, os testes de unidade da máquina de estados do `Proposal` e os E2E.
- **Novo teste de integração de jornada** `ProposalSprint3JourneyApiIntegrationTest` (MockMvc + Postgres real,
  espelhando `OpportunitySprint2JourneyApiIntegrationTest`): percorre os **três fluxos** pela **API real** (uma
  transação por chamada HTTP), partindo de uma Oportunidade já em `READY_FOR_PROPOSAL` (resultado do Sprint 2,
  semeado por JDBC — o funil em si é validado pela jornada do Sprint 2).
- **Novo E2E** `proposal-customer-rejection.spec.ts` (recusa do cliente) e **reforço** do `proposal-rejection.spec.ts`
  (rejeição interna), rodados num navegador real contra a pilha isolada (4201).
- Suítes completas executadas: **backend `./mvnw verify`**, **frontend `ng test` + `ng build`**, **E2E Playwright**.

## 2. Resultado dos fluxos
- **Principal (feliz) — OK:** Oportunidade `READY_FOR_PROPOSAL` → Proposta `DRAFT` → itens → **total conferido**
  (2×500 = 1000) → validade + termos → `submit` (`READY_FOR_REVIEW`) → `approve` (`APPROVED`) → `send` (`SENT`) →
  `accept` (`ACCEPTED`) → **Pedido Comercial** `PENDING_BOOKING`, com itens/total/refs espelhados; **Oportunidade de
  origem vira `WON`**. O contrato do Pedido expõe **só dados comerciais** (asserções `booking/receivable/payment/
  commission` **doesNotExist**). Visibilidade mantida (financeiro → 403; gerente → 200).
- **Alternativo 1 (rejeição interna) — OK:** `READY_FOR_REVIEW` → `reject` (motivo) → `REJECTED`; **não pode ser
  enviado** (`POST /send` ⇒ 422 `proposal.not-approved`) **nem gerar Pedido** (`POST /orders` ⇒ 422
  `proposal.not-accepted`); segue **legível/rastreável**. No E2E, após rejeitar, os botões **Aprovar/Rejeitar/Marcar
  como enviada/Criar pedido comercial** somem.
- **Alternativo 2 (recusa do cliente) — OK:** `SENT` → `decline` (motivo) → `REJECTED`; **nenhum Pedido** é criado
  (`POST /orders` ⇒ 422 `proposal.not-accepted`); a **Oportunidade de origem permanece `READY_FOR_PROPOSAL`** (nunca
  foi ganha) e **rastreável**. No E2E, a recusa registra motivo + nota, não oferece criar/ver pedido, e a oportunidade
  de origem continua "Pronta p/ proposta".

## 3. Defeitos encontrados e corrigidos
- **Defeitos de produto: 0.** O código já estava completo — todas as transições e guardas existem
  (`Proposal.requireDraft/requireUnderReview/requireApproved/requireSent`; `CommercialOrder.createFromProposal`
  exige `ACCEPTED`). A validação **confirmou** o comportamento; nenhuma correção de produto foi necessária.
- **Ajuste de robustez de teste (não-produto):** no E2E reforçado de rejeição interna, a asserção de ausência do
  botão "Rejeitar" usava correspondência por substring e casava o botão "**Rejeitar** proposta" do diálogo (em
  teardown), gerando intermitência. Corrigido com **correspondência exata** (`exact: true`) — passou a ser
  determinístico. Não houve mudança de produto.
- **Lacunas de cobertura fechadas:** (a) jornada feliz **coerente** num único teste; (b) guardas negativas explícitas
  "REJECTED não envia / não gera pedido" (antes só testadas a partir de DRAFT/SENT); (c) E2E inexistente da **recusa
  do cliente**; (d) E2E de rejeição interna agora prova a **ausência das ações**.

## 4. Critérios de aceite cobertos
Fluxo principal Proposta→Pedido ponta-a-ponta ✓ · fluxo de rejeição interna ponta-a-ponta ✓ · fluxo de recusa do
cliente ponta-a-ponta ✓ · nenhum passo depende de planilha/dado externo (tudo pela API/UI do sistema) ✓ · regras de
visibilidade mantidas durante o fluxo (financeiro 403; gerente 200) ✓ · o Pedido Comercial carrega informação
suficiente para o Booking do Sprint 4 (itens com tipo bookável + quantidade, total, refs de origem, status
`PENDING_BOOKING`) ✓ · nenhum recurso de escopo futuro introduzido (sem Booking/Finance/Receivable/Payment/Commission/
Customer Care) ✓ · comportamento de Sprint 1 e Sprint 2 segue funcionando (suíte completa verde) ✓.

## 5. Riscos remanescentes
- "Nenhum Booking/Receivable/Payment/Commission é criado" é verificado pela **ausência** desses campos no contrato e
  pela **inexistência** desses agregados nesta sprint — quando o Sprint 4 introduzir Booking, valerá reconfirmar que a
  criação do Pedido continua **não** disparando efeitos colaterais.
- E2E de jornada longa é sensível a timing (toasts interceptando cliques); mitigado com atalhos de teclado
  (`m`/`c`/`x`), `test.slow()` e `exact: true` nas asserções de ausência; `retries:1` cobre transitórios.
- A semântica "informação suficiente para o Booking" foi validada pela presença de itens bookáveis + status; o
  desenho fino do Booking (por item vs por pedido) é decisão do Sprint 4.

## 6. Próximo prompt recomendado
> **SLICE 14 (Sprint 4): Iniciar as Operações de Reserva (Booking) a partir de um Pedido Pendente de reserva.** Criar
> o bounded context `domain.booking` (agregado, escopos `booking:*`, migração Flyway própria e tela), permitindo
> **abrir/registrar** a reserva dos itens que a exigem (Pacote de viagem / Locação) a partir de um Pedido
> `PENDING_BOOKING`, mudando o status do Pedido conforme. **Decidir com o dono:** granularidade (reserva por item vs
> por pedido), os estados da reserva e o efeito no status do Pedido. Ainda **sem** financeiro/comissão; cobrir feliz +
> tristes (TDD) e manter o ciclo Oportunidade → Proposta → Pedido → Indicadores intacto.
