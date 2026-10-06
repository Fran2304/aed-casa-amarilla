# Requerimientos del proyecto: Algoritmos y Estructura de Datos

## Objetivo

Desarrollar en **Java** un sistema orientado a objetos de **registro y matrícula de alumnos** para una entidad educativa.

## Equipo

- Grupo de **4 a 5 alumnos** del mismo turno de laboratorio.
- Elegir un coordinador. El plan indica que los integrantes permanecen en el grupo hasta terminar el curso.

## Modelo de datos

| Clase | Atributos |
|---|---|
| `Alumno` | `codAlumno` (`int`, correlativo desde 202010001), `nombres` (`String`), `apellidos` (`String`), `dni` (`String`), `edad` (`int`), `celular` (`int`), `estado` (`int`: 0 registrado, 1 matriculado, 2 retirado) |
| `Curso` | `codCurso` (`int`), `asignatura` (`String`), `ciclo` (`int`: 0 primero a 5 sexto), `créditos` (`int`), `horas` (`int`) |
| `Matrícula` | `numMatricula` (`int`, correlativo desde 100001), `codAlumno` (`int`), `codCurso` (`int`), `fecha` (`String`, `dd/mm/aaaa`), `hora` (`String`, `hh:mm:ss`) |
| `Retiro` | `numRetiro` (`int`, correlativo desde 200001), `numMatricula` (`int`), `fecha` (`String`, `dd/mm/aaaa`), `hora` (`String`, `hh:mm:ss`) |

## Menú principal y funciones

### 1. Mantenimiento

**Alumnos**

- Adicionar: generar un código correlativo, ingresar nombres, apellidos, DNI, edad y celular; iniciar con estado `0`.
- Impedir que se repita el DNI.
- Consultar y mostrar todos los datos.
- Modificar cualquier dato excepto el código y el DNI.
- Eliminar físicamente solo si el alumno está en estado `0`, con confirmación previa.
- Grabar los cambios.

**Cursos**

- Adicionar: ingresar un código único de cuatro dígitos, asignatura, ciclo, créditos y horas.
- Mantener la lista ordenada por código después de cada adición.
- Consultar y mostrar todos los datos.
- Modificar cualquier dato excepto el código.
- Eliminar físicamente solo si ningún alumno está matriculado en el curso, con confirmación previa.
- Grabar los cambios.

### 2. Registro

**Matrículas**

- Adicionar: generar número correlativo y tomar fecha y hora del sistema.
- Matricular a cada alumno una sola vez y en un solo curso; cambiar su estado a `1`.
- Consultar y mostrar todas las matrículas con sus datos completos.
- Modificar únicamente el curso matriculado.
- Cancelar físicamente una matrícula solo si el alumno no está en estado `2`, con confirmación previa.
- Grabar los cambios.

**Retiros**

- Adicionar: generar número correlativo y tomar fecha y hora del sistema.
- Desactivar temporalmente la matrícula; cambiar el estado del alumno a `2`.
- Consultar y mostrar todos los retiros con sus datos completos.
- Modificar únicamente el curso de una matrícula desactivada.
- Cancelar físicamente un retiro solo si el alumno está en estado `2`, con confirmación previa.
- Grabar los cambios.

### 3. Consulta

- Buscar por código de alumno y mostrar sus datos completos; si está matriculado, mostrar también los datos del curso.
- Buscar por código de curso y mostrar sus datos completos.
- Buscar por número de matrícula y mostrar los datos completos del alumno y curso.
- Buscar por número de retiro y mostrar los datos completos del alumno y curso.

### 4. Reporte

- Alumnos con matrícula pendiente: alumnos en estado `0`.
- Alumnos con matrícula vigente: alumnos en estado `1`.
- Alumnos matriculados por curso: nombres de los alumnos en cada curso.

## Implementación y entregables

- Aplicar desarrollo orientado a objetos.
- Implementar interfaces gráficas (GUI). La rúbrica menciona `ArrayList` y grabación de datos en archivos.
- Entregar el sistema y sus desarrollos, un **informe editable `.docx`**, una **presentación `.pptx`** y un **video demo estructurado de 3 a 5 minutos**. El video no sustituye la sustentación.
- Sustentar el proyecto en grupo.

## Contenido y formato del informe

- Introducción, justificación de la utilidad e impacto, y beneficiarios directos e indirectos.
- Al menos **dos objetivos SMART** y definición del alcance.
- Productos y entregables; máximo **tres conclusiones** y **tres recomendaciones**.
- Glosario, bibliografía y anexos.
- Página A4; márgenes superior e inferior de 3 cm, izquierdo y derecho de 2,5 cm; Arial 11; interlineado simple.
- Carátula con título, curso, profesor, ciclo, aula, semestre, coordinador e integrantes.

## Avances y evaluación

El plan menciona diseño de GUI y clases en la **semana 6**, módulos de mantenimiento sin archivos de texto en la **semana 11**, y módulos adicionales, archivos de texto, reportes y sustentación en la **semana 14**. Para la calificación final, señala **60 % informe y 40 % sustentación**.

> **Pendiente de confirmar con el docente:** otra sección del mismo plan ubica el avance en la semana 5 y la entrega final en la semana 7. La rúbrica también incluye nombres de entidades ajenas a este sistema (`Cama`, `Paciente`, `Medicina`) y menciona cuatro reportes, aunque el alcance solo define tres. Las fechas y esa parte de la rúbrica son inconsistentes.

## Fuente

- Plan de proyecto de investigación aplicada 2026-02
