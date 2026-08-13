-- Catálogo inicial de categorías del blog: no hay endpoint de administración de categorías
-- en el alcance (RF-02/RF-09 las tratan como un catálogo fijo de áreas de práctica).
INSERT INTO categorias (nombre, slug)
VALUES ('Laboral', 'laboral'),
       ('Familia', 'familia'),
       ('Civil', 'civil'),
       ('Mercantil', 'mercantil'),
       ('Administrativo', 'administrativo'),
       ('Constitucional', 'constitucional'),
       ('Actualidad Jurídica', 'actualidad-juridica');
