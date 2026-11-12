# HU08 · Diseño para agregar una canción a un álbum

Propuesta de interfaz para revisión en [#30](https://github.com/Osaron/vinilos-android/issues/30).

## Flujo del coleccionista

1. Desde el detalle de un álbum, en **Canciones**, toca **Agregar canción**. El formulario abre con ese álbum seleccionado.
2. En **Álbum**, toca **Cambiar álbum** para elegir otro del catálogo. Muestra el título y, si está disponible, el artista. Conserva el ID del álbum seleccionado incluso si hay títulos iguales. Cancelar la selección mantiene el álbum anterior.
3. Completa **Nombre de la canción** y **Duración**. Pulsa **Guardar canción**. Si el guardado se confirma, abre el detalle del álbum elegido, actualiza su lista de canciones y muestra **Canción agregada**.

## Composición de la pantalla

| Orden | Elemento | Comportamiento |
|---|---|---|
| 1 | Volver · Agregar canción | Si hay cambios sin guardar, permite seguir editando o descartarlos. |
| 2 | Álbum · Cambiar álbum | Muestra el destino antes de enviar. Si la carga del selector falla, ofrece reintentar. Si el catálogo está vacío, informa que no hay álbumes disponibles. |
| 3 | Nombre de la canción | Obligatorio. Valida tras recortar espacios. Mensaje: «Escribe el nombre de la canción». |
| 4 | Duración | Obligatoria. Ayuda visible «Ejemplo: 3:45». Se propone minutos y dos dígitos de segundos entre 00 y 59. Mensaje: «Ingresa la duración en minutos y segundos, por ejemplo 3:45». |
| 5 | Guardar canción | Botón de acción principal. Los errores de validación aparecen junto al campo y el foco va al primero con error. |

Reutilizar etiquetas, campos y botones del formulario **Agregar álbum**. Mantener la pantalla desplazable con teclado y fuente ampliada. Usar etiquetas visibles, orden de foco de arriba abajo y mensajes de estado que TalkBack pueda anunciar.

## Estados y contrato

- **Edición.** Conserva álbum y borrador al rotar. Cambiar de álbum conserva los campos.
- **Envío.** Muestra «Guardando canción…» y deshabilita el botón hasta recibir respuesta.
- **Error confirmado.** Conserva el borrador. Si el álbum dejó de existir, pide elegir otro. Si se perdió la respuesta después de enviar, consulta las canciones del álbum antes de ofrecer un nuevo envío. Una coincidencia por nombre y duración puede ayudar a la revisión del usuario, pero no confirma qué solicitud creó la canción.
- **Guardado confirmado y actualización fallida.** Informa que la canción se guardó y ofrece recargar el detalle sin repetir el guardado.

El controlador de [BackVynils](https://github.com/TheSoftwareDesignLab/BackVynils/blob/7178f83662022904dc9e88b8a2292568954b56cf/src/track/track.controller.ts) expone `POST /albums/{albumId}/tracks` y devuelve HTTP 200 con el track guardado. El [servicio](https://github.com/TheSoftwareDesignLab/BackVynils/blob/7178f83662022904dc9e88b8a2292568954b56cf/src/track/track.service.ts) exige `name` y `duration` como cadenas y genera el ID. El formato de duración propuesto es una validación de interfaz para acordar con el equipo.

## Entrega a implementación

- **Donaldo (#31 y #33).** Selector, ID de destino y contrato del servicio.
- **Hernán (#32).** Campos, validación y conservación del borrador.
- **Óscar (#34 y #35).** Estados de envío y pruebas de guardado, rotación, cancelación, errores y actualización del álbum de destino.
