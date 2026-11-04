# La sala de pareja

Dos corredores con un teléfono cada uno se unen en una sala. Cada teléfono manda el estado de su corredor al otro, y cada corredor ve y oye al compañero (ver `correr-en-pareja`, #1).

## El enlace

Los dos teléfonos hablan por BLE, con un servicio GATT propio. El enlace no necesita Google Play Services, ni un emparejamiento del sistema, ni el permiso de la ubicación. Una notificación GATT cada segundo es lo mismo que hace la correa. El enlace no tiene una cola de operaciones: el teléfono que se une escribe su estado solo después de la suscripción, y un estado que encuentra el enlace ocupado se pierde, porque el segundo siguiente manda uno más nuevo.

Un fabricante puede tener fallos en el modo periférico (el lado que anuncia y espera la conexión). La app comprueba `isMultipleAdvertisementSupported` antes de crear una sala.

## Crear y unirse

Un teléfono crea la sala, y el otro se une:

- El que crea la sala elige un código de 4 cifras al azar. Anuncia el código por BLE y hace de servidor GATT.
- El que se une escribe el código. Busca un anuncio con ese código y se conecta.

La sala tiene dos corredores como mucho. El que crea la sala deja de anunciar el código cuando el compañero se conecta, y así un tercer teléfono no entra. Después de una pérdida del enlace, el que crea la sala anuncia otra vez, y el que se une busca el código otra vez. La reconexión usa el mismo camino que la primera conexión.

## El estado del corredor

Cada segundo, cada teléfono manda el estado de su corredor:

| El campo | El contenido |
|---|---|
| El nombre | El nombre de Ajustes, o la letra de la sala |
| `bpm` | La última muestra de la correa |
| `trackingState` | `GOOD`, `LOW` o `HIGH` |
| `alerting` | El aviso del corredor suena |
| La correa | La correa está conectada |
| La salida | El corredor sale de la sala |

El estado cabe en un paquete BLE sin cambiar el MTU (el tamaño del paquete). Los límites de zona del compañero quedan fuera, porque la pantalla no los necesita.

## La privacidad

El enlace va sin cifrado. Un cifrado necesita un emparejamiento del sistema, y eso es un paso más en la calle. Un extraño necesita estar a pocos metros con un escáner BLE, y solo ve un nombre y un pulso.

`privacy-policy.md` dice que la app manda el nombre y el pulso del corredor por Bluetooth, sin cifrado, solo al teléfono de la sala. La app no manda nada a un servidor.

## La sección "Correr en pareja"

La sala vive en Ajustes, en una sección "Correr en pareja" encima del botón **Iniciar**. La sección tiene dos botones: **Crear sala** y **Unirse**.

- **Crear sala** muestra el código de 4 cifras y "Esperando al compañero".
- **Unirse** abre un campo de 4 cifras. La búsqueda dura 4 minutos como mucho, y el corredor la puede parar antes con **Cancelar**. Sin la sala en 4 minutos, la sección muestra "No encuentro la sala" y vuelve a **Crear sala** y **Unirse**.
- Con el compañero conectado, la sección muestra el nombre del compañero, el estado del enlace y el botón **Salir de la sala**.

## La vida de la sala

La sala dura hasta que el corredor toca **Salir de la sala**, o hasta que la app se cierra. **Parar** e **Iniciar** no cambian la sala, y una pausa en la carrera no pide el código otra vez. Entre **Iniciar** y **Parar**, el foreground service mantiene viva la sala con la pantalla apagada.

Un corredor que toca **Salir de la sala** manda un último estado con la salida, y después corta el enlace. El otro teléfono dice "Ana, fuera de la sala" (en inglés, "Ana, left the room"), deja de buscar y sale de la sala también. La sección vuelve a **Crear sala** y **Unirse**. Una búsqueda sin fin gasta batería, y "sin conexión" no dice la verdad de una salida.

Un último estado que no llega deja solo una pérdida del enlace (ver "La pérdida del compañero").

## El compañero en la sesión

La pantalla de la sesión muestra una fila pequeña debajo del pulso propio, con el nombre del compañero, su `bpm` y el color de su `trackingState`. El pulso propio queda grande, porque es el dato principal del corredor.

Sin el enlace, la fila muestra "--". Con la correa del compañero desconectada, la fila muestra el texto `disconnected` de la app.

## La voz del compañero

Mi teléfono dice las tres frases de zona con el nombre del compañero: "Ana, muy alto", "Ana, muy bajo" y "Ana, bien". Mi teléfono no calcula la zona del compañero. Habla cuando el `alerting` del compañero empieza, en un cambio de "bajo" a "alto", y cuando el compañero vuelve a la zona después de un aviso. El **Parar** del compañero no es una vuelta a la zona, y no dice nada. Así los ajustes del compañero deciden cuándo suena su aviso.

Los pitidos, las vibraciones y la repetición de `alertInterval` son solo para el corredor propio.

## La pérdida del compañero

Una pérdida del enlace dice una frase una vez, y la vuelta del enlace dice otra:

| El momento | Español | Inglés |
|---|---|---|
| La pérdida | "Ana, sin conexión" | "Ana, disconnected" |
| La vuelta | "Ana, con conexión" | "Ana, connected" |

Las frases en español no tienen género, porque la app no conoce el género del compañero. Los dos teléfonos intentan la reconexión durante 4 minutos. Sin el compañero en 4 minutos, la sala termina sin voz, y la sección muestra "La sala terminó sin el compañero".

Un teléfono que apaga Bluetooth pierde el enlace igual. Android no avisa de esa desconexión, así que el teléfono mira el estado de Bluetooth en cada envío del estado. Con Bluetooth otra vez encendido, el teléfono anuncia o busca la sala otra vez.

Una correa del compañero desconectada sale solo en la fila, sin voz. El teléfono del compañero ya avisa a su corredor.

## Solo en la sesión

Las frases del compañero suenan solo entre el **Iniciar** y el **Parar** del corredor propio. Fuera de la sesión, la sección de Ajustes muestra el compañero sin voz.

## Dos frases a la vez

Una frase que llega mientras otra suena espera su turno, con `QUEUE_ADD` de `TextToSpeech`. Así "Javi, muy alto" y "Ana, bien" suenan las dos, aunque lleguen en el mismo segundo. `QUEUE_FLUSH` corta la primera frase, y el corredor pierde un aviso. La regla vale también para las frases propias.

## Los otros idiomas

Solo el español y el inglés dicen las frases del compañero. En otro idioma, el teléfono no dice nada del compañero, y solo la fila lo muestra. Un `.wav` sin el nombre suena igual que el aviso propio, y confunde al corredor. Por la misma razón, un fallo de la voz no toca un `.wav` para el compañero.

## Las letras de un nombre vacío

Un corredor sin un nombre en Ajustes es "A" en el teléfono que crea la sala, y "B" en el teléfono que se une. Sin una sala, el corredor es "A". La letra es el nombre del corredor en todas partes: en sus propios avisos, en su estado y en las frases del compañero.

## El permiso

Anunciar necesita el permiso `BLUETOOTH_ADVERTISE`. La app lo pide cuando el corredor toca **Crear sala**, y no antes. El que se une no lo necesita, porque buscar usa `BLUETOOTH_SCAN`, y la app ya lo pide.

## Un error del enlace

Un fallo de BLE sale en la sección "Correr en pareja", con su código: "No puedo anunciar la sala (error 3)". Un teléfono sin el modo periférico muestra "Este teléfono no puede crear una sala. Crea la sala en el otro teléfono". Un Samsung de la prueba en la calle no tiene `adb`, y el texto de la sección es la única pista de un fallo allí.

## Las pruebas

- El agente prueba por `adb` en el HONOR y el Vivo, con la demo. La build de debug recibe un broadcast que inyecta el estado de un compañero falso, como `SET_BPM` inyecta el pulso. `docs/agents/prueba-por-adb.md` tiene el comando.
- La prueba en la calle usa el HONOR del dueño y un Samsung (S22 Ultra o S24 Ultra), con la Fitbit Air y el Coospo. El HONOR y un Samsung son los teléfonos del uso real. El Vivo sirve solo para la depuración.

## La instalación en un Samsung

Un Samsung recibe la APK de release como un archivo por WhatsApp. La APK sale de este comando:

```bash
set -a; . ~/.config/android-signing/fitbit-air/signing.env; set +a
./gradlew :app:assembleRelease
ls app/build/outputs/apk/release/app-release.apk
```

En el Samsung, la instalación tiene dos trampas:

- One UI 6 y más tiene el "Bloqueador automático", en Ajustes > Seguridad y privacidad. El ajuste bloquea una APK de WhatsApp. Apágalo antes de la instalación.
- Play Protect puede avisar de una app desconocida. Toca "Instalar de todos modos".
