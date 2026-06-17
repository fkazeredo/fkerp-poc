# FKERP — Manual do Usuário

> **Público:** usuários finais do sistema FKERP (equipe comercial / vendas).
> **Idioma:** Português (pt-BR). Há uma edição em inglês mantida em paralelo
> (`fkerp-user-manual.en-US.md`).
> **Escopo:** cobre tudo que foi liberado até a **v0.12.0** — o ciclo de vida completo de
> Leads do Comercial / CRM (cadastro, lista/busca/filtros, detalhe, atribuição, interações, regra de
> Em contato, qualificação, fluxo de Perda, visibilidade por perfil, pendências e indicadores) mais a
> **deduplicação** de Leads. Será ampliado à medida que novos recursos forem lançados.

---

## 1. O que é o FKERP

O FKERP é o ERP da empresa. A primeira área disponível é o **Comercial / CRM**, onde a equipe de
vendas cadastra e organiza **Leads** — pessoas ou empresas que demonstraram interesse inicial. Um Lead
é apenas um *interessado*; ainda não é um cliente, uma oportunidade ou uma venda.

Esta versão permite:

- Entrar com segurança.
- Cadastrar um novo Lead (com uma primeira anotação e um responsável, ambos opcionais).
- Ver, buscar e filtrar os Leads que você pode trabalhar.
- Abrir o detalhe de um Lead e qualificá-lo, marcá-lo como perdido (com motivo) ou reatribuí-lo.
- Gerenciar as listas de apoio (*cadastros*): **Origens**, **Motivos de perda**, **Tipos de
  interação** e **Resultados de interação**.

---

## 2. Primeiros passos

### 2.1 Acessando o sistema

Abra o navegador no endereço informado pelo seu administrador. Em um ambiente local, é:

| O quê | Endereço |
|-------|----------|
| Aplicação | http://localhost:4200 |

### 2.2 Entrando

1. Na tela de login, informe seu **usuário** e sua **senha**.
2. Pressione **Enter** ou clique em **Entrar**.
3. Ao entrar, você chega na tela inicial. Se as credenciais estiverem erradas, aparece a mensagem
   *"Usuário ou senha inválidos."*

> Contas de desenvolvimento/demonstração (senha = usuário + `123`): **`comercial`** (gerente — vê e
> opera tudo), **`vendedor`** (vendedor — os seus + o pool de não atribuídos), **`representante`**
> (representante — só os seus Leads), **`diretor`** (diretoria — vê tudo, somente leitura) e
> **`financeiro`** (sem acesso a Leads). Sua conta real é criada pelo administrador.

Sua sessão é mantida ativa de forma automática e segura enquanto você usa o sistema. Se recarregar a
página, você continua conectado. Use **Sair** na barra superior para encerrar a sessão.

A **versão** da aplicação (ex.: `v0.12.0`) é exibida na tela de login e no rodapé da barra lateral,
para você sempre saber qual release está usando.

### 2.3 Perfis e acesso — o que você vê e faz

O que você vê e faz depende do seu **perfil**. O sistema garante isso no servidor, então as regras
valem sempre, mesmo fora da tela:

| Perfil | Vê | Pode operar? |
|---|---|---|
| **Admin / Gerente Comercial** | todos os Leads | sim — criar, atribuir, qualificar, marcar perdido, interações |
| **Diretoria, Marketing** | todos os Leads | **não** — apenas consulta (somente leitura) |
| **Vendedores, Call Center** | os seus Leads **+ o pool de não atribuídos** | sim |
| **Representantes** | **somente os seus** Leads | sim (só nos seus) |
| **Financeiro / RH / TI** | — | sem acesso ao módulo de Leads |

- Um **representante nunca vê** Leads de outros representantes e não pode abri-los nem agir sobre
  eles. Buscar e filtrar nunca revelam Leads fora da sua visibilidade.
- Usuários **somente consulta** (diretoria, marketing) navegam por listas e detalhes, mas não veem
  **nenhum botão de ação** nem *Novo lead*.
- Usuários **sem acesso a Leads** não veem o menu **Leads** e são levados de volta à tela inicial, que
  mostra um aviso curto de "sem acesso".

---

## 3. Navegação pelo teclado

O FKERP foi feito para ser usado **pelo teclado**, então você raramente precisa do mouse.

