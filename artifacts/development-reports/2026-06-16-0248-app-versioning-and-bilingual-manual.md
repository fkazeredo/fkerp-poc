# Relatório — Versionamento (SemVer) + versão na tela + manual bilíngue + release notes

- **Data:** 2026-06-16
- **Branch:** `feature/app-versioning` (a partir de `develop`)
- **Escopo entregue:** introduzir **versionamento SemVer** da aplicação (em `APP_VERSION`), **exibir a
  versão na tela**, registrar a regra no `CLAUDE.md`, manter o **manual do usuário em en-US e pt-BR** e
  criar `artifacts/release-notes/` com a **release note da Sprint 1 (v0.1.0)**.

## 1. O que foi implementado

- **Versão (SemVer `MAJOR.MINOR.PATCH`)** sourced de **`APP_VERSION`**: `application.yml`
  (`app.version: ${APP_VERSION:0.1.0}`) + `compose.yaml` (`APP_VERSION: ${APP_VERSION:-0.1.0}`) →
  `AppProperties.version` (`@NotBlank`, fail-fast). **Versão escolhida: 0.1.0** (primeira entrega,
  pré-1.0, em evolução — Sprint 1).
- **Endpoint público** `GET /api/version` (`VersionController` → `VersionResponse{version}`),
  liberado no `SecurityConfig` (`permitAll`), para a UI consumir sem autenticação.
- **Exibição na tela:** `VersionService` (frontend) busca `/api/version` uma vez e expõe um signal;
  a versão aparece na **tela de login** e no **rodapé da barra lateral** (`vX.Y.Z`).
- **Regra no `CLAUDE.md` (§14):** definição SemVer, `APP_VERSION` em `.env`/compose → exibida na UI,
  release notes em `artifacts/release-notes/`, e **manual mantido em en-US e pt-BR**.
- **Manual bilíngue:** `fkerp-user-manual.md` → renomeado para **`fkerp-user-manual.en-US.md`**
  (atualizado: escopo Sprint 1 v0.1.0, dedup, nota da versão na tela) + nova **edição pt-BR**
  (`fkerp-user-manual.pt-BR.md`, tradução completa).
- **Release notes:** `artifacts/release-notes/v0.1.0-sprint-1.md` — nota da Sprint 1 derivada dos
  development-reports (funcionalidades, escopo futuro, qualidade).

## 2. Observação sobre o `.env`

Os arquivos `.env`/`.env.example` são **protegidos de leitura** pela política de permissões deste
ambiente, então não pude editá-los diretamente. O wiring foi feito com **default** em `compose.yaml`
(`${APP_VERSION:-0.1.0}`) e em `application.yml` (`${APP_VERSION:0.1.0}`), de modo que tudo funciona
sem `.env`. **Ação para o dono:** adicionar a linha `APP_VERSION=0.1.0` ao `.env` e ao `.env.example`
(a partir daí o valor do `.env` sobrescreve o default e é a fonte única de verdade).

## 3. Testes / validações

- **Backend `./mvnw verify` verde** (Postgres real) — inclui `VersionApiIntegrationTest` (público,
  shape SemVer). Spotless + Checkstyle + ArchUnit + Modulith ok.
- **Frontend `npm test` verde: 87** (+2 `version.service.spec`).
- **E2E (stack isolada) verde: 25** (+`version.spec`: a tela de login exibe `vX.Y.Z`).
- **Smoke no dev (4200):** `GET /api/version` → `0.1.0`.

## 4. Arquivos alterados (principais)

- **Backend (novos):** `application/api/VersionController`, `application/api/dto/VersionResponse`,
  `application/api/VersionApiIntegrationTest`. **Editados:** `infra/config/AppProperties`,
  `infra/security/SecurityConfig`, `application.yml`, `compose.yaml`.
- **Frontend (novos):** `core/api/version.service.ts` + `.spec`, `e2e/version.spec.ts`. **Editados:**
  `login.{ts,html,spec}`, `shell.{ts,html,spec}`.
- **Docs:** `CLAUDE.md` (§14 regra), manual `…en-US.md` (renomeado+atualizado) + `…pt-BR.md` (novo),
  `artifacts/release-notes/v0.1.0-sprint-1.md` (novo).

## 5. Gaps conhecidos / decisões

- `.env`/`.env.example` não editáveis aqui (ver §2) — funciona via defaults; o dono adiciona a linha.
- A versão é **buscada do backend** (fonte única `APP_VERSION`), não embutida no build do frontend.
- **0.1.0** representa a Sprint 1; próximas fatias do backlog de Lead (dedup já incluída no app)
  incrementam a partir daí (ex.: 0.2.0) — basta bumpar `APP_VERSION` e adicionar uma release note.

## 6. Próximo passo recomendado

> *Adicionar `APP_VERSION=0.1.0` ao `.env`/`.env.example` (dono). Seguir o backlog do Lead (Fatia 2:
> reabrir Lead perdido), confirmando a regra antes de implementar; ao liberar, bumpar `APP_VERSION` e
> escrever a release note correspondente em `artifacts/release-notes/`.*
