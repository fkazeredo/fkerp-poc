# FKERP — Manual do Usuário

> **Público:** usuários finais do sistema FKERP (equipe comercial / vendas).
> **Idioma:** Português (pt-BR). Há uma edição em inglês mantida em paralelo
> (`fkerp-user-manual.en-US.md`).
> **Escopo:** cobre tudo que foi liberado até a **v0.47.2** — o **Comercial / CRM** (ciclo de vida completo
> de Leads e de Oportunidades), **Vendas & Propostas** (propostas, itens, valores e descontos, fluxo de
> aprovação/envio/aceite e os pedidos comerciais) e as **Operações de reserva** (o módulo Reservas: a fila de
> trabalho, o detalhe, o histórico de tentativas, a confirmação de itens de Pacote e Locação, o registro de
> falhas com retry, o **status consolidado da reserva refletido no Pedido**, a visão de **Reservas pendentes** e
> os **Indicadores de reservas**). Será ampliado à medida que novos recursos forem lançados.

---

## 1. O que é o FKERP

O FKERP é o ERP da empresa, organizado em **módulos** que seguem o fluxo de trabalho comercial e operacional.
O sistema cobre o **funil comercial de ponta a ponta** e a **operação de reservas**, com **monitoramento**,
**listas de apoio editáveis**. Cada usuário vê apenas os módulos e as ações do seu
perfil.

O que o sistema oferece hoje, por módulo:

- **Comercial** — o funil em ordem: **Leads** (interessados a trabalhar) → **Oportunidades** (negociações em
  andamento) → **Propostas** (a oferta formal, com itens, totais, desconto, validade e aprovação) → **Pedidos**
  (os negócios fechados). Inclui qualificar/perder leads, registrar interações e atividades, avançar o pipeline,
  e registrar o aceite/recusa do cliente. (Seções 5 a 9.)
- **Reservas** — a operação de back-office que **conduz as reservas** dos itens de um pedido fechado: a fila de
  trabalho, o detalhe, as tentativas manuais, a confirmação de pacotes e locações, o registro de falhas e a
  retentativa, e o status consolidado refletido no pedido. (Seção 10.)
- **Acompanhamento** — o monitoramento de todo o funil em dois hubs: **Pendências** (o que precisa de ação) e
  **Indicadores** (os números), com abas por área conforme o seu perfil.
- **Cadastros** — as **listas de apoio editáveis** que alimentam os formulários (origens, motivos, tipos,
  resultados, canais, tipos de item…), gerenciadas pela administração sem precisar de uma nova versão. (Seção
  11.)

Tudo com **entrada segura**, **navegação por teclado** e **validação clara** das informações.

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
- A mesma lógica de perfis vale para os demais módulos (Oportunidades, Propostas, Pedidos, Reservas,
  Financeiro): você só vê e opera o que o seu perfil permite (detalhado nas respectivas seções).
- No **Financeiro** (seção 11), o perfil **financeiro** cria e vê todas as **contas a receber**, **registra os
  pagamentos** (e passa a consultar os pedidos comerciais para localizar a origem), enquanto **Gerente** e
  **Diretoria** apenas consultam.
- A **administração** (perfil de cadastros) gerencia os **Cadastros** (seção 12); esse módulo só aparece no
  menu para quem tem esse perfil.

---

## 3. Navegação pelo teclado

O FKERP foi feito para ser usado **pelo teclado**, então você raramente precisa do mouse.

| Ação | Atalho |
|------|--------|
| Abrir a **paleta de comandos** (buscar qualquer ação) | `Ctrl` + `K` (ou `Cmd` + `K` no macOS) |
| Ver **todos os atalhos** (ajuda) | `?` |
| Novo lead | `n` |
| Ir para a **lista de Leads** | `g` depois `l` |
| Ir para **Oportunidades** | `g` depois `o` |
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

## 4. Início e navegação por módulos

Ao entrar, você vê a **tela inicial do sistema**, que apresenta um **card para cada módulo** ao qual você tem
acesso. Os módulos seguem o **fluxo de trabalho**:

- **Comercial** — o funil comercial em ordem: **Leads → Oportunidades → Propostas → Pedidos** (mais a ação
  **Novo lead**).
- **Reservas** — a fila operacional das reservas a operar, nascidas dos pedidos fechados (seção 10).
- **Financeiro** — as operações financeiras, começando pelas **contas a receber** geradas dos pedidos com reserva
  confirmada (seção 11).
- **Acompanhamento** — o monitoramento de todo o funil reunido em dois hubs: **Pendências** (o que precisa de
  ação — Leads, Oportunidades e **Reservas**) e **Indicadores** (os números do funil — Leads, Oportunidades,
  Propostas, Pedidos e **Reservas**). Cada hub é uma página com **abas** por área — você vê apenas as abas que o
  seu perfil pode ver.
- **Cadastros** — as listas de apoio que alimentam os fluxos (seção 12).

Clicar em um card abre a **home daquele módulo**, com os atalhos para suas telas.

O **menu lateral** acompanha essa organização: cada módulo é uma **seção recolhível** (acordeão). Clique no
**título do módulo** para ir à home dele, ou na **setinha** para recolher/expandir a seção — o sistema lembra
quais seções você deixou recolhidas. Assim o menu continua organizado mesmo quando o sistema cresce. No topo,
**Início** volta para a tela inicial do sistema.

Um lembrete dos principais atalhos de teclado é exibido no rodapé (e a tecla `?` mostra todos).

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
| **Estágio** | Nova, Descoberta, Aderência, Pronta p/ proposta, Ganha, Perdida. |
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

**Oportunidade ganha.** A Oportunidade passa ao estágio **Ganha** (também **final**) automaticamente quando se
**cria um pedido comercial** a partir de uma proposta aceita (ver a seção 9.8) — não há uma ação manual de
"marcar como ganha". Como a *Perdida*, a *Ganha* fica **oculta na lista padrão** (selecione o estágio no filtro
para vê-la) e sai do pipeline ativo e das pendências. Marcar como ganha **não** dispara financeiro nem reserva.

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

## 9. Propostas e pedidos (módulo Comercial)

As **Propostas** e os **Pedidos** são as etapas finais do funil **Comercial** — depois de Leads e
Oportunidades, no mesmo módulo. Uma **proposta comercial** é a oferta formalizada ao cliente, criada a partir
de uma Oportunidade que esteja **Pronta para proposta**.

