# aed-casa-amarilla

Sistema de admisión y matrícula 2027 del **Nido La Casa Amarilla**, desarrollado en Java (Swing) como proyecto del curso de Algoritmos y Estructura de Datos.

## Requisitos

- Java 21 (JDK)
- Eclipse IDE (opcional: el repositorio incluye `.project` y `.classpath`)

## Cómo ejecutar

**Con Eclipse:** importa el proyecto (*File → Import → Existing Projects into Workspace*) y ejecuta `src/Main.java`.

**Desde la terminal:**

```bash
javac -d bin $(find src -name "*.java")
java -cp bin Main
```

## Estructura

| Paquete | Contenido |
|---|---|
| `modelo` | Entidades del dominio (`Alumno`, `Apoderado`, `Solicitud`, `Matricula`…), enums de estado y excepciones |
| `datos` | Colecciones basadas en arreglos (`ArregloAlumnos`, `ArregloSolicitudes`…) |
| `negocio` | Reglas del proceso: transiciones de estado, vacantes, expediente de documentos |
| `ui` | Pantallas Swing (`PrincipalUI` y sus paneles) |
| `util` | Utilidades de fechas de admisión |

La carpeta `docs/` contiene los requerimientos del curso, el flujo de matrícula y los diseños en Figma.

## Cómo trabajamos

- Las tareas están en [Issues](https://github.com/Fran2304/aed-casa-amarilla/issues), agrupadas por [milestones](https://github.com/Fran2304/aed-casa-amarilla/milestones) (M0–M7) y etiquetadas por dificultad (`facil`, `medio`, `dificil`) y área (`modelo`, `negocio`, `ui`, `persistencia`, `docs`).
- Una rama por issue: `feature/<número-de-issue>`.
- Commits con el número del issue, por ejemplo: `feat(#16): registra y valida los 4 documentos del expediente`.
- Los cambios entran a `main` mediante pull request.
