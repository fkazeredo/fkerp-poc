# Relatório — Sprint 2 / Slice 12: Validação ponta a ponta da Sprint 2

- **Data:** 2026-06-17
- **Branch:** `feature/crm-sprint2-validation` (a partir de `develop`)
- **Versão:** 0.23.0 → **0.23.1** (PATCH — slice de validação, mudança interna)
- **Escopo:** garantir que a Sprint 2 (Oportunidades) funciona como **fluxo de negócio coerente**, não
  como operações isoladas. Espelha a validação da Sprint 1 (jornada backend + E2E Playwright + relatório).
  **Não** implementa Proposta/Venda/Pedido/Booking/Financeiro/conversão de Cliente.

## 1. Validação realizada

- **Jornada backend (integração, Postgres real)** — `OpportunitySprint2JourneyApiIntegrationTest` (2
  testes), encadeando o fluxo via API real (do Lead qualificado em diante, sem atalhos JDQL/JDBC no
  caminho feliz), com checagens de visibilidade no meio do fluxo.
- **Jornada E2E (Playwright, stack isolada porta 4201, Postgres efêmero)** — `opportunity-journey.spec.ts`
  (2 testes) dirigindo os dois fluxos pela UI (criar Lead → contato → qualificar → criar Oportunidade →
  avançar estágios → registrar atividades → editar dados comerciais → perder), além da suíte E2E completa
  para detectar regressões cruzadas.
- **Suítes completas:** `./mvnw verify` (backend), `ng test`/`ng build` (frontend) e a suíte E2E inteira.

## 2. Resultados dos fluxos

- **Fluxo principal (verde):** Lead Qualificado → criar Oportunidade (nasce **Nova**) → **Descoberta** →
  atividade (Reunião / Cliente engajado, com próxima ação) → **editar** valor estimado + previsão de
  fechamento → **Aderência** → atividade (Aderência identificada) → **Pronta para proposta**. No final, a
  Oportunidade carrega tudo que a Sprint 3 precisa (valor, previsão, interesse principal, Lead de origem,
  3 movimentos de estágio, 2 atividades) e **nenhum** dado de Proposta/Venda/Booking/Financeiro/Comissão é
  criado ou exposto. A visibilidade se manteve (financeiro 403; representante não-dono 403; gerente vê).
- **Fluxo alternativo / Perda (verde):** criar Oportunidade → atividade sem fit → **Marcar como perdida**
  sem motivo → **rejeitado (400)** → com motivo → **Perdida**; sai da **lista padrão**, é achada pelo
  **filtro Perdida**; o **Lead de origem segue rastreável** (no detalhe e por si só); perder de novo →
  **422** (LOST é terminal).

## 3. Defeitos encontrados e corrigidos

- **1 defeito (regressão de teste introduzida na Sprint 2), corrigido.** O item de navegação
  **"Indicadores de oportunidades"** (adicionado na Slice 11) tornou **ambíguo** o seletor do E2E de
  **Indicadores de Lead** (`lead-indicators.spec.ts`): `getByRole('link', { name: 'Indicadores' })` passou
  a casar **dois** links da sidebar ("Indicadores" e "Indicadores de oportunidades") → violação de
  *strict mode*. **Correção:** selecionar o link por **href** (`'.sidebar a[href="/indicadores"]'`) e
  fixar o heading com `exact: true`. É a única correção; **nenhum defeito de comportamento de produto** foi
  encontrado (as duas jornadas passaram sem alterar código de produção).
- Observação técnica registrada no spec novo: o nome acessível dos botões PrimeNG carrega um espaço à
  esquerda (vindo do ícone), então o confirm do diálogo de estágio é casado por nome **não exato**.

## 4. Critérios de aceite cobertos

| Critério | Status | Evidência |
|---|---|---|
| O fluxo principal funciona ponta a ponta | ✅ | `…JourneyApiIntegrationTest.mainFlow…` + `opportunity-journey.spec.ts` (jornada principal) |
| O fluxo alternativo (Perda) funciona ponta a ponta | ✅ | `…alternativeFlow_…lostAndTraceable` + spec (jornada de perda) |
| Nenhum passo depende de planilha/dado manual externo | ✅ | tudo via API/UI reais; dados criados no sistema |
| As regras de visibilidade se mantêm no fluxo | ✅ | financeiro 403, representante não-dono 403, gerente vê (jornada backend) |
| A Oportunidade "Pronta para proposta" tem informação suficiente p/ Sprint 3 | ✅ | detalhe assere valor/previsão/interesse/Lead/estágios/atividades |
| A Oportunidade perdida fica acessível, fora da lista padrão | ✅ | exclusão da lista + filtro Perdida + detalhe (ambas as camadas) |
| Nenhuma funcionalidade de escopo futuro foi introduzida | ✅ | sem Proposta/Venda/Booking/Financeiro/Comissão (asserts `doesNotExist`; nada novo no produto) |
| Comportamento existente das Oportunidades segue funcionando | ✅ | suíte completa verde |

## 5. Riscos remanescentes

- A jornada E2E roda como **um ator** (gerente, que é o responsável); a visibilidade multi-ator no meio do
  fluxo é coberta **no backend** (não repetida na UI) — decisão de custo/benefício.
- E2E depende do Docker da stack isolada; foi executada e está verde, mas exige o ambiente para reexecução.
- "Pronta para proposta" apenas sinaliza prontidão; a geração da **proposta** é da Sprint 3 (fora de escopo).
- Indicadores seguem **ponto-no-tempo** (sem série histórica) — já documentado.

## 6. Próximo prompt de implementação recomendado

> *Sprint 3 / Slice 1: iniciar a etapa de **Proposta** a partir de uma Oportunidade `READY_FOR_PROPOSAL`
> (entidade Proposal separada, referenciando `opportunityId`; precondição de domínio a partir da
> Oportunidade pronta; novo escopo de operação `crm:proposal:*`; migração Flyway; validação em
> profundidade §5.5; erros/i18n; honrando as read tiers). Incluir testes (unit + integração real + e2e) e
> relatório. Ainda **sem** Venda/Pedido/Booking/Financeiro/Comissão/Cliente.*

---

**Snapshot dos gates:** `./mvnw verify` **verde: 291 testes** (Postgres real); `ng test` **verde: 131**;
`ng build` **verde**; **E2E `playwright test` verde: 30** (stack isolada, porta 4201) — inclui as 2
jornadas novas de Oportunidade e o `lead-indicators` corrigido.
