"""Стиль SA: значок приложения «Стиль» для телефона (остальное — скин игрока — рисуется в игре,
см. client/style/SkinComposer.java). Запуск: python3 tools/gen_style.py"""
import gen_ui

if __name__ == '__main__':
    gen_ui.APP_COLORS = {'style': gen_ui.APP_COLORS['style']}
    gen_ui.app_icons()
    print('style.png готов')
