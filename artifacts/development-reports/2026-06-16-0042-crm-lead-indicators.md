# Relatório — Sprint 1 / Slice 10: Indicadores funcionais mínimos de Leads

- **Data:** 2026-06-16
- **Branch:** `feature/crm-lead-indicators` (a partir de `develop`)
- **Escopo entregue:** uma visão **somente leitura** do **topo do funil** para o gerente comercial —
  contagens agregadas sobre os Leads que o usuário pode ver (gerente global, representante só os seus),
  num período opcional por data de criação. Reaproveita as **tiers de visibilidade da Slice 8** como
  WHERE de cada consulta — sem nova autorização. Não é dashboard executivo; **sem biblioteca de
  gráficos**, sem dados de Opportunity/Vendas/Reserva/Financeiro/Comissão/Campanha (nenhum existe).

## 1. O que foi implementado

- **Endpoint** `GET /api/leads/indicators` (coberto pelo gate de leitura existente `/api/leads/**` →
  qualquer tier de leitura; Finance/HR/IT → 403). Params opcionais `createdFrom`/`createdTo`
  (`LocalDate` → `Instant` UTC, `to` via `plusDays(1)`, exatamente como na lista). Sem migração, sem
  DTO de entrada, sem nova exceção, sem i18n no backend.
- **Indicadores** sobre o conjunto **visível**: **total** no período; por status **Novos / Em contato
  / Qualificados / Perdidos** (Lost **conta aqui**, ao contrário da lista operacional que o esconde);
  **por origem**; **por responsável** (com bucket de não atribuídos = `responsibleName` nulo);
  **aguardando 1º contato** = Novos **sem nenhuma interação** (reaproveita a definição da Slice 9 —
  mais estreito que "Novos").
- **`LeadIndicatorQueries`** (`@Component`, `domain/crm`, com `EntityManager`): 4 consultas Criteria
  group-by/count que **reutilizam a Specification de visibilidade** via `visible.toPredicate(root,
  query, cb)` + o predicado de período (fonte única de verdade). `countByStatus`, `countByOrigin`
  (label + count, mais movimentado primeiro), `countByResponsible` (id nulo = não atribuído),
  `countWaitingFirstContact` (NEW + `NOT EXISTS` interação via `LeadInteraction.leadId`). Group-by
  retorna só os buckets presentes — 4 queries no total, sem N+1 nem cargas ilimitadas (§11).
- **`LeadService.indicators(userId, canSeeAll, canSeeUnassigned, from, to)`** (`@Transactional(readOnly)`):
  monta a Specification, chama as 4 consultas, **deriva `total` = Σ por status**, resolve os nomes dos
  responsáveis (`users.findAllById`, mesmo padrão da `list`) e monta a view.
- **Frontend**: página `/indicadores` (guarda `crmReadGuard`) com **cards de KPI** (Total, Novos, Em
  contato, Qualificados, Perdidos, Aguardando 1º contato) e duas listas `.surface` (**Por origem**,
  **Por responsável**) com **barra de proporção em CSS** (sem lib de gráfico). Range **Criado de / até**
  (PrimeNG datepicker) com **padrão mês-corrente** (dia 1 → hoje), re-busca ao mudar; botão "Todo o
  período" limpa para all-time; estados de loading/empty/erro; "Sem responsável" para o bucket nulo.
  Entrada na **sidebar** ("Indicadores", `pi-chart-bar`), **tile na home** e comando na paleta — todos
  restritos a quem tem leitura.

## 2. Regras funcionais cobertas

Visão do topo do funil (total, por status incl. Perdidos, por origem, por responsável, aguardando 1º
contato); respeita visibilidade (gerente global; representante só os seus); período por data de
criação com padrão mês-corrente; é leitura operacional, **não** dashboard executivo (sem gráficos,
previsão ou números financeiros).

## 3. Critérios de aceite cobertos (→ consulta + teste que trava)

- Total de leads no período → `countByStatus` somado em `LeadService.indicators`;
  `LeadIndicatorsApiIntegrationTest.managerSeesGlobalIndicatorsIncludingLost` (total 7) +
  `LeadServiceTest.indicatorsDeriveTotalFromStatusesAndResolveResponsibleNames`.
- Quantidade por status, **Lost incluído** → `countByStatus`; idem (new 4, contacted 1, qualified 1,
  **lost 1**).