| Ação | Atalho |
|------|--------|
| Abrir a **paleta de comandos** (buscar qualquer ação) | `Ctrl` + `K` (ou `Cmd` + `K` no macOS) |
| Ver **todos os atalhos** (ajuda) | `?` |
| Novo lead | `n` |
| Ir para a **lista de Leads** | `g` depois `l` |
| Ir para **Origens** | `g` depois `o` |
| Ir para o **Início** | `g` depois `i` |
| Mover entre campos | `Tab` / `Shift` + `Tab` |
| Confirmar / enviar um formulário | `Enter` |
| Fechar um diálogo / cancelar | `Esc` |

**Na página de detalhe de um Lead** (quando nenhum diálogo está aberto):

| Ação | Atalho |
|------|--------|
| **Registrar interação** | `i` |
| **Qualificar** | `q` |
| **Marcar como perdido** | `p` |
| **Reatribuir / Assumir** | `r` |
| Voltar à lista | `Esc` |

Observações:

- A **paleta de comandos** (`Ctrl/Cmd + K`) permite digitar o nome de uma ação e executá-la — o jeito
  mais rápido de se locomover. Pressione `?` a qualquer momento para ver a lista completa.
- Ao abrir um formulário, o **primeiro campo recebe o foco automaticamente**, então você já pode
  começar a digitar.
- Atalhos de uma letra (como `n` ou `i`) são ignorados enquanto você digita em um campo ou enquanto um
  diálogo está aberto, então nunca atrapalham a entrada de dados.

### Tema claro / escuro

Use o **botão de sol/lua** na barra superior (ou o comando *"Alternar tema claro/escuro"* na paleta)
para alternar entre os modos claro e escuro. Sua escolha fica salva no dispositivo.

---

## 4. Início

A tela inicial resume a área Comercial / CRM e oferece atalhos rápidos para:

- **Novo lead** — abre o formulário de cadastro de Lead.
- **Cadastros** — gerenciar as listas de apoio.

Um lembrete dos principais atalhos de teclado é exibido no rodapé.

---

## 5. Cadastrando um Lead

Abra **Novo Lead** (menu superior, atalho `n` ou paleta de comandos).

### 5.1 Campos

| Campo | Obrigatório | Observações |
|-------|-------------|-------------|
| **Nome** | Sim | O nome da pessoa ou empresa. |
| **Origem** | Sim | De onde o Lead veio (ex.: Website, Instagram). Escolha na lista. |
| **Telefone** | — | Apenas dígitos. |
| **WhatsApp** | — | Apenas dígitos. |
| **E-mail** | — | Precisa ser um e-mail válido. |
| **Responsável** | — | O usuário que vai trabalhar o Lead. Deixe vazio para criar sem responsável. |
| **Anotação inicial** | — | Uma primeira nota; vira o primeiro item do histórico do Lead. |

**Regra de contato:** um Lead precisa ter **ao menos uma** forma de contato — telefone, WhatsApp ou
e-mail. Se nenhuma for informada, o sistema rejeita o Lead com a mensagem *"Informe ao menos um
contato (telefone, WhatsApp ou e-mail)."*

**Sem duplicados:** um Lead é recusado quando já existe um Lead **ativo** (que ainda não foi Perdido)
com o **mesmo telefone/WhatsApp ou e-mail** — aparece a mensagem *"Já existe um lead ativo com este
telefone ou e-mail"*. Abra o Lead existente em vez de criar uma cópia. Depois que um Lead é marcado
como **Perdido**, o mesmo contato pode ser cadastrado novamente.

### 5.2 Salvando

- Pressione **Enter** ou clique em **Salvar lead**.
- Ao salvar, aparece uma confirmação verde (*"Lead criado"*) e você volta à tela inicial.
- O novo Lead começa no status **Novo** (NEW).
- Se algo estiver inválido, a mensagem aparece **ao lado do campo** correspondente, para você corrigir
  e enviar de novo. Use **Cancelar** para descartar e voltar à tela inicial.

---

## 6. Encontrando Leads — a lista, busca e filtros

Abra **Leads** no menu superior (pressione `g` depois `l`, ou use a paleta de comandos). A lista mostra
os Leads que você pode trabalhar — aqueles em que você é o responsável, mais os não atribuídos. Os
gerentes veem todos os Leads.

