# Los docs del dominio

El repo es single-context: un `GLOSSARY.md` en la raíz y las ADR en `docs/adr/`.

## Antes de explorar

Lee `GLOSSARY.md`, y las ADR de `docs/adr/` que tocan la zona del trabajo. Un archivo que no existe no se nombra ni se propone. `/domain-modeling` crea cada archivo cuando un término o una decisión queda resuelto.

## El vocabulario del glosario

Un concepto del dominio usa el término de `GLOSSARY.md`: en el título de una issue, en una propuesta, en una hipótesis o en el nombre de un test. Un sinónimo que el glosario evita no se usa.

Un concepto que falta en el glosario es una señal. O el término es inventado y el proyecto no lo usa, o el glosario tiene un hueco para `/domain-modeling`.

## Un conflicto con una ADR

Una salida que contradice una ADR lo dice de forma explícita:

> _Contradice la ADR-0007 (pedidos con event sourcing), pero vale la pena abrirla otra vez porque…_
