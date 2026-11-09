# Portadas HTTPS en API 21

## Evidencia

En el Nexus 5 API 21 del equipo, la portada de A Night at the Opera falla con
SSLHandshakeException → CertificateException → CertPathValidatorException:
`Trust anchor for certification path not found` (8 de noviembre de 2026).
DNS resuelve upload.wikimedia.org; desde la Mac la URL responde 200 image/png.
Esto confirma fallo de confianza de certificado en el emulador, no un HTTP 404.

Wikimedia documenta que utiliza certificados de Let's Encrypt en sus bordes de
Estados Unidos y DigiCert en otros bordes. No se pudo inspeccionar directamente
la cadena de esa conexión desde este entorno. La compatibilidad con las raíces
ISRG es la hipótesis de corrección que debe validarse en el mismo Nexus.

## Implementación

El ImageLoader compartido de Coil utiliza el cliente normal salvo para HTTPS al
host exacto upload.wikimedia.org en API < 25. En ese caso se añaden ISRG Root X1 y
X2 a las raíces de la plataforma mediante HandshakeCertificates. Se conserva la
verificación estándar del hostname y de la cadena. No se instala una CA en el
emulador ni se cambia Retrofit/backend. Se impiden redirecciones en ese cliente
para no ampliar a otros hosts el uso de las raíces añadidas.

Los PEM son certificados públicos del almacén de CA del entorno, contrastados
con los sujetos, claves y números de serie publicados por Let's Encrypt. SHA256:

- X1: 96BCEC06264976F37460779ACF28C5A7CFE8A3C0AAE11A8FFCEE05C0BDDF08C6
- X2: 69729B8E15A86EFC177A57AFB7171DFC64ADD28C2FCA8CF1507E34453CCB1470

Fuentes consultadas el 8 de noviembre de 2026:
- https://wikitech.wikimedia.org/wiki/HTTPS
- https://letsencrypt.org/certificates/
- https://letsencrypt.org/certs/isrgrootx1.txt
- https://letsencrypt.org/certs/isrg-root-x2.txt
- https://developer.android.com/privacy-and-security/security-ssl

## Verificación

LegacyImageCallsTest comprueba en HTTPS con certificados locales controlados:
aceptación de una cadena con raíz añadida, rechazo de CA desconocida y hostname
incorrecto, alcance por versión/host/esquema y bloqueo de redirecciones.
Los resultados se consultan en CI del PR; este documento no los da por aprobados.

Pendiente manual: ejecutar esta rama en el Nexus con DNS ya corregido, comprobar
las cuatro portadas y el detalle, y capturar el resultado. Si sigue fallando,
conservar `adb logcat -d -s AlbumCover:W '*:S'` y revisar la cadena real desde la Mac.
No cerrar la incidencia únicamente por pasar las pruebas con certificados locales.