### 9.1 Criando uma proposta
No detalhe de uma Oportunidade em **Pronta para proposta**, clique em **Criar proposta**. Informe o
**título / resumo para o cliente** (vem preenchido com o nome da Oportunidade, e você pode ajustar) e,
opcionalmente, a **validade**, os **termos comerciais**, **anotações** e o **responsável** (que, por
padrão, é o mesmo da Oportunidade). A proposta aproveita os dados da Oportunidade de origem — e, por meio
dela, do Lead — **sem redigitação**, e nasce como **Rascunho**. A Oportunidade **não** é alterada.

Regras importantes:

- **Só uma Oportunidade "Pronta para proposta"** pode originar uma proposta — estágios anteriores e
  Oportunidades **perdidas** não podem.
- **Uma proposta ativa por Oportunidade**: enquanto houver uma proposta em aberto, o sistema bloqueia
  criar outra para a mesma Oportunidade (uma nova só é permitida depois que a anterior for rejeitada,
  expirada ou cancelada).
- Criar uma proposta **não** cria venda, pedido, reserva nem dado financeiro — isso é etapa futura.

### 9.2 A lista e o detalhe
Abra **Comercial → Propostas** no menu para a lista operacional das propostas que você pode ver. Cada linha
mostra o **título**, o **status**, o **responsável**, a **Oportunidade de origem** (pelo nome, com atalho
para a Oportunidade), o **total**, a **validade**, a **data de criação** e a **data da última atualização**.

Por padrão a lista mostra apenas as propostas **ativas** — as **rejeitadas, expiradas e canceladas** não
aparecem, a menos que você as escolha no filtro de status. Use os **filtros** no topo para refinar:

- **Buscar** — por trecho do título da proposta ou do nome da Oportunidade de origem;
- **Status** — uma ou mais situações (inclua Rejeitada/Expirada/Cancelada para ver as inativas);
- **Responsável** — uma pessoa ou "Sem responsável";
- **Criada de / até** e **Validade de / até** — períodos;
- **Valor mín. / máx.** — faixa de total;
- **Limpar** — zera todos os filtros.

Clique no título para abrir o **detalhe**, para revisar tudo antes de agir. Ele reúne: o **resumo** da
proposta (responsável, validade, termos comerciais, notas de pagamento, anotações internas, datas), o
**status** atual, o card da **Oportunidade de origem** (com atalho para a Oportunidade), o card do **Lead de
origem** com os **contatos do cliente** (nome, telefone, WhatsApp, e-mail) e atalho para o Lead, os **itens**
com **subtotal / desconto / total** (veja a seguir) e o **Histórico de status** — uma linha do tempo das
mudanças de status (de → para, quando e por quem). Conforme o ciclo de vida da proposta avança, esse
histórico também passa a registrar a **aprovação**, o **envio** e a **decisão do cliente** (quem e quando).
Valem as mesmas **regras de visibilidade** das Oportunidades: o representante vê apenas as próprias propostas;
o gerente vê todas. O detalhe expõe apenas dados comerciais — nunca reserva, pagamento, recebível ou comissão.

### 9.3 Itens da proposta
O card **Itens** no detalhe da proposta lista o que será ofertado ao cliente. Enquanto a proposta estiver em
**Rascunho** e você tiver permissão para operá-la, é possível **adicionar**, **editar** e **remover** itens;
cada item compõe o **total** da proposta, exibido no rodapé do card e na lista.

Cada item tem:

- um **tipo** — *Pacote de viagem*, *Locação de veículo*, *Taxa de serviço* ou *Outro*;
- uma **descrição**, uma **quantidade** (número inteiro, pelo menos 1) e um **valor unitário**;
- um **desconto opcional**, aplicado por linha como **valor (R$)** ou **percentual (%)** — escolha
  *Sem desconto*, *Valor (R$)* ou *Percentual (%)*.

O **total da linha** é o valor unitário multiplicado pela quantidade, menos o desconto da linha. Um desconto
percentual deve ficar entre 0 e 100, e um desconto em valor não pode ultrapassar o valor da linha.

Os itens só podem ser alterados **enquanto a proposta estiver em Rascunho**. Adicionar ou editar itens
**não** reserva nada, não verifica disponibilidade com fornecedores e **não** cria venda, pedido, reserva,
financeiro nem comissão — a proposta continua sendo uma oferta comercial.

### 9.4 Totais, desconto e validade
Abaixo dos itens, a proposta mostra um resumo claro de **totais**:

- **Subtotal** — a soma dos totais de linha de todos os itens;
- **Desconto da proposta** — um desconto opcional sobre a proposta inteira (em **valor (R$)** ou
  **percentual (%)**), exibido apenas quando houver;
- **Total** — o subtotal menos o desconto da proposta. O total **nunca é negativo**: um desconto em valor é
  limitado ao subtotal.

Use **Editar dados comerciais** — disponível enquanto a proposta está em Rascunho e você pode operá-la — para
informar a **validade**, os **termos comerciais**, as **notas de pagamento** (texto descritivo apenas — **não**
cria nenhum registro financeiro ou de recebível) e o **desconto da proposta**. O total é recalculado
automaticamente.

Quando a oferta estiver pronta, use **Enviar para revisão**. Para enviar, a proposta precisa ter **ao menos um
item**, um **total maior que zero**, uma **data de validade** e um **responsável** — se faltar algo, o botão
fica desabilitado e um aviso lista o que falta (a validade você ajusta em **Editar dados comerciais**). Ao
enviar, ela sai de *Rascunho* e passa a *Pronta para revisão*, e a partir daí seus itens e dados não podem
mais ser editados. Enviar para revisão **não** envia a proposta ao cliente nem cria pedido, reserva,
financeiro ou comissão — apenas muda o status para a etapa interna de aprovação.

### 9.5 Aprovar ou rejeitar (revisão interna)
Uma proposta em *Pronta para revisão* passa pela **aprovação interna**: o **gerente** (perfil com a permissão
de aprovação) vê os botões **Aprovar** e **Rejeitar** no topo do detalhe.

- **Aprovar** muda a proposta para *Aprovada* e registra **quem aprovou e quando** no Histórico de status.
- **Rejeitar** abre um diálogo onde você escolhe um **motivo** (lista fixa) e, opcionalmente, uma **anotação**;
  a proposta passa a *Rejeitada*, o motivo fica visível no resumo e a transição (quem/quando) entra no
  Histórico. Uma proposta rejeitada **não** é enviada ao cliente; para revisar a oferta, crie uma **nova
  proposta** a partir da mesma Oportunidade (a rejeitada libera a Oportunidade).