### O que a lista mostra

Cada linha mostra o **nome** do Lead, o **contato principal**, a **origem**, o **status**, o
**responsável** (ou *Sem responsável* quando não atribuído), a **data de criação**, a **última
interação** (data e tipo, quando houver) e a **data do próximo contato** (quando definida).

### Buscando e filtrando

Use a barra de filtros acima da tabela:

- **Buscar** — digite parte de um nome ou contato; os resultados atualizam enquanto você digita.
- **Status** — escolha um ou mais status. Por padrão **os Leads perdidos ficam ocultos**; eles
  aparecem apenas quando você adiciona **Perdido** ao filtro de status.
- **Origem** — restringe a uma única origem.
- **Responsável** — restringe a uma pessoa, ou escolha **Sem responsável** para ver só os não
  atribuídos.
- **Criado de / até** — restringe a um intervalo de data de criação.
- **Limpar** — zera todos os filtros.

A lista é paginada — use o paginador no rodapé para mudar de página. Você só consegue ver os Leads que
pode trabalhar; buscar e filtrar nunca revelam Leads de outras pessoas.

### Pendências

Abra **Pendências** na barra lateral (ou pelo tile da tela inicial) para um worklist operacional dos
Leads que **precisam de ação**, para nenhum follow-up se perder. Cada Lead recebe um ou mais motivos:

- **Sem responsável** — o Lead não tem responsável (precisa de um dono).
- **Sem interação** — um Lead Novo que nunca foi contatado.
- **Contato atrasado** — o próximo contato agendado está vencido.
- **Sem desfecho** — um Lead Em contato sem follow-up planejado e que ainda não foi qualificado nem
  perdido.

Valem as mesmas **regras de visibilidade**: um representante ou vendedor vê só as próprias pendências;
um gerente vê todas. Leads qualificados e perdidos nunca aparecem aqui. Clique no nome para abrir o
detalhe e agir.

### Indicadores

Abra **Indicadores** na barra lateral (ou pelo tile da tela inicial) para uma visão somente leitura do
**topo do funil** num período. Mostra:

- **Cards de KPI:** **Total** no período, depois por status — **Novos**, **Em contato**,
  **Qualificados** e **Perdidos** — mais **Aguardando 1º contato** (Leads Novos ainda não contatados).
  Diferente da lista operacional, **os Leads Perdidos são contados aqui**.
- **Por origem** e **Por responsável** — cada rótulo com sua contagem e uma barra de proporção; Leads
  não atribuídos aparecem como **Sem responsável**.

Use os seletores **Criado de / até** para escolher o período (por data de criação); o padrão é o **mês
atual** (dia 1 → hoje). Clique em **Todo o período** para limpar as datas e ver os números de todo o
histórico.

Valem as mesmas **regras de visibilidade**: um representante ou vendedor vê só os próprios números; um
gerente vê os de todos. É uma leitura operacional, não um dashboard executivo — ainda não há gráficos,
previsões ou valores financeiros.

## 7. Abrindo um Lead — detalhe e ações

Clique no **nome** de um Lead na lista para abrir a página de detalhe (você só consegue abrir Leads que
pode ver; os demais retornam uma mensagem de permissão).

### O que o detalhe mostra

- **Dados e contatos:** nome, status, telefone, WhatsApp, e-mail, origem, responsável, datas de
  criação e última atualização e a data do próximo contato, quando definida.
- **Histórico de interações:** as notas/contatos do Lead ao longo do tempo, quando houver.
- **Histórico de atribuições:** quem atribuiu o Lead a quem, e quando.
- **Qualificação:** mostrada depois que o Lead foi qualificado (quando, por quem, anotação).
- **Perda:** mostrada depois que o Lead foi marcado como perdido (motivo, quando, por quem, anotação).

### Agindo sobre um Lead

Os botões aparecem no topo quando a ação se aplica ao status atual:

- **Qualificar** — marca o Lead como **Qualificado**. Aparece somente quando o Lead está **Em contato**
  e tem um **responsável**; é obrigatório informar o **interesse principal** e é possível adicionar uma
  anotação comercial. Qualificar, por si só, não cria uma Oportunidade nem um Cliente — torna o Lead
  **apto** a originar uma.
