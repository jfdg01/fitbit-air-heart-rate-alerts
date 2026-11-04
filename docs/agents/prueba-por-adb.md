# La prueba por adb

Un agente prueba los avisos en un teléfono sin la correa, con la demo y un pulso fijo.

## La instalación

La app de los teléfonos tiene la firma de release. Una build de debug con otra firma da `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, y desinstalar borra los ajustes. Firma la build de debug con la clave de release:

```bash
./gradlew :app:assembleDebug
set -a; . ~/.config/android-signing/fitbit-air/signing.env; set +a
$ANDROID_HOME/build-tools/36.0.0/apksigner sign \
  --ks ~/.config/android-signing/fitbit-air/keystore.jks \
  --ks-pass env:SIGNING_STORE_PASSWORD --ks-key-alias "$SIGNING_KEY_ALIAS" \
  --key-pass env:SIGNING_KEY_PASSWORD \
  --out /tmp/app-debug-release-key.apk app/build/outputs/apk/debug/app-debug.apk
adb -s <serie> install -r /tmp/app-debug-release-key.apk
```

## La demo

1. En la pantalla de conexión, toca el corazón 5 veces, con menos de 1 s entre toques. Sale "Modo demo".
2. Toca **Conectar** y elige "Heart Alert Demo".
3. Toca **Iniciar**.

La demo da una onda de pulso. La build de debug fija el pulso con un broadcast, y `-1` devuelve la onda:

```bash
adb -s <serie> shell am broadcast -a com.nikashitsa.polar_alert_android.SET_BPM \
  -p com.nikashitsa.polar_alert_android.fitbit --ei bpm 170
```

## El log

Cada sonido y cada vibración deja una línea en logcat:

```bash
adb -s <serie> logcat -s Alerts:D DemoBpmReceiver:D
```

- La vibración viene apagada en los ajustes, y sin ella no sale la línea `vibration`.
- El teléfono Vivo V2028 esconde las líneas de `Log.d`, porque tiene `log.tag` = `E`. Abre las dos etiquetas antes de la prueba. El ajuste dura hasta el siguiente reinicio:

```bash
adb -s <serie> shell setprop log.tag.Alerts D
adb -s <serie> shell setprop log.tag.DemoBpmReceiver D
```
