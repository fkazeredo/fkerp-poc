# Handoff funcional — Opportunity "Pronta para proposta" → Commercial Proposal (Sprint 3)

> Documento de **contrato funcional** para a futura entrega. **Não** descreve implementação. O Sprint 2
> **não** implementa Proposal; aqui só se registra o que uma Opportunity em `READY_FOR_PROPOSAL` garante
> para quando a Proposal for construída.

## Regra futura

Uma **Opportunity em `READY_FOR_PROPOSAL` pode originar uma Proposal comercial** no Sprint 3. A Proposal é
uma entidade **separada**: a Opportunity **não** vira Proposal, **não** é convertida em Sale/Sales Order,
**não** cria Customer e **não** é apagada. A Proposal nascerá **a partir** da Opportunity pronta,
referenciando-a (por `opportunityId`) e **semeando** seus dados — sem recapturar informação básica. Lead,
Opportunity e Proposal permanecem **três registros separados**, cada um fonte de verdade do seu próprio
escopo (o Lead segue sendo a fonte do contato e do histórico).

Fronteira do Sprint 2 (mantida): nada de Proposal, Proposal approval, Sale, Sales Order, Booking, Finance,
Commission, Marketing Campaign, Call-Center queue, Customer Care ou conversão de Customer.

## O que a Opportunity "Pronta para proposta" preserva (snapshot)

Quando uma Opportunity chega a `READY_FOR_PROPOSAL`, todos os dados abaixo já estão persistidos no agregado
`Opportunity` (`backend/src/main/java/com/fksoft/erp/domain/crm/model/Opportunity.java`) — com o Lead de
origem ainda como sistema de registro do contato — e são expostos por `GET /api/opportunities/{id}`
(read model `OpportunityDetail`,
`backend/.../domain/crm/service/data/OpportunityDetail.java`). O Sprint 3 lê o agregado e semeia a Proposal
a partir daqui — **sem recapturar nada**.

| # | Informação (exigida no fecho) | Campo de origem | Exposição na API (`GET /api/opportunities/{id}`) |
|---|---|---|---|
| 1 | **Lead de origem** | `Opportunity.leadId` (UUID, imutável, único) | `leadId` + `sourceLead.id` |
| 2 | **Origem do Lead** | `Opportunity.origin` → `Origin` (semeada do Lead na criação) | `origin` (rótulo) |
| 3 | **Responsável** | `Opportunity.responsiblePersonId` (UUID) | `responsibleId` (UUID) + `responsibleName` + `unassigned` |
| 4 | **Informações de contato** | no Lead de origem (sistema de registro) | `sourceLead.phone` / `sourceLead.whatsapp` / `sourceLead.email` |
| 5 | **Interesse principal** | `Opportunity.mainInterest` (herdado da qualificação; imutável aqui) | `mainInterest` |
| 6 | **Interesse estimado em produto/serviço** | `Opportunity.productType` | `productType` |
| 7 | **Valor estimado** | `Opportunity.estimatedValue` (`BigDecimal`, ≥ 0) | `estimatedValue` |
| 8 | **Data prevista de fechamento** | `Opportunity.expectedCloseDate` (`LocalDate`) | `expectedCloseDate` |
| 9 | **Histórico de atividades comerciais** | `Opportunity.activities` `@OneToMany` | `activities[]` (tipo, resultado, descrição, data, próxima ação, autor) |
| 10 | **Histórico de movimentação de estágio** | `Opportunity.stageChanges` `@OneToMany` | `stageHistory[]` (de/para, quando, quem) |
| 11 | **Notas relevantes** | `Opportunity.notes` (+ `nextActionDate` da última atividade) | `notes` + `nextActionDate` |
| 12 | **Prontidão para proposta** | `Opportunity.stage == READY_FOR_PROPOSAL` | `stage` |

O agregado já documenta a intenção: a Opportunity "moves through the commercial pipeline" e o read model
"Exposes commercial pipeline data only — never Proposal, Sale, Sales Order, Booking, Financial, Commission
or Customer Care data" (`OpportunityDetail.java`). A jornada
`OpportunitySprint2JourneyApiIntegrationTest.mainFlow_…readyForProposal` **assere**, no estágio
`READY_FOR_PROPOSAL`, a presença de valor, previsão, interesse principal, Lead de origem, histórico de
estágio (3) e de atividades (2), e a **ausência** de campos de proposta/venda/booking/financeiro.

### Identidades estáveis para a futura FK

`Opportunity.id`, `Opportunity.leadId` e `Opportunity.responsiblePersonId` são **UUIDs estáveis**
(resolvidos para nomes só nas views). A Proposal do Sprint 3 pode, portanto, referenciar a Opportunity por
`opportunityId` e reaproveitar `leadId` / `responsiblePersonId` diretamente, sem duplicar a fonte de
verdade.

## Como o Sprint 3 deve consumir (sem implementar agora)

- Criar a Proposal **só** a partir de uma Opportunity em `READY_FOR_PROPOSAL` (precondição de domínio); um
  estágio anterior ou `LOST` não origina Proposal.
- **Semear** a Proposal com o snapshot acima (incl. `mainInterest`, `estimatedValue`, `expectedCloseDate`,
  `productType`); manter `opportunityId` como referência de origem (rastreabilidade), sem duplicar a fonte
  de verdade da Opportunity nem do Lead.
- **Não** alterar nem "consumir" a Opportunity ao gerar a Proposal: ela permanece como registro
  histórico/pronto. Lead, Opportunity e Proposal continuam **separados**.
- Honrar as **read tiers** de Opportunity já existentes (own / unassigned / all) e definir um **novo
  escopo de operação** próprio da Proposal (ex.: `crm:proposal:*`) — sem reusar o `crm:opportunity:update`.
- Definir o ciclo da Proposal (rascunho/enviada/aprovada/recusada) **na Sprint 3**; aqui nada de aprovação,
  preço final, Sale, Sales Order, Booking, Finance ou Commission.

## Fora de escopo deste documento

Modelagem da Proposal, aprovação, precificação final, Sale, Sales Order, Customer, Booking, Finance,
Commission. Tudo isso é Sprint 3+. Aqui registra-se apenas o **contrato de handoff** garantido pelo
Sprint 2.