- **Criar oportunidade** — aparece somente em um Lead **Qualificado**. Abre uma **Oportunidade**
  comercial a partir do Lead (veja *Oportunidades* adiante). Atalho de teclado: **`o`**.
- **Marcar como perdido** — coloca o Lead em **Perdido**; é obrigatório escolher um **motivo de perda**
  e é possível adicionar uma anotação. Perdido é definitivo.
- **Reatribuir** — muda o responsável (ou limpa para não atribuído). Toda mudança fica registrada no
  histórico de atribuições.
- **Assumir** — atribui o Lead **a você**. Aparece somente em um Lead não atribuído que você pode ver e
  é como um representante assume novos Leads.

Depois de uma ação, o detalhe é atualizado e aparece uma confirmação. O histórico e as seções de
qualificação/perda são sempre **preservados** — um Lead perdido ou qualificado continua mostrando essas
informações.

### Quem pode atribuir um Lead

Qual dos dois botões você vê depende da sua **autoridade de atribuição**:

- **Gerentes comerciais e administradores** podem atribuir ou reatribuir um Lead a **qualquer pessoa**
  (e limpar para não atribuído). Eles veem o botão **Reatribuir**.
- **Representantes** só podem **assumir um Lead não atribuído para si**. Eles veem o botão **Assumir** —
  não podem passar um Lead para outra pessoa nem desatribuir. O sistema garante isso no servidor,
  então a regra vale mesmo fora da tela.

### Registrando uma interação

Todo contato, tentativa de contato ou nota interna fica registrado no histórico do Lead. Clique em
**Registrar interação** no topo do detalhe e preencha:

| Campo | Obrigatório | Observações |
|-------|-------------|-------------|
| **Tipo** | Sim | Ligação, WhatsApp, E-mail, Presencial, Nota interna ou Outro. |
| **Resultado** | Sim | O desfecho da interação (veja abaixo). |
| **Data** | Sim | Quando aconteceu. O padrão é agora; você pode retroagir, mas não pode ser no futuro. |
| **Descrição** | Sim | O que aconteceu — vira o item do histórico. |
| **Próximo contato** | — | Opcionalmente agende o próximo contato; ele passa a aparecer no Lead e na lista. |

A interação é adicionada ao **histórico** (que nunca é apagado); o autor e o momento são registrados
automaticamente.

**Novo → Em contato.** Registrar um **contato efetivo** move um Lead no status **Novo** para **Em
contato**. Um contato é efetivo para todo resultado **exceto** *Não atendeu* e *Contato inválido* —
essas são tentativas sem sucesso, então o Lead permanece **Novo** mas mantém a tentativa no histórico.
Leads que já passaram de Novo mantêm seu status.

## 8. Oportunidades

Uma **Oportunidade** é uma negociação comercial real, aberta a partir de um Lead **qualificado**. O
Lead e a Oportunidade permanecem **registros separados**: a Oportunidade aproveita os dados do Lead
(origem, responsável, interesse principal) para nada ser redigitado, enquanto o Lead segue sendo a
fonte dos contatos e do histórico. Uma Oportunidade **não** é proposta, venda, pedido nem registro
financeiro.

### 8.1 Criando uma Oportunidade

No detalhe de um Lead **Qualificado**, clique em **Criar oportunidade** (ou tecle **`o`**). Você pode,
opcionalmente, informar o **tipo de produto / área de interesse**, um **valor estimado**, a
**previsão de fechamento** e uma **anotação comercial inicial**. A Oportunidade nasce na primeira
etapa do pipeline, **Nova**. Cada Lead origina **no máximo uma** Oportunidade; uma segunda tentativa é
bloqueada e aponta a já existente. Criar uma Oportunidade não altera o Lead.

### 8.2 A lista operacional

Abra **Oportunidades** no menu lateral (ou pela paleta de comandos, `Ctrl K`) para ver as
Oportunidades que você pode trabalhar. A lista é paginada e mostra, para cada Oportunidade:

