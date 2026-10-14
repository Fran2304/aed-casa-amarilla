# La Casa Amarilla · Matrícula 2027

Aplicación de escritorio en **Java 21 y Swing**, sin Maven, Gradle ni dependencias externas.
El almacenamiento usa archivos **CSV**, no una base de datos.

## Compilar

Desde la raíz del proyecto, configura `JAVA_HOME` y `PATH` con un JDK 21 y verifica:

```sh
java -version
javac -version
find src -name '*.java' > /tmp/casaamarilla-sources.txt
javac -encoding UTF-8 -Xlint:none -d bin @/tmp/casaamarilla-sources.txt
```

## Generar datos para la exposición (seed)

Ejecuta desde terminal, después de compilar:

```sh
java -Djava.awt.headless=true -cp bin GenerarDatosDemo
```

Cada ejecución crea una carpeta nueva bajo `datos/demo-profesor/` con once CSV y
comprobantes PNG ficticios «DEMO — SIN VALOR». No sobrescribe datos normales ni demos
anteriores. El generador imprime la ruta absoluta y el comando para abrir esa demo:

```sh
java -Dcasaamarilla.datos="<carpeta-generada>" -cp bin Main
```

Reemplaza `<carpeta-generada>` por la ruta impresa, o copia directamente el comando.
**Generar los CSV y abrir la aplicación son dos pasos separados.**

### Qué queda preparado

- **Tulipanes:** cinco casos preexistentes, incluidos dos con matrícula activa.
- **Girasoles:** sin solicitudes ni matrículas, para registrar el happy path manualmente.

El recorrido detallado de los casos y del happy path está en la
[guía de demo de la spec](docs/flujo_matricula_casa_amarilla_2027_ia.md#10-demo-opcional-para-el-profesor-issue-82).

Para continuar tras cerrar, abre **la misma carpeta**, sin volver a generar los datos.
Para una exposición nueva con plazos vigentes, genera otra carpeta. No muevas ni borres
la carpeta anterior si quieres conservar sus comprobantes.

## Arranque normal

```sh
java -cp bin Main
```

`Main` carga los CSV existentes y procesa vencimientos; **no genera ejemplos al arrancar**.
Sin `-Dcasaamarilla.datos`, usa `datos/`.
