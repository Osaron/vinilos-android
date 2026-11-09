# HU06 — Detalle de coleccionista (adelanto W5)

## Contrato observado

`GET /collectors/:collectorId` devuelve identidad y contacto del coleccionista, `comments` y `favoritePerformers`. También devuelve `collectorAlbums`, pero en el contrato observado esos elementos solo contienen `id`, `price` y `status`; no traen el álbum asociado. La app presenta los artistas/bandas favoritos y comentarios, y omite la relación de compra porque el nombre y portada del álbum no están disponibles en esta respuesta.

IDs comprobados localmente contra BackVynils: 100 (Manolo Bellon) y 101 (Jaime Monsalve). Para 100, el endpoint devolvió a Rubén Blades como favorito y un comentario con calificación; para 101, devolvió a Queen y un comentario.

## Jerarquía de pantalla

1. Acción Atrás.
2. Nombre del coleccionista.
3. Correo y teléfono, ocultos cuando no existen.
4. Lista de artistas/bandas favoritos, con imagen, nombre y atributos que entregue el backend.
5. Comentarios y calificación cuando estén presentes.
6. Estados de carga, error con reintento y secciones vacías.

La navegación de detalle se ofrece después de seleccionar una fila del catálogo, preservando la selección/listado existente. Atrás retorna al listado con la búsqueda y selección conservadas.

## Alcance del adelanto

- Detalle accesible desde el catálogo de coleccionistas en Android.
- Consumo REST mediante DTO → Service Adapter → Repository.
- ViewModel con carga, contenido, error y reintento.
- Unitarias, prueba de servicio y Espresso para navegación, contenido y reintento.
- El flujo HU05 → HU06 queda pendiente de la integración del listado de HU05.
