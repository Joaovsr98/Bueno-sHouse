-- Foto de cada produto do cardapio de exemplo (Unsplash, licenca livre; servidas pelo CDN do
-- proprio Unsplash, como ele recomenda). So preenche produto ainda sem foto: uma foto
-- cadastrada pela tela de Catalogo nunca e sobrescrita.
UPDATE products p
JOIN (
    SELECT 'X-Burguer Artesanal' AS name, 'photo-1568901346375-23c9450c58cd' AS photo
    UNION ALL SELECT 'X-Bacon Duplo',           'photo-1553979459-d2229ba7433b'
    UNION ALL SELECT 'Refrigerante Lata 350ml', 'photo-1622483767028-3f66f32aef97'
    UNION ALL SELECT 'Pudim de Leite',          'photo-1702728109878-c61a98d80491'
    UNION ALL SELECT 'Hot Dog Completo',        'photo-1612392166886-ee8475b03af2'
    UNION ALL SELECT 'Batata Frita',            'photo-1630384060421-cb20d0e0649d'
    UNION ALL SELECT 'Pão de Batata',           'photo-1776099536340-780ba11e159d'
) x ON LOWER(p.name) = LOWER(x.name)
SET p.image_url = CONCAT('https://images.unsplash.com/', x.photo, '?w=800&q=80&fit=crop&auto=format')
WHERE p.unit_id = 1 AND p.deleted_at IS NULL AND (p.image_url IS NULL OR p.image_url = '');
