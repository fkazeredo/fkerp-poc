# Relatório — Pré-Sprint: Navegação por módulos + auditoria de testes (coverage + TDD)

- **Data:** 2026-06-17
- **Branch:** `feature/nav-modules-and-test-audit` (a partir de `develop`)
- **Versão:** 0.26.0 → **0.27.0** (MINOR — nova navegação/homes), literal em `application.yml`
- **Escopo:** três pedidos do dono antes de continuar as slices — (1) sidebar escalável, (2) home do sistema
  com homes de módulo, (3) auditoria de testes com coverage + adoção de TDD.

## 1. O que foi implementado

**Navegação por módulos (frontend)**
- **`core/navigation/NavigationService`**: uma **config única** (módulos + itens + ações + gating por escopo)
  que alimenta a sidebar, a home do sistema, as homes de módulo e a paleta de comandos — sem duplicação.
- **Sidebar em acordeão**: cada módulo (Comercial / CRM, Vendas, Cadastros) é uma **seção recolhível**; o
  cabeçalho leva à **home do módulo**, a setinha recolhe/expande, e o estado fica **persistido em
  `localStorage`** (transição suave). "Início" no topo leva à home do sistema.
- **Home do sistema** (`/`): **cards por módulo** acessível, levando às homes de módulo.
- **Homes de módulo**: **um** componente reutilizável `ModuleHome` parametrizado pelo módulo, em `/crm`,
  `/vendas`, `/cadastros`, renderizando as telas do módulo como tiles (com aviso de "sem acesso").
- Paleta de comandos e atalhos `g`-leader derivados da config (`g c` agora vai à home de Cadastros).

**Auditoria de testes + coverage (B)**
- **Tooling de cobertura habilitado** (não existia): **JaCoCo** no backend (`./mvnw verify` →
  `target/site/jacoco`) e **vitest v8** no frontend (`ng test --coverage` → `coverage/`).
- **Auditoria** de todas as categorias contra os critérios do §13 (assere o contrato? cobre os tristes?).
- **Lacunas reais fechadas via TDD** (arquivos sem teste): `proposal.service`, `reference.service`,
  `opportunity-read.guard`, `proposal-read.guard`, `unsaved-changes.service`.
- **Relatório extenso** em `artifacts/test-reports/2026-06-17-2052-test-audit.md` (tipos, pirâmide, coverage
  por camada, avaliação de "realidade", lacunas, como rodar).

**Processo/decisões**
- **TDD** e **versão centralizada no `application.yml`** gravados em memória do projeto e formalizados no
  CLAUDE.md (§3, §13, §14).

## 2. Regras funcionais cobertas
- Navegação reflete o acesso do usuário (a config gata por escopo; backend continua a única autoridade).
- Módulo sem itens/ações não aparece; usuário sem acesso a um módulo vê aviso na home do módulo.
- Sidebar permanece organizada conforme o sistema cresce (acordeão + persistência).
- Cobertura é **sinal**, não meta; TDD passa a ser o padrão (teste falha primeiro).

## 3. Critérios de aceitação cobertos
- ✅ Sidebar escalável (acordeão recolhível e persistido) — decisão de UX pesquisada e registrada.
- ✅ Home do sistema com cards → homes de módulo; submenu (cabeçalho da seção) linka à home do módulo.
- ✅ Auditoria feita; coverage real medido (backend **96.8%** instr / **96.9%** linhas / **84.2%** branches;
  frontend `.ts` 80–96%, número global enviesado por templates — explicado no relatório); lacunas fechadas;
  TDD adotado; relatório salvo em `artifacts/`.

## 4. Arquivos alterados (principais)
- **Frontend (novos):** `core/navigation/navigation.ts` (+spec), `features/home/module-home.{ts,html,spec}`,
  `features/home/tiles.css`; specs de gap (`proposal.service.spec`, `reference.service.spec`,
  `opportunity-read.guard.spec`, `proposal-read.guard.spec`, `unsaved-changes.service.spec`),
  `home.spec`.
- **Frontend (alterados):** `core/layout/shell.{ts,html,css}` (acordeão + palette da config),
  `features/home/home.{ts,html}`, `app.routes.ts`, `angular.json` (budget de CSS do shell 4→6kB warning;
  devDep `@vitest/coverage-v8`), 16 specs E2E (heading da home; `lead-visibility` ajustado).
- **Backend:** `pom.xml` (plugin JaCoCo), `application.yml` (0.27.0). Sem mudança de código backend.
- **Docs:** `CLAUDE.md` §3/§12/§13/§14, manual en-US + pt-BR (§4 navegação), test report, este relatório.

## 5. Testes / validações
- **Frontend:** 24→**32 arquivos**, 167→**199 testes**, 0 falhas; `ng build` verde. Cobertura de **lógica
  `.ts` 85,6%** (templates `.html` 16% puxam o número global p/ 38% — split verificado no test report).
- **Backend:** `./mvnw verify` verde (**≈364 testes**) + relatório JaCoCo gerado (96.8% instr).
- **E2E:** ripple do heading da home resolvido em 16 specs; suíte completa re-executada na stack isolada
  (resultado registrado na verificação de entrega).
- **Coverage:** backend JaCoCo e frontend vitest v8 rodando e versionados como reporting (sem virar gate).

## 6. Lacunas conhecidas / próximos passos
- Cobertura de **estado de template** no frontend (loading/empty/error) é o investimento seguinte — adotado
  como hábito de TDD para novos componentes (sem coverage-theater).
- Pontos baixos de backend são bootstrap (`main`) e construtores triviais de exceção — não são riscos.
- Evolução futura da navegação: "icon-rail" de duas camadas quando os módulos se multiplicarem (Rule Zero →
  não agora).

## 7. Próximo prompt sugerido
> Read CLAUDE.md and the current Sprint 3 Sales & Proposals implementation. Continue Sprint 3. Implement only
> the next functional slice: **SLICE 4: Proposal review & approval** (TDD) — an authorized reviewer **approves**
> a `READY_FOR_REVIEW` proposal (`→ APPROVED`) or **sends it back to Draft** with a note, recording who/when in
> a status history; gate it with a distinct review permission and keep the read model/audit. Write the failing
> tests first (happy + all sad paths). Out of scope: sending to the client, acceptance, sale/order/booking/
> financial. Report the 7 points.
