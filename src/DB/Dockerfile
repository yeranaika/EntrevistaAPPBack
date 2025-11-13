FROM postgres:16

# Variables por defecto para que la imagen ya sepa cómo levantar
ENV POSTGRES_DB=DBentrevista
ENV POSTGRES_USER=root
ENV POSTGRES_PASSWORD=root

# Copiamos el script de inicialización dentro de la imagen
COPY initdb/BasedeDatos.EntrevistaApp.sql /docker-entrypoint-initdb.d/
