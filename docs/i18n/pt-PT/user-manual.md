# <a id="vehicle-expenses-automated--user-manual"></a>Despesas com veículos automatizadas - Manual do utilizador

> **Editar fonte (Markdown).** Os browsers e o leitor na aplicação abrem o **HTML renderizado**:
> - Web: [`docs/user-manual/index.html`](index.html) (regenerar com `./scripts/render-user-manual.sh`)
> - Aplicação: Ajuda/Sobre → manual completo (HTML + screenshots incluídos)
>
> Não direcione os utilizadores finais para URLs `.md` em bruto — os browsers apenas mostram texto simples.

Rastreamento com câmara para abastecimentos de combustível e despesas de veículos, com sincronização opcional de vários dispositivos e backup nas **suas** contas na nuvem.

Este é o **manual completo** (screenshots + cada passo). No telefone, **Menu → Ajuda** é um guia de primeiros passos mais curto.

**Não abordado aqui:** Importar imagens antigas, experiência de alinhamento e experiência de bomba (desenvolvedor/ferramentas avançadas).

---

## <a id="table-of-contents"></a>Índice

1. [O que precisa](#what-you-need)
2. [Resumo dos ícones](#icons-at-a-glance)
3. [Abrir o menu](#open-the-menu)
4. [Configuração inicial: Gerir veículos](#first-time-setup-manage-vehicles)
5. [Backups e sincronização de vários dispositivos](#backups-and-multi-device-sync)
6. [Abastecer (combustível)](#fill-up-fuel)
7. [Iniciar viagem](#start-trip)
8. [Despesas](#expenses)
9. [Relatórios](#reports)
10. [Definições (preferências locais)](#settings-local-preferences)
11. [Sincronizando](#syncing)
12. [Ajuda e Sobre](#help--about)
13. [Documentos relacionados](#related-docs)

---

## <a id="what-you-need"></a>O que precisa

- telefone ou tablet Android.
- Para melhor OCR: uma vista clara do **conta-quilómetros do painel** e dos **totais da bomba** (ou introduza os números manualmente).
- Opcional: contas **que controla** para dados de folhas de cálculo e/ou cópia de segurança de fotografias (consulte [Backups e sincronização de vários dispositivos](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Ícones em resumo

Aparecem nas telas principais. Conhecê-los poupa muita caça.

| Onde | Ícone/controlo | O que faz |
|-------|----------------|-------------|
| Barra superior | **☰ Menu** (hambúrguer) | Abre a gaveta de navegação |
| Barra superior | **ⓘ** (ajuda de página) | Breve ajuda para a página **atual** (junto ao menu, quando disponível) |
| Barra superior | **`?N`** (amarelo) | Questões pendentes de revisão de importação — abre Revisão de importação |
| Barra superior | **!** (vermelho) | Uma folha de cálculo ou destino de fotografia falhou recentemente. Abra **Sincronizando** para corrigir |
| Barra superior | **☰ + ←** | Relatório de filhos e lista de despesas mostram **menu e voltar** juntos; O hub de relatórios é apenas menu |
| Configurações/edição de combustível | **←** | Voltar (folha de cálculo/foto de definições e edição de combustível permanecem focadas) |
| Preencher | **Círculo branco** (obturador) | Captar o odómetro ou o visor da bomba para OCR |
| Preencher | **Disco / Guardar** | Salvar o abastecimento (necessita de veículo e pelo menos um de odo/volume/custo) |
| Preencher | **↕ setas** (interruptor de modo) | Alternar entre **modo conta-quilómetros** e **modo bomba (custo/volume)**. Borda verde destaca grupo de campo ativo |
| Preencher | **↔ setas** (entre custo e volume) | Altere o custo e o volume se o OCR os colocar nos campos errados |
| Preencher | **Zoom 1x /…** | Taxas de zoom da câmara quando a objetiva as suporta |
| Preencher (após captura) | **Atualizar** no botão principal | Descartar visualização e voltar à câmara ao vivo |
| Preencher (durante o processamento) | **X** no botão principal | Cancelar captura/OCR em curso |
| Despesa | **Guardar** | Poupe nas despesas |
| Despesa | **Círculo do obturador** | Tire uma fotografia ao recibo |
| Despesa | **Galeria** | Escolha uma imagem de recibo da biblioteca |
| Despesa | **Refazer** | Limpe a foto do recibo atual e fotografe novamente |
| Despesar / Gerir Veículos | **+ / −** FABs | Amplie a pré-visualização da foto |
| Diálogo de pontos de referência | **Editar OCR** ​​​​| Corrija ou adicione texto de referência que os motores tenham perdido |
| Ficha de trabalho/Formulários fotográficos | **🔍 Pesquisa** | Procure uma folha de cálculo ou pasta no Google Drive (após iniciar sessão) |

Os símbolos de moeda nos campos de custo e **G/L** nos campos de volume podem ser tocados: abra um pequeno menu para alterar a moeda ou galões versus litros para essa entrada.

---

## <a id="open-the-menu"></a>Abra o menu

1. Toque em **☰** no canto superior esquerdo.
2.º Escolha uma página.

![Gaveta de navegação](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Gaveta principal:** Preencher · Iniciar viagem · Gerir veículos · Nova despesa · **Relatórios** · Definições · Sincronização · Ajuda · Sobre.

**Gaveta de experiências** (Definições → Mostrar ecrãs de experiências): Experiência de alinhamento · Experiência de bombeamento · **Importar fotografias antigas**.

**Via hub de relatórios (não gaveta principal):** Lista de despesas · Preencher histórico.

---

## <a id="first-time-setup-manage-vehicles"></a>Configuração inicial: Gerir veículos

OCR e **correspondência automática de veículos** funcionam melhor depois de registar cada veículo com uma **foto de referência do painel**, executar o **Discovery**, definir o corte do odómetro a partir de uma semente de remo e guardar pontos de referência para esse painel.

### <a id="open-manage-vehicles"></a>Abrir Gerir Veículos

Menu → **Gerir viaturas**. Escolha um veículo (ou **Adicionar novo veículo**).

![Gerir veículos com referência, colheitas e descobertas](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Adicionar ou editar um veículo

1. Abra o menu suspenso **Veículo** → escolha um veículo ou **Adicionar novo veículo**.
2. Capte ou escolha uma **foto de referência do painel** nítida (conjunto de instrumentos completo, bem iluminado, telefone quase quadrado). Utilize **Tirar fotografia** ou **Galeria**.
3. Toque em **Executar descoberta** — a deteção de remo coloca **sementes** amarelas na fotografia (os pontos de referência utilizam o mesmo `"ML Kit"` JSON que antes).
4. Toque em **Selecionar Odo**, toque na semente nos dígitos, ajuste as pegas e depois em **Guardar**.
5. **Editar Odo** ajusta uma caixa que já lá está. **Clear Odo** remove-o e o botão apresenta **Select Odo**.
6. Reveja com **Mostrar pontos de referência**. Utilize **Editar OCR** ​​para corrigir erros de leitura ou **adicionar** texto que foi perdido.
7. Preencha **Nome do Veículo** (obrigatório), para além da marca/modelo/ano/matrícula conforme desejar.
8. Toque em **Criar veículo** ou **Guardar alterações** (requer nome + fotografia de referência para um novo veículo).

![Adicionar novos campos de formulário de veículo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Landmarks: corrigir o que o Discovery perdeu

Após **Mostrar pontos de referência**, percorra a lista e corrija os valores. Por vezes, os motores perdem dígitos pequenos (por exemplo, um relógio **60** no canto inferior direito do cluster). Utilize **Editar OCR** ​​para os adicionar ou corrigir para que a identidade do veículo permaneça fiável.

![Caixa de diálogo Pontos de referência — rever e editar OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>A escrever sem uma foto perfeita

Pode ainda utilizar a aplicação selecionando um veículo e **digitando** conta-quilómetros, volume e custo em Preencher — o OCR é opcional para todos os campos. A importação da galeria funciona para a fotografia de referência do painel quando prefere não fotografar na aplicação.

**Sugestão:** Após a sincronização da folha de cálculo, as definições dos veículos (culturas, pontos de referência) ficam na base de dados local — não necessita de reabrir Gerir veículos para preenchimento para as utilizar.

---

## <a id="backups-and-multi-device-sync"></a>Backups e sincronização de vários dispositivos

A aplicação foi desenvolvida para que **vários telefones ou tablets possam partilhar os mesmos dados da frota** e para que possa manter uma **cópia dos seus dados e fotografias fora do dispositivo**. Isto é feito com destinos que **você** configura nas **suas** contas ou nos **seus** servidores auto-hospedados — e não numa “nuvem de despesas de veículos” gerida pela empresa que outras pessoas possam ver.

### <a id="what-runs-where"></a>O que funciona onde

| Tipo | O que armazena | Uso típico |
|------|----------------|------------|
| **Folha de cálculo/sincronização tabular** | Veículos, abastecimento de combustível, despesas (linhas e guias) | Fusão de vários dispositivos + backup estruturado |
| **Backup de fotografias** | Imagens binárias (traço/bomba/recibo/fotos de referência) | Backup de fotografias + restauro de ficheiros em falta |

Pode configurar **múltiplos destinos** de cada tipo (soft cap por tipo). Os trabalhadores manuais **Sincronizem agora** e **em segundo plano** executam os ativados.

### <a id="offline-first"></a>Off-line primeiro

- **Não é necessária rede** para adicionar preenchimento, despesa ou recibo. Tudo é guardado **localmente primeiro**.
- Quando a rede está disponível, a sincronização e o backup das fotografias são executados como **tarefas em segundo plano** (de acordo com um horário definido por si e ao tocar em **Sincronizar agora**). As falhas aparecem como texto vermelho nas linhas Definições e um **!** na barra de título da aplicação.

### <a id="your-accounts-only"></a>Apenas as suas contas

O login e os tokens permanecem no dispositivo para os fornecedores que escolher (Google, Microsoft, chaves S3, URLs auto-hospedados e assim por diante). Os destinos estão sob **controlo total do utilizador** — a sua conta Google, o seu OneDrive, o seu bucket MinIO, o seu host EtherCalc, etc. Nada é partilhado com outros utilizadores do Vehicle Expenses através de um back-end partilhado.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Alvos suportados — dados (folha de cálculo/tabela)

Configurado em **Menu → Sincronização → Sincronização de folha de cálculo** (também acessível nas linhas de resumo de definições). Opções de selecionador de primeira classe:

| Alvo | Notas |
|-------|--------|
| **Folhas de cálculo Google** | Incumprimento comum; guias para veículos, despesas e combustível por veículo |
| **Excelente** | Pasta de trabalho da Microsoft através de ligação estilo Graph/OneDrive |
| **EtherCalc** | Salas de folhas de cálculo colaborativas auto-hospedadas |
| **Outros →** backends implementados | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Adiado/ainda sem headless (listado em Outros, mas não totalmente implementado): OnlyOffice, Collabora. Consulte também [índice de auto-host](reference/self-host/INDEX.md).

O CSV **exportar/importar** (ZIP do mesmo layout de separador) está disponível nas Definições como cópia de segurança portátil, independente da sincronização ao vivo.

### <a id="supported-targets--photos-image-backup"></a>Alvos suportados — fotos (backup de imagem)

Configurado em **Menu → Sincronização → Cópia de segurança de fotografias** (também nas linhas de resumo das definições):

| Alvo | Notas |
|-------|--------|
| **Google Drive** | Pasta que escolher (navegar ou colar URL) |
| **OneDrive** | Conta Microsoft + prefixo do caminho |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO e outros endpoints compatíveis com S3 |
| **Outros** | armazenamento suportado por rclone (por exemplo, WebDAV, SFTP e outros controlos remotos selecionados disponíveis no seletor na aplicação) |

Configure cheatsheets para fotos auto-hospedadas e alvos tabulares: [índice de auto-host](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Comportamento de vários dispositivos (curto)

- As linhas são fundidas por **Sync ID** com **last-write-wins** nos carimbos de data e hora **Atualizados**.
- As exclusões são suaves; uma edição mais recente noutro dispositivo pode restaurar uma linha.
- Inserir o **mesmo preenchimento duas vezes** em dois dispositivos cria **duas linhas** — apague o extra quando reparar.
- Mais detalhes: [Notas de comportamento de sincronização](#sync-behavior-notes) e [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Exemplo: adicionar Folhas de Cálculo Google (dados)

1. **Menu → Sincronização → Sincronização de folha de cálculo** (ou Definições → Sincronização de folha de cálculo).

![Lista de sincronização de folhas de cálculo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Toque em **Adicionar destino da folha de cálculo**.

![Seletor de fornecedor](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3.º Escolha **Fichas Google**.

![Formulário do Google Sheets](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Faça login com o Google** uma vez — este login também lhe permite navegar. Nome de visualização → cole um **URL da folha de cálculo** (ainda válido) ou **🔍** navegue. A navegação abre em **Todas e partilhadas** para que possa escolher uma folha de cálculo existente da sua propriedade ou que seja partilhada consigo. De seguida, programe opções → ativar → guardar.
5. **Sincronize agora** uma vez para criar/atualizar separadores: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Exemplo: adicionar Google Drive (fotos)

1. **Menu → Sincronização → Backup de fotografias** (ou Definições → Backup de fotografias).

![Lista de cópias de segurança de fotografias](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Toque em **Adicionar destino da fotografia**.

![Seletor de fornecedor de fotografia](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3.º Escolha **Google Drive**.

![Formulário Google Drive](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Inicie sessão com o Google (Drive)** → URL da pasta opcional/navegar → ativar → guardar → **Sincronizar agora**.

Manual **Sincronizar agora** para fotos é um passe completo; o backup em segundo plano processa normalmente uploads **apenas pendentes** de acordo com uma programação.

### <a id="sync-behavior-notes"></a>Sincronizar notas de comportamento

- Após a atualização da aplicação, poderá ver brevemente **“Atualizar base de dados após a atualização…”** (preenchimento de ID de sincronização local).
- Se uma sincronização for interrompida, a próxima sincronização **bem-sucedida** irá fundir novamente e reparar os separadores remotos.
- Falhas: resumo vermelho nos cartões de sincronização + **!** na barra de aplicações.

---

## <a id="fill-up-fuel"></a>Encher (combustível)

Esta é a **ecrã inicial** quando abre a aplicação.

### <a id="vehicle-selection-usually-automatic"></a>Seleção do veículo (geralmente automática)

Não **tem** de escolher o veículo primeiro. Quando os veículos têm **pontos de referência** configurados em Gerir veículos, o Fill **deteta automaticamente qual o veículo** na imagem do painel de instrumentos depois de capturar o conta-quilómetros. Pode ainda abrir o menu suspenso **Veículo** para substituir, se necessário.

### <a id="aim-at-the-odometer"></a>Aponte no odómetro

Permaneça no modo odómetro e enquadre o cluster. Instrução: * Aponte para o conta-quilómetros. Toque no obturador para captar. *

![Preencher — câmara em direto no odómetro (exemplo de imagem do painel na visualização)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Após o obturador do conta-quilómetros

OCR preenche **Odo** e tenta corresponder o veículo com os pontos de referência (revisar ambos, se necessário). O botão principal passa a ser **Tentar novamente** para filmar novamente. A instrução resume a leitura.

![Preencher - resultado do OCR do conta-quilómetros](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Modo bomba (custo e volume)

1. Toque em **↕** para passar para o modo de bomba: *Observe o visor da bomba (custo/volume). Toque no obturador. *
2. Capte os totais da bomba. Preenchimento dos campos custo e volume; utilize **↔** se forem trocados.
3. Toque em moeda ou **G/L** se necessário e em **Guardar** (disco). Os campos vazios fazem um **preenchimento parcial** (ainda permitido).

![Preencher - visualização da bomba/total (exemplo de imagem da bomba na visualização)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Permanece em Preencher para a paragem seguinte (campos limpos após guardar). Trabalhar totalmente **off-line**; a sincronização é posteriormente executada em segundo plano quando configurada.

### <a id="manual-entry-no-camera--bad-ocr"></a>Entrada manual (sem câmara/OCR deficiente)

1. Toque em **Odo**, **custo** ou **volume** e introduza os valores (retrato utiliza o teclado do sistema; paisagem utiliza um teclado no ecrã).
2. Escolha ou confirme o **Veículo** se a deteção automática não foi efetuada.
3.º Guarde como acima.

### <a id="modes-and-borders"></a>Modos e bordas

- **Borda verde** em redor do veículo+odo → captura/edição do conta-quilómetros.
- **Borda verde** em torno de custo+volume → modo de bomba.
- **Guardar** permanece desativado até que um veículo seja selecionado e pelo menos um de odo/custo/volume tenha dados e o OCR ainda não esteja em execução.

Dica no ecrã (abaixo da linha de instruções): *Obturador = capturar · Disco = guardar · ↕ = modo odo/bomba · ↔ = custo/volume de troca.*

---

## <a id="expenses"></a>Despesas

### <a id="new-expense"></a>Nova despesa

Menu → **Nova despesa**.

![Novo formulário de despesas e controlos da câmara (exemplo de imagem de traço na faixa da câmara quando vazia)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Guardar** (disco), **obturador** (foto de recibo) ou **galeria** (escolher imagem).
2. Preencha **Data**, **Veículo**, **Fornecedor**, **Descrição**, **Valor** (símbolo de moeda tocável), **Categoria**, opcional **Odómetro**.
3. Recibos de várias páginas: capture páginas adicionais se a UI oferecer paginação (a página 0 é o recibo principal).
4. **Guardar** na loja (local primeiro; o backup das fotografias e a sincronização da folha de cálculo acontecem em segundo plano quando configurados).

### <a id="expense-list"></a>Lista de despesas

Menu → **Relatórios** → **Lista de despesas** — navegue pelas despesas anteriores não relacionadas com combustível; abra um item para editar.

![Lista de despesas](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Editar despesa

Abra uma linha da lista. Fornecedor correto, quantidade, categoria, veículo e descrição. Se o recibo estiver apenas no backup de fotografias (sem ficheiro local legível), utilize **Procurar imagem do ficheiro** quando mostrado (funciona em destinos de fotografias configurados).

![Editar despesa](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Iniciar viagem

Menu → **Iniciar viagem** (após preencher a gaveta). Capture ou insira o conta-quilómetros, escolha o tipo de viagem e guarde com o ícone **disco**. **Parar** é um atalho para Pessoal agora na localização GPS mantida. Utilize **ⓘ** para lembretes de controlo.

![Iniciar viagem](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

As partidas de viagem são armazenadas como linhas de combustível com um **Tipo de viagem** (não abastecimentos normais). Aparecem em **Relatórios → Milhas de viagem**, e não em Histórico de combustível.

---

## <a id="reports"></a>Relatórios

Menu → **Relatórios** abre o hub do produto (resumo de todos os tempos + fichas de catálogo). Esta é a única superfície de relatórios de produtos – não existe nenhum item de gaveta “Relatórios e Gráficos” separado.

![Hub de relatórios](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Abra um cartão para o modo veículo (**Todos / Cada / Único**), filtros de período, gráficos e partilha (**TEXTO / CSV / PDF**). Barra superior nos filhos do relatório: **☰ + ←** (e **ⓘ** quando registado).

### <a id="time-based-reports"></a>Relatórios baseados no tempo

O cartão gráfico principal. Métricas opcionais (mpg, volume/distância como G/mi, preço unitário como $/G, custo/distância, $ mensais, milhas de viagem, % de viagem por tipo) com compartimentos **Smooth** e **escalas Y independentes** (economia à esquerda; dinheiro e famílias de viagem à direita).

![Relatórios baseados no tempo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Relatórios baseados no tempo - métricas roladas](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Detalhes da matemática económica: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Preencher histórico vs histórico de combustível

- **Relatórios → Histórico de preenchimento** — preenchimentos cronológicos para os filtros do relatório (**apenas preenchimentos**; nenhuma viagem começa).

![Preencher relatório histórico](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Histórico de combustível** (se presente na navegação da sua construção) — inventário de preenchimento por veículo, também apenas preenchimentos; toque numa linha para editar.

### <a id="trip-miles"></a>Milhas de viagem

**Relatórios → Milhas de viagem** — milhas por tipo, gráficos e uma **lista cronológica de início/segmento de viagem**. Toque num início real para abrir **Editar preenchimento** dessa linha.

![Milhas da viagem](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Editar preenchimento

Em Histórico de abastecimento, Histórico de combustível ou Milhas de viagem, abra um abastecimento. Disposição: veículo e conta-quilómetros, **moeda antes do custo**, volume, notas. O tipo de viagem aparece apenas quando a linha é um início de viagem. A localização tem um resumo mais **Detalhes da localização**. Foto local em falta com identidade na nuvem: **Pesquisar imagem do ficheiro**.

![Editar preenchimento — Pesquisar no ficheiro quando falta uma foto local](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Outros cartões de catálogo incluem as despesas por categoria, o resumo do veículo e a lista de despesas.

Money utiliza a moeda de cada linha quando definida. Os totais em moedas mistas mostram **subtotais por moeda** (sem conversão FX silenciosa).

---

## <a id="syncing"></a>Sincronizando

Menu → **Sincronização** é o centro para destinos de folhas de cálculo e fotografias (não apenas enterrado nas Definições).

![Hub de sincronização](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Cartões para **Sincronização de folhas de cálculo** e **Backup de fotografias** com estado curto, **Sincronização** para este tipo e **›** na lista de destinos.
- Abra um destino para **Testar ligação** e **Sincronizar agora (este destino)** / tudo configurado.
- Falha **Detalhes** e o vermelho **!** na barra de título aparecem aqui.
- Configuração passo a passo do Google Sheets e do Drive: [Backups e sincronização de vários dispositivos](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Definições (preferências locais)

Menu → **Definições**.

![Definições](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Mais definições (rolado)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Para destinos, prefira **Menu → Sincronização**. As definições podem ainda mostrar linhas de resumo que abrem as mesmas listas.

### <a id="local-preferences-common"></a>Preferências locais (comuns)

- **Guardar fotografias dos recibos de combustível** / **Guardar fotografias das despesas localmente** — mantenha as imagens no dispositivo (pode solicitar autorização para fotografias).
- **Reproduzir som do obturador**
- **Moeda** / **Unidade de volume** — padrões da aplicação (sistema ou explícito). A alteração da unidade de volume com os dados de combustível existentes pode oferecer uma caixa de diálogo de conversão.
- **Modo escuro**
- **Dicas de configuração** — reabra os tutoriais de sincronização/veículo de primeira execução.
- **Preenchimento de depuração** / **Mostrar ecrãs de experiências (dev)** — avançado; deixe para uso diário. As telas de experiências não estão documentadas aqui.

O CSV **exportar/importar** (código postal dos separadores Veículos / Despesas / Combustível) está disponível em Definições quando oferecido pela versão atual.

---

## <a id="help--about"></a>Ajuda e Sobre

![Ajuda](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Sobre](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Ajuda** — arranque rápido no dispositivo, tutoriais de configuração, ligação para este manual, índice de configuração do próprio host.
- **Sobre** — versão, licenças, GitHub, este manual (empacotado offline + HTML online quando publicado).

---

## <a id="related-docs"></a>Documentos relacionados

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — breve guia diário
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — configuração de foto/tabular auto-hospedada
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — fusão, recuperação, duplicados
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — detalhe das métricas económicas