Quem não é aprovador (vendedores, representantes) **não** vê esses botões e não pode aprovar — nem as próprias
propostas. Aprovar ou rejeitar **não** cria pedido comercial, reserva, financeiro nem comissão.

### 9.6 Marcar como enviada ao cliente
Depois de **aprovada**, a proposta pode ser **registrada como enviada/apresentada ao cliente**, para a empresa
acompanhar a decisão dele. No detalhe de uma proposta *Aprovada*, quem **opera** a proposta (vendedores,
representantes e o gerente) vê o botão **Marcar como enviada** (atalho **`m`**).

- Ao confirmar, é possível registrar **opcionalmente** o **canal de envio** — *E-mail*, *WhatsApp*,
  *Apresentação por telefone*, *Apresentação presencial* ou *Outro* — ou deixar em branco.
- A proposta passa a *Enviada*; o canal (quando informado) aparece no resumo como **Canal de envio**, e a
  transição **quem/quando** entra no Histórico de status. A proposta *Enviada* **continua disponível** para a
  decisão do cliente.

Marcar como enviada é apenas um **registro**: o sistema **não** dispara e-mail, WhatsApp ou ligação reais, não
gera PDF nem assinatura, e **não** cria aceite do cliente, pedido comercial, reserva, financeiro nem comissão.
Apenas propostas *Aprovadas* podem ser marcadas como enviadas (uma proposta em rascunho, em revisão ou
rejeitada não pode).

### 9.7 Registrar o aceite ou a recusa do cliente
Quando o cliente responde a uma proposta *Enviada*, registra-se a decisão dele para **encerrar a negociação**.
No detalhe de uma proposta *Enviada*, quem **opera** a proposta (vendedores, representantes e o gerente) vê os
botões **Registrar aceite** (atalho **`c`**) e **Registrar recusa** (atalho **`x`**).

- **Registrar aceite** abre um diálogo com uma **nota de confirmação opcional**; a proposta passa a *Aceita*. A
  proposta aceita **continua disponível** (é a oferta vencedora) e **prepara** a geração do **pedido comercial**,
  que virá numa próxima versão.
- **Registrar recusa** abre um diálogo onde você escolhe um **motivo** (lista fixa: *Preço muito alto*, *Escolheu
  concorrente*, *Viagem adiada*, *Viagem cancelada*, *Mudou de destino*, *Sem resposta após a proposta*, *Produto
  não atende*, *Outro*) e, opcionalmente, uma **anotação**; a proposta passa a *Rejeitada*. Esse motivo é o **da
  recusa do cliente**, distinto do motivo de uma rejeição na revisão interna (seção 9.5).

Em ambos os casos a transição **quem/quando** entra no Histórico de status, e o motivo/nota aparece no resumo.
Registrar a decisão **não** cria reserva, financeiro, comissão **nem pedido comercial**. Apenas propostas
*Enviadas* podem ser aceitas ou recusadas pelo cliente (rascunho, em revisão, aprovada ou já rejeitada não podem).

### 9.8 Criar o pedido comercial
Quando o cliente **aceita** a proposta, gera-se um **Pedido Comercial** — o **registro formal interno** do
negócio fechado, antes das etapas de reserva e financeiro. No detalhe de uma proposta *Aceita*, quem tem a
permissão de pedidos vê o botão **Criar pedido comercial** (atalho **`o`**).

- Ao confirmar, o sistema cria o pedido (você é levado direto ao **detalhe do pedido**) e marca a **Oportunidade
  de origem como Ganha**.
- O pedido **preserva** a proposta de origem, a oportunidade, o responsável, os **itens** e o **total** (uma
  cópia fiel do que foi vendido).
- O pedido nasce **Pendente de reserva** quando contém itens que exigem reserva (**Pacote de viagem** ou
  **Locação de veículo**); caso contrário, nasce **Reserva não necessária**.
- Cada proposta gera **um único** pedido ativo. Depois de criado, a proposta passa a mostrar **Ver pedido
  comercial** (em vez de criar outro).

Criar o pedido **não** cria reserva, financeiro, comissão nem pagamento — é apenas o registro do negócio
fechado. As reservas e o financeiro virão em próximas versões. Só uma proposta *Aceita* gera pedido.

### 9.9 A lista de pedidos
Os pedidos comerciais ficam em **Comercial → Pedidos** (atalho de teclado **`g d`**). A lista mostra, para cada
pedido: o **Identificador** (um número amigável, ex.: **PC-0001**, que leva ao detalhe), o **Resumo** (o título
da proposta de origem), a **Oportunidade**, o **Responsável**, o **Total**, o **Status**, o indicador de
**Reserva** (*Exige reserva* quando há item de Pacote de viagem ou Locação; senão *Não exige*), o **Status da
reserva** (o andamento das operações de reserva — *Pendente*, *Em andamento*, *Parcialmente confirmada*,
*Confirmada* ou *Falhou*; *Não iniciada* enquanto a reserva não começou) e a **data de criação**.

- **Filtros:** por **status**, **necessidade de reserva**, **responsável**, **período de criação** e **faixa de
  valor**, além de uma **busca** pelo resumo (título da proposta). Use **Limpar** para zerar.
- **Visibilidade por perfil:** cada um vê os pedidos que pode ver — **representantes** veem **apenas os seus**;
  **gerentes** veem **todos**. Nenhum filtro mostra um pedido que você não tem permissão de ver.
- Os pedidos **cancelados** ficam ocultos por padrão; selecione o status *Cancelado* no filtro para vê-los.

O **detalhe** do pedido mostra o número, o status, os **itens** e o **total** (cópia do que foi vendido), as
origens (proposta/oportunidade/lead) e uma nota de **próximo passo** (quando *Pendente de reserva*, indica que a
próxima etapa pode iniciar as operações de reserva). Para já chegar **pronto à etapa de reserva** sem redigitar
nada, o detalhe também reúne o **contexto comercial vindo da proposta de origem** — os **termos comerciais**, a
**validade**, as **notas** e as **observações de pagamento** (quando preenchidos) — e um indicador explícito de
**necessidade de reserva** (*Sim/Não*).

O detalhe traz ainda o **Status da reserva** — o reflexo do andamento das operações de reserva (seção 10) — com
uma orientação clara: quando a reserva está **Confirmada**, o pedido fica **pronto para seguir ao Financeiro**;
quando **Falhou**, o pedido aparece com um **problema na reserva** que precisa de atenção. Esse status é apenas
um **reflexo de leitura**: ele **não** altera a situação do pedido (que continua sob a área Comercial), **não**
cancela o pedido e **não** cria nada de financeiro.

