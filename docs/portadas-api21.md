# Portadas HTTPS en Android antiguo

## Evidencia del 8 de noviembre de 2026

En Nexus 5 API 21, después de corregir el DNS del emulador, cargan tres de cuatro portadas. Para Wikimedia, AlbumCover registra SSLHandshakeException. Desde la Mac, la misma URL devuelve HTTP 200 image/png; el Nexus resuelve upload.wikimedia.org. El registro no distingue todavía la causa interna del handshake.

Wikimedia documenta certificados de Let’s Encrypt en sus bordes estadounidenses. ISRG Root X1 está incluida por defecto desde Android 7.1.1. La raíz ausente es una hipótesis de compatibilidad, no un resultado ya verificado en el Nexus de la usuaria.

## Cambio

El ImageLoader de Coil añade ISRG Root X1 a las raíces del sistema solo hasta API 24; desde API 25 conserva el cliente predeterminado. El cliente de la API REST no cambia. No se aceptan certificados arbitrarios, no se desactiva hostname verification y no se cambia HTTPS por HTTP.

Certificado obtenido de https://letsencrypt.org/certs/isrgrootx1.pem. SHA-256 DER: 96BCEC06264976F37460779ACF28C5A7CFEF8A3C0AAE11A8FFCEE05C0BDDF08C6.

## Validación

Pruebas con servidor HTTPS local: certificado firmado por CA adicional aceptado, CA desconocida rechazada y hostname incorrecto rechazado. Deben ejecutarse en CI. La regresión conserva API 21/29/34. Pendiente confirmar la descarga real en el Nexus; si persiste el error, obtener la causa interna antes de atribuirlo a otra configuración TLS.

## Fuentes

- https://wikitech.wikimedia.org/wiki/HTTPS
- https://letsencrypt.org/docs/certificate-compatibility/
