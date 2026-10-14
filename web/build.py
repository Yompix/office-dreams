# Збирає новий фронтенд в один файл web.html
import hashlib, os
D = os.path.dirname(os.path.abspath(__file__)) + '/src/'
css = open(D + 'style.css', encoding='utf8').read()
body = open(D + 'body.html', encoding='utf8').read()
js = '\n'.join(open(D + f, encoding='utf8').read() for f in ['i18n.js', 'core.js', 'anim.js', 'lobby.js', 'match.js', 'cards.js', 'wallet.js'])
html = f'''<!doctype html>
<html lang="uk"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover">
<title>Pixel Casino</title><meta name="theme-color" content="#0b0624">
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Exo+2:wght@500;600;700&family=Tiny5:wght@400..800&display=swap" rel="stylesheet">
<script src="https://telegram.org/js/telegram-web-app.js"></script>
<style>{css}</style></head><body>
{body}
<script>
{js}
</script></body></html>'''
out = os.path.dirname(D[:-1]) + '/../web.html'
open(out, 'w', encoding='utf8').write(html)
print('web.html', len(html.encode()), hashlib.sha256(html.encode()).hexdigest())