O detalhe (e a lista) mostram também o **Status financeiro** — o reflexo da **conta a receber** ligada ao pedido
(seção 11): **Em aberto** / **Parcialmente paga** / **Paga** / **Vencida** (ou vazio, quando ainda não há conta a
receber). Quando **Paga**, o pedido fica **pronto para o Comissionamento** (etapa futura); quando **Vencida**, ele
aparece como **problema financeiro** a tratar; **Parcialmente paga** não é tratado como pago. Assim como o reflexo
da reserva, o status financeiro é apenas **leitura**: o pedido **continua sob a área Comercial** (o Financeiro
nunca assume o pedido), e refleti-lo **não** cria comissão. O status **Vencida** é marcado por uma **verificação
diária** que sinaliza as contas vencidas com saldo em aberto. A lista e o detalhe mostram **dados do pedido + os
reflexos de reserva e financeiro** — nunca pagamento ou comissão.

> A partir de um pedido **Pendente de reserva**, as **operações de reserva** já estão disponíveis no módulo
> **Reservas** (seção 10); conforme a reserva é confirmada ou falha por lá, o **Status da reserva** do pedido se
> atualiza automaticamente.

### 9.10 Indicadores de propostas

Abra **Acompanhamento → Indicadores** e selecione a aba **Propostas** (ou use a paleta de comandos, `Ctrl K`)
para uma visão mínima do fluxo de propostas. A tela tem **dois blocos**:

- **Volume no período** (filtrado pela data de criação; padrão = mês atual): **Total** de propostas,
  **Valor proposto** (a soma dos totais), **Valor aceito** (a soma das que hoje estão *Aceitas*) e
  **Recusadas** (as que hoje estão *Rejeitadas*), mais as quebras **Por status** e **Por responsável**.
  Ajuste o período pelos campos **Criado de / até**, ou clique em **Todo o período** para ver todo o histórico.
- **Em andamento** (um retrato de hoje, que **independe do período**): **Aguardando revisão** (propostas
  prontas para a revisão interna) e **Aguardando cliente** (propostas enviadas, à espera da decisão do cliente).

Valem as mesmas **regras de visibilidade**: um representante vê **somente os próprios** números; um gerente
ou a diretoria veem os de todos. É uma leitura operacional, **não** um dashboard executivo — sem receita,
fluxo de caixa, previsão, comissão ou ROI — e mostra **apenas** dados comerciais das propostas, nunca venda,
pedido, reserva, financeiro, pagamento ou comissão.

### 9.11 Indicadores de pedidos

Abra **Acompanhamento → Indicadores** e selecione a aba **Pedidos** para acompanhar os pedidos fechados. A tela
também tem **dois blocos**:

- **Volume no período** (por data de criação; padrão = mês atual): **Total** de pedidos e o **Valor total**
  (a soma), mais a quebra **Por responsável**. Ajuste ou limpe o período como na tela de propostas.
- **Em andamento** (retrato de hoje, **independe do período**): **Pendentes de reserva** — os pedidos que
  ainda aguardam o início das operações de reserva.

Valem as mesmas **regras de visibilidade** (representante vê só os próprios; gerente/diretoria veem todos). A
tela mostra **apenas** dados do pedido — nunca reserva, financeiro, pagamento ou comissão — e **não** é um
dashboard executivo.

## 10. Operando reservas (módulo Reservas)

Quando um pedido comercial é fechado com itens que exigem reserva (**Pacote de viagem** ou **Locação de
veículo**), nasce uma **solicitação de reserva** — o trabalho operacional de efetivar, junto aos fornecedores
e sistemas, o que foi vendido. O módulo **Reservas** é a área de retaguarda onde a equipe de operações
acompanha e trabalha essas solicitações.

> O processo é **manual e operacional**. O sistema **não** integra automaticamente com fornecedores, **não**
> verifica disponibilidade sozinho e **não** cria nada de financeiro, pagamento, comissão ou atendimento ao
> cliente. Ele organiza e registra o trabalho da equipe.

### 10.1 Perfis e acesso

| Perfil | Vê | Pode operar? |
|---|---|---|
| **Operações** (`operacoes`) | todas as reservas | sim — registrar tentativas, confirmar itens, registrar falhas |
| **Gerente Comercial** (`comercial`) | todas as reservas | sim (acompanhamento operacional) |
| **Diretoria** (`diretor`) | todas as reservas | **não** — apenas consulta |
| **Vendedores, Representantes, Financeiro / RH / TI** | — | sem acesso ao módulo Reservas |

Como em todo o sistema, a regra é garantida no servidor: quem não tem acesso não vê o menu **Reservas** nem
abre uma reserva por link direto.

### 10.2 A lista de reservas

Abra **Reservas** na barra lateral (ou pela paleta de comandos, `Ctrl K`, atalho **`g r`**). A lista é a
**fila de trabalho** das operações. Para cada reserva ela mostra: o **Pedido** de origem (o identificador
amigável, ex.: **PC-0001**, que leva ao detalhe), a **Proposta** de origem, o **Status**, o **Operador**
responsável pela reserva, o **Responsável** comercial, as contagens de **Itens p/ reservar** e de
**Confirmados**, a **Última tentativa**, e as datas de **criação** e **atualização**.

- **Filtros:** por **status**, **operador** (inclui *Sem operador*), **responsável comercial**, **tipo de
  item**, **período de criação** e um interruptor **Com falhas** (mostra só as reservas que têm algum item
  com falha). Use **Limpar** para zerar.
- **O que aparece por padrão:** as reservas **ativas**. As **Confirmadas** e **Canceladas** ficam ocultas
  (selecione esses status no filtro para vê-las); as **com falha** continuam visíveis, pois são problemas a
  resolver.
- **Visibilidade por perfil:** cada um vê apenas as reservas que pode ver; nenhum filtro revela uma reserva
  fora da sua permissão.

A lista mostra **apenas dados operacionais da reserva** — nunca financeiro, pagamento ou comissão.

### 10.3 O detalhe da reserva

Clique no pedido para abrir o **detalhe**. Ele reúne:

- **Resumo da reserva:** o pedido, o status, o operador e o responsável comercial, as contagens de itens
  **para reservar / confirmados / com falha**, as observações e os dados de auditoria (criada em / por).
