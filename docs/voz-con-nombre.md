# La voz con el nombre

Un aviso de zona dice el nombre del corredor. Así un corredor sabe de quién habla el aviso cuando dos corren juntos (ver `correr-en-pareja`, #1).

## Los avisos que dicen el nombre

Solo las tres frases de zona dicen el nombre. Estas frases suenan al principio de un aviso, en un cambio de "bajo" a "alto", y al volver a la zona. `TextToSpeech` dice la frase entera, en el idioma de la app:

| El aviso | Español | Inglés |
|---|---|---|
| `TOO_HIGH` | "Javi, muy alto" | "Javi, too high" |
| `TOO_LOW` | "Javi, muy bajo" | "Javi, too low" |
| `GOOD` | "Javi, bien" | "Javi, good" |

Los pitidos que se repiten cada `alertInterval` no cambian. Un pitido con un nombre cada pocos segundos cansa. Los avisos de conexión (`CONNECTED`, `DISCONNECTED` y `RECONNECTING`) tampoco cambian, porque hablan del teléfono propio.

## Los otros idiomas

Solo el español y el inglés tienen las frases. Los otros idiomas de la app tocan los `.wav` de `res/raw-<tag>/`, sin el nombre. Android usa `values/strings.xml` (el inglés) para un idioma sin la frase, y un corredor alemán oye entonces una frase en inglés. Por eso la app comprueba el idioma antes de usar la voz.

## Un nombre vacío

Un corredor sin un nombre en Ajustes es "A", y la frase dice "A, muy alto". Un teléfono tiene un solo corredor. En una sala, el corredor que se une es "B" (ver `sala-de-pareja.md`).

Una voz en español puede leer "A" como la preposición "a". La prueba del dueño en la calle comprueba cómo suena la letra.

## Un fallo de la voz

El motor de `TextToSpeech` puede no tener la voz del idioma, o puede no estar listo todavía. Entonces suena el `.wav` del aviso, y `logcat` muestra la razón. Un aviso sin el nombre es mejor que un aviso que no suena.

## El volumen

La voz usa el volumen del slider de Ajustes y los mismos `AudioAttributes` que `SoundPool` (`USAGE_GAME`). Así la voz y el pitido suenan al mismo nivel, con un solo control.

## El campo del nombre

El nombre vive en Ajustes, en el primer campo de la pantalla. El campo tiene la etiqueta "Nombre" y el texto de ayuda "A", el nombre de un corredor sin nombre. El campo usa el estilo de `NumberField.kt`, como los otros campos de Ajustes.

La app quita los espacios de los extremos del nombre. Un nombre tiene 10 caracteres o menos, porque un nombre largo hace una frase lenta.
