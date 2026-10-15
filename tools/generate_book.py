#!/usr/bin/env python3
"""Generates the Domain Codex: a written book whose pages are translatable components.

Writes data/skysdomains/recipe/domain_codex.json (Book + Arcane Dust -> codex) and merges the page
texts into assets/skysdomains/lang/en_us.json and de_de.json (keys book.skysdomains.*).
Keep every page short (about 12 lines of 20 characters) - book pages do not scroll.
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
LANG = os.path.join(ROOT, 'assets', 'skysdomains', 'lang')
RECIPE = os.path.join(ROOT, 'data', 'skysdomains', 'recipe', 'domain_codex.json')

# (key, english, german). A page = title + body. Grids use letters explained below the grid.
PAGES = [
    ('intro', "Sky's Domains", "Sky's Domains",
     "Talismans with inscribed sigils open a Domain around you: a sphere in which every enemy is hit, no matter where it hides.\n\nThis codex explains the path from the first ore to the Structural Collapse.",
     "Talismane mit eingeschriebenem Sigil öffnen eine Domain um dich: eine Sphäre, in der jeder Gegner getroffen wird, egal wo er sich versteckt.\n\nDieser Codex erklärt den Weg vom ersten Erz bis zum Structural Collapse."),
    ('activate', "Activation", "Aktivierung",
     "Hold a talisman in your OFFHAND and press the activation key (default R, see Controls).\n\nEach domain costs energy, has a radius, a duration and a cooldown. The talisman tooltip shows the current values.",
     "Halte einen Talisman in der ZWEITHAND und drücke die Aktivierungstaste (Standard R, siehe Steuerung).\n\nJede Domain kostet Energie, hat Radius, Dauer und Abklingzeit. Der Tooltip des Talismans zeigt die aktuellen Werte."),
    ('energy', "Domain energy", "Domain-Energie",
     "The bar above your hotbar is your domain energy. It regenerates slowly; a talisman in the offhand speeds this up (more per tier).\n\nWhile your domain is open the bar shows the remaining time, afterwards the cooldown.",
     "Der Balken über der Hotbar ist deine Domain-Energie. Sie regeneriert langsam; ein Talisman in der Zweithand beschleunigt das (je Stufe mehr).\n\nSolange deine Domain offen ist, zeigt der Balken die Restzeit, danach die Abklingzeit."),
    ('rules', "Rules", "Regeln",
     "- Everyone inside the sphere is hit, no line of sight needed.\n\n- You are immune to your own domain.\n\n- Other players count as enemies; scoreboard teams are spared.\n\n- Two overlapping domains clash: the higher tier wins, on a tie the one with more energy spent.",
     "- Jeder in der Sphäre wird getroffen, keine Sichtlinie nötig.\n\n- Gegen die eigene Domain bist du immun.\n\n- Andere Spieler gelten als Gegner; Scoreboard-Teams werden verschont.\n\n- Zwei überlappende Domains kollidieren: höhere Stufe gewinnt, bei Gleichstand die mit mehr Energie."),
    ('ores', "Ores", "Erze",
     "Arcane Ore: Overworld, Y -64 to 16, iron pickaxe. Drops Arcane Dust.\n\nAbyssal Ore: Nether, Y 8 to 100, diamond pickaxe. Drops Abyssal Shards.\n\nVoid Ore: End outer islands, netherite pickaxe. Drops Void Crystals.\n\nFortune works, Silk Touch gives the block.",
     "Arkanerz: Oberwelt, Y -64 bis 16, Eisenspitzhacke. Droppt Arkanstaub.\n\nAbyssalerz: Nether, Y 8 bis 100, Diamantspitzhacke. Droppt Abyssalsplitter.\n\nLeerenerz: äußere End-Inseln, Netheritspitzhacke. Droppt Leerenkristalle.\n\nGlück wirkt, Behutsamkeit gibt den Block."),
    ('drops', "Boss drops", "Boss-Drops",
     "Wither: 85% chance of a Cursed Fragment (next to the Nether Star).\n\nWarden: 90% chance of Echoed Matter.\n\nBoth are independent of Looting.",
     "Wither: 85% Chance auf ein Verfluchtes Fragment (zusätzlich zum Netherstern).\n\nWarden: 90% Chance auf Echomaterie.\n\nBeides unabhängig von Plünderung."),
    ('core', "Domain Core", "Domain-Kern",
     "Crafting table:\n\nD E D\nE B E\nD E D\n\nD = Arcane Dust\nE = Ender Pearl\nB = Book",
     "Werkbank:\n\nD E D\nE B E\nD E D\n\nD = Arkanstaub\nE = Enderperle\nB = Buch"),
    ('ink', "Sealing Ink & Refinement", "Siegeltinte & Verfeinerung",
     "Sealing Ink (3x), shapeless:\nInk Sac + Arcane Dust + Glass Bottle\n\nRefinement, shapeless:\nAbyssal Shard + Arcane Dust + Amethyst Shard",
     "Siegeltinte (3x), formlos:\nTintenbeutel + Arkanstaub + Glasflasche\n\nVerfeinerung, formlos:\nAbyssalsplitter + Arkanstaub + Amethystsplitter"),
    ('talisman', "Fractured Talisman (T1)", "Gebrochener Talisman (S1)",
     "Crafting table:\n\nP I P\nP C P\nP _ P\n\nP = Paper\nI = Sealing Ink\nC = Domain Core\n\nA talisman without a sigil is inactive.",
     "Werkbank:\n\nP I P\nP C P\nP _ P\n\nP = Papier\nI = Siegeltinte\nC = Domain-Kern\n\nEin Talisman ohne Sigil ist inaktiv."),
    ('sigil_use', "Inscribing a sigil", "Sigil einschreiben",
     "Talisman + Sigil in a crafting table (any positions).\n\nThe talisman keeps its tier and refinements. A new sigil overwrites the old one, which is lost.\n\nAll six domains work from tier 1.",
     "Talisman + Sigil in der Werkbank (beliebige Felder).\n\nDer Talisman behält Stufe und Verfeinerungen. Ein neues Sigil überschreibt das alte, das verloren geht.\n\nAlle sechs Domains gehen ab Stufe 1."),
    ('sigil_frost', "Frost Sigil", "Frost-Sigil",
     "I P I\nD B D\nI _ I\n\nI = Packed Ice\nP = Powder Snow Bucket\nD = Arcane Dust\nB = Blue Ice",
     "I P I\nD B D\nI _ I\n\nI = Packeis\nP = Pulverschnee-Eimer\nD = Arkanstaub\nB = Blaueis"),
    ('sigil_umbral', "Umbral Sigil", "Schatten-Sigil",
     "K S K\nD E D\nK S K\n\nK = Ink Sac\nS = Sculk\nD = Arcane Dust\nE = Echo Shard",
     "K S K\nD E D\nK S K\n\nK = Tintenbeutel\nS = Sculk\nD = Arkanstaub\nE = Echo-Scherbe"),
    ('sigil_glass', "Glass Sigil", "Glas-Sigil",
     "G A G\nD I D\nG A G\n\nG = Glass\nA = Amethyst Shard\nD = Arcane Dust\nI = Gold Ingot",
     "G A G\nD I D\nG A G\n\nG = Glas\nA = Amethystsplitter\nD = Arkanstaub\nI = Goldbarren"),
    ('sigil_ember', "Ember Sigil", "Glut-Sigil",
     "B M B\nD F D\nB M B\n\nB = Blaze Powder\nM = Magma Cream\nD = Arcane Dust\nF = Fire Charge",
     "B M B\nD F D\nB M B\n\nB = Lohenstaub\nM = Magmacreme\nD = Arkanstaub\nF = Feuerkugel"),
    ('sigil_stasis', "Stasis Sigil", "Stasis-Sigil",
     "A C A\nD S D\nA _ A\n\nA = Amethyst Shard\nC = Clock\nD = Arcane Dust\nS = Abyssal Shard",
     "A C A\nD S D\nA _ A\n\nA = Amethystsplitter\nC = Uhr\nD = Arkanstaub\nS = Abyssalsplitter"),
    ('sigil_collapse', "Collapse Sigil", "Kollaps-Sigil",
     "S E S\nD F D\n\nS = Abyssal Shard\nE = End Crystal\nD = Arcane Dust\nF = Cursed Fragment",
     "S E S\nD F D\n\nS = Abyssalsplitter\nE = Endkristall\nD = Arkanstaub\nF = Verfluchtes Fragment"),
    ('tiers', "Tiers", "Stufen",
     "Each tier raises radius, duration and energy regeneration and lowers the cooldown.\n\nUpgrades happen in the smithing table: talisman + template + catalyst. Sigil and refinements are kept.\n\nTemplates are found in chests (next page).",
     "Jede Stufe erhöht Radius, Dauer und Energie-Regeneration und senkt die Abklingzeit.\n\nAufwertungen passieren im Schmiedetisch: Talisman + Vorlage + Katalysator. Sigil und Verfeinerungen bleiben.\n\nVorlagen findest du in Truhen (nächste Seite)."),
    ('templates', "Templates", "Vorlagen",
     "Sealing Template: Nether fortress chests (35%).\n\nAbsolute Template: End city chests (30%).\n\nTranscendent Template: Ancient city chests (25%).",
     "Siegel-Vorlage: Netherfestungs-Truhen (35%).\n\nAbsolut-Vorlage: Endsiedlungs-Truhen (30%).\n\nTranszendenz-Vorlage: Tiefe-Stadt-Truhen (25%)."),
    ('catalysts', "Catalysts", "Katalysatoren",
     "Shapeless crafting:\n\nSealing Catalyst: 2 Abyssal Shards + 1 Cursed Fragment\n\nAbsolute Catalyst: 2 Void Crystals + 1 Nether Star\n\nTranscendent Catalyst: 2 Echoed Matter + 2 Void Crystals + 1 Cursed Fragment",
     "Formloses Rezept:\n\nSiegel-Katalysator: 2 Abyssalsplitter + 1 Verfluchtes Fragment\n\nAbsolut-Katalysator: 2 Leerenkristalle + 1 Netherstern\n\nTranszendenz-Katalysator: 2 Echomaterie + 2 Leerenkristalle + 1 Verfluchtes Fragment"),
    ('upgrades', "Upgrade paths", "Aufwertungen",
     "Smithing table (template / base / addition):\n\nT2 Sealed: Sealing Template + Fractured + Sealing Catalyst\n\nT3 Absolute: Absolute Template + Sealed + Absolute Catalyst\n\nT4 Transcendent: Transcendent Template + Absolute + Transcendent Catalyst",
     "Schmiedetisch (Vorlage / Basis / Zusatz):\n\nS2 Versiegelt: Siegel-Vorlage + Gebrochen + Siegel-Katalysator\n\nS3 Absolut: Absolut-Vorlage + Versiegelt + Absolut-Katalysator\n\nS4 Transzendent: Transzendenz-Vorlage + Absolut + Transzendenz-Katalysator"),
    ('refine', "Refinement", "Verfeinerung",
     "Anvil: talisman in the left slot, Refinement in the right slot.\n\nUp to 3 per talisman, each +10% radius and duration. Costs a few levels. The tooltip shows the count.",
     "Amboss: Talisman links, Verfeinerung rechts.\n\nMaximal 3 pro Talisman, je +10% Radius und Dauer. Kostet einige Level. Der Tooltip zeigt die Anzahl."),
    ('d_frost', "Frost Vault", "Frost Vault",
     "Enemies freeze fast and get Slowness; fully frozen they take frost damage every second.\n\nWater in the sphere turns to ice, lava to obsidian. You never freeze.",
     "Gegner frieren schnell ein und bekommen Langsamkeit; komplett vereist nehmen sie jede Sekunde Frostschaden.\n\nWasser in der Sphäre wird zu Eis, Lava zu Obsidian. Du frierst nie."),
    ('d_umbral', "Umbral Sea", "Umbral Sea",
     "Darkness for everyone but you. Enemies are pulled towards the centre and drown in shadows there: magic damage that ignores armour.",
     "Dunkelheit für alle außer dir. Gegner werden zum Mittelpunkt gezogen und ertrinken dort in Schatten: Magieschaden, der Rüstung ignoriert."),
    ('d_glass', "Glass Heaven", "Glass Heaven",
     "Pure defence: you get Resistance II and Regeneration II, enemies Weakness II. Hostile projectiles shatter on the sphere.\n\nLasts longer than the attack domains.",
     "Reine Verteidigung: du bekommst Resistenz II und Regeneration II, Gegner Schwäche II. Feindliche Projektile zerspringen an der Sphäre.\n\nHält länger als die Angriffs-Domains."),
    ('d_ember', "Ember Garden", "Ember Garden",
     "The ground burns. Enemies take fire damage every second and keep burning. You are immune to fire and lava while the garden is open.",
     "Der Boden brennt. Gegner nehmen jede Sekunde Feuerschaden und brennen weiter. Du bist feuer- und lavaimmun, solange der Garten offen ist."),
    ('d_stasis', "Stasis", "Stasis",
     "Time stops for mobs: no movement, no AI, no attacks. Projectiles vanish. Players still move.\n\nNo damage of its own: strike the frozen enemies yourself.",
     "Die Zeit steht für Mobs still: keine Bewegung, keine KI, keine Angriffe. Projektile verschwinden. Spieler bewegen sich weiter.\n\nKein eigener Schaden: greife die eingefrorenen Gegner selbst an."),
    ('d_collapse', "Structural Collapse", "Structural Collapse",
     "Enemies freeze, the sky becomes space. A solar system forms, collapses into a white hole and hurls its planets at your enemies: 3 / 4 / 6 / 8 planets per tier.\n\nTier 4: the sun itself falls last and hits everyone still standing.",
     "Gegner erstarren, der Himmel wird zum Weltraum. Ein Sonnensystem entsteht, kollabiert zu einem Weißen Loch und schleudert seine Planeten auf deine Gegner: 3 / 4 / 6 / 8 Planeten je Stufe.\n\nStufe 4: Zuletzt stürzt die Sonne selbst und trifft alle, die noch stehen."),
    ('collapse_dmg', "Collapse: damage", "Collapse: Schaden",
     "Each planet deals explosion damage (Mercury weak ... Jupiter strong, the sun strongest). Block damage is off by default.\n\nEnemies stay frozen until they are hit or the domain ends.",
     "Jeder Planet macht Explosionsschaden (Merkur schwach ... Jupiter stark, die Sonne am stärksten). Blockschaden ist standardmäßig aus.\n\nGegner bleiben eingefroren, bis sie getroffen werden oder die Domain endet."),
    ('config', "Settings", "Einstellungen",
     "config/skysdomains.json: energy, per-tier values, domain strengths, PvP and team rules, block damage, reduced flashes (accessibility), camera shake.\n\nThe activation key can be changed in the Controls menu.",
     "config/skysdomains.json: Energie, Werte je Stufe, Domain-Stärken, PvP- und Team-Regeln, Blockschaden, reduzierte Blitze (Barrierefreiheit), Kamera-Shake.\n\nDie Aktivierungstaste änderst du im Steuerungsmenü."),
]


WIDTHS = {' ': 4, 'i': 2, 'l': 3, 't': 4, 'f': 5, 'I': 4, '.': 2, ',': 2, ':': 2, '!': 2, "'": 3, '(': 4, ')': 4, '-': 6, '_': 6, '+': 6, '/': 6, '%': 6}
PAGE_WIDTH = 114
BODY_LINES = 10  # lines available under a title + blank line


def wrapped_lines(text):
    lines = 0
    for paragraph in text.split('\n'):
        width = 0
        lines += 1
        for word in paragraph.split(' '):
            w = sum(WIDTHS.get(c, 6) for c in word) + 4
            if width + w > PAGE_WIDTH and width > 0:
                lines += 1
                width = w
            else:
                width += w
    return lines


def split_body(body):
    """Splits a body at paragraph breaks into chunks that fit a page."""
    chunks, current = [], ''
    for paragraph in body.split('\n\n'):
        candidate = paragraph if not current else current + '\n\n' + paragraph
        if current and wrapped_lines(candidate) > BODY_LINES:
            chunks.append(current)
            current = paragraph
        else:
            current = candidate
    if current:
        chunks.append(current)
    return chunks


def main():
    en = json.load(open(os.path.join(LANG, 'en_us.json')))
    de = json.load(open(os.path.join(LANG, 'de_de.json')))
    for k in [k for k in en if k.startswith('book.skysdomains.')]:
        del en[k]
    for k in [k for k in de if k.startswith('book.skysdomains.')]:
        del de[k]
    en['item.skysdomains.domain_codex'] = 'Domain Codex'
    de['item.skysdomains.domain_codex'] = 'Domain-Codex'
    en['book.skysdomains.title'] = 'Domain Codex'
    de['book.skysdomains.title'] = 'Domain-Codex'
    en['book.skysdomains.toc'] = 'Contents'
    de['book.skysdomains.toc'] = 'Inhalt'
    pages = []
    # table of contents (page numbers: toc is page 1)
    pages = []
    toc_en, toc_de = [], []
    body_pages = [p for p in PAGES]
    toc_entries = [k for k, *_ in body_pages if not (k.startswith('sigil_') and k != 'sigil_use')]
    toc_pages = (len(toc_entries) + 8) // 9
    page_no = toc_pages + 1
    for key, ten, tde, ben, bde in body_pages:
        if key in toc_entries:
            toc_en.append(f'{page_no} {ten}')
            toc_de.append(f'{page_no} {tde}')
        page_no += max(len(split_body(ben)), len(split_body(bde)))
    for t in range(toc_pages):
        suffix = '' if t == 0 else str(t + 1)
        en[f'book.skysdomains.toc{suffix}.body'] = '\n'.join(toc_en[t * 9:(t + 1) * 9])
        de[f'book.skysdomains.toc{suffix}.body'] = '\n'.join(toc_de[t * 9:(t + 1) * 9])
        extra = [{"translate": "book.skysdomains.toc", "bold": True, "color": "dark_aqua"}]
        if t > 0:
            extra.append({"text": f" ({t + 1})", "color": "dark_aqua"})
        extra += [{"text": "\n\n"}, {"translate": f"book.skysdomains.toc{suffix}.body"}]
        pages.append({"text": "", "extra": extra})
    for key, ten, tde, ben, bde in body_pages:
        color = 'dark_purple' if key.startswith('d_') or key == 'collapse_dmg' else 'dark_aqua'
        chunks_en, chunks_de = split_body(ben), split_body(bde)
        count = max(len(chunks_en), len(chunks_de))
        for part in range(count):
            suffix = '' if part == 0 else f'.{part + 1}'
            en[f'book.skysdomains.{key}.body{suffix}'] = chunks_en[min(part, len(chunks_en) - 1)] if part < len(chunks_en) else ''
            de[f'book.skysdomains.{key}.body{suffix}'] = chunks_de[min(part, len(chunks_de) - 1)] if part < len(chunks_de) else ''
            extra = [{"translate": f"book.skysdomains.{key}.title", "bold": True, "color": color}]
            if part > 0:
                extra.append({"text": f" ({part + 1})", "color": color})
            extra += [{"text": "\n\n"}, {"translate": f"book.skysdomains.{key}.body{suffix}"}]
            pages.append({"text": "", "extra": extra})

    recipe = {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["minecraft:book", "skysdomains:arcane_dust"],
        "result": {"id": "minecraft:written_book", "components": {
            "minecraft:item_name": {"translate": "item.skysdomains.domain_codex"},
            "minecraft:written_book_content": {"title": "Domain Codex", "author": "Sky", "generation": 0, "resolved": True, "pages": pages},
            "minecraft:enchantment_glint_override": True}}}
    with open(RECIPE, 'w') as f:
        json.dump(recipe, f, indent=2, ensure_ascii=False)
        f.write('\n')
    for path, data in ((os.path.join(LANG, 'en_us.json'), en), (os.path.join(LANG, 'de_de.json'), de)):
        with open(path, 'w') as f:
            json.dump(data, f, indent='\t', ensure_ascii=False)
            f.write('\n')
    print('codex: %d pages' % len(pages))


if __name__ == '__main__':
    main()