- **Origens** (sempre rastreáveis): o **pedido**, a **proposta**, a **oportunidade** e o **lead** que deram
  origem à reserva, cada um com um atalho para a tela correspondente.
- **Itens da reserva:** cada item vendido, com seu **tipo**, **descrição**, **quantidade**, se **exige
  reserva** e seu **status** (Pendente, Em andamento, Confirmado, Falhou, Não requer reserva, Cancelado).
- Os cards **Confirmações de reserva**, **Problemas operacionais** e o **Histórico de tentativas** (abaixo).

### 10.4 Registrar uma tentativa

Cada passo do trabalho — acessar um sistema externo, ligar ou escrever para o fornecedor, conferir
internamente, checar disponibilidade — pode ser **registrado como uma tentativa**, formando um **histórico**
do que foi feito.

1. Clique em **Registrar tentativa** (atalho **`a`**).
2. Informe o **tipo** e o **resultado** (ex.: *Aguardando fornecedor*, *Disponibilidade encontrada*, *Precisa
   nova tentativa*…), a **data**, uma **descrição** e, opcionalmente, a qual **item** se refere (ou à reserva
   toda) e uma **próxima ação**.
3. Salve. A tentativa entra no histórico.

Registrar a primeira tentativa pode mover a reserva de **Pendente** para **Em andamento**. Uma tentativa é
**apenas histórico**: ela **não** confirma nem falha a reserva por conta própria.

### 10.5 Confirmar a reserva de um item

Quando um item (Pacote de viagem ou Locação de veículo) é efetivamente reservado junto ao fornecedor, você
**confirma** esse item.

1. Na tabela de itens, clique em **Confirmar** no item.
2. Informe o **sistema ou fornecedor** e o **localizador / código de reserva** (ambos obrigatórios) e a **data
   da confirmação**. Conforme o tipo, registre dados opcionais: para o **Pacote**, o pacote/destino, as datas
   de viagem e observações de passageiros; para a **Locação**, a locadora, a categoria do carro, os locais e as
   datas de retirada e devolução. Em ambos, há um campo de **notas operacionais**.
3. Salve. O item passa a **Confirmado** e a confirmação aparece no card **Confirmações de reserva**.

Conforme os itens são confirmados, o **status da reserva** se ajusta sozinho: **Confirmada** quando todos os
itens que exigem reserva estão confirmados; **Parcialmente confirmada** quando só parte está. Confirmar
**não** chama nenhum sistema externo e **não** cria voucher, financeiro, pagamento nem comissão.

### 10.6 Registrar uma falha e retentar

Quando a reserva de um item **não dá certo** — sem disponibilidade, fornecedor indisponível, dados inválidos,
preço alterado etc. — você registra a **falha** do item.

1. Na tabela de itens, clique em **Falhar**.
2. Escolha o **motivo da falha** (obrigatório) entre as opções — *Sem disponibilidade*, *Fornecedor
   indisponível*, *Dados comerciais inválidos*, *Dados do passageiro ausentes*, *Sistema externo
   indisponível*, *Preço alterado*, *Erro de operação manual*, *Fora da política* ou *Outro* —, a **data** e,
   opcionalmente, uma **nota**.
3. Salve. O item passa a **Falhou** e aparece no card **Problemas operacionais**, com o motivo, a nota e quem
   registrou, quando.

Um item com falha **continua visível como um problema operacional** a resolver — ele **não some** da reserva.
A reserva, então, fica **Parcialmente confirmada** (se algum outro item já estava confirmado) ou **Falhou**
(se nenhum estava).

**Retentar é simples:** um item com falha pode receber **novas tentativas** (seção 10.4) e pode ser
**confirmado depois** (seção 10.5) — ao confirmá-lo, a reserva se **reconsolida** automaticamente para
*Parcialmente confirmada* ou *Confirmada*. Registrar uma falha **não** cancela o pedido comercial e **não**
cria nada de financeiro, pagamento, comissão ou atendimento ao cliente.

### 10.7 Como a reserva aparece no Pedido

O **status consolidado** da reserva (*Pendente* · *Em andamento* · *Parcialmente confirmada* · *Confirmada* ·
*Falhou*) é **refletido automaticamente** no **Pedido Comercial** de origem (seção 9.9), para que toda a equipe
veja em que pé está a reserva sem sair da tela do pedido. Uma reserva **Confirmada** deixa o pedido **pronto para
seguir ao Financeiro**; uma reserva que **Falhou** marca o pedido com um **problema de reserva** a tratar. Esse
reflexo é apenas **leitura**: o Pedido continua **sob a área Comercial**, a sua situação não muda por causa da
reserva e ele **nunca** é cancelado automaticamente — nem é criado qualquer registro financeiro.

> **Preparando o Financeiro (próximo ciclo).** Uma reserva **Confirmada** deixa o pedido pronto para a futura
> etapa **Financeira** sem redigitação: a reserva guarda todo o registro operacional — os **localizadores**, o
> **sistema/fornecedor** e as **datas** de cada confirmação, o histórico de tentativas e as origens
> (pedido/proposta/oportunidade/lead); o **valor** do negócio continua no **Pedido**. O Financeiro, quando chegar,
> apenas **lê** essas informações; a reserva permanece **sem qualquer valor financeiro**.

### 10.8 Reservas pendentes (o que precisa de ação)

Para que nenhuma reserva trave em silêncio, há uma visão de **Reservas pendentes**: a lista das solicitações de
reserva que **precisam de ação**, cada uma marcada com os **motivos** da pendência. Ela vive como a aba
**Reservas** do hub **Acompanhamento → Pendências** (atalho `Ctrl K`), ao lado de Leads e Oportunidades.

Uma reserva aparece como pendente quando: está **sem operador** responsável; está **Pendente sem nenhuma
tentativa**; está **Em andamento sem tentativa recente** (sem atividade há mais de **7 dias**); tem um **item com
falha**; tem um **item que exige reserva ainda pendente**; está **Parcialmente confirmada**; ou tem uma **próxima
ação atrasada**. As reservas já **Confirmadas** ou **Canceladas** não aparecem (não precisam de ação).

Cada linha mostra o pedido (PC-000n, com link para o detalhe da reserva), a proposta, o status, o operador, o
responsável, os itens a reservar, a próxima ação e a última tentativa, além das **etiquetas de motivo**. É uma
visão **operacional** (não um painel executivo): **somente leitura**, sem alertas por e-mail, sem motor de
notificação ou SLA e sem novas tentativas automáticas. Valem as mesmas **regras de visibilidade** das reservas —
quem não tem acesso a reservas (vendedores, representantes, financeiro) não vê esta lista.

