# AuctionHouse

Plugin de casa de subastas para servidores **Minecraft Bedrock** basados en **NukkitPetteriM1Edition (PM1E)**.

AuctionHouse permite a los jugadores publicar objetos, explorar las publicaciones activas, comprar artículos de otros jugadores y recuperar objetos enviados a Storage cuando una publicación vence o se cancela.

## Características

- Interfaz de casa de subastas mediante inventario doble.
- Visualización de publicaciones activas con paginación.
- Sección para consultar las publicaciones propias.
- Confirmación de compra mediante formulario.
- Compra con EconomyAPI y entrega del objeto al inventario del comprador.
- Validación de espacio disponible en el inventario.
- Protección para impedir comprar las propias publicaciones.
- Reserva de publicaciones durante el proceso de compra para evitar compras simultáneas.
- Duración configurable de las publicaciones.
- Precio mínimo y máximo configurables.
- Límites de publicaciones configurables por rango y permisos.
- Publicaciones vencidas transferidas a Storage.
- Almacenamiento persistente con SQLite.
- Serialización de objetos para conservar sus datos al guardarlos y recuperarlos.

## Requisitos

- NukkitPetteriM1Edition.
- Java 25 (versión utilizada para compilar el proyecto).
- FakeInventories.
- FormConstructor 3.1.0.
- EconomyAPI 2.0.7.

**LuckPerms:** se puede utilizar para asignar los permisos de límites por rango. No es una dependencia obligatoria.

El controlador SQLite JDBC se incluye dentro del JAR generado mediante Maven Shade.

## Instalación

1. Descarga el archivo JAR desde [Releases](https://github.com/BrunoJaegerK/AuctionHouseNK/releases).
2. Comprueba que las dependencias requeridas estén instaladas.
3. Coloca el JAR en la carpeta `plugins`.
4. Inicia el servidor para generar la configuración y la base de datos.
5. Edita `plugins/AuctionHouse/config.yml`.
6. Reinicia el servidor para aplicar los cambios.

Haz una copia de seguridad de `config.yml` y de `auctionhouse.db` antes de actualizar.

## Comandos

| Comando | Descripción | Permiso |
|---|---|---|
| `/ah` | Abre la casa de subastas. | `auctionhouse.use` |
| `/ah sell <precio>` | Publica el objeto que el jugador tiene en la mano. | `auctionhouse.sell` |
| `/ah testitem` | Ejecuta una prueba de serialización del objeto en la mano. | `auctionhouse.admin` |

## Permisos

| Permiso | Descripción | Predeterminado |
|---|---|---|
| `auctionhouse.use` | Permite abrir la casa de subastas. | Todos |
| `auctionhouse.sell` | Permite publicar objetos. | Todos |
| `auctionhouse.admin` | Permite funciones administrativas y publicaciones ilimitadas. | Operadores |
| `auctionhouse.limit.<rango>` | Aplica el límite configurado para el rango. | Según configuración |

## Límites por rango

Los límites se configuran en `config.yml`.

```yaml
auction:
  listing-duration-hours: 24

  # 0 significa publicaciones ilimitadas.
  listing-limits:
    default: 5
    vip: 10
    vipplus: 15

  tax-percent: 0.0
  allow-self-purchase: false
  min-price: 1.0
  max-price: 1000000000.0

storage:
  type: SQLITE
  database: auctionhouse.db
```

Los nombres de las claves, excepto `default`, se convierten en permisos:

- `vip` → `auctionhouse.limit.vip`
- `vipplus` → `auctionhouse.limit.vipplus`

Asigna estos permisos a los grupos correspondientes desde LuckPerms.

Si un jugador tiene varios permisos de rango, se aplica el límite numérico más alto. Si uno de los límites que le corresponde es `0`, tendrá publicaciones ilimitadas. Los jugadores con `auctionhouse.admin` también tienen publicaciones ilimitadas.

Para añadir otro rango, agrega una nueva clave en `listing-limits` y asigna el permiso correspondiente. No necesitas modificar el código Java.

## Configuración

| Clave | Descripción |
|---|---|
| `auction.listing-duration-hours` | Duración de cada publicación en horas. |
| `auction.listing-limits` | Límites máximos por defecto y por rango. |
| `auction.tax-percent` | Porcentaje de impuesto configurado. |
| `auction.allow-self-purchase` | Opción de configuración relacionada con la compra de publicaciones propias. |
| `auction.min-price` | Precio mínimo permitido. |
| `auction.max-price` | Precio máximo permitido. |
| `storage.type` | Tipo de almacenamiento. Actualmente se utiliza SQLite. |
| `storage.database` | Nombre del archivo de base de datos. |

## Compilar desde el código fuente

El proyecto utiliza Maven.

1. Clona el repositorio:

```bash
git clone https://github.com/BrunoJaegerK/AuctionHouseNK.git
```

2. Abre el proyecto en IntelliJ IDEA como proyecto Maven.
3. Coloca las dependencias locales en la carpeta `libs`, con estos nombres:

- `server.jar`
- `FakeInventories-1.1.8-PM1E-IWareQ.jar`
- `economyapi-2.0.7.jar`

4. Ejecuta:

```bash
mvn clean package
```

5. El JAR se genera en `target/` con el nombre `AuctionHouse-<versión>.jar`.

Maven Shade incluye SQLite JDBC en el JAR final y conserva el registro del controlador JDBC.

## Almacenamiento y seguridad

Las publicaciones se guardan en SQLite. El plugin utiliza estados para controlar el ciclo de vida de las publicaciones y reserva una publicación antes de completar una compra, evitando que dos compradores procesen simultáneamente la misma oferta.

Los objetos se serializan para conservar sus datos. Las publicaciones activas que alcanzan su fecha de vencimiento se procesan y sus objetos se guardan en Storage para que el propietario pueda recuperarlos.

## Desarrollo

- **Plataforma:** NukkitPetteriM1Edition.
- **Lenguaje:** Java.
- **Build:** Maven.
- **Base de datos:** SQLite.

## Autor

**BrunoJaegerK**

Creado con cariño para **DELUXEMINE NETWORK**.

## Reportar problemas

Si encuentras un error o tienes una sugerencia, abre un [Issue](https://github.com/BrunoJaegerK/AuctionHouseNK/issues) e incluye:

- Versión de AuctionHouse.
- Versión de NukkitPetteriM1Edition.
- Mensajes relevantes de la consola.
- Pasos para reproducir el problema.
