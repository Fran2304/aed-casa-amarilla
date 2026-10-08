Revisa este pull request y responde en español. El diff a revisar en el contexto que recibirás contiene solo cambios nuevos desde el último review exitoso; no vuelvas a reportar problemas ya revisados.

- Contrasta contra docs/flujo_matricula_casa_amarilla_2027_ia.md: estados Solicitud/Matrícula/Pago, invitación 48 h, documentos 7 días calendario, pago 72 h, vacantes (capacidad menos PENDIENTE_PAGO/ACTIVA) y unicidad activa por Alumno. No inventes reglas de “Pendiente de definición”.
- Identifica el issue del título/rama `type(#NN)` y verifica sus comentarios si el contexto lo permite. Lee CONTEXT.md: usa Alumno, Apoderado, Apoderado principal/adicional y Personal; señala términos evitables como estudiante, padre, tutor o usuario.
- Busca únicamente bugs reales: nulls, límites, duplicados, transiciones inválidas y edad al 31/03/2027. Mantén excepciones en src/modelo, negocio en src/negocio y sin dominio en src/ui. No exijas JUnit: el proyecto no tiene framework de tests.
- Si cambia src/ui, contrasta con el Figma indicado solo si FIGMA_TOKEN permite verificarlo; si no, indica honestamente “fidelidad de diseño no verificable en CI: requiere revisión manual contra el Figma”.
- Un `blocker` rompe una regla funcional o requerimiento y exige cambios. Un `nonblocker` es una observación real no bloqueante. No hagas nitpicking.
- Cada id debe ser estable entre revisiones (basado en el problema/concepto, no en una línea que pueda desplazarse). Cada hallazgo inline debe usar la ruta y línea RIGHT del diff completo. Los bloqueadores sin línea válida se conservarán en el resumen.

Devuelve solamente el objeto JSON solicitado por el helper.