### 10.9 Indicadores de reservas (carga e problemas)

Para o gestor de operações acompanhar a **carga de trabalho e os problemas** das reservas, há os **Indicadores de
reservas**, na aba **Reservas** do hub **Acompanhamento → Indicadores**. Como nos demais indicadores, há **dois
blocos**:

- **Volume no período** (pela data de criação; padrão = mês atual): o **Total** de reservas, a quebra **Por
  status** (Pendente, Em andamento, Parcialmente confirmada, Confirmada, Falhou, Cancelada), os **Itens por tipo**
  (Pacote de viagem, Locação de veículo, Taxa de serviço, Outro), os **Itens com falha** e o **Tempo médio até a
  confirmação** (a média do tempo entre criar a reserva e confirmá-la, no período). Ajuste o período pelos campos
  **Criado de / até** ou clique em **Todo o período**.
- **Em andamento** (retrato de hoje, **independe do período**): **Prontas p/ Financeiro** — as reservas hoje
  **Confirmadas**, que podem seguir para o Financeiro.

Valem as mesmas **regras de visibilidade**: gestores de operações e comerciais veem os números globais; quem não
tem acesso a reservas não vê estes indicadores. É uma visão **operacional**, não um painel executivo — **sem**
dados financeiros, de pagamento, de comissão ou de integração externa (integrações ainda não existem).

## 11. Operações financeiras — contas a receber (módulo Financeiro)

O módulo **Financeiro** inicia as **operações financeiras** a partir dos negócios já fechados. Ele entrega as
**contas a receber**: o valor que a empresa tem a receber de um cliente por um pedido cuja **reserva está
confirmada**. A partir desta versão já é possível **registrar o pagamento integral** de uma parcela, dando baixa na
conta. **Comissões, notas fiscais, pagamentos parciais e estornos** chegam nas próximas versões.

### 11.1 Perfis e acesso

- O **Financeiro** (perfil financeiro) **cria e vê todas** as contas a receber e **registra os pagamentos**. Para
  localizar o pedido de origem, esse perfil também passa a **consultar os pedidos comerciais** (apenas leitura —
  ele não cria nem altera pedidos).
- O **Gerente comercial** e a **Diretoria** **consultam** as contas a receber (somente leitura, **sem registrar
  pagamentos**), para acompanhamento.
- **Vendedores, representantes** e as áreas de **RH/TI** **não** veem o módulo Financeiro.

Como em todo o sistema, **o servidor é a autoridade**: a tela apenas esconde o que o seu perfil não pode fazer.

### 11.2 O cliente (pagador)

Quando um **pedido comercial é criado** (o fechamento do negócio), o sistema **promove o Lead a Cliente**
automaticamente — copiando o nome e os contatos do Lead. Esse **Cliente é o pagador** que aparece na conta a
receber. Não há um cadastro manual de clientes nesta versão: ele nasce sozinho no fechamento. Documento (CPF/CNPJ)
e endereço de cobrança ficam para uma etapa futura.

### 11.3 Gerando uma conta a receber

Há dois caminhos:

- No módulo **Financeiro → Contas a receber**, clique em **Nova conta a receber**.
- No **detalhe de um pedido** com a **reserva confirmada**, clique em **Gerar conta a receber** — o pedido já vem
  selecionado.

No formulário, informe:

- **Pedido** (obrigatório) — a lista oferece apenas os **pedidos elegíveis**: aqueles com **reserva confirmada** e
  que **ainda não têm** uma conta a receber ativa. O valor do pedido aparece como referência.
- **Vencimento** (obrigatório quando **não** há parcelas) — a data de vencimento da conta.
- **Parcelas** (opcional) — você pode **dividir a conta em parcelas** (veja abaixo).
- **Responsável financeiro** (opcional) — quem, no Financeiro, cuida desta conta.
- **Observações de pagamento** (opcional) — um texto livre (não é um registro de pagamento).

**Parcelamento.** Use **Adicionar parcela** para dividir a conta. Cada parcela tem um **valor**, um **vencimento**
e, opcionalmente, observações. A **soma das parcelas precisa ser igual ao valor do pedido** — a tela mostra o
**Restante** em tempo real e só libera o botão **Gerar conta a receber** quando o valor bate. **Sem** parcelas, a
conta nasce com **uma única parcela** no valor total, no vencimento informado. As parcelas começam **Em aberto**.

A conta a receber **preserva a origem comercial** (pedido, proposta, oportunidade e lead), o **cliente** e o
**valor total** do pedido, e nasce no estado **Em aberto**. Cada pedido tem **no máximo uma conta a receber ativa**
— se já houver uma, o sistema avisa. Só pedidos com **reserva confirmada** podem gerar uma conta; um pedido sem essa
condição é recusado com uma mensagem clara. Gerar a conta (e suas parcelas) **não** cria pagamento, comissão, nota
fiscal nem altera o pedido.

### 11.4 A lista e o detalhe

A tela **Contas a receber** é a **lista operacional** — as contas que precisam de acompanhamento financeiro, para
**priorizar a cobrança**. Para cada conta ela mostra: o **pedido** de origem (código PC-000n), o **cliente
(pagador)**, o **valor total**, o **valor pago**, o **valor em aberto**, o **status**, o **próximo vencimento**
(com um destaque **Vencida** quando a conta está atrasada), o **responsável comercial** e o **financeiro**, a data
de **criação** e o **último pagamento**. Os valores **pago**, **em aberto** e **último pagamento** refletem os
pagamentos já registrados na conta.

**Por padrão**, a lista mostra as contas **em acompanhamento** (Em aberto, Parcialmente paga, Vencida) e **omite as
Pagas e Canceladas** — selecione esses status no filtro para vê-las. As **vencidas continuam visíveis** como
problema operacional. Você pode **filtrar** por **status**, **cliente (pagador)**, **pedido nº**, **período de
vencimento**, **período de criação**, **responsável comercial**, **responsável financeiro**, **faixa de valor** e
**somente vencidas**. Cada perfil vê apenas as contas que tem permissão para ver — **representantes não veem** o
detalhe financeiro. A lista **não** mostra dados de **comissão** nem de **conciliação bancária**.

Clique em uma conta para abrir o seu **detalhe** — a consulta completa para entender a **origem, as parcelas, os
pagamentos e o saldo em aberto**. O detalhe reúne:

