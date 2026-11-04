# Issue tracker: GitHub

Las issues y las specs de este repo viven en las GitHub Issues. Cada operación usa el CLI `gh`, que saca el repo de `git remote -v`.

## Las operaciones

- **Crear una issue**: `gh issue create --title "..." --body "..."`. Un texto de varias líneas va en un heredoc.
- **Leer una issue**: `gh issue view <número> --json number,title,body,labels,comments`.
- **Listar las issues**: `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`, con los filtros `--label` y `--state`.
- **Hacer una sub-issue**: `gh issue create --parent <padre> ...`, o `gh issue edit <padre> --add-sub-issue <hija>` (`gh` 2.94 o más). Sin sub-issues, la hija empieza con `Part of #<padre>`.
- **Comentar**: `gh issue comment <número> --body "..."`.
- **Poner o quitar una label**: `gh issue edit <número> --add-label "..."` o `--remove-label "..."`.
- **Cerrar**: `gh issue close <número> --comment "..."`.

Una skill que dice "publish to the issue tracker" crea una GitHub issue. Una skill que dice "fetch the relevant ticket" lee la issue como en **Leer una issue**.

## Las pull requests

**PRs as a request surface: no.** `/triage` lee este valor. Con `yes`, una PR externa pasa por las mismas labels que una issue.

## Wayfinding

`/wayfinder` usa un **mapa**: una issue con la label `wayfinder:map` y el texto Notes / Decisions-so-far / Fog. Cada ticket es una issue **hija** del mapa.

- **Una hija** es una sub-issue del mapa, con la label `wayfinder:<tipo>` (`research`, `prototype`, `grilling` o `task`). Sin sub-issues, la hija va en una lista de tareas del mapa y empieza con `Part of #<mapa>`.
- **El bloqueo** usa las dependencias nativas de GitHub: `gh api --method POST repos/<dueño>/<repo>/issues/<hija>/dependencies/blocked_by -F issue_id=<id>`. El `<id>` es el database id del bloqueo (`gh api repos/<dueño>/<repo>/issues/<n> --jq .id`), y no el número. Sin dependencias, la hija empieza con `Blocked by: #<n>, #<n>`. Una hija queda libre cuando cada bloqueo está cerrado.
- **La frontera** son las hijas abiertas del mapa sin un bloqueo abierto y sin asignar. La primera en el orden del mapa gana.
- **Tomar una hija**: `gh issue edit <n> --add-assignee @me`, la primera escritura de la sesión.
- **Resolver una hija**: `gh issue comment <n> --body "<respuesta>"`, después `gh issue close <n>`, y después un puntero (resumen y enlace) en el Decisions-so-far del mapa.
