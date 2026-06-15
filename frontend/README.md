# Frontend

Angular 22 (standalone, signals, lazy routes) com **PrimeNG 21** (preset Aura) e navegação
orientada a teclado. Gerado com [Angular CLI](https://github.com/angular/angular-cli) 22.0.1.

> PrimeNG 21 ainda declara peer `@angular/*` ^21; a instalação usa `--legacy-peer-deps` e foi
> validada (build + testes) no Angular 22.

## Estrutura

- `core/auth` — `AuthService` (access token em memória/signal), interceptor (anexa o Bearer e, em 401,
  faz um refresh silencioso via cookie httpOnly e repete a requisição) e guard de rota.
- `core/api` — clients HTTP (`LeadService`, `ReferenceService`).
- `core/layout/shell` — casca autenticada: menubar, **command palette** (`Ctrl/Cmd+K`) e atalhos.
- `features/auth/login`, `features/home`, `features/leads/lead-create`, `features/crm/reference-list`.

## Atalhos de teclado

- `Ctrl/Cmd + K` — abre a paleta de comandos.
- `n` — novo lead. `g` depois `o` — origens. `g` depois `i` — início.
- `Tab`/`Enter`/`Esc` em todos os formulários; o primeiro campo recebe foco automaticamente.

## Acesso (dev)

Usuário seed: **`comercial`** / senha **`comercial123`** (scopes `crm:lead:create` e `crm:reference:manage`).
Fluxo: login → criar lead (origem vem de `GET /api/crm/origins`) → gerenciar cadastros.

## Development server

O dev-server faz proxy de `/api` para `http://localhost:8080` (ver `proxy.conf.json`), mantendo o
cookie de refresh same-origin. Suba o backend (`docker compose up backend db` ou `./mvnw spring-boot:run`)
e então:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