| Coluna | Observações |
|--------|-------------|
| **Título** | O título da Oportunidade; leva ao **Lead de origem**. |
| **Responsável** | O dono, ou *Sem responsável* (não atribuída). |
| **Estágio** | Nova, Descoberta, Aderência, Pronta p/ proposta, Perdida. |
| **Valor estimado** | Quando informado. |
| **Fechamento previsto** | Quando informado. |
| **Criado em** | A data de criação. |
| **Última atividade / Próxima ação** | A data da atividade mais recente e a próxima ação planejada, quando houver. |

Acima da lista há uma barra de **busca e filtros**. A **busca** procura por título, tipo de produto
ou interesse da Oportunidade **e também pelo nome e pelos contatos (telefone, WhatsApp, e-mail) do
Lead de origem** — então é possível encontrar uma negociação digitando o nome ou o telefone do
cliente. Os filtros disponíveis são:

| Filtro | O que faz |
|--------|-----------|
| **Estágio** | Um ou mais estágios do pipeline. |
| **Responsável** | Um responsável específico ou *Sem responsável* (não atribuídas). |
| **Origem** | A origem do Lead que deu início à Oportunidade. |
| **Criado de / até** | O período de criação da Oportunidade. |
| **Fechamento de / até** | O período da previsão de fechamento. |
| **Valor mín. / máx.** | A faixa de valor estimado. |

Os filtros se combinam (a lista mostra apenas as Oportunidades que atendem a todos) e o botão
**Limpar** zera todos de uma vez. Ao aplicar qualquer filtro, a lista volta para a primeira página.

- **Oportunidades perdidas ficam ocultas por padrão.** Só aparecem quando você seleciona o estágio
  **Perdida** no filtro.
- **Você vê apenas o que lhe compete.** O representante vê **somente as próprias** Oportunidades; o
  vendedor vê as próprias **e as não atribuídas**; gerência comercial e diretoria veem **todas**. Busca
  e filtros nunca revelam Oportunidades fora do seu alcance.
- A lista mostra **apenas dados comerciais do pipeline** — nunca proposta, venda, pedido, reserva,
  financeiro ou comissão.

### 8.3 Detalhe da Oportunidade

Clique no **título** de uma Oportunidade na lista para abrir o seu **detalhe** — uma visão de consulta
para entender a negociação antes da próxima ação comercial. O detalhe mostra:

- **Resumo comercial:** responsável, origem, interesse principal, tipo de produto, valor estimado e
  previsão de fechamento (quando informados), anotações e as datas de criação e atualização.
- **Lead de origem:** nome e contatos (telefone, WhatsApp, e-mail) do Lead que deu início à
  Oportunidade, com um atalho **Ver lead de origem** — o Lead permanece rastreável e é sempre o registro
  de origem dos contatos e do histórico.
- **Perda:** quando a Oportunidade está **Perdida**, o detalhe mostra o motivo, a data, quem registrou e
  a anotação.
- **Movimentação de estágio:** o histórico das mudanças de estágio da Oportunidade (de qual estágio para
  qual, quando e por quem), com a mais recente no topo.
- **Histórico de atividades comerciais:** todas as atividades registradas (tipo, resultado, descrição,
  data, próxima ação e autor), com a mais recente no topo.

**Editar dados comerciais.** Se você tem permissão de operação, o detalhe oferece a ação **Editar dados
comerciais** (atalho de teclado **`e`**): ajuste o **valor estimado**, a **previsão de fechamento**, o
**tipo de produto / área de interesse** e as **anotações comerciais**. Deixar um campo em branco o
**limpa**. O **interesse principal** não é editado aqui — ele permanece como definido na qualificação do
Lead. O valor e a previsão de fechamento passam a aparecer também na **lista**. A edição **não** cria
financeiro, recebível, previsão de vendas, reserva, proposta nem comissão — é apenas informação
comercial.

**Registrar atividade.** Se você tem permissão de operação, o detalhe oferece a ação **Registrar
atividade** (atalho de teclado **`a`**): informe o **tipo** (ligação, WhatsApp, e-mail, reunião, nota
interna, solicitação de documento, discussão de preço, esclarecimento de requisito de viagem ou outro),
o **resultado** (cliente engajado, precisa follow-up, aguardando cliente, aguardando informação interna,
aderência identificada, pronta para proposta, sem interesse ou outro), a **data** (não pode ser no
futuro), uma **descrição** e, opcionalmente, uma **próxima ação**. A atividade entra no histórico (que
**não** pode ser apagado) e a data da última atividade e a próxima ação passam a aparecer também na
**lista**. Registrar uma atividade **não** move o estágio (use **Avançar estágio**) e **não** cria
proposta, venda, reserva nem financeiro.