- Por origem → `countByOrigin`; idem (Website 5, Instagram 2).
- Por responsável, com não atribuídos → `countByResponsible`;
  `…managerSeesGlobalIndicatorsIncludingLost` (comercial 4, representante 2) +
  `…unassignedShowsAsANullResponsibleBucketForTheManager` (bucket nulo = 1).
- Aguardando 1º contato (Novo sem interação) → `countWaitingFirstContact`; idem (waiting 3; o Novo
  **com** interação é excluído).
- Período estreita as contagens → `…thePeriodFilterNarrowsTheCounts` (hoje → 7; amanhã → 0).
- Respeita visibilidade (representante só os seus) → Specification na query;
  `…representativeIndicatorsAreScopedToTheirOwnLeads` (total 2, só representante, exclui comercial e
  não atribuídos).
- Não expõe leads não autorizados / sem acesso → `…financeHasNoAccessToIndicators` (403).
- Sprint 1 anterior segue funcionando → suíte completa verde (backend + frontend + e2e).

## 4. Arquivos alterados (principais)

- **Backend (novos):** `domain/crm` — `LeadIndicatorQueries`, `LeadIndicatorsView`, `OriginCountView`,
  `ResponsibleCountView`, `ResponsibleCount` (carrier da query); `application/api/dto` —
  `LeadIndicatorsResponse`. **Editados:** `domain/crm/LeadService` (+`indicators`),
  `application/api/LeadController` (+`GET /indicators`). **Sem migração, sem endpoint de escrita.**
- **Frontend:** `core/api/lead.service.ts` (`indicators` + tipos `LeadIndicators`/`OriginCount`/
  `ResponsibleCount`); `features/leads/lead-indicators/*` (novo: `.ts`/`.html`/`.css`/`.spec`);
  `app.routes.ts` (rota guardada `/indicadores`); `core/layout/shell.ts` (nav + comando);
  `features/home/home.html` + `home.css` (tile).
- **Docs:** este relatório; seção "Indicators (Indicadores)" no manual do usuário.

## 5. Testes / validações adicionados

- **Backend unit `LeadServiceTest`** (+1): `indicators` deriva total = Σ por status e resolve nomes de
  responsáveis (mock de `LeadIndicatorQueries`).
- **Backend integração (Postgres real) `LeadIndicatorsApiIntegrationTest`** (5): gerente vê total e por
  status (incl. Lost), por origem e por responsável (incl. bucket nulo) e aguardando 1º contato; o
  período estreita as contagens; representante só os seus; finance 403. Assertam **shape + números**.
  `./mvnw verify` **verde: 157 testes**, Spotless + Checkstyle limpos.
- **Frontend unit (Vitest, 85):** `lead.service` (`indicators` GET com/sem datas);
  `lead-indicators` (padrão mês-corrente carrega; 6 cards de KPI incl. Perdidos; bucket nulo →
  "Sem responsável" + barras proporcionais; mudança de período re-busca; "Todo o período" → datas
  nulas; empty state; erro).
- **E2E (Playwright, stack isolada) — 21 verdes:** `lead-indicators` — criar lead → **Indicadores**
  → o card **Total** mostra número, os KPIs (Novos/Perdidos/Aguardando 1º contato) e as seções
  **Por origem** / **Por responsável** aparecem.
- **Smoke** no stack de dev (4200): login `comercial` → `GET /api/leads/indicators` 200 com o shape
  esperado.

## 6. Gaps conhecidos

- **Ponto-no-tempo:** as contagens são recalculadas a cada carregamento (sem snapshot histórico, sem
  série temporal/tendência).
- **Período por `createdAt`:** "no período" = lead criado no intervalo; não reflete mudanças de status
  ocorridas dentro do período (ex.: lead antigo qualificado hoje não entra pelo filtro de criação).
- **Sem gráficos / previsão / números financeiros / conversão entre etapas** (barras de proporção em
  CSS apenas); sem export.
- Admin == Gerente para Leads; Board/Marketing veem os mesmos indicadores via tier `read:all`.

## 7. Próximo prompt de implementação recomendado

> *Sprint 2 / Slice 1: criar uma **Opportunity** a partir de um Lead **qualificado**, honrando as tiers
> de visibilidade — entidade Opportunity + transição a partir do Lead, com migração Flyway, regras de
> domínio, validação em profundidade e erros/i18n padronizados. Incluir testes (unit + integração real
> + e2e) e relatório. Alternativas Sprint 1: taxa de conversão entre etapas nos indicadores, ou
> enriquecer as Pendências com filtro por motivo e ordenação por urgência.*
