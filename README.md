# Zeus Anti-Cheat

Plugin anti‑cheat ligero para servidores Spigot/Bukkit. Incluye detecciones de movimiento, combate, colocación/rotura, inventario, pesca, teletransporte, vehículos y elytra, todas con interruptores `enabled` y umbrales configurables en `config.yml`. Mensajería disponible en español e inglés.

## Características
- Detecciones con toggle: `enabled` por check en `config.yml`.
- Setbacks y cancelaciones inmediatas para acciones ilegales.
- Sistema de violaciones (VL) con reglas de `warn` y `kick` por check.
- Comandos de administración (`/anticheat`) y utilidades (`/zeus`).
- Mensajes personalizables en `messages_es.yml` y `messages_en.yml`.
- Hook opcional con Essentials (softdepend).

## Requisitos
- Java 8 o superior.
- Servidor Spigot/Bukkit. Compila contra `spigot-api:1.8.8`, y declara `api-version: 1.20` en `plugin.yml`. Ajusta según tu versión de servidor si fuese necesario.

## Instalación
1. Descarga/compila el JAR.
2. Copia `build/libs/ZeusAntiCheat-0.1.0.jar` a la carpeta `plugins/` de tu servidor.
3. Reinicia el servidor o usa `/reload`.
4. Configura `plugins/ZeusAntiCheat/config.yml` según tus necesidades.

## Compilación (desde fuente)
- Windows: `./gradlew.bat clean build`
- Linux/Mac: `./gradlew clean build`
- Artefacto: `build/libs/ZeusAntiCheat-0.1.0.jar`

## Configuración rápida
Todos los checks se pueden activar/desactivar y ajustar:

```yml
# Ejemplos de toggles
checks:
  movement:
    speed:
      enabled: true
    fly:
      enabled: true
    bhop:
      enabled: true
    ongroundspped:
      enabled: true
    step:
      enabled: true
    jesus:
      enabled: true
    jetpack:
      enabled: true
    spider:
      enabled: true
    blink:
      enabled: true
    nofall:
      enabled: true
    timer:
      enabled: true
    noslow:
      enabled: true
  combat:
    reach:
      enabled: true
    killaura:
      enabled: true
  click:
    autoclicker:
      enabled: true
  inventory:
    autototem:
      enabled: true
    autotool:
      enabled: true
  break:
    fastbreak:
      enabled: true
  place:
    fastplace:
      enabled: true
    scaffold:
      enabled: true
    freecam:
      enabled: true
    autocrystal:
      enabled: true
    autotrap:
      enabled: true
  teleport:
    chorus:
      enabled: true
  elytra:
    enabled: true
  vehicle:
    boat:
      speed:
        enabled: true
      fly:
        enabled: true
```

Además, cada check tiene umbrales configurables (por ejemplo `checks.movement.speed.max_base_walk`, `checks.combat.reach.max_distance`, etc.). Revisa `src/main/resources/config.yml` para la lista completa y comentarios.

### Castigos (punishments)
Configura por check cuándo avisar (`warn`) y expulsar (`kick`):

```yml
punishments:
  Speed: { warn: 5, kick: 12 }
  Fly: { warn: 4, kick: 10 }
  # ... resto de checks
```

## Comandos
`/anticheat` (permiso: `anticheat.admin`)
- `reload` — Recarga configuración y mensajes.
- `status <jugador>` — Muestra violaciones del jugador.
- `reset <jugador> [all|check]` — Reinicia VL totales o de un check.

`/zeus` (permisos varios `zeus.*`)
- `help` — Muestra ayuda.
- `reload` — Recarga configuración y mensajes.
- `logs [jugador] [cantidad] [página]` — Registros de detecciones.
- `last [jugador]` — Muestra el último detalle de detección (por defecto tú).
- `alerts` — Activa/desactiva alertas para el staff.
- `mute <jugador> [minutos]` — Silencia jugador.
- `unmute <jugador>` — Quita silencio.

## Permisos
- `anticheat.admin` — administración de Zeus Anti-Cheat (por defecto `op`).
- `zeus.reload`, `zeus.help`, `zeus.logs`, `zeus.alerts`, `zeus.mute`, `zeus.unmute` — utilidades de Zeus.
- `zeus.*` — acceso a todos los permisos de Zeus.

## Checks disponibles
- Movement: `Speed`, `OnGroundSpeed`, `Fly`, `BHop`, `Blink`, `Step`, `WaterWalk`, `Jetpack`, `NoFall`, `Timer`, `Climb`, `NoSlow`.
- Elytra/Vehículos: `ElytraFly`, `BoatSpeed`, `BoatFly`.
- Combate: `Reach`, `KillAura`.
- Colocación: `FastPlace`, `Scaffold`, `Freecam`, `AutoCrystal`, `AutoTrap`.
 - Rotura: `FastBreak`.
 - Click/Inventario: `AutoClicker`, `AutoTotem`, `AutoTool`, `Inventory`.
- TP: `ChorusControl`.
 - Protección: `Crash` (libros/carteles), `Baritone` (pathing automático).

## Mensajes y localización
- Archivos: `lang/messages_es.yml` y `lang/messages_en.yml`.
- Prefijo configurable: `messages.prefix` en `config.yml`.

## Hooks
- `Essentials` (softdepend): si está presente y `hooks.essentials.enabled: true`, se registran listeners de moderación.

## Consejos de ajuste
- Ajusta umbrales si detectas falsos positivos (por ejemplo superficies deslizantes en `checks.movement.speed.ignore_surfaces`).
- Usa `/zeus alerts` para gestionar el ruido de alertas del staff.
- Desactiva checks que no te interesen con `enabled: false`.

