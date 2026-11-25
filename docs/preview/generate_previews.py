#!/usr/bin/env python3
"""
Genera las vistas previas de AST Campo (marca de agua y pantallas) para aprobar la estética.

Es una REPLICA de las medidas del diseño en Python (Pillow), no una captura de la app:
los íconos son aproximados (en la app se usan los íconos Material) y la foto es sintética.
Uso:  python3 docs/preview/generate_previews.py
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))
FONT_DIR = "/usr/share/fonts/opentype/inter"

# ---- Paleta propuesta (NO es un Brand Kit oficial: es una propuesta para aprobar) ----
NAVY_950 = (7, 16, 31)
NAVY_900 = (11, 26, 51)
NAVY_800 = (17, 37, 68)
NAVY_700 = (26, 58, 102)
NAVY_600 = (38, 78, 130)
ORANGE = (245, 130, 32)
CYAN = (43, 212, 240)
TEXT = (232, 238, 247)
TEXT_DIM = (159, 176, 200)
WM_BG = (10, 31, 68)
WM_GRAY = (205, 214, 228)


def font(weight, size):
    return ImageFont.truetype(f"{FONT_DIR}/Inter-{weight}.otf", max(1, int(round(size))))


# --------------------------------------------------------------------------------------
# Foto sintética (granja solar) para probar la marca
# --------------------------------------------------------------------------------------
def synthetic_photo(w, h, seed=7):
    rnd = random.Random(seed)
    img = Image.new("RGB", (w, h))
    px = ImageDraw.Draw(img)
    horizon = int(h * 0.46)
    for y in range(horizon):
        t = y / horizon
        px.line([(0, y), (w, y)], fill=(int(70 + 120 * t), int(130 + 90 * t), int(200 + 40 * t)))
    for y in range(horizon, h):
        t = (y - horizon) / (h - horizon)
        px.line([(0, y), (w, y)], fill=(int(150 - 60 * t), int(130 - 50 * t), int(95 - 40 * t)))
    # lomas lejanas
    pts = [(0, horizon)]
    for x in range(0, w + 40, 40):
        pts.append((x, horizon - 18 - int(14 * math.sin(x / 140.0) + 8 * math.sin(x / 57.0))))
    pts.append((w, horizon))
    px.polygon(pts, fill=(96, 118, 104))
    # filas de paneles en perspectiva
    vx, vy = w * 0.5, horizon - 4
    rows = 9
    for r in range(rows):
        t = (r / rows) ** 1.7
        y0 = horizon + 8 + t * (h - horizon) * 0.78
        y1 = horizon + 8 + (((r + 0.72) / rows) ** 1.7) * (h - horizon) * 0.78
        span0 = (0.18 + 0.95 * t) * w
        span1 = (0.18 + 0.95 * ((r + 0.72) / rows) ** 1.7) * w
        quad = [(vx - span0 / 2, y0), (vx + span0 / 2, y0), (vx + span1 / 2, y1), (vx - span1 / 2, y1)]
        px.polygon(quad, fill=(22 + r * 2, 44 + r * 3, 86 + r * 4))
        cols = 14
        for c in range(cols + 1):
            f = c / cols
            xa = vx - span0 / 2 + span0 * f
            xb = vx - span1 / 2 + span1 * f
            px.line([(xa, y0), (xb, y1)], fill=(120, 150, 190), width=max(1, int(1 + 2 * t)))
        px.line([(quad[0][0], (y0 + y1) / 2), (quad[1][0], (y0 + y1) / 2)], fill=(110, 140, 180), width=1)
        # estructura / patas
        px.line([(vx - span0 / 2, y1), (vx - span1 / 2, y1 + 6 + 10 * t)], fill=(70, 70, 70), width=2)
    # gabinete / transformador en primer plano
    bw, bh = int(w * 0.20), int(h * 0.30)
    bx, by = int(w * 0.70), int(h * 0.60)
    px.rectangle([bx, by, bx + bw, by + bh], fill=(150, 155, 160))
    px.rectangle([bx + 8, by + 8, bx + bw - 8, by + bh - 8], outline=(110, 114, 120), width=3)
    px.rectangle([bx + bw // 2 - 3, by + 12, bx + bw // 2 + 3, by + bh - 12], fill=(110, 114, 120))
    px.rectangle([bx + 14, by + bh // 2, bx + 34, by + bh // 2 + 14], fill=(240, 190, 40))
    px.rectangle([bx - 6, by + bh, bx + bw + 6, by + bh + 12], fill=(95, 95, 95))
    # ruido suave
    noise = Image.effect_noise((w, h), 14).convert("RGB")
    img = Image.blend(img, noise, 0.04)
    return img.filter(ImageFilter.GaussianBlur(0.6))


# --------------------------------------------------------------------------------------
# Marca de agua: mismas proporciones que WatermarkRenderer.kt
# --------------------------------------------------------------------------------------
def draw_watermark(img, route, dt, data, company="", corner="BL", size="S", opacity=0.60):
    w, h = img.size
    short = min(w, h)
    t = short * (0.016 if size == "S" else 0.022)
    margin = short * 0.02
    pad_x, pad_y = t * 0.75, t * 0.6
    bar_w, bar_gap = t * 0.20, t * 0.65
    gap = t * 0.35
    radius = t * 0.35

    f_route = font("SemiBold", t)
    f_data = font("Regular", t * 0.92)
    f_co = font("SemiBold", t * 0.80)
    lines = [(route, f_route, (255, 255, 255)), (dt, f_data, WM_GRAY), (data, f_data, WM_GRAY)]
    lines = [ln for ln in lines if ln[0]]

    def tw(txt, f):
        return f.getlength(txt)

    heights = [f.getbbox("Ágy")[3] - f.getbbox("Ágy")[1] for _, f, _ in lines]
    asc = [f.getbbox("Ágy")[1] for _, f, _ in lines]
    text_w = max(tw(x, f) for x, f, _ in lines)
    co_w = 0
    if company and len(lines) > 1:
        co_w = tw(company, f_co)
        text_w = max(text_w, tw(lines[1][0], lines[1][1]) + t * 1.2 + co_w)
    block_w = pad_x + bar_w + bar_gap + text_w + pad_x
    block_h = pad_y * 2 + sum(heights) + gap * (len(lines) - 1)

    x0 = margin if corner[1] == "L" else w - margin - block_w
    y0 = h - margin - block_h if corner[0] == "B" else margin

    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle([x0, y0, x0 + block_w, y0 + block_h], radius=radius, fill=WM_BG + (int(255 * opacity),))
    # barra naranja fina a la izquierda
    d.rounded_rectangle(
        [x0 + pad_x * 0.55, y0 + pad_y, x0 + pad_x * 0.55 + bar_w, y0 + block_h - pad_y],
        radius=bar_w / 2, fill=ORANGE + (255,),
    )
    tx = x0 + pad_x * 0.55 + bar_w + bar_gap
    ty = y0 + pad_y
    for i, (txt, f, col) in enumerate(lines):
        d.text((tx, ty - asc[i]), txt, font=f, fill=col + (255,))
        if i == 1 and company:
            d.text((x0 + block_w - pad_x - co_w, ty - asc[i] + (t * 0.92 - t * 0.80) * 0.5), company, font=f_co, fill=ORANGE + (255,))
        ty += heights[i] + gap
    return Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")


SAMPLE = dict(
    route="Granja Solar X › Pruebas eléctricas › CT 5",
    dt="25/11/2026 14:05",
    data="10.968540, -74.781320   31 °C",
)


def make_watermark_samples():
    photo = synthetic_photo(2000, 1500)
    wm = draw_watermark(photo, SAMPLE["route"], SAMPLE["dt"], SAMPLE["data"], company="A.S.T.")
    wm.save(os.path.join(OUT, "marca_horizontal.jpg"), quality=92)
    portrait = synthetic_photo(1125, 1500, seed=11)
    wm_p = draw_watermark(portrait, SAMPLE["route"], SAMPLE["dt"], SAMPLE["data"], company="A.S.T.")
    wm_p.save(os.path.join(OUT, "marca_vertical.jpg"), quality=92)
    # acercamiento a la esquina para juzgar el detalle
    crop = wm.crop((0, 1500 - 330, 1000, 1500))
    crop = crop.resize((crop.width * 2, crop.height * 2), Image.LANCZOS)
    crop.save(os.path.join(OUT, "marca_detalle.jpg"), quality=92)
    return photo, portrait


# --------------------------------------------------------------------------------------
# Mockups de pantallas (1080x2340 = 411 dp de ancho)
# --------------------------------------------------------------------------------------
W, H = 1080, 2340
K = W / 411.0


def dp(v):
    return v * K


class Screen:
    def __init__(self, bg=NAVY_900):
        self.im = Image.new("RGB", (W, H), bg)
        self.d = ImageDraw.Draw(self.im, "RGBA")

    # texto en dp
    def text(self, x, y, s, size=14, weight="Regular", fill=TEXT, anchor="la"):
        self.d.text((dp(x), dp(y)), s, font=font(weight, dp(size)), fill=fill, anchor=anchor)

    def box(self, x, y, w, h, r=12, fill=NAVY_800, outline=None, width=2):
        self.d.rounded_rectangle([dp(x), dp(y), dp(x + w), dp(y + h)], radius=dp(r), fill=fill, outline=outline, width=width)

    def circle(self, cx, cy, r, fill, outline=None, width=2):
        self.d.ellipse([dp(cx - r), dp(cy - r), dp(cx + r), dp(cy + r)], fill=fill, outline=outline, width=width)

    def line(self, pts, fill=TEXT, width=2):
        self.d.line([(dp(x), dp(y)) for x, y in pts], fill=fill, width=int(dp(width)), joint="curve")

    # ---- íconos aproximados ----
    def ic_camera(self, cx, cy, s=24, col=NAVY_950):
        self.box(cx - s / 2, cy - s * 0.32, s, s * 0.7, r=s * 0.12, fill=col)
        self.box(cx - s * 0.18, cy - s * 0.46, s * 0.36, s * 0.2, r=s * 0.06, fill=col)
        self.circle(cx, cy + s * 0.03, s * 0.2, fill=(0, 0, 0, 0), outline=ORANGE if col == NAVY_950 else NAVY_900, width=int(dp(s * 0.1)))

    def ic_folder(self, cx, cy, s=24, col=CYAN):
        self.box(cx - s / 2, cy - s * 0.30, s * 0.45, s * 0.2, r=s * 0.06, fill=col)
        self.box(cx - s / 2, cy - s * 0.18, s, s * 0.6, r=s * 0.1, fill=col)

    def ic_pin(self, cx, cy, s=22, col=CYAN):
        self.circle(cx, cy - s * 0.12, s * 0.32, fill=col)
        self.d.polygon([(dp(cx - s * 0.28), dp(cy)), (dp(cx + s * 0.28), dp(cy)), (dp(cx), dp(cy + s * 0.5))], fill=col)
        self.circle(cx, cy - s * 0.12, s * 0.12, fill=NAVY_800)

    def ic_thermo(self, cx, cy, s=22, col=CYAN):
        self.box(cx - s * 0.1, cy - s * 0.5, s * 0.2, s * 0.8, r=s * 0.1, fill=col)
        self.circle(cx, cy + s * 0.32, s * 0.2, fill=col)

    def ic_back(self, cx, cy, s=22, col=TEXT):
        self.line([(cx + s * 0.25, cy - s * 0.4), (cx - s * 0.2, cy), (cx + s * 0.25, cy + s * 0.4)], fill=col, width=2.4)

    def ic_chev(self, cx, cy, s=14, col=TEXT_DIM):
        self.line([(cx - s * 0.2, cy - s * 0.4), (cx + s * 0.2, cy), (cx - s * 0.2, cy + s * 0.4)], fill=col, width=2)

    def ic_search(self, cx, cy, s=22, col=TEXT_DIM):
        self.circle(cx - s * 0.08, cy - s * 0.08, s * 0.28, fill=(0, 0, 0, 0), outline=col, width=int(dp(2)))
        self.line([(cx + s * 0.12, cy + s * 0.12), (cx + s * 0.4, cy + s * 0.4)], fill=col, width=2.4)

    def ic_gear(self, cx, cy, s=22, col=TEXT):
        for i in range(8):
            a = i * math.pi / 4
            self.circle(cx + math.cos(a) * s * 0.38, cy + math.sin(a) * s * 0.38, s * 0.12, fill=col)
        self.circle(cx, cy, s * 0.32, fill=col)
        self.circle(cx, cy, s * 0.14, fill=NAVY_900)

    def ic_plus(self, cx, cy, s=22, col=NAVY_950):
        self.line([(cx - s * 0.4, cy), (cx + s * 0.4, cy)], fill=col, width=3)
        self.line([(cx, cy - s * 0.4), (cx, cy + s * 0.4)], fill=col, width=3)

    def ic_share(self, cx, cy, s=22, col=TEXT):
        a, b, c = (cx - s * 0.3, cy), (cx + s * 0.3, cy - s * 0.3), (cx + s * 0.3, cy + s * 0.3)
        self.line([a, b], fill=col, width=2)
        self.line([a, c], fill=col, width=2)
        for p in (a, b, c):
            self.circle(p[0], p[1], s * 0.12, fill=col)

    def ic_trash(self, cx, cy, s=22, col=TEXT):
        self.box(cx - s * 0.3, cy - s * 0.2, s * 0.6, s * 0.65, r=s * 0.08, fill=col)
        self.line([(cx - s * 0.4, cy - s * 0.28), (cx + s * 0.4, cy - s * 0.28)], fill=col, width=2.4)
        self.line([(cx - s * 0.12, cy - s * 0.4), (cx + s * 0.12, cy - s * 0.4)], fill=col, width=2.4)

    def ic_move(self, cx, cy, s=22, col=TEXT):
        self.ic_folder(cx, cy, s * 0.9, col)
        self.line([(cx - s * 0.15, cy + s * 0.12), (cx + s * 0.2, cy + s * 0.12)], fill=NAVY_900, width=2.2)
        self.line([(cx + s * 0.05, cy - s * 0.02), (cx + s * 0.2, cy + s * 0.12), (cx + s * 0.05, cy + s * 0.26)], fill=NAVY_900, width=2.2)

    def ic_eye(self, cx, cy, s=22, col=TEXT):
        self.d.ellipse([dp(cx - s * 0.46), dp(cy - s * 0.26), dp(cx + s * 0.46), dp(cy + s * 0.26)], outline=col, width=int(dp(2)))
        self.circle(cx, cy, s * 0.14, fill=col)

    def ic_undo(self, cx, cy, s=22, col=CYAN):
        self.d.arc([dp(cx - s * 0.3), dp(cy - s * 0.3), dp(cx + s * 0.3), dp(cy + s * 0.3)], 200, 20, fill=col, width=int(dp(2.4)))
        self.line([(cx - s * 0.36, cy - s * 0.02), (cx - s * 0.3, cy - s * 0.3), (cx - s * 0.06, cy - s * 0.2)], fill=col, width=2.2)

    # ---- componentes ----
    def status_bar(self, light=False):
        self.text(24, 12, "14:05", 13, "Medium")
        self.text(411 - 24, 12, "LTE  87%", 12, "Medium", anchor="ra")

    def logo(self, cx, cy, s=30):
        # escudo provisional con rayo
        pts = [(cx, cy - s * 0.5), (cx + s * 0.42, cy - s * 0.32), (cx + s * 0.42, cy + s * 0.08), (cx, cy + s * 0.5), (cx - s * 0.42, cy + s * 0.08), (cx - s * 0.42, cy - s * 0.32)]
        self.d.polygon([(dp(x), dp(y)) for x, y in pts], fill=NAVY_700, outline=CYAN)
        bolt = [(cx + s * 0.06, cy - s * 0.3), (cx - s * 0.16, cy + s * 0.04), (cx - s * 0.01, cy + s * 0.04), (cx - s * 0.07, cy + s * 0.3), (cx + s * 0.17, cy - s * 0.06), (cx + s * 0.02, cy - s * 0.06)]
        self.d.polygon([(dp(x), dp(y)) for x, y in bolt], fill=ORANGE)

    def top_bar(self, title, back=True, settings=False, logo=False):
        self.status_bar()
        y = 52
        if back:
            self.ic_back(36, y + 28)
        if logo:
            self.logo(40, y + 28, 34)
            self.text(68, y + 14, title, 20, "SemiBold")
        else:
            self.text(68 if back else 24, y + 14, title, 20, "SemiBold")
        if settings:
            self.ic_gear(411 - 36, y + 28, 24)

    def chip(self, x, y, label, icon=None, color=CYAN, w=None, h=34, fill=None, outline=None):
        wid = w if w else 26 + len(label) * 8.0 + (26 if icon else 0)
        self.box(x, y, wid, h, r=h / 2, fill=fill or (color[0], color[1], color[2], 38), outline=outline or color, width=int(dp(1.2)))
        tx = x + 14
        if icon:
            icon(x + 24, y + h / 2, 18, color)
            tx = x + 40
        self.text(tx, y + h / 2 - 9, label, 13.5, "Medium", fill=color if not fill else TEXT)
        return wid

    def fab_extended(self, label, y=None, icon_fn=None, x=None, w=None):
        w = w or (24 + len(label) * 9.5 + 40)
        x = x if x is not None else 411 - 20 - w
        y = y if y is not None else 790
        self.box(x, y, w, 58, r=18, fill=ORANGE)
        (icon_fn or self.ic_plus)(x + 30, y + 29, 24)
        self.text(x + 54, y + 17, label, 16.5, "SemiBold", fill=NAVY_950)

    def photo_tile(self, x, y, s, seed):
        ph = synthetic_photo(300, 300, seed=seed).resize((int(dp(s)), int(dp(s))))
        self.im.paste(ph, (int(dp(x)), int(dp(y))))


def screen_projects():
    s = Screen()
    s.top_bar("AST Campo", back=False, settings=True, logo=True)
    # búsqueda
    s.box(16, 124, 379, 52, r=26, fill=NAVY_800, outline=NAVY_700)
    s.ic_search(44, 150)
    s.text(68, 138, "Buscar proyecto", 15, fill=TEXT_DIM)
    items = [
        ("Granja Solar X", "1.248 fotos", "Hoy, 14:05"),
        ("Proyecto Y", "436 fotos", "Ayer, 16:40"),
        ("Subestación Norte", "212 fotos", "21 nov, 09:15"),
        ("Parque Solar Z", "97 fotos", "12 nov, 11:02"),
    ]
    y = 196
    for name, n, last in items:
        s.box(16, y, 379, 118, r=16, fill=NAVY_800, outline=NAVY_700)
        s.ic_folder(48, y + 34, 26, CYAN)
        s.text(76, y + 18, name, 18.5, "SemiBold")
        s.text(76, y + 48, n, 14, fill=CYAN, weight="Medium")
        s.text(76, y + 72, "Última actividad: " + last, 13, fill=TEXT_DIM)
        # botón de captura rápida
        s.box(311, y + 31, 64, 56, r=16, fill=ORANGE)
        s.ic_camera(343, y + 59, 30)
        y += 134
    s.fab_extended("Nuevo proyecto", y=770)
    return s.im


def screen_project():
    s = Screen()
    s.top_bar("Proyecto Y", back=True)
    # breadcrumb
    x = 16
    for i, part in enumerate(["Proyecto Y", "Subestación 1"]):
        last = i == 1
        w = s.chip(x, 120, part, color=CYAN if last else TEXT_DIM, h=32, fill=(43, 212, 240, 30) if last else None)
        x += w + 10
        if not last:
            s.ic_chev(x - 4, 136)
    s.text(16, 172, "SUBCARPETAS", 12, "SemiBold", fill=TEXT_DIM)
    subs = [("Transformadores", 42), ("Tableros", 18), ("Generadores", 7), ("Banco de condensadores", 12)]
    y = 196
    for name, n in subs:
        s.box(16, y, 379, 62, r=14, fill=NAVY_800, outline=NAVY_700)
        s.ic_folder(44, y + 31, 24, CYAN)
        s.text(70, y + 19, name, 16.5, "Medium")
        s.text(340, y + 20, str(n), 15, "SemiBold", fill=CYAN, anchor="ra")
        s.ic_chev(370, y + 31)
        y += 70
    s.text(16, y + 8, "FOTOS EN ESTA CARPETA", 12, "SemiBold", fill=TEXT_DIM)
    y += 34
    size = (379 - 2 * 6) / 3
    for r in range(2):
        for c in range(3):
            s.photo_tile(16 + c * (size + 6), y + r * (size + 6), size, seed=3 + r * 3 + c)
    # acciones
    s.box(16, 770, 112, 58, r=18, fill=NAVY_700, outline=CYAN, width=int(dp(1.2)))
    s.ic_folder(40, 799, 22, CYAN)
    s.text(60, 786, "Nueva", 14, "SemiBold", fill=CYAN)
    s.fab_extended("Capturar aquí", icon_fn=s.ic_camera, y=770)
    return s.im


def screen_capture():
    s = Screen()
    s.top_bar("Captura", back=True)
    s.text(16, 122, "DESTINO", 12, "SemiBold", fill=TEXT_DIM)
    s.box(16, 144, 379, 64, r=16, fill=NAVY_800, outline=ORANGE, width=int(dp(1.6)))
    s.ic_folder(44, 176, 24, ORANGE)
    s.text(70, 158, "Granja Solar X › Pruebas eléctricas", 14, "Medium")
    s.text(70, 177, "› CT 5", 14, "SemiBold", fill=ORANGE)
    s.ic_chev(372, 176)
    x = 16
    x += s.chip(x, 224, "±8 m", icon=s.ic_pin, color=CYAN) + 8
    x += s.chip(x, 224, "31 °C", icon=s.ic_thermo, color=CYAN) + 8
    s.text(16, 266, "Toca la temperatura para escribirla a mano", 12.5, fill=TEXT_DIM)
    # visor/placeholder
    s.box(16, 296, 379, 290, r=18, fill=NAVY_950, outline=NAVY_700)
    s.ic_camera(205.5, 400, 64, NAVY_700)
    s.text(205.5, 450, "Lista para la siguiente foto", 15, "Medium", fill=TEXT_DIM, anchor="ma")
    s.text(205.5, 476, "La marca se aplica al volver de la cámara", 12.5, fill=TEXT_DIM, anchor="ma")
    # última foto
    s.box(16, 600, 379, 84, r=16, fill=NAVY_800, outline=NAVY_700)
    s.photo_tile(26, 610, 64, seed=21)
    s.text(104, 616, "Última foto guardada", 13, fill=TEXT_DIM)
    s.text(104, 638, "Sesión: 12 fotos", 16.5, "SemiBold", fill=CYAN)
    s.box(290, 626, 96, 42, r=21, fill=(0, 0, 0, 0), outline=CYAN, width=int(dp(1.2)))
    s.ic_undo(312, 647, 20)
    s.text(326, 638, "Deshacer", 12.5, "Medium", fill=CYAN)
    # botón grande
    s.box(16, 706, 379, 96, r=24, fill=ORANGE)
    s.ic_camera(120, 754, 46)
    s.text(158, 733, "TOMAR FOTO", 24, "Bold", fill=NAVY_950)
    return s.im


def screen_viewer(photo):
    s = Screen(bg=(0, 0, 0))
    ph = photo.resize((W, int(W * photo.height / photo.width)))
    s.im.paste(ph, (0, int(dp(150))))
    s.d = ImageDraw.Draw(s.im, "RGBA")
    s.status_bar()
    s.ic_back(36, 80)
    s.text(205.5, 68, "Foto 3 de 42", 15, "Medium", anchor="ma")
    # barra inferior
    s.box(0, 560, 411, 240, r=0, fill=NAVY_900)
    s.text(16, 576, "Granja Solar X › Pruebas eléctricas › CT 5", 14.5, "SemiBold")
    rows = [
        ("Fecha", "25/11/2026 14:05"),
        ("Ubicación", "10.968540, -74.781320  (±8 m)"),
        ("Temperatura", "31 °C  (referencia de la zona)"),
        ("Archivo", "Granja Solar X_Pruebas eléctricas_CT 5_20261125_140509_007.jpg"),
    ]
    y = 604
    for k, v in rows:
        s.text(16, y, k, 12.5, fill=TEXT_DIM)
        s.text(110, y, v if len(v) < 40 else v[:36] + "…", 12.5, "Medium", fill=CYAN if k == "Ubicación" else TEXT)
        y += 24
    # acciones
    acts = [("Mover", s.ic_move), ("Compartir", s.ic_share), ("Original", s.ic_eye), ("Eliminar", s.ic_trash)]
    for i, (lab, ic) in enumerate(acts):
        cx = 16 + 47.5 + i * 95.7
        s.box(cx - 44, 716, 88, 68, r=16, fill=NAVY_800, outline=NAVY_700)
        ic(cx, 740, 24, ORANGE if lab == "Eliminar" else TEXT)
        s.text(cx, 762, lab, 12.5, "Medium", anchor="ma")
    return s.im


def screen_settings(photo):
    s = Screen()
    s.top_bar("Ajustes", back=True)
    s.text(16, 122, "MARCA DE AGUA", 12, "SemiBold", fill=TEXT_DIM)
    prev = draw_watermark(photo.copy(), SAMPLE["route"], SAMPLE["dt"], SAMPLE["data"], company="A.S.T.")
    pw = 379
    ph = prev.resize((int(dp(pw)), int(dp(pw) * prev.height / prev.width)))
    s.im.paste(ph, (int(dp(16)), int(dp(144))))
    s.d = ImageDraw.Draw(s.im, "RGBA")
    y = 144 + ph.height / K + 12
    s.text(16, y, "Vista previa sobre foto de ejemplo", 12.5, fill=TEXT_DIM)
    y += 30
    toggles = [("Ruta de destino", True), ("Fecha y hora", True), ("Coordenadas", True), ("Temperatura", True)]
    for lab, on in toggles:
        s.text(16, y + 8, lab, 15.5, "Medium")
        s.box(335, y + 4, 52, 30, r=15, fill=ORANGE if on else NAVY_700)
        s.circle(372 if on else 350, y + 19, 11, fill=NAVY_950 if on else TEXT_DIM)
        y += 44
    s.text(16, y + 6, "Esquina", 15.5, "Medium")
    x = 120
    for lab, sel in [("Inf. izq.", True), ("Inf. der.", False), ("Sup. izq.", False), ("Sup. der.", False)]:
        s.box(x, y, 64, 34, r=17, fill=(43, 212, 240, 45) if sel else None, outline=CYAN if sel else TEXT_DIM, width=int(dp(1.2)))
        s.text(x + 32, y + 9, lab, 11.5, "Medium", fill=CYAN if sel else TEXT_DIM, anchor="ma")
        x += 70
    y += 48
    s.text(16, y + 6, "Tamaño", 15.5, "Medium")
    s.box(120, y, 275, 38, r=19, fill=NAVY_800, outline=NAVY_700)
    s.box(122, y + 2, 135, 34, r=17, fill=CYAN)
    s.text(190, y + 10, "Pequeño", 14, "SemiBold", fill=NAVY_950, anchor="ma")
    s.text(326, y + 10, "Mediano", 14, "Medium", fill=TEXT_DIM, anchor="ma")
    y += 52
    s.text(16, y + 2, "Opacidad  60 %", 15.5, "Medium")
    s.box(16, y + 40, 379, 6, r=3, fill=NAVY_700)
    s.box(16, y + 40, 200, 6, r=3, fill=CYAN)
    s.circle(216, y + 43, 10, fill=CYAN)
    y += 66
    s.text(16, y, "Temperatura de referencia de la zona, no medida en el sitio.", 12.5, fill=TEXT_DIM)
    return s.im


def make_screens(photo):
    shots = [
        ("pantalla_1_proyectos", screen_projects()),
        ("pantalla_2_proyecto", screen_project()),
        ("pantalla_3_captura", screen_capture()),
        ("pantalla_4_visor", screen_viewer(draw_watermark(photo.copy(), SAMPLE["route"], SAMPLE["dt"], SAMPLE["data"], company="A.S.T."))),
        ("pantalla_5_ajustes", screen_settings(photo)),
    ]
    small = []
    for name, im in shots:
        sm = im.resize((540, 1170), Image.LANCZOS)
        sm.save(os.path.join(OUT, name + ".png"), optimize=True)
        small.append(sm)
    pad = 28
    sheet = Image.new("RGB", (len(small) * 540 + (len(small) + 1) * pad, 1170 + 2 * pad), (4, 9, 18))
    for i, sm in enumerate(small):
        sheet.paste(sm, (pad + i * (540 + pad), pad))
    sheet = sheet.resize((sheet.width * 2 // 3, sheet.height * 2 // 3), Image.LANCZOS)
    sheet.save(os.path.join(OUT, "pantallas.png"), optimize=True)


def make_palette():
    sw = [("Fondo", NAVY_900), ("Superficie", NAVY_800), ("Borde", NAVY_700), ("Acento (acciones)", ORANGE), ("Datos / activo", CYAN), ("Texto", TEXT)]
    im = Image.new("RGB", (len(sw) * 220, 200), NAVY_950)
    d = ImageDraw.Draw(im)
    for i, (n, c) in enumerate(sw):
        d.rectangle([i * 220 + 10, 10, i * 220 + 210, 120], fill=c, outline=(80, 90, 110))
        d.text((i * 220 + 14, 130), n, font=font("SemiBold", 17), fill=TEXT)
        d.text((i * 220 + 14, 156), "#%02X%02X%02X" % c, font=font("Regular", 16), fill=TEXT_DIM)
    im.save(os.path.join(OUT, "paleta.png"))


if __name__ == "__main__":
    landscape, _ = make_watermark_samples()
    make_screens(landscape)
    make_palette()
    print("Listo:", sorted(f for f in os.listdir(OUT) if f.endswith((".png", ".jpg"))))
