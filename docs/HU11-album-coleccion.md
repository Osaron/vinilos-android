# HU11 · Agregar un álbum a una colección

[Tarjeta #140](https://github.com/Osaron/vinilos-android/issues/140) · [Criterios y diseño inicial W8](https://github.com/Osaron/vinilos-android/wiki/Historias-adicionales-W8)

## Contrato

- `GET /collectors/{collectorId}/albums` devuelve relaciones. Para excluir álbumes ya asociados se usa `album.id` de cada relación. El `id` exterior identifica la relación. Se confirmó la forma de la respuesta con BackVynils local.
- `POST /collectors/{collectorId}/albums/{albumId}` recibe `price` y `status` (`Active` o `Inactive`). El adaptador valida que la respuesta corresponda a ambos identificadores. La columna de precio es entera.
- El código del servidor crea una nueva relación en cada POST. La interfaz debe excluir asociaciones existentes, impedir dobles envíos y consultar la relación después de un fallo ambiguo antes de ofrecer un reintento. La comprobación de la app cubre el uso secuencial. La unicidad ante clientes concurrentes requiere una garantía en el servidor.

## Flujo y verificación

Desde el detalle del coleccionista activo, abrir el selector compartido de HU15, buscar un álbum disponible, elegir precio y estado, confirmar y volver a la colección actualizada. Preservar los datos del formulario al fallar y mantener el foco en una acción útil al recorrer el formulario con TalkBack.

Comprobar en el emulador que una asociación aparece al recargar, que un segundo coleccionista mantiene sus álbumes y que un fallo recuperable permite continuar con el borrador. Registrar además las pruebas de visitante, duplicado y validación de precio.

Se implementó el recorrido en Android y se ejecutaron la compilación del APK debug y la suite unitaria. En un BackVynils efímero, el POST devolvió 200, la consulta posterior confirmó el álbum con precio y estado, y la segunda colección conservó sus asociaciones. En API 21 se comprobó el guardado con reintento y el regreso al detalle con el álbum visible. Se corrigió un fallo de compatibilidad en la confirmación de guardado y se agregó una prueba instrumentada contra el BackVynils efímero de CI. Queda verificar el resultado de CI y probar TalkBack.