## Compatibilidad
- Compila contra Spigot 1.8.8; `plugin.yml` declara `api-version: 1.20`. El plugin está diseñado para funcionar en una gama amplia de versiones; valida en tu entorno y ajusta `plugin.yml`/dependencias si tu servidor usa otra versión.

## Soporte
Si tienes dudas o detectas problemas, abre un issue o ajusta la configuración y vuelve a probar. Asegúrate de compartir versión de servidor, Java y configuraciones relevantes.

Plugin básico de anti-cheat para servidores Paper/Spigot. Incluye detecciones iniciales de velocidad (Speed) y vuelo ilegal (Fly), gestor de violaciones (VL) y comando de administración.

## Requisitos
- Java JDK 17
- Gradle instalado (o importa el proyecto en IntelliJ/VSCode con soporte Gradle)
- Servidor Paper 1.20.x

## Construcción
```bash
gradle build
```
El artefacto se generará en `build/libs/ZeusAntiCheat-0.1.0.jar`.

## Instalación
1. Copia `ZeusAntiCheat-0.1.0.jar` a la carpeta `plugins` de tu servidor Paper.
2. Inicia el servidor. Se creará `plugins/Zeus Anti-Cheat/config.yml` con los umbrales por defecto.
3. Ajusta los valores según tu gameplay para reducir falsos positivos.

## Comandos
- `/anticheat reload` — Recarga la configuración.
- `/anticheat status <jugador>` — Muestra los VL activos del jugador (solo checks con VL>0) y el total acumulado.
- `/anticheat reset <jugador> [all|check]` — Reinicia todos los VL del jugador (`all`) o solo el VL del `check` indicado.

Permiso: `anticheat.admin`

### Zeus
- `/zeus help` — Muestra la ayuda de comandos de Zeus.
- `/zeus reload` — Recarga configuración y mensajes.
- `/zeus logs [jugador] [cantidad] [página]` — Muestra registros recientes de detecciones/advertencias/expulsiones. Si se indica `jugador`, filtra por ese nombre; `cantidad` limita elementos por página (por defecto 10); `página` permite navegar.
- `/zeus last [jugador]` — Muestra el último detalle de detección para un jugador. Si omites `jugador` y eres un player, usa tu propio nombre.
- `/zeus alerts` — Activa/Desactiva tus alertas personales de staff.
- `/zeus mute <jugador> [minutos]` — Silencia a un jugador (0 o vacío = indefinido).
- `/zeus unmute <jugador>` — Quita el silencio a un jugador.

Permisos: `zeus.help`, `zeus.reload`, `zeus.logs`, `zeus.alerts`, `zeus.mute`, `zeus.unmute` (o `zeus.*`)

## Configuración
`src/main/resources/config.yml` contiene:
- Umbrales de velocidad (caminar/sprint) y bonus por efecto de Speed.
- Superficies que se ignoran para evitar falsos positivos (hielo, slime, etc.).
- Máximo de ticks en el aire para detectar fly.
- Umbrales de advertencia y expulsión por cada check.

### Broadcast de sanciones (Kick/Ban/Mute)
- Broadcast público configurable al sancionar: `messages.broadcast_on_kick`, `messages.broadcast_on_ban`, `messages.broadcast_on_mute` (por defecto `true`).
- Opción para mostrar VL: `messages.include_vl_in_broadcasts` (por defecto `false`). Si está activado, se añade el fragmento localizado `sanction.vl_fragment` al final del broadcast del anti-cheat.
- Mensajes personalizables en `messages_es.yml` / `messages_en.yml`:
  - `public.kick` — Mensaje de expulsión.
  - `public.ban` — Mensaje de ban; usa `{duration}` para mostrar duración.
  - `public.mute` — Mensaje de mute; usa `{duration}`.
  - `sanction.duration_minutes` / `sanction.duration_permanent` — Fragmentos para componer `{duration}`.
  - `sanction.vl_fragment` — Fragmento opcional para mostrar `VL` si está habilitado en config.
  - `player_muted` — Aviso al jugador cuando intenta chatear estando muteado.
- Umbrales opcionales por check (si se desean sanciones automáticas):
  - `punishments.<Check>.ban` y `punishments.<Check>.ban_minutes` (0 = permanente).
  - `punishments.<Check>.mute` y `punishments.<Check>.mute_minutes` (0 = indefinido).
  - `punishments.<Check>.kick` continúa funcionando como antes.
 
Notas:
- El mute bloquea el chat vía listener y muestra un aviso al jugador con la duración restante.
- El ban utiliza la lista de bans del servidor y expulsa inmediatamente.

### Compatibilidad con Essentials
- Si el plugin Essentials está instalado, Zeus puede anunciar acciones de moderación ejecutadas con sus comandos:
  - `/kick`, `/ban`, `/tempban`, `/mute`, `/unmute`.
- Activa/desactiva el hook en `hooks.essentials.enabled` (por defecto `true`).
- Controla si se emite broadcast con `hooks.essentials.broadcast` (por defecto `true`).
- Los mensajes de broadcast usan claves `public_mod.*` en `messages_es.yml`/`messages_en.yml` y muestran moderador, razón y duración.
 - El fragmento de `VL` solo se aplica a broadcasts del anti-cheat, no a los de Essentials.

## Limitaciones y próximos pasos
- Este plugin es un punto de partida. No bloquea “casi todos” los hacks.
- Añadir checks de combate (KillAura, alcance), autoclicker, scaffold, timer, nofall, etc.
- Integrar análisis de paquetes con ProtocolLib para detecciones más robustas.
- Teleport/setback seguro en flags altos para prevenir ganancia de posición.
- Sistema de perfiles por mundo/modo de juego y registro estructurado (webhook/archivo).

Contribuciones y mejoras son bienvenidas.