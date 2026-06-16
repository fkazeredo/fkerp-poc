# Relatório — Sprint 1 / Slice 5: Registrar interações do Lead (+ NEW → Em contato)

- **Data:** 2026-06-15
- **Branch:** `feature/crm-lead-interactions` (a partir de `develop`)
- **Escopo entregue:** o caso de uso de **registrar uma interação** (contato, tentativa ou nota) no
  histórico do Lead — com tipo, resultado, data (autor), descrição e **próximo contato** opcional —
  e a **regra de estado** de que um Lead **Novo** passa a **Em contato** após um **contato efetivo**.
  Sem Opportunity/Customer/Sale/Booking/Finance e **sem** integração externa (WhatsApp/e-mail/ligação).

## 1. O que foi implementado

- **Endpoint** `POST /api/leads/{id}/interactions` (escopo `crm:lead:update`; visibilidade primeiro:
  ausente → 404, não-visível → 403). Autor = usuário autenticado; data informada (não pode ser futura).
- **Regra de contato efetivo:** resultado é **efetivo** exceto **"Não atendeu"** e **"Contato
  inválido"** (`InteractionResult.isEffectiveContact()`, conjunto não-efetivo fechado
  `{NO_ANSWER, INVALID_CONTACT}`). Um Lead **NEW** vira **CONTACTED** no contato efetivo; uma
  tentativa frustrada preserva o histórico e mantém NEW; nenhum outro status é alterado aqui
  (`Lead.recordInteraction`).
- **Próximo contato:** gravado **na interação** (histórico do que foi agendado e quando) e espelhado
  em `leads.next_contact_at` (campo que a lista já exibe).
- **Histórico append-only:** interações nunca são apagadas (regra preservada).
- **Frontend:** ação **Registrar interação** no detalhe (todas as situações), com diálogo de tipo,
  resultado, **data/hora** (padrão agora, sem futuro), **descrição** (obrigatória) e **próximo
  contato** opcional; o detalhe recarrega e o status reflete a transição. O próximo contato aparece
  também por interação no histórico.

## 2. Regras funcionais cobertas

- Toda interação tem **autor, data, tipo, resultado e descrição**; pode definir **próximo contato**.
- **Contato efetivo** (Contato realizado, Pediu retorno, Interessado, Não interessado e — por decisão
  do dono — Precisa follow-up e Outro) move NEW → CONTACTED; **não-efetivo** (Não atendeu, Contato
  inválido) mantém NEW.
- O **histórico não é apagado**; tentativas frustradas continuam registradas.
- Nenhum comportamento de Opportunity/Customer/Sale/Booking/Finance nem integração externa.

## 3. Critérios de aceite cobertos

Usuário autorizado registra interação em Lead que pode operar; exige tipo/resultado/data/autor/
descrição; permite próximo contato; aparece no detalhe; última interação e próximo contato aparecem
na lista; NEW → Em contato após contato efetivo; permanece Novo após "Não atendeu" e após "Contato
inválido"; todas as interações permanecem no histórico; criação/listagem/filtros/detalhe/atribuição
seguem funcionando. **Todos cobertos.**

## 4. Arquivos alterados (principais)

- **Backend:** `domain/crm` — `InteractionResult.isEffectiveContact`, `LeadInteraction` (coluna
  `next_contact_at`, factory `record`, `content` `@NotBlank`), `Lead.recordInteraction`,
  `RecordInteractionCommand` (novo), `InteractionType/ResultNotAvailableException` (novos),
  `LeadService.recordInteraction` + `InteractionResultRepository` injetado, `InteractionView`
  (+`nextContactAt`); `application/api` — `LeadController` (`POST /{id}/interactions`),
  `RegisterInteractionRequest` (novo), `LeadDetailResponse.InteractionItem` (+`nextContactAt`);
  `infra/web/HttpErrorMapping` (2 mapeamentos → 422), `infra/security/SecurityConfig` (matcher),
  `messages.properties` (2 chaves), `db/migration/V9__lead_interactions.sql` (novo).
- **Frontend:** `core/api/lead.service.ts` (`recordInteraction`, `InteractionItem.nextContactAt`),
  `features/leads/lead-detail/lead-detail.ts` + `.html` (ação + diálogo + histórico de próximo contato).

## 5. Testes / validações adicionados

- **Backend unit:** `LeadTest` (+7) — efetivo → CONTACTED; "Não atendeu"/"Contato inválido" mantêm
  NEW; "Precisa follow-up"/"Outro" → CONTACTED; não reverte QUALIFIED; agenda próximo contato;
  `isEffectiveContact` por código. `LeadServiceTest` (+5) — resolve tipo/resultado ativos, rejeita
  tipo/resultado inexistentes (422 domain), nega quando não visível, caminho feliz → CONTACTED.
- **Backend integração (Postgres real):** `LeadInteractionApiIntegrationTest` (13) — registrar +
  detalhe; NEW→CONTACTED; "Não atendeu"/"Contato inválido" mantêm NEW; faltando descrição/tipo/
  resultado → **400** com o campo; data futura → 400; tipo/resultado inválidos → **422**; próximo
  contato no detalhe e na **lista**; histórico append-only; 403 não-visível; 404 desconhecido; 403
  sem `crm:lead:update`. `./mvnw verify` **verde: 110 testes**.
- **Frontend unit (Vitest, 58):** `lead.service` (`recordInteraction` POST correto); `lead-detail`
  (carrega tipos/resultados ao abrir; guarda campos obrigatórios; registra e reflete CONTACTED).
- **E2E (Playwright, 12):** contato efetivo move para **Em contato** e aparece no histórico;
  "Não atendeu" mantém **Novo** e registra o histórico. Verde contra o stack vivo.

## 6. Gaps conhecidos

- **`result_id` permanece nullable** no banco: a nota de criação não é um contato e não tem
  resultado; não há discriminador SQL que separe os dois caminhos, então a regra "resultado
  obrigatório" (interações registradas) é garantida nas camadas 1–4, não por `CHECK` (exceção
  registrada no V9 e aqui, §2/§5.5).
- Sem ação rápida **Contatar** dedicada nem edição/correção de interação (histórico é imutável).
- `nextContactAt` é só agenda/exibição; ainda **não há uma visão de follow-up/agenda** sobre ela.
- Datas exibidas no fuso do navegador; nomes por **username**.
- OpenAPI gerado pelo springdoc (sem contrato manual).

## 7. Próximo prompt de implementação recomendado

> *Sprint 1 / Slice 6: Agenda de follow-up. Uma visão operacional dos próximos contatos baseada em
> `nextContactAt` (hoje/atrasados/próximos), filtrável por responsável, levando ao detalhe do Lead;
> opcionalmente uma ação rápida "Contatar" que registra a interação e reprograma o próximo contato.
> Alternativa: edição/correção de interação com auditoria. Incluir testes (unit + integração real +
> e2e) e relatório.*