**Avançar estágio.** Cada Oportunidade tem um **estágio atual** no pipeline comercial — **Nova**,
**Descoberta**, **Aderência**, **Pronta p/ proposta** ou **Perdida**. Se você tem permissão de operação,
o detalhe oferece a ação **Avançar estágio** (atalho de teclado **`s`**): a Oportunidade avança **um
passo por vez** ao longo do pipeline — **Nova → Descoberta → Aderência → Pronta p/ proposta** — e **não**
é possível **pular** etapas nem **voltar** a um estágio anterior. Cada avanço fica registrado na
**movimentação de estágio**. Em **Pronta p/ proposta** não há próximo passo (o pipeline desta sprint
termina aí) e *Pronta p/ proposta* é apenas um estágio — **não** gera uma proposta nesta versão. Para
encerrar uma negociação use **Marcar como perdida** (a seguir), de qualquer estágio ativo.

**Marcar como perdida.** Se você tem permissão de operação sobre Oportunidades, o detalhe oferece a ação
**Marcar como perdida** (atalho de teclado **`p`**): escolha um **motivo da perda** (obrigatório) —
sem orçamento, sem decisão, sem resposta, concorrente escolhido, incompatibilidade de produto, preço
muito alto, viagem cancelada, oportunidade duplicada, fora do perfil ou outro — e, opcionalmente, uma
anotação. (Esses motivos são próprios da Oportunidade, diferentes dos motivos de perda do Lead.) A
Oportunidade passa ao estágio **Perdida** (que é **final** — não reabre) e a perda fica registrada no
próprio detalhe. Essa ação **não altera** o Lead de origem. Quem só pode consultar não vê as ações, e
ninguém pode operar uma Oportunidade que não tem permissão para ver.

O detalhe mostra **apenas dados comerciais** — nunca proposta, venda, pedido, reserva, financeiro,
comissão ou atendimento.

### 8.4 Oportunidades pendentes

Abra **Oportunidades pendentes** na barra lateral (ou pela paleta de comandos, `Ctrl K`) para um
worklist operacional das Oportunidades que **precisam de ação**, para a negociação não estagnar
silenciosamente. A tabela lista, para cada Oportunidade, o **título** (leva ao detalhe), o **estágio**,
o **responsável**, o **valor estimado**, o **fechamento previsto**, a **próxima ação**, a **última
atividade** e os **motivos** da pendência. Cada Oportunidade recebe um ou mais motivos:

- **Sem atividade recente** — não há atividade comercial registrada nos últimos **14 dias**.
- **Próxima ação atrasada** — a próxima ação planejada está vencida.
- **Parada em Nova** — segue na etapa **Nova** há mais de 14 dias, sem avançar.
- **Parada em Descoberta** — segue na etapa **Descoberta** há mais de 14 dias.
- **Pronta p/ proposta** — está pronta para uma proposta (etapa ainda não tratada nesta versão).
- **Fechamento vencido** — a previsão de fechamento já passou.

Valem as mesmas **regras de visibilidade** da lista: um representante vê **somente as próprias**
pendências; um gerente vê todas. Oportunidades **perdidas** nunca aparecem aqui (são finais). É uma
leitura operacional, não um dashboard executivo — não há notificações, alertas por e-mail nem metas de
prazo (SLA), e nada de proposta, venda, reserva ou financeiro.

### 8.5 Indicadores de oportunidades

Abra **Indicadores de oportunidades** na barra lateral (ou pela paleta de comandos, `Ctrl K`) para uma
visão mínima do pipeline comercial. A tela tem **dois blocos**, como na maioria dos CRMs:

- **Volume no período** (filtrado pela data de criação; padrão = mês atual): **Total** e **Perdidas**, mais
  as quebras **Por estágio**, **Por origem** e **Por responsável** (contagens). Ajuste o período pelos
  campos **Criado de / até**, ou clique em **Todo o período** para ver todo o histórico.
