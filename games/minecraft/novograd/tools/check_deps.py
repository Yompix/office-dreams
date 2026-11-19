#!/usr/bin/env python3
"""Проверка зависимостей в готовых архивах Новограда: у каждого мода из fabric.mod.json «depends» должен быть внутри.

  python3 tools/check_deps.py download/Novograd-Full-0.6.6.zip download/Novograd-Kino-0.6.6.mrpack ... [--cache DIR]...

Что считается «есть»: jar-файлы в архиве (на любом уровне папок), вложенные в них jar (META-INF/jars, как GeckoLib
внутри Новограда и модули внутри Fabric API) и их «provides». Для .mrpack файлы из modrinth.index.json ищутся в
папках --cache по sha1 (сборщик их туда скачивает). Для архива TLauncher дополнительно читается optional.json:
необязательные моды (скачиваются по желанию) проверяются вместе со всеми обязательными.

Код возврата 1 — чего-то не хватает (сборка должна остановиться).
"""
import hashlib, io, json, os, re, sys, zipfile

BUILTIN = {'mixinextras'}  # идёт внутри Fabric Loader
GAME = '1.21.1'
JAVA = '21'


def read_json(raw):
    # fabric.mod.json бывает с BOM и с переводами строк внутри строк — читаем терпимо
    t = raw.decode('utf-8-sig', 'replace')
    try:
        return json.loads(t, strict=False)
    except json.JSONDecodeError:
        return json.loads(re.sub(r'[\x00-\x1f]', ' ', t), strict=False)


def mods_in_jar(data, where):
    """[(id, version, depends, breaks, where)] для jar и всех вложенных jar."""
    out = []
    try:
        z = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile:
        return out
    with z:
        names = set(z.namelist())
        if 'fabric.mod.json' not in names:
            return out
        fmj = read_json(z.read('fabric.mod.json'))
        mid = fmj.get('id', '?')
        ids = [mid] + list(fmj.get('provides', []))
        out.append(dict(ids=ids, id=mid, version=str(fmj.get('version', '0')), depends=fmj.get('depends', {}) or {},
                        breaks=fmj.get('breaks', {}) or {}, where=where))
        for j in fmj.get('jars', []) or []:
            f = j.get('file')
            if f in names:
                out += mods_in_jar(z.read(f), where + ' > ' + os.path.basename(f))
    return out


# ---------------------------------------------------------------- версии (упрощённый semver, как у Fabric Loader)
def vparse(v):
    v = v.split('+')[0]
    main, _, pre = v.partition('-')
    nums = []
    for p in main.split('.'):
        m = re.match(r'\d+', p)
        nums.append(int(m.group()) if m else 0)
    return nums, pre


def vcmp(a, b):
    na, pa = vparse(a)
    nb, pb = vparse(b)
    n = max(len(na), len(nb))
    na += [0] * (n - len(na))
    nb += [0] * (n - len(nb))
    if na != nb:
        return -1 if na < nb else 1
    if pa == pb:
        return 0
    if not pa:
        return 1
    if not pb:
        return -1
    return -1 if pa < pb else 1


def one(ver, pred):
    pred = pred.strip()
    if pred in ('', '*'):
        return True
    for op in ('>=', '<=', '>', '<', '=', '^', '~'):
        if pred.startswith(op):
            x = pred[len(op):].strip()
            break
    else:
        op, x = '=', pred
    if x.endswith('.x') or x.endswith('.*'):
        x = x[:-2]
        op = '~' if op == '=' else op
    c = vcmp(ver, x)
    if op == '>=':
        return c >= 0
    if op == '<=':
        return c <= 0
    if op == '>':
        return c > 0
    if op == '<':
        return c < 0
    if op == '^':
        return c >= 0 and vparse(ver)[0][:1] == vparse(x)[0][:1]
    if op == '~':  # ~1.2.3 — от 1.2.3 до 1.3
        k = 2 if len(vparse(x)[0]) >= 2 else 1
        return c >= 0 and vparse(ver)[0][:k] == vparse(x)[0][:k]
    return c == 0


def satisfies(ver, spec):
    specs = spec if isinstance(spec, list) else [spec]
    for s in specs:  # список — «или»; внутри строки через пробел — «и»
        if all(one(ver, p) for p in str(s).split()):
            return True
    return False


# ---------------------------------------------------------------- архивы
def cache_index(dirs):
    idx = {}
    for d in dirs:
        if not os.path.isdir(d):
            continue
        for f in os.listdir(d):
            p = os.path.join(d, f)
            if os.path.isfile(p) and f.endswith('.jar'):
                idx[hashlib.sha1(open(p, 'rb').read()).hexdigest()] = p
    return idx


