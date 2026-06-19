# Relatório — Sprint 3 / Slice 6: Enviar Proposta para revisão (validade + responsável obrigatórios)

- **Data:** 2026-06-19
- **Branch:** `feature/sales-proposal-submit` (a partir de `develop`)
- **Versão:** 0.29.0 → **0.30.0** (MINOR — nova regra funcional na ação de envio)
- **Escopo entregue:** a ação **"Enviar para revisão"** (DRAFT → READY_FOR_REVIEW) já existia desde a Slice 3
  (validava DRAFT + ≥ 1 item + total positivo) e ganhou o histórico de status na Slice 5. Esta slice
  **completa** as pré-condições do story, acrescentando **data de validade obrigatória** e **responsável
  obrigatório** no envio.

## 1. O que foi implementado
**Backend (domínio)**
- Duas exceptions novas (espelham `ProposalHasNoItemsException`): **`ProposalValidityRequiredException`**
  (`proposal.validity-required`) e **`ProposalResponsibleRequiredException`** (`proposal.responsible-required`).
- **`Proposal.submitForReview`**: após os checks de item e total, passa a exigir `validUntil != null` e
  `responsiblePersonId != null` (ordem item → total → validade → responsável). Javadoc atualizado.
- **`HttpErrorMapping`**: as duas novas exceptions → **422**. **`messages.properties`**: duas chaves pt-BR.
- Sem migração nova (as colunas `valid_until` / `responsible_person_id` já existem; a regra é de transição,
  não invariante de tabela).

**Frontend**
- `proposal-detail.ts` `canSubmit()`: além de DRAFT + escopo + itens + total > 0, agora exige **validade**
  (`!!p.validUntil`) e **responsável** (`!p.unassigned`).
- `proposal-detail.html`: quando o usuário pode operar mas o envio está desabilitado, um **aviso** lista o que
  falta ("ao menos um item, total positivo, data de validade e responsável"). A validade é ajustável em
  **Editar dados comerciais**.

## 2. Regras funcionais cobertas
- **Só DRAFT** pode ser enviado ✓ (já existia). **≥ 1 item** ✓ · **total positivo** ✓ (já existiam).
- **Validade obrigatória** ✓ (novo) · **responsável obrigatório** ✓ (novo).
- Envio muda para **Ready for Review** ✓. Itens/totais **não editáveis fora do DRAFT** ✓ (`requireDraft()`).
- O envio **não** manda ao cliente e **não** cria Order/Booking/Finance/Commission ✓ (só muda o status +
  registra o histórico).

## 3. Critérios de aceite cobertos
Usuário autorizado envia DRAFT ✓ · sem itens não envia ✓ · sem total válido não envia ✓ · **sem validade não
envia** ✓ · enviado vira Ready for Review ✓ · não é enviado ao cliente ✓ · nenhum Commercial Order criado ✓ ·
comportamento existente preservado ✓.

## 4. Arquivos alterados (principais)
- **Backend (novos):** `domain/sales/exception/ProposalValidityRequiredException.java`,
  `ProposalResponsibleRequiredException.java`.
- **Backend (editados):** `Proposal.java` (2 checks no submit), `infra/web/HttpErrorMapping.java`,
  `messages.properties`, `application.yml` (0.30.0).
- **Backend (testes):** `ProposalTotalsTest` (happy-path com validade + 2 sad-paths novos),
  `ProposalLifecycleApiIntegrationTest` (happy-path com PUT validade + 2 sad-paths novos).
- **Frontend:** `proposal-detail.{ts,html,css,spec.ts}`; `e2e/proposal-creation.spec.ts` (define a validade
  antes do envio).
- **Docs:** manual en-US + pt-BR (9.4 — pré-condições do envio), este relatório.

## 5. Testes / validações
- **Backend `./mvnw verify`: 384 verdes** (Spotless/Checkstyle/ArchUnit/Modulith + Testcontainers; inclui o
  `HttpErrorMappingTest` que garante que toda DomainException tem status). Novos sad-paths: envio sem validade
  → **422** `proposal.validity-required`; sem responsável → **422** `proposal.responsible-required` (unidade +
  integração). Os sad-paths "sem itens"/"total não positivo" seguem iguais (o check anterior dispara primeiro).
- **Frontend `ng test`: 261 verdes** (`canSubmit` false sem validade e sem responsável; demais estados). **`ng
  build`** verde.
- **E2E `npm run e2e`: 39 verdes** — `proposal-creation` agora **define a validade** em "Editar dados
  comerciais" (datepicker, via `pressSequentially`) antes de enviar, refletindo a nova pré-condição.
- Pilha dev recriada; sem migração nova; `/api/version` = 0.30.0.

## 6. Gaps conhecidos
- **Validade = presença** (a proposta tem uma data de validade), não checagem de data futura/não-vencida —
  leitura literal do story; "não vencida" fica como possível evolução.
- **Sem ação de (re)atribuir responsável na Proposta:** o responsável vem da **criação** (herda da
  Oportunidade). Uma proposta sem responsável (caso raro: origem sem responsável) fica sem como ser enviada
  até existir uma ação de atribuição — gap para slice futura. A regra é enforçada mesmo assim (422).
- "Voltar para DRAFT" / "rejeitar" (mencionados na regra de não-edição) são transições de slices futuras do
  ciclo de vida — fora de escopo aqui; a não-edição fora do DRAFT já é garantida.

## 7. Próximo prompt recomendado
> **SLICE 7: Proposal lifecycle — aprovação interna.** Implemente `READY_FOR_REVIEW → APPROVED` (e a volta
> `READY_FOR_REVIEW → DRAFT` / rejeição, se desejado), cada transição gravando o `ProposalStatusChange` (que
> já aparece no Histórico de status do detalhe), com gating por escopo (decidir com o dono: reusar
> `sales:proposal:update` ou criar `sales:proposal:approve`) e as validações/telas correspondentes. Sem criar
> Sale/Booking/Financial; cobrir feliz + tristes e manter lista/detalhe/itens/totais/envio funcionando.
