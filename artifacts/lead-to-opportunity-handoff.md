# Handoff funcional — Lead Qualificado → Opportunity (Sprint 2)

> Documento de **contrato funcional** para a futura entrega. **Não** descreve implementação. O
> Sprint 1 **não** implementa Opportunity; aqui só se registra o que um Lead qualificado garante
> para quando a Opportunity for construída.

## Regra futura

Um **Lead QUALIFICADO pode originar uma Opportunity comercial** no Sprint 2. A Opportunity é uma
entidade **separada**: o Lead **não** vira Opportunity, **não** é convertido em Customer e **não** é
apagado. A Opportunity nasce **a partir** do Lead qualificado, referenciando-o (por `leadId`) e
**semeando** seus dados — sem recapturar informação básica.

Fronteira do Sprint 1 (mantida): nada de Opportunity, pipeline, Proposal, Sale, Sales Order, Booking,
Finance, Commission, Campaign, Call-Center queue, Customer Care ou conversão de Customer.

## O que o Lead qualificado preserva (snapshot)

Quando um Lead chega a `QUALIFIED`, todos os dados abaixo já estão persistidos no agregado `Lead`
(`backend/src/main/java/com/fksoft/erp/domain/crm/Lead.java`) e expostos por
`GET /api/leads/{id}` (`LeadDetailResponse`). O Sprint 2 lê o agregado e semeia a Opportunity a
partir daqui — **sem recapturar nada**.

| # | Informação | Campo no agregado `Lead` | Exposição na API (`GET /api/leads/{id}`) |
|---|---|---|---|
| 1 | Nome | `name` (`Lead.java:54`) | `name` |
| 2 | Contatos | `phone` / `whatsapp` / `email` (`:58` / `:62` / `:66`) | `phone` / `whatsapp` / `email` |
| 3 | Origem | `origin` ManyToOne → `Origin` (`:71`) | `origin` (rótulo) |
| 4 | Responsável | `responsiblePersonId` **UUID** (`:79`) | `responsibleId` (UUID) + `responsibleName` |
| 5 | Histórico de interações | `interactions` `@OneToMany` (`:120`) | `interactions[]` (tipo, resultado, conteúdo, datas, autor) |
| 6 | Interesse principal | `mainInterest` (`:96`) | `qualification.mainInterest` |
| 7 | Notas de qualificação | `qualificationNote` (`:100`) | `qualification.note` |
| 8 | Data de qualificação | `qualifiedAt` (`:89`) | `qualification.qualifiedAt` |
| 9 | Quem qualificou | `qualifiedBy` **UUID** (`:92`) | `qualification.qualifiedBy` (nome resolvido) |

O agregado já documenta a intenção: *"Qualification outcome … kept for history and to seed a future
Opportunity"* (`Lead.java:86-87`).

### Identidades estáveis para a futura FK

No domínio, `Lead.id`, `responsiblePersonId` e `qualifiedBy` são **UUIDs estáveis** (resolvidos para
nomes só nas views). A Opportunity do Sprint 2 pode, portanto, referenciar o Lead por `leadId` e
reaproveitar `responsiblePersonId` / `qualifiedBy` diretamente — a API expõe `responsibleId` como
UUID; `qualifiedBy` é exposto como nome na API, mas o **UUID persiste no agregado** para a transição
de domínio.

## Como o Sprint 2 deve consumir (sem implementar agora)

- Criar a Opportunity **só** a partir de um Lead em `QUALIFIED` (precondição de domínio).
- **Semear** a Opportunity com o snapshot acima (incl. `mainInterest`); manter `leadId` como
  referência de origem (rastreabilidade), sem duplicar a fonte de verdade do Lead.
- **Não** alterar nem "consumir" o Lead: ele permanece como registro histórico/qualificado. Lead e
  Opportunity continuam **separados**.
- Honrar as **tiers de visibilidade** já existentes (own / unassigned / all) ao criar/ver
  Opportunities, reaproveitando o modelo de escopos do CRM.

## Fora de escopo deste documento

Modelagem da Opportunity, pipeline/estágios, Proposal, Sale, Customer, Booking, Finance, Commission.
Tudo isso é Sprint 2+. Aqui registra-se apenas o **contrato de handoff** garantido pelo Sprint 1.