- **Pipeline atual** (um retrato de hoje, que **independe do período**): **Ativas** (em aberto, não
  perdidas), **Prontas p/ proposta**, **Fechamento vencido** (em aberto com a previsão de fechamento já
  passada), o **valor do pipeline ativo** (R$) e o **valor por responsável**.

Valem as mesmas **regras de visibilidade**: um representante vê **somente os próprios** números; um
gerente vê os de todos. As Oportunidades **perdidas** entram na contagem do volume (e em "Perdidas"), mas
nunca no pipeline ativo nem no valor. É uma leitura operacional, **não** um dashboard executivo — sem
receita, fluxo de caixa, previsão de vendas, comissão ou ROI, e o indicador "Prontas p/ proposta" **não**
cria proposta.

## 9. Gerenciando cadastros

Os cadastros são as listas que alimentam o formulário de Lead e fluxos futuros. São quatro, todas
gerenciadas do mesmo jeito:

| Cadastro | Usado para |
|----------|------------|
| **Origens** | De onde vêm os Leads. |
| **Motivos de perda** | Por que um Lead acaba sendo perdido. |
| **Tipos de interação** | Tipos de contato (ligação, WhatsApp, nota…). |
| **Resultados de interação** | Desfecho de um contato. |

Abra-os em **Cadastros** no menu superior ou pela paleta de comandos.

### 9.1 A lista

Cada linha mostra o **código**, o **rótulo** (o que os usuários veem), a **ordem** e se está **Ativo**
ou **Inativo**. Por padrão só os ativos aparecem; use **Mostrar inativos / Ocultar inativos** para
alternar.

### 9.2 Criando um registro

1. Clique em **Novo**.
2. Preencha **Código** (um código interno estável), **Rótulo** (o texto exibido) e **Ordem** (ordem de
   exibição, um número ≥ 0).
3. Clique em **Salvar**. Códigos precisam ser únicos — reusar um é rejeitado.

### 9.3 Editando um registro

1. Clique no ícone de **lápis** na linha.
2. Você pode alterar o **rótulo**, a **ordem** e o interruptor **Ativo**. O **código** não pode ser
   alterado (é o identificador estável).
3. Clique em **Salvar**.

### 9.4 Ativando / desativando

- Clique no ícone de **proibido** para **desativar** um registro (exclusão lógica). Valores inativos
  permanecem para a fidelidade histórica, mas **não podem ser usados em novos Leads**.
- Para um registro inativo, clique no ícone de **check** para **reativá-lo**.

---

## 10. Mensagens e validação

O FKERP valida o que você digita e mostra mensagens claras, em português:

- **Mensagens por campo** aparecem ao lado do campo que precisa de atenção.
- **Campos obrigatórios** são marcados com um asterisco (`*`).
- O sistema nunca mostra erros técnicos crus; se algo inesperado acontecer, você recebe uma mensagem
  curta e segura e pode tentar de novo.

---

## 11. Saindo

Clique em **Sair** no canto superior direito da barra de menu. Você volta para a tela de login e sua
sessão é encerrada.

---

## 12. O que vem a seguir

Esta edição cobre todo o ciclo de vida de Leads da **Sprint 1** (cadastrar e encontrar Leads, o
detalhe, atribuição, histórico de interações com a regra de **Em contato**, **qualificação**, fluxo de
**Perda**, visibilidade por perfil, **Pendências**, **Indicadores** e deduplicação) e a **Sprint 2 —
Oportunidades** já completa: criar uma Oportunidade a partir de um Lead qualificado, a **lista
operacional** com filtros, o **detalhe da Oportunidade**, a movimentação **pelas etapas** (Nova →
Descoberta → Aderência → Pronta para proposta), o **registro de atividades comerciais**, a **edição dos
dados comerciais** (valor estimado e previsão de fechamento), o fluxo de **Perda** com motivo, as
**Oportunidades pendentes** e os **Indicadores de oportunidades** — tudo com visibilidade por perfil.

A próxima etapa (**Sprint 3**) dará sequência ao pipeline a partir de uma Oportunidade **Pronta para
proposta**, com a geração da **proposta** comercial. Este manual será atualizado a cada lançamento.

---

*Status do documento: Sprint 1 e Sprint 2 concluídas (captação de Leads e Oportunidades comerciais);
próxima etapa: Proposta (Sprint 3). Mantido junto com o produto.*
