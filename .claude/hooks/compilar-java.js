// Hook PostToolUse: tras editar un .java en un proyecto Eclipse (src/ + .classpath o .project),
// compila todo src/ con javac. Si falla, sale con código 2 y los errores van a Claude por stderr.
const fs = require("fs");
const os = require("os");
const path = require("path");
const { spawnSync } = require("child_process");

function listarJava(dir, acc) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) listarJava(p, acc);
    else if (e.name.endsWith(".java")) acc.push(p);
  }
  return acc;
}

function buscarProyecto(desde) {
  let dir = path.dirname(desde);
  while (true) {
    const esEclipse =
      fs.existsSync(path.join(dir, "src")) &&
      (fs.existsSync(path.join(dir, ".classpath")) || fs.existsSync(path.join(dir, ".project")));
    if (esEclipse) return dir;
    const padre = path.dirname(dir);
    if (padre === dir) return null;
    dir = padre;
  }
}

let entrada = "";
process.stdin.on("data", (d) => (entrada += d));
process.stdin.on("end", () => {
  let archivo;
  try {
    archivo = JSON.parse(entrada).tool_input?.file_path;
  } catch {
    process.exit(0);
  }
  if (!archivo || !archivo.endsWith(".java")) process.exit(0);

  const raiz = buscarProyecto(path.resolve(archivo));
  if (!raiz) process.exit(0);

  const fuentes = listarJava(path.join(raiz, "src"), []);
  if (fuentes.length === 0) process.exit(0);

  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "compilar-java-"));
  const lista = path.join(tmp, "sources.txt");
  fs.writeFileSync(lista, fuentes.map((f) => `"${f.replace(/\\/g, "/")}"`).join("\n"));

  const r = spawnSync("javac", ["-encoding", "UTF-8", "-d", path.join(tmp, "out"), "@" + lista], {
    encoding: "utf8",
  });
  fs.rmSync(tmp, { recursive: true, force: true });

  if (r.error) process.exit(0); // javac no disponible: no bloquear
  if (r.status !== 0) {
    process.stderr.write(
      `El proyecto ${path.basename(raiz)} ya no compila tras editar ${path.basename(archivo)}:\n` +
        (r.stderr || r.stdout).trim() + "\n"
    );
    process.exit(2);
  }
  process.exit(0);
});
