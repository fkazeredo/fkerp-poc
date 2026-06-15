# Relatório — Sprint 1 / Slice 1: Criação de Lead (Commercial / CRM)

- **Data:** 2026-06-15
- **Branches:** `feature/crm-lead-creation` (backend) + `feature/crm-frontend` (frontend), ambas a partir de `develop`
- **Escopo entregue:** fatia **completa ponta a ponta** — backend (criação de Lead + cadastros + autenticação) **e** frontend Angular (login, criação de Lead, gestão dos cadastros).

## 1. O que foi implementado (backend)

- **Autenticação (JWT própria da app):** `POST /api/auth/login` (usuário/senha → **access token** no corpo + **refresh token** em **cookie httpOnly**), `/api/auth/refresh`, `/api/auth/logout`. Resource Server valida o access token (HMAC) e exige scopes. Módulo **`domain.identity`** (User, scopes, `IdentityService`, `PasswordHasher` port + adapter BCrypt). Seed dev: usuário `comercial` / `comercial123` com `crm:lead:create` e `crm:reference:manage`.
- **Criação de Lead** (`POST /api/leads`, scope `crm:lead:create`): cria Lead **NEW**, responsável opcional (validado direto contra Identity), nota inicial vira a 1ª **interação** (histórico), publica evento `LeadRegistered`. Módulo **`domain.crm`**.
- **Cadastros (reference data) com CRUD** — Origin, LossReason, InteractionType, InteractionResult — `GET/POST/PUT/DELETE /api/crm/{origins|loss-reasons|interaction-types|interaction-results}`; **soft delete** (flag ativo) e **bloqueio de uso de inativos** em novos Leads; leitura = autenticado, escrita = scope `crm:reference:manage`. Lead referencia Origin/InteractionType por **FK**.
- **Infra transversal:** kernel de erros (`DomainException`/`ErrorDetails`), `GlobalExceptionHandler` + `HttpErrorMapping` (corpo `{code,message,fields}`), i18n **pt-BR**. UUID em tudo. Migrations Flyway V2 (identity), V3 (reference + seed), V4 (leads/interactions).

## 1.1 O que foi implementado (frontend)

- **Stack:** Angular 22 standalone (signals, rotas lazy) + **PrimeNG 21** (preset Aura via `@primeuix/themes`), navegação **orientada a teclado** (decisão do dono).
- **Autenticação no cliente:** `AuthService` mantém o **access token só em memória** (signal); interceptor funcional anexa o `Bearer`, envia credenciais e, em **401**, faz um **refresh silencioso** via cookie httpOnly e repete a requisição; guard de rota protege a área autenticada; um initializer restaura a sessão no boot.
- **Telas:** login (auto-foco no usuário), home, **criação de Lead** (form reativo, `p-select` de origens vindo de `GET /api/crm/origins`, mapeamento de erros de campo do backend, toast de sucesso) e **CRUD genérico de cadastros** dirigido por `data` de rota (listar/criar/editar/ativar/inativar + alternar inativos), reaproveitado pelos 4 cadastros.
- **UX de teclado:** **command palette** (`Ctrl/Cmd+K`), aceleradores (`n` novo lead, `g`+`o` origens, `g`+`i` início), Tab/Enter/Esc em todos os formulários.
- **Proxy same-origin `/api`:** nginx (prod) + `proxy.conf.json` no `angular.json` (dev) para o cookie de refresh permanecer first-party.
- **Compatibilidade:** não existe PrimeNG para Angular 22 ainda; instalado PrimeNG 21 com `--legacy-peer-deps` (decisão do dono) e validado com build + teste verdes.

## 2. Decisões de arquitetura tomadas nesta fatia (refletidas no CLAUDE.md)

- **`domain` é internamente aberto:** qualquer classe de `domain` pode usar outra de `domain` diretamente (ex.: `crm` → `identity`), sem Facade. Removido o Spring Modulith como impositor de fronteiras intra-domínio. (CLAUDE.md §6.)
- **Única fronteira dura:** `domain` ↛ `application`/`infra` (ArchUnit). `infra`/`application` → `domain` por **port & adapter** (preferível), sem Facade.
- **`application` organizado por mecanismo, não por módulo:** controllers em `application.api`, DTOs em `application.api.dto`, validações em `application.api.validation` (sem `application.api.<área>`). (CLAUDE.md §6/§5.1.)

## 3. Validação (gates + runtime real)

- **`./mvnw` (container Maven):** Spotless, Checkstyle, compile, ArchUnit, Spring Modulith e testes unitários — **verdes**.
- **Smoke end-to-end (artefato real via Docker Compose):** `health 200`; **login** OK; **criar Lead → 201** `{"status":"NEW"}`; **sem token → 401**; **sem nome → 400** com `{"code":"validation.failed",…,"Nome é obrigatório"}` (pt-BR); **criar Origin → 201**; DB `leads=1, origins=10, interactions=1`.
- **Testes de integração** (Testcontainers + MockMvc): escritos e compilando; rodam com `./mvnw verify` numa máquina com Docker (não rodaram no sandbox DooD por limitação de rede do Docker-in-Docker).
- **Frontend:** `npm run build` (produção) **verde** e `npm test` (Vitest) **verde**; Prettier aplicado. Não há ainda teste de unidade dedicado de AuthService/interceptor/form (gap).

## 4. Correções de incompatibilidade do Spring Boot 4 (modularização de autoconfig)

- **Flyway não rodava** (autoconfig modularizado) → adicionado `spring-boot-flyway`. Bug latente desde o bootstrap (lá não havia entidades para o `validate` reclamar).
- `TestRestTemplate` e `@AutoConfigureMockMvc` mudaram de pacote → testes migrados para **MockMvc via `webAppContextSetup + springSecurity`**.
- Handlers de 401/403 não dependem mais de bean `ObjectMapper` (não exposto por padrão no Boot 4).

## 5. Gaps conhecidos / próximos passos

- **PrimeNG em Angular 22 via `--legacy-peer-deps`:** combinação não-oficial (PrimeNG 21 declara peer ^21). Funciona hoje; revisar quando sair PrimeNG para Angular 22.
- **Sem testes de frontend dedicados** (AuthService/interceptor/validação de form) além do smoke do `App`.
- **Verificação E2E do frontend** (login → criar Lead pela UI) ainda não executada via Compose com o frontend servido por trás do nginx; o fluxo foi validado a nível de API.
- Refresh token é JWT stateless (logout limpa o cookie, mas não revoga server-side) — revisar quando necessário.
- Próximas fatias do Sprint 1: transições de status (Contacted/Qualified/Lost), interações, perda.
