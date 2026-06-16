# Relatório — Modernização da UI, atalhos e isolamento do E2E

- **Data:** 2026-06-16
- **Branch:** `feature/ui-modernization` (a partir de `develop`)
- **Motivação (feedback do dono):** a interface estava "feia"/genérica, os atalhos pareciam
  quebrados e faltavam atalhos para as ações novas, e os testes E2E estavam **poluindo o banco de
  desenvolvimento** com dezenas de leads "E2E …".

## 1. O que foi implementado

### Dados / infraestrutura de teste
- **Causa dos dados:** os testes Playwright apontavam para `http://localhost:4200` (a mesma stack de
  desenvolvimento), gravando no volume persistente. Havia **64 leads** no banco dev (61 "E2E …").
- **Limpeza:** `TRUNCATE leads CASCADE` no banco dev (reference data e usuários preservados).
- **Isolamento:** `compose.e2e.yaml` — stack **descartável** (Postgres efêmero em `tmpfs`, portas
  próprias, frontend **4201**); scripts `e2e:up`/`e2e:down`; `playwright.config` baseURL agora 4201
  (sobrescrevível por `E2E_BASE_URL`). Validado: 16 testes E2E passam e o banco dev **continua com 0
  leads**. Regra registrada no `CLAUDE.md` §13 e no `README`.

### UI moderna (decisão do dono: sidebar SaaS + claro/escuro)
- **Tailwind v4** instalado e integrado ao PrimeNG por camadas
  (`tailwind-base, primeng, tailwind-utilities`) — conforme `CLAUDE.md` §12 (antes ausente).
- **Shell** redesenhado: **sidebar** (marca, CTA "Novo lead", navegação com estado ativo, cadastros,
  rodapé com Atalhos/Sair), **top bar** (paleta de comandos + alternância de tema), **drawer** no
  mobile. Tokens de design (`--app-*`) com tema claro/escuro; **sidebar escura** nos dois temas.
- **Tema claro/escuro** (`ThemeService`): alterna `.app-dark` no `<html>`, persistido no
  `localStorage`, com fallback para a preferência do SO.
- **Telas repaginadas:** login (card com gradiente), home (tiles de ação), lista de leads (filtros e
  tabela em "surfaces"), detalhe, novo lead e cadastros — cabeçalhos e superfícies consistentes.

### Atalhos
- Globais auditados e documentados: `Ctrl/Cmd+K` (paleta), `n` (novo lead), `g`+`l/i/o`, **`?`**
  (ajuda — novo, com diálogo listando tudo).
- **Contextuais no detalhe** (novos): `i` registrar interação, `q` qualificar, `p` marcar perdido,
  `r` reatribuir/assumir, `Esc` voltar — ignorados enquanto há diálogo aberto ou foco em campo.

## 2. Arquivos alterados (principais)

- **Infra/E2E:** `compose.e2e.yaml` (novo), `frontend/playwright.config.ts`, `frontend/package.json`
  (scripts `e2e:up/down`), `README.md`, `CLAUDE.md` §13.
- **Tailwind/tema:** `frontend/.postcssrc.json` (novo), `frontend/src/styles.css`, `app.config.ts`
  (cssLayer), `core/theme/theme.service.ts` (novo).
- **Shell:** `core/layout/shell.{ts,html,css}`.
- **Telas:** `features/auth/login/*`, `features/home/*`, `features/leads/lead-list/*`,
  `lead-detail/*` (atalhos), `lead-create/*`, `features/crm/reference-list/*`.

## 3. Testes / validações

- **Frontend unit (Vitest): 67** (antes 62) — `ThemeService` (toggle/persistência), `Shell`
  (Ctrl+K/n/g+l/?/guarda de digitação), `lead-detail` (atalho `i`, ignora com diálogo aberto).
- **E2E (Playwright): 16** na stack isolada (4201) — inclui atalhos globais (`n`, `g`+`l`), paleta,
  atalho `i` no detalhe e alternância de tema; demais jornadas (login, criação, lista, detalhe,
  atribuição, interação, qualificação) seguem verdes. Banco dev intacto (0 leads).
- **Build** de produção verde (budget inicial ajustado para 600kB por causa do Tailwind).
- Backend **não** foi alterado.

## 4. Gaps conhecidos

- `Ctrl+K` é coberto por teste **unitário** do Shell (o Chromium headless intercepta o atalho), não
  por E2E; a paleta abre por clique no E2E.
- Sem um lint script no frontend (`CLAUDE.md` §15 cita `npm run lint`, ainda não definido).
- Redesign é um primeiro passe coeso; telas específicas podem receber mais polimento (densidade de
  tabela, estados vazios ilustrados) em iterações futuras.

## 5. Próximo passo recomendado

> *Definir um `npm run lint` (ESLint Angular) e adicioná-lo ao gate do frontend; opcionalmente uma
> home "dashboard" com KPIs (leads por status, follow-ups de hoje/atrasados) reaproveitando a
> sidebar; e seguir o Sprint 2 (criar Opportunity a partir de um lead qualificado).*