- o **resumo**: **valor total**, **valor pago**, **valor em aberto**, **próximo vencimento** (com a marca
  **Vencida** e "vencida há N dias" quando atrasada), o **status**, o **responsável financeiro** e as **observações
  financeiras**;
- o **cliente (pagador)**;
- a **origem comercial** rastreável — o **pedido** (PC-000n) e as **referências** da **proposta** e da
  **oportunidade** de origem (com atalhos para abri-los, além do lead) e o **responsável comercial**;
- a **tabela de parcelas** (número, valor, **pago**, **em aberto**, vencimento, status e observações), com cada
  **parcela vencida** (não paga e com vencimento passado) **em destaque** e, para quem pode, o botão **Registrar
  pagamento** em cada parcela com saldo;
- a seção **Pagamentos** — o **histórico dos pagamentos registrados** (parcela, valor, data, forma de pagamento,
  quem registrou e observações); fica vazia enquanto não houver pagamentos.

A tela mostra **apenas dados da conta a receber e seus pagamentos** — nunca **comissão**, **conciliação bancária**
ou **nota fiscal**. Você só abre o detalhe das contas que tem permissão para ver.

### 11.5 Registrando um pagamento (integral ou parcial)

Quem tem o perfil **financeiro** pode **registrar um pagamento** de uma parcela que ainda tenha saldo (**Em aberto**
ou **Parcialmente paga**) — o valor recebido pode ser **integral** ou **parcial**. No detalhe da conta, clique em
**Registrar pagamento** na parcela desejada (ou use o atalho **`p`**, que abre o registro para a primeira parcela com
saldo). Informe:

- **Valor** (obrigatório) — já vem preenchido com o **saldo em aberto** da parcela; ajuste para registrar um
  **pagamento parcial**. O valor deve ser **maior que zero** e **não pode exceder o saldo em aberto** da parcela
  (pagamento a maior não é tratado nesta versão).
- **Forma de pagamento** (obrigatória) — escolha entre as formas cadastradas (Dinheiro, Transferência bancária, Pix,
  Cartão de crédito, Cartão de débito, Pagamento de fatura, Outro). O administrador gerencia essa lista em
  **Cadastros → Formas de pagamento**.
- **Data do pagamento** (obrigatória) — quando o valor foi recebido; **não pode ser futura**.
- **Observações** (opcional) — uma referência ou anotação livre.

Ao confirmar: se o valor **quita o saldo** da parcela, ela fica **Paga**; se cobre **parte** do saldo, fica
**Parcialmente paga** e você pode registrar **novos pagamentos** até quitá-la. A conta a receber fica **Paga** quando
**nada mais está em aberto**, ou **Parcialmente paga** enquanto houver saldo. O pagamento aparece na seção
**Pagamentos** e os valores **pago** / **em aberto** (da conta e de cada parcela) se atualizam. Registrar um pagamento
**não** cria comissão, nota fiscal, recibo nem faz conciliação bancária, e **não** altera o pedido, o lead ou o
cliente.

### 11.6 Estados da conta e das parcelas · Vencidas

Tanto a conta a receber quanto cada **parcela** podem estar **Em aberto**, **Parcialmente paga**, **Paga**,
**Vencida** ou **Cancelada**. Toda conta nasce **Em aberto**; ao registrar pagamentos, a parcela passa a
**Parcialmente paga** (pagamento parcial) ou **Paga** (quitada), e a conta a **Parcialmente paga** ou **Paga**.

Uma conta torna-se **Vencida** quando o vencimento passou e ainda há **saldo em aberto** — uma **verificação
diária** marca automaticamente essas contas como **Vencida** (parcelas **pagas** ou **canceladas** nunca ficam
vencidas). As contas **Vencidas continuam visíveis por padrão** na lista (como problema a priorizar) e você pode
filtrar **somente vencidas**; no detalhe, cada **parcela vencida** aparece sinalizada. **Identificar o atraso não
aplica juros nem multa, não envia notificação e não abre atendimento ao cliente** nesta versão.

---

## 12. Gerenciando cadastros

Os cadastros são as **listas que alimentam os formulários** de todo o sistema — as opções que você
escolhe nos campos de tipo, motivo, resultado e canal. A partir desta versão, **praticamente todas essas
listas são editáveis** pela administração: você pode **adicionar, renomear, reordenar e desativar** opções
**sem depender de uma nova versão do sistema**. O que antes era fixo no programa agora é dado que você
controla.

São gerenciadas do mesmo jeito, organizadas por área:

| Área | Cadastros |
|------|-----------|
| **Leads** | Origens · Motivos de perda · Tipos de interação · Resultados de interação |
| **Oportunidades** | Tipos de atividade · Resultados de atividade · Motivos de perda (oportunidade) |
| **Propostas** | Motivos de rejeição · Motivos de recusa do cliente · Canais de envio · Tipos de item |
| **Reservas** | Tipos de tentativa · Resultados de tentativa · Motivos de falha |
| **Financeiro** | Formas de pagamento |

Quando você renomeia uma opção, o novo rótulo passa a aparecer nas telas operacionais imediatamente; quando
você **desativa** uma opção, ela deixa de ser oferecida em novos registros, mas continua visível nos
registros antigos que já a usavam (fidelidade histórica). Abra os cadastros em **Cadastros** no menu, ou
pela paleta de comandos.

### 11.1 A lista

Cada linha mostra o **código**, o **rótulo** (o que os usuários veem), a **ordem** e se está **Ativo**
ou **Inativo**. Por padrão só os ativos aparecem; use **Mostrar inativos / Ocultar inativos** para
alternar.

### 11.2 Criando um registro

1. Clique em **Novo**.
2. Preencha **Código** (um código interno estável), **Rótulo** (o texto exibido) e **Ordem** (ordem de
   exibição, um número ≥ 0).
3. Clique em **Salvar**. Códigos precisam ser únicos — reusar um é rejeitado.

### 11.3 Editando um registro

1. Clique no ícone de **lápis** na linha.
2. Você pode alterar o **rótulo**, a **ordem** e o interruptor **Ativo**. O **código** não pode ser
   alterado (é o identificador estável).
3. Clique em **Salvar**.

### 11.4 Ativando / desativando

- Clique no ícone de **proibido** para **desativar** um registro (exclusão lógica). Valores inativos
  permanecem para a fidelidade histórica, mas **não podem ser usados em novos Leads**.
- Para um registro inativo, clique no ícone de **check** para **reativá-lo**.

---

## 13. Mensagens e validação

