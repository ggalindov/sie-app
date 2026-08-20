-- Imagen de portada del artículo (enlace externo pegado por el admin), separada del
-- contenido enriquecido: es la miniatura que se ve en /blog y en la home, no se inserta
-- en el cuerpo del artículo (ese uso ya lo cubre el botón "insertar imagen" del editor).
ALTER TABLE articulos ADD COLUMN imagen_url VARCHAR(500);
