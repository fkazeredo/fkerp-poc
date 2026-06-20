# Handoff funcional — Commercial Order "Pendente de reserva" → Booking Operations (Sprint 4)

> Documento de **contrato funcional** para a futura entrega. **Não** descreve implementação. O Sprint 3
> **não** implementa Booking; aqui só se registra o que um Commercial Order em `PENDING_BOOKING` garante
> para quando as Operações de Reserva forem construídas.

## Regra futura

Um **Commercial Order em `PENDING_BOOKING` pode originar Operações de Reserva (Booking)** no Sprint 4. O
Booking é um conceito **separado**: o Pedido **não** vira Booking, **não** é convertido em Sale/Financeiro,
**não** cria Receivable/Payment/Commission e **não** é apagado nem alterado. As reservas nascerão **a
partir** do Pedido pronto, referenciando-o (por `orderId`) e **lendo** seus dados — sem recapturar
informação comercial básica. Lead, Opportunity, Proposal, Commercial Order e o futuro Booking permanecem
**registros separados**, cada um fonte de verdade do seu próprio escopo.

Fronteira do Sprint 3 (mantida): nada de Booking, reserva de Pacote de viagem, reserva de Locação de
veículo, integração de reserva, Finance, Receivable, Payment, Commission, Customer Care, Refund ou
cancelamento operacional pós-venda.

## O que o Commercial Order "Pendente de reserva" preserva

Quando um Pedido é criado de uma Proposta **Aceita**, ele **snapshota** os itens, o total e as referências
de origem no agregado `CommercialOrder`
(`backend/src/main/java/com/fksoft/erp/domain/sales/model/CommercialOrder.java`), e **superficia** o
contexto comercial (termos, validade, notas) lido da **Proposta de origem imutável** — sem duplicar dados.
Tudo é exposto por `GET /api/orders/{id}` (read model `CommercialOrderDetail`,
`backend/.../domain/sales/service/data/CommercialOrderDetail.java`). O Sprint 4 lê o Pedido e inicia a
reserva a partir daqui — **sem recapturar nada**.

| # | Informação (exigida no fecho) | Campo de origem | Exposição na API (`GET /api/orders/{id}`) |
|---|---|---|---|
| 1 | **Proposta de origem** | `CommercialOrder.proposalId` (UUID, imutável) | `proposalId` + `sourceProposal.id` |
| 2 | **Oportunidade de origem** | `CommercialOrder.opportunityId` (UUID; agora `WON`) | `opportunityId` + `sourceOpportunity.id` |
| 3 | **Lead de origem / origem comercial** | `CommercialOrder.leadId` (UUID); a origem comercial vive no Lead | `leadId` + `sourceLead` (nome, telefone, e-mail) |
| 4 | **Responsável** | `CommercialOrder.responsiblePersonId` (UUID) | `responsibleId` + `responsibleName` + `unassigned` |
| 5 | **Itens comerciais** | `CommercialOrder.items` `@OneToMany` (snapshot da Proposta) | `items[]` (descrição, quantidade, valor unit., desconto, total de linha) |
| 6 | **Tipos de item** | `CommercialOrderItem.type` (`TRAVEL_PACKAGE`/`CAR_RENTAL`/`SERVICE_FEE`/`OTHER`) | `items[].type` — define **o que** exige reserva |
| 7 | **Valor total** | `CommercialOrder.subtotal` / `total` (`BigDecimal`, snapshot) | `subtotal` + `total` |
| 8 | **Termos comerciais** | `Proposal.commercialTerms` (superficiado da Proposta preservada) | `sourceProposal.commercialTerms` |
| 9 | **Indicador de necessidade de reserva** | `CommercialOrder.status == PENDING_BOOKING` | `status` + `requiresBooking` (booleano explícito) |
| 10 | **Notas voltadas ao cliente** | `Proposal.commercialTerms` / `Proposal.paymentNotes` (termos/condições da oferta) | `sourceProposal.commercialTerms` / `sourceProposal.paymentNotes` |
| 11 | **Notas internas relevantes** | `Proposal.notes` (anotação geral) + validade `Proposal.validUntil` | `sourceProposal.notes` + `sourceProposal.validUntil` |

> Observação sobre as notas: a Proposta não tem uma taxonomia "cliente vs. interno" separada — ela carrega
> `commercialTerms`, `paymentNotes` (termos/condições da oferta, voltados ao cliente) e `notes` (anotação
> geral). O handoff mapeia esses **campos existentes** às categorias acima **sem inventar** uma nova divisão.

O read model já documenta a intenção: "Exposes commercial-order data only — never Booking, Receivable,
Payment, Commission or Customer Care data" (`CommercialOrderDetail.java`). A jornada
`ProposalSprint3JourneyApiIntegrationTest.mainFlow_…toCommercialOrder` **assere**, no Pedido criado,
`status=PENDING_BOOKING`, `requiresBooking=true`, os itens/tipos/total espelhados, os termos/validade
superficiados, a Oportunidade `WON`, e a **ausência** de campos de booking/receivable/payment/commission.

### Identidades estáveis para a futura FK

`CommercialOrder.id`, `proposalId`, `opportunityId`, `leadId` e `responsiblePersonId` são **UUIDs estáveis**
(resolvidos para nomes só nas views). O Booking do Sprint 4 pode, portanto, referenciar o Pedido por
`orderId` e reaproveitar as referências de origem diretamente, sem duplicar a fonte de verdade.

## Como o Sprint 4 deve consumir (sem implementar agora)

- Criar a Reserva **só** a partir de um Pedido em `PENDING_BOOKING` (precondição de domínio); um Pedido
  `BOOKING_NOT_REQUIRED` ou cancelado não origina reserva.
- **Ler** os itens que exigem reserva pelo `type` (`TRAVEL_PACKAGE` / `CAR_RENTAL`) — o `SERVICE_FEE`/`OTHER`
  não reserva. Quantidade, descrição e valor já vêm no item; termos/validade/notas vêm do Pedido.
- **Não** alterar nem "consumir" o Pedido ao gerar a reserva: ele permanece como registro do negócio
  fechado. Lead, Opportunity, Proposal e Commercial Order continuam **separados**.
- Honrar as **read tiers** de Pedido já existentes (own / unassigned / all) e definir um **novo escopo de
  operação** próprio do Booking (ex.: `booking:*`) — sem reusar `sales:order:*`.
- Definir o ciclo da Reserva (abrir / confirmar / falhar) **na Sprint 4**; aqui nada de integração com
  fornecedor, disponibilidade, custo, Finance, Payment ou Commission.

## Fora de escopo deste documento

Modelagem do Booking, reserva de Pacote de viagem, reserva de Locação de veículo, integração de reserva,
Finance, Receivable, Payment, Commission, Customer Care, Refund e cancelamento operacional pós-venda. Tudo
isso é Sprint 4+. Aqui registra-se apenas o **contrato de handoff** garantido pelo Sprint 3.