def check(mods, label, loader=None):
    have = {}
    base = [('minecraft', GAME), ('java', JAVA)] + ([('fabricloader', loader)] if loader else [])
    for i, v in base:
        have[i] = dict(ids=[i], id=i, version=v, depends={}, breaks={}, where=i)
    for m in mods:
        for i in m['ids']:
            have.setdefault(i, m)
    bad = []
    for m in mods:
        for dep, spec in m['depends'].items():
            if dep == 'fabric-loader':
                dep = 'fabricloader'
            if dep in BUILTIN or (dep == 'fabricloader' and not loader):
                continue
            if dep not in have:
                bad.append(f'{m["where"]}: нужен «{dep}» {spec} — его НЕТ')
            elif not satisfies(have[dep]['version'], spec):
                bad.append(f'{m["where"]}: нужен «{dep}» {spec}, а есть {have[dep]["version"]}')
        for dep, spec in m['breaks'].items():
            if dep in have and satisfies(have[dep]['version'], spec):
                bad.append(f'{m["where"]}: несовместим с «{dep}» {have[dep]["version"]}')
    top = sorted({m['id'] for m in mods if ' > ' not in m['where']})
    print(f'  [{label}] модов: {len(top)} (с вложенными: {len(mods)}) — ' + ('OK' if not bad else f'ОШИБОК: {len(bad)}'))
    for b in bad:
        print('    ✗ ' + b)
    return not bad


def main():
    args, cache, cli_loader = [], [], None
    it = iter(sys.argv[1:])
    for a in it:
        if a == '--cache':
            cache.append(next(it))
        elif a == '--loader':
            cli_loader = next(it)
        else:
            args.append(a)
    idx = cache_index(cache) if cache else {}
    ok = True
    for path in args:
        print(os.path.basename(path))
        with zipfile.ZipFile(path) as z:
            # версия Fabric Loader, с которой архив будет запускаться: из .mrpack, из профиля лаунчера в архиве,
            # из manifest.json установщика или из --loader
            loader = cli_loader
            for n in z.namelist():
                m_ = re.match(r'(?:.*/)?fabric-loader-([0-9][^/]*?)-1\.21\.1\.json$', n)
                if m_:
                    loader = m_.group(1)
            if 'manifest.json' in z.namelist():
                loader = json.loads(z.read('manifest.json')).get('loader', loader)
            if path.endswith('.mrpack'):
                loader = json.loads(z.read('modrinth.index.json')).get('dependencies', {}).get('fabric-loader', loader)
            print(f'  Fabric Loader: {loader or "не задан — не проверяю (укажи --loader)"}')
            mods = []
            for n in z.namelist():
                if n.endswith('.jar'):
                    mods += mods_in_jar(z.read(n), n)
            if path.endswith('.mrpack'):
                index = json.loads(z.read('modrinth.index.json'))
                deps = index.get('dependencies', {})
                print(f'  загрузчик: fabric-loader {deps.get("fabric-loader")}, игра {deps.get("minecraft")}')
                if deps.get('minecraft') != GAME or not deps.get('fabric-loader'):
                    print(f'    ✗ ожидается minecraft {GAME} и версия fabric-loader')
                    ok = False
                req, opt = [], []
                for f in index['files']:
                    if not f['path'].endswith('.jar'):
                        continue
                    if not f.get('hashes', {}).get('sha1') or not f.get('hashes', {}).get('sha512') or not f.get('downloads'):
                        print(f'    ✗ {f["path"]}: нет хэшей или ссылки')
                        ok = False
                        continue
                    p = idx.get(f['hashes']['sha1'])
                    if p is None:
                        print(f'    ✗ {f["path"]}: нет в кэше (--cache) — проверить нельзя')
                        ok = False
                        continue
                    ms = mods_in_jar(open(p, 'rb').read(), f['path'] + ' (скачивается)')
                    (opt if f.get('env', {}).get('client') == 'optional' else req).extend(ms)
                ok &= check(mods + req, 'обязательные', loader)
                ok &= check(mods + req + opt, 'со всеми необязательными', loader)
            elif 'optional.json' in z.namelist():
                optional = json.loads(z.read('optional.json'))
                opt = []
                for f in optional:
                    if not f['file'].endswith('.jar'):
                        continue
                    p = idx.get(f['sha1'])
                    if p is None:
                        print(f'    ✗ {f["file"]}: нет в кэше (--cache) — проверить нельзя')
                        ok = False
                        continue
                    opt += mods_in_jar(open(p, 'rb').read(), f['file'] + ' (скачивается по желанию)')
                ok &= check(mods, 'в архиве', loader)
                ok &= check(mods + opt, 'со всеми необязательными', loader)
            else:
                ok &= check(mods, 'в архиве', loader)
            if not any(m['id'] == 'fabric-api' for m in mods if ' > ' not in m['where']):
                print('    ✗ в архиве нет самого Fabric API')
                ok = False
    print('ИТОГ: ' + ('все зависимости на месте' if ok else 'НЕ ХВАТАЕТ ЗАВИСИМОСТЕЙ — сборку не выкладывать'))
    sys.exit(0 if ok else 1)


if __name__ == '__main__':
    main()
