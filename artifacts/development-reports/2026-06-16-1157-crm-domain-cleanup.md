# Relatório — Refatoração do `domain.crm`: camadas internas + tirar DTOs de leitura do domínio

- **Data:** 2026-06-16
- **Branch:** `refactor/crm-domain-cleanup` (a partir de `develop`)
- **Versão:** **0.14.1** (refactor interno → PATCH; bump por mudança, sem release note — regra nova do §14)
- **Tipo:** refatoração interna, **sem mudança de comportamento nem de contrato** (o JSON das APIs é
  idêntico; os testes de integração de leitura passam inalterados, servindo de rede de segurança).

## 1. O que foi feito (e por quê — Regra Zero)

Dois movimentos pedidos pelo dono, para reduzir overengineering em `domain.crm`:

**(A) Tirar os DTOs de leitura do domínio (opção B).** O domínio tinha um par redundante para cada
leitura: um `*View` no `domain` **e** um `*Response` no `application`, com remapeamento `.from()`. Os
`*View` foram **removidos**; o `application` passa a montar os `*Response` direto das entidades.

- Novos **`application.read.LeadReadService` / `OpportunityReadService`**: orquestram repositórios,
  policies, specifications e queries do domínio e **montam os `*Response`** (resolvendo nome do
  responsável/atores via Identity, última interação, contagens de indicadores). Ficam **fora de
  `..api..`**, então podem injetar repositórios — o gate `controllersMustNotAccessRepositories` (que só
  restringe `..api..`) continua válido; a visibilidade é aplicada no nível da query.
- **Domain services** (`LeadService`/`OpportunityService`) ficam só com **comandos**; as transições
  retornam `void` e o controller relê o detalhe atualizado via read service.
- **Apagados** 12 records de leitura do domínio (Lead/Opportunity `*View` + aninhados
  Interaction/Assignment/Qualification/Loss/OriginCount/ResponsibleCount + o carrier `ResponsibleCount`)
  e o `ResponsibleView` (identity). `LeadIndicatorQueries` passa a devolver `Map`s ordenados.
- `domain.crm.dto` fica só com **entradas** (commands + criteria).

**(B) Organizar o `domain.crm` em camadas internas.** O pacote (que estava com ~60+ arquivos) foi
dividido por papel: **`model`** (entidades/enums/eventos/VOs/lógica), **`repository`** (repos +
projeções + `LeadIndicatorQueries`), **`service`** (services de comando + policies + specifications),
**`exception`**, **`dto`** (commands + criteria). Pura organização de pacote (ArchUnit `..domain..` e
Modulith inalterados).

## 2. Regras de design reforçadas no CLAUDE.md (pedido do dono)

- **§5.1/§5.4/§6:** `domain.<area>` agora é organizado em sub-pacotes por papel
  (`model/repository/service/exception/dto`); **DTOs de leitura não vivem no domínio**; a montagem de
  leitura vive em **`application.read`** (fora de `..api..`, pode usar repositórios); o `*Response`
  (em `application.api.dto`, montado a partir da entidade) é o contrato — entidades não vazam.
- **§14:** **release note só no fim de uma entrega completa** (sprint/marco), nunca por fatia nem para
  refactor interno; o **bump de versão continua por mudança** (PATCH para refactor interno).

## 3. Arquivos

- **Novos:** `application/read/LeadReadService`, `application/read/OpportunityReadService`,
  `test/.../application/read/LeadReadServiceTest`; este relatório.
- **Reescritos:** `LeadController`/`OpportunityController`/`ResponsibleController` (GET → read service;
  escrita → service de comando + relê detalhe); os `*Response` (montam de entidade); `LeadService`/
  `OpportunityService` (só comandos); `LeadIndicatorQueries` (retorna `Map`s); `LeadServiceTest`
  (só comandos).
- **Apagados:** 12 records de leitura do domínio + `ResponsibleView` + os release notes `v0.13.0`/`v0.14.0`.
- **Movidos:** os 60 arquivos de `domain.crm` para `model/service/repository/exception/dto`.
- **Editados:** `CLAUDE.md` (§5.1/§5.4/§6/§14); `application.yml` + `compose.yaml` (0.14.0 → 0.14.1).

## 4. Verificação

- `./mvnw verify` **verde — 190 testes, 0 violações Checkstyle**; ArchUnit (incl.
  `controllersMustNotAccessRepositories`, `domainMustNotDependOnDeliveryOrInfra`), Modulith e
  completude do `HttpErrorMapping` passam. Testes de leitura de integração **inalterados** confirmam
  o JSON idêntico. `npm test` + e2e isolado verdes (frontend não tocado).
- Entregue em commits por fase (opção B → layer-split → docs) na branch; merge `develop`+`main` e push.

## 5. Notas

- A opção B remove DTOs do domínio **adicionando** a camada `application.read` — é a escolha do dono
  (CQRS-ish), registrada como regra. O `lastActivityAt`/`nextActionDate` da Oportunidade seguem nulos
  (fatia futura). Lembrete: `.env`/`.env.example` (protegidos aqui) → `APP_VERSION=0.14.1`.
