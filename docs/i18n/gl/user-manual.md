# <a id="vehicle-expenses-automated--user-manual"></a>Gastos do vehículo automatizados — Manual de usuario

> **Editar a fonte (Markdown).** Os navegadores e o lector na aplicación abren o **HTML renderizado**:
> - Web: [`docs/user-manual/index.html`](index.html) (rexenerar con `./scripts/render-user-manual.sh`)
> - Aplicación: Axuda / Acerca de → manual completo (HTML incluído + capturas de pantalla)
>
> Non apuntar aos usuarios finais a URL `.md` brutos: os navegadores só mostran texto sen formato.

Primeiro seguimento da cámara para cargas de combustible e gastos de vehículos, con sincronización e copia de seguridade de varios dispositivos opcionales nas **as túas** contas na nube.

Este é o **manual completo** (capturas de pantalla + cada paso). No teléfono, **Menú → Axuda** é unha guía de iniciación máis breve.

**Non se trata aquí:** Importar imaxes antigas, Experimento de aliñamento e Experimento de bomba (ferramentas para desenvolvedores/avanzadas).

---

## <a id="table-of-contents"></a>Índice

1. [O que necesitas](#what-you-need)
2. [Iconas dunha ollada](#icons-at-a-glance)
3. [Abrir o menú](#open-the-menu)
4. [Configuración por primeira vez: xestionar vehículos](#first-time-setup-manage-vehicles)
5. [Copias de seguranza e sincronización de varios dispositivos](#backups-and-multi-device-sync)
6. [Encher (combustible)](#fill-up-fuel)
7. [Iniciar viaxe](#start-trip)
8. [Gastos](#expenses)
9. [Informes](#reports)
10. [Configuración (preferencias locais)](#settings-local-preferences)
11. [Sincronizando](#syncing)
12. [Axuda e información](#help--about)
13. [Documentos relacionados](#related-docs)

---

## <a id="what-you-need"></a>O que necesitas

- Teléfono ou tableta Android.
- Para obter o mellor OCR: unha visión clara do **odómetro do panel de control** e dos **totais da bomba** (ou escriba os números a man).
- Opcional: contas **que controlas** para datos da folla de cálculo e/ou copia de seguranza de fotos (consulta [Copias de seguranza e sincronización multidispositivo](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Iconas dunha ollada

Estes aparecen nas pantallas principais. Coñecelos aforra moita caza.

| Onde | Icona / control | Que fai |
|--------|-----------------|--------------|
| Barra superior | **☰ Menú** (hamburguesa) | Abre o caixón de navegación |
| Barra superior | **ⓘ** (axuda da páxina) | Axuda breve para a páxina **actual** (a carón do menú cando estea dispoñible) |
| Barra superior | **`?N`** (amarelo) | Preguntas pendentes de revisión de importación: abre Revisión de importación |
| Barra superior | **!** (vermello) | Produciuse un erro recentemente nunha folla de cálculo ou no destino dunha foto. Abre **Sincronización** para corrixir |
| Barra superior | **☰ + ←** | Informe os nenos e a lista de gastos mostran **menú e volta** xuntos; O centro de informes é só menú |
| Configuración / edición de combustible | **←** | Atrás (a configuración da folla de cálculo/foto e a edición de combustible mantéñense centradas) |
| Encher | **Círculo branco** (obturador) | Captura odómetro ou pantalla da bomba para OCR |
| Encher | **Disco/Gardar** | Gardar o recheo (necesita un vehículo e polo menos un de odo/volume/custo) |
| Encher | **↕ frechas** (interruptor de modo) | Alterna **modo odómetro** contra **modo bomba (custo/volume)**. O bordo verde destaca o grupo de campo activo |
| Encher | **↔ frechas** (entre custo e volume) | Intercambiar custo e volume se o OCR os puxo nos campos incorrectos |
| Encher | **Ampliar 1x / …** | Relacións de zoom da cámara cando a lente as admite |
| Encher (despois da captura) | **Actualizar** no botón principal | Descartar a vista previa e volver á cámara en directo |
| Encher (durante o procesamento) | **X** no botón principal | Cancelar captura/OCR en curso |
| Gastos | **Gardar** | Aforra o gasto |
| Gastos | **Círculo de obturación** | Fai unha foto do recibo |
| Gastos | **Galería** | Escolle unha imaxe de recibo da biblioteca |
| Gastos | **Retomar** | Borrar a foto do recibo actual e disparar de novo |
| Gastos / Xestionar Vehículos | **+ / −** FABs | Ampliar a vista previa da foto |
| Diálogo de puntos de referencia | **Editar OCR** ​​| Corrixe ou engade un texto de referencia que perderon os motores |
| Folla de cálculo / Formularios fotográficos | **🔍 Busca** | Busca en Google Drive unha folla ou cartafol (despois de iniciar sesión) |

Os símbolos de moeda nos campos de custo e **G/L** nos campos de volume pódense tocar: abre un pequeno menú para cambiar a moeda ou os litros e os litros para esa entrada.

---

## <a id="open-the-menu"></a>Abre o menú

1. Toca **☰** na parte superior esquerda.
2. Escolle unha páxina.

![Caixón de navegación](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Caixón principal:** Encher · Iniciar viaxe · Xestionar vehículos · Novo gasto · **Informes** · Configuración · Sincronización · Axuda · Acerca de.

**Caixón de experimentos** (Configuración → Mostrar pantallas de experimento): Experimento de aliñamento · Experimento de bomba · **Importar imaxes antigas**.

**A través do centro de informes (non do caixón principal):** Lista de gastos · Historial de recheo.

---

## <a id="first-time-setup-manage-vehicles"></a>Configuración por primeira vez: xestionar vehículos

OCR e a **coincidencia automática de vehículos** funcionan mellor despois de rexistrar cada vehículo cunha **foto do panel de referencia**, executar **Discovery**, establecer o recorte do odómetro a partir dunha semente de pa e gardar os puntos de referencia para ese guión.

### <a id="open-manage-vehicles"></a>Abre Xestionar vehículos

Menú → **Xestionar vehículos**. Escolla un vehículo (ou **Engadir un novo vehículo**).

![Xestionar vehículos con trazo de referencia, cultivos e descubrimento](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Engadir ou editar un vehículo

1. Abre o menú despregable **Vehículo** → escolle un vehículo ou **Engadir novo vehículo**.
2. Captura ou escolle unha **foto do guión de referencia** clara (conxunto de instrumentos completo, ben iluminado, teléfono aproximadamente cadrado). Usa **Foto** ou **Galería**.
3. Toca **Executar descubrimento**: a detección de paletas coloca **sementes** amarelas na foto (os puntos de referencia usan o mesmo `"ML Kit"` JSON que antes).
4. Toca **Selecciona Odo**, toca a semente dos díxitos, axusta as asas e despois **Gardar**.
5. **Editar Odo** axusta unha caixa que xa está alí. **Borrar Odo** elimínao e o botón indica **Seleccionar Odo**.
6. Revisa con **Mostrar puntos de referencia**. Usa **Editar OCR** ​​para corrixir lecturas erróneas ou **engadir** texto que se perdeu.
7. Encha **Nome do vehículo** (obrigatorio), máis marca/modelo/ano/placa como queiras.
8. Toca **Crear vehículo** ou **Gardar cambios** (require nome e foto de referencia para un vehículo novo).

![Engadir novos campos de formulario de vehículo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Fitos: corrixir o que Discovery perdeu

Despois de **Mostrar puntos de referencia**, desprázate pola lista e corrixe os valores. Ás veces, os motores perden pequenos díxitos (por exemplo, un reloxo **60** na parte inferior dereita do clúster). Usa **Editar OCR** ​​para engadilos ou corrixilos para que a identidade do vehículo siga sendo fiable.

![Diálogo de puntos de referencia — revisa e edita OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Escribindo sen unha foto perfecta

Aínda podes usar a aplicación seleccionando un vehículo e **escribindo** odómetro, volume e custo en Fill - OCR é opcional para todos os campos. A importación da galería funciona para a foto do guión de referencia cando prefires non disparar na aplicación.

**Consello:** Despois da sincronización da folla de cálculo, as definicións de vehículos (cultivos, puntos de referencia) están na base de datos local; non é necesario volver abrir Xestionar vehículos para encher para utilizalas.

---

## <a id="backups-and-multi-device-sync"></a>Copias de seguranza e sincronización de varios dispositivos

A aplicación está creada para que **varios teléfonos ou tabletas poidan compartir os mesmos datos da flota** e así podes manter unha **copia dos teus datos e fotos fóra do dispositivo**. Iso faise cos destinos que **que** configuras en **as túas** contas ou **os teus** servidores autoaloxados, non unha "nube de gastos de vehículos" dirixida pola empresa que outras persoas poden ver.

### <a id="what-runs-where"></a>O que corre onde

| amable | Que almacena | Uso típico |
|-------|-----------------|-------------|
| **Folla de cálculo/sincronización tabular** | Vehículos, recheos de combustible, gastos (filas e pestanas) | Combinación de varios dispositivos + copia de seguridade estruturada |
| **Copia de seguridade de fotos** | Imaxes binarias (trazo/bomba/recibo/fotos de referencia) | Copia de seguridade de fotos + restaurar os ficheiros que faltan |

Podes configurar **múltiples destinos** de cada tipo (tapa suave por tipo). Os traballadores **Sincronizar agora** manual e **en segundo plano** executan os activados.

### <a id="offline-first"></a>Primeiro sen conexión

- **Non se precisa ningunha rede** para engadir un recheo, un gasto ou un recibo. Todo gárdase **primeiro localmente**.
- Cando a rede estea dispoñible, a sincronización e a copia de seguranza das fotos execútanse como **tarefas en segundo plano** (segundo unha programación que estableza e cando toque **Sincronizar agora**). Os erros móstranse como texto vermello debaixo das filas de Configuración e un **!** na barra de título da aplicación.

### <a id="your-accounts-only"></a>Só as túas contas

O inicio de sesión e os tokens permanecen no dispositivo para os provedores que escollas (claves de Google, Microsoft, S3, URL autoaloxados, etc.). Os destinos están baixo o **control total do usuario**: a túa conta de Google, o teu OneDrive, o teu depósito MinIO, o teu servidor EtherCalc, etc. Non se comparte nada con outros usuarios de Gastos de vehículos a través dun backend compartido.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Obxectivos admitidos: datos (folla de cálculo/tabular)

Configurado en **Menú → Sincronización → Sincronización da folla de cálculo** (tamén accesible desde as filas de resumo de Configuración). Opcións de selección de primeira clase:

| Obxectivo | Notas |
|---------|--------|
| **Follas de cálculo de Google** | Por defecto común; fichas para Vehículos, Gastos e combustible por vehículo |
| **Excel** | Libro de traballo de Microsoft mediante encadernación ao estilo Graph/OneDrive |
| **EtherCalc** | Salas de follas de cálculo colaborativas autoaloxadas |
| **Outros →** backends implementados | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Aplazado / aínda non sen cabeza (listado en Outros pero non implementado completamente): OnlyOffice, Collabora. Consulte tamén [índice de auto-host](reference/self-host/INDEX.md).

CSV **exportar/importar** (ZIP do mesmo deseño de pestanas) está dispoñible en Configuración como unha copia de seguridade portátil, independentemente da sincronización en directo.

### <a id="supported-targets--photos-image-backup"></a>Obxectivos admitidos: fotos (copia de seguridade da imaxe)

Configurado en **Menú → Sincronización → Copia de seguranza da foto** (tamén desde as filas de resumo da configuración):

| Obxectivo | Notas |
|---------|--------|
| **Google Drive** | Cartafol que escolla (buscar ou pegar URL) |
| **OneDrive** | Conta de Microsoft + prefixo de ruta |
| **S3** | AWS, Wasabi, Cloudflare R2, MinIO e outros extremos compatibles con S3 |
| **Outro** | Almacenamento respaldado por rclone (por exemplo, WebDAV, SFTP e outros mandos a distancia seleccionados dispoñibles no selector da aplicación) |

Configura fichas de trucos para fotos autoaloxadas e obxectivos tabulares: [índice de autoaloxamento](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Comportamento en varios dispositivos (curto)

- As filas fusionan mediante **ID de sincronización** con **last-write-wins** en marcas de tempo **Actualizadas**.
- As eliminacións son suaves; unha edición máis recente noutro dispositivo pode restaurar unha fila.
: introducir o **mesmo recheo dúas veces** en dous dispositivos crea **dúas filas**: elimina o extra cando o note.
- Máis detalles: [Notas de comportamento de sincronización](#sync-behavior-notes) e [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Exemplo: engadir Follas de cálculo de Google (datos)

1. **Menú → Sincronización → Sincronización da folla de cálculo** (ou Configuración → Sincronización da folla de cálculo).

![Lista de sincronización de follas de cálculo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Toca **Engadir destino da folla de cálculo**.

![Selector de provedores](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Escolla **Follas de Google**.

![Formulario de Follas de cálculo de Google](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Iniciar sesión con Google** unha vez; ese inicio de sesión tamén che permite navegar. Nome para mostrar → pega un **URL da folla** (aínda válido) ou **🔍** explora. Navegación ábrese en **Todo e compartido** para que poidas escoller unha folla de cálculo existente que teñas ou que compartas contigo. A continuación, programa opcións → activar → gardar.
5. **Sincronizar agora** unha vez para crear/actualizar pestanas: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Exemplo: engadir Google Drive (fotos)

1. **Menú → Sincronización → Copia de seguranza de fotos** (ou Configuración → Copia de seguranza de fotos).

![Lista de copia de seguridade de fotos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Toca **Engadir destino da foto**.

![Selector de provedores de fotos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Escolla **Google Drive**.

![Formulario de Google Drive](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Iniciar sesión con Google (Drive)** → URL do cartafol opcional/explorar → activar → gardar → **Sincronizar agora**.

Manual **Sincronizar agora** para fotos é un paso completo; A copia de seguridade en segundo plano normalmente procesa cargas **só pendentes** nunha programación.

### <a id="sync-behavior-notes"></a>Sincronizar notas de comportamento

- Despois da actualización da aplicación, podes ver brevemente **"Actualizando a base de datos despois da actualización..."** (recheo local do identificador de sincronización).
- Se unha sincronización se interrompe, a seguinte sincronización **exitosa** volve combinarse e repara as pestanas remotas.
- Fallos: resumo vermello nas tarxetas de sincronización + **!** na barra de aplicacións.

---

## <a id="fill-up-fuel"></a>Recheo (combustible)

Esta é a **pantalla de inicio** cando abres a aplicación.

### <a id="vehicle-selection-usually-automatic"></a>Selección do vehículo (normalmente automática)

**Non** necesitas escoller primeiro o vehículo. Cando os vehículos teñan **marcas** configurados en Xestionar vehículos, Enche **detecta automaticamente que vehículo** a partir da imaxe do guión despois de capturar o contakilómetros. Aínda podes abrir o menú despregable **Vehículo** para anular se é necesario.

### <a id="aim-at-the-odometer"></a>Apunta ao contakilómetros

Permanece no modo odómetro e enmarca o clúster. Instrución: *Apuntar ao contakilómetros. Toca o obturador para capturar.*

![Encher — cámara en directo no contakilómetros (exemplo de imaxe do guión na vista previa)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Despois do obturador do contakilómetros

OCR enche **Odo** e tenta facer coincidir o vehículo desde puntos de referencia (revisa ambos se é necesario). O botón principal pasa a ser **Reintento** para volver disparar. A instrución resume a lectura.

![Encher — resultado OCR do odómetro](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Modo de bomba (custo e volume)

1. Toca **↕** para cambiar ao modo de bomba: *Apunta á visualización da bomba (custo/volume). Toca obturador.*
2. Captura os totais da bomba. Os campos de custo e volume enchen; use **↔** se se intercambian.
3. Toca a moeda ou **G/L** se é necesario e despois **Gardar** (disco). Os campos baleiros fan un **enchemento parcial** (aínda permitido).

![Encher — vista previa da bomba / totais (imaxe da bomba de exemplo na vista previa)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Permanece en Encher para a seguinte parada (os campos borran despois de gardar). Traballar totalmente **sen conexión**; a sincronización execútase máis tarde en segundo plano cando se configura.

### <a id="manual-entry-no-camera--bad-ocr"></a>Entrada manual (sen cámara/OCR incorrecto)

1. Toca **Odo**, **custo** ou **volume** e escribe os valores (o retrato usa o teclado do sistema; a paisaxe usa un teclado en pantalla).
2. Escolle ou confirma o **Vehículo** se non se executou a detección automática.
3. Garda como anterior.

### <a id="modes-and-borders"></a>Modos e bordos

- **Borde verde** arredor do vehículo+odo → capturar/editar odómetro.
- **Borde verde** arredor de custo+volume → modo bomba.
- **Gardar** permanece desactivado ata que se selecciona un vehículo e polo menos un de odo/custo/volume teña datos e o OCR aínda non está en execución.

Consello en pantalla (debaixo da liña de instrucións): *Obturador = captura · Disco = gardar · ↕ = modo odo/bomba · ↔ = custo/volume de intercambio.*

---

## <a id="expenses"></a>Gastos

### <a id="new-expense"></a>Novo gasto

Menú → **Novo gasto**.

![Novo formulario de gastos e controis da cámara (exemplo de imaxe de guión na banda da cámara cando está baleira)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Gardar** (disco), **obturador** (foto do recibo) ou **galería** (escoller imaxe).
2. Encha **Data**, **Vehículo**, **Provedor**, **Descrición**, **Cantidade** (símbolo de moeda que se pode tocar), **Categoría**, **Odómetro** opcional.
3. Recibos de varias páxinas: captura páxinas adicionais se a IU ofrece paginación (a páxina 0 é o recibo principal).
4. **Gardar** para almacenar (primeiro local; a copia de seguridade de fotos e a sincronización da folla de cálculo ocorren en segundo plano cando se configuran).

### <a id="expense-list"></a>Lista de gastos

Menú → **Informes** → **Lista de gastos**: explora os gastos anteriores que non son de combustible; abra un elemento para editar.

![Lista de gastos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Editar gasto

Abre unha fila da lista. Vendedor, cantidade, categoría, vehículo e descrición correctos. Se o recibo só está na copia de seguranza da foto (sen ficheiro local lexible), usa **Obter imaxe do arquivo** cando se mostra (funciona nos destinos de fotos configurados).

![Editar gasto](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Comezar a viaxe

Menú → **Iniciar viaxe** (despois de Encher o caixón). Captura ou introduce o odómetro, escolle o tipo de viaxe e gárdao coa icona **disco**. **Parar** é un atallo para Personal agora na localización GPS mantida. Usa **ⓘ** para os recordatorios de control.

![Comezar viaxe](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Os inicios das viaxes gárdanse como filas de combustible cun **Tipo de viaxe** (non cheos normais). Aparecen en **Informes → Millas de viaxe**, non en Historial de combustible.

---

## <a id="reports"></a>Informes

Menú → **Informes** abre o centro de produtos (resumo de todos os tempos + tarxetas de catálogo). Esta é a única superficie de informes de produtos: non hai un elemento separado do caixón "Informes e gráficos".

![Centro de informes](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Abre unha tarxeta para o modo de vehículo (**Todo / Cada / Único**), filtros de período, gráficos e compartir (**TEXT / CSV / PDF**). Barra superior dos nenos informados: **☰ + ←** (e **ⓘ** cando esteas rexistrado).

### <a id="time-based-reports"></a>Informes baseados no tempo

A tarxeta do gráfico principal. Métricas opcionais (mpg, volume/distancia como G/mi, prezo unitario como $/G, custo/distancia, $ mensuais, millas de viaxe, % de viaxe por tipo) con contenedores **Smooth** e **escalas Y independentes** (economía á esquerda; cartos e familias de viaxe á dereita).

![Informes baseados no tempo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Informes baseados no tempo — métricas desprazadas](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Detalles de matemáticas económicas: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Historial de enchemento vs Historial de combustible

- **Informes → Historial de recheos**: recheos cronolóxicos para os filtros de informes (**só enchedos**; non se inicia ningunha viaxe).

![Encher informe de historial](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Historial de combustible** (se está presente na navegación da túa construción): inventario de recheo por vehículo, tamén se enche só; toca unha fila para editar.

### <a id="trip-miles"></a>Viaxe millas

**Informes → Millas de viaxe** — millas por tipo, gráficos e unha **lista de segmentos/inicio da viaxe**. Toca un inicio real para abrir **Editar recheo** para esa fila.

![Viaxe millas](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Editar recheo

Desde Historial de recheo, Historial de combustible ou Millas de viaxe, abra un recheo. Disposición: vehículo e contakilómetros, **moeda antes do custo**, volume, notas. O tipo de viaxe só aparece cando a fila é un inicio de viaxe. A localización ten un resumo máis **Detalles da localización**. Falta unha foto local coa identidade da nube: **Obtén a imaxe do arquivo**.

![Editar recheo — Obter do arquivo cando falta unha foto local](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Outras tarxetas do catálogo inclúen gastos por categoría, resumo do vehículo e lista de gastos.

O diñeiro usa a moeda de cada fila cando se define. Os totais de moeda mixta mostran **subtotais por moeda** (sen conversión silenciosa de divisas).

---

## <a id="syncing"></a>Sincronizando

Menú → **Sincronización** é o centro de destinos de follas de cálculo e fotos (non só se atopa en Configuración).

![Centro de sincronización](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Tarxetas para **Sincronización da folla de cálculo** e **Copia de seguranza de fotos** con estado breve, **Sincronizar** para ese tipo e **›** na lista de destinos.
- Abre un destino para **Probar conexión** e **Sincronizar agora (este destino)** / todo configurado.
- Fallo **Detalles** e o vermello **!** na barra de título caen aquí.
- Configuración de Google Sheets e Drive paso a paso: [Copias de seguranza e sincronización de varios dispositivos](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Configuración (preferencias locais)

Menú → **Configuración**.

![Configuración](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Máis axustes (desprazado)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Para os destinos, prefire **Menú → Sincronización**. A configuración aínda pode mostrar filas de resumo que abren as mesmas listas.

### <a id="local-preferences-common"></a>Preferencias locais (común)

- **Gardar fotos do recibo de combustible** / **Gardar fotos de gastos localmente**: garda imaxes no dispositivo (pode solicitar permiso para Fotos).
- **Reproducir o son do obturador**
- **Moeda** / **Unidade de volume** — valores predeterminados da aplicación (sistema ou explícito). Cambiar a unidade de volume cos datos de combustible existentes pode ofrecer un diálogo de conversión.
- **Modo escuro**
- **Consellos de configuración**: volve abrir os tutoriais de sincronización e vehículos de primeira execución.
- **Recheo de depuración** / **Mostrar pantallas de experimentos (dev)** — avanzado; deixar para o uso diario. As pantallas de experimentos non están documentadas aquí.

CSV **exportación/importación** (ZIP de pestanas de vehículos/gastos/combustible) está dispoñible en Configuración cando ofreza a versión actual.

---

## <a id="help--about"></a>Axuda e información

![Axuda](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Acerca de](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Axuda** — inicio rápido no dispositivo, tutoriais de configuración, ligazón a este manual, índice de configuración de auto-host.
- **Acerca de** — versión, licenzas, GitHub, este manual (incluído sen conexión + HTML en liña cando se publique).

---

## <a id="related-docs"></a>Documentos relacionados

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — breve guía diaria
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — configuración de foto/tabular autoaloxada
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — fusión, recuperación, duplicados
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — detalle de métricas económicas
