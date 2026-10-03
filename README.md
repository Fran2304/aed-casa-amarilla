# aed-casa-amarilla

## Antes de trabajar

Activa una vez en tu clon el hook que cancela el `git push` si el proyecto no compila:

```bash
git config core.hooksPath .githooks
```

Además, GitHub Actions compila cada push y cada PR (check `compilar`).
