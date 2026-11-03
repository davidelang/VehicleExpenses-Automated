# <a id="vehicle-expenses-automated--user-manual"></a>Gastos de Vehículo Automatizados — Manual de Usuario

> **Editar fuente (Markdown).** Los navegadores y el lector de la aplicación abren el **HTML renderizado**:
> - Web: [`docs/user-manual/index.html`](index.html) (regenerar con `./scripts/render-user-manual.sh`)
> - Aplicación: Ayuda / Acerca de → manual completo (HTML incluido + capturas de pantalla)
>
> No dirija a los usuarios finales a URL `.md` sin formato: los navegadores solo muestran texto sin formato.

Seguimiento con cámara para repostajes de combustible y gastos del vehículo, con sincronización multidispositivo opcional y copia de seguridad en **tus** cuentas en la nube.

Este es el **manual completo** (capturas de pantalla + cada paso). En el teléfono, **Menú → Ayuda** es una guía de introducción más breve.

**No se trata aquí:** Importar imágenes antiguas, experimento de alineación y experimento de bombeo (desarrollador/herramientas avanzadas).

---

## <a id="table-of-contents"></a>Tabla de contenido

1. [Lo que necesitas](#what-you-need)
2. [Iconos de un vistazo](#icons-at-a-glance)
3. [Abrir el menú](#open-the-menu)
4. [Configuración por primera vez: Administrar vehículos](#first-time-setup-manage-vehicles)
5. [Copias de seguridad y sincronización multidispositivo](#backups-and-multi-device-sync)
6. [Llene (combustible)](#fill-up-fuel)
7. [Iniciar viaje](#start-trip)
8. [Gastos](#expenses)
9. [Informes](#reports)
10. [Configuración (preferencias locales)](#settings-local-preferences)
11. [Sincronización](#syncing)
12. [Ayuda y acerca de](#help--about)
13. [Documentos relacionados](#related-docs)

---

## <a id="what-you-need"></a>Lo que necesitas

- Teléfono o tableta Android.
- Para obtener el mejor OCR: una vista clara del **odómetro del tablero** y los **totales de la bomba** (o escriba los números a mano).
- Opcional: cuentas **que usted controla** para datos de hojas de cálculo y/o copias de seguridad de fotografías (consulte [Copias de seguridad y sincronización multidispositivo](#backups-and-multi-device-sync)).

---

## <a id="icons-at-a-glance"></a>Iconos de un vistazo

Aparecen en las pantallas principales. Conocerlos ahorra mucha caza.

| Dónde | Icono/control | Qué hace |
|-------|----------------|--------------|
| Barra superior | **☰ Menú** (hamburguesa) | Abre el cajón de navegación |
| Barra superior | **ⓘ** (ayuda de la página) | Ayuda breve para la página **actual** (al lado del menú cuando esté disponible) |
| Barra superior | **`?N`** (amarillo) | Preguntas pendientes de revisión de importación: abre Revisión de importación |
| Barra superior | **!** (rojo) | Un destino de hoja de cálculo o foto falló recientemente: abra **Sincronización** para solucionarlo |
| Barra superior | **☰ + ←** | Informe de niños y lista de gastos muestran **menú y atrás** juntos; El centro de informes es solo menú |
| Configuración/edición de combustible | **←** | Atrás (la configuración de la hoja de cálculo/edición de fotos y combustible permanece enfocada) |
| Rellenar | **Círculo blanco** (obturador) | Capture la pantalla del odómetro o de la bomba para OCR |
| Rellenar | **Disco/Guardar** | Guardar el repostaje (necesita un vehículo y al menos uno de odo/volumen/coste) |
| Rellenar | **↕ flechas** (cambio de modo) | Alternar **modo de odómetro** frente a **modo de bomba (costo/volumen)**. El borde verde resalta el grupo de campos activo |
| Rellenar | **↔ flechas** (entre costo y volumen) | Intercambiar costo y volumen si OCR los coloca en los campos incorrectos |
| Rellenar | **Zoom 1x / …** | Relaciones de zoom de la cámara cuando la lente las admite |
| Rellenar (después de la captura) | **Actualizar** en el botón principal | Descartar vista previa y volver a cámara en vivo |
| Llenar (durante el procesamiento) | **X** en el botón principal | Cancelar captura en curso/OCR ​​|
| Gasto | **Guardar** | Ahorre el gasto |
| Gasto | **Círculo de obturación** | Tome una foto del recibo |
| Gasto | **Galería** | Elija una imagen de recibo de la biblioteca |
| Gasto | **Retomar** | Borre la foto del recibo actual y vuelva a disparar |
| Gastar / Gestionar Vehículos | **+ / −** FAB | Ampliar la vista previa de la foto |
| Diálogo de puntos de referencia | **Editar OCR** ​​| Corrija o agregue texto emblemático que los motores omitieron |
| Formularios de hoja de cálculo/fotografía | **🔍 Buscar** | Busque en Google Drive una hoja o carpeta (después de iniciar sesión) |

Los símbolos de moneda en los campos de costo y **G/L** en los campos de volumen se pueden tocar: abra un pequeño menú para cambiar la moneda o galones versus litros para esa entrada.

---

## <a id="open-the-menu"></a>Abrir el menú

1. Toca **☰** en la parte superior izquierda.
2. Elige una página.

![Cajón de navegación](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/01-drawer.jpg)

**Cajón principal:** Llenar · Iniciar viaje · Administrar vehículos · Nuevo gasto · **Informes** · Configuración · Sincronización · Ayuda · Acerca de.

**Cajón de experimentos** (Configuración → Mostrar pantallas de experimentos): Experimento de alineación · Experimento de bomba · **Importar imágenes antiguas**.

**A través del centro de informes (no del cajón principal):** Lista de gastos · Historial de relleno.

---

## <a id="first-time-setup-manage-vehicles"></a>Configuración por primera vez: Administrar vehículos

El OCR y la **combinación automática de vehículos** funcionan mejor después de registrar cada vehículo con una **foto de tablero de referencia**, ejecutar **Descubrimiento**, configurar el odómetro a partir de una semilla de paleta y guardar puntos de referencia para ese tablero.

### <a id="open-manage-vehicles"></a>Abrir Administrar vehículos

Menú → **Administrar vehículos**. Elija un vehículo (o **Agregar vehículo nuevo**).

![Administrar vehículos con tablero de referencia, cultivos y descubrimiento](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r1-manage-vehicles-crops.jpg)

### <a id="add-or-edit-a-vehicle"></a>Agregar o editar un vehículo

1. Abra el menú desplegable **Vehículo** → elija un vehículo o **Agregar nuevo vehículo**.
2. Capture o elija una **foto de tablero de referencia** clara (grupo de instrumentos completo, bien iluminado, teléfono aproximadamente en posición cuadrada). Utilice **Tomar foto** o **Galería**.
3. Toca **Ejecutar Discovery**: la detección de paletas coloca **semillas** amarillas en la foto (los puntos de referencia usan el mismo `"ML Kit"` JSON que antes).
4. Toque **Seleccionar Odo**, toque la semilla en los dígitos, ajuste las manijas y luego **Guardar**.
5. **Editar Odo** ajusta un cuadro que ya está allí. **Borrar Odo** lo elimina y el botón luego dice **Seleccionar Odo**.
6. Revisar con **Mostrar puntos de referencia**. Utilice **Editar OCR** ​​para corregir errores de lectura o **agregar** texto que se omitió.
7. Complete **Nombre del vehículo** (obligatorio), además de la marca/modelo/año/placa que desee.
8. Toque **Crear vehículo** o **Guardar cambios** (requiere nombre + foto de referencia para un vehículo nuevo).

![Agregar campos de formulario de vehículo nuevo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/03b-manage-vehicles-new.jpg)

### <a id="landmarks-fix-what-discovery-missed"></a>Puntos de referencia: corrija lo que Discovery se perdió

Después de **Mostrar puntos de referencia**, desplácese por la lista y corrija los valores. A los motores a veces les faltan dígitos pequeños (por ejemplo, un reloj **60** en la parte inferior derecha del grupo). Utilice **Editar OCR** ​​para agregarlos o corregirlos para que la identidad del vehículo siga siendo confiable.

![Diálogo de puntos de referencia: revisar y editar OCR](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r2-manage-vehicles-landmarks.jpg)

### <a id="typing-without-a-perfect-photo"></a>Escribiendo sin una foto perfecta

Aún puedes usar la aplicación seleccionando un vehículo y **escribiendo** el odómetro, el volumen y el costo en Llenar; el OCR es opcional para todos los campos. La importación de galería funciona para la foto del tablero de referencia cuando prefieres no tomar fotografías en la aplicación.

**Consejo:** Después de la sincronización de la hoja de cálculo, las definiciones de vehículos (cultivos, puntos de referencia) se encuentran en la base de datos local; no es necesario volver a abrir Administrar vehículos para completar para usarlas.

---

## <a id="backups-and-multi-device-sync"></a>Copias de seguridad y sincronización multidispositivo

La aplicación está diseñada para que **varios teléfonos o tabletas puedan compartir los mismos datos de la flota** y para que puedas guardar una **copia de tus datos y fotos fuera del dispositivo**. Esto se hace con los destinos que **usted** configura en **sus** cuentas o **sus** servidores autohospedados, no con una “nube de gastos de vehículos” administrada por la empresa que otras personas pueden ver.

### <a id="what-runs-where"></a>Qué corre dónde

| Amable | Qué almacena | Uso típico |
|------|----------------|-------------|
| **Hoja de cálculo/sincronización tabular** | Vehículos, llenados de combustible, gastos (filas y pestañas) | Fusión de múltiples dispositivos + copia de seguridad estructurada |
| **Copia de seguridad de fotos** | Imágenes binarias (guión/bomba/recibo/fotos de referencia) | Copia de seguridad de fotos + restaurar archivos faltantes |

Puede configurar **múltiples destinos** de cada tipo (límite flexible por tipo). Los trabajadores manuales **Sincronizar ahora** y **en segundo plano** ejecutan los habilitados.

### <a id="offline-first"></a>Sin conexión primero

- **No se requiere red** para agregar un reabastecimiento, un gasto o un recibo. Todo se guarda **localmente primero**.
: cuando la red está disponible, la sincronización y la copia de seguridad de las fotos se ejecutan como **tareas en segundo plano** (según un cronograma que establezcas y cuando toques **Sincronizar ahora**). Los errores se muestran como texto rojo debajo de las filas de Configuración y un **!** en la barra de título de la aplicación.

### <a id="your-accounts-only"></a>Solo tus cuentas

El inicio de sesión y los tokens permanecen en el dispositivo para los proveedores que elija (Google, Microsoft, claves S3, URL autohospedadas, etc.). Los destinos están bajo **control total del usuario**: su cuenta de Google, su OneDrive, su depósito MinIO, su host EtherCalc, etc. No se comparte nada con otros usuarios de Gastos de Vehículos a través de un backend compartido.

### <a id="supported-targets--data-spreadsheet--tabular"></a>Objetivos admitidos: datos (hoja de cálculo/tabular)

Configurado en **Menú → Sincronización → Sincronización de hoja de cálculo** (también accesible desde las filas de resumen de Configuración). Opciones de recogida de primera clase:

| Objetivo | Notas |
|--------|--------|
| **Hojas de cálculo de Google** | Incumplimiento común; pestañas de Vehículos, Gastos y combustible por vehículo |
| **Excelente** | Libro de trabajo de Microsoft mediante encuadernación estilo Graph/OneDrive |
| **EtherCalc** | Salas de hojas de cálculo colaborativas autohospedadas |
| **Otros →** backends implementados | **Baserow**, **NocoDB**, **Airtable**, **PocketBase**, **Supabase**, **Firebase**, **Zoho Sheet** |

Diferido/aún no sin cabeza (enumerado en Otros pero no completamente implementado): OnlyOffice, Collabora. Véase también [índice de autohospedaje](reference/self-host/INDEX.md).

CSV **exportar/importar** (ZIP del mismo diseño de pestaña) está disponible en Configuración como una copia de seguridad portátil, independiente de la sincronización en vivo.

### <a id="supported-targets--photos-image-backup"></a>Objetivos admitidos: fotos (copia de seguridad de imagen)

Configurado en **Menú → Sincronización → Copia de seguridad de fotos** (también desde las filas de resumen de Configuración):

| Objetivo | Notas |
|--------|--------|
| **GoogleDrive** | Carpeta que elijas (buscar o pegar URL) |
| **OneDrive** | Cuenta de Microsoft + prefijo de ruta |
| **T3** | AWS, Wasabi, Cloudflare R2, MinIO y otros puntos finales compatibles con S3 |
| **Otro** | almacenamiento respaldado por rclone (por ejemplo, WebDAV, SFTP y otros controles remotos seleccionados disponibles en el selector de la aplicación) |

Configure hojas de trucos para fotografías autohospedadas y objetivos tabulares: [índice de autohospedaje](reference/self-host/INDEX.md).

### <a id="multi-device-behavior-short"></a>Comportamiento multidispositivo (breve)

: las filas se combinan por **ID de sincronización** con **ganancias de última escritura** en marcas de tiempo **actualizadas**.
- Las eliminaciones son suaves; una edición más reciente en otro dispositivo puede restaurar una fila.
: ingresar **el mismo relleno dos veces** en dos dispositivos crea **dos filas**; elimine el exceso cuando lo note.
- Más detalles: [Notas de comportamiento de sincronización](#sync-behavior-notes) y [SYNC_BEHAVIOR.md](reference/SYNC_BEHAVIOR.md).

### <a id="example-add-google-sheets-data"></a>Ejemplo: agregar Google Sheets (datos)

1. **Menú → Sincronización → Sincronización de hojas de cálculo** (o Configuración → Sincronización de hojas de cálculo).

![Lista de sincronización de hojas de cálculo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/08-spreadsheet-sync.jpg)

2. Toca **Agregar destino de hoja de cálculo**.

![Selector de proveedor](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/09-spreadsheet-provider-picker.jpg)

3. Elija **Hojas de cálculo de Google**.

![formulario de Google Sheets](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/10-spreadsheet-google-form.jpg)

4. **Inicia sesión con Google** una vez; ese inicio de sesión también te permite navegar. Nombre para mostrar → pegue una **URL de hoja** (aún válida) o **🔍** explore. Explorar se abre en **Todos y compartidos** para que pueda elegir una hoja de cálculo existente que sea de su propiedad o que se haya compartido con usted. Luego programar opciones → habilitar → guardar.
5. **Sincronizar ahora** una vez para crear/actualizar pestañas: `Vehicles`, `Expenses`, `Fuel - {vehicle name}`.

### <a id="example-add-google-drive-photos"></a>Ejemplo: agregar Google Drive (fotos)

1. **Menú → Sincronización → Copia de seguridad de fotos** (o Configuración → Copia de seguridad de fotos).

![Lista de copias de seguridad de fotos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/11-photo-backup.jpg)

2. Toca **Agregar destino de la foto**.

![Selector de proveedor de fotos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/12-photo-provider-picker.jpg)

3. Elija **Google Drive**.

![Formulario de Google Drive](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/13-photo-google-form.jpg)

4. **Iniciar sesión con Google (Drive)** → URL de carpeta opcional/explorar → habilitar → guardar → **Sincronizar ahora**.

Manual **Sincronizar ahora** para fotos es un pase completo; La copia de seguridad en segundo plano normalmente procesa cargas **solo pendientes** según una programación.

### <a id="sync-behavior-notes"></a>Sincronizar notas de comportamiento

: después de la actualización de la aplicación, es posible que vea brevemente **“Actualizando la base de datos después de la actualización…”** (relleno de ID de sincronización local).
: si se interrumpe una sincronización, la siguiente sincronización **exitosa** se vuelve a fusionar y repara las pestañas remotas.
- Fallos: resumen rojo en Sincronizar tarjetas + **!** en la barra de aplicaciones.

---

## <a id="fill-up-fuel"></a>Llene (combustible)

Esta es la **pantalla de inicio** cuando abres la aplicación.

### <a id="vehicle-selection-usually-automatic"></a>Selección de vehículo (normalmente automática)

**No** es necesario que elijas el vehículo primero. Cuando los vehículos tienen **puntos de referencia** configurados en Administrar vehículos, Fill **detecta automáticamente qué vehículo** de la imagen del tablero después de capturar el odómetro. Aún puedes abrir el menú desplegable **Vehículo** para anularlo si es necesario.

### <a id="aim-at-the-odometer"></a>Apunta al odómetro

Permanezca en modo odómetro y encuadre el grupo. Instrucción: *Apunte al odómetro. Toque el obturador para capturar.*

![Relleno: cámara en vivo en el odómetro (ejemplo de imagen del tablero en vista previa)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r3-fill-odo-live.jpg)

### <a id="after-the-odometer-shutter"></a>Después de la persiana del odómetro

El OCR llena **Odo** e intenta hacer coincidir el vehículo con los puntos de referencia (revise ambos si es necesario). El botón principal se convierte en **Reintentar** para volver a disparar. La instrucción resume la lectura.

![Rellenar — resultado OCR del odómetro](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r4-fill-odo-result.jpg)

### <a id="pump-mode-cost-and-volume"></a>Modo de bomba (coste y volumen)

1. Toque **↕** para cambiar al modo de bomba: *Apunte a la pantalla de la bomba (costo/volumen). Toque el obturador.*
2. Capture los totales de la bomba. Se llenan los campos de costo y volumen; use **↔** si se intercambian.
3. Toque moneda o **G/L** si es necesario, luego **Guardar** (disco). Los campos vacíos generan un **relleno parcial** (aún se permite).

![Rellenar — vista previa de bomba/totales (imagen de bomba de ejemplo en vista previa)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r5-fill-pump-result.jpg)

Permaneces en Completar para la siguiente parada (los campos se borran después de guardar). Trabaje completamente **sin conexión**; la sincronización se ejecuta más tarde en segundo plano cuando se configura.

### <a id="manual-entry-no-camera--bad-ocr"></a>Entrada manual (sin cámara / OCR incorrecto)

1. Toque **Odo**, **costo** o **volumen** y escriba los valores (la vertical usa el teclado del sistema; la horizontal usa un teclado en pantalla).
2. Elija o confirme el **Vehículo** si no se ejecutó la detección automática.
3. Guarde como se indica arriba.

### <a id="modes-and-borders"></a>Modos y fronteras

- **Borde verde** alrededor del vehículo+odo → captura/edición del odómetro.
- **Borde verde** alrededor de costo+volumen → modo de bomba.
- **Guardar** permanece deshabilitado hasta que se selecciona un vehículo y al menos uno de odo/costo/volumen tiene datos, y el OCR aún no se está ejecutando.

Consejo en pantalla (debajo de la línea de instrucciones): *Obturador = capturar · Disco = guardar · ↕ = modo odo/bomba · ↔ = costo/volumen de intercambio.*

---

## <a id="expenses"></a>Gastos

### <a id="new-expense"></a>Nuevo gasto

Menú → **Nuevo gasto**.

![Nuevo formulario de gastos y controles de cámara (ejemplo de imagen de guión en la banda de la cámara cuando está vacía)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/r6-new-expense.jpg)

1. **Guardar** (disco), **obturador** (foto del recibo) o **galería** (seleccionar imagen).
2. Complete **Fecha**, **Vehículo**, **Proveedor**, **Descripción**, **Cantidad** (símbolo de moneda que se puede tocar), **Categoría**, **Odómetro** opcional.
3. Recibos de varias páginas: capture páginas adicionales si la interfaz de usuario ofrece paginación (la página 0 es el recibo principal).
4. **Guardar** en la tienda (primero local; la copia de seguridad de las fotos y la sincronización de la hoja de cálculo se realizan en segundo plano cuando se configuran).

### <a id="expense-list"></a>Lista de gastos

Menú → **Informes** → **Lista de gastos**: explore los gastos no relacionados con el combustible; abrir un elemento para editarlo.

![Lista de gastos](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/05-expense-list.jpg)

### <a id="edit-expense"></a>Editar gasto

Abre una fila de la lista. Proveedor, monto, categoría, vehículo y descripción correctos. Si el recibo solo está en la copia de seguridad de la foto (no hay un archivo local legible), use **Obtener imagen del archivo** cuando se muestre (funciona en destinos de fotos configurados).

![Editar gasto](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/expense-edit.jpg)

---

## <a id="start-trip"></a>Iniciar viaje

Menú → **Iniciar viaje** (después de Llenar el cajón). Capture o ingrese el odómetro, elija el tipo de viaje y guárdelo con el ícono **disco**. **Detener** es un atajo para Personal ahora en la ubicación GPS retenida. Utilice **ⓘ** para recordatorios de control.

![Iniciar viaje](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/start-trip.jpg)

Los inicios de viaje se almacenan como filas de combustible con un **Tipo de viaje** (no llenados normales). Aparecen en **Informes → Millas de viaje**, no en Historial de combustible.

---

## <a id="reports"></a>Informes

Menú → **Informes** abre el centro de productos (resumen de todos los tiempos + tarjetas de catálogo). Esta es la única superficie de informes de productos: no hay un elemento separado en el cajón "Informes y gráficos".

![Centro de informes](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/06-reports.jpg)

Abra una tarjeta para el modo vehículo (**Todos / Cada uno / Individual**), filtros de período, gráficos y comparta (**TEXTO / CSV / PDF**). Barra superior en informes de niños: **☰ + ←** (y **ⓘ** cuando esté registrado).

### <a id="time-based-reports"></a>Informes basados en tiempo

La tarjeta del gráfico principal. Métricas opcionales (mpg, volumen/distancia como G/mi, precio unitario como $/G, costo/distancia, $ mensuales, millas de viaje, % de viaje por tipo) con contenedores **Smooth** y **escalas Y independientes** (economía a la izquierda; dinero y familias de viajes a la derecha).

![Informes basados en tiempo](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports.jpg)

![Informes basados en tiempo: métricas desplazadas](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/time-based-reports-scrolled.jpg)

Detalles de matemáticas económicas: [REPORTS_METRICS.md](reference/REPORTS_METRICS.md).

### <a id="fill-history-vs-fuel-history"></a>Historial de llenado vs Historial de combustible

- **Reportes → Historial de llenado**: llenados cronológicos para los filtros de informes (**solo llenados**; no se inician viajes).

![Rellenar informe de historial](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fill-history.jpg)

- **Historial de combustible** (si está presente en la navegación de su versión): inventario de llenado por vehículo, también solo llenado; toque una fila para editarla.

### <a id="trip-miles"></a>Millas de viaje

**Informes → Millas de viaje**: millas por tipo, gráficos y una **lista de inicio de viaje/segmento** cronológica. Toque un comienzo real para abrir **Editar relleno** para esa fila.

![Millas de viaje](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/trip-miles.jpg)

### <a id="edit-fill"></a>Editar relleno

Desde Historial de repostaje, Historial de combustible o Millas de viaje, abra un repostaje. Diseño: vehículo y odómetro, **moneda antes de costo**, volumen, billetes. El tipo de viaje aparece solo cuando la fila es un inicio de viaje. La ubicación tiene un resumen más **Detalles de la ubicación**. Falta una foto local con identidad en la nube: **Obtener imagen del archivo**.

![Editar relleno: recuperar del archivo cuando falta la foto local](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/fuel-edit.jpg)

Otras tarjetas de catálogo incluyen gastos por categoría, resumen de vehículos y lista de gastos.

El dinero usa la moneda de cada fila cuando se configura. Los totales en monedas mixtas muestran **subtotales por moneda** (sin conversión de divisas silenciosa).

---

## <a id="syncing"></a>Sincronizando

Menú → **Sincronización** es el centro para destinos de hojas de cálculo y fotografías (no solo oculto en Configuración).

![Centro de sincronización](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/syncing-hub.jpg)

- Tarjetas para **Sincronización de hojas de cálculo** y **Copia de seguridad de fotos** con estado breve, **Sincronización** para ese tipo y **›** en la lista de destinos.
: abra un destino para **Probar conexión** y **Sincronizar ahora (este destino)**/todo configurado.
- Error **Detalles** y el **!** rojo en la barra de título aterrizan aquí.
- Configuración paso a paso de Google Sheets y Drive: [Copias de seguridad y sincronización multidispositivo](#backups-and-multi-device-sync).

---

## <a id="settings-local-preferences"></a>Configuración (preferencias locales)

Menú → **Configuración**.

![Configuración](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07-settings.jpg)

![Más configuraciones (desplazadas)](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/07b-settings-more.jpg)

Para destinos, prefiera **Menú → Sincronización**. Es posible que la configuración aún muestre filas de resumen que abren las mismas listas.

### <a id="local-preferences-common"></a>Preferencias locales (comunes)

- **Guardar fotos de recibos de combustible** / **Guardar fotos de gastos localmente**: mantenga las imágenes en el dispositivo (puede solicitar permiso para fotos).
- **Reproducir sonido del obturador**
- **Moneda** / **Unidad de volumen**: valores predeterminados de la aplicación (del sistema o explícitos). Cambiar la unidad de volumen con datos de combustible existentes puede ofrecer un cuadro de diálogo de conversión.
- **Modo oscuro**
- **Consejos de configuración**: vuelva a abrir los tutoriales de sincronización/vehículo de primera ejecución.
- **Relleno de depuración** / **Mostrar pantallas de experimento (dev)** — avanzado; déjelo para el uso diario. Las pantallas de experimentos no están documentadas aquí.

CSV **exportar/importar** (ZIP de pestañas de vehículos/gastos/combustible) está disponible en Configuración cuando lo ofrece la versión actual.

---

## <a id="help--about"></a>Ayuda y acerca de

![Ayuda](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/14-help.jpg)

![Acerca de](https://raw.githubusercontent.com/davidelang/VehicleExpenses-Automated/master/docs/user-manual/images/15-about.jpg)

- **Ayuda**: inicio rápido en el dispositivo, tutoriales de configuración, enlace a este manual, índice de configuración de autohospedaje.
- **Acerca de**: versión, licencias, GitHub, este manual (incluido sin conexión + HTML en línea cuando se publique).

---

## <a id="related-docs"></a>Documentos relacionados

- [QUICK_GUIDE.md](../QUICK_GUIDE.md) — breve guía diaria
- [self-host/INDEX.md](../reference/self-host/INDEX.md) — configuración tabular/foto autohospedada
- [SYNC_BEHAVIOR.md](../reference/SYNC_BEHAVIOR.md) — fusión, recuperación, duplicados
- [REPORTS_METRICS.md](../reference/REPORTS_METRICS.md) — detalle de métricas económicas
