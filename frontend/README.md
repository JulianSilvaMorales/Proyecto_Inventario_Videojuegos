# Frontend

El frontend usa React, TypeScript, Vite y Material UI. Para iniciar el servidor
local, primero asegúrate de que el backend esté disponible y luego ejecuta
desde esta carpeta:

```powershell
npm ci
npm run dev
```

Abre `http://localhost:5173`. La API apunta por defecto a
`http://localhost:8080`; para cambiarla, crea `.env.local` con
`VITE_API_URL=http://localhost:8080`. No guardes credenciales en variables
`VITE_*`, porque son visibles para el navegador.

Los comandos de verificación son `npm run lint`, `npm run typecheck`,
`npm test` y `npm run build`.

La interfaz CRUD genérica se genera desde los metadatos del modelo y está
disponible para países, monedas, idiomas, tipos de cambio, impuestos,
categorías, bodegas, proveedores y clientes. Las referencias y los clientes
usan `/api/v1/referencias/{entidad}` y `/api/v1/clientes`; el backend limita los
datos según los permisos y los países asignados al usuario.