O FKERP valida o que você digita e mostra mensagens claras, em português:

- **Mensagens por campo** aparecem ao lado do campo que precisa de atenção.
- **Campos obrigatórios** são marcados com um asterisco (`*`).
- O sistema nunca mostra erros técnicos crus; se algo inesperado acontecer, você recebe uma mensagem
  curta e segura e pode tentar de novo.
- **Alterações não salvas são protegidas.** Se você começar a preencher um formulário ou um diálogo de edição
  e tentar sair da página — por um link, por um atalho de teclado ou fechando a aba do navegador — o sistema
  pede confirmação antes de descartar as alterações (**Descartar** para sair, **Continuar editando** para
  ficar).

---

## 13.1 Atalhos de teclado

O menu é organizado em **módulos** claros — **Comercial**, **Reservas**, **Financeiro**, **Acompanhamento** e
**Cadastros** — cada um uma seção recolhível na barra lateral, com uma **home própria** (veja a seção 4). Tudo
também é acessível pelo teclado:

- **`Ctrl`/`Cmd` + `K`** — a **paleta de comandos**: busque e vá para qualquer tela ou ação de qualquer lugar.
- **`?`** — mostra a ajuda completa de atalhos a qualquer momento.
- **Ir para (tecle `g`, depois uma letra):** `g i` Início · `g l` Leads · `g o` Oportunidades · `g p`
  Propostas · `g d` Pedidos · `g r` Reservas · `g f` Financeiro (contas a receber) · `g c` Cadastros.
  **`n`** cria um novo lead.
- **Em um lead:** `i` registrar interação · `q` qualificar · `o` criar oportunidade · `p` marcar perdido ·
  `r` reatribuir · `Esc` voltar.
- **Em uma oportunidade:** `a` registrar atividade · `e` editar dados · `s` avançar estágio · `p` marcar
  perdida · `Esc` voltar.
- **Em uma proposta:** `i` adicionar item · `e` editar dados comerciais · `s` enviar para revisão · `Esc`
  voltar.
- **Em uma reserva:** `a` registrar tentativa · `Esc` voltar.
- **Em uma conta a receber:** `p` registrar pagamento (primeira parcela com saldo) · `Esc` voltar / cancelar.

---

## 14. Saindo

Clique em **Sair** no canto superior direito da barra de menu. Você volta para a tela de login e sua
sessão é encerrada.

---

## 15. O que vem a seguir

Esta edição cobre todo o ciclo de vida de Leads da **Sprint 1** (cadastrar e encontrar Leads, o
detalhe, atribuição, histórico de interações com a regra de **Em contato**, **qualificação**, fluxo de
**Perda**, visibilidade por perfil, **Pendências**, **Indicadores** e deduplicação) e a **Sprint 2 —
Oportunidades** já completa: criar uma Oportunidade a partir de um Lead qualificado, a **lista
operacional** com filtros, o **detalhe da Oportunidade**, a movimentação **pelas etapas** (Nova →
Descoberta → Aderência → Pronta para proposta), o **registro de atividades comerciais**, a **edição dos
dados comerciais** (valor estimado e previsão de fechamento), o fluxo de **Perda** com motivo, as
**Oportunidades pendentes** e os **Indicadores de oportunidades** — tudo com visibilidade por perfil.

A **Sprint 3 — Vendas & Propostas** está concluída: a partir de uma Oportunidade pronta é possível **criar uma
proposta comercial** (**Comercial → Propostas**), gerir seus **itens, valores e descontos**, **enviá-la para
revisão interna**, **aprovar ou rejeitar**, **marcar uma proposta aprovada como enviada** ao cliente,
**registrar o aceite ou a recusa do cliente**, **criar o pedido comercial** a partir de uma proposta aceita
(que marca a Oportunidade como **Ganha**) e **consultar a lista de pedidos** (**Comercial → Pedidos**).

A **Sprint 4 — Operações de reserva** está **concluída**: o módulo **Reservas** (seção 10) entrega a **fila de
trabalho** das solicitações de reserva nascidas dos pedidos fechados, o **detalhe** da reserva com suas origens
rastreáveis, o **histórico de tentativas**, a **confirmação** de itens de **Pacote de viagem** e de **Locação de
veículo**, o **registro de falhas com retry**, o **reflexo do status consolidado da reserva no Pedido Comercial**
(seção 10.7) — deixando o pedido identificável como *pronto para o Financeiro* ou *com problema de reserva* —, a
visão de **Reservas pendentes** (seção 10.8) e os **Indicadores de reservas** (seção 10.9), no hub Acompanhamento.
A entrega foi **validada de ponta a ponta**.

A **Sprint 5 — Operações Financeiras** está em andamento: o módulo **Financeiro** (seção 11) já permite **gerar
contas a receber** a partir dos **pedidos com reserva confirmada**, com o **Cliente** (pagador) criado
automaticamente no fechamento, o **parcelamento** da conta (uma ou várias parcelas, cuja soma é igual ao valor do
pedido), a **lista operacional** das contas a acompanhar — com colunas de **pago / em aberto**, destaque de
**vencidas** e um conjunto completo de **filtros** (status, cliente, pedido, períodos de vencimento e criação,
responsáveis, faixa de valor e somente vencidas) — e o **detalhe de consulta** de cada conta (origem comercial com
referências, parcelas com atraso em destaque, pago/em aberto e a seção de pagamentos pronta para a próxima etapa),
tudo com visibilidade por perfil. O Financeiro lê o registro já
pronto (valor no Pedido; localizadores, datas e fornecedor na reserva) sem redigitar dados, e Reserva e Comercial
seguem separados. As **próximas etapas** desta Sprint trazem o **registro de pagamentos** (e as transições para
*Paga* / *Parcialmente paga* / *Vencida*), o reflexo do status financeiro no pedido e a **comissão**. Este manual
será atualizado a cada lançamento.

---

*Status do documento: Sprints 1, 2, 3 e 4 concluídas e Sprint 5 (Operações Financeiras) em andamento — módulo
Financeiro com geração de contas a receber a partir de pedidos com reserva confirmada, o Cliente (pagador)
materializado no fechamento, o **parcelamento** da conta, a **lista operacional** das contas a acompanhar (pago /
em aberto, vencidas em destaque, filtros completos) e o **detalhe de consulta** da conta (origem, parcelas, pago/em
aberto e a seção de pagamentos pronta), com visibilidade por perfil. Próximas etapas da Sprint 5: pagamentos e
comissão. Mantido junto com o produto.*
